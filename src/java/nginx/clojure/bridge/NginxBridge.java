package nginx.clojure.bridge;

import java.io.IOException;
import java.util.Map;

import nginx.clojure.java.NginxJavaRequest;

public interface NginxBridge {
	
	public void boot(Map<String, String> properties);
	public Object[] handle(NginxJavaRequest req) throws IOException;
	
}
