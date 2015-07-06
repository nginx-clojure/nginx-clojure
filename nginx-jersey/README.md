# nginx-jersey

A Java library designed to intergrate Jersey into Nginx by Nignx-Clojure Module so that 
Nginx can Support Java standard RESTful Web Services (JAX-RS)

## Usage

To get nginx-jersey-x.x.x.jar

```shell
lein jar
```

in nginx.conf

```nginx
      location /jersey {
          content_handler_type java;
          content_handler_name 'nginx.clojure.bridge.NginxBridgeHandler';
          content_handler_property system.m2rep '/home/who/.m2/repository';
          
          ##we can put jars into some dir then all of their path will be appended into the classpath
          #content_handler_property bridge.lib.dirs 'my-jersey-libs-dir:myother-dir';
          
          ##we can also put jars or classes directory one by one here.
          ##the path of nginx-jersey-x.x.x.jar must be included in the below classpath or one of above #{bridge.lib.dirs}
          content_handler_property bridge.lib.cp '/home/who/git/nginx-clojure/nginx-jersey/bin:/home/who/git/jersey/examples/json-jackson/target/classes:#{m2rep}/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar:#{m2rep}/org/glassfish/jersey/core/jersey-common/2.17/jersey-common-2.17.jar:#{m2rep}/org/glassfish/jersey/media/jersey-media-json-jackson/2.17/jersey-media-json-jackson-2.17.jar:#{m2rep}/org/glassfish/jersey/core/jersey-server/2.17/jersey-server-2.17.jar:#{m2rep}/org/glassfish/jersey/ext/jersey-entity-filtering/2.17/jersey-entity-filtering-2.17.jar:#{m2rep}/org/glassfish/hk2/external/javax.inject/2.4.0-b10/javax.inject-2.4.0-b10.jar:#{m2rep}/clojure-complete/clojure-complete/0.2.3/clojure-complete-0.2.3.jar:#{m2rep}/junit/junit/4.11/junit-4.11.jar:#{m2rep}/org/glassfish/hk2/hk2-locator/2.4.0-b10/hk2-locator-2.4.0-b10.jar:#{m2rep}/javax/ws/rs/javax.ws.rs-api/2.0.1/javax.ws.rs-api-2.0.1.jar:#{m2rep}/javax/annotation/javax.annotation-api/1.2/javax.annotation-api-1.2.jar:#{m2rep}/org/glassfish/hk2/hk2-api/2.4.0-b10/hk2-api-2.4.0-b10.jar:#{m2rep}/org/glassfish/jersey/core/jersey-client/2.17/jersey-client-2.17.jar:#{m2rep}/com/fasterxml/jackson/jaxrs/jackson-jaxrs-base/2.3.2/jackson-jaxrs-base-2.3.2.jar:#{m2rep}/com/fasterxml/jackson/module/jackson-module-jaxb-annotations/2.3.2/jackson-module-jaxb-annotations-2.3.2.jar:#{m2rep}/com/fasterxml/jackson/jaxrs/jackson-jaxrs-json-provider/2.3.2/jackson-jaxrs-json-provider-2.3.2.jar:#{m2rep}/org/glassfish/hk2/osgi-resource-locator/1.0.1/osgi-resource-locator-1.0.1.jar:#{m2rep}/com/fasterxml/jackson/core/jackson-databind/2.3.2/jackson-databind-2.3.2.jar:#{m2rep}/org/glassfish/jersey/bundles/repackaged/jersey-guava/2.17/jersey-guava-2.17.jar:#{m2rep}/org/glassfish/hk2/hk2-utils/2.4.0-b10/hk2-utils-2.4.0-b10.jar:#{m2rep}/org/glassfish/jersey/media/jersey-media-jaxb/2.17/jersey-media-jaxb-2.17.jar:#{m2rep}/org/clojure/tools.nrepl/0.2.6/tools.nrepl-0.2.6.jar:#{m2rep}/javax/validation/validation-api/1.1.0.Final/validation-api-1.1.0.Final.jar:#{m2rep}/com/fasterxml/jackson/core/jackson-annotations/2.3.2/jackson-annotations-2.3.2.jar:#{m2rep}/com/fasterxml/jackson/core/jackson-core/2.3.2/jackson-core-2.3.2.jar:#{m2rep}/org/javassist/javassist/3.18.1-GA/javassist-3.18.1-GA.jar:#{m2rep}/org/glassfish/hk2/external/aopalliance-repackaged/2.4.0-b10/aopalliance-repackaged-2.4.0-b10.jar';
          content_handler_property bridge.imp 'nginx.clojure.jersey.NginxJerseyContainer';
          
          ##aplication path usually it is the same with nginx location 
          content_handler_property jersey.app.path '/jersey';
          
          ##application resources which can be either of JAX-RS resources, providers
          content_handler_property jersey.app.resources '
                org.glassfish.jersey.examples.jackson.EmptyArrayResource,
                org.glassfish.jersey.examples.jackson.NonJaxbBeanResource,
                org.glassfish.jersey.examples.jackson.CombinedAnnotationResource,
                org.glassfish.jersey.examples.jackson.MyObjectMapperProvider,
                org.glassfish.jersey.examples.jackson.ExceptionMappingTestResource,
                org.glassfish.jersey.jackson.JacksonFeature
          ';
          gzip on;
          gzip_types application/javascript application/xml text/plain text/css 'text/html;charset=UTF-8'; 
      }
```

All sources about this example can be found from jersey github repository 's example [json-jackson](https://github.com/jersey/jersey/tree/2.17/examples/json-jackson/src/main/java/org/glassfish/jersey/examples/jackson).

then we test the JAX-RS services by curl

```shell
$ curl  -v http://localhost:8080/jersey/emptyArrayResource
> GET /jersey/emptyArrayResource HTTP/1.1
> User-Agent: curl/7.35.0
> Host: localhost:8080
> Accept: */*
> 
< HTTP/1.1 200 OK
< Date: Sat, 23 May 2015 17:47:14 GMT
< Content-Type: application/json
< Transfer-Encoding: chunked
< Connection: keep-alive
* Server nginx-clojure is not blacklisted
< Server: nginx-clojure
< 
{
  "emtpyArray" : [ ]
}
```

```shell
$ curl -v http://localhost:8080/jersey/nonJaxbResource
> GET /jersey/nonJaxbResource HTTP/1.1
> User-Agent: curl/7.35.0
> Host: localhost:8080
> Accept: */*
> 
< HTTP/1.1 200 OK
< Date: Sat, 23 May 2015 17:46:17 GMT
< Content-Type: application/javascript
< Transfer-Encoding: chunked
< Connection: keep-alive
* Server nginx-clojure is not blacklisted
< Server: nginx-clojure
< 
callback({
  "name" : "non-JAXB-bean",
  "description" : "I am not a JAXB bean, just an unannotated POJO",
  "array" : [ 1, 1, 2, 3, 5, 8, 13, 21 ]
* Connection #0 to host localhost left intact
})
```

## License

Copyright © 2013-2015 Zhang, Yuexiang (xfeep) and released under the BSD 3-Clause license.



