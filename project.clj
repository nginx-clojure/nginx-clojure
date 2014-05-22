(defproject nginx-clojure "0.2.2"
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
  :java-source-paths ["src/java"]
  :javac-options ["-target" "1.6" "-source" "1.6" "-g" "-nowarn"]
  ;; Directory in which to place AOT-compiled files. Including %s will
  ;; splice the :target-path into this value.
  :compile-path "target/classes"
  ;; Leave the contents of :source-paths out of jars (for AOT projects).
  :omit-source false
  :jar-exclusions [#"^test" #"\.java$" #"Test.*class$" #".*for_test.clj$"]
  :uberjar-exclusions [#"^test" #"\.java$"]
  :manifest {"Premain-Class" "nginx.clojure.wave.JavaAgent"
             "Can-Redefine-Classes" "true"
             "Can-Retransform-Classes" "true"
             }
  :profiles {:dev {:dependencies [;only for test usage
                                  [ring/ring-core "1.2.1"]
                                  [compojure "1.1.6"]
                                  [clj-http "0.7.8"]
                                  [junit/junit "4.11"]
                                  [org.clojure/java.jdbc "0.3.3"]
                                  [mysql/mysql-connector-java "5.1.30"]
                                  ;for test file upload with ring-core which need it
                                  [javax.servlet/servlet-api "2.5"]
                                  ]}
             :unittest {
                    :jvm-opts ["-javaagent:target/nginx-clojure-0.2.2.jar=mb"
                               "-Dnginx.clojure.wave.udfs=pure-clj.txt,compojure.txt,compojure-http-clj.txt"]
                    :java-source-paths ["test/java" "test/clojure"]
                    :test-paths ["src/test/clojure"]
                    :source-paths ["test/clojure" "test/java" "test/nginx-working-dir/coroutine-udfs"]
                    :junit ["test/java"]
                    :compile-path "target/testclasses"
                    :dependencies [
                                  [ring/ring-core "1.2.1"]
                                  [compojure "1.1.6"]
                                  [clj-http "0.7.8"]
                                  [junit/junit "4.11"]
                                  [org.clojure/java.jdbc "0.3.3"]
                                  ;[mysql/mysql-connector-java "5.1.30"]
                                  ]
                        }
             :1.4 {:dependencies [[org.clojure/clojure "1.4.0"]]}
             :1.5 {:dependencies [[org.clojure/clojure "1.5.0"]]}
             })