package nginx.clojure;

import java.util.Map;

public interface NginxFilterRequest extends NginxRequest {

	public int responseStatus();
	
	public Map<String, Object> responseHeaders();
	
	public long chunkChain();
	
	public boolean isLast();
}
