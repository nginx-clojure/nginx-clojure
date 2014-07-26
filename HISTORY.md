## 0.2.4 (2014-07-25)

1. New Feature: Support Groovy - another dynamic jvm language (issue #34)
2. Fix bug: Slow Memory Leak for Coroutine based Socket bug (issue #32 )
3. Fix bug: Should Clone ThreadLocals for Coroutines (issue #31)
4. New Feature: More friendly to java users who maybe know nothing about clojure feature (issue #29)
5. Five new nginx directives `handler_type`, `handler_name`, `handler_code`, `rewrite_handler_name`, `rewrite_handler_code`. 
Make Clojure/Java/Groovy handler configurations have the same form. e.g. The old pair of nginx directives `clojure`, `clojure_code` is equivalent to `handler_type='clojure'` + `handler_code`.

## 0.2.3 (2014-07-05)

1. Fix issue After invoking on coroutine based socket nginx worker will exit and be recreated when network is disabled (issue #26)
2. Fix issue PATCH loses the data payload (issue #27)
3. Support user defined http request method (issue #28 )
4. Fix issue Nginx worker crashes when to fetch http header "authorization" from request (issue #30)

## 0.2.2 (2014-05-31)

1. Fix bug of with Compojure 1.1.5 + Apache Solrj 4.3.0 + httpclient 4.3.2 NPE happens first time then everything is OK (issue #22)
2. Verifying option for auto generated waving configurations needed by coroutine based socket (issue #23)

## 0.2.1 (2014-05-17)

1. Support to close coroutine based socket from non-main thread (issue #19)
2. Auto generated waving class configurations about Proxy InvocationHandler instance (issue #17 )
3. Supports auto turn on thread pool mode when turning on Run Tool Mode feature (issue #16 )
4. Fix bug of reading from coroutine based socket inputstream returns 0 when eof, should return -1 (issue #15)
5. Handle multiple sockets parallel in sub coroutines, e.g. we can invoke two remote services at the same time feature (issue #14)
6. Support nginx rewrite handler to set var before proxy pass (issue #3)

## 0.2.0 (2014-04-25)

1. non-blocking socket based on coroutine and compatible with largely existing java library such as apache http client, mysql jdbc drivers
1. asynchronous callback API of socket for some advanced usage
1. run initialization clojure code when nginx worker starting
1. provide a build-in tool to make setting of coroutine based socket easier
1. support Linux 32bit x86 now
1. publish [binary release compiled with lastes stable nginx 1.6.0](https://sourceforge.net/projects/nginx-clojure/files/) about Linux x64, Linux i586, Win32 & MacOS X

## 0.1.2 (2014-02-03)

1. fix [#2 Problems with HTTP Redirect 302](/nginx-clojure/nginx-clojure/issues/2)
1. header names are  case-insensitive now.
1. publish [binary release](https://sourceforge.net/projects/nginx-clojure/files/) about Linux x64, Win32 & MacOS X


## 0.1.1 (2014-01-20)

1. Supports InputStream, ISeq & recursive ISeq in Response Body. 
1. Auto maintains HTTP last-modified header for multiple files in Response Body


## 0.1.0 (2014-01-09)

1. Compitiable with Ring Spec (1.1) 
1. Supports Java Thread Pool for handle request
1. Fast Static File Service



