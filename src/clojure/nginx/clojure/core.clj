(ns nginx.clojure.core
  (:import [nginx.clojure Coroutine Stack NginxClojureRT 
            NginxRequest NginxServerChannel ChannelListener
            AppEventListenerManager AppEventListenerManager$Listener
            AppEventListenerManager$Decoder AppEventListenerManager$PostedEvent])
  (:import [nginx.clojure.clj Constants])
  (:import [java.nio ByteBuffer]))

(defn without-coroutine 
  "wrap a handler f to a new handler which will keep away the coroutine context"
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
  (->> fns (clojure.lang.RT/seqToTypedArray Callable) NginxClojureRT/coBatchCall seq))

(defn get-ngx-var 
  "get nginx variable"
  [^NginxRequest req name]
  (NginxClojureRT/getNGXVariable (.nativeRequest req) name))

(defn set-ngx-var! 
  "set nginx variable"
  [^NginxRequest req name, val]
  (NginxClojureRT/setNGXVariable (.nativeRequest req) name val))

(def phrase-done Constants/PHRASE_DONE)

(defn hijack! 
  "Hijack a nginx request and return a server channel.
   After being hijacked, the ring handler's result will be ignore.
   If ignore-nginx-filter? is true all data output to channel won't be filtered
   by any nginx HTTP header/body filters such as gzip filter, chucked filter, etc.
   We can use this function to implement long poll / Server Sent Events (SSE) easily."
  [^NginxRequest req ignore-nginx-filter?]
  (-> req (.handler) (.hijack req ignore-nginx-filter?)))

(defprotocol HttpServerChannel
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
  (on-close! [ch attachment listener]
    "Add a close event listener.
     attachement is a  object which will be passed to listener when close event happens
     listener is a function like (fn[attachement] ... )")
  )

(extend-type NginxServerChannel HttpServerChannel
  (close [ch] (.close ch))
  (send! [ch data flush? last?]
    (cond
      (instance? String data) (.send ch ^String data flush? last?)
      (instance? ByteBuffer) (.send ch ^ByteBuffer data flush? last?)
      :else
      (.send ch ^bytes data 0 (.length ^bytes data) flush? last?)))
  (send-header! [ch status ^java.util.Map headers flush? last?]
    (.sendHeader ch status (.entrySet headers) flush? last?))
  (send-response! [ch resp]
    (.sendResponse ch resp))
  (on-close! [ch attachment listener]
    (.addListener ch (proxy [ChannelListener] []
                       (onClose [att]
                         (listener att)))
      attachment)))


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
  "Add a broadcasted event listener.
   Function f is like (fn[event] ... ) and event has the form {:tag tag, :data `bytes or long`, :offset offset :length length }
   `offset & `length are meamingless if data is a long integer."
  [f]
  (-> (NginxClojureRT/getAppEventListenerManager)
      (.addListener (proxy [AppEventListenerManager$Listener] []
                      (onEvent [e] (f (event-clj-wrap e)))))))

(defn on-broadcast-event-decode!
  "Add a decoder to broadcast event decoder chain.
   Decoders will be called one by one and the current decode result will be past to the next decoder.
   Function tester is a checker and only if it return true the decoder will be invoked."
  [tester decoder]
  (-> (NginxClojureRT/getAppEventListenerManager)
    (.addDecoder (proxy [AppEventListenerManager$Decoder] []
                   (shouldDecode [e] (tester (event-clj-wrap e)))
                   (decode [e] (clj-event-wrap (decoder (event-clj-wrap e))))))))

