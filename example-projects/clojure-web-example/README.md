# Clojure Web Example for Nginx-Clojure

A basic example about nginx-clojure & clojure web dev. It uses:
* [Compojure](https://github.com/weavejester/compojure) (for uri routing)
* [Hiccup](https://github.com/weavejester/hiccup) (for html rendering)
* [Websocket API](http://nginx-clojure.github.io/more.html#38--sever-side-websocket) & [Sub/Pub API]() (to demo a simple chatroom)
* ring.middleware.reload (for auto-reloading modified namespaces in dev environments)


## Run It

Suppose our example project path is `/home/who/git/nginx-clojure/example-projects/clojure-web-example` .

```shell
$ export EXAMPLE_ROOT=/home/who/git/nginx-clojure/example-projects/clojure-web-example
$ cd $EXAMPLE_ROOT
$ lein with-profile embed run
```

Then browser http://localhost:8080/

When we do some modifications the related namespaces will be auto-reloaded 
so that we need not restart lein repl.


## Deploy with embeded Nginx-Clojure (for Small Projects)

*  **Build embed standalone jar file**
```shell
$ cd $EXAMPLE_ROOT
## build a standalone clojure-web-example-embed.jar at target/uberjar/
$ lein with-profile embed uberjar
```
*  **Start the embed server**
```shell
## suppose we deploy it to directory testdeploy
$ cd testdeploy
## make sure directory testdeploy has file clojure-web-example-embed.jar and logback.xml
## otherwise we need copy them to it. Then run the server by below command.
$ java -cp . -jar clojure-web-example-embed.jar 8080
```

## Deploy on Normal Nginx

*  **Build stand-alone jar file**

```shell
$ cd $EXAMPLE_ROOT
## build a standalone clojure-web-example-default.jar at target/uberjar/
$ lein uberjar
```
*  **Get binaries of Nginx-Clojure**

We can download the binaries of Nginx compiled with Nginx-Clojure or we can compile nginx-clojure with
our nginx by [this guide](http://nginx-clojure.github.io/installation.html).
```shell
$ cd /tmp
$ wget https://sourceforge.net/projects/nginx-clojure/files/nginx-clojure-0.4.3.tar.gz
$ tar -xzvf nginx-clojure-0.4.3.tar.gz
$ sudo mv nginx-clojure-0.4.3 /opt/
```
*  **Create OS user**

```shell
## create user nginx who will be run as by Nginx Worker processes
## we have specified this by directive `user nginx nginx;` in nginx.conf
$ sudo adduser --system --no-create-home --disabled-login --disabled-password --group nginx
```
*  **Copy files**

```shell
$ sudo mkdir -p /opt/nginx-clojure-0.4.3/libs/res
$ sudo cp $EXAMPLE_ROOT/target/uberjar/clojure-web-example-default.jar /opt/nginx-clojure-0.4.3/libs
$ sudo cp $EXAMPLE_ROOT/conf/logback.xml /opt/nginx-clojure-0.4.3/libs/res
$ sudo cp $EXAMPLE_ROOT/conf/nginx.conf /opt/nginx-clojure-0.4.3/conf/nginx.conf
$ sudo cp -R $EXAMPLE_ROOT/resources/public /opt/nginx-clojure-0.4.3/
```
*  **Grant permissions**

```shell
## make /opt/nginx-clojure-0.4.3 searchable
$ sudo chmod o+x /opt
$ sudo chmod o+x /opt/nginx-clojure-0.4.3
$ cd /opt/nginx-clojure-0.4.3
$ sudo cp jars/nginx-clojure-0.4.3.jar libs/
## rename nginx-xxx to nginx
$ sudo mv nginx-linux-x64 nginx
## grant minimal permissions for safety
$ sudo chmod o+rx $(sudo find libs -type d)
$ sudo chmod o+r $(sudo find libs -type f)
$ sudo chmod -R -w public
$ sudo chown -R nginx public logs temp
$ sudo chmod -R u+rwx logs temp
$ sudo chmod u+rx $(sudo find public -type d)
$ sudo chmod u+r $(sudo find public -type f)
```
*  **Check configuration**

```shell
$ sudo ./nginx -t
```
*  **Start/Reload/Stop**

```shell
##start and we can check error in file logs/error.log
$ sudo ./nginx
## list nginx processes
$ ps aux | grep nginx
##reload when configuration changes without stopping our service.
$ sudo ./nginx -s reload
##stop
$ sudo ./nginx -s stop
```



