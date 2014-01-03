Nginx-Clojure
=============

Nginx-Clojure is a [Nginx](http://nginx.org/) module for embedding Clojure or Java programs, typically those [Ring](https://github.com/ring-clojure/ring/blob/master/SPEC) based handlers.

There are some core features :

1. compatible with [Ring](https://github.com/ring-clojure/ring/blob/master/SPEC) and obviously supports those Ring based frameworks, such as Compojure etc.
1. one of  benifits of [Nginx](http://nginx.org/) is worker processes are automatically restarted by a master process if they crash
1. utilizes lazy headers and direct memory operation between [Nginx](http://nginx.org/) and JVM to fast handle dynamic contents from Clojure or Java code.
1. utilizes [Nginx](http://nginx.org/) zero copy file sending mechanism to fast handle static contents controlled by Clojure or Java code.


Installation
=============

Installation by Binary
-------------

### Linux x64 Binary


### Win32 Binary


### MacOSX x64 Binary


Installation by Source
-------------