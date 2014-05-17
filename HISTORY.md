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



