(ns nginx.clojure.session
  "Shared-memory session storage among nginx worker processes."
  (:use ring.middleware.session.store)
  (:require [clojure.tools.reader.edn :as edn])
  (:import java.util.UUID)
  (:import nginx.clojure.clj.ClojureSharedHashMap))

(defn- ^String serialize [x]
  (pr-str x))

(defn- deserilize [s]
  (edn/read-string s))

(deftype SharedMemoryStore [delayed-smap]
  SessionStore
  (read-session [_ key]
    (if key
      (let [data (@delayed-smap key)]
        (if data (deserilize data)))))
  (write-session [_ key data]
    (let [key (or key (str (UUID/randomUUID)))]
      (assoc! @delayed-smap key (serialize data))
      key))
  (delete-session [_ key]
    (dissoc! @delayed-smap key)
    nil))

(defn shared-map-store 
    "Creates an shared map based session storage engine.
    `name` is a shared map name declared in the nginx.conf.
    See [shared map](http://nginx-clojure.github.io/directives.html#shared_map)"
    [name]
  (SharedMemoryStore. (delay (ClojureSharedHashMap. name))))
