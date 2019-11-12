/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.java;

import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;

import nginx.clojure.NginxRequest;
import nginx.clojure.NginxSimpleResponse;

public class NginxJavaResponse extends NginxSimpleResponse {

	protected Object[] response;
	
	public NginxJavaResponse() {
	}
	
	public NginxJavaResponse(NginxRequest req, Object[] response) {
		super(req);
		this.response = response;
		if (response == Constants.ASYNC_TAG) {
			this.type = TYPE_FAKE_ASYNC_TAG;
		} else if (response == Constants.PHASE_DONE) {
			this.type = TYPE_FAKE_PHASE_DONE;
		} else if (response == null) {
			this.response = EMPTY_RESPONSE;
		}
	}

	@Override
	public int fetchStatus(int defaultStatus) {
		int status = defaultStatus;
		Object statusObj = response[0];
		if (statusObj != null) {
			if (statusObj instanceof Number){
				status = ((Number)statusObj).intValue();
			}else {
				status = Integer.parseInt(statusObj.toString());
			}
		}
		return status;
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	@Override
	public <K, V> Collection<Entry<K,V>> fetchHeaders() {
		Map headers = (Map)response[1];
		return headers == null ? null : headers.entrySet();
	}

	@Override
	public Object fetchBody() {
		return response[2];
	}

}
