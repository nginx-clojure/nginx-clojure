(defproject clojure-web-example "0.1.0"
  :description "FIXME: write description"
  :url "https://github.com/nginx-clojure/nginx-clojure/tree/master/example-projects/clojure-web-example"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.9.0"] ;; v1.5.1+ is OK
                 [compojure "1.4.0"]
                 [ring/ring-defaults "0.1.2"]
                 [ring/ring-anti-forgery "1.0.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [ch.qos.logback/logback-classic "1.0.9"]
                 [org.clojure/tools.reader "0.8.1"]
                 [ring/ring-devel "1.4.0"]
                 ]
  :target-path "target/%s"
  :aot [clojure-web-example.handler]
  :uberjar-name "clojure-web-example-default.jar"
  :profiles {
             :provided {:dependencies [[nginx-clojure "0.5.1"]]}
             :dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                                  [ring-mock "0.1.5"]]}
             :embed {:dependencies 
                       [[nginx-clojure/nginx-clojure-embed "0.5.1"]]
                     :aot [clojure-web-example.embed-server]
                     :main  clojure-web-example.embed-server
                     :uberjar-name "clojure-web-example-embed.jar"
                     }
             })
