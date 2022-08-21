package nginx.clojure;

import java.util.Collection;
import java.util.Map;

public interface NginxResponse {
	
	public static int TYPE_FAKE_PHASE_DONE = -5000;
	public static int TYPE_FAKE_ASYNC_TAG = -5001;
	public static int TYPE_FAKE_BODY_FILTER_TAG = -5002;
	public static int TYPE_NORMAL = 0;
	public static int TYPE_ERROR = 1;
	public static int TYPE_FATAL = 2;
	public static Object[] EMPTY_RESPONSE = new Object[] {null, null, null};
	
	public int type();
	
	public int fetchStatus(int defaultStatus);
	
	public <K,V> Collection<Map.Entry<K, V>> fetchHeaders();
	
	public Object fetchBody();
	
	public NginxRequest request();
	
	public boolean isLast();
	
}
