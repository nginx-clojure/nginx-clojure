# nginx-tomcat

A Java library designed to embed Tomcat into Nginx by Nignx-Clojure Module so that Nginx can  Support Java Standard Web Applications.

## Which Version?

Apache Tomcat version | Nginx-Clojure version|Nginx-Tomcat8 version
------------ | -------------|-------------
8.0.20 | >=0.4.x|0.1.x
8.0.23,8.0.24 | >=0.4.x|0.2.x

## Get Jar File

We can get the released version from [clojars](https://clojars.org/nginx-clojure/nginx-tomcat8) or 
the jar in [nginx-clojure binary release](https://sourceforge.net/projects/nginx-clojure/files/) 

For get the latest version from the github source

```shell
git clone https://github.com/nginx-clojure/nginx-clojure
cd nginx-clojure/nginx-tomcat8
lein jar
```
## Configuration

in nginx.conf

```nginx
      location / {
      
          content_handler_type java;
          content_handler_name 'nginx.clojure.bridge.NginxBridgeHandler';
          
          ##Tomcat 8 installation path
          content_handler_property system.catalina.home '/home/who/share/apps/apache-tomcat-8.0.20';
          content_handler_property system.catalina.base '#{catalina.home}';
          
          ##uncomment this to disable websocket perframe-compression
          #content_handler_property system.org.apache.tomcat.websocket.DISABLE_BUILTIN_EXTENSIONS true;
          
          ##log manger
          content_handler_property system.java.util.logging.manager 'org.apache.juli.ClassLoaderLogManager';
          
          ## all jars or direct child directories will be appended into the classpath of this bridge handler's class-loader
          content_handler_property bridge.lib.dirs '#{catalina.home}/lib:#{catalina.home}/bin';
          
          ##set nginx tomcat8 bridge implementation jar and other jars can also be appended here
          content_handler_property bridge.lib.cp 'my-jar-path/nginx-tomcat8-x.x.x.jar';
          
          ##The implementation class of nginx-clojure bridge handler for Tomcat 8
          content_handler_property bridge.imp 'nginx.clojure.tomcat8.NginxTomcatBridge';
          
          ##ignore nginx filter, default is false
          #content_handler_property ignoreNginxFilter false;
          
          ##when dispatch is false tomcat servlet will be executed in main thread.By default dispatch is false
          ##when use websocket with tomcat it must be set true otherwise maybe deadlock will happen.
          #content_handler_property dispatch false;
          
          gzip on;
          gzip_types text/plain text/css 'text/html;charset=ISO-8859-1' 'text/html;charset=UTF-8'; 
          
          ##if for small message, e.g. small json/websocket message write_page_size can set to be a small value
          #write_page_size 2k;
      }
```

## Session Management

If `worker_processes` > 1 there will be more than one jvm instances viz. more tomcat instances so to get synchronized session information we can not use the default tomcat session manger.
Instead we may consider to use either of 
1. Cookied based Session Store  viz. storing all session attribute information into cookies.
1. Shared HashMap among processes in the same machine ,e.g. nginx-clojure built-in [Shared Map][], OpenHFT [Chronicle Map][]
1. External Session Store,  e.g.  Redis / MySQL / Memcached Session Store


## About Performance

### Disable Tomcat Access Log

When we need access log , use Nginx access log instead of Tomcat access log.

In server.xml comment AccessLogValve configuration to disable Tomcat access log.

```xml
<!--
        <Valve className="org.apache.catalina.valves.AccessLogValve" directory="logs"
               prefix="localhost_access_log" suffix=".txt"
               pattern="%h %l %u %t &quot;%r&quot; %s %b" />
-->
```

###Disable logging to console

Because Tomcat console log is duplicate with other log such as catalina log, manager log ,etc, so it can be disabled.
In conf/logging.properties remove all `java.util.logging.ConsoleHandler`

```shell
handlers = 1catalina.org.apache.juli.AsyncFileHandler, 2localhost.org.apache.juli.AsyncFileHandler, 3manager.org.apache.juli.AsyncFileHandler, 4host-manager.org.apache.juli.AsyncFileHandler

.handlers = 1catalina.org.apache.juli.AsyncFileHandler
```

### Don't Enable Tomcat Compression

By default compression is off , do not turn it on.

```xml
<Connector port="8080" protocol="HTTP/1.1" compression="off"
```
If we need compression use nginx gzip filter instead. e.g. In nginx.conf

```nginx
location /examples {
    gzip on;
    gzip_types text/plain text/css 'text/html;charset=ISO-8859-1' 'text/html;charset=UTF-8'; 
}
```
`gzip` can also appear at http, server blocks. More details can be found [HERE](http://nginx.org/en/docs/http/ngx_http_gzip_module.html)

## License

Copyright © 2013-2016 Zhang, Yuexiang (xfeep) and released under the BSD 3-Clause license.

[Shared Map]: https://nginx-clojure.github.io/sharedmap.html
[Chronicle Map]: https://github.com/OpenHFT/Chronicle-Map