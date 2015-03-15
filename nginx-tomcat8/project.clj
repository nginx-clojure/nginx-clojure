(defproject nginx-clojure/nginx-tomcat8 "0.1.0"
  :description "Embed Tomcat into Nginx by Nignx-Clojure Module so that Nginx can  Support Java Standard Web Applications"
  :url "https://github.com/nginx-clojure/nginx-clojure/nginx-tomcat"
  :license {:name "BSD 3-Clause license"
            :url "http://opensource.org/licenses/BSD-3-Clause"}
  :plugins [[lein-junit "1.1.7"]
            [venantius/ultra "0.1.9"]]
  :dependencies [[nginx-clojure/nginx-clojure "0.4.0"]
                 [org.apache.tomcat/tomcat-catalina "8.0.20"]
                 ]
  :source-paths ["src/clojure"]
  :java-source-paths ["src/java"]
  :jar-exclusions [#"^test" #"\.java$" #"Test.*class$" #".*for_test.clj$"]
  :javac-options ["-target" "1.7" "-source" "1.7" "-g" "-nowarn"]
  :profiles {
           :dev {:dependencies [;only for test / compile usage
                                ]}}
  )
