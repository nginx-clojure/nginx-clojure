(ns nginx.clojure.core
  "Core functions."
  (:import [nginx.clojure Coroutine Stack NginxClojureRT 
            NginxRequest NginxHttpServerChannel ChannelListener
            AppEventListenerManager AppEventListenerManager$Listener
            AppEventListenerManager$Decoder AppEventListenerManager$PostedEvent
            MessageAdapter WholeMessageAdapter ChannelCloseAdapter])
  (:import [nginx.clojure.net NginxClojureAsynChannel NginxClojureAsynChannel$CompletionListener
            NginxClojureAsynSocket])
  (:import [nginx.clojure.clj Constants])
  (:import [java.nio ByteBuffer]))

(def process-id NginxClojureRT/processId)

(defn without-coroutine 
  "wrap a handler `f` to a new handler which will keep away the coroutine context"
  [f]
  (fn [& args]
    (let [s (Stack/getStack)]
      (try
        (Stack/setStack nil)
        (apply f args)
        (finally (Stack/setStack s))))))

(defmacro co-pvalues
  "Returns a sequence of the values of the exprs, which are evaluated in parallel coroutines.
   If there's no coroutine support, it will turn to use thread pool to make testing with lein-ring easy.
   e.g. fetch two services in parallel:
   (let [[r1, r2] (co-pvalues (client/get \"http://page1-url\") (client/get \"http://page2-url\"))]
    ;println bodies of two remote responses
    (println (str (:body r1) \"====\\n\" (:body r2) ))
  "
  [& exprs]
  `(co-pcalls ~@(map #(list `fn [] %) exprs)))

(defn co-pcalls
  "Executes the no-arg fns in parallel coroutines, returning a  sequence of their values
   If there's no coroutine support, it will turn to use thread pool to make testing with lein-ring easy.
   e.g. fetch two services in parallel:
   (let [[r1, r2] (co-pcalls (fn[] (client/get \"http://page1-url\")) (fn[] (client/get \"http://page2-url\")))]
    ;println bodies of two remote responses
    (println (str (:body r1) \"====\\n\" (:body r2) ))
  "
  [& fns]
  (let [fns (if  NginxClojureRT/coroutineEnabled  
                         fns 
                         (let [bindings (clojure.lang.Var/getThreadBindings)]
                            (doall  
                                (for [f fns] #(do (with-bindings* bindings f))))) )]
    (->> fns (clojure.lang.RT/seqToTypedArray Callable) NginxClojureRT/coBatchCall seq) ) )

(defn get-ngx-var 
  "get nginx variable"
  ([^NginxRequest req name]
    (.getVariable req name))
  ([^NginxRequest req name defaultVal]
    (.getVariable req name defaultVal)) 
  )

(defn set-ngx-var! 
  "set nginx variable"
  [^NginxRequest req name, val]
  (.setVariable req name val))

(defn discard-request-body!
  "discard request body"
  [^NginxRequest req]
  (.discardRequestBody  req))

(def phrase-done Constants/PHRASE_DONE)

(def phase-done phrase-done)

(defn hijack! 
  "Hijack a nginx request and return a server channel.
   After being hijacked, the ring handler's result will be ignored.
   If ignore-nginx-filter? is true all data output to channel won't be filtered
   by any nginx HTTP header/body filters such as gzip filter, chucked filter, etc.
   We can use this function to implement long polling / Server Sent Events (SSE) easily."
  [^NginxRequest req ignore-nginx-filter?]
  (-> req (.handler) (.hijack req ignore-nginx-filter?)))

(defprotocol HttpServerChannel
  (closed? [ch])
  (close! [ch] 
    "Asynchronously close the channel. If there's remaining data to send it won't block 
     current thread  and will flush data on the background asynchronously and later close the channel safely")
  (send! [ch data flush? last?]
    "Asynchronously send data to channel without blocking current thread. 
     data can be byte[], String, or ByteBuffer
     If flush? is false it will put data into buffers chain eitherwise it will write data to network.
     If close? is true it will close channel after all data are sent.
    ")
  (send-header! [ch status headers flush? last?] 
    "Asynchronously send HTTP status & headers to channel.
     status is a integer for HTTP status code, headers is a HTTP headers map.
     If flush? is false it will put data into buffers chain eitherwise it will write data to network.
     If close? is true it will close channel after all data are sent.")
  (send-response! [ch resp]
    "Asynchronously send a complete HTTP response to channel and close channel after all data are sent.
     resp is a ring Response Map, e.g. {:status 200, headers {\"Content-Type\" \"text/html\"}, :body \"Hello, Nginx-Clojure!\" } .
     ")
  (add-listener! [ch callbacks-map]
    "Add a channel event listener.
      callbacks-map is a map whose key can be either of :on-open,:on-message,:on-close,:on-error
      the value of :on-open is a function like (fn[ch]...)
      the value of :on-message is a function like (fn[ch message remaining?]...)
      the value of :on-close is a function like (fn[ch reason]...)
      the value of :on-error is a function like (fn[ch status])
     ")
  (add-aggregated-listener! [ch max-message-size callbacks-map]
    "Add an aggregated message listener.
      callbacks-map is a map whose key can be either of :on-open,:on-message,:on-close,:on-error
      the value of :on-open is a function like (fn[ch]...)
      the value of :on-message is a function like (fn[ch message]...)
      the value of :on-close is a function like (fn[ch reason]...)
      the value of :on-error is a function like (fn[ch status])
     ")
  (websocket-upgrade! [ch send-err-for-nonwebsocekt?]
   "Send upgrade headers and return true if upgrade success
    If `send-err-for-nonwebsocekt?` is true it will send error response.
   ")
  (on-close! [ch attachment listener]
    "Add a close event listener.
     `attachement is a  object which will be passed to listener when close event happens
     `listener is a function like (fn[attachement] ... )
     A close event will happen immediately when channel is closed by either of these three cases:
     (1) channel close function/method is invoked on this channel, e.g. (close! ch)
     (2) inner unrecoverable error happens with this channel, e.g. not enough memory to read/write
     (3) remote client connection is closed or broken.")
  (get-context [ch])
  (set-context! [ch ctx])
  )

(defprotocol AsynchronousChannel
  "Only works on non-threadpool mode, viz. coroutine mode or default mode."
  (aclose! [ch]
     "Close the channel")
  (aconnect! [ch url attachment on-err on-done]
     "Connect to the remote url.
      `url can be \"192.168.2.34:80\" , \"www.bing.com:80\", or unix domain socket \"unix:/var/mytest/server.sock\"
      `on-err and `on-done are  functions which have the form (fn[status,attachment]
       when passed to `on-err, `status is an error code which range from 
	NginxClojureAsynSocket.NGX_HTTP_CLOJURE_SOCKET_ERR to NGX_HTTP_CLOJURE_SOCKET_ERR_OUTOFMEMORY
       when passed to `on-done `status is always 0. ")
  (arecv! [ch buf attachment on-err on-done]
     "receive data from the channel.
      `buf can be byte[] or ByteBuffer
      `on-err and `on-done are  functions which have the form (fn[status,attachment]
       when passed to `on-err, `status is an error code which is eof (0) or range from 
	NginxClojureAsynSocket.NGX_HTTP_CLOJURE_SOCKET_ERR to NGX_HTTP_CLOJURE_SOCKET_ERR_OUTOFMEMORY
       when passed to `on-done `status is always > 0 and means number of bytes received.
      ")
  (asend! [ch data attachment on-err on-done]
     "send data to the channel.
      `data can be String, byte[] or ByteBuffer
      `on-err and `on-done are  functions which have the form (fn[status,attachment]
       when passed to `on-err, `status is an error code which is eof (0) or range from 
	NginxClojureAsynSocket.NGX_HTTP_CLOJURE_SOCKET_ERR to NGX_HTTP_CLOJURE_SOCKET_ERR_OUTOFMEMORY
       when passed to `on-done `status is always > 0 and means number of bytes sent.")
  (aclosed? [ch])
  (ashutdown! [ch how]
   "shutdown some kind of events trigger of the socket
    `how can be :soft-read :soft-write :soft-both :read :write :both. If we use :soft-xxx it won't 
    shutdown the physical socket and just turn off the events trigger for better performance.
    Otherwise it will shutdown the physical socket, more details can be found from http://linux.die.net/man/2/shutdown")
  (aset-context! [ch ctx])
  (aget-context [ch])
  (aset-timeout! [ch connect-timeout read-timeout write-timeout]
    "The timeout unit is millisecond.
     if timeout < 0 it will be ignored, if timeout = 0 it means no timeout settings"
    )
  (error-str [ch code]
    "return the error string message from the error code"))

(extend-type NginxHttpServerChannel HttpServerChannel
  (closed? [ch] (.isClosed ch))
  (close [ch] (.close ch))
  (send! [ch data flush? last?]
    (cond
      (instance? String data) (.send  ch ^String data ^boolean flush? ^boolean last?)
      (instance? ByteBuffer data) (.send   ch ^ByteBuffer data ^boolean flush? ^boolean last?)
      :else
      (.send ch ^bytes data 0 (count data) flush? last?)))
  (send-header! [ch status ^java.util.Map headers flush? last?]
    (.sendHeader ch status (.entrySet headers) flush? last?))
  (send-response! [ch resp]
    (.sendResponse ch resp))
  (on-close! [ch attachment listener]
    (.addListener ch attachment (proxy [ChannelCloseAdapter] []
                       (onClose [att]
                         (listener att)))))
  
  (add-listener! [ch {:keys [on-open on-message on-close on-error]}]
    (.addListener ch ch (proxy [MessageAdapter] []
                          (onOpen [c] (if on-open (on-open c)))
                          (onTextMessage [c msg rem?] (if on-message (on-message c msg rem?)))
                          (onBinaryMessage [c msg rem?] (if on-message (on-message c msg rem?)))
                          (onClose ;([c] (if on-close (on-close c "0")))
                                   ([c status reason] (if on-close (on-close c (str status ":" reason)))))
                          (onError [c status] (if on-error (on-error c (NginxClojureAsynSocket/errorCodeToString status)))))))
  (add-aggregated-listener! [ch max-message-size {:keys [on-open on-message on-close on-error]}]
    (.addListener ch ch (proxy [WholeMessageAdapter] [max-message-size]
                          (onOpen [c] (if on-open (on-open c)))
                          (onWholeTextMessage [c msg] (if on-message (on-message c msg)))
                          (onWholeBiniaryMessage [c msg] (if on-message (on-message c msg)))
                          (onClose ;([c] (if on-close (on-close c "0")))
                                   ([c status reason] (if on-close (on-close c (str status ":" reason)))))
                          (onError [c status] (if on-error (on-error c (NginxClojureAsynSocket/errorCodeToString status)))))))
  (websocket-upgrade! [ch send-err-for-nonwebsocekt?]
    (.webSocketUpgrade ch ^boolean send-err-for-nonwebsocekt?))
  (get-context [ch]
    (.getContext ch))
  (set-context! [ch ctx]
    (.setContext ch ctx)))

(defn- make-asyn-listener [on-err on-done]
  (proxy [NginxClojureAsynChannel$CompletionListener] []
    (onDone [c a] (on-done c a))
    (onError [c a] (on-err c a))))

(extend-type NginxClojureAsynChannel AsynchronousChannel
  (aclose! [ch] (.close ch))
  (aconnect! [ch ^String url attachment on-err on-done]
    (.connect ch url attachment (make-asyn-listener on-err on-done)))
  (arecv! [ch buf attachment on-err on-done]
    (if (instance? ByteBuffer buf)
      (.read ch ^ByteBuffer buf attachment (make-asyn-listener on-err on-done))
      (.read ch ^bytes buf 0 (count buf) attachment (make-asyn-listener on-err on-done))))
  (asend! [ch data attachment on-err on-done]
    (if (instance? ByteBuffer data)
      (.write ch ^ByteBuffer data attachment (make-asyn-listener on-err on-done))
      (if (instance? String data)
        (let [bs (.getBytes ^String data "utf-8")]
          (.write ch ^bytes bs 0 (count bs) attachment (make-asyn-listener on-err on-done)))
        (.write ch ^bytes data 0 (count data) attachment (make-asyn-listener on-err on-done)))))
  (aclosed? [ch] (.isClosed ch))
  (ashutdown! [ch how] 
    (-> ch (.getAsynSocket) 
      (.shutdown 
        (how {:read NginxClojureAsynSocket/NGX_HTTP_CLOJURE_SOCKET_SHUTDOWN_READ
              :write NginxClojureAsynSocket/NGX_HTTP_CLOJURE_SOCKET_SHUTDOWN_WRITE
              :both NginxClojureAsynSocket/NGX_HTTP_CLOJURE_SOCKET_SHUTDOWN_BOTH
              :soft-read NginxClojureAsynSocket/NGX_HTTP_CLOJURE_SOCKET_SHUTDOWN_SOFT_READ
              :soft-write NginxClojureAsynSocket/NGX_HTTP_CLOJURE_SOCKET_SHUTDOWN_SOFT_WRITE
              :soft-both NginxClojureAsynSocket/NGX_HTTP_CLOJURE_SOCKET_SHUTDOWN_SOFT_BOTH}))))
  (aset-context! [ch ctx]
    (.setContext ch ctx))
  (aget-context [ch]
    (.getContext ch))
  (aset-timeout! [ch connect-timeout read-timeout write-timeout]
    (.setTimeout ch connect-timeout read-timeout write-timeout))
  (error-str [ch code]
    (.buildError ch code)))

(defn achannel 
  "create an asynchronous socket channal."
  []
  (NginxClojureAsynChannel.))

(defn broadcast!
  "Broadcast a  event to all nginx worker processes. 
   This function can be used to notify all subscribers in different nginx
worker processes from the same nginx instance.
   `event has the form {:data data} or {:tag tag, :data data} 
   `data can be Long, String, byte[]. If it is long integer it must be less than 0x0100000000000000L
If it is string or bytes, it must be less than PIPE_BUF - 8, generally on Linux/Windows is 4088, 
on MacosX is 504
	 `data will be truncated if its length exceeds this limitation.
   If `tag is given, `data can also be long integer which means this event is 
   very simple and only has a event id without any body or body is stored externally.
   The default tag value is 0x20 when `msg is Long otherwise the default value is 0x80.
   Here is a list of `tag values range:
   * System Event :0x00 ~ 0x1f -- Application should not use them
	 * Application Event : 0x20 ~ 0xff
	 * Simple  Event : 0x00 ~ 0x7f, only event id (7Byte), no message body
	 * Complex Event : 0x80 ~ 0xff"
  [event]
  (let [mgr (NginxClojureRT/getAppEventListenerManager)
        pe (.buildPostedEvent mgr (:tag event) (:data event))]
    (.broadcast mgr pe)))

(defn- event-clj-wrap [^AppEventListenerManager$PostedEvent e]
  {:tag (.tag e), :data (.data e), :offset (.offset e), :length (.length e)})

(defn- clj-event-wrap [{:keys [tag, data, offset length]}]
  (AppEventListenerManager$PostedEvent. tag data offset length))

(defn on-broadcast! 
  "Add a broadcasted event listener and return a removal function to delete the listener
   Function f is like (fn[event] ... ) and event has the form {:tag tag, :data `bytes or long`, :offset offset :length length }
   `offset & `length are meamingless if data is a long integer."
  [f]
  (let [l (proxy [AppEventListenerManager$Listener] []
               (onEvent [e] (f (event-clj-wrap e))))
        m (NginxClojureRT/getAppEventListenerManager)]
    (.addListener m l)
    (fn [] (.removeListener m l))))

(defn on-broadcast-event-decode!
  "Add a pair of tester & decoder to broadcast event decoder chain
and return a removal function to delete the decoder.
   Decoders will be called one by one and the current decode result will be past to the next decoder.
   Decoders should return decoded event which has the form {:tag tag, :data `any type of data`, :offset offset :length length }
   offset & `length are meamingless if data is a long integer.
   Function tester is a checker and only if it return true the decoder will be invoked."
  [tester decoder]
  (let [d (proxy [AppEventListenerManager$Decoder] []
            (shouldDecode [e] (tester (event-clj-wrap e)))
            (decode [e] (clj-event-wrap (decoder (event-clj-wrap e)))))
        m (NginxClojureRT/getAppEventListenerManager)]
  (.addDecoder m d)
  (fn [] (.removeDecoder m d))))

(defn build-topic! [name]
  "build a topic"
  (nginx.clojure.util.NginxPubSubTopic. name))

(defprotocol PubSubTopic
  (pub! [topic message]
     "Publishs a message to the topic")
  (sub! [topic att callback]
     "Subscribes to a topic and returns an unsubscribing function. 
When a message comes the callback function will be invoked. e.g.
      (def my-topic (build-topic! \"my-topic\"))
      (sub! my-topic (atomic 0) 
           (function [message counter]
              (println \"received :\" message \", times=\" (swap counter inc)))")
  (destory! [topic]
      "Destory the topic."))

(extend-type nginx.clojure.util.NginxPubSubTopic PubSubTopic
  (pub! [topic message]
    (.publish topic message))
  (sub! [topic att callback]
    (let [pd (.subscribe topic att 
               (proxy [nginx.clojure.util.NginxPubSubListener] []
                 (onMessage [message att]
                   (callback message att))))]
      (fn [] (.unsubscribe topic pd))))
  (destory! [topic] (.destory topic)))
