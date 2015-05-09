package nginx.clojure.java;

import java.util.Map;

import nginx.clojure.MessageListener;
import nginx.clojure.NginxClojureRT;
import nginx.clojure.NginxHttpServerChannel;

public class WSEcho implements NginxJavaRingHandler {

	@Override
	public Object[] invoke(Map<String, Object> request) {
		NginxJavaRequest r = (NginxJavaRequest)request;
		NginxHttpServerChannel sc = r.handler().hijack(r, true);
		sc.addListener(sc, new MessageListener<NginxHttpServerChannel>() {

			@Override
			public void onClose(NginxHttpServerChannel data) {
			}

			@Override
			public void onConnect(long status, NginxHttpServerChannel data) {
				NginxClojureRT.log.info("WSEcho connected!");
			}

			@Override
			public void onRead(long status, NginxHttpServerChannel data) {
			}

			@Override
			public void onWrite(long status, NginxHttpServerChannel data) {
			}

			@Override
			public void onTextMessage(NginxHttpServerChannel sc, String message, boolean remining) {
				NginxClojureRT.log.info("WSEcho onTextMessage: msg=%s, rem=%s", message, remining);
				sc.send(message, !remining, false);
			}

			@Override
			public void onBinaryMessage(NginxHttpServerChannel data, byte[] message, boolean remining) {
				
			}
		});
		return null;
	}

}
