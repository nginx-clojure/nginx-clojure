(ns nginx.clojure.coroutine-socket-handlers-for-test  
  (:use [ring.util.response]
        [ring.middleware.session]
        [ring.middleware.cookies]
        [ring.middleware.params]
        [ring.middleware.content-type]
        [ring.middleware.session.memory]
        [ring.middleware.session.cookie]
        ;sadly ring.middleware.multipart-params is dependent on servlet api so we must comment it
        ;[ring.middleware.multipart-params]
        [compojure.core]
        [nginx.clojure.core]
        )
  (:require [compojure.route :as route]
            [clj-http.client :as client]
            [clojure.java.jdbc :as jdbc])
  (:import [ring.middleware.session.memory.MemoryStore]
           [nginx.clojure.net SimpleHandler4TestHttpClientGetMethod]
           [nginx.clojure Coroutine]
           [nginx.clojure.logger TinyLogService]))



(def tlog (TinyLogService/createDefaultTinyLogService))
;
(defn tprintln [& args]
  (.info tlog (first args) (into-array  Object (rest args))))


(defn do-simple-selfresume [selfresume]
  (tprintln "enter do-simple-response")
  (tprintln "before yield")
;  (.printStackTrace (Exception. "debug stack trace"))
  (if selfresume
    (let [cr (Coroutine/getActiveCoroutine)]
     (future (java.lang.Thread/sleep 3000) (tprintln "before resume") (try  (.resume cr) (catch Throwable e (.printStackTrace e))))))
  (Coroutine/yield)
  (tprintln "after yield")
  {:status 200, :headers {"content-type" "text/plain"}, :body "Simple Response\n"})


(def db-spec 
  {:classname "com.mysql.jdbc.Driver"
   :subprotocol "mysql"
   :subname "//mysql-0:3306/nctest"
   :user "nginxclojure"
   :password "111111"})

(defroutes coroutine-socket-test-handler
  (GET "/simple-clj-http-test" [] 
       (let [{:keys [status, headers, body]} (client/get "https://www.apache.org/dist/httpcomponents/httpclient/RELEASE_NOTES-4.3.x.txt" {:socket-timeout 50000})]
         (println headers)
         (println (.length body))
         {:status status,  :headers (dissoc headers "transfer-encoding" "server" "content-length" "connection" "etag"), :body body}))
  (GET "/simple-clj-https-test" [] 
       (let [{:keys [status, headers, body]} (client/get "https://www.apache.org/dist/httpcomponents/httpclient/RELEASE_NOTES-4.3.x.txt" {:socket-timeout 50000 :insecure? true})]
         (println headers)
         (println (.length body))
         {:status status,  :headers (dissoc headers "transfer-encoding" "server" "content-length" "connection" "etag"), :body body}))  
  (GET "/" [] {:status 200, :headers {"content-type" "text/plain"}, :body "hello"})
  (GET "/simple-httpclientget" [:as req] (let [[s h b] (.invoke (SimpleHandler4TestHttpClientGetMethod.) {})] {:status s :headers h :body b}))
  (GET "/simple" [] 
       (let [{:keys [status,headers, body]} (do-simple-selfresume true)]
         {:status status, :headers headers :body body})
       )
  (GET "/simplefalse" [] 
     (let [{:keys [status,headers, body]} (do-simple-selfresume false)]
       {:status status, :headers headers :body body})
     )
  (GET "/fetch-two-pages" []
       (let [[r1 r2] (co-pvalues 
                       (client/get "https://www.apache.org/dist/httpcomponents/httpclient/KEYS" {:socket-timeout 10000})
                       (client/get "https://www.apache.org/dist/httpcomponents/httpcore/KEYS" {:socket-timeout 10000}))]
         {:status 200, 
          :headers {"content-type" "text/html"}, 
          :body (str (:body r1) "\n==========================\n" (:body r2)) }))
  ;this is only call by junit test
  (GET "/simple2" [] 
       (let [{:keys [status,headers, body]} (do-simple-selfresume false)]
         {:status status, :headers headers :body body})
       )
  (GET "/mysql-create" []
       (jdbc/db-do-commands db-spec
                     (jdbc/create-table-ddl :language
                                            [:name "varchar(32)"]
                                            [:rank "varchar(32)"]))
       {:status 200, :headers {"content-type" "text/plain"}, :body "created!"})
  (GET "/mysql-drop" []
       (jdbc/db-do-commands db-spec
                            (jdbc/drop-table-ddl :language))
       {:status 200, :headers {"content-type" "text/plain"}, :body "dropped!"})
  (PUT "/mysql-insert" [name rank]
       (jdbc/insert! db-spec :language {:name name :rank rank})
       {:status 200, :headers {"content-type" "text/plain"}, :body "inserted!"})
  (GET "/mysql-query/:name" [name]
       (let [rows (jdbc/query db-spec ["select * from language where name = ?" name])]
         {:status 200, :headers {"content-type" "text/plain"}, :body (pr-str rows) })))

(def coroutine-socket-test-handler (wrap-params coroutine-socket-test-handler))

(defn simple-handler [req]
  (coroutine-socket-test-handler {:uri "/simple2", :scheme :http, :request-method :get, :headers {}}))
