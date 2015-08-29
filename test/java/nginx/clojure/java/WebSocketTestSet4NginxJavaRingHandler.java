/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.java;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Map;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import nginx.clojure.HackUtils;
import nginx.clojure.MessageAdapter;
import nginx.clojure.NginxClojureRT;
import nginx.clojure.NginxHttpServerChannel;
import nginx.clojure.SuspendExecution;
import nginx.clojure.WholeMessageAdapter;
import nginx.clojure.anno.Suspendable;


public class WebSocketTestSet4NginxJavaRingHandler {

	/**
	 * This websocket echo handler works with nginx directive `auto_upgrade_ws on;`
	 */
	public static class WSEcho implements NginxJavaRingHandler {

		@Override
		public Object[] invoke(Map<String, Object> request) {
			NginxJavaRequest r = (NginxJavaRequest)request;
			NginxHttpServerChannel sc = r.hijack(true);
			
			sc.addListener(sc, new MessageAdapter<NginxHttpServerChannel>() {
				int total = 0;
				@Override
				public void onOpen(NginxHttpServerChannel data) {
					NginxClojureRT.log.debug("WSEcho onOpen!");
				}

				@Override
				public void onTextMessage(NginxHttpServerChannel sc, String message, boolean remaining) throws IOException {
					if (NginxClojureRT.log.isDebugEnabled()) {
						NginxClojureRT.log.debug("WSEcho onTextMessage: msg=%s, rem=%s", HackUtils.truncateToDotAppendString(message, 10), remaining);
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
	
	public static class NonAutoUpgradeWSEcho extends WSEcho {

		@Override
		public Object[] invoke(Map<String, Object> request) {
			NginxJavaRequest r = (NginxJavaRequest)request;
			NginxHttpServerChannel sc = r.hijack(true);
			//If we use nginx directive `auto_upgrade_ws on;`, these three lines can be omitted.
			if (!sc.webSocketUpgrade(true)) {
				return null;
			}
			return super.invoke(request);
		}
	}
	
	@Suspendable
	public static class WSRemote implements NginxJavaRingHandler {

		@Override
		public Object[] invoke(Map<String, Object> request) throws IOException {

			NginxJavaRequest r = (NginxJavaRequest)request;
			NginxHttpServerChannel sc = r.hijack(true);
			//If we use nginx directive `auto_upgrade_ws on;`, these three lines can be omitted.
			if (!sc.webSocketUpgrade(true)) {
				return null;
			}
			sc.addListener(sc, new MessageAdapter<NginxHttpServerChannel>() {
				int total = 0;
				@Override
				public void onOpen(NginxHttpServerChannel data) {
					NginxClojureRT.log.debug("WSRemote onOpen!");
				}

				@Override
				@Suspendable
				public void onTextMessage(NginxHttpServerChannel sc, String message, boolean remaining) throws IOException, SuspendExecution {
					if (NginxClojureRT.log.isDebugEnabled()) {
						NginxClojureRT.log.debug("WSRemote onTextMessage: msg=%s, rem=%s", HackUtils.truncateToDotAppendString(message, 10), remaining);
					}
					
					CloseableHttpClient httpclient = HttpClients.createDefault();
					HttpGet httpget = new HttpGet(message);
					CloseableHttpResponse response = null;
					try {
						response = httpclient.execute(httpget);
						InputStream in = response.getEntity().getContent();
						byte[] buf = new byte[1024];
						int c = 0;
						ByteArrayOutputStream out = new ByteArrayOutputStream();
						while ((c = in.read(buf)) > 0) {
							out.write(buf, 0, c);
						}
						in.close();
						sc.send(new String(out.toByteArray()), true, false);
					} finally {
						if (httpclient != null) {
							try {
								httpclient.close();
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
					}
					
				}
				
				@Override
				public void onClose(NginxHttpServerChannel req) {
					if (NginxClojureRT.log.isDebugEnabled()) {
						NginxClojureRT.log.debug("WSRemote onClose: total=%d", total);
					}
				}
				
				@Override
				public void onClose(NginxHttpServerChannel req, long status, String reason) {
					if (NginxClojureRT.log.isDebugEnabled()) {
					  NginxClojureRT.log.info("WSRemote onClose2: total=%d, status=%d, reason=%s", total, status, reason);
					}
				}
				
				@Override
				public void onError(NginxHttpServerChannel data, long status) {
					if (NginxClojureRT.log.isDebugEnabled()) {
						  NginxClojureRT.log.info("WSRemote onError: total=%d, status=%d", total, status);
						}
				}

			});
			return null;
		
		}
	}
	
	public static class WSWholeTextHandler implements NginxJavaRingHandler {

		@Override
		public Object[] invoke(Map<String, Object> request) throws IOException {
			NginxJavaRequest r = (NginxJavaRequest)request;
			NginxHttpServerChannel sc = r.hijack(true);
			//If we use nginx directive `auto_upgrade_ws on;`, these three lines can be omitted.
			if (!sc.webSocketUpgrade(true)) {
				return null;
			}
			sc.addListener(sc, new WholeMessageAdapter<NginxHttpServerChannel>(9*1024) {
				/* (non-Javadoc)
				 * @see nginx.clojure.WholeMessageAdapter#onWholeTextMessage(java.lang.Object, java.lang.String)
				 */
				@Override
				public void onWholeTextMessage(NginxHttpServerChannel ch, String message) throws IOException {
					ch.send(message, true, false);
				}
			});
			return null;
		}
	}
	
	public static class WSWholeMessageHandler implements NginxJavaRingHandler {

		@Override
		public Object[] invoke(Map<String, Object> request) throws IOException {
			NginxJavaRequest r = (NginxJavaRequest)request;
			NginxHttpServerChannel sc = r.hijack(true);
			//If we use nginx directive `auto_upgrade_ws on;`, these three lines can be omitted.
			if (!sc.webSocketUpgrade(true)) {
				return null;
			}
			sc.addListener(sc, new WholeMessageAdapter<NginxHttpServerChannel>(64*1024) {
				/* (non-Javadoc)
				 * @see nginx.clojure.WholeMessageAdapter#onWholeTextMessage(java.lang.Object, java.lang.String)
				 */
				@Override
				public void onWholeTextMessage(NginxHttpServerChannel ch, String message) throws IOException {
					ch.send(message, true, false);
				}
				
				/* (non-Javadoc)
				 * @see nginx.clojure.WholeMessageAdapter#onWholeBiniaryMessage(java.lang.Object, java.nio.ByteBuffer)
				 */
				@Override
				public void onWholeBiniaryMessage(NginxHttpServerChannel ch, ByteBuffer message) throws IOException {
					ch.send(message, true, false);
				}
			});
			return null;
		}
	}
}
