/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.java;

import java.util.Map;

public interface NginxJavaRingHandler {

	public Object[] invoke(Map<String, Object> request);
	
}
