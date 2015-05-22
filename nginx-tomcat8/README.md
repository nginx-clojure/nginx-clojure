# nginx-tomcat

A Java library designed to embed Tomcat into Nginx by Nignx-Clojure Module so that Nginx can  Support Java Standard Web Applications.

## Usage

To get nginx-tomcat8-x.x.x.jar

```shell
lein jar
```

in nginx.conf

```nginx
      location / {
      
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
          content_handler_property bridge.imp 'nginx.clojure.tomcat8.NginxTomcatBridgeImpl';
          
          gzip on;
          gzip_types text/plain text/css 'text/html;charset=UTF-8'; 
      }
```

## For performance

### Diable Tomcat Access Log

When we need access log , use Nginx access log instead of Tomcat access log.

In server.xml comment AccessLogValve configuration to disable Tomcat access log.

```
<!--
        <Valve className="org.apache.catalina.valves.AccessLogValve" directory="logs"
               prefix="localhost_access_log" suffix=".txt"
               pattern="%h %l %u %t &quot;%r&quot; %s %b" />
-->
```
### Don't Enable Tomcat Compression

By default compression is off , don not turn it on.

```xml
<Connector port="8080" protocol="HTTP/1.1" compression="off"
```

## License

Copyright © 2013-2015 Zhang, Yuexiang (xfeep) and released under the BSD 3-Clause license.
