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
            [clj-http.client :as client])
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
  (.printStackTrace (Exception. "debug stack trace"))
  (if selfresume
    (let [cr (Coroutine/getActiveCoroutine)]
     (future (java.lang.Thread/sleep 3000) (println "before resume") (try  (.resume cr) (catch Throwable e (.printStackTrace e))))))
  (Coroutine/yield)
  (println "after yield")
  {:status 200, :headers {"content-type" "text/plain"}, :body "Simple Response\n"})


(defroutes coroutine-socket-test-handler
  (GET "/simple-clj-http-test" [] 
       (let [{:keys [status, body]} (client/get "http://cn.bing.com")]
         {:status status, :body body}))
  (GET "/simple-httpclientget" [req] ((SimpleHandler4TestHttpClientGetMethod.) req))
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
       ))

(defn simple-handler [req]
  (coroutine-socket-test-handler {:uri "/simple2", :scheme :http, :request-method :get, :headers {}}))
