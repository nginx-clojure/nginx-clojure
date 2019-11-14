/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.java;

import java.io.IOException;
import java.util.Map;

public interface NginxJavaHeaderFilter extends DefinedPrefetch {
	
	public Object[] doFilter(int status, Map<String, Object> request, Map<String, Object> responseHeaders)  throws IOException;
	
	/* (non-Javadoc)
	 * @see nginx.clojure.java.DefinedPrefetch#headersNeedPrefetch()
	 */
	@Override
	default String[] headersNeedPrefetch() {
		return ALL_HEADERS;
	}
	
	/* (non-Javadoc)
	 * @see nginx.clojure.java.DefinedPrefetch#variablesNeedPrefetch()
	 */	
	@Override
	default String[] variablesNeedPrefetch() {
		return NO_VARS;
	}
	
	/* (non-Javadoc)
	 * @see nginx.clojure.java.DefinedPrefetch#responseHeadersNeedPrefetch()
	 */
	@Override
	default String[] responseHeadersNeedPrefetch() {
		return ALL_HEADERS;
	}

}
