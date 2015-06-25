(ns nginx.clojure.tomcat8.example-app-test
   (:use [clojure.test])
   (:require [clj-http.client :as client]
             ;[clojure.data.json :as json]
             [gniazdo.core :as ws]
             ;[clojure.edn :as edn]
             )
   (:import [java.io BufferedReader StringReader]))

(def ^:dynamic *host* "localhost")
(def ^:dynamic *tomcat-host* "localhost")
(def ^:dynamic *tomcat-port* 8180)
(def ^:dynamic *port* "8080")
(def ^:dynamic *debug* false)

(defn debug-println [& args]
  (when (true? *debug*)
      (apply println args)))

(defn format-body [r]
  (-> r (:body) (clojure.string/replace (re-pattern (str *tomcat-host* ":" *tomcat-port*)) (str *host* ":" *port*))))

(deftest ^{:remote true} test-servlet-basic
  (let [base (str "http://" *host* ":" *port* "/examples/servlets/servlet/")
        tbase (str "http://" *tomcat-host* ":" *tomcat-port* "/examples/servlets/servlet/")]
    (doseq [test-topic ["HelloWorldExample"
                        "RequestInfoExample"
                        "RequestHeaderExample"
                        "RequestParamExample"
                        "RequestParamExample?firstname=tomcat&lastname=jack"]]
       (testing test-topic
         (let [r (client/get (str base test-topic) {:coerce :unexceptional})
               tr (client/get (str tbase test-topic) {:coerce :unexceptional})
               h (:headers r)]
           (debug-println r)
           (debug-println "=====================" test-topic "=========================")
           (is (= 200 (:status r)))
           (is (= (format-body r) (:body r)))))
      )
    )
)


(deftest ^{:remote true} test-jsp-basic
  (let [base (str "http://" *host* ":" *port* "/examples/jsp/jsp2/el/")
        tbase (str "http://" *tomcat-host* ":" *tomcat-port* "/examples/jsp/jsp2/el/")]
    (doseq [test-topic ["basic-arithmetic.jsp"
                        "basic-comparisons.jsp"
                        "implicit-objects.jsp?foo=bar"
                        "functions.jsp?foo=JSP+2.0"
                        "composite.jsp"]]
       (testing test-topic
         (let [r (client/get (str base test-topic) {:coerce :unexceptional})
               tr (client/get (str tbase test-topic) {:coerce :unexceptional})
               h (:headers r)]
           (debug-println r)
           (debug-println "=====================" test-topic "=========================")
           (is (= 200 (:status r)))
           (is (= (format-body r) (:body r)))))
      )
    )
)

(deftest ^{:remote true} test-websocket-basic
  (let [base (str "ws://" *host* ":" *port* "/examples/websocket/echoProgrammatic")
        test-topic "/websocket/echoProgrammatic"]
       (testing test-topic
         (let [
               msg "hello, nginx-clojure & tomcat & websocket!"
               result (promise)
               ws-client (ws/connect base
                                     :on-receive #(deliver result %))
               ]
           (debug-println "=====================" test-topic "=========================")
           (ws/send-msg ws-client msg)
           (is (= msg @result))))
    )
)
