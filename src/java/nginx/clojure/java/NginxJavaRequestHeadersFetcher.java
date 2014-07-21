/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.java;

import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_REQ_HEADERS_IN_OFFSET;

import java.nio.charset.Charset;

import nginx.clojure.RequestVarFetcher;

public class NginxJavaRequestHeadersFetcher implements RequestVarFetcher {

	@Override
	public Object fetch(long r, Charset encoding) {
		return new JavaLazyHeaderMap(r + NGX_HTTP_CLOJURE_REQ_HEADERS_IN_OFFSET);
	}

}