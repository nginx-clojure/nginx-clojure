/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.java;

import java.nio.charset.Charset;

import nginx.clojure.RequestVarFetcher;

public class NginxJavaRequestHeadersFetcher implements RequestVarFetcher {

	@Override
	public Object fetch(long r, Charset encoding) {
		return new JavaLazyHeaderMap(r, false);
	}

}