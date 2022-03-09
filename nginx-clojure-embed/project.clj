(defproject nginx-clojure/nginx-clojure-embed "0.5.3"
  :description "Embeding Nginx-Clojure into a standard clojure/java/groovy app without additional Nginx process"
  :url "https://github.com/nginx-clojure/nginx-clojure/tree/master/nginx-clojure-embed"
  :license {:name "BSD 3-Clause license"
            :url "http://opensource.org/licenses/BSD-3-Clause"}
  :plugins []
  :dependencies [
                 [nginx-clojure/nginx-clojure "0.5.3"]
                 ]
  :source-paths ["src/clojure"]
  :java-source-paths ["src/java"]
  :resource-paths ["res" "src/java"]
  :target-path "target/"
  :jar-exclusions [#"^test" #"Test.*class$" #".*for_test.clj$"]
  :javac-options ["-target" "1.8" "-source" "1.8" "-g" "-nowarn"]
  :test-paths ["test/clojure"]
  :profiles {
             :provided {
                        :dependencies [
                                  [org.clojure/clojure "1.9.0"]]
                        }
             :dev {:dependencies [;only for test / compile usage
                                  [ring/ring-core "1.7.1"]
                                  [compojure "1.1.6"]
                                  [clj-http "0.7.8"]
                                  [stylefruits/gniazdo "1.1.2"]
                                  [junit/junit "4.13.1"]
                                  ]}} 
  )
