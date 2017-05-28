[Nginx-Clojure](http://nginx-clojure.github.io)
=============

![Alt text](logo.png) [Nginx-Clojure](http://nginx-clojure.github.io) is a [Nginx](http://nginx.org/) module for embedding Clojure or Java or Groovy programs, typically those [Ring](https://github.com/ring-clojure/ring/blob/master/SPEC) based handlers.


Core Features
=================

The latest release is v0.4.5, more detail changes about it can be found from [Release History](//nginx-clojure.github.io/downloads.html).

1. Compatible with [Ring](https://github.com/ring-clojure/ring/blob/master/SPEC) and obviously supports those Ring based frameworks, such as Compojure etc.
1. Http Services by  using Clojure / Java / Groovy to write simple handlers for http services.
1. Nginx Access Handler by Clojure / Java / Groovy
1. Nginx Header Filter by Clojure / Java / Groovy
1. **_NEW_**: Nginx Body Filter by Clojure / Java / Groovy
1. Pub/Sub Among Nginx Worker Processes
1. Shared Map based on shared memory & Shared Map based Ring session store
1. Support Sente, see [this PR](https://github.com/ptaoussanis/sente/pull/160)
1. Support Per-message Compression Extensions (PMCEs) for WebSocket
1. APIs for Embedding Nginx-Clojure into a Standard Clojure/Java/Groovy App
1. Server Side Websocket
1. A build-in Jersey container to support java standard RESTful web services (JAX-RS 2.0)
1. Tomcat 8 embedding support (so servlet 3.1/jsp/sendfile/JSR-356 websocket work within nginx!)
1. Dynamic proxying by using Clojure / Java / Groovy to write a simple nginx rewrite handler to set var or return errors before proxy pass or content ring handler
1. Non-blocking coroutine based socket which is Compatible with Java Socket API and works well with largely existing java library such as apache http client, mysql jdbc drivers. 
With this feature  one java main thread can handle thousands of connections.
1. Handle multiple sockets parallel in sub coroutines, e.g. we can invoke two remote services at the same time.
1. Asynchronous callback API of socket/Channel  for some advanced usage
1. Long Polling & Server Sent Events
1. Run initialization clojure code when nginx worker starting
1. Support user defined http request method
1. Compatible with the Nginx lastest stable version 1.12.0. (Nginx 1.8.x, Nginx 1.6.x, 1.4.x is also ok, older version is not tested and maybe works.)
1. One of  benifits of [Nginx](http://nginx.org/) is worker processes are automatically restarted by a master process if they crash
1. Utilizes lazy headers and direct memory operation between [Nginx](http://nginx.org/) and JVM to fast handle dynamic contents from Clojure or Java code.
1. Utilizes [Nginx](http://nginx.org/) zero copy file sending mechanism to fast handle static contents controlled by Clojure or Java code.
1. Supports Linux x64, Linux x86 32bit, Win32, Win64  and Mac OS X. Freebsd version can also be got from Freebsd ports.

By the way it is very fast, the benchmarks can be found [HERE(with wrk2)](https://github.com/ptaoussanis/clojure-web-server-benchmarks/).
Jar Repository
================

Nginx-Clojure has already been published to https://clojars.org/ whose maven repository is 

```xml
<repository>
  <id>clojars.org</id>
  <url>http://clojars.org/repo</url>
</repository>
``` 

After adding clojars repository, you can reference nginx-clojure 0.4.5 , e.g.

 Leiningen (clojure, no need to add clojars repository which is a default repository for Leiningen) 
-----------------
 
```clojure
[nginx-clojure "0.4.5"]
```
Gradle (groovy/java)
-----------------
 
```
compile "nginx-clojure:nginx-clojure:0.4.5"
```
Maven
-----------------
 
```xml
<dependency>
  <groupId>nginx-clojure</groupId>
  <artifactId>nginx-clojure</artifactId>
  <version>0.4.5</version>
</dependency>
```

More Documents
=================

More Documents can be found from its web site [nginx-clojure.github.io](http://nginx-clojure.github.io/)


License
=================
Copyright © 2013-2017 Zhang, Yuexiang (xfeep) and released under the BSD 3-Clause license.

This program uses:
* Re-rooted ASM bytecode engineering library which is distributed under the BSD 3-Clause license
* Modified Continuations Library Written by Matthias Mann  is distributed under the BSD 3-Clause license
