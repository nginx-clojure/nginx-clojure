/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.java;

import static nginx.clojure.MiniConstants.NR_ASYNC_TAG;
import static nginx.clojure.MiniConstants.NR_PHRASE_DONE;
import static nginx.clojure.java.Constants.ASYNC_TAG;
import static nginx.clojure.java.Constants.PHRASE_DONE;
import nginx.clojure.NginxClojureRT;
import nginx.clojure.NginxRequest;
import nginx.clojure.NginxResponse;
import nginx.clojure.NginxSimpleHandler;

public class NginxJavaHandler extends NginxSimpleHandler {

	protected NginxJavaRingHandler ringHandler;
	
	public NginxJavaHandler() {
	}
	
	
	public NginxJavaHandler(NginxJavaRingHandler ringHandler) {
		super();
		this.ringHandler = ringHandler;
	}


	@Override
	public NginxRequest makeRequest(long r) {
		if (r == 0) {
			return new NginxJavaRequest(ringHandler, r, new Object[0]);
		}
		return new NginxJavaRequest(ringHandler, r);
	}

	public static NginxResponse toNginxResponse(Object[] resp) {
		if (resp == ASYNC_TAG) {
			return NR_ASYNC_TAG;
		}
		if (resp == PHRASE_DONE) {
			return NR_PHRASE_DONE;
		}
		return new NginxJavaResponse(resp);
	}
	
	public static void completeAsyncResponse(long r, Object[] resp) {
		NginxClojureRT.completeAsyncResponse(r, toNginxResponse(resp));
	}
}
