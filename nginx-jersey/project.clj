(defproject nginx-clojure/nginx-jersey "0.1.7"
  :description "Intergrate Jersey into Nginx by Nignx-Clojure Module so that 
                Nginx can Support Java standard RESTful Web Services (JAX-RS)"
  :url "https://github.com/nginx-clojure/nginx-clojure/nginx-jersey"
  :license {:name "BSD 3-Clause license"
            :url "http://opensource.org/licenses/BSD-3-Clause"}
  :dependencies [
                 [javax.ws.rs/javax.ws.rs-api "2.0.1"]
                 [nginx-clojure/nginx-clojure "0.5.2"]
                 ]
  :source-paths ["src/clojure"]
  :java-source-paths ["src/java"]
  :jar-exclusions [#"^test" #"\.java$" #"Test.*class$" #".*for_test.clj$"]
  :javac-options ["-target" "1.7" "-source" "1.7" "-g" "-nowarn"]
  :profiles {
           :provided {
                        :dependencies [
                                  [org.glassfish.jersey.core/jersey-common "2.17"]
                                  ;[org.glassfish.jersey.media/jersey-media-json-jackson "2.17"]
                                  [org.glassfish.jersey.core/jersey-server "2.17"]]
                      }
           :dev {:dependencies [;only for test / compile usage
                                [junit/junit "4.11"]
                                ]}}
  )
