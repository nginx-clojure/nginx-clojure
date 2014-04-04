Nginx-Clojure
=============

![Alt text](logo.png)Nginx-Clojure is a [Nginx](http://nginx.org/) module for embedding Clojure or Java programs, typically those [Ring](https://github.com/ring-clojure/ring/blob/master/SPEC) based handlers.

There are some core features :

1. Compatible with [Ring](https://github.com/ring-clojure/ring/blob/master/SPEC) and obviously supports those Ring based frameworks, such as Compojure etc.
1. One of  benifits of [Nginx](http://nginx.org/) is worker processes are automatically restarted by a master process if they crash
1. Utilizes lazy headers and direct memory operation between [Nginx](http://nginx.org/) and JVM to fast handle dynamic contents from Clojure or Java code.
1. Utilizes [Nginx](http://nginx.org/) zero copy file sending mechanism to fast handle static contents controlled by Clojure or Java code.
1. Supports Linux x64, Win32 and Mac OS X

By the way it is very fast, the benchmarks can be found [HERE](https://github.com/ptaoussanis/clojure-web-server-benchmarks) .


1. Installation
=============

The lastest release is 0.1.2. Please check the  [Update History](HISTORY.md) for more details.

1.1 Installation by Binary
-------------

1. First you can download  Release 0.1.2  from [here](https://sourceforge.net/projects/nginx-clojure/files/). 
The zip file includes Nginx-Clojure binaries about Linux x64, Win32 and Mac OS X.
1. Unzip the zip file downloaded then rename the file `nginx-${os-arc}` to `nginx`, eg. for linux is `nginx-linux-x64`


1.2 Installation by Source
-------------

Nginx-Clojure may be compiled successfully on Linux x64, Win32 and Mac OS X x64.

1. First download from [nginx site](http://nginx.org/en/download.html) or check out nginx source by hg from http://hg.nginx.org/nginx. 
For Win32 users MUST check out nginx source by hg because the zipped source doesn't contain Win32 related code.
1. Check out Nginx-Clojure source from github OR download the zipped source code from https://github.com/xfeep/nginx-clojure/releases
1. If you want to use Http SSL module, you should install openssl and openssl dev first.
1. Setting Java header include path in nginx-clojure/src/c/config

	```nginx
	#eg. on ubuntu
	JNI_HEADER_1="/usr/lib/jvm/java-7-oracle/include"
	JNI_HEADER_2="${JNI_HEADER_1}/linux"
	````
1. Add Nginx-Clojure module to Nginx configure command, here is a simplest example without more details about [InstallOptions](http://wiki.nginx.org/InstallOptions)

	```bash
	#If nginx source is checked out from hg, please replace ./configure with auto/configure
	$./configure \
		--add-module=nginx-clojure/src/c
	$ make
	$ make install
	```
1. Create the jar file about Nginx-Clojure

	Please check the lein version `lein version`, it should be at least 2.0.0.

	```bash
	$ cd nginx-clojure
	$ lein javac
	$ lein jar
	```
	Then you'll find nginx-clojure-${version}.jar (eg. nginx-clojure-0.1.0.jar) in the target folder.

2. Configurations
=================

2.1 JVM Path , Class Path & Other JVM Options
-----------------

Setting JVM path and class path within `http {` block in  nginx.conf

```nginx

    #for win32,  jvm_path maybe "C:/Program Files/Java/jdk1.7.0_25/jre/bin/server/jvm.dll";
    #for macosx, jvm_path maybe "/Library/Java/JavaVirtualMachines/1.6.0_65-b14-462.jdk/Contents/Libraries/libserver.dylib";
    #for linux,  jvm_path maybe "/usr/lib/jvm/java-7-oracle/jre/lib/amd64/server/libjvm.so";
    
    jvm_path "/usr/lib/jvm/java-7-oracle/jre/lib/amd64/server/libjvm.so";
    
    #jvm_options can be repeated once per option.
    #for win32, class path seperator is ";",  jvm_options maybe "-Djava.class.path=jars/nginx-clojure-0.1.0.jar;jars/clojure-1.5.1.jar";
    jvm_options "-Djava.class.path=jars/nginx-clojure-0.1.0.jar:jars/clojure-1.5.1.jar";
    
    #for memory setting
    #jvm_options "-Xms256m";
    #jvm_options "-Xmx256m";
    
    #for engble java remote debug uncomment next two lines
    #jvm_options "-Xdebug";
    #jvm_options "-Xrunjdwp:server=y,transport=dt_socket,address=8400,suspend=n";
````
Now you can start nginx and access http://localhost:8080/clojure, if some error happens please check error.log file. 

2.2 Threads Number for Request Handler Thread Pool on JVM
-----------------
Within `http {` block in nginx.conf `jvm_workers` is a directive about threads number for request handler thread pool on JVM, default is 0. 
**ONLY IF** you can't resolve your performance problems by increasing worker_processes or reducing single request-response time, 
you can try this way. If your tasks are often blocked by slow I/O operations, the thread pool method can make the nginx worker not blocked until
all threads are exhuasted.

eg.

```nginx
jvm_workers 40;
```
Now Nginx-Clojure will create a thread pool with fixed 40 threads  per JVM instance/Nginx worker to handle requests. If you get more memory, you can set
a bigger number.

2.3 Ring Handler for Location
-----------------

Within `location` block, directive `clojure` is an enable flag and directive `clojure_code` is used to setting a Ring handler.


###2.3.1 Inline Ring Handler

```nginx
       location /clojure {
          clojure;
          clojure_code ' 
						(fn[req]
						  {
						    :status 200,
						    :headers {"content-type" "text/plain"},
						    :body  "Hello Clojure & Nginx!" 
						    })
          ';
       }
```

###2.3.2 Reference of External Ring Handlers

```clojure
(ns my.hello)
(defn hello-world [request]
  {:status 200
   :headers {"Content-Type" "text/plain"}
   :body "Hello World"})

```

You should set your clojure JAR files to class path, see [2.1 JVM path & class path](#2.1 JVM path & class path) .


```nginx
       location /myClojure {
          clojure;
          clojure_code ' 
          (do
               (use \'[my.hello])
                 hello-world))
          ';
       }
```
For more details and more useful examples for [Compojure](https://github.com/weavejester/compojure) which is a small routing library for Ring that allows web applications to be composed of small, independent parts. Please refer to https://github.com/weavejester/compojure


###2.3.3 Pure Java Handler

```java
package my;

import nginx.clojure.Constants;
import clojure.lang.AFn;
import clojure.lang.IPersistentMap;
import clojure.lang.PersistentArrayMap;

public class HelloHandler extends AFn {
	
	@Override
	public Object invoke(Object r) {
		IPersistentMap req = (IPersistentMap)r;
		
		//get some info from req. eg. req.valAt(Constants.QUERY_STRING)
		//....
		
		//prepare resps, more details about Ring handler on this site https://github.com/ring-clojure/ring/blob/master/SPEC
		Object[] resps = new Object[] {Constants.STATUS, 200, 
				Constants.HEADERS, new PersistentArrayMap(new Object[]{Constants.CONTENT_TYPE.getName(),"text/plain"}),
				Constants.BODY, "Hello Java & Nginx!"};
		return new PersistentArrayMap(resps);
	}
	
}
```


In nginx.conf, eg.

```nginx
	location /java {
          clojure;
          clojure_code ' 
               (do (import \'[my HelloHandler]) (HelloHandler.) )
          ';
       }
```

You should set your  JAR files to class path, see [2.1 JVM path & class path](#2.1 JVM path & class path) .

3. Useful Links
=================

* [Ring Documents](https://github.com/ring-clojure/ring/wiki)
* [Comojure Documents](https://github.com/weavejester/compojure/wiki)
* [Simple Examples](test/nginx-working-dir/conf/nginx.conf) in Nginx Clojure Testing Configuration & [Testing Client Code](test/clojure/nginx/clojure/test_all.clj)
* [Nginx Clojure Ring Handlers Examples for Testing](test/clojure/nginx/clojure/ring_handlers_for_test.clj) (Testing with Ring Core 1.2.1)


4. License
=================
Copyright © 2013-2014 Zhang, Yuexiang (xfeep) and released under the BSD 3-Clause license.

This program uses:
* Re-rooted ASM bytecode engineering library which is distributed under the BSD 3-Clause license
* Modified Continuations Library Written by Matthias Mann  is distributed under the BSD 3-Clause license
