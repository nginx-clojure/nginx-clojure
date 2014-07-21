/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.clj;

import static nginx.clojure.clj.Constants.HEADER_FETCHER;
import static nginx.clojure.clj.Constants.KNOWN_RESP_HEADERS;
import static nginx.clojure.clj.Constants.REQUEST_METHOD_FETCHER;
import nginx.clojure.MiniConstants;
import nginx.clojure.NginxHandler;
import nginx.clojure.NginxHandlerFactory;
import clojure.lang.IFn;
import clojure.lang.RT;

public class NginxClojureHandlerFactory extends NginxHandlerFactory {

	public NginxClojureHandlerFactory() {
		HEADER_FETCHER = new RequestHeadersFetcher();
		REQUEST_METHOD_FETCHER = new RequestMethodFetcher();
		KNOWN_RESP_HEADERS.putAll(MiniConstants.KNOWN_RESP_HEADERS);
		KNOWN_RESP_HEADERS.put("cache-control", new ResponseSeqHeaderPusher("cache-control", MiniConstants.NGX_HTTP_CLOJURE_HEADERSO_CACHE_CONTROL_OFFSET));
	}
	
	@Override
	public NginxHandler newInstance(String name, String code) {
		IFn f = (IFn)RT.var("clojure.core", "eval").invoke(RT.var("clojure.core","read-string").invoke(code));
		return new NginxClojureHandler(f);
	}

}
