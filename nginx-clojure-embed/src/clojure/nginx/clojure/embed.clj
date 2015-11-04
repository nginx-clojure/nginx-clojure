(ns nginx.clojure.embed
  (:import [nginx.clojure.embed NginxEmbedServer]))

(def ^:dynamic *nginx-work-dir* nil)

(defn run-server
  "Starts an embeded nginx server where nginx-clojure module has been built into, e.g.
   (1) Starts it with ring handler and an options map
    (run-server my-app {:port 8080})

   (2) Starts it with a nginx.conf file
    (run-server \"/my-dir/nginx.conf\")

   (3) Starts it with a given work dir
    (binding [*nginx-work-dir* my-work-dir]
      (run-server ...))
"
  ([handler options]
   (let [server (NginxEmbedServer/getServer) 
         opts (java.util.HashMap.)]
     (when *nginx-work-dir* (.setWorkDir server *nginx-work-dir*))
     (def default-handler handler)
     (def default-jvm-init-handler (:jvm-init-handler options (fn [_] nil)))
     (doseq [[k v] (dissoc options :jvm-init-handler)]
       (.put opts (name k) (str v)))
     (.put opts "jvm_handler_type" "clojure")
     (.put opts "jvm-init-handler-name" "nginx.clojure.embed/default-jvm-init-handler")
     (.put opts "content-handler-type" "clojure")
     (.start server "nginx.clojure.embed/default-handler" opts)))
  ([nginx-conf]
    (let [server (NginxEmbedServer/getServer)]
      (when *nginx-work-dir* (.setWorkDir server *nginx-work-dir*))
      (.start server nginx-conf)))
  )

(defn stop-server []
  (-> (NginxEmbedServer/getServer) (.stop)))

