/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure;

public interface NginxHeaderHolder {
	
	public String name();

	public long knownOffset();
	
	public long headersOffset();
	
	public Object fetch(long h);
	
	public void push(long h, long pool, Object v);
	
	public void clear(long h);
	
	public boolean exists(long h);
	
}
