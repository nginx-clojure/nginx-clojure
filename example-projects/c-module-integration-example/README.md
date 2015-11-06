# Integration Example for C Module

This simple example show how a nginx C module integrates existing Java libraries by using nginx-clojure.
In this example we have a nginx c module say x-module which will send "HELLO, WORLD" to the client.
When a request whose uri is matched with the location using x-module's directive say `"x"`:

1. x-module will initialize the Java handler say  MyHandler if it has not been initialized
2. x-module will set the value of variable `my_array` to "hello, world"
3. x-module will invoke MyHandler by nginx-clojure API
4. MyHandler will get the value of variable `my_array` and set the upper-cased result back to variable `my_array`
5. x-module will get the new value of variable `my_array`, viz "HELLO, WORLD" and sent it to the client.


## Build C Module

```shell
## suppose nginx_clojure_root is /home/who/git/nginx-clojure
$ export nginx_clojure_root=/home/who/git/nginx-clojure

## at the nginx source dir
$ auto/configure --add-module=${nginx_clojure_root}/src/c \
--add-module=${nginx_clojure_root}/example-projects/c-module-integration-example/src/c
make -j
```

Then we'll get `objs/nginx` viz. the executable nginx binary file.

## Build Java handler

```shell
$ cd $nginx_clojure_root/example-projects/c-module-integration-example
$ mvn package
```
Then we'll get `target/c-module-integration-example-0.0.1.jar`.

## Configure nginx.conf

See $nginx_clojure_root/example-projects/c-module-integration-example/conf/nginx.conf
Make sure `jvm_classpath` and other nginx-clojure directives are rightly configurated.

## Run this example

```shell
## run nginx
$ ./nginx
## test it by curl
$ curl -v http://localhost:8080/hello
```
Then we 'll get the result :

```
*   Trying 127.0.0.1...
* Connected to localhost (127.0.0.1) port 8080 (#0)
> GET /hello HTTP/1.1
> User-Agent: curl/7.35.0
> Host: localhost:8080
> Accept: */*
> 
< HTTP/1.1 200 OK
< Server: nginx/1.8.0
< Date: Fri, 06 Nov 2015 16:36:15 GMT
< Content-Type: text/plain
< Content-Length: 12
< Connection: keep-alive
< 
HELLO, WORLD
```

