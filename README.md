[Nginx-Clojure](http://nginx-clojure.github.io)
=============

![Alt text](logo.png) [Nginx-Clojure](http://nginx-clojure.github.io) is a [Nginx](http://nginx.org/) module for embedding Clojure or Java or Groovy programs, typically those [Ring](https://github.com/ring-clojure/ring/blob/master/SPEC) based handlers.

There are some core features :

1. Compatible with [Ring](https://github.com/ring-clojure/ring/blob/master/SPEC) and obviously supports those Ring based frameworks, such as Compojure etc.
1. Use Clojure / Java / Groovy(**_NEW_** ) to write simple handlers for http services.
1. Use Clojure / Java / Groovy(**_NEW_** ) to write a simple nginx rewrite handler to set var or return errors before proxy pass or content ring handler
1. Non-blocking coroutine based socket which is Compatible with Java Socket API and works well with largely existing java library such as apache http client, mysql jdbc drivers. 
With this feature  one java main thread can handle thousands of connections.
1. Handle multiple sockets parallel in sub coroutines, e.g. we can invoke two remote services at the same time feature
1. Asynchronous callback API of socket for some advanced usage
1. Run initialization clojure code when nginx worker starting
1. Support user defined http request method
1. Compatible with the Nginx lastest stable version 1.6.0. (Nginx 1.4.x is also ok, older version is not tested and maybe works.)
1. One of  benifits of [Nginx](http://nginx.org/) is worker processes are automatically restarted by a master process if they crash
1. Utilizes lazy headers and direct memory operation between [Nginx](http://nginx.org/) and JVM to fast handle dynamic contents from Clojure or Java code.
1. Utilizes [Nginx](http://nginx.org/) zero copy file sending mechanism to fast handle static contents controlled by Clojure or Java code.
1. Supports Linux x64, Linux x86 32bit, Win32 and Mac OS X. Win64 users can also run it with a 32bit JRE/JDK.

By the way it is very fast, the benchmarks can be found [HERE](https://github.com/ptaoussanis/clojure-web-server-benchmarks) .

# Contents of Guide

# [1.Installation](http://nginx-clojure.github.io/#1-installation)
## [1.1 Installation By Binary](http://nginx-clojure.github.io/#11-installation-by-binary)
## [1.2 Installation by Source](http://nginx-clojure.github.io/#12-installation-by-source)

# [2. Configurations](http://nginx-clojure.github.io/#2-configurations)
## [2.1 JVM Path , Class Path & Other JVM Options](http://nginx-clojure.github.io/#21-jvm-path--class-path--other-jvm-options)
## [2.2 Initialization Handler for nginx worker](http://nginx-clojure.github.io/#22-initialization-handler-for-nginx-worker)
## [2.3 Ring Handler for Location](http://nginx-clojure.github.io/#23-ring-handler-for-location)
### [2.3.1 Inline Ring Handler](http://nginx-clojure.github.io/#231-inline-ring-handler)
### [2.3.2 Reference of External Ring Handlers](http://nginx-clojure.github.io/#232-reference-of-external-ring-handlers)
### [2.3.3 Pure Java Handler](http://nginx-clojure.github.io/#233-pure-java-handler)
## [2.4 Chose Coroutine based Socket Or Asynchronous Socket Or Thread Pool for slow I/O operations](http://nginx-clojure.github.io/#24-chose--coroutine-based-socket-or-asynchronous-socket-or-thread-pool-for-slow-io-operations)
### [2.4.1 Enable Coroutine based Socket](http://nginx-clojure.github.io/#241-enable-coroutine-based-socket)
### [2.4.2 Use Asynchronous Socket](http://nginx-clojure.github.io/#242-use-asynchronous-socket)
### [2.4.3 Use Thread Pool](http://nginx-clojure.github.io/#243-use-thread-pool)
## [2.5 Nginx rewrite handler](http://nginx-clojure.github.io/#25-nginx-rewrite-handler)
### [2.5.1 Simple Example about Nginx rewrite handler](http://nginx-clojure.github.io/#251-simple-example-about-nginx-rewrite-handler)
### [2.5.2 Simple Dynamic Balancer By Nginx rewrite handler](http://nginx-clojure.github.io/#252-simple-dynamic-balancer-by-nginx-rewrite-handler)
### [2.5.3 Simple Access Controller By Nginx rewrite handler](http://nginx-clojure.github.io/#253-simple-access-controller-by-nginx-rewrite-handler)
# [3.More about Nginx-Clojure](http://nginx-clojure.github.io/#3-more-about-nginx-clojure)
## [3.1 Handle Multiple Coroutine Based Sockets Parallel](http://nginx-clojure.github.io/#31-handle-multiple-coroutine-based-sockets-parallel)
## [3.2 Shared Map among Nginx Workers](http://nginx-clojure.github.io/#32-shared-map-among-nginx-workers)
## [3.3 User Defined Http Method](http://nginx-clojure.github.io/#33-user-defined-http-method)
# [4. Useful Links](http://nginx-clojure.github.io/#4-useful-links)
# 5. License
Copyright © 2013-2014 Zhang, Yuexiang (xfeep) and released under the BSD 3-Clause license.

This program uses:
* Re-rooted ASM bytecode engineering library which is distributed under the BSD 3-Clause license
* Modified Continuations Library Written by Matthias Mann  is distributed under the BSD 3-Clause license
