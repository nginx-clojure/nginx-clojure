(ns nginx.clojure.test-all
   (:use [clojure.test])
   (:require [clj-http.client :as client]
             [clojure.data.json :as json]
             [gniazdo.core :as ws]
             [clojure.edn :as edn])
   (:import [java.io BufferedReader StringReader]))

(def ^:dynamic *host* "localhost")
(def ^:dynamic *port* "8080")
(def ^:dynamic *debug* false)

(def ^:dynamic *http-get* client/get)

(defn debug-println [& args]
  (when (true? *debug*)
      (apply println args)))

(deftest ^{:remote true} test-naive-simple
  (testing "hello clojure"
           (let [r (client/get (str "http://" *host* ":" *port* "/clojure") {:coerce :unexceptional})
                 h (:headers r)]
             (debug-println r)
             (debug-println "=================hello clojure end=============================")
             (is (= 200 (:status r)))
             (is (= "Hello Clojure & Nginx!" (:body r)))
             (is (= "text/plain" (h "content-type")))
             (is (= "22" (h "content-length")))
             (is (.startsWith (h "server") "nginx-clojure"))))
    (testing "hello java"
           (let [r (client/get (str "http://" *host* ":" *port* "/java/hello") {:coerce :unexceptional})
                 h (:headers r)]
             (debug-println r)
             (debug-println "=================hello clojure end=============================")
             (is (= 200 (:status r)))
             (is (= "Hello, Java & Nginx!" (:body r)))
             (is (= "text/plain" (h "content-type")))
             (is (= "20" (h "content-length")))
             (is (.startsWith (h "server") "nginx-clojure")))))



(deftest ^{:remote true} test-headers
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
  
    (testing "java simple  headers"
           (let [r (client/get (str "http://" *host* ":" *port* "/java/headers") {:coerce :unexceptional})
                 h (:headers r)
                 b (-> r :body (json/read-str))]
             (debug-println r)
             (debug-println "===============simple headers end =============================")
             (is (= 200 (:status r)))
             (is (= "e29b7ffb8a5325de60aed2d46a9d150b" (h "etag")))
             (is (= ["no-store" "no-cache"] (h "cache-control")))
             (is (.startsWith (h "server") "nginx-clojure"))
             (is (= "http" (b "scheme")))
             (is (= "/java/headers" (b "uri")))
             (is (= *port* (b "server-port")))))
  
  (testing "lowercase/uppercase headers"
           (let [r (client/get (str "http://" *host* ":" *port* "/loweruppercaseheaders") {:coerce :unexceptional, :headers {"My-Header" "mytest"}})
                 h (:headers r)
                 b (-> r :body (edn/read-string))]
             (debug-println r)
             (debug-println "===============lowercase/uppercase headers =============================")
             (is (= 200 (:status r)))
             (is (= "e29b7ffb8a5325de60aed2d46a9d150b" (h "etag")))
             (is (= ["no-store" "no-cache"] (h "cache-control")))
             (is (.startsWith (h "server") "nginx-clojure"))
             (is (= "http" (b :scheme)))
             (is (= "text/plain" (h "content-type")))
             (is (= "mytest" (b :my-header)))
             (is (= "/loweruppercaseheaders" (b :uri)))
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
  
    (testing "java cookie & user defined headers"
           (let [r (client/get (str "http://" *host* ":" *port* "/java/headers") {:coerce :unexceptional, :headers {"my-header" "mytest"}, :cookies {"tc1" {:value "tc1value"}, "tc2" {:value "tc2value"} } })
                 h (:headers r)
                 b (-> r :body (json/read-str))]
             (debug-println r)
             (debug-println "===============cookie & user defined headers end=============================")
             (is (= 200 (:status r)))
             (is (= "e29b7ffb8a5325de60aed2d46a9d150b" (h "etag")))
             (is (= ["no-store" "no-cache"] (h "cache-control")))
             (is (.startsWith (h "server") "nginx-clojure"))
             (is (= "http" (b "scheme")))
             (is (= "/java/headers" (b "uri")))
             (is (= *port* (b "server-port")))
             (is (= "mytest" (b "my-header")))
             (is (= "tc1=tc1value;tc2=tc2value" (b "cookie")))))
  
    (testing "query string & character-encoding"
           (let [r (client/get (str "http://" *host* ":" *port* "/headers?my=test") {:coerce :unexceptional, :headers {"my-header" "mytest" "content-type" "text/plain; charset=utf-8"}, :cookies {"tc1" {:value "tc1value"}, "tc2" {:value "tc2value"} } })
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

(deftest ^{:remote true} test-form
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
  
    (testing "form larger multipart-formdata"
           (let [r (client/post (str "http://" *host* ":" *port* "/echoUploadfile") {:coerce :unexceptional, :multipart [{:name "mytoken", :content "123456"},
                                                                                                 {:name "myf", :content (clojure.java.io/file "test/nginx-working-dir/post-test-large-data")}
                                                                                                 ]})
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================form multipart-formdata=============================")
             (is (= 200 (:status r)))
             (is (< 0 (.indexOf b "name=\"mytoken\"")))
             (is (< 0 (.indexOf b "123456")))
             (is (< 0 (.indexOf b "name=\"myf\"")))
             (is (< 0 (.indexOf b "Release 4.3.3")))
             (is (< 0 (.indexOf b "Contributed by Roland Weber <rolandw at apache.org>")))
             ))
  )


(deftest ^{:remote true} test-file
  (testing "static file without gzip"
           (let [r (client/get (str "http://" *host* ":" *port* "/files/small.html") {:coerce :unexceptional, :decompress-body false})
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================static file (no gzip) end =============================")
             (is (= 200 (:status r)))
             (is (= "680" (h "content-length")))))
    (testing "static file with gzip"
           ;clj-http will auto use Accept-Encoding	gzip, deflate
           (let [r (client/get (str "http://" *host* ":" *port* "/files/small.html"))
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================static file (with gzip) end=============================")
             (is (= 200 (:status r)))
             (is (= "gzip" (:orig-content-encoding r)))
             (is (= 680 (count (r :body))))))
    
   (testing "static file with range operation"
      ;clj-http will auto use Accept-Encoding	gzip, deflate
      (let [r (client/get (str "http://" *host* ":" *port* "/files/small.html")  {:coerce :unexceptional, :decompress-body false, :headers {"Range" "bytes=0-128"}})
            h (:headers r)
            b (r :body)]
        (debug-println r)
        (debug-println "=================static file (with range 0-128) end=============================")
        ;206 Partial Content
        (is (= 206 (:status r)))
        (is (= 129 (count (r :body))))))
    
  )

(deftest ^{:remote true} test-seq
  (testing "seq include String &  File without gzip"
           (let [r (client/get (str "http://" *host* ":" *port* "/testMySeq") {:coerce :unexceptional, :decompress-body false})
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================seq include String &  File without gzip=============================")
             (is (= 200 (:status r)))
             (is (= (str (+ 680 (count "header line\n"))) (h "content-length")))))
  )


(deftest ^{:remote true} test-inputstream
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

(deftest ^{:remote true} test-redirect
  (testing "redirect"
           (let [r (client/get (str "http://" *host* ":" *port* "/testRedirect") {:follow-redirects false})
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================redirect=============================")
             (is (= 302 (:status r)))
             (is (= (str  "http://" *host* ":" *port*  "/files/small.html") (h "location"))))))


(deftest ^{:remote true} test-ring-compojure
    (testing "hello"
           (let [r (client/get (str "http://" *host* ":" *port* "/ringCompojure/hello") {:throw-exceptions false})
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================test-ring-compojure hello=============================")
             (is (= 200 (:status r)))
             (is (= "text/plain" (h "content-type")))
             (is (= "Hello World" b))))
    (testing "redirect"
           (let [r (client/get (str "http://" *host* ":" *port* "/ringCompojure/redirect") {:follow-redirects false :throw-exceptions false})
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================test-ring-compojure redirect=============================")
             (is (= 302 (:status r)))
             (is (= "http://example.com" (h "location")))))
    (testing "file-response"
           (let [r (client/get (str "http://" *host* ":" *port* "/ringCompojure/file-response" ) {:throw-exceptions false})
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================test-ring-compojure file-response=============================")
             (is (= 200 (:status r)))
             ;(is (= "gzip" (:orig-content-encoding r)))
             (is (= 680 (count (r :body))))))
    (testing "resource-response"
           (let [r (client/get (str "http://" *host* ":" *port* "/ringCompojure/resource-response") {:throw-exceptions false})
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================test-ring-compojure resource-response=============================")
             (is (= 200 (:status r)))
             ;(is (= "gzip" (:orig-content-encoding r)))
             (is (= 680 (count (r :body))))))
    (testing "wrap-content-type"
           (let [r (client/get (str "http://" *host* ":" *port* "/ringCompojure/wrap-content-type.html") {:throw-exceptions false})
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================test-ring-compojure wrap-content-type=============================")
             (is (= 200 (:status r)))
             (is (= "text/x-foo" (h "content-type")))))
    (testing "wrap-params"
           (let [r (client/get (str "http://" *host* ":" *port* "/ringCompojure/wrap-params?x=hello&x=world") {:throw-exceptions false})
                 h (:headers r)
                 params (-> "rmap" h (edn/read-string) :params)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================test-ring-compojure wrap-params=============================")
             (is (= 200 (:status r)))
             ;(is (= "gzip" (:orig-content-encoding r)))
             (is (= ["hello", "world"] (params "x")))))
    (testing "wrap-params-post"
           (let [r (client/post (str "http://" *host* ":" *port* "/ringCompojure/wrap-params" ) {:coerce :unexceptional, :form-params {:foo "bar"}, :throw-exceptions false})
                 h (:headers r)
                 params (-> "rmap" h (edn/read-string) :params)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================test-ring-compojure wrap-params-post=============================")
             (is (= 200 (:status r)))
             ;(is (= "gzip" (:orig-content-encoding r)))
             (is (= "bar" (params "foo")))))
    (testing "wrap-cookies"
           (let [r (client/get (str "http://" *host* ":" *port* "/ringCompojure/wrap-cookies") {:coerce :unexceptional,:cookies {"tc1" {:value "tc1value"}, "tc2" {:value "tc2value"} }, :throw-exceptions false})
                 h (:headers r)
                 cookies (-> "rmap" h (edn/read-string) :cookies)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================test-ring-compojure wrap-cookies=============================")
             (is (= 200 (:status r)))
             ;(is (= "gzip" (:orig-content-encoding r)))
             (is (= {"tc1" {:value "tc1value"}, "tc2" {:value "tc2value"} } cookies))))
    (testing "authorized-service"
             (let [
                   r1 (client/get (str "http://" *host* ":" *port* "/ringCompojure/authorized-service") {:coerce :unexceptional, :throw-exceptions false})
                   r2 (client/get (str "http://" *host* ":" *port* "/ringCompojure/authorized-service") {:basic-auth ["nginx-clojure" "xxxx"] :coerce :unexceptional, :throw-exceptions false})
                   ]
               (is (= 401 (:status r1)))
               (is (= 200 (:status r2)))))
    (testing "json-patch"
             (let [msg "{\"value\": 5}"
                   r (client/patch (str "http://" *host* ":" *port* "/ringCompojure/json-patch") {:coerce :unexceptional, :throw-exceptions false, :body msg})
                   ]
               (is (= (str "Your patch succeeded! length=" (count msg)) (:body r)))))
    (testing "wrap-session"
           (let [cs (clj-http.cookies/cookie-store)]
             (let [r (client/get (str "http://" *host* ":" *port* "/ringCompojure/wrap-session") {:throw-exceptions false, :cookie-store cs})
                   b (r :body)]
             (debug-println r)
             (debug-println cs)
             (debug-println "=================test-ring-compojure wrap-session 1=============================")
             (is (= 200 (:status r)))
             (is (= "Welcome guest!" b)))
             (let [r (client/get (str "http://" *host* ":" *port* "/ringCompojure/wrap-session?user=Tom") {:throw-exceptions false, :cookie-store cs})
                   b (r :body)]
             (debug-println r)
             (debug-println cs)
             (debug-println "=================test-ring-compojure wrap-session 2=============================")
             (is (= 200 (:status r)))
             (is (= "Welcome Tom!" b)))
             (let [r (client/get (str "http://" *host* ":" *port* "/ringCompojure/wrap-session") {:throw-exceptions false, :cookie-store cs})
                   b (r :body)]
             (debug-println r)
             (debug-println cs)
             (debug-println "=================test-ring-compojure wrap-session 3=============================")
             (is (= 200 (:status r)))
             (is (= "Welcome Tom!" b)))
             )
           )
    (testing "not-found"
           (let [r (client/get (str "http://" *host* ":" *port* "/ringCompojure/not-found") {:throw-exceptions false})
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================test-ring-compojure hello=============================")
             (is (= 404 (:status r)))
             (is (= "text/html; charset=utf-8" (h "content-type")))
             (is (= "<h1>Page not found</h1>" b))))
    
    (testing "sub/pub (by long polling & broadcast)"
             (let [p (future (client/get (str "http://" *host* ":" *port* "/ringCompojure/sub") {:throw-exceptions false :socket-timeout 20000}))]
               (Thread/sleep 5000) ;;let sub succeed
               (client/get (str "http://" *host* ":" *port* "/ringCompojure/pub?good") {:throw-exceptions false :socket-timeout 10000})
               (debug-println @p)
               (debug-println "=================test-sub/pub (by long polling & broadcast)=============================")
               (is (= "good" (:body @p)))))
    (testing "sse-sub/sse-pub (by sever sent envets & broadcast)"
         (let [p (future (client/get (str "http://" *host* ":" *port* "/ringCompojure/sse-sub") {:throw-exceptions false :socket-timeout 20000}))]
           (Thread/sleep 5000) ;;let sub succeed
           (client/get (str "http://" *host* ":" *port* "/ringCompojure/sse-pub?good!") {:throw-exceptions false :socket-timeout 10000})
           (client/get (str "http://" *host* ":" *port* "/ringCompojure/sse-pub?bad!") {:throw-exceptions false :socket-timeout 10000})
           (client/get (str "http://" *host* ":" *port* "/ringCompojure/sse-pub?finish!") {:throw-exceptions false :socket-timeout 10000})
           (debug-println @p)
           (debug-println "=================test-sse-sub/sse-pub (by sever sent envets & broadcast)=============================")
           (is (= "retry: 4500\r\ndata: good!\r\n\r\ndata: bad!\r\n\r\ndata: finish!\r\n\r\n" (:body @p)))))
  )


(deftest ^{:remote true} test-hijacksend
  (testing "hijack chunk send"
           (let [r (client/get (str "http://" *host* ":" *port* "/java/mchain") {:coerce :unexceptional, :decompress-body false})
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================hijack chunk send end =============================")
             (is (= 200 (:status r)))
             (is (= "first part.\r\nsecond part.\r\nthird part.\r\nlast part.\r\n" (r :body)))))
    (testing "hijack utf8 chunk send"
           ;clj-http will auto use Accept-Encoding	gzip, deflate
           (let [r (client/get (str "http://" *host* ":" *port* "/java/utf8mchain"))
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================hijack utf8 chunk send end=============================")
             (is (= 200 (:status r)))
             (is (= "来1点中文，在utf8分隔下，中文字符会被截到不同的chain中" (r :body)))))
  )

(def remote-socket-content 
  (delay (let [sf (nginx.clojure.net.SimpleHandler4TestNginxClojureSocket.)
               r1 (.invoke sf {})
               b1 (slurp (get r1 2))
               ] b1)))

(def remote-http-content
  (delay 
    (let [r1 (client/get "https://www.apache.org/dist/httpcomponents/httpclient/RELEASE_NOTES-4.3.x.txt" {:socket-timeout 10000})
         b1 (r1 :body)] b1)))

(deftest ^{:async true :remote true} test-asyncsocket
    (let [
        ;r1 (client/get "http://www.apache.org/dist/httpcomponents/httpclient/RELEASE_NOTES-4.3.x.txt")
        ;b1 (r1 :body)
        abc ""
        ]
      (testing "asyncsocket --simple example"
           (let [r (client/get (str "http://" *host* ":" *port* "/asyncsocket") {:throw-exceptions false})
                 h (:headers r)
                 b (r :body)
                 bb (subs b (.indexOf b "\r\n\r\n"))
                 b1 @remote-socket-content
                 b1b (subs b1 (.indexOf b1 "\r\n\r\n"))]
             (debug-println "=================asyncsocket simple example =============================")
             (is (= 200 (:status r)))
             (is (= (.length bb) (.length b1b)))
             (is (= bb b1b))))
    )
  
  )

(deftest ^{:async true :remote true} test-asyncchannel
    (let [b1 @remote-http-content]
      (testing "asyncchannel --simple example"
           (let [r (client/get (str "http://" *host* ":" *port* "/asyncchannel") {:throw-exceptions false})
                 h (:headers r)
                 b (r :body)
                 ]
             (debug-println "=================asyncchannel simple example =============================")
             (is (= 200 (:status r)))
             (is (= (.length b) (.length b1)))
             (is (= b b1))))
    )
  )

(deftest ^{:async true :remote true} test-cljasyncchannel
    (let [b1 @remote-http-content]
      (testing "cljasyncchannel --simple example"
           (let [r (client/get (str "http://" *host* ":" *port* "/cljasyncchannel") {:throw-exceptions false})
                 h (:headers r)
                 b (r :body)
                 ]
             (debug-println "=================cljasyncchannel simple example =============================")
             (is (= 200 (:status r)))
             (is (= (.length b) (.length b1)))
             (is (= b b1))))
    )
  )

(deftest ^{:async true :remote true} test-cljasyncsocket
    (let [
        ;r1 (client/get "http://www.apache.org/dist/httpcomponents/httpclient/RELEASE_NOTES-4.3.x.txt")
        ;b1 (r1 :body)
        abc ""
        ]
      (testing "asyncsocket --simple example"
           (let [r (client/get (str "http://" *host* ":" *port* "/cljasyncsocket") {:throw-exceptions false})
                 h (:headers r)
                 b (r :body)
                 bb (subs b (.indexOf b "\r\n\r\n"))
                 b1 @remote-socket-content
                 b1b (subs b1 (.indexOf b1 "\r\n\r\n"))]
             (debug-println "=================clj asyncsocket simple example =============================")
             (is (= 200 (:status r)))
             (is (= (.length bb) (.length b1b)))
             (is (= bb b1b))))
    )
  
  )

;(comment 
(deftest ^{:remote true} test-coroutine
  (let [
        b1 @remote-http-content]
      (testing "coroutine based socket--simple example"
           (let [r (client/get (str "http://" *host* ":" *port* "/socket") {:throw-exceptions false})
                 h (:headers r)
                 b (r :body)
                 bb (subs b (.indexOf b "\r\n\r\n"))
                 b1 @remote-socket-content
                 b1b (subs b1 (.indexOf b1 "\r\n\r\n"))]
             (debug-println "=================coroutine based socket simple example =============================")
             (is (= 200 (:status r)))
             (is (= (.length bb) (.length b1b)))
             (is (= bb b1b))))

     (testing "coroutine based socket--httpclient get"
              (let [r (client/get (str "http://" *host* ":" *port* "/httpclientget") {:throw-exceptions false})
                    h (:headers r)
                    b (r :body)
;                 r1 (client/get "http://www.apache.org/dist/httpcomponents/httpclient/RELEASE_NOTES-4.3.x.txt")
;                 b1 (r1 :body)
                ]
             (debug-println "=================coroutine based socket httpclient get =============================")
             (is (= 200 (:status r)))
             (is (= (.length b) (.length b1)))
             (is (= b b1))))
     
     (testing "coroutine based socket--compojure & httpclient get"
            (let [r (client/get (str "http://" *host* ":" *port* "/coroutineSocketAndCompojure/simple-httpclientget") {:throw-exceptions false})
                  h (:headers r)
                  b (r :body)
                ]
             (debug-println "=================coroutine based socket compojure & httpclient get =============================")
             (is (= 200 (:status r)))
             (is (= (.length b) (.length b1)))
             (is (= b b1))))  
    ;http://localhost:8080/coroutineSocketAndCompojure/simple-clj-http-test
     (testing "coroutine based socket--compojure & clj-http get"
            (let [r (client/get (str "http://" *host* ":" *port* "/coroutineSocketAndCompojure/simple-clj-http-test") {:throw-exceptions false})
                  h (:headers r)
                  b (r :body)
;                 r1 (client/get "http://www.apache.org/dist/httpcomponents/httpclient/RELEASE_NOTES-4.3.x.txt")
;                 b1 (r1 :body)
                ]
             (debug-println "=================coroutine based socket compojure & clj-http get =============================")
             (is (= 200 (:status r)))
             (is (= (.length b) (.length b1)))
             (is (= b b1)))) 
    ;http://localhost:8080/coroutineSocketAndCompojure/simple-clj-https-test
     (testing "coroutine based socket--compojure & clj-http get"
            (let [r (client/get (str "http://" *host* ":" *port* "/coroutineSocketAndCompojure/simple-clj-https-test") {:throw-exceptions false})
                  h (:headers r)
                  b (r :body)
;                 r1 (client/get "http://www.apache.org/dist/httpcomponents/httpclient/RELEASE_NOTES-4.3.x.txt")
;                 b1 (r1 :body)
                ]
             (debug-println "=================coroutine based socket compojure & clj-http get =============================")
             (is (= 200 (:status r)))
             (is (= (.length b) (.length b1)))
             (is (= b b1))))
     ;http://localhost:8080/coroutineSocketAndCompojure/fetch-two-pages
     (testing "coroutine based socket--co-pvalues & compojure & clj-http "
            (let [r (client/get (str "http://" *host* ":" *port* "/coroutineSocketAndCompojure/fetch-two-pages") {:throw-exceptions false})
                  h (:headers r)
                  b (r :body)
                 [r1, r2] (pvalues (client/get "http://www.apache.org/dist/httpcomponents/httpclient/KEYS")
                                   (client/get "http://www.apache.org/dist/httpcomponents/httpcore/KEYS"))
                 b12 (str (:body r1) "\n==========================\n" (:body r2))
                 b (.replace b "<address>Apache/2.4.10 (Unix) OpenSSL/1.0.1i Server at www.apache.org Port 80</address>\n</body>" "")
                 b12   (.replace b12  "<address>Apache/2.4.10 (Unix) OpenSSL/1.0.1i Server at www.apache.org Port 80</address>\n</body>" "")
                ]
             (debug-println "=================coroutine based socket--co-pvalues & compojure & clj-http  =============================")
             (is (= 200 (:status r)))
             (is (= (.length b) (.length b12)))
             (is (= b b12))))       
    )
  )
;)

(deftest ^{:remote true :jdbc true} test-coroutine-jdbc
  (let [
        b1 @remote-socket-content]     
     (testing "coroutine based socket--compojure & mysql jdbc"
            (let [cr (client/get (str "http://" *host* ":" *port* "/coroutineSocketAndCompojure/mysql-create") {:throw-exceptions false})
                  ir1 (client/put (str "http://" *host* ":" *port* "/coroutineSocketAndCompojure/mysql-insert") {:form-params {:name "java" :rank "5"} :throw-exceptions false})
                  ir2 (client/put (str "http://" *host* ":" *port* "/coroutineSocketAndCompojure/mysql-insert") {:form-params {:name "clojure" :rank "4"} :throw-exceptions false})
                  ir3 (client/put (str "http://" *host* ":" *port* "/coroutineSocketAndCompojure/mysql-insert") {:form-params {:name "c" :rank "5"} :throw-exceptions false})
                  qr1 (client/get (str "http://" *host* ":" *port* "/coroutineSocketAndCompojure/mysql-query/java") {:throw-exceptions false })
                  qr2 (client/get (str "http://" *host* ":" *port* "/coroutineSocketAndCompojure/mysql-query/clojure") {:throw-exceptions false})
                  dr  (client/get (str "http://" *host* ":" *port* "/coroutineSocketAndCompojure/mysql-drop") {:throw-exceptions false})
                  qad (client/get (str "http://" *host* ":" *port* "/coroutineSocketAndCompojure/mysql-query/java") {:throw-exceptions false})
                ]
             (debug-println "=================coroutine based socket compojure & mysql jdbc- created=============================")
             (is (= 200 (:status cr)))
             (is (= "created!" (:body cr)))
             (is (= 200 (:status ir1)))
             (is (= "inserted!" (:body ir1)))
             (is (= 200 (:status ir2)))
             (is (= "inserted!" (:body ir2)))
             (is (= 200 (:status ir3)))
             (is (= "inserted!" (:body ir3)))
             (is (= 200 (:status qr1)))
             (is (= [{:name "java" :rank "5"}]  (-> qr1 :body (edn/read-string))))
             (is (= 200 (:status qr2)))
             (is (= [{:name "clojure" :rank "4"}] (-> qr2 :body (edn/read-string))))
             (is (= 200 (:status dr)))
             (is (= "dropped!" (:body dr)))
             (is (= 500 (:status qad)))
             ))       
    )
  )

(deftest ^{:remote true} test-coroutine-redis
  (let [
        b1 @remote-http-content]
      (testing "coroutine based socket--redis large value example"
           (let [r (client/get (str "http://" *host* ":" *port* "/redis") {:throw-exceptions false})
                 h (:headers r)
                 ]
             (debug-println "=================coroutine based socket--redis large value example =============================")
             (is (= 200 (:status r)))
             (is (= "74499" (h "content-length")))))      
    )
  )

(deftest ^{:remote true} test-nginx-var
  (testing "simple nginx var"
           (let [r (client/get (str "http://" *host* ":" *port* "/vartest") {:follow-redirects false})
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================vartest=============================")
             (is (= 200 (:status r)))
             (is (= "Hello,Xfeep!" (:body r))))))


(deftest ^{:remote true :rewrite-handler true} test-rewrite-handler
  (testing "rewritesimple"
           (let [r (client/get (str "http://" *host* ":" *port* "/rewritesimple") {:follow-redirects false})
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================rewritesimple=============================")
             (is (= 200 (:status r)))
             (is (= "Hello,Xfeep!" (:body r)))))
    (testing "javarewritesimple"
           (let [r (client/get (str "http://" *host* ":" *port* "/javarewritesimple") {:follow-redirects false})
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================javarewritesimple=============================")
             (is (= 200 (:status r)))
             (is (= "Hello,Xfeep!" (:body r)))))
      (testing "rewrite proxy pass"
           (let [r (client/get (str "http://" *host* ":" *port* "/uptest") {:follow-redirects false})
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================rewritesimple=============================")
             (is (= 200 (:status r)))
             (is (or (= "hello,a!/mytestpath" (:body r)) (= "hello,b!/mytestpath" (:body r))))))
       (testing "rewrite proxy pass by body -- small body"
           (let [r (client/post (str "http://" *host* ":" *port* "/javarewritebybodyproxy/") {:follow-redirects false, :body "ub" :throw-exceptions false :socket-timeout 10000})
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================rewrite proxy pass by body -- small body=============================")
             (is (= 200 (:status r)))
             (is  (= "hello,b!/javarewritebybodyproxy/" (:body r)) )))
        (testing "rewrite proxy pass by body -- large body"
           (let [r (client/post (str "http://" *host* ":" *port* "/javarewritebybodyproxy/") {:follow-redirects false, :body (clojure.java.io/file "test/nginx-working-dir/post-rewrite-large-body-data") :throw-exceptions false :socket-timeout 10000})
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================rewritesimple=============================")
             (is (= 200 (:status r)))
             (is  (= "hello,b!/javarewritebybodyproxy/" (:body r)) )))
       (testing "rewrite hijack pass"
           (let [r (client/get (str "http://" *host* ":" *port* "/javarewrite/hijackpass0") {:follow-redirects false})
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================rewrite hijack pass=============================")
             (is (= 200 (:status r)))
             (is (= "Hello,Xfeep!" (:body r)))))
      (testing "rewrite hijack pass & ignore filter"
          (let [r (client/get (str "http://" *host* ":" *port* "/javarewrite/hijackpass1") {:follow-redirects false})
                h (:headers r)
                b (r :body)]
            (debug-println r)
            (debug-println "=================rewrite hijack pass & ignore filter=============================")
            (is (= 200 (:status r)))
            (is (= "Hello,Xfeep!" (:body r)))))
      (testing "rewrite hijack 400"
           (let [r (client/get (str "http://" *host* ":" *port* "/javarewrite/hijackbad0") {:follow-redirects false :throw-exceptions false})
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================rewrite hijack 400=============================")
             (is (= 400 (:status r)))
             (is (= "hijacked rewrite handler no pass to content handler!" (:body r))))) 
      (testing "rewrite hijack & ignore filter 400"
           (let [r (client/get (str "http://" *host* ":" *port* "/javarewrite/hijackbad1") {:follow-redirects false :throw-exceptions false})
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================rewrite hijack 400=============================")
             (is (= 400 (:status r)))
             (is (= "hijacked rewrite handler no pass to content handler!" (:body r))))) 
      (testing "rewrite with remote"
           (let [r (client/get (str "http://" *host* ":" *port* "/javarewrite/remote") {:follow-redirects false :throw-exceptions false})
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================rewrite hijack 400=============================")
             (is (= 200 (:status r)))
             (is (= (str @remote-http-content ",Xfeep!") (:body r)))))      
        
  )

;(deftest ^{:remote true :rewrite-handler true :keepalive true} test-rewrite-handler-keepalive
;  (client/with-connection-pool {:timeout 5 :threads 4 :insecure? false :default-per-route 10}
;    (test-rewrite-handler)))

(comment
  (deftest ^{:remote true :rewrite-handler true :keepalive true} test-rewrite-hijackbad0
  (client/with-connection-pool {:timeout 5 :threads 4 :insecure? false :default-per-route 10}
      (testing "rewrite hijack 400"
           (let [r (client/get (str "http://" *host* ":" *port* "/javarewrite/hijackbad0") {:follow-redirects false :throw-exceptions false})
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================rewrite hijack 400=============================")
             (is (= 400 (:status r)))
             (is (= "hijacked rewrite handler no pass to content handler!" (:body r))))))
  ))


(defn- first-line [str]
  (-> str (StringReader. ) (BufferedReader. ) (line-seq) (first)))

(deftest ^{:remote true :access-handler true} test-access-handler
  (testing "acces deny"
           (let [r (client/get (str "http://" *host* ":" *port* "/javaaccess/deny") {:coerce :unexceptional :follow-redirects false  :throw-exceptions false})
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================/javaaccess/deny=============================")
             (is (= 403 (:status r)))))
           
    (testing "access exception"
           (let [r (client/get (str "http://" *host* ":" *port* "/javaaccess/ex") {:coerce :unexceptional :follow-redirects false :throw-exceptions false})
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================/javaaccess/ex=============================")
             (is (= 500 (:status r)))
             (is (= "java.lang.RuntimeException: ExceptionInAccessHandler" (first-line (:body r))))))
      (testing "access basic auth--fail"
           (let [r (client/get (str "http://" *host* ":" *port* "/javaaccess/basic0") {:coerce :unexceptional :follow-redirects false :throw-exceptions false})
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================/javaaccess/basic0=============================")
             (is (= 401 (:status r)))
            (is (= "<HTML><BODY><H1>401 Unauthorized.</H1></BODY></HTML>" (first-line (:body r))))))
       (testing "access basic auth-success"
           (let [r (client/get (str "http://" *host* ":" *port* "/javaaccess/basic0") {:coerce :unexceptional :follow-redirects false :basic-auth ["xfeep" "hello!"] :throw-exceptions false})
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================/javaaccess/basic0=============================")
             (is (= 200 (:status r)))
             (is  (= "Hello, Java & Nginx!" (:body r)) )))
        (testing "access basic auth-success with static file"
           (let [r (client/get (str "http://" *host* ":" *port* "/javaaccess/basic1/small.html") {:coerce :unexceptional :follow-redirects false, :basic-auth ["xfeep" "hello!"] :throw-exceptions false})
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================/javaaccess/basic1/small.html=============================")
             (is (= 200 (:status r)))
             (is  (= "680" (h  "content-length")) )))
        
         (testing "access basic auth-success"
           (let [r (client/get (str "http://" *host* ":" *port* "/javaaccess/basic1/small2.html") {:coerce :unexceptional :follow-redirects false, :basic-auth ["xfeep" "hello!"] :throw-exceptions false})
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================/javaaccess/basic1/small2.html=============================")
             (is (= 404 (:status r)))
             ;; 146 nginx 1.18.0, 162 ngin 1.14.2
             (is  (= "146" (h  "content-length")) )))
         
         (testing "access basic auth-fail with 401"
           (let [r (client/get (str "http://" *host* ":" *port* "/javaaccess/basic1/small2.html") {:coerce :unexceptional :follow-redirects false, :basic-auth ["xfeep" "xxxxx!"] :throw-exceptions false})
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================/javaaccess/basic1/small2.html=============================")
             (is (= 401 (:status r)))
              (is (= "<HTML><BODY><H1>401 Unauthorized BAD USER & PASSWORD.</H1></BODY></HTML>" (first-line (:body r))))))
 
         (testing "access with remote access"
           (let [r (client/get (str "http://" *host* ":" *port* "/javaaccess/rc") {:coerce :unexceptional :follow-redirects false, :basic-auth ["xfeep" "xxxxx!"] :throw-exceptions false})
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================/javaaccess/rc=============================")
             (is (= 200 (:status r)))
             (is  (= "Hello, Java & Nginx!" (:body r)) ))) 

         (testing "access hijack with 401"
           (let [r (client/get (str "http://" *host* ":" *port* "/javaaccess/hijack0") {:coerce :unexceptional :follow-redirects false, :basic-auth ["xfeep" "xxxxx!"] :throw-exceptions false})
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================/javaaccess/basic1/small2.html=============================")
             (is (= 401 (:status r)))
              (is (= "<HTML><BODY><H1>401 Unauthorized BAD USER & PASSWORD.</H1></BODY></HTML>" (first-line (:body r))))))
         
        (testing "access hijack sucesss"
           (let [r (client/get (str "http://" *host* ":" *port* "/javaaccess/hijack0") {:coerce :unexceptional :follow-redirects false, :basic-auth ["xfeep" "hello!"] :throw-exceptions false})
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================/javaaccess/rc=============================")
             (is (= 200 (:status r)))
             (is  (= "Hello, Java & Nginx!" (:body r)) ))) 
 
         (testing "access hijack & ingore filter with 401"
           (let [r (client/get (str "http://" *host* ":" *port* "/javaaccess/hijack1") {:coerce :unexceptional :follow-redirects false, :basic-auth ["xfeep" "xxxxx!"] :throw-exceptions false})
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================/javaaccess/hijack1 hijack & ingore filter 401=============================")
             (is (= 401 (:status r)))
              (is (= "<HTML><BODY><H1>401 Unauthorized BAD USER & PASSWORD.</H1></BODY></HTML>" (first-line (:body r))))))
         
        (testing "access hijack & ingore filter sucesss"
           (let [r (client/get (str "http://" *host* ":" *port* "/javaaccess/hijack1") {:coerce :unexceptional :follow-redirects false, :basic-auth ["xfeep" "hello!"] :throw-exceptions false})
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================/javaaccess/hijack1 hijack & ingore filter=============================")
             (is (= 200 (:status r)))
             (is  (= "Hello, Java & Nginx!" (:body r)) )))         
        
        )

(deftest ^{:remote true}  test-java-header-filter
  (testing "header filter add with static file"
           (let [r (client/get (str "http://" *host* ":" *port* "/javafilter/small.html") {:coerce :unexceptional :follow-redirects false :throw-exceptions false})
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================/javafilter/small.html=============================")
             (is (= 200 (:status r)))
             (is (= "Hello!" (h "xfeep-header")))
             (is  (= "680" (h  "content-length")) )
             (is (= 680 (.length b)))
             )

           )
    (testing "header filter add with simple hello"
           (let [r (client/get (str "http://" *host* ":" *port* "/javafilter/hello") {:coerce :unexceptional :follow-redirects false :throw-exceptions false})
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================/javafilter/hello=============================")
             (is (= 200 (:status r)))
             (is (= "Hello!" (h "xfeep-header")))
             (is  (= "Hello, Java & Nginx!" b) ))

           )
    
    (testing "header filter remove & add more"
           (let [r (client/get (str "http://" *host* ":" *port* "/javafilter/ra") {:coerce :unexceptional :follow-redirects false :throw-exceptions false})
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================/javafilter/ra=============================")
             (is (= 200 (:status r)))
             (is (= "Hello2!" (h "xfeep-header")))
              ;;;content-type: text/html
              (is (= "text/html" (h "content-type")))
             (is  (= "Hello, Java & Nginx!" b) ))
           )  
    
     (testing "header filter with exception"
           (let [r (client/get (str "http://" *host* ":" *port* "/javafilter/ex0") {:coerce :unexceptional :follow-redirects false :throw-exceptions false})
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================/javafilter/ex0=============================")
             (is (= 500 (:status r)))
              ;;;content-type: text/html
             (is  (= "java.lang.RuntimeException: Hello, exception in header filter!" (first-line b)) ))
           ) 
 
     (testing "header filter with exception 1"
           (let [r (client/get (str "http://" *host* ":" *port* "/javafilter/ex1") {:coerce :unexceptional :follow-redirects false :throw-exceptions false})
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================/javafilter/ex1=============================")
             (is (= 500 (:status r)))
              ;;;content-type: text/html
             (is  (= "java.lang.RuntimeException: Hello, exception in header filter!" (first-line b)) ))
           )            
     
      (testing "header filter with remote access 0"
           (let [r (client/get (str "http://" *host* ":" *port* "/javafilter/rc0") {:coerce :unexceptional :follow-redirects false :throw-exceptions false})
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================/javafilter/rc0=============================")
             (is (= 404 (:status r)))
             (is (= "77342" (h "remote-content-length"))))
           )  
      
      (testing "header filter with remote access & static file"
           (let [r (client/get (str "http://" *host* ":" *port* "/javafilter/rc1/small.html") {:coerce :unexceptional :follow-redirects false :throw-exceptions false})
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================/javafilter/rc1=============================")
             (is (= 200 (:status r)))
              ;;;content-type: text/html
              (is (= "77342" (h "remote-content-length")))
             (is  (= "680" (h  "content-length")) )
             (is (= 680 (.length b))))
           )  
      
     (testing "header filter with remote access & dynamic content"
           (let [r (client/get (str "http://" *host* ":" *port* "/javafilter/rc2") {:coerce :unexceptional :follow-redirects false :throw-exceptions false})
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================/javafilter/rc2=============================")
             (is (= 200 (:status r)))
             (is (= "77342" (h "remote-content-length")))
              ;;;content-type: text/html
             (is (= "Hello, Java & Nginx!" b) ))
           )
     ;;proxybufferonheaderfilter
     
  )

(deftest ^{:remote true} test-proxy-pass-with-header-filter
  (testing "proxy buffer on with header filter"
           (let [r (client/get (str "http://" *host* ":" *port* "/proxybufferonheaderfilter") {:coerce :unexceptional, :decompress-body false})
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================/proxybufferonheaderfilter =============================")
             (is (= 200 (:status r)))
             (is (= "first part.\r\nsecond part.\r\nthird part.\r\nlast part.\r\n" (r :body)))))
  (testing "proxy buffer off with header filter"
           (let [r (client/get (str "http://" *host* ":" *port* "/proxybufferoffheaderfilter") {:coerce :unexceptional, :decompress-body false})
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================/proxybufferoffheaderfilter =============================")
             (is (= 200 (:status r)))
             (is (= "first part.\r\nsecond part.\r\nthird part.\r\nlast part.\r\n" (r :body)))))
  )

(deftest ^{:remote true} test-proxy-pass-with-body-filter
  (testing "proxy buffer on with body filter"
           (let [r (client/get (str "http://" *host* ":" *port* "/proxybufferonlargebodyfilter") {:coerce :unexceptional, :decompress-body false})
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================/proxybufferonlargebodyfilter =============================")
             (is (= 200 (:status r)))
             (is (= 1024000 (.length (r :body))))
             (is (= "123456789\n" (.substring (r :body) 1023990) ))))
  (testing "proxy buffer off with body filter"
           (let [r (client/get (str "http://" *host* ":" *port* "/proxybufferofflargebodyfilter") {:coerce :unexceptional, :decompress-body false})
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================/proxybufferofflargebodyfilter =============================")
             (is (= 200 (:status r)))
             (is (= 1024000 (.length (r :body))))
             (is (= "123456789\n" (.substring (r :body) 1023990) ))))
  )

(deftest ^{:remote true}  test-java-body-filter
  (testing "body filter with static file"
           (let [r (client/get (str "http://" *host* ":" *port* "/javabodyfilter/small.html") {:coerce :unexceptional :follow-redirects false :throw-exceptions false})
                 h (:headers r)
                 b (r :body)
                 eb (slurp (clojure.java.io/file "test/nginx-working-dir/testfiles/small.html"))
                 eb (.toUpperCase eb)]
             (debug-println r)
             (debug-println "=================/javabodyfilter/small.html=============================")
             (is (= 200 (:status r)))
;             (is  (= "680" (h  "content-length")) )
             (is (= 680 (.length b)))
             )

           )
    (testing "body filter with simple hello"
           (let [r (client/get (str "http://" *host* ":" *port* "/javabodyfilter/hello") {:coerce :unexceptional :follow-redirects false :throw-exceptions false})
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================/javabodyfilter/hello=============================")
             (is (= 200 (:status r)))
             (is  (= (.toUpperCase "Hello, Java & Nginx!") b) ))
           )
    
    (testing "body filter with multiple-chained response"
           (let [r (client/get (str "http://" *host* ":" *port* "/javabodyfilter/mchain") {:coerce :unexceptional :follow-redirects false :throw-exceptions false})
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================/javabodyfilter/mchain=============================")
             (is (= 200 (:status r)))
             (is  (= "FIRST PART.\r\nSECOND PART.\r\nTHIRD PART.\r\nLAST PART.\r\n" b) ))
           ) 

    (testing "body filter with utf8 multiple-chained response"
           (let [r (client/get (str "http://" *host* ":" *port* "/javabodyfilter/utf8mchain") {:coerce :unexceptional :follow-redirects false :throw-exceptions false})
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================/javabodyfilter/mchain=============================")
             (is (= 200 (:status r)))
             (is  (= "来1点中文，在UTF8分隔下，中文字符会被截到不同的CHAIN中" b) ))
           )     
  )


(deftest ^{:remote true}  test-clj-header-filter
  (testing "header filter add with static file"
           (let [r (client/get (str "http://" *host* ":" *port* "/cljfilter/small.html") {:coerce :unexceptional :follow-redirects false :throw-exceptions false})
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================/cljfilter/small.html=============================")
             (is (= 200 (:status r)))
             (is (= "Hello!" (h "xfeep-header")))
             (is  (= "680" (h  "content-length")) )
             (is (= 680 (.length b)))
             )

           )
    (testing "header filter add with simple hello"
           (let [r (client/get (str "http://" *host* ":" *port* "/cljfilter/hello") {:coerce :unexceptional :follow-redirects false :throw-exceptions false})
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================/cljfilter/hello=============================")
             (is (= 200 (:status r)))
             (is (= "Hello!" (h "xfeep-header")))
             (is  (= "Hello, Java & Nginx!" b) )             
             )
           )
    
    (testing "header filter remove & add more"
           (let [r (client/get (str "http://" *host* ":" *port* "/cljfilter/ra") {:coerce :unexceptional :follow-redirects false :throw-exceptions false})
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================/cljfilter/ra=============================")
             (is (= 200 (:status r)))
             (is (= "Hello2!" (h "xfeep-header")))
              ;;;content-type: text/html
              (is (= "text/html" (h "content-type")))
             (is  (= "Hello, Java & Nginx!" b) ))
           )  
    
     (testing "header filter with exception"
           (let [r (client/get (str "http://" *host* ":" *port* "/cljfilter/ex0") {:coerce :unexceptional :follow-redirects false :throw-exceptions false})
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================/cljfilter/ex0=============================")
             (is (= 500 (:status r)))
              ;;;content-type: text/html
             (is  (= "java.lang.RuntimeException: Hello, exception in header filter!" (first-line b)) ))
           ) 
 
     (testing "header filter with exception 1"
           (let [r (client/get (str "http://" *host* ":" *port* "/javafilter/ex1") {:coerce :unexceptional :follow-redirects false :throw-exceptions false})
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================/cljfilter/ex1=============================")
             (is (= 500 (:status r)))
              ;;;content-type: text/html
             (is  (= "java.lang.RuntimeException: Hello, exception in header filter!" (first-line b)) ))
           )            
     
      (testing "header filter with remote access 0"
           (let [r (client/get (str "http://" *host* ":" *port* "/cljfilter/rc0") {:coerce :unexceptional :follow-redirects false :throw-exceptions false})
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================/cljfilter/rc0=============================")
             (is (= 404 (:status r)))
             (is (= "77341" (h "remote-content-length"))))
           )  
      
      (testing "header filter with remote access & static file"
           (let [r (client/get (str "http://" *host* ":" *port* "/cljfilter/rc1/small.html") {:coerce :unexceptional :follow-redirects false :throw-exceptions false})
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================/cljfilter/rc1=============================")
             (is (= 200 (:status r)))
              ;;;content-type: text/html
              (is (= "77341" (h "remote-content-length")))
             (is  (= "680" (h  "content-length")) )
             (is (= 680 (.length b))))
           )  
      
     (testing "header filter with remote access & dynamic content"
           (let [r (client/get (str "http://" *host* ":" *port* "/cljfilter/rc2") {:coerce :unexceptional :follow-redirects false :throw-exceptions false})
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================/cljfilter/rc2=============================")
             (is (= 200 (:status r)))
             (is (= "77341" (h "remote-content-length")))
              ;;;content-type: text/html
             (is (= "Hello, Java & Nginx!" b) ))
           )        
  )

(deftest ^{:remote true}  test-clj-body-filter
  (testing "body filter with static file"
           (let [r (client/get (str "http://" *host* ":" *port* "/cljbodyfilter/small.html") {:coerce :unexceptional :follow-redirects false :throw-exceptions false})
                 h (:headers r)
                 b (r :body)
                 eb (slurp (clojure.java.io/file "test/nginx-working-dir/testfiles/small.html"))
                 eb (.toUpperCase eb)]
             (debug-println r)
             (debug-println "=================/cljbodyfilter/small.html=============================")
             (is (= 200 (:status r)))
;             (is  (= "680" (h  "content-length")) )
             (is (= 680 (.length b)))
             )

           )
    (testing "body filter with simple hello"
           (let [r (client/get (str "http://" *host* ":" *port* "/cljbodyfilter/hello") {:coerce :unexceptional :follow-redirects false :throw-exceptions false})
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================/cljbodyfilter/hello=============================")
             (is (= 200 (:status r)))
             (is  (= (.toUpperCase "Hello, Java & Nginx!") b) ))
           )
    
    (testing "body filter with multiple-chained response"
           (let [r (client/get (str "http://" *host* ":" *port* "/cljbodyfilter/mchain") {:coerce :unexceptional :follow-redirects false :throw-exceptions false})
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================/cljbodyfilter/mchain=============================")
             (is (= 200 (:status r)))
             (is  (= "FIRST PART.\r\nSECOND PART.\r\nTHIRD PART.\r\nLAST PART.\r\n" b) ))
           ) 

    (testing "body filter with utf8 multiple-chained response"
           (let [r (client/get (str "http://" *host* ":" *port* "/cljbodyfilter/utf8mchain") {:coerce :unexceptional :follow-redirects false :throw-exceptions false})
                 h (:headers r)
                 b (r :body)]
             (debug-println r)
             (debug-println "=================/cljbodyfilter/mchain=============================")
             (is (= 200 (:status r)))
             (is  (= "来1点中文，在UTF8分隔下，中文字符会被截到不同的CHAIN中" b) ))
           )     
  )

(deftest ^{:remote true :websocket true} test-websocket-basic
  (testing "/java-ws/echo"
         (let [base (str "ws://" *host* ":" *port* "/java-ws/echo")
               msg "hello, nginx-clojure & websocket!"
               result (promise)
               ws-client (ws/connect base
                                     :on-error #(deliver result %)
                                     :on-close (fn [c r] (deliver result (str c ":" r)))
                                     :on-receive #(deliver result %))
               ]
           (debug-println "===================/java-ws/echo=======================")
           (ws/send-msg ws-client msg)
           (is (= msg @result))
           (ws/close ws-client)))
  (testing "/java-ws/nu-echo"
         (let [base (str "ws://" *host* ":" *port* "/java-ws/nu-echo")
               msg "hello, nginx-clojure & websocket!"
               result (promise)
               ws-client (ws/connect base
                                     :on-error #(deliver result %)
                                     :on-close (fn [c r] (deliver result (str c ":" r)))
                                     :on-receive #(deliver result %))
               ]
           (debug-println "===================/java-ws/nu-echo=======================")
           (ws/send-msg ws-client msg)
           (is (= msg @result))
           (ws/close ws-client)))  
  (testing "/java-ws/wh-echo"
         (let [base (str "ws://" *host* ":" *port* "/java-ws/wh-echo")
               msg (clojure.string/join  (for [i (range 9216)] "a"))
               result (promise)
               ws-client (ws/connect base
                                     :on-error #(deliver result %)
                                     :on-close (fn [c r] (deliver result (str c ":" r)))
                                     :on-receive #(deliver result %))
               ]
           (debug-println "===================/java-ws/wh-echo=======================")
           (ws/send-msg ws-client msg)
           (is (= msg @result))
           (ws/close ws-client))
         (let [base (str "ws://" *host* ":" *port* "/java-ws/wh-echo")
               msg (clojure.string/join  (for [i (range 9217)] "a"))
               result (promise)
               ws-client (ws/connect base
                                     :on-error #(deliver result %)
                                     :on-close (fn [c r] (deliver result (str c ":" r)))
                                     :on-receive #(deliver result %))
               ]
           (debug-println "===================/java-ws/wh-echo=======================")
           (ws/send-msg ws-client msg)
           (is (= "1000:" @result))
           (ws/close ws-client)))  
  (testing "/ringCompojure/ws-echo"
         (let [base (str "ws://" *host* ":" *port* "/ringCompojure/ws-echo")
               msg "hello, nginx-clojure & websocket!"
               result (promise)
               ws-client (ws/connect base
                                     :on-error #(deliver result %)
                                     :on-close (fn [c r] (deliver result (str c ":" r)))
                                     :on-receive #(deliver result %))
               ]
           (debug-println "===================/ringCompojure/ws-echo=======================")
           (ws/send-msg ws-client msg)
           (Thread/sleep 2000)
           (ws/close ws-client)
           (is (= msg @result))))
  (testing "/ringCompojure/ws-remote"
       (let [base (str "ws://" *host* ":" *port* "/ringCompojure/ws-remote")
             msg "http://www.apache.org/dist/httpcomponents/httpclient/RELEASE_NOTES-4.2.x.txt"
             result (promise)
             ws-client (ws/connect base
                                     :on-error #(deliver result %)
                                     :on-close (fn [c r] (deliver result (str c ":" r)))
                                     :on-receive #(deliver result %))
             ]
         (debug-println "===================/ringCompojure/ws-remote=======================")
         (ws/send-msg ws-client msg)
         (Thread/sleep 1000)
         (let [content (:body (client/get "http://www.apache.org/dist/httpcomponents/httpclient/RELEASE_NOTES-4.2.x.txt"))]
           ;(println @result)
           (Thread/sleep 8000)
           (ws/close ws-client)
           (is (= content @result)))))
)


(deftest ^{:remote true :shared-map true} test-shared-map
  (doseq [target ["tinyMap" 
                  "hashMap"]]
    (let [base (str "http://" *host* ":" *port* "/java-sharedmap/" target)]
      (client/get base {:coerce :unexceptional, :query-params {:op "clear"}})
      (testing (str target "-strstr")
         (let [r (client/get base {:coerce :unexceptional, :query-params {:op "put" :key "nginx-clojure" :val "shared map is OK?"}})]
           (is (= 200 (:status r)))
           (is (= "put:nginx-clojure:shared map is OK?, old=null, size=1" (:body r))))
         (let [r (client/get base {:coerce :unexceptional, :query-params {:op "get" :key "nginx-clojure"}})]
           (is (= 200 (:status r)))
           (is (= "get:nginx-clojure:shared map is OK?, size=1" (:body r))))
         (let [r (client/get base {:coerce :unexceptional, :query-params {:op "put" :key "nginx-clojure" :val "OK!"}})]
           (is (= 200 (:status r)))
           (is (= "put:nginx-clojure:OK!, old=shared map is OK?, size=1" (:body r))))
         (let [r (client/get base {:coerce :unexceptional, :query-params {:op "get" :key "nginx-clojure"}})]
           (is (= 200 (:status r)))
           (is (= "get:nginx-clojure:OK!, size=1" (:body r))))   
         (let [r (client/get base {:coerce :unexceptional, :query-params {:op "remove" :key "nginx-clojure"}})]
           (is (= 200 (:status r)))
           (is (= "remove:nginx-clojure:OK!, size=0" (:body r))))
         (let [r (client/get base {:coerce :unexceptional, :query-params {:op "remove" :key "nginx-clojure"}})]
           (is (= 200 (:status r)))
           (is (= "remove:nginx-clojure:null, size=0" (:body r))))
      )
      (testing (str target "-intstr")
         (let [r (client/get base {:coerce :unexceptional, :query-params {:op "iput" :key 2147483647 :val "shared map is OK?"}})]
           (is (= 200 (:status r)))
           (is (= "iput:2147483647:shared map is OK?, old=null, size=1" (:body r))))
         (let [r (client/get base {:coerce :unexceptional, :query-params {:op "iget" :key 2147483647}})]
           (is (= 200 (:status r)))
           (is (= "iget:2147483647:shared map is OK?, size=1" (:body r))))
         (let [r (client/get base {:coerce :unexceptional, :query-params {:op "iput" :key 2147483647 :val "OK!"}})]
           (is (= 200 (:status r)))
           (is (= "iput:2147483647:OK!, old=shared map is OK?, size=1" (:body r))))
         (let [r (client/get base {:coerce :unexceptional, :query-params {:op "iget" :key 2147483647}})]
           (is (= 200 (:status r)))
           (is (= "iget:2147483647:OK!, size=1" (:body r))))   
         (let [r (client/get base {:coerce :unexceptional, :query-params {:op "iremove" :key 2147483647}})]
           (is (= 200 (:status r)))
           (is (= "iremove:2147483647:OK!, size=0" (:body r))))
         (let [r (client/get base {:coerce :unexceptional, :query-params {:op "iremove" :key 2147483647}})]
           (is (= 200 (:status r)))
           (is (= "iremove:2147483647:null, size=0" (:body r))))
      ) 
      (testing (str target "-longstr")
         (let [r (client/get base {:coerce :unexceptional, :query-params {:op "lput" :key 9223372036854775807 :val "shared map is OK?"}})]
           (is (= 200 (:status r)))
           (is (= "lput:9223372036854775807:shared map is OK?, old=null, size=1" (:body r))))
         (let [r (client/get base {:coerce :unexceptional, :query-params {:op "lget" :key 9223372036854775807}})]
           (is (= 200 (:status r)))
           (is (= "lget:9223372036854775807:shared map is OK?, size=1" (:body r))))
         (let [r (client/get base {:coerce :unexceptional, :query-params {:op "lput" :key 9223372036854775807 :val "OK!"}})]
           (is (= 200 (:status r)))
           (is (= "lput:9223372036854775807:OK!, old=shared map is OK?, size=1" (:body r))))
         (let [r (client/get base {:coerce :unexceptional, :query-params {:op "lget" :key 9223372036854775807}})]
           (is (= 200 (:status r)))
           (is (= "lget:9223372036854775807:OK!, size=1" (:body r))))       
         (let [r (client/get base {:coerce :unexceptional, :query-params {:op "lremove" :key 9223372036854775807}})]
           (is (= 200 (:status r)))
           (is (= "lremove:9223372036854775807:OK!, size=0" (:body r))))
         (let [r (client/get base {:coerce :unexceptional, :query-params {:op "lremove" :key 9223372036854775807}})]
           (is (= 200 (:status r)))
           (is (= "lremove:9223372036854775807:null, size=0" (:body r))))
      )
      (testing (str target "-strint")
         (let [r (client/get base {:coerce :unexceptional, :query-params {:op "puti" :key "int"  :val 2147483647}})]
           (is (= 200 (:status r)))
           (is (= "puti:int:2147483647, old=0, size=1" (:body r))))
         (let [r (client/get base {:coerce :unexceptional, :query-params {:op "get" :key "int"}})]
           (is (= 200 (:status r)))
           (is (= "get:int:2147483647, size=1" (:body r))))
         (let [r (client/get base {:coerce :unexceptional, :query-params {:op "puti" :key "int" :val 2147483646}})]
           (is (= 200 (:status r)))
           (is (= "puti:int:2147483646, old=2147483647, size=1" (:body r))))
         (let [r (client/get base {:coerce :unexceptional, :query-params {:op "get" :key "int"}})]
           (is (= 200 (:status r)))
           (is (= "get:int:2147483646, size=1" (:body r))))       
         (let [r (client/get base {:coerce :unexceptional, :query-params {:op "remove" :key "int"}})]
           (is (= 200 (:status r)))
           (is (= "remove:int:2147483646, size=0" (:body r))))
         (let [r (client/get base {:coerce :unexceptional, :query-params {:op "remove" :key "int"}})]
           (is (= 200 (:status r)))
           (is (= "remove:int:null, size=0" (:body r))))
      ) 
      (testing (str target "-strlong")
         (let [r (client/get base {:coerce :unexceptional, :query-params {:op "putl" :key "long"  :val 9223372036854775807}})]
           (is (= 200 (:status r)))
           (is (= "putl:long:9223372036854775807, old=0, size=1" (:body r))))
         (let [r (client/get base {:coerce :unexceptional, :query-params {:op "get" :key "long"}})]
           (is (= 200 (:status r)))
           (is (= "get:long:9223372036854775807, size=1" (:body r))))
         (let [r (client/get base {:coerce :unexceptional, :query-params {:op "putl" :key "long" :val 9223372036854775806}})]
           (is (= 200 (:status r)))
           (is (= "putl:long:9223372036854775806, old=9223372036854775807, size=1" (:body r))))
         (let [r (client/get base {:coerce :unexceptional, :query-params {:op "get" :key "long"}})]
           (is (= 200 (:status r)))
           (is (= "get:long:9223372036854775806, size=1" (:body r))))      
         (let [r (client/get base {:coerce :unexceptional, :query-params {:op "remove" :key "long"}})]
           (is (= 200 (:status r)))
           (is (= "remove:long:9223372036854775806, size=0" (:body r))))
         (let [r (client/get base {:coerce :unexceptional, :query-params {:op "remove" :key "long"}})]
           (is (= 200 (:status r)))
           (is (= "remove:long:null, size=0" (:body r))))
      )
      (testing (str target "-strbyte[]")
         (let [r (client/get base {:coerce :unexceptional, :query-params {:op "puta" :key "nginx-clojure" :val "shared map is OK?"}})]
           (is (= 200 (:status r)))
           (is (= "puta:nginx-clojure:shared map is OK?, old=null, size=1" (:body r))))
         (let [r (client/get base {:coerce :unexceptional, :query-params {:op "get" :key "nginx-clojure"}})]
           (is (= 200 (:status r)))
           (is (= "get:nginx-clojure:shared map is OK?, size=1" (:body r))))
         (let [r (client/get base {:coerce :unexceptional, :query-params {:op "puta" :key "nginx-clojure" :val "OK!"}})]
           (is (= 200 (:status r)))
           (is (= "puta:nginx-clojure:OK!, old=shared map is OK?, size=1" (:body r))))
         (let [r (client/get base {:coerce :unexceptional, :query-params {:op "get" :key "nginx-clojure"}})]
           (is (= 200 (:status r)))
           (is (= "get:nginx-clojure:OK!, size=1" (:body r))))   
         (let [r (client/get base {:coerce :unexceptional, :query-params {:op "remove" :key "nginx-clojure"}})]
           (is (= 200 (:status r)))
           (is (= "remove:nginx-clojure:OK!, size=0" (:body r))))
         (let [r (client/get base {:coerce :unexceptional, :query-params {:op "remove" :key "nginx-clojure"}})]
           (is (= 200 (:status r)))
           (is (= "remove:nginx-clojure:null, size=0" (:body r))))
      )
      (testing (str target "-byte[]str")
         (let [r (client/get base {:coerce :unexceptional, :query-params {:op "aput" :key "nginx-clojure" :val "shared map is OK?"}})]
           (is (= 200 (:status r)))
           (is (= "aput:nginx-clojure:shared map is OK?, old=null, size=1" (:body r))))
         (let [r (client/get base {:coerce :unexceptional, :query-params {:op "aget" :key "nginx-clojure"}})]
           (is (= 200 (:status r)))
           (is (= "aget:nginx-clojure:shared map is OK?, size=1" (:body r))))
         (let [r (client/get base {:coerce :unexceptional, :query-params {:op "aput" :key "nginx-clojure" :val "OK!"}})]
           (is (= 200 (:status r)))
           (is (= "aput:nginx-clojure:OK!, old=shared map is OK?, size=1" (:body r))))
         (let [r (client/get base {:coerce :unexceptional, :query-params {:op "aget" :key "nginx-clojure"}})]
           (is (= 200 (:status r)))
           (is (= "aget:nginx-clojure:OK!, size=1" (:body r))))   
         (let [r (client/get base {:coerce :unexceptional, :query-params {:op "aremove" :key "nginx-clojure"}})]
           (is (= 200 (:status r)))
           (is (= "aremove:nginx-clojure:OK!, size=0" (:body r))))
         (let [r (client/get base {:coerce :unexceptional, :query-params {:op "aremove" :key "nginx-clojure"}})]
           (is (= 200 (:status r)))
           (is (= "aremove:nginx-clojure:null, size=0" (:body r))))
      )
      (testing (str target "-perfi")
         (let [r (client/get base {:coerce :unexceptional, :query-params {:op "perfi" :key 2147483647 :val "shared map is OK?"}})]
           (is (= 200 (:status r))))
      )      
      )))

;eg. (concurrent-run 10 (run-tests 'nginx.clojure.test-all))
(defmacro concurrent-run 
  [n, form]
  (list 'apply 'pcalls (list 'repeat n (list 'fn [] form)) ))

;(binding [*host* "macosx"] (concurrent-test 4))
;(binding [*host* "cxp"] (concurrent-test 4))
(defn concurrent-test
  [n]
  (concurrent-run n (run-tests 'nginx.clojure.test-all)))




