(ns nginx.clojure.log_handlers_for_test
  (:use [nginx.clojure.core]))

(defn simple-log-handler
  [r]
    (spit "logs/SimpleLogHandler.log" 
          (str (get-ngx-var r "remote_addr") " - "
               (get-ngx-var r "remote_user" "x") " "
               (get-ngx-var r "time_local") " "
               (get-ngx-var r "request") " "
               (get-ngx-var r "status") " "
               (get-ngx-var r "body_bytes_sent") " "
               (get-ngx-var r "http_referer" "x") " "
               (get-ngx-var r "http_user_agent") " "
                "\n")
          :append true ))

;;; make variables prefetched to access them at non-main thread
(def simple-log-handler (with-meta simple-log-handler {"variablesNeedPrefetch" 
                                        ["remote_addr", "remote_user", "time_local", "request", 
                                         "status", "body_bytes_sent", "http_referer", "http_user_agent"]}))


  
