(defproject nginx-clojure/nginx-tomcat8 "0.2.7"
  :description "Embed Tomcat into Nginx by Nignx-Clojure Module so that Nginx can  Support Java Standard Web Applications"
  :url "https://github.com/nginx-clojure/nginx-clojure/nginx-tomcat8"
  :license {:name "BSD 3-Clause license"
            :url "http://opensource.org/licenses/BSD-3-Clause"}
  :plugins []
  :dependencies [
                 [nginx-clojure/nginx-clojure "0.5.2"]
                 [org.apache.tomcat/tomcat-catalina "8.0.27"]
                 ]
  :source-paths ["src/clojure"]
  :java-source-paths ["src/java"]
  :jar-exclusions [#"^test" #"\.java$" #"Test.*class$" #".*for_test.clj$"]
  :javac-options ["-target" "1.7" "-source" "1.7" "-g" "-nowarn"]
  :profiles {
           :dev {:dependencies [;only for test / compile usage
                                [stylefruits/gniazdo "1.1.2"]]}}
  )
