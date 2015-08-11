# nginx-clojure-embed

Embeding Nginx-Clojure into a standard java/clojure app without additional Nginx process

Jar Repository
================

For Clojure

```clojure
[nginx-clojure/nginx-clojure-embed "0.4.1"]
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
  <version>0.4.1</version>
</dependency>
```

Quick Start
================

For Clojure

```clojure
    ;;(1) Starts it with ring handler and an options map
    ;;my-app can be a simple ring hanler or a compojure router.
    (run-server my-app {:port 8080})


   ;;(2) Starts it with a nginx.conf file
    (run-server \"/my-dir/nginx.conf\")

   ;;(3) Starts it with a given work dir
    (binding [*nginx-work-dir* my-work-dir]
      (run-server ...))
```

For Java

```java

```