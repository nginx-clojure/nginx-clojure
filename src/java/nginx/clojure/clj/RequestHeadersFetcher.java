/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.clj;

import java.nio.charset.Charset;

import nginx.clojure.RequestVarFetcher;

public class RequestHeadersFetcher implements RequestVarFetcher {

	@Override
	public Object fetch(long r, Charset encoding) {
		return new LazyHeaderMap(r, false);
	}

}
