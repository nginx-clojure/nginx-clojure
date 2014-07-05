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
        )
  (:require [compojure.route :as route])
  (:require [ring.util.codec :as codec])
  (:import [ring.middleware.session.memory.MemoryStore]))

(def my-session-store (cookie-store {:key "my-secrect-key!!"}))

(defn- echo-handler [r]
  {:status 200
   :headers {"rmap" (pr-str (dissoc r :body))}
   :body "ok"})

(defn session-handler [{session :session, {user "user"} :params }]
  (let [user   (or user (:user session "guest"))
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
  (GET "/not-found" [] (route/not-found "<h1>Page not found</h1>")))


