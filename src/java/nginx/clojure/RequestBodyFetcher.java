/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure;

import java.nio.charset.Charset;

public class RequestBodyFetcher extends RequestKnownNameVarFetcher {
	
	public RequestBodyFetcher() {
		super("request_body");
	}
	
	@Override
	public Object fetch(long r, Charset encoding) {
		return super.fetch(r, encoding);
	}
}
