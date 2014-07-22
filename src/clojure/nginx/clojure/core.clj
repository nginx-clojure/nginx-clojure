(ns nginx.clojure.core
  (:import [nginx.clojure Coroutine Stack NginxClojureRT NginxRequest])
  (:import [nginx.clojure.clj Constants]))

(defn without-coroutine 
  "wrap a handler f to a new handler which will keep away the coroutine context"
  [f]
  (fn [& args]
    (let [s (Stack/getStack)]
      (try
        (Stack/setStack nil)
        (apply f args)
        (finally (Stack/setStack s))))))

(defmacro co-pvalues
  "Returns a sequence of the values of the exprs, which are evaluated in parallel coroutines.
   If there's no coroutine support, it will turn to use thread pool to make testing with lein-ring easy.
   e.g. fetch two services in parallel:
   (let [[r1, r2] (co-pvalues (client/get \"http://page1-url\") (client/get \"http://page2-url\"))]
    ;println bodies of two remote responses
    (println (str (:body r1) \"====\\n\" (:body r2) ))
  "
  [& exprs]
  `(co-pcalls ~@(map #(list `fn [] %) exprs)))

(defn co-pcalls
  "Executes the no-arg fns in parallel coroutines, returning a  sequence of their values
   If there's no coroutine support, it will turn to use thread pool to make testing with lein-ring easy.
   e.g. fetch two services in parallel:
   (let [[r1, r2] (co-pcalls (fn[] (client/get \"http://page1-url\")) (fn[] (client/get \"http://page2-url\")))]
    ;println bodies of two remote responses
    (println (str (:body r1) \"====\\n\" (:body r2) ))
  "
  [& fns]
  (->> fns (clojure.lang.RT/seqToTypedArray Callable) NginxClojureRT/coBatchCall seq))

(defn get-ngx-var 
  "get nginx variable"
  [^NginxRequest req name]
  (NginxClojureRT/getNGXVariable (.nativeRequest req) name))

(defn set-ngx-var! 
  "set nginx variable"
  [^NginxRequest req name, val]
  (NginxClojureRT/setNGXVariable (.nativeRequest req) name val))

(def phrase-done Constants/PHRASE_DONE)
