package nginx.clojure.bridge;

import java.io.IOException;
import java.util.Map;

import nginx.clojure.Configurable;
import nginx.clojure.java.NginxJavaRequest;
import nginx.clojure.java.NginxJavaRingHandler;

public class NginxBridgeHandler implements NginxJavaRingHandler, Configurable {

	protected NginxBridge bridge;
	
	public void setBridge(NginxBridge bridge) {
		this.bridge = bridge;
	}
	
	public NginxBridge getBridge() {
		return bridge;
	}
	
	public NginxBridgeHandler() {
	}
	
	@Override
	public Object[] invoke(Map<String, Object> req) throws IOException {
		NginxJavaRequest r = (NginxJavaRequest)req;
		return bridge.handle((NginxJavaRequest)req);
	}

	public static void main(String[] args) {
		NginxBridgeHandler n = new NginxBridgeHandler();
	}

	@Override
	public void config(Map<String, String> properties) {
		if (bridge == null) {
			NginxBridgeStarter starter = new NginxBridgeStarter();
			starter.start(properties, this);
		}
	}
}
