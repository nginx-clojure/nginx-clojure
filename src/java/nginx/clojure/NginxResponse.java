package nginx.clojure;

import java.util.Collection;
import java.util.Map;

public interface NginxResponse {
	
	public long buildOutputChain(long r);
	
	public int fetchStatus(int defaultStatus);
	
	public Collection<Map.Entry> fetchHeaders();
	
	public Object fetchBody();
	
}
