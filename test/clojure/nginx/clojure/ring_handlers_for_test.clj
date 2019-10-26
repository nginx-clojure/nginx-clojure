(ns nginx.clojure.ring-handlers-for-test
  (:use [ring.util.response]
        [ring.middleware.session]
        [ring.middleware.cookies]
        [ring.middleware.params]
        [ring.middleware.content-type]
        [ring.middleware.session.memory]
        [ring.middleware.session.cookie]
        [ring.middleware.multipart-params]
        [compojure.core]
        [nginx.clojure.core]
        [clojure.tools.trace]
        )
  (:require [compojure.route :as route]
            [clj-http.client :as client])
  (:require [ring.util.codec :as codec])
  (:import [ring.middleware.session.memory.MemoryStore]
           [nginx.clojure NginxRequest]
           [nginx.clojure.logger LoggerService]
           [nginx.clojure NginxClojureRT]))

(def ^LoggerService logger (NginxClojureRT/getLog))

(defn- log[fmt & ss]
  (.info logger (apply (partial format fmt) ss)))

(def my-session-store (cookie-store {:key "my-secrect-key!!"}))

(defn- echo-handler [r]
  {:status 200
   :headers {"rmap" (pr-str (dissoc r :body))}
   :body "ok"})

(defn hello-ring [req]
  {:status 200, :headers {"content-type" "text/html"}, :body "Hello, Ring handler!"})

(defn session-handler [req]
  
  (let [session (:session req)
        user ((:params req) "user")
        user   (or user (:user session "guest"))
        ;_ (println session)
        ;_ (println my-session-store)
        session (assoc session :user user)]
    (-> (response (str "Welcome " user "!"))
        (content-type "text/html")
        (assoc :session session))))

(defn decode [encoded]
  (String. (codec/base64-decode encoded)))

(defn get-username-from-authorization-header [header-value]
  (if (not-empty header-value)
   (let [user-pass (second (clojure.string/split header-value #"\s+"))]
     (first (clojure.string/split (decode user-pass) #":")))
   ""))


(defn check-authorisation [context]
  (let [authorised? (= "nginx-clojure"
                      (get-username-from-authorization-header (get-in context [:request
                      :headers "authorization"])))]
    (do
      (println (format "request_authorised=%s" authorised?))
      authorised?)))

(def sse-subscribers (atom {}))
(def long-polling-subscribers (atom {}))

(def init-topics
  (delay
    (def sse-topic (build-topic! "sse-topic"))
    (def long-polling-topic (build-topic! "long-polling-topic"))
    (sub! sse-topic nil 
          (fn [m _]
            (doseq [[ch _] @sse-subscribers]
              (send! ch (str "data: " m "\r\n\r\n") true (= "finish!" m) ))))
    (sub! long-polling-topic nil
          (fn [m _]
            (doseq [[ch _] @long-polling-subscribers]
              (send-response! ch {:status 200, :headers {"content-type" "text/plain"}, :body m}))))))


(defroutes ring-compojure-test-handler
  (GET "/hello2" [] {:status 200, :headers {"content-type" "text/plain"}, :body "Hello World"})
  (GET "/hello" [] (-> (response "Hello World")
    (content-type "text/plain")))
  (GET "/redirect" [] (redirect "http://example.com"))
  (GET "/file-response" [] (file-response "small.html" {:root "testfiles"})) 
  (GET "/resource-response" [] (resource-response "small.html" {:root "public"}))
  (GET "/wrap-content-type.html" [] (wrap-content-type (fn [req] (response "Hello World")) {:mime-types {"html" "text/x-foo"}}))
  ;http://example.com/demo?x=hello&x=world, {:params {"x" ["hello", "world"]}
  (GET "/wrap-params" [] (wrap-params echo-handler))
  ;test form post
  (POST "/wrap-params" [] (wrap-params echo-handler))
  ;:cookies {"username" {:value "alice"}} ,, {"secret" {:value "foobar", :secure true, :max-age 3600}}
  (GET "/wrap-cookies" [] (wrap-cookies echo-handler))
  (GET "/authorized-service" []
       (fn [req]
         (if (check-authorisation {:request req})
           {:status 200, :headers {"content-type" "text/plain"}, :body "OK, you have authorized to see this message!"}
           {:status 401, :headers {"www-authenticate" "Basic realm=\"Secure Area\"" :body "<HTML><BODY><H1>401 Unauthorized.</H1></BODY></HTML>"}})))
  (PATCH "/json-patch" []
         (fn [req]
            {:status 200, :headers {"content-type" "text/plain"}, :body (str "Your patch succeeded! length=" (-> req :body slurp count))}))
  ;:session
  (GET "/wrap-session" [] (-> session-handler wrap-params (wrap-session {:store my-session-store}) ))
  (POST "/ring-upload" [] (wrap-multipart-params 
                            (wrap-params 
                              (fn [{params :params}] 
                                (let [{:keys [tempfile filename]} (params "myfile")]
                                  {:status 200, 
                                   :headers {"rmap" (pr-str (dissoc params "myfile")), "content-type" "text/plain"}
                                   :body (java.io.File. filename)})))))
  (GET "/not-found" [] (route/not-found "<h1>Page not found</h1>"))
  (GET "/exception" [] (throw (Exception. "my exception")))
  ;;server sent events publisher
  (GET "/sse-pub" [] 
       (fn [req]
         @init-topics
         (pub! sse-topic (:query-string req))
         {:body "OK"}))
  ;;server sent events subscriber
  (GET "/sse-sub" [] 
       (fn [^NginxRequest req]
         @init-topics
         (let [ch (hijack! req true)]
           (on-close! ch ch 
                      (fn [ch] (log "channel closed. id=%d" (.nativeRequest req))
                         (log "#%s sse-sub: onclose arg:%s, sse-subscribers=%s" process-id ch (pr-str @sse-subscribers))
                         (swap! sse-subscribers dissoc ch)))
           (swap! sse-subscribers assoc ch req)
           (send-header! ch 200 {"Content-Type", "text/event-stream"} false false)
           (send! ch "retry: 4500\r\n" true false))))
  (GET "/pub" []
       (fn [req]
         @init-topics
         (pub! long-polling-topic (:query-string req))
         {:body "OK"}))
  (GET "/sub" []
       (fn [^NginxRequest req]
         @init-topics
         (let [ch (hijack! req true)]
           (on-close! ch ch (fn [ch] 
                              (log "#%s channel closed. id=%d" process-id (.nativeRequest req))
                              (swap! long-polling-subscribers dissoc ch)))
           (swap! long-polling-subscribers assoc ch req))))
  (GET "/ws-echo" []
       (fn [^NginxRequest req]
         (let [ch (hijack! req true)]
           (if (websocket-upgrade! ch true)
             (add-listener! ch { :on-open (fn [ch] (log "uri:%s, on-open!" (:uri req)))
                                 :on-message (fn [ch msg rem?] (send! ch msg (not rem?) false))
                                 :on-close (fn [ch reason] (log "uri:%s, on-close:%s" (:uri req) reason))
                                 :on-error (fn [ch error] (log "uri:%s, on-error:%s" (:uri req)  error))
                                })
             {}))))
 (GET "/ws-remote" []
    (fn [^NginxRequest req]
      (-> req
          (hijack! true)
          (add-listener! { :on-open (fn [ch] (log "uri:%s, on-open!" (:uri req)))
                           :on-message (fn [ch msg rem?] 
                                         (send! ch (:body 
                                                     (client/get msg {:socket-timeout 50000})) true false)
;                                          (send! ch (clojure.string/join (for [i (range 4708)] 'a')) true false)
                                         )
                           :on-close (fn [ch reason] (log "uri:%s, on-close:%s" (:uri req) reason))
                           :on-error (fn [ch error] (log "uri:%s, on-error:%s" (:uri req)  error))
                          })))))


;(trace-ns compojure.core)
;(trace-ns compojure.route)
;(trace-ns ring.util.response)
;(trace-ns ring.middleware.session)
;(trace-ns ring.middleware.cookies)
;(trace-ns ring.middleware.params)
;(trace-ns ring.middleware.content-type)
;(trace-ns ring.middleware.session.memory)
;(trace-ns ring.middleware.session.cookie)
;(trace-ns ring.middleware.multipart-params)
;(trace-ns compojure.core)
;(trace-vars nginx.clojure.ring-handlers-for-test/ring-compojure-test-handler)

