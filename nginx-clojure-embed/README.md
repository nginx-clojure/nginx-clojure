# nginx-clojure-embed

Embeding Nginx-Clojure into a standard java/clojure app without additional Nginx process.
It can make test/debug with nginx-clojure clojure/java handler quite easy.

Jar Repository
================

For Clojure

```clojure
[nginx-clojure/nginx-clojure-embed "0.4.3"]
```

For Java (Maven)

```xml
<repository>
  <id>clojars.org</id>
  <url>http://clojars.org/repo</url>
</repository>
```

```xml
<dependency>
  <groupId>nginx-clojure</groupId>
  <artifactId>nginx-clojure-embed</artifactId>
  <version>0.4.3</version>
</dependency>
```

Start/Stop Embedded Server
================

For Clojure

```clojure
    ;;(1) Start it with ring handler and an options map
    ;;my-app can be a simple ring hanler or a compojure router.
    (run-server my-app {:port 8080})


   ;;(2) Start it with a nginx.conf file
    (run-server "/my-dir/nginx.conf")

   ;;(3) Start it with a given work dir
    (binding [*nginx-work-dir* my-work-dir]
      (run-server ...))
   
   ;;(4) Stop the server
    (stop-server)
```

For Java

```java
//Start it with ring handler and an options map
NginxEmbedServer.getServer().start("my.HelloHandler", ArrayMap.create("port", "8081"));


//Start it with with a nginx.conf file
NginxEmbedServer.getServer().start("/my-dir/nginx.conf");

//Start it with a given work dir
NginxEmbedServer.getServer().setWorkDir(my-work-dir);
NginxEmbedServer.getServer().start(...);


//Stop the server
NginxEmbedServer.getServer().stop();
```

Default Options
================

```clojure
          "error-log", "logs/error.log",
          "max-connections", "1024",
          "access-log", "off",
          "keepalive-timeout", "65",
          "max-threads", "8",
          "host", "0.0.0.0",
          "port", "8080",
```

User defined zones

```clojure
          ;;;at nginx.conf top level
          "global-user-defined", "",
          
          ;;;at nginx.conf http block
          "http-user-defined", "",
          
          ;;at nginx.conf types mapping block
          "types-user-defined", "",
          
          ;;at nginx.conf server block
          "server-user-defined", "",
          
          ;;at nginx.conf location block
          "location-user-defined", "" 
```

Build Notes
================

## generate makefile

```
Administrator@who-8f29c72b513 ~/build-for-embed/nginx-clojure
$ cd nginx-clojure-embed/

$ export NGINX_SRC=c:/mingw/msys/1.0/home/administrator/build-for-embed/nginx-c
urrent


Administrator@who-8f29c72b513 ~/build-for-embed/nginx-clojure/nginx-clojure-embe
d
$ ./configure-win32
javac is /c/Program Files/Java/jdk1.8.0_11/bin/javac
java is /c/Program Files/Java/jdk1.8.0_11/bin/java
checking for OS
```

## openssl 1.x

modify auto/lib/openssl/makefile.msvc to

```
all:
	cd $(OPENSSL)

	perl Configure VC-WIN64A no-shared				\
		--prefix="%cd%/openssl" 				\
		--openssldir="%cd%/openssl/ssl" 			\
		$(OPENSSL_OPT)

	if exist ms\do_ms.bat (						\
		ms\do_win64a						\
		&& $(MAKE) -f ms\nt.mak					\
		&& $(MAKE) -f ms\nt.mak install				\
	) else (							\
		$(MAKE)							\
		&& $(MAKE) install_sw					\
	)
```

## warning

src\os\win32\nginx_win32_config.h


line 32 : 

```

#ifdef _MSC_VER
#pragma warning(disable:4201)
#pragma warning(disable:4306)
#pragma warning(disable:4244)
#pragma warning(disable:4267)
#pragma warning(disable:4334)
#pragma warning(disable:4018)
#pragma warning(disable:4133)
#pragma warning(disable:4214)
#endif

```


## do make

```
$ cd ../..
$ nmake -f objs/Makefile NGX_WIN64=1 embed
```


License
================

Copyright © 2013-2022 Zhang, Yuexiang (xfeep) and released under the BSD 3-Clause license.