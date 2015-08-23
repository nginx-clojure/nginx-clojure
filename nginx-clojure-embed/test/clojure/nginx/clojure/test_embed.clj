(ns nginx.clojure.test-embed
  (:use [nginx.clojure.core]
        [nginx.clojure.embed]
        [clojure.test])
  (:require [compojure.core :as comp :refer (defroutes GET POST)])
  (:require [clj-http.client :as client]
            [gniazdo.core :as ws]))

(defroutes my-app
  (GET "/hello" req {:status 200 :body "Hello, Nginx & Clojure!"})
  (GET "/websocket-echo" req 
       (let [ch (hijack! req true)]
          (when (websocket-upgrade! ch true)
            (add-listener! 
             ch 
             {:on-open (fn [ch] (println "on-open!"))
              :on-message (fn [ch msg rem?] 
                            (println "on-message:" msg) 
                            (send! ch msg (not rem?) false))
              :on-close (fn [ch reason] (println "on-close:" reason))
              :on-error (fn [ch error] (println "on-error:" error))
              }))))
  (GET "/websocket-agg-echo" req
       (let [ch (hijack! req true)]
          (when (websocket-upgrade! ch true)
            (add-aggregated-listener!
             ch
             10240
             {:on-open (fn [ch] (println "on-open!"))
              :on-message (fn [ch msg] 
                            (println "on-message:" msg) 
                            (send! ch msg true false))
              :on-close (fn [ch reason] (println "on-close:" reason))
              :on-error (fn [ch error] (println "on-error:" error))
              })))))


(deftest test-embed-server-default-mode
  (run-server my-app {:port 8080, :max-threads 0})
  (is (= "Hello, Nginx & Clojure!" (:body (client/get "http://localhost:8080/hello"))))
  (let [ws-response (promise)
        ws-client (ws/connect
                    "ws://localhost:8080/websocket-echo"
                    :on-error #(deliver ws-response %)
                    :on-close (fn [c r] (deliver ws-response (str c ":" r)))
                    :on-receive #(deliver ws-response %))
        msg "Hello, Nginx & Clojure!"]
    (ws/send-msg ws-client msg)
    (is (= msg @ws-response))
    (ws/close ws-client))
  (let [ws-response (promise)
        ws-client (ws/connect
                    "ws://localhost:8080/websocket-agg-echo"
                    :on-error #(deliver ws-response %)
                    :on-close (fn [c r] (deliver ws-response (str c ":" r)))
                    :on-receive #(deliver ws-response %))
        msg (clojure.string/join "" (repeat 10240 "a"))]
    (ws/send-msg ws-client msg)
    (is (= msg @ws-response))
    (ws/close ws-client))
  (let [ws-response (promise)
      ws-client (ws/connect
                  "ws://localhost:8080/websocket-agg-echo"
                  :on-error #(deliver ws-response %)
                  :on-close (fn [c r] (deliver ws-response (str c ":" r)))
                  :on-receive #(deliver ws-response %))
      msg (clojure.string/join "" (repeat 10241 "a"))]
  (ws/send-msg ws-client msg)
  (is (= "1000:" @ws-response))
  (ws/close ws-client))
  (stop-server)
  )


(deftest test-embed-server-thread-mode
  (run-server my-app {:port 8080, :max-threads 8})
  (is (= "Hello, Nginx & Clojure!" (:body (client/get "http://localhost:8080/hello"))))
  (let [ws-response (promise)
        ws-client (ws/connect
                    "ws://localhost:8080/websocket-echo"
                    :on-error #(deliver ws-response %)
                    :on-close (fn [c r] (deliver ws-response (str c ":" r)))
                    :on-receive #(deliver ws-response %))
        msg "Hello, Nginx & Clojure!"]
    (ws/send-msg ws-client msg)
    (is (= msg @ws-response))
    (ws/close ws-client))
  (stop-server)
  )



