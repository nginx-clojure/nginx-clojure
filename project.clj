(defproject nginx-clojure/nginx-clojure "0.5.2"
  :description "Nginx module for clojure or groovy or java programming"
  :url "https://github.com/nginx-clojure/nginx-clojure"
  :license {:name "BSD 3-Clause license"
            :url "http://opensource.org/licenses/BSD-3-Clause"}
  :dependencies [
                 ]
  :plugins [[lein-junit "1.1.7"]
            [lein-javadoc "0.2.0"]
            [lein-codox "0.9.0"]
            ;[venantius/ultra "0.1.9"]
            ]
  ;; CLJ source code path
  :source-paths ["src/clojure"]
  :target-path "target/"
  :global-vars {*warn-on-reflection* true
                *assert* false}
  :java-source-paths ["src/java"]
  :javac-options ["-target" "1.8" 
                  "-source" "1.8" 
                  "-g" 
                  "-Xlint:unchecked"
                  ;;"-nowarn"
                  ]
  ;; Directory in which to place AOT-compiled files. Including %s will
  ;; splice the :target-path into this value.
  :compile-path "target/classes"
  ;; Leave the contents of :source-paths out of jars (for AOT projects).
  :omit-source false
  :jar-exclusions [#"^test" 
                   #"asm/.*\.java$" 
                   #"Test.*class$" #".*for_test.clj$"]
  :uberjar-exclusions [#"^test" #"\.java$"]
  :manifest {"Premain-Class" "nginx.clojure.wave.JavaAgent"
             "Can-Redefine-Classes" "true"
             "Can-Retransform-Classes" "true"
             }
  :javadoc-opts {
             :package-names ["nginx.clojure"]
             }
  :codox {:source-paths ["src/clojure"
                         "nginx-clojure-embed/src/clojure"]
          :project {:name "nginx-clojure", :version "0.5.0", :description "N/A"}
          :output-path "../nginx-clojure.github.io/api"
          ;:metadata {:doc/format :markdown}
          :namespaces ["nginx.clojure.core" "nginx.clojure.session" "nginx.clojure.embed"]
          :source-uri "https://github.com/nginx-clojure/nginx-clojure/blob/master/src/clojure/{classpath}#L{line}"}  
  :profiles {
             :provided {
                        :dependencies [
                                  [org.clojure/clojure "1.9.0"]
                                  [org.clojure/tools.reader "0.8.1"]]
                        }
             :dev  {:dependencies [;only for test / compile usage
                                  [org.clojure/clojure "1.9.0"]
                                  [ring/ring-core "1.7.1"]
                                  [compojure "1.1.6"]
                                  [clj-http "0.7.8"]
                                  [junit/junit "4.13.1"]
                                  [org.clojure/java.jdbc "0.3.3"]
                                  [mysql/mysql-connector-java "5.1.30"]
                                  [redis.clients/jedis "3.1.0"]
                                  ;for test file upload with ring-core which need it
                                  [javax.servlet/servlet-api "2.5"]
                                  [org.clojure/data.json "0.2.5"]
                                  [org.codehaus.jackson/jackson-mapper-asl "1.9.13"]
                                  [org.codehaus.groovy/groovy "2.5.8"]
                                  [stylefruits/gniazdo "1.1.2"]
                                  [javax.xml.bind/jaxb-api "2.3.1"]
                                  [org.clojure/tools.trace "0.7.10"]
                                  ]}
             :unittest {
                    :jvm-opts ["-javaagent:target/nginx-clojure-0.5.2.jar=mb"
                               "-Dfile.encoding=UTF-8"
                               "-Dnginx.clojure.wave.udfs=pure-clj.txt,compojure.txt,compojure-http-clj.txt,mysql-jdbc.txt,test-groovy.txt"
                               "-Xbootclasspath/a:target/nginx-clojure-0.5.2.jar"]
                    :junit-options {:fork "on"}
                    :java-source-paths ["test/java" "test/clojure"]
                    :test-paths ["src/test/clojure"]
                    :source-paths ["test/clojure" "test/java" "test/nginx-working-dir/coroutine-udfs"]
                    :junit ["test/java"]
                    :compile-path "target/testclasses"
                    :dependencies [
                                  [org.clojure/clojure "1.9.0"]
                                  [ring/ring-core "1.7.1"]
                                  [compojure "1.1.6"]
                                  [clj-http "0.7.8"]
                                  [junit/junit "4.13.1"]
                                  [org.clojure/java.jdbc "0.3.3"]
                                  [org.codehaus.jackson/jackson-mapper-asl "1.9.13"]
                                  [javax.xml.bind/jaxb-api "2.3.1"]
                                  ;[mysql/mysql-connector-java "5.1.30"]
                                  [redis.clients/jedis "3.1.0"]
                                  [org.clojure/tools.trace "0.7.10"]
                                  ]
                        }
             :cljremotetest {
                                :java-source-paths ["test/java" "test/clojure"]
                                :test-paths ["src/test/clojure"]
                                :source-paths ["test/clojure" "test/java" "test/nginx-working-dir/coroutine-udfs"]
                                :compile-path "target/testclasses"
                                :test-selectors {:default (fn [m] (and (:remote m) (not (:async m)) (not (:jdbc m))))
                                                 :async :async
                                                 :jdbc :jdbc
                                                 :no-async (fn [m] (and (:remote m) (not (:async m))))
                                                 :access-handler :access-handler
                                                 :rewrite-handler :rewrite-handler
                                                 :websocket :websocket
                                                 :keepalive :keepalive
                                                 :all :remote}
                                :dependencies [
                                              [org.clojure/clojure "1.9.0"]
                                              [ring/ring-core "1.7.1"]
                                              [compojure "1.1.6"]
                                              [clj-http "0.7.8"]
                                              [junit/junit "4.13.1"]
                                              [org.clojure/java.jdbc "0.3.3"]
                                              [org.clojure/tools.nrepl "0.2.3"]
                                              ;for test file upload with ring-core which need it
                                              [javax.servlet/servlet-api "2.5"]
                                              [org.codehaus.jackson/jackson-mapper-asl "1.9.13"]
                                              [org.clojure/data.json "0.2.5"]
                                              [stylefruits/gniazdo "1.1.2"]
                                              [javax.xml.bind/jaxb-api "2.3.1"]
                                              ;[mysql/mysql-connector-java "5.1.30"]
                                              [redis.clients/jedis "3.1.0"]
                                              [org.clojure/tools.trace "0.7.10"]
                                              ]
                                    }             
             })
