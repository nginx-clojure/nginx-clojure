Downloads & Release History
=============

1. [Binaries of Releases](http://sourceforge.net/projects/nginx-clojure/files/)
1. [Sources of Releases](https://github.com/nginx-clojure/nginx-clojure/releases)

## 0.5.3 (2022-03-10)

1. Bug Fix: #256 NginxClojureAsynSocket isClosed is not return correct result
1. Binaries Distribution: Built with Nginx v1.20.2


## 0.5.2 (2020-12-23)

1. Bug Fix: #234 Try to fix no response when NGX_AGAIN return at next header filter
1. Bug Fix: #233 Fix compiler warnings when there's no zlib found
1. Enhancement: Delayed update to improve setVariable/set-ngx-var! performance at thread-pool mode
1. Example Project: Add an example project for Jersey & Spring DI
1. Example Project: Add example for integration with Spring framework
1. Binaries Distribution: Built with Nginx v1.18.0


## 0.5.1 (2019-11-23)

1. Bug Fix: Connection hangs with header filter at thread-pool mode #209 #153
1. Bug Fix: Body filter hangs when body size is larger than the value specified in proxy_buffers #219
1. Bug Fix: Segment fault caused by request is marked as closed too late #222
1. Code Style: Fix generic warnings in java code code-style #223 (Thanks to Michael @mgoblin)
1. Bug Fix: NPE caused by damaged memory because unsafe modify response headers #198 
1. Bug Fix: Wrong response for uncompressed message with PMCE enabled
1. CI: both auto-triggered unit test and integration test use travis-ci

## 0.5.0 (2019-10-26)

1. New Feature: Java 9, 10, 11, 12 support. But Java 6 and Java 7 are deprecated now.
1. New Feature: Log handler
1. New Feature: HTTP V2 support (thanks to Nginx v1.14.2)
1. Bug Fix: Memory leak with file handler #180
1. Bug Fix: Zero buffer error when hijack_send empty string #181
1. Bug Fix: Mysql driver issue about jdbc4 flag
1. Bug Fix: ContainsKey of nginx shared map
1. Bug Fix: NginxRequest.setVariable in a rewrite handler will hang
1. Bug Fix: Segmentation fault on shutdown
1. Bug Fix: Make clojure request map immutable for compojure
1. Enhancement: API for discarding request body (request.discardRequestBody())
1. Enhancement: Coroutine support for cascade constructor invoking
1. Enhancement: Configurable headers/variables prefetch for more safety in multithreaded mode 
1. Binaries Distribution: built with Nginx v1.14.2

## 0.4.5 (2017-05-28)

1. New Feature: Support to be compiled as Nginx dynamic module, thanks to [Andrew Hutchings](https://github.com/LinuxJedi)
1. Bug Fix: Cannot add multiple Cookies in a response
1. Bug Fix: Too many empty chunks are passed to Body filter & some body data lost
1. Enhancement: [Nginx-Jersey] Support jersey application sub class
1. Enhancement: Try to use enviroment variable JAVA_HOME to detect jvm when jvm_path is auto
1. Enhancement: NginxSharedHashMap.keySet/values/entrySet for debug/test usage.
1. Bug Fix: Can not use more than two shared maps.
1. Bug Fix: NullPointerExecption will happen when multiple rewrite handlers are invoked for one request
1. Bug Fix: Can't access ring request data in Sente handler. (content_handler_property fore-prefetch-all-properties true;)
1. Enhancement: Compile against Nginx 1.11 & Nginx 1.12
1. Bug Fix: Nginx reload will cause connection reset without response
1. Bug Fix: Header filter can not change response status from upstream
1. Bug Fix: body filters sometimes crash under thread pool mode
1. Binaries Distribution:  built with the latest stable Nginx v1.12.0 & openssl v1.1.0e

## 0.4.4 (2016-03-04)

1. New Feature: experimental nginx body filter by Java/Clojure/Groovy (issue #107)
1. New Feature: read request body by event callback (issue #109)
1. Bug Fix: 500 (internal server error) returns when committing 2000+ files to nginx as a proxy for apache mod_dav_svn (issue #106)

## 0.4.3 (2015-10-25)
1. New Feature: Add directive [jvm_classpath][] which supports wildcard character * (issue #95)
1. New Feature: Add directive [jvm_classpath_check][] which is enabled by default and when it is enabled access permission about classpaths will be checked.
1. New Feature: Add [NginxPubSubTopic(Java)/PubSubTopic(Clojure)][] to simplify handling messages among Nginx worker processes. (issue #97)
1. New Feature: [Shared Map based on shared memory][] (issue #96) and it has two implementations : tinymap & hashmap.
1. New Feature: [Shared Map based Ring session store][] (issue #98)
1. Enhancement: on-broadcast-event-decode!/on-broadcast! returns a removal function which can be used to remove the registered decoder/listener
1. Enhancement: embedded nginx-clojure becomes friendly to mock tests and  also fix issue #101
1. Bug Fix: After stopping an embedded Nginx-Clojure server keep-alived connections become CLOSE_WAIT.
1. Bug Fix: HackUtil.decode decodes unnecessary chars when several strings share one char[] generally on JDK 6
1. Bug Fix: jvm crashes with thread pool mode when open_file_cache is enabled.
1. Bug Fix: Fix compile errors when no sha1-implementation/zlib can be found (issue #99)
1. Example Project: Add [an example project about clojure web dev][] to show how to develop & deploy with Nginx-Clojure. Thanks to [Peter Taoussanis](https://github.com/ptaoussanis) without whose
comments there would not be such example project. (issue #91)
1. Documents: Add [Directives Reference](http://nginx-clojure.github.io/directives.html)


## 0.4.2 (2015-08-31)

1. New Feature: Support Sente (issue #87, see [this PR](https://github.com/ptaoussanis/sente/pull/160))
1. New Feature: Per-message Compression Extensions (PMCEs) for WebSocket (issue #88)
1. New Feature: Add `add-aggregated-listener!` in HttpServerChannel to makes handling small but fragmented websocket messages easier by clojure.
1. Enhancement: Support to build on a Linux ARM machine
1. Bug Fix: WebSocket and Server Channel do not Work with Some Ring Middlewares (issue #89)
1. Bug Fix: Autodetect jvm_path doesn't work sometimes

## 0.4.1 (2015-08-12)

1. New Feature: Coroutine based socket supports unix domain socket
1. New Feature: APIs for Embedding Nginx-Clojure into a standard Clojure/Java/Groovy App (issue #86)
1. New Feature: Autodetect jvm_path (issue #85)
1. New Feature: Support to use annotation to mark a class or method to be suspenable in coroutine context (issue #84)
1. Enhancement: Auto send error when meets a non websocket request with `auto_upgrade_ws` is on 
1. Enhancement: Add `websocket-upgrade!` to server channel API
1. Enhancement: Add `WholeMessageAdapter` to make handling small websocket messages easier.
1. Bug Fix: NginxHttpServerChannel.write(ByteBuffer buf) does not reset the buffer's position (issue #83)
1. Bug Fix: No access to tomcat server 8.24 from nginx-clojure (issue #82)
1. Binaries Distribution: Including some java sources for easy debug.
1. Build Script: Autodetect JNI header files

## 0.4.0 (2015-07-06)

1. New Feature: Server Side Websocket (issue #73)
1. New Feature: A build-in Jersey container to support java standard RESTful web services (JAX-RS 2.0) (issue #74)
1. New Feature: Tomcat 8 embedding support (so servlet 3.1/jsp/sendfile/JSR-356 websocket work within nginx!) (issue #67)
1. New Feature: Coroutined Based Client Socket Supports to Bind to Specified IP Address (issue #69)
1. New Feature: Handler's Property Configuration (issue #66)
1. Enhancement: NginxHttpServerChannel can work with Rewrite Handler or Access Handler (issue #79)
1. Enhancement: Configurable Write Buffer Size for SSE or Websocket (issue #76)
1. Bug Fix: When we do not configure jvm_path proxy_pass will not work (issue #72)
1. Bug Fix: nginx worker restart when get the value of header X-Forwarded-For (issue #70)
1. Bug Fix: proxy_cache_path causes crash (issue #64)
1. Bug Fix: send_timeout does not take effect with NginxHttpServerChannel (issue #78)
1. Bug Fix: Waving tool generates wrong wave information of fuzzing classes (issue #80)
1. Documents : Release History link in README (issue #68)
1. Binaries Distribution: built with The latest stable Nginx v1.8.0 which released at 2015-04-21.


## 0.3.0 (2014-12-11)

1. Discard: Directive `clojure`, `clojure_code` are no longer supported, use `handler_type`/`content_handler_type`, 
`handler_name`/`content_handler_name`, `handler_code`/`content_handler_code` instead.
1. Discard:  Now `handler_***` can not be used to declare a nginx worker  initialization handler, use `jvm_init_handler_*** instead.
1. New Feature:  Supports  writing nginx access handler  by  java/clojure/groovy (issue #53)
1. New Feature:  Supports  writing nginx header filter  by  java/clojure/groovy (issue #55)
1. New Feature:  Add new directive `max_balanced_tcp_connections`  to make nginx auto set worker_connections.
1. Enhancement:  For Java We can use  r.setVariable,  r.getVariable now if r is an instance of NginxJavaRequest.
1. Deprecated Directives: handler_type, handler_name, handler_code  are deprecated and maybe will be removed in the next version, add new directive content_handler_type, content_handler_code, content_handler_code
1. New Directives: rewrite_handler_type, access_handler_type, header_filter_type, body_filter_type
1. New Feature: Supports nested locations (issue #56)
1. Bug Fix : uppercase letters in nginx variable name can not work (issue #54)
1. Bug Fix: The first registered handler will not work if there 's a asynchronous reading of request body (issue #51)
1. Enhancement: `handlers_lazy_init` can be used to make handler initialized lazily or eagerly  (issue #52)


## 0.2.7 (2014-11-11)

1. New Feature: Compiling option for  disabling all functions silently when JVM_PATH not configured. (issue #47)
1. New Feature: Access request BODY in rewrite handler (issue #49)
1. Enhancement : Optimization of encoding String to Nginx temp buffer chain to reduce  Java heap memory usage and improve the performance.


## 0.2.6 (2014-10-10)

1. Fix Bug: rewrite handler does not handle write event correctly with thread pool mode or coroutine mode (issue #43)
1. Fix Bug: built-in jvm variable #{pno} doesn't work (issue #44)
1. Fix Bug: rewrite_handler_name does not work without content handler (issue #45). Thanks [Eric Kubacki](https://github.com/ekubacki) for finding this bug.
1. Fix Bug: rewrite handler does not handle write event correctly with thread pool mode or coroutine mode (issue #43)
1. Documents : Correct some inaccuracies and add section about logging in Chapter [More about Nginx-Clojure](http://nginx-clojure.github.io/more.html#user-content-37--about-logging)
1. Binaries: built with The latest stable Nginx v1.6.2 which released at 2014-09-16.



## 0.2.5 (2014-09-07)

1. New Feature: Reference variables in jvm_options & different jvm debug ports for jvm processes (issue #42)
1. New Feature: Server Sent Events(SSE) & Long polling (issue #41, issue #36)
1. New Feature: Supports 64-bit JDK on 64-bit Windows (issue #40)
1. New Feature: Coroutine based socket supports JDK8 (issue #39)
1. New Feature: More easier to archive Sub/Pub services with Broadcast Events to all Nginx workers (issue #39)
1. New Feature: Asynchronous Channel a wrapper of asynchronous socket to make the usage easier (issue #37)
1. Enhancement: Fix--On Windows a little many write events happen and these events seem useless (issue #35)


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

[jvm_classpath_check]: //nginx-clojure.github.io/directives.html#jvm_classpath_check
[jvm_classpath]: //nginx-clojure.github.io/directives.html#jvm_classpath
[NginxPubSubTopic(Java)/PubSubTopic(Clojure)]: //nginx-clojure.github.io/subpub.html
[an example project about clojure web dev]: https://github.com/nginx-clojure/nginx-clojure/tree/master/example-projects/clojure-web-example
[Shared Map based on shared memory]: //nginx-clojure.github.io/sharedmap.html
[Shared Map based Ring session store]: //nginx-clojure.github.io/sharedmap.html
