(ns nginx.clojure.asyn-socket-handlers-for-test
  (:require [compojure.route :as route])
  (:import  [nginx.clojure.logger LoggerService]
            [nginx.clojure NginxClojureRT LazyRequestMap]
            [nginx.clojure.net NginxClojureAsynSocket]
            [java.io ByteArrayInputStream ByteArrayOutputStream]))

(def ^LoggerService logger (NginxClojureRT/getLog))

(defn connect-handler 
  "handle connect event, as is a NginxClojureAsynSocket, sc is status code"
  [^NginxClojureAsynSocket as, ^long sc]
  (if (not= sc NginxClojureAsynSocket/NGX_HTTP_CLOJURE_SOCKET_OK)
    (do 
      (.error logger (format "on connect error: %s" (.errorCodeToString as sc)))
      (.close as)
      (NginxClojureRT/completeAsyncResponse (-> as .getContext :creq) 500))
    ;else
    (.info logger "connected now!")))


(defn write-handler
  "handle write event, as is a NginxClojureAsynSocket, sc is status code"  
  [^NginxClojureAsynSocket as, ^long sc]
  (if (not= sc NginxClojureAsynSocket/NGX_HTTP_CLOJURE_SOCKET_OK)
    (do 
      (.error logger (format ["on write error: %s" (.errorCodeToString as sc)]))
      (.close as)
      (NginxClojureRT/completeAsyncResponse (-> as .getContext :creq) 500))
    ;else
    (let [ctx (.getContext as) 
          {:keys [rc, wc,req-sent?, creq, req, buf, resp]} @ctx]
      (if req-sent? 
        (do 
          (.info logger "after request meet write again, just ignored..........")
          (.shutdown as NginxClojureAsynSocket/NGX_HTTP_CLOJURE_SOCKET_SHUTDOWN_SOFT_WRITE))
        
        ;else
        (loop [wc wc]
          (let [n (.write as req wc (- (alength req) wc))]
            (if (< n 0) 
              (when (not= n NginxClojureAsynSocket/NGX_HTTP_CLOJURE_SOCKET_ERR_AGAIN)
                (.error logger (format "write error :%s" (.errorCodeToString as sc)))
                (.close as)
                (NginxClojureRT/completeAsyncResponse creq 500))
              (do 
                (swap! ctx update-in [:wc] + n)
                (let [wc (:wc @ctx)]
	                (.info logger (format "write %d, total %d" n wc))
	                (if (= wc (alength req))
	                  (do 
	                    (swap! ctx assoc :req-sent? true)
	                    (.info logger (format "fininsh write total write: %d", wc)))
	                  (recur wc)))))))))))

(defn read-handler
  "handle read event, as is a NginxClojureAsynSocket, sc is status code"
  [^NginxClojureAsynSocket as, ^long sc]
  (if (not= sc NginxClojureAsynSocket/NGX_HTTP_CLOJURE_SOCKET_OK)
    (do 
      (.error logger (format "on read error: %s" (.errorCodeToString as sc)))
      (.close as)
      (NginxClojureRT/completeAsyncResponse (-> as .getContext :creq) 500))
    ;else
    (let [ctx (.getContext as) 
          {:keys [rc, wc,req-sent?, creq, req, buf, resp]} @ctx
          buf-len (alength buf)]
      (if (false? req-sent?) 
        (.warn logger "we have not write all request!")
        ;else
        (loop [rc rc]
          (let [n (.read as buf 0 buf-len)]
            (if (< n 0) 
              (when (not= n NginxClojureAsynSocket/NGX_HTTP_CLOJURE_SOCKET_ERR_AGAIN)
                (.error logger (format "read error :%s" (.errorCodeToString as sc)))
                (.close as)
                (NginxClojureRT/completeAsyncResponse  creq 500))
              (do 
                (if (= n 0)
                  (do
                    (.info logger (format "fininsh request total read: %d" rc))
                    (.close as)
                    (NginxClojureRT/completeAsyncResponse creq 
                                                          {:status 200, 
                                                           :headers {:content-type "text/html"}
                                                           ;just for test not for good performance and right behavior for a http proxy
                                                           :body (ByteArrayInputStream. (.toByteArray resp) )}))
                  (do 
	                  (swap! ctx update-in [:rc] + n)
	                  (.write resp buf 0 (int n))
	                  (let [rc (:rc @ctx)]
	                    (.info logger (format "read %d, total %d", n, rc))
	                    (recur rc))))))))))))

(defn release-handler
  "handle release event, as is a NginxClojureAsynSocket, sc is status code"
  [^NginxClojureAsynSocket as, ^long sc]
  (.info logger (format "on released %d", sc)))

(defn async-socket-example-handler [^LazyRequestMap req]
  (let [;request to nginx (downstream client request)
        creq (.nativeRequest req)
        ;user defined context for attach with a NginxClojureAsynSocket
        ctx (atom {:rc 0, ;read count 
                   :wc 0, ;write count
                   :req-sent? false, ;have sent request
                   :creq creq
                   :req (.getBytes (str "GET /apache/httpcomponents/httpclient/RELEASE_NOTES-4.3.x.txt HTTP/1.1\r\n"
                                        "User-Agent: curl/7.32.0\r\n" 
                                        "Host: mirror.bit.edu.cn\r\n" 
                                        "Accept: */*\r\n" 
                                        "Connection: close\r\n\r\n"))
                   :buf (byte-array 4096)
                   :resp (ByteArrayOutputStream.)})
        as (NginxClojureAsynSocket. 
             (fn [as, type, sc]
								(case type 
										"connect" (connect-handler as sc)
											"read"  (read-handler as sc)
											"write" (write-handler as sc)
											"release" (release-handler as sc))
         ))]
    (.setContext as ctx)
    (.connect as "mirror.bit.edu.cn:80")
    ;tell nginx clojure our work isn't done.
    NginxClojureRT/ASYNC_TAG)
  )