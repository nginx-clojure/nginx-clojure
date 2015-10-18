(ns nginx.clojure.asyn-channel-handlers-for-test
  (:use nginx.clojure.core)
  (:import  [nginx.clojure.logger LoggerService]
            [nginx.clojure NginxClojureRT]
            [java.nio ByteBuffer]))

(def ^LoggerService logger (NginxClojureRT/getLog))

(defn- log[fmt & ss]
  (.info logger (apply (partial format fmt) ss)))

(defn- error-handler [status {:keys [buf upstream downstream] :as pipe}]
  (log "error happend: %d, %s" status (error-str upstream status))
  (aclose! upstream)
  (if (= "sent" (get-context downstream))
    (send! downstream (error-str upstream status) true true)
    (send-response! downstream {:status 500 
                                :headers {"Content-Type" "text/html"} 
                                :body (error-str upstream status)})))

(defn- pipe-handler
  "like a proxy, it read data from upstream and send them to the downstream"
  [status {:keys [buf upstream downstream] :as pipe}]
  (log "read data %d" status)
  (let [end? (or (= status 0) (.hasRemaining buf))]
    (.flip buf)
    (set-context! downstream "sent")
    (send! downstream buf true end?)
    (.clear buf)
    (if end?
      (aclose! upstream)
      (arecv! upstream buf pipe error-handler pipe-handler))))

;;ring handler
(defn async-channel-example-handler 
  "this example get content from www.apache.org:8080 and sent it to client"
  [req]
  (let [downstream (hijack! req false)
        upstream (achannel)
        buf (ByteBuffer/allocateDirect 4096)
        pipe {:buf buf :upstream upstream :downstream downstream}
        proxy-handler (fn [status {:keys [buf upstream downstream] :as pipe}]
                        (log "we have sent the request to remote! length: %d" status)
                        (ashutdown! upstream :soft-write)
                        (arecv! upstream buf pipe error-handler pipe-handler)
                        )]
    (aset-timeout! upstream 20000 20000 20000)
    (aconnect! upstream "www.apache.org:80" upstream error-handler
               (fn [status att]
                 (.info logger "connected successfully")
                 (asend! upstream (str "GET /dist/httpcomponents/httpclient/RELEASE_NOTES-4.3.x.txt HTTP/1.1\r\n"
                                        "User-Agent: nginx-clojure/0.3.0\r\n" 
                                        "Host: www.apache.org\r\n" 
                                        "Accept: */*\r\n" 
                                        "Connection: close\r\n\r\n")
                         pipe error-handler 
                         proxy-handler)))))