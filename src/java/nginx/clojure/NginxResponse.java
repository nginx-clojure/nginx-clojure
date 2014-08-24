package nginx.clojure;

import java.util.Collection;
import java.util.Map;

public interface NginxResponse {
	
	public int fetchStatus(int defaultStatus);
	
	public <K,V> Collection<Map.Entry<K, V>> fetchHeaders();
	
	public Object fetchBody();
	
	public NginxRequest request();
	
}
