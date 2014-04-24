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
(defn println [& args]
  (.info tlog (first args) (into-array  Object (rest args))))


(defn do-simple-selfresume [selfresume]
  (println "enter do-simple-response")
  (println "before yield")
;  (.printStackTrace (Exception. "debug stack trace"))
  (if selfresume
    (let [cr (Coroutine/getActiveCoroutine)]
     (future (java.lang.Thread/sleep 3000) (println "before resume") (try  (.resume cr) (catch Throwable e (.printStackTrace e))))))
  (Coroutine/yield)
  (println "after yield")
  {:status 200, :headers {"content-type" "text/plain"}, :body "Simple Response\n"})


(def db-spec 
  {:classname "com.mysql.jdbc.Driver"
   :subprotocol "mysql"
   :subname "//mysql-0:3306/nginx-clojure"
   :user "nginxclojure"
   :password "111111"})

(defroutes coroutine-socket-test-handler
  (GET "/simple-clj-http-test" [] 
       (let [{:keys [status, headers, body]} (client/get "http://mirror.bit.edu.cn/apache/httpcomponents/httpclient/RELEASE_NOTES-4.3.x.txt" {:socket-timeout 50000})]
         {:status status,  :headers (dissoc headers "transfer-encoding" "server"), :body body}))
  (GET "/simple-httpclientget" [:as req] ((SimpleHandler4TestHttpClientGetMethod.) req))
  (GET "/simple" [] 
       (let [{:keys [status,headers, body]} (do-simple-selfresume true)]
         {:status status, :headers headers :body body})
       )
  (GET "/simplefalse" [] 
     (let [{:keys [status,headers, body]} (do-simple-selfresume false)]
       {:status status, :headers headers :body body})
     )
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
