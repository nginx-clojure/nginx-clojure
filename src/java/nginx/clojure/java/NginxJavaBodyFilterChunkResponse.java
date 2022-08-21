/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.java;

import nginx.clojure.NginxFilterRequest;

public class NginxJavaBodyFilterChunkResponse extends NginxJavaResponse {
	
	public NginxJavaBodyFilterChunkResponse() {
	}

	public NginxJavaBodyFilterChunkResponse(NginxFilterRequest req, Object[] response) {
		super(req, response);
		type = TYPE_FAKE_BODY_FILTER_TAG;
	}
	
	@Override
	public boolean isLast() {
		return response != null && response[0] != null;
	}
}
