/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.clj;

import static nginx.clojure.MiniConstants.NR_ASYNC_TAG;
import static nginx.clojure.MiniConstants.NR_PHRASE_DONE;
import static nginx.clojure.clj.Constants.ASYNC_TAG;
import static nginx.clojure.clj.Constants.PHRASE_DONE;

import java.util.Map;

import nginx.clojure.NginxClojureRT;
import nginx.clojure.NginxRequest;
import nginx.clojure.NginxResponse;
import nginx.clojure.NginxSimpleHandler;
import clojure.lang.IFn;
import clojure.lang.Keyword;

public class NginxClojureHandler extends NginxSimpleHandler {

	private IFn ringHandler;
	
	public NginxClojureHandler() {
	}
	
	public NginxClojureHandler(IFn ringHandler) {
		this.ringHandler = ringHandler;
	}
	
	public static  String normalizeHeaderName(Object nameObj) {
		String name;
		if (nameObj instanceof String) {
			name = (String)nameObj;
		}else if (nameObj instanceof Keyword) {
			name = ((Keyword)nameObj).getName();
		}else {
			name = nameObj.toString();
		}
		return name == null ? null : name.toLowerCase();
	}

	@Override
	public NginxRequest makeRequest(long r) {
		if (r == 0) {
			return new LazyRequestMap(ringHandler, r, new Object[0]); 
		}
		return new LazyRequestMap(ringHandler, r);
	}
	
	public static NginxResponse toNginxResponse(Map resp) {
		if (resp == ASYNC_TAG) {
			return NR_ASYNC_TAG;
		}
		if (resp == PHRASE_DONE) {
			return NR_PHRASE_DONE;
		}
		if (resp == null) {
			return null;
		}
		return new NginxClojureResponse(resp);
	}
	
	public static void completeAsyncResponse(long r, Map resp) {
		NginxClojureRT.completeAsyncResponse(r, toNginxResponse(resp));
	}

}
