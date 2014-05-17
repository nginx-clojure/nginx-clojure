Nginx-Clojure
=============

![Alt text](logo.png)Nginx-Clojure is a [Nginx](http://nginx.org/) module for embedding Clojure or Java programs, typically those [Ring](https://github.com/ring-clojure/ring/blob/master/SPEC) based handlers.

There are some core features :

1. Compatible with [Ring](https://github.com/ring-clojure/ring/blob/master/SPEC) and obviously supports those Ring based frameworks, such as Compojure etc.
1. **_NEW_**: Use Java/Clojure code to write a simple nginx rewrite handler to set var before proxy pass
1. **_NEW_**: Non-blocking coroutine based socket which is Compatible with Java Socket API and works well with largely existing java library such as apache http client, mysql jdbc drivers. 
With this feature  one java main thread can handle thousands of connections.
1. **_NEW_**: Handle multiple sockets parallel in sub coroutines, e.g. we can invoke two remote services at the same time feature
1. **_NEW_**: Asynchronous callback API of socket for some advanced usage
1. **_NEW_**: Run initialization clojure code when nginx worker starting
1. **_NEW_**: Compatible with the Nginx lastest stable version 1.6.0. (Nginx 1.4.x is also ok, older version is not tested and maybe works.)
1. One of  benifits of [Nginx](http://nginx.org/) is worker processes are automatically restarted by a master process if they crash
1. Utilizes lazy headers and direct memory operation between [Nginx](http://nginx.org/) and JVM to fast handle dynamic contents from Clojure or Java code.
1. Utilizes [Nginx](http://nginx.org/) zero copy file sending mechanism to fast handle static contents controlled by Clojure or Java code.
1. Supports Linux x64, Linux x32 (**_NEW_**), Win32 and Mac OS X. Win64 users can also run it with a 32bit JRE/JDK.

By the way it is very fast, the benchmarks can be found [HERE](https://github.com/ptaoussanis/clojure-web-server-benchmarks) .


1. Installation
=============

The lastest release is 0.2.1. Please check the  [Update History](HISTORY.md) for more details.

1.1 Installation by Binary
-------------

1. First you can download  Release 0.2.1  from [here](https://sourceforge.net/projects/nginx-clojure/files/). 
The zip file includes Nginx-Clojure binaries about Linux x64, Linux i586, Win32 and Mac OS X.
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
	$ lein jar
	```
	Then you'll find nginx-clojure-${version}.jar (eg. nginx-clojure-0.2.1.jar) in the target folder. 
	The jar file is self contained and only depend on the clojure core jar, e.g clojure-1.5.1.jar

2. Configurations
=================

2.1 JVM Path , Class Path & Other JVM Options
-----------------

Setting JVM path and class path within `http {` block in  nginx.conf

```nginx

    #for win32,  jvm_path maybe is "C:/Program Files/Java/jdk1.7.0_25/jre/bin/server/jvm.dll";
    #for macosx, jvm_path maybe is "/Library/Java/JavaVirtualMachines/1.6.0_65-b14-462.jdk/Contents/Libraries/libserver.dylib";
    #for ubuntu, jvm_path maybe is "/usr/lib/jvm/java-7-oracle/jre/lib/amd64/server/libjvm.so";
    #for centos, jvm_path maybe is "/usr/java/jdk1.6.0_45/jre/lib/amd64/server/libjvm.so";
    #for centos 32bit, jvm_path maybe is "/usr/java/jdk1.7.0_51/jre/lib/i386/server/libjvm.so";
    
    jvm_path "/usr/lib/jvm/java-7-oracle/jre/lib/amd64/server/libjvm.so";
    
    #jvm_options can be repeated once per option.
    #for win32, class path seperator is ";",  jvm_options maybe "-Djava.class.path=jars/nginx-clojure-0.2.1.jar;jars/clojure-1.5.1.jar";
    jvm_options "-Djava.class.path=jars/nginx-clojure-0.2.1.jar:jars/clojure-1.5.1.jar";
    
    #jvm heap memory
    jvm_options "-Xms1024m";
    jvm_options "-Xmx1024m";
    
    #for enable java remote debug uncomment next two lines, make sure "master_process = off;" or "worker_processes = 1;"
    #jvm_options "-Xdebug";
    #jvm_options "-Xrunjdwp:server=y,transport=dt_socket,address=8400,suspend=n";
````

###Some Useful Tips

These tips are really useful. Most of them are from real users. Thanks [Rickr Nook](https://github.com/rickr-nook) who give us some useful tips.

1. When importing Swing We Must specifiy `jvm_options "-Djava.awt.headless=true"` , otherwise the nginx will hang.
1. By adding the location of your clojure source files to the classpath,then just issue "nginx -s reload" and changes to the sources get picked up!
1. You can remove clojure-1.5.1.jar from class path and point at your "lein uberjar" to pick up a different version of clojure. 
1. To use Java 7 on OSX, in nginx.conf your may set `jvm_path "/Library/Java/JavaVirtualMachines/jdk1.7.0_55.jdk/Contents/Home/jre/lib/server/libjvm.dylib";`

2.2 Initialization Handler for nginx worker
-----------------

You can embed clojure code in the `http { ` block to do initialization when nginx worker starting. e.g

```clojure
http {
......
    clojure;
    clojure_code '
    (fn[ctx]
        (.println System/err "on http clojure context")
        )
    ';
....
}
```

Now `ctx` is only empty map but in the future we will give more information with it.
clojure code should return a ring handler which can use status 500 and body to report some errors or just return nothing.

Please Keep these in your mind:

* By default if the initialization failed the nginx won't start successfully and the worker will exit after reporting an error message in error log file but the master keep running and take the port.
* Because the maybe more than one nginx worker processes, so this code will run everytime per worker starting. 
* If you use [SharedHashMap](https://github.com/OpenHFT/HugeCollections/wiki/Getting-Started)  to share data 
among nginx worker processes, Java file lock can be used to let only one nginx worker process do the initialization.
* If you enabled [coroutine support](#), nginx maybe will start successfully even if your initialization failed after some socket operations. If you case it, you can 
use `nginx.clojure.core/without-coroutine` to wrap your handler, e.g.

	```nginx
	    clojure_code '
	    (do
		    (use \'nginx.clojure.core)
		    (without-coroutine
		      (fn[ctx]
		        ....
		        )
		    ))
	    ';
	```


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

Now you can start nginx and access http://localhost:8080/clojure, if some error happens please check error.log file. 

###2.3.2 Reference of External Ring Handlers

```clojure
(ns my.hello)
(defn hello-world [request]
  {:status 200
   :headers {"Content-Type" "text/plain"}
   :body "Hello World"})

```

You should set your clojure JAR files to class path, see [2.1 JVM Path , Class Path & Other JVM Options](#21-jvm-path--class-path--other-jvm-options) .


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

You should set your  JAR files to class path, see [2.1 JVM Path , Class Path & Other JVM Options](#21-jvm-path--class-path--other-jvm-options) .

2.4 Chose  Coroutine based Socket Or Asynchronous Socket Or Thread Pool for slow I/O operations
-----------------

If the http service should do some slow I/O operations such as access external http service, database, etc.  nginx worker will be blocked by those operations 
and the new  user  request even static file request will be blocked. It really sucks！ Before v0.2.0 the only choice is using thread pool but now we have 
three choice :

1. Coroutine based Socket
	* :smiley:It's Java Socket API Compatible and work well with largely existing java library such as apache http client, mysql jdbc drivers etc.
	* :smiley:It's non-blocking, cheap, fast and let one java main thread be able to handle thousands of connections.
	* :smiley:Your old code **_need not be changed_** and those plain and old java socket based code such as Apache Http Client, MySQL mysql jdbc drivers etc. will be on the fly with epoll/kqueue on Linux/BSD!
	* :worried:You must do some steps to get the right class waving configuration file and set it in the nginx conf file.
1. Asynchronous Socket
	* :smiley:It's the fastest among those three choice and you can controll it finely.
	* :smiley:It can work with default mode or Coroutine based Socket enabled mode but can't work with Thread Pool mode.
	* :worried:Your old code **_must be changed_** to use the event driven pattern.
1. Thread Pool
	* :smiley:It's a trade off choice and almost all Java server such as Jetty, Tomcat , Glassfish etc. use thread pool to handle http requests.
	* :smiley:Your old code **_need not be changed_**.
	* :worried:The nginx worker will be blocked after all threads are exhuasted by slow I/O operations.
	* :worried:Becase the max number of threads is alwarys more smaller than the total number of socket connections supported by Operation Systems and
thread in java is costlier than coroutine, facing large amount of connections this choice isn't as good as Coroutine based choice.

### 2.4.1 Enable Coroutine based Socket

#### 1. Get a User Defined Class Waving Configuration File for Your Web App

* Turn on Run Tool Mode
		
	```nginx
	http {
	...
	
	#To make sure generated Class Waving Configuration File won't be mixed by many workers. 
	worker_processes  1;
	
	#turn on run tool mode, t means Tool
	jvm_options "-javaagent:jars/nginx-clojure-0.2.1.jar=tmb";
  jvm_options "-Xbootclasspath/a:jars/nginx-clojure-0.2.1.jar:jars/clojure-1.5.1.jar";
  ...
	}
	```
* Setting Output Path of Waving Configuration File
	
	```nginx
	#Optional The default value is $nginx-workdir/nginx.clojure.wave.CfgToolOutFile
	#Setting Output Path of Waving Configuration File, 
  jvm_options "-Dnginx.clojure.wave.CfgToolOutFile=/tmp/my-wave-cfg.txt";
	```
* Setting Dump Configuration Service

	```nginx
	       location /dump {
	         clojure;
	         clojure_code ' 
	               (do (import \'[nginx.clojure.wave SuspendMethodTracer]) 
	                 (fn[req]
	                    (SuspendMethodTracer/dump) 
	                    {:status 200, :body "ok", :headers {"content-type"  "text/plain"}} 
	                 ))
	          ';       
	       
	       }
	```
* Start Nginx which Compiled with Nginx Clojure Module
* Run curl or httpclient based junit tests to access your http services which directly or indirectly use Java Socket API, e.g Apache Http Client, MySQL JDBC Driver etc.
* After All responses completed We'll get a generated class waving configuration file e.g `my-wave-cfg.txt` by access Dump Configuration Service.

	```bash
	/*use curl or just put the Dump Service url to browser and click GO!
	 *Dump Service will generate Waving Configuration File to the path defined by 
	 *java system property `nginx.clojure.wave.CfgToolOutFile`
	 */
	curl -v http://localhost:8080/dump
	```
	
	Don't foget reset `worker_processes` and turn off run tool mode for product enviroument after get class waving configuration
	
#### 2. Enable Coroutine Support

* Turn on Coroutine Support

	```nginx
	http {
	...
	#make sure it is reset to a normal number after  above step, e.g. 8 worker processes.
	worker_processes  8;
			
	#turn on coroutine mode
	jvm_options "-javaagent:jars/nginx-clojure-0.2.1.jar=mb";
	
	#append nginx-clojure &  clojure runtime jars to jvm bootclasspath 		
	#for win32, class path seperator is ";", e.g "-Xbootclasspath/a:jars/nginx-clojure-0.2.1.jar;jars/clojure-1.5.1.jar"
	jvm_options "-Xbootclasspath/a:jars/nginx-clojure-0.2.1.jar:jars/clojure-1.5.1.jar";
	
	#coroutine-udfs is a directory to put your User Defined Class Waving Configuration File
	#for win32, class path seperator is ";", e.g "-Djava.class.path=coroutine-udfs;YOUR_CLASSPATH_HERE"
	#Note: DON NOT put nginx-clojure &  clojure runtime jars here, because they have been appened to the jvm bootclasspath
	jvm_options "-Djava.class.path=coroutine-udfs:YOUR_CLASSPATH_HERE";
	
	
	#copy the waving configuration file generated from previous step to you any classpath dir e.g. coroutine-udfs
	#setting user defined class waving configuration files which are in the above boot classpath, the seperator is "," 
	jvm_options "-Dnginx.clojure.wave.udfs=my-wave-cfg.txt";
	...
	}
	```
	
* restart nginx or reload nginx
	
	Now every nginx worker can handle thousands of connections easily! 
	
	Those plain and old java socket based code such as Apache Http Client, MySQL mysql jdbc drivers etc. will be on the fly with epoll/kqueue on Linux/BSD!
	
	Nginx won't blocked until nginx connections exhuasted or jvm OutOfMemory!

### 2.4.2 Use Asynchronous Socket

Asynchronous Socket Can be used with default mode or coroutined enabled mode without any additional settings. It just a set of API.
It uses event driven pattern and works with a java callback handler or clojure function for callback.
Please check the source code and examples for more details.

* source: [nginx.clojure.net.NginxClojureAsynSocket](src/java/nginx/clojure/net/NginxClojureAsynSocket.java)
* example: 
	* Java [nginx.clojure.net.SimpleHandler4TestNginxClojureAsynSocket](test/java/nginx/clojure/net/SimpleHandler4TestNginxClojureAsynSocket.java)
	* Clojure [nginx.clojure.asyn-socket-handlers-for-test](test/clojure/nginx/clojure/asyn_socket_handlers_for_test.clj)

In future we'll give more clojure style wrapper and examples. **_Pull requests are also welcome!_**

### 2.4.3 Use Thread Pool

If your tasks are often blocked by slow I/O operations, the thread pool method can make the nginx worker not blocked until
all threads are exhuasted. When facing large amount of connections this choice isn't as good as above coroutine based choice or asynchronous socket.

eg.

```nginx

#turn off coroutine mode,  n means do nothing. You can also comment this line to turn off coroutine mode 
jvm_options "-javaagent:jars/nginx-clojure-0.2.1.jar=nmb";

jvm_workers 40;
```
Now Nginx-Clojure will create a thread pool with fixed 40 threads  per JVM instance/Nginx worker to handle requests. If you get more memory, you can set
a bigger number.

2.5 Nginx rewrite handler
-----------------

### 2.5.1 Simple Example about Nginx rewrite handler

Here's a simple example for Nginx rewrite handler :

```nginx

       set $myvar "";
       
       location /rewritesimple {
          clojure;
          clojure_rewrite_code '
           (do (use \'[nginx.clojure.core]) 
						(fn[req]
						  (set-ngx-var! req "myvar" "Hello")
						  phrase-done))
          ';
          clojure_code '
           (do (use \'[nginx.clojure.core]) 
						(fn[req]
						  (set-ngx-var! req "myvar" 
						             (str (get-ngx-var req "myvar") "," "Xfeep!"))
						  {
						    :status 200,
						    :headers {"content-type" "text/plain"},
						    :body  (get-ngx-var req "myvar") 
						    }))
          ';
       }    

```

### 2.5.2 Simple Dynamic Balancer By Nginx rewrite handler

We can alos use this feature to complete a simple dynamic balancer , e.g.

```nginx

       set $myhost "";
       
       location /myproxy {
          clojure;
          clojure_rewrite_code '
           (do (use \'[nginx.clojure.core]) 
						(fn[req]
						  ;compute myhost based req & remote service, e.g.
						  (let [myhost (compute-myhost req)])
						  (set-ngx-var! req "myvar" myhost)
						  phrase-done))
          ';
          proxy_pass $myhost
       }    

```


4. Useful Links
=================

* [Ring Documents](https://github.com/ring-clojure/ring/wiki)
* [Comojure Documents](https://github.com/weavejester/compojure/wiki)
* [Simple Examples](test/nginx-working-dir/conf/nginx.conf) in Nginx Clojure Testing Configuration & [Testing Client Code](test/clojure/nginx/clojure/test_all.clj)
* [Nginx Clojure Ring Handlers Examples for Testing](test/clojure/nginx/clojure/ring_handlers_for_test.clj) (Testing with Ring Core 1.2.1)


5. License
=================
Copyright © 2013-2014 Zhang, Yuexiang (xfeep) and released under the BSD 3-Clause license.

This program uses:
* Re-rooted ASM bytecode engineering library which is distributed under the BSD 3-Clause license
* Modified Continuations Library Written by Matthias Mann  is distributed under the BSD 3-Clause license
