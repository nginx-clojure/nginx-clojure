package nginx.clojure;

public interface NginxRequest {
	
	public long nativeRequest();
	
	public NginxResponse process();
	
	 //for safe access with another thread
	public void	prefetchAll();
	
}
