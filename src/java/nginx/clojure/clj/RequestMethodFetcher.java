/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.clj;

import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_REQ_METHOD_OFFSET;
import static nginx.clojure.NginxClojureRT.fetchNGXInt;
import static nginx.clojure.clj.Constants.HTTP_METHODS;

import java.nio.charset.Charset;

import nginx.clojure.MiniConstants;
import nginx.clojure.RequestKnownNameVarFetcher;
import nginx.clojure.RequestVarFetcher;

public class RequestMethodFetcher implements RequestVarFetcher {

	public final static RequestKnownNameVarFetcher REQUEST_METHOD_VAR_FETCHER = new RequestKnownNameVarFetcher("request_method");
	
	@Override
	public Object fetch(long r, Charset encoding) {
		int methodIdx = 0;
		int methodCode = fetchNGXInt(r + NGX_HTTP_CLOJURE_REQ_METHOD_OFFSET);
		while (methodCode > 1) {
			methodCode = methodCode >> 1;
			methodIdx ++;
		}
		if (methodIdx >=  HTTP_METHODS.length || methodIdx == 0){
			String m = (String) REQUEST_METHOD_VAR_FETCHER.fetch(r, MiniConstants.DEFAULT_ENCODING);
			return m == null ? HTTP_METHODS[0] : m;
		}else {
			return HTTP_METHODS[methodIdx];
		}
	}

}
