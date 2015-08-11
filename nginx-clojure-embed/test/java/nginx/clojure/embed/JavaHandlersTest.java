/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.embed;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import nginx.clojure.MessageAdapter;
import nginx.clojure.NginxClojureRT;
import nginx.clojure.NginxHttpServerChannel;
import nginx.clojure.WholeMessageAdapter;
import nginx.clojure.java.ArrayMap;
import nginx.clojure.java.NginxJavaRequest;
import nginx.clojure.java.NginxJavaRingHandler;

import org.apache.http.ParseException;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class JavaHandlersTest {
	
	public static class HelloHandler implements NginxJavaRingHandler {

		@Override
		public Object[] invoke(Map<String, Object> request) throws IOException {
			return new Object[]{200, null, "Hello, Embeded Nginx!"};
		}
		
	}
	
	public static class WebSocketWholeMessageEcho implements NginxJavaRingHandler {
		@Override
		public Object[] invoke(Map<String, Object> request) throws IOException {
			NginxHttpServerChannel ch = ((NginxJavaRequest)request).hijack(true);
			if (ch.webSocketUpgrade(true)) {
				ch.addListener(ch, new WholeMessageAdapter<NginxHttpServerChannel>(9*1024){
					@Override
					public void onOpen(NginxHttpServerChannel sc)
							throws IOException {
						System.out.println("on open!");
					}
					@Override
					public void onWholeTextMessage(NginxHttpServerChannel sc,
							String message) throws IOException {
						sc.send(message, true, false);
					}
				});
			}
			return null;
		}
	}
	
	public static class WebSocketFullFunctionEcho implements NginxJavaRingHandler {
		@Override
		public Object[] invoke(Map<String, Object> request) throws IOException {

			NginxJavaRequest r = (NginxJavaRequest)request;
			NginxHttpServerChannel sc = r.hijack(true);
			if (!sc.webSocketUpgrade(true))  {
				return null;
			}
			sc.addListener(sc, new MessageAdapter<NginxHttpServerChannel>() {
				int total = 0;
				@Override
				public void onOpen(NginxHttpServerChannel data) {
					NginxClojureRT.log.debug("WSEcho onOpen!");
				}

				@Override
				public void onTextMessage(NginxHttpServerChannel sc, String message, boolean remaining) throws IOException {
					if (NginxClojureRT.log.isDebugEnabled()) {
						NginxClojureRT.log.debug("WSEcho onTextMessage: msg=%s, rem=%s", message, remaining);
					}
					total += message.length();
					sc.send(message, !remaining, false);
				}
				
				@Override
				public void onBinaryMessage(NginxHttpServerChannel sc, ByteBuffer message, boolean remining) throws IOException {
					if (NginxClojureRT.log.isDebugEnabled()) {
						NginxClojureRT.log.debug("WSEcho onBinaryMessage: msg=%s, rem=%s, total=%d", message, remining, total);
					}
					total += message.remaining();
					sc.send(message, !remining, false);
				}
				
				@Override
				public void onClose(NginxHttpServerChannel req) {
					if (NginxClojureRT.log.isDebugEnabled()) {
						NginxClojureRT.log.debug("WSEcho onClose: total=%d", total);
					}
					
				}
				
				@Override
				public void onClose(NginxHttpServerChannel req, long status, String reason) {
					if (NginxClojureRT.log.isDebugEnabled()) {
					  NginxClojureRT.log.info("WSEcho onClose2: total=%d, status=%d, reason=%s", total, status, reason);
					}
				}
				
				@Override
				public void onError(NginxHttpServerChannel data, long status) {
					if (NginxClojureRT.log.isDebugEnabled()) {
						  NginxClojureRT.log.info("WSEcho onError: total=%d, status=%d", total, status);
					}
				}

			});
			return null;
		
		}
	}
	
	public static class SimpleRouting implements NginxJavaRingHandler {
		Map<String, NginxJavaRingHandler> handlers = new HashMap<String, NginxJavaRingHandler>();
		public SimpleRouting() {
			handlers.put("/hello", new HelloHandler());
			handlers.put("/websocket-echo", new WebSocketFullFunctionEcho());
			handlers.put("/websocket-whecho", new WebSocketWholeMessageEcho());
		}
		@Override
		public Object[] invoke(Map<String, Object> request) throws IOException {
			return handlers.get(request.get("uri")).invoke(request);
		}
		
	}

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testStartAndStop() throws ParseException, ClientProtocolException, IOException {
		NginxEmbedServer server = NginxEmbedServer.getServer();
		Map<String, String> opts = ArrayMap.create("port", "8081");
		server.start(SimpleRouting.class.getName(), opts);
		CloseableHttpClient httpclient = HttpClients.createDefault();
		HttpGet httpget = new HttpGet("http://localhost:8081/hello");
		String resp = EntityUtils.toString(httpclient.execute(httpget).getEntity());
		Assert.assertEquals("Hello, Embeded Nginx!", resp);
		server.stop();
	}
	
	public static void main(String[] args) {
		NginxEmbedServer server = NginxEmbedServer.getServer();
		server.setWorkDir("test/work-dir");
		Map<String, String> opts = ArrayMap.create("port", "8081");
		server.start(SimpleRouting.class.getName(), opts);
	}
}
