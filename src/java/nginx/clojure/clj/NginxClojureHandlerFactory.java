/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.clj;

import static nginx.clojure.MiniConstants.NGX_HTTP_BODY_FILTER_PHASE;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_HEADERSO_CACHE_CONTROL_OFFSET;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_HEADERSO_HEADERS_OFFSET;
import static nginx.clojure.MiniConstants.NGX_HTTP_HEADER_FILTER_PHASE;
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
		KNOWN_RESP_HEADERS.put("Cache-Control", new SeqHeaderHolder("Cache-Control",
				NGX_HTTP_CLOJURE_HEADERSO_CACHE_CONTROL_OFFSET, NGX_HTTP_CLOJURE_HEADERSO_HEADERS_OFFSET));
	}
	
	@Override
	public NginxHandler newInstance(int phase, String name, String code) {
		if (name != null) {
			int d = name.lastIndexOf('/');
			if (d > 0) {
				code = "(do (require '";
				code += name.substring(0, d);
				code += ")";
				code += name;
				code += ")";
			}else {
				code = name;
			}
			
		}
		IFn f = (IFn)RT.var("clojure.core", "eval").invoke(RT.var("clojure.core","read-string").invoke(code));
		switch (phase) {
		case NGX_HTTP_HEADER_FILTER_PHASE:
			return new NginxClojureHandler(null, f);
		case NGX_HTTP_BODY_FILTER_PHASE:
			return new NginxClojureHandler(f);
		default:
			return new NginxClojureHandler(f, null);
		}
	}

}
