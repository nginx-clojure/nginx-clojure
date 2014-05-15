package nginx.clojure.net;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import nginx.clojure.Constants;
import nginx.clojure.LazyRequestMap;
import nginx.clojure.NginxClojureRT;
import nginx.clojure.logger.LoggerService;
import clojure.lang.AFn;
import clojure.lang.PersistentArrayMap;

public class SimpleHandler4TestNginxClojureAsynSocket extends AFn{

	public static class AsynHttpContext {
		int rc;
		int wc;
		boolean reqSent;
		byte[] req;
		byte[] buf;
		ByteArrayOutputStream resp;
		long clientRequest;
	}
	
	static LoggerService log;
	
	public SimpleHandler4TestNginxClojureAsynSocket() {
		if (log == null) {
			log = NginxClojureRT.getLog();
		}
	}
	
	@Override
	public Object invoke(Object r) {
		LazyRequestMap req = (LazyRequestMap) r;
		
		NginxClojureAsynSocket asynSocket = new NginxClojureAsynSocket();
		AsynHttpContext ctx = new AsynHttpContext();
		ctx.rc = ctx.wc = 0;
//		tell server we won't keep-alive, we have two choices :
//		(1) http header  "Connection" = close
//      (2) after send all request, call s.shutdown(NginxClojureAsynSocket.NGX_HTTP_CLOJURE_SOCKET_SHUTDOWN_WRITE);
//      here we will use choice (1)
//		http://mirror.bit.edu.cn/apache/httpcomponents/httpclient/RELEASE_NOTES-4.3.x.txt
		ctx.req = "GET /apache/httpcomponents/httpclient/RELEASE_NOTES-4.3.x.txt HTTP/1.1\r\nUser-Agent: curl/7.32.0\r\nHost: mirror.bit.edu.cn\r\nAccept: */*\r\nConnection: close\r\n\r\n".getBytes();
		ctx.buf = new byte[1024];
		ctx.resp = new ByteArrayOutputStream();
		ctx.clientRequest = req.nativeRequest();
		asynSocket.setContext(ctx);
		asynSocket.setHandler(new NginxClojureSocketHandler() {
			
			@Override
			public void onWrite(NginxClojureAsynSocket s, long sc) {
				if (sc != NginxClojureAsynSocket.NGX_HTTP_CLOJURE_SOCKET_OK) {
					log.error("onWrite error %d", sc);
					s.close();
					AsynHttpContext ctx = s.getContext();
					NginxClojureRT.completeAsyncResponse(ctx.clientRequest, 500);
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
						NginxClojureRT.completeAsyncResponse(ctx.clientRequest, 500);
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
			public void onRead(NginxClojureAsynSocket s, long sc) {
				if (sc != NginxClojureAsynSocket.NGX_HTTP_CLOJURE_SOCKET_OK) {
					log.error("onRead error %d", sc);
					s.close();
					AsynHttpContext ctx = s.getContext();
					NginxClojureRT.completeAsyncResponse(ctx.clientRequest, 500);
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
							NginxClojureRT.completeAsyncResponse(ctx.clientRequest, 500);
						}else if (n == 0){
							log.info("fininsh request total read: %d", ctx.rc);
							s.close();
							Object[] resps = new Object[] {
									Constants.STATUS,
									200,
									Constants.HEADERS,
									new PersistentArrayMap(new Object[] {
											Constants.CONTENT_TYPE.getName(),
											"text/html" }),
							Constants.BODY, new ByteArrayInputStream(ctx.resp.toByteArray()) };
							//just for test not for good performance and right behavior for a http proxy
							NginxClojureRT.completeAsyncResponse(ctx.clientRequest, new  PersistentArrayMap(resps));
						}else {
							ctx.rc += n;
							ctx.resp.write(ctx.buf, 0, (int)n);
							log.info("read %d, total: %d", n, ctx.rc);
						}
					}while (n > 0);
				}
			}
			
			@Override
			public void onConnect(NginxClojureAsynSocket s, long sc) {
				if (sc != NginxClojureAsynSocket.NGX_HTTP_CLOJURE_SOCKET_OK) {
					log.error("onConnect error %d", sc);
					s.close();
					AsynHttpContext ctx = s.getContext();
					NginxClojureRT.completeAsyncResponse(ctx.clientRequest, 500);
					return;
				}
				log.info("connected now!");
			}
		});
		asynSocket.connect("mirror.bit.edu.cn:80");
		//tell nginx clojure our work isn't done.
		return NginxClojureRT.ASYNC_TAG;
	}

}
