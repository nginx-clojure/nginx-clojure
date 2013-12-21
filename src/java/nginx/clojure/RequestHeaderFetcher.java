/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure;

import java.nio.charset.Charset;

import static nginx.clojure.Constants.*;

public class RequestHeaderFetcher implements RequestVarFetcher {

	@Override
	public Object fetch(long r, Charset encoding) {
		return new LazyHeaderMap(r + NGX_HTTP_CLOJURE_REQ_HEADERS_IN_OFFSET);
	}

}
