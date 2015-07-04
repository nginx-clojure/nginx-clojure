package nginx.clojure.java;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import nginx.clojure.MessageAdapter;
import nginx.clojure.NginxClojureRT;
import nginx.clojure.NginxHttpServerChannel;
import nginx.clojure.SuspendExecution;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

public class WSRemote implements NginxJavaRingHandler {

	@Override
	public Object[] invoke(Map<String, Object> request) throws IOException {

		NginxJavaRequest r = (NginxJavaRequest)request;
		NginxHttpServerChannel sc = r.handler().hijack(r, true);
		sc.addListener(sc, new MessageAdapter<NginxHttpServerChannel>() {
			int total = 0;
			@Override
			public void onOpen(NginxHttpServerChannel data) {
				NginxClojureRT.log.debug("WSRemote onOpen!");
			}

			@Override
			public void onTextMessage(NginxHttpServerChannel sc, String message, boolean remaining) throws IOException, SuspendExecution {
				if (NginxClojureRT.log.isDebugEnabled()) {
					NginxClojureRT.log.debug("WSRemote onTextMessage: msg=%s, rem=%s", message, remaining);
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
