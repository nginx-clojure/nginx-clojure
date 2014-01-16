(ns nginx.clojure.test-all
   (:use [clojure.test])
   (:require [clj-http.client :as client]
             [clojure.edn :as edn]))

(def ^:dynamic *host* "localhost")
(def ^:dynamic *port* "8080")
(def ^:dynamic *debug* false)

(defn debug-println [& args]
  (when (true? *debug*)
      (apply println args)))

(deftest test-naive-simple
  (testing "hello clojure"
           (let [r (client/get (str "http://" *host* ":" *port* "/clojure") {:coerce :unexceptional})
                 h (:headers r)]
             (debug-println r)
             (debug-println "=================hello clojure end=============================")
             (is (= 200 (:status r)))
             (is (= "Hello Clojure & Nginx!" (:body r)))
             (is (= "text/plain" (h "content-type")))
             (is (= "22" (h "content-length")))
             (is (.startsWith (h "server") "nginx-clojure")))))

(deftest test-headers
  (testing "simple headers"
           (let [r (client/get (str "http://" *host* ":" *port* "/headers") {:coerce :unexceptional})
                 h (:headers r)
                 b (-> r :body (edn/read-string))]
             (debug-println r)
             (debug-println "===============simple headers end =============================")
             (is (= 200 (:status r)))
             (is (= "e29b7ffb8a5325de60aed2d46a9d150b" (h "etag")))
             (is (= ["no-store" "no-cache"] (h "cache-control")))
             (is (.startsWith (h "server") "nginx-clojure"))
             (is (= "http" (b :scheme)))
             (is (= "/headers" (b :uri)))
             (is (= *port* (b :server-port)))))
  
  (testing "cookie & user defined headers"
           (let [r (client/get (str "http://" *host* ":" *port* "/headers") {:coerce :unexceptional, :headers {"my-header" "mytest"}, :cookies {"tc1" {:value "tc1value"}, "tc2" {:value "tc2value"} } })
                 h (:headers r)
                 b (-> r :body (edn/read-string))]
             (debug-println r)
             (debug-println "===============cookie & user defined headers end=============================")
             (is (= 200 (:status r)))
             (is (= "e29b7ffb8a5325de60aed2d46a9d150b" (h "etag")))
             (is (= ["no-store" "no-cache"] (h "cache-control")))
             (is (.startsWith (h "server") "nginx-clojure"))
             (is (= "http" (b :scheme)))
             (is (= "/headers" (b :uri)))
             (is (= *port* (b :server-port)))
             (is (= "mytest" (b :my-header)))
             (is (= "tc1=tc1value;tc2=tc2value" (b :cookie)))))
  
    (testing "query string & character-encoding"
           (let [r (client/get (str "http://" *host* ":" *port* "/headers?my=test") {:coerce :unexceptional, :headers {"my-header" "mytest" "Content-Type" "text/plain; charset=utf-8"}, :cookies {"tc1" {:value "tc1value"}, "tc2" {:value "tc2value"} } })
                 h (:headers r)
                 b (-> r :body (edn/read-string))]
             (debug-println r)
             (debug-println "===============query string & character-encoding =============================")
             (is (= 200 (:status r)))
             (is (= "e29b7ffb8a5325de60aed2d46a9d150b" (h "etag")))
             (is (= ["no-store" "no-cache"] (h "cache-control")))
             (is (.startsWith (h "server") "nginx-clojure"))
             (is (= "http" (b :scheme)))
             (is (= "/headers" (b :uri)))
             (is (= *port* (b :server-port)))
             (is (= "mytest" (b :my-header)))
             (is (= "tc1=tc1value;tc2=tc2value" (b :cookie)))
             (is (= "my=test" (b :query-string)))
             (is (= "utf-8" (b :character-encoding))))))

(deftest test-form
  (testing "form method=get"
           (let [r (client/get (str "http://" *host* ":" *port* "/form") {:coerce :unexceptional, :query-params {:foo "bar"}})
                 h (:headers r)
                 b (-> r :body (edn/read-string))]
             (debug-println r)
             (debug-println "=================form method=get end=============================")
             (is (= 200 (:status r)))
             (is (= "foo=bar" (b :query-string)))
             (is (nil? (b :form-body-str)))
             ))
  (testing "form method=post"
           (let [r (client/post (str "http://" *host* ":" *port* "/form") {:coerce :unexceptional, :form-params {:foo "bar"}})
                 h (:headers r)
                 b (-> r :body (edn/read-string))]
             (debug-println r)
             (debug-println "=================form method=post end=============================")
             (is (= 200 (:status r)))
             (is (= "foo=bar" (b :form-body-str)))
             (is (= "nginx.clojure.NativeInputStream" (b :form-body-type)))
             (is (nil? (b :query-string)))
             ))
  (testing "form multipart-formdata"
           (let [r (client/post (str "http://" *host* ":" *port* "/echoUploadfile") {:coerce :unexceptional, :multipart [{:name "mytoken", :content "123456"},
                                                                                                 {:name "myf", :content (clojure.java.io/file "test/nginx-working-dir/post-test-data")}
                                                                                                 ]})
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================form multipart-formdata=============================")
             (is (= 200 (:status r)))
             (is (< 0 (.indexOf b "name=\"mytoken\"")))
             (is (< 0 (.indexOf b "123456")))
             (is (< 0 (.indexOf b "name=\"myf\"")))
             (is (< 0 (.indexOf b "Apache HTTP Server Version 2.4")))
             (is (< 0 (.indexOf b "Modules | Directives | FAQ | Glossary | Sitemap")))
             ))
  )


(deftest test-file
  (testing "static file without gzip"
           (let [r (client/get (str "http://" *host* ":" *port* "/testfiles/small.html") {:coerce :unexceptional, :decompress-body false})
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================static file (no gzip) end =============================")
             (is (= 200 (:status r)))
             (is (= "680" (h "content-length")))))
    (testing "static file with gzip"
           ;clj-http will auto use Accept-Encoding	gzip, deflate
           (let [r (client/get (str "http://" *host* ":" *port* "/testfiles/small.html"))
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================static file (with gzip) end=============================")
             (is (= 200 (:status r)))
             (is (= "gzip" (:orig-content-encoding r)))
             (is (= 680 (count (r :body))))))
    
   (testing "static file with range operation"
      ;clj-http will auto use Accept-Encoding	gzip, deflate
      (let [r (client/get (str "http://" *host* ":" *port* "/testfiles/small.html")  {:coerce :unexceptional, :decompress-body false, :headers {"Range" "bytes=0-128"}})
            h (:headers r)
            b (r :body)]
        (debug-println r)
        (debug-println "=================static file (with range 0-128) end=============================")
        ;206 Partial Content
        (is (= 206 (:status r)))
        (is (= 129 (count (r :body))))))
    
  )

(deftest test-seq
  (testing "seq include String &  File without gzip"
           (let [r (client/get (str "http://" *host* ":" *port* "/testSeq") {:coerce :unexceptional, :decompress-body false})
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================seq include String &  File without gzip=============================")
             (is (= 200 (:status r)))
             (is (= (str (+ 680 (count "header line\n"))) (h "content-length")))))
  )


(deftest test-inputstream
  (testing "inputstream without gzip"
           (let [r (client/get (str "http://" *host* ":" *port* "/testInputStream") {:coerce :unexceptional, :decompress-body false})
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================inputstream (no gzip) end =============================")
             (is (= 200 (:status r)))
             (is (= "680" (h "content-length")))))
    (testing "inputstream with gzip"
           ;clj-http will auto use Accept-Encoding	gzip, deflate
           (let [r (client/get (str "http://" *host* ":" *port* "/testInputStream"))
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================inputstream (with gzip) end=============================")
             (is (= 200 (:status r)))
             (is (= "gzip" (:orig-content-encoding r)))
             (is (= 680 (count (r :body))))))
  )

;eg. (concurrent-run 10 (run-tests 'nginx.clojure.test-all))
(defmacro concurrent-run 
  [n, form]
  (list 'apply 'pcalls (list 'repeat n (list 'fn [] form)) ))

;(binding [*host* "macosx"] (concurrent-test 4))
;(binding [*host* "cxp"] (concurrent-test 4))
(defn concurrent-test
  [n]
  (concurrent-run n (run-tests 'nginx.clojure.test-all)))




