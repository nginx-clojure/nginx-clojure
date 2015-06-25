package nginx.clojure.bridge;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import nginx.clojure.Configurable;
import nginx.clojure.java.NginxJavaRequest;
import nginx.clojure.java.NginxJavaRingHandler;

public class NginxBridgeHandler implements NginxJavaRingHandler, Configurable {

	protected NginxBridge bridge;
	protected String alias;
	protected static Map<String, NginxBridge> aliases = new HashMap<String, NginxBridge>();
	
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
		if (bridge == null) {
			bridge = aliases.get(alias);
		}
		if (bridge == null) {
			return new Object[]{404, null, null};
		}
		NginxJavaRequest r = (NginxJavaRequest)req;
		ClassLoader oldLoader = Thread.currentThread().getContextClassLoader();
		try {
			Thread.currentThread().setContextClassLoader(bridge.getClassLoader());
			return bridge.handle(r);
		}finally {
			Thread.currentThread().setContextClassLoader(oldLoader);
		}
		
	}

	@Override
	public void config(Map<String, String> properties) {
		if (bridge == null) {
			String alias = properties.get(NginxBridgeStarter.BRIDGE_ALIAS);
			String impl = properties.get(NginxBridgeStarter.BRIDGE_IMP);
			if (alias != null) {
				if (impl == null) {
					return;
				}
			}
			NginxBridgeStarter starter = new NginxBridgeStarter();
			bridge = starter.start(properties);
			if (alias != null) {
				aliases.put(alias, bridge);
			}
			
		}
	}
}
