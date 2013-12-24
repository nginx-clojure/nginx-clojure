nginx-clojure
=============

Nginx module for embedding Clojure or Java programs, typically those [Ring](https://github.com/ring-clojure/ring/blob/master/SPEC) based handlers.

There are some core features :

1. compatible with [Ring](https://github.com/ring-clojure/ring/blob/master/SPEC) and obviously support those Ring based frameworks, such as Compojure etc.
1. one of  benifits of nginx is worker processes are automatically restarted by a master process if they crash
1. utilize lazy headers and direct memory operation between Nginx and JVM to fast handle dynamic content from Clojure or Java code.
1. utilize Nginx zero copy file sending mechanism to fast handle static content controlled by Clojure or Java code.


 
