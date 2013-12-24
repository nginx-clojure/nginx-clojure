package nginx.clojure;

public interface ResponseHeaderPusher {
	
	public String name();

	public long knownOffset();
	
	public void push(long h, long pool, Object v);
	
}
