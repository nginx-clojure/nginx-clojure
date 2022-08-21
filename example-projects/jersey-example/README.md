# nginx jsersey example

## build

```bash
mvn compile assembly:single -DskipTests
```

## Configuration

in nginx.conf

```nginx
      location /jersey {
          
          content_handler_type java;
          content_handler_name 'nginx.clojure.bridge.NginxBridgeHandler';
          
          ##we can set system properties ,e.g. m2rep
          #content_handler_property system.m2rep '/home/who/.m2/repository';
          
          ##we can put jars into some dir then all of their path will be appended into the classpath
          #content_handler_property bridge.lib.dirs 'my-jersey-libs-dir:myother-dir';
          
          ##the path of nginx-jersey-x.x.x.jar must be included in the below classpath or one of above #{bridge.lib.dirs}
          ##we can use maven assembly plugin to get a all-in-one jar file with dependencies, e.g. json-jackson-example-with-dependencies.jar.
          content_handler_property bridge.lib.cp 'jars/nginx-jersey-0.1.0.jar:myjars/json-jackson-example-with-dependencies.jar';
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