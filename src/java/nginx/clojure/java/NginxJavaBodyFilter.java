/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.java;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

public interface NginxJavaBodyFilter {
	
	/**
	 * For one request this method can be invoked multiple times and at the last time the argument 
	 * <i>isLast</i> will be true. Note that `bodyChunk` is valid only at its call scope and can
	 * not be stored for later usage. 
	 * The result returned must be an array which has three elements viz. {status, headers, filtered_chunk}.
	 * If `status` is not null `filtered_chunk` will be used as the final chunk. `status` and `headers` will
	 * be ignored when the response headers has been sent already.
	 * `filtered_chunk` can be either of 
	 * <ul>
	 * <li>File, viz. java.io.File</li>
	 * <li>String</li>
	 * <li>InputStream</li>
	 * <li>Array/Iterable, e.g. Array/List/Set of above types</li>
	 * </ul>
	 */
	public Object[] doFilter(Map<String, Object> request, InputStream bodyChunk, boolean isLast)  throws IOException;

}
