package nginx.clojure.java;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;

import nginx.clojure.ChannelCloseAdapter;
import nginx.clojure.HackUtils;
import nginx.clojure.MiniConstants;
import nginx.clojure.NginxClojureRT;
import nginx.clojure.NginxHttpServerChannel;
import nginx.clojure.logger.LoggerService;
import nginx.clojure.net.NginxClojureAsynSocket;

/**
 * 
 * This handler is only used to demo NginxHttpServerChannel 's low level write which is no buffer used
 * and  non-blocking.In this handler we only use a small buffer (4k) to send a large content to client so only
 * 4M memory need for 1000 concurrent users.
 * If you want to implement a dynamic proxy please use rewrite handler + proxy_pass & proxy_buffering 
 *  & proxy_buffer_size nginx will mange buffer carefully.
 * If you want to service large static file please just return java.io.File Object as Reponse body 
 */
public class StreamingWriteHandler implements NginxJavaRingHandler {

	//initialize large_content_for_demo
	static byte[] large_content_for_demo = new byte[30*1024*1024]; //30M
	static byte[] resp_headers;
	static {
		resp_headers = ("HTTP/1.1 200 OK\r\n"
				+ "Date: Wed, 08 Jul 2015 07:12:26 GMT\r\n" 
	            + "Content-Type: text/plain\r\n"
				+ "Content-Length: "+large_content_for_demo.length+"\r\n"
	            +"\r\n").getBytes();
		for (int i = resp_headers.length; i < large_content_for_demo.length; i++) {
			large_content_for_demo[i] = (byte) ('a' + (i % 26));
		}
	}

	static LoggerService log = NginxClojureRT.getLog();
	
	public static class MyStreamContext {
		public ByteBuffer headers;
		public ByteBuffer content;
		public ByteBuffer buffer = ByteBuffer.allocate(4096);
		public boolean headSent;
		public MyStreamContext(ByteBuffer headers, ByteBuffer content) {
			this.headers = headers;
			this.content = content;
			this.headSent = false;
			buffer.flip();
		}
	}
	
	@Override
	public Object[] invoke(Map<String, Object> request) throws IOException {
		NginxHttpServerChannel ch = ((NginxJavaRequest)request).hijack(true);
		//make access log have correct http status
		NginxClojureRT.pushNGXInt(((NginxJavaRequest) request).nativeRequest() + MiniConstants.NGX_HTTP_CLOJURE_REQ_HEADERS_OUT_OFFSET
				+ MiniConstants.NGX_HTTP_CLOJURE_HEADERSO_STATUS_OFFSET, 200);
		ch.turnOnEventHandler(false, true, true);//turn on write event trigger and non-keepalive
		ByteBuffer headers = ByteBuffer.wrap(resp_headers);
		ByteBuffer content = ByteBuffer.wrap(large_content_for_demo);
		ch.setContext(new MyStreamContext(headers, content));
		ch.addListener(ch, new ChannelCloseAdapter<NginxHttpServerChannel>() {
			@Override
			public void onClose(NginxHttpServerChannel data) throws IOException {
				log.info("StreamingWriteHandler closed now!");
			}

			@Override
			public void onWrite(long status, NginxHttpServerChannel ch) throws IOException {
				if (status < 0) {
					log.error("onWrite error %s", NginxClojureAsynSocket.errorCodeToString(status));
					ch.close();
				}else {
					doWrite(ch);
				}
			}
		});
		doWrite(ch);
		return null;
	}
	
	protected void doWrite(NginxHttpServerChannel ch) throws IOException {
		try{
			int c = 0;
			MyStreamContext ctx = (MyStreamContext) ch.getContext();
			do {
				if (ctx.buffer.hasRemaining()) {
//					int oldPos = ctx.buffer.position();
					c = (int)ch.write(ctx.buffer);
					if (c == 0) {
						break;
					}
				}else {
					ctx.buffer.clear();
					if (ctx.headSent) {
						if (ctx.content.remaining() == 0) {
							ch.close();
							break;
						}else {
							HackUtils.putBuffer(ctx.buffer, ctx.content);
						}
					}else {
						HackUtils.putBuffer(ctx.buffer, ctx.headers);
						if (ctx.headers.remaining() == 0) {
							ctx.headSent = true;
						}
					}
					ctx.buffer.flip();
				}
			}while (true);
		}catch(Throwable e) {
			log.error("write error!", e);
			ch.close();
		}
	}

}
