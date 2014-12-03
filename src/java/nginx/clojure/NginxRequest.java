package nginx.clojure;

public interface NginxRequest {
	
	public long nativeRequest();
	
	public boolean isReleased();
	
	 //for safe access with another thread
	public void	prefetchAll();
	
	public NginxHandler handler();
	
	public NginxHttpServerChannel channel();
	
	public boolean isHijacked();
	
	public int phase();
	
}
