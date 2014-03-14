(defproject nginx-clojure "0.2.0"
  :description "Nginx module for clojure & java programming"
  :url "https://github.com/xfeep/nginx-clojure"
  :license {:name "BSD 3-Clause license"
            :url "http://opensource.org/licenses/BSD-3-Clause"}
  :dependencies [
                 [org.clojure/clojure "1.5.1"]
                 ]
  :plugins [[lein-junit "1.1.2"]]
  ;; CLJ source code path
  :source-paths ["src/clojure"]
  :target-path "target/"
  :global-vars {*warn-on-reflection* true
                *assert* false}
  :java-source-paths ["src/java" "test/java"]
  :test-paths ["src/test/clojure"]
  :javac-options ["-target" "1.6" "-source" "1.6" "-Xlint:-options" "-g"]
  ;; Directory in which to place AOT-compiled files. Including %s will
  ;; splice the :target-path into this value.
  :compile-path "target/classy-files"
  ;; Leave the contents of :source-paths out of jars (for AOT projects).
  :omit-source false
  :junit ["test/java"]
  :jar-exclusions [#"^test" #"\.java$" #"Test.*class$" #".*for_test.clj$"]
  :uberjar-exclusions [#"^test" #"\.java$"]
  :manifest {"Premain-Class" "nginx.clojure.wave.JavaAgent"
             "Can-Redefine-Classes" "true"
             }
  :profiles {:dev {:dependencies [;only for test usage
                                  [ring/ring-core "1.2.1"]
                                  [compojure "1.1.6"]
                                  [clj-http "0.7.8"]
                                  [junit/junit "4.11"]
                                  ]}
             :coroutine-test {:jvm-opts ["-javaagent:target/nginx-clojure-0.2.0.jar"] 
                              :dependencies [[junit/junit "4.11"]]}
             :1.4 {:dependencies [[org.clojure/clojure "1.4.0"]]}
             :1.5 {:dependencies [[org.clojure/clojure "1.5.0"]]}
             })