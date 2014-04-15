(ns nginx.clojure.compojure-fns-for-test
    (:import [nginx.clojure Coroutine]
           [java.util ArrayList]
           )
    (:use [ring.util.response]
      [ring.middleware.session]
      [ring.middleware.cookies]
      [ring.middleware.params]
      [ring.middleware.content-type]
      [ring.middleware.session.memory]
      [ring.middleware.session.cookie]
      [compojure.core]
      )
  (:require [compojure.route :as route]))

(defn do-simple-response []
  (println "ct enter do-simple-response")
  (println "ct before yield")
;  (.printStackTrace (Exception. "ct debug stack trace"))
  (Coroutine/yield)
  (println "ct after yield")
  {:status 200, :headers {"content-type" "text/plain"}, :body "Simple Response\n"})

(defn do-simple-selfresume []
  (println "ct enter do-simple-response")
  (println "ct before yield")
;  (.printStackTrace (Exception. "debug stack trace"))
  (let [cr (Coroutine/getActiveCoroutine)]
    (future (java.lang.Thread/sleep 3000) (println "ct before resume") (.resume cr)))
  (Coroutine/yield)
  (println "ct after yield")
  {:status 200, :headers {"content-type" "text/plain"}, :body "Simple Response\n"})

(defroutes simple-route
  (GET "/simple" [] 
       (let [{:keys [status,headers, body]} (do-simple-response)]
         {:status status, :headers headers :body body})
       )
    (GET "/simple-selfresume" [] 
       (let [{:keys [status,headers, body]} (do-simple-selfresume)]
         {:status status, :headers headers :body body})
       )
)

(defn hello [] (println "hello"))

(defn simple-handler []
  (simple-route {:uri "/simple", :scheme :http, :request-method :get, :headers {}}))

(defn simple-selfresume-handler [req]
  (simple-route {:uri "/simple-selfresume", :scheme :http, :request-method :get, :headers {}}))