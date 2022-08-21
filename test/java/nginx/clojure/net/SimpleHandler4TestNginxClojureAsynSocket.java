package nginx.clojure.net;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

import nginx.clojure.NginxClojureRT;
import nginx.clojure.NginxHttpServerChannel;
import nginx.clojure.NginxRequest;
import nginx.clojure.java.ArrayMap;
import nginx.clojure.java.Constants;
import nginx.clojure.java.NginxJavaRingHandler;
import nginx.clojure.logger.LoggerService;

public class SimpleHandler4TestNginxClojureAsynSocket implements NginxJavaRingHandler {

	public static class AsynHttpContext {
		int rc;
		int wc;
		boolean reqSent;
		byte[] req;
		byte[] buf;
		ByteArrayOutputStream resp;
		NginxHttpServerChannel downstreamChannel;
	}
	
	static LoggerService log;
	
	public SimpleHandler4TestNginxClojureAsynSocket() {
		if (log == null) {
			log = NginxClojureRT.getLog();
		}
	}
	
	@Override
	public Object[] invoke(Map<String, Object> request) {
		NginxRequest req = (NginxRequest) request;
		NginxHttpServerChannel serverChannel = req.hijack(false);
		@SuppressWarnings("resource")
		NginxClojureAsynSocket asynSocket = new NginxClojureAsynSocket();
		AsynHttpContext ctx = new AsynHttpContext();
		ctx.rc = ctx.wc = 0;
//		tell server we won't keep-alive, we have two choices :
//		(1) http header  "Connection" = close
//      (2) after send all request, call s.shutdown(NginxClojureAsynSocket.NGX_HTTP_CLOJURE_SOCKET_SHUTDOWN_WRITE);
//      here we will use choice (1)
//		http://www.apache.org/apache/httpcomponents/httpclient/RELEASE_NOTES-4.3.x.txt
		ctx.req = "GET /dist/httpcomponents/httpclient/RELEASE_NOTES-4.3.x.txt HTTP/1.1\r\nUser-Agent: nginx-clojure/0.2.5\r\nHost: www.apache.org\r\nAccept: */*\r\nConnection: close\r\n\r\n".getBytes();
		ctx.buf = new byte[1024];
		ctx.resp = new ByteArrayOutputStream();
		ctx.downstreamChannel = serverChannel;
		asynSocket.setContext(ctx);
		asynSocket.setHandler(new NginxClojureSocketHandler() {
			
			@Override
			public void onWrite(NginxClojureAsynSocket s, long sc) throws IOException {
				if (sc != NginxClojureAsynSocket.NGX_HTTP_CLOJURE_SOCKET_OK) {
					log.error("onWrite error %d", sc);
					s.close();
					AsynHttpContext ctx = s.getContext();
					ctx.downstreamChannel.sendResponse(500);
					return;
				}
				AsynHttpContext ctx = s.getContext();
				if (ctx.reqSent) {
					log.info("after request meet write again, just ignored..........");
					//we don't need to write data now
					s.shutdown(NginxClojureAsynSocket.NGX_HTTP_CLOJURE_SOCKET_SHUTDOWN_SOFT_WRITE);
					return;
				}
				
				long n = 0;
				//write as much as possible
				do{
					n = s.write(ctx.req, ctx.wc, ctx.req.length-ctx.wc);
					if (n < 0) {
						if (n == NginxClojureAsynSocket.NGX_HTTP_CLOJURE_SOCKET_ERR_AGAIN) {
							log.info("we 'll try write again");
							return;
						}
						log.error("write error : %s", n);
						s.close();
						ctx.downstreamChannel.sendResponse(500);
						return;
					}else {
						ctx.wc += n;
						log.info("write %d, total %d", n, ctx.wc);
						if (ctx.wc == ctx.req.length) {
							ctx.reqSent = true;
//							s.shutdown(NginxClojureAsynSocket.NGX_HTTP_CLOJURE_SOCKET_SHUTDOWN_WRITE);
							log.info("fininsh write total write: %d", ctx.wc);
							return;
						}
					}
				}while(n > 0);
			}
			
			@Override
			public void onRelease(NginxClojureAsynSocket s, long sc) {
				log.info("released %d", sc);
			}
			
			@Override
			public void onRead(NginxClojureAsynSocket s, long sc) throws IOException {
				if (sc != NginxClojureAsynSocket.NGX_HTTP_CLOJURE_SOCKET_OK) {
					log.error("onRead error %d", sc);
					s.close();
					AsynHttpContext ctx = s.getContext();
					ctx.downstreamChannel.sendResponse(500);
					return;
				}
				AsynHttpContext ctx = s.getContext();
				if (ctx.wc != ctx.req.length) {
					log.warn("we have not write all request!");
				}else {
					long n = 0;
					//read as much as possible
					do {
						n = s.read(ctx.buf, 0, ctx.buf.length);
						if (n < 0) {
							if (n == NginxClojureAsynSocket.NGX_HTTP_CLOJURE_SOCKET_ERR_AGAIN) {
								log.info("we 'll try read again");
								return;
							}
							log.error("read error : %s", n);
							s.close();
							ctx.downstreamChannel.sendResponse(500);
						}else if (n == 0){
							log.info("fininsh request total read: %d", ctx.rc);
							s.close();
							Object[] resps = new Object[] {
									200,
									ArrayMap.create(Constants.CONTENT_TYPE, "text/html"),
							        new ByteArrayInputStream(ctx.resp.toByteArray()) };
							//just for test not for good performance and right behavior for a http proxy
							ctx.downstreamChannel.sendResponse(resps);
						}else {
							ctx.rc += n;
							ctx.resp.write(ctx.buf, 0, (int)n);
							log.info("read %d, total: %d", n, ctx.rc);
						}
					}while (n > 0);
				}
			}
			
			@Override
			public void onConnect(NginxClojureAsynSocket s, long sc) throws IOException {
				if (sc != NginxClojureAsynSocket.NGX_HTTP_CLOJURE_SOCKET_OK) {
					log.error("onConnect error %d", sc);
					s.close();
					AsynHttpContext ctx = s.getContext();
					ctx.downstreamChannel.sendResponse(500);
					return;
				}
				log.info("connected now!");
			}
		});
		asynSocket.setTimeout(10000, 10000, 10000);
		asynSocket.connect("www.apache.org:80");
		return null;
	}

}
