(ns nginx.clojure.filter-handlers-for-test
  (:use [nginx.clojure.core])
  (:import  [nginx.clojure.logger LoggerService]
            [nginx.clojure NginxClojureRT])
  (:require  [clj-http.client :as client]))

(def ^LoggerService logger (NginxClojureRT/getLog))

(defn add-more-headers [status request response-headers]
  (assoc!  response-headers "Xfeep-Header"  "Hello!") 
   phase-done)

(defn remove-and-add-more-headers [status request response-headers]
  (dissoc!  response-headers "Content-Type") 
  (assoc!  response-headers "Content-Type"  "text/html")
  (assoc!  response-headers "Xfeep-Header"  "Hello2!")
  (assoc!  response-headers "Server" "My-Test-Server") 
  phase-done)

(defn exception-in-header-filter [status request response-headers]
  (throw (RuntimeException. "Hello, exception in header filter!")))

(defn access-remote-header-filter [status request response-headers]
  (let [resp (client/get "https://www.apache.org/dist/httpcomponents/httpclient/RELEASE_NOTES-4.3.x.txt" {:socket-timeout 50000})
             body (:body resp)]
    (assoc! response-headers "remote-content-length" (.length body))
    phase-done))

(defn uppercase-filter [request body-chunk last?]
  (let [upper-body (.toUpperCase body-chunk)]
      (if last? {:status 200 :body upper-body}
        {:body upper-body})))

(def body-map (atom {}))

(defn handle-whole-body [body]
  (.toUpperCase body))

(defn accumulated-body-filter! [req,chunk,last?]
  (.info logger chunk)
  (let [nr (.nativeRequest req)]
    (swap! body-map update-in [nr] (fnil #(.append % chunk) (StringBuilder.)))
      (if last?
        (let [body (@body-map nr)]
          (swap! body-map dissoc nr)
          {:status 200, :body (handle-whole-body (.toString body)) })
        ;;else
        {:body ""})))
