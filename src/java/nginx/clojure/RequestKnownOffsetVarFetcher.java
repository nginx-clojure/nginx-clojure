/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure;

import java.nio.charset.Charset;

import static nginx.clojure.NginxClojureRT.*;

public  class RequestKnownOffsetVarFetcher implements RequestVarFetcher {

	private long offset;
	
	public RequestKnownOffsetVarFetcher(long offset) {
		this.offset = offset;
	}
	
	@Override
	public Object fetch(long r, Charset encoding) {
		return fetchNGXString(r + offset, encoding);
	}
	
}