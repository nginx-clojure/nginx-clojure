package nginx.clojure.java;

import java.nio.ByteBuffer;
import java.util.Map;

import nginx.clojure.MessageAdapter;
import nginx.clojure.NginxClojureRT;
import nginx.clojure.NginxHttpServerChannel;

public class WSEcho implements NginxJavaRingHandler {

	@Override
	public Object[] invoke(Map<String, Object> request) {
		NginxJavaRequest r = (NginxJavaRequest)request;
		NginxHttpServerChannel sc = r.handler().hijack(r, true);
		sc.addListener(sc, new MessageAdapter<NginxHttpServerChannel>() {
			int total = 0;
			@Override
			public void onOpen(NginxHttpServerChannel data) {
				NginxClojureRT.log.debug("WSEcho onOpen!");
			}

			@Override
			public void onTextMessage(NginxHttpServerChannel sc, String message, boolean remaining) {
				if (NginxClojureRT.log.isDebugEnabled()) {
					NginxClojureRT.log.debug("WSEcho onTextMessage: msg=%s, rem=%s", message, remaining);
				}
				total += message.length();
				sc.send(message, !remaining, false);
			}
			
			@Override
			public void onBinaryMessage(NginxHttpServerChannel sc, ByteBuffer message, boolean remining) {
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
