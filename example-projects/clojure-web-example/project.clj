(defproject clojure-web-example "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.7.0"] ;; v1.5.1+ is OK
                 [compojure "1.4.0"]
                 [ring/ring-defaults "0.1.2"]
                 [ring/ring-anti-forgery "1.0.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [ch.qos.logback/logback-classic "1.0.9"]
                 [nginx-clojure "0.4.2"]]
  :plugins [[lein-ring "0.8.13"]]
  :ring {:handler clojure-web-example.handler/app}
  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring-mock "0.1.5"]
                        [ring/ring-devel "1.4.0"]
                        ;;; embeded nginx-clojure is for debug/test usage
                        [nginx-clojure/nginx-clojure-embed "0.4.2"]]}}
  :main ^:skip-aot clojure-web-example.handler)
