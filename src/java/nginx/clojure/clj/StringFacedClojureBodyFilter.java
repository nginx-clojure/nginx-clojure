/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.clj;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Map;

import clojure.lang.IFn;
import nginx.clojure.java.StringFacedJavaBodyFilter;

/**
 * @author who
 *
 */
public class StringFacedClojureBodyFilter {
	
	public static final String CHAR_DECODER_BUF_REM_IN_REQUEST = "$char-decoder-buf-rem-in-request!";

	protected IFn bodyFilter;
	
	public StringFacedClojureBodyFilter() {
	}
	
	public StringFacedClojureBodyFilter(IFn bodyFilter) {
		this.bodyFilter = bodyFilter;
	}

	@SuppressWarnings("rawtypes")
	public Map invoke(LazyFilterRequestMap request, InputStream bodyChunk, boolean isLast) throws IOException {
		ByteBuffer rem = (ByteBuffer) request.valAt(CHAR_DECODER_BUF_REM_IN_REQUEST);
		if (rem == null) {
			request.upsert(CHAR_DECODER_BUF_REM_IN_REQUEST, rem = ByteBuffer.allocate(3));
			rem.flip();
		}
		StringBuilder sb = StringFacedJavaBodyFilter.decodeToString(rem, bodyChunk);
		return (Map) bodyFilter.invoke(request, sb.toString(), isLast);
	}

}
