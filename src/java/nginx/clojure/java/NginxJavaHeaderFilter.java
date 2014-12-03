/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.java;

import java.util.Map;

public interface NginxJavaHeaderFilter {
	public Object[] doFilter(int status, Map<String, Object> request, Map<String, Object> responseHeaders);
}
