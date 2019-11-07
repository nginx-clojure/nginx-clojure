/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.clj;

import java.util.Map;

import nginx.clojure.NginxRequest;

public class NginxClojureBodyFilterChunkResponse extends NginxClojureResponse {

	public NginxClojureBodyFilterChunkResponse() {
	}

	public NginxClojureBodyFilterChunkResponse(NginxRequest req, @SuppressWarnings("rawtypes") Map response) {
		super(req, response);
		type = TYPE_FAKE_BODY_FILTER_TAG;
	}

	@Override
	public boolean isLast() {
		return response.get(Constants.STATUS) != null;
	}
}
