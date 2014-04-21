(ns nginx.clojure.core
  (:import [nginx.clojure Coroutine Stack]))

(defn without-coroutine [f]
  "wrap a handler f to a new handler which will keep away the coroutine context"
  (fn [& args]
    (let [s (Stack/getStack)]
      (try
        (Stack/setStack nil)
        (apply f args)
        (finally (Stack/setStack s))))))

