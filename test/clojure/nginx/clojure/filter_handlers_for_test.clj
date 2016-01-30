(ns nginx.clojure.filter-handlers-for-test
  (:use [nginx.clojure.core])
  (:require  [clj-http.client :as client]))

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
  (let [resp (client/get "http://www.apache.org/dist/httpcomponents/httpclient/RELEASE_NOTES-4.3.x.txt" {:socket-timeout 50000})
             body (:body resp)]
    (assoc! response-headers "remote-content-length" (.length body))
    phase-done))

(defn uppercase-filter [request body-chunk last?]
  (let [upper-body (.toUpperCase body-chunk)]
      (if last? {:status 200 :body upper-body}
        {:body upper-body})))