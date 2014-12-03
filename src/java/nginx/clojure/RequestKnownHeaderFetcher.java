/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure;

import static nginx.clojure.MiniConstants.KNOWN_REQ_HEADERS;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_REQ_HEADERS_IN_OFFSET;

import java.nio.charset.Charset;

public class RequestKnownHeaderFetcher implements RequestVarFetcher {
	
	private NginxHeaderHolder headerHolder;
	
	public RequestKnownHeaderFetcher(String name) {
		headerHolder = KNOWN_REQ_HEADERS.get(name);
	}
	

	@Override
	public Object fetch(long r, Charset encoding) {
		return headerHolder.fetch(r + NGX_HTTP_CLOJURE_REQ_HEADERS_IN_OFFSET);
	}

}
