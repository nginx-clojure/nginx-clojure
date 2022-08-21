/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.clj;

import static nginx.clojure.clj.Constants.BODY;
import static nginx.clojure.clj.Constants.HEADERS;
import static nginx.clojure.clj.Constants.STATUS;

import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;

import nginx.clojure.NginxRequest;
import nginx.clojure.NginxSimpleResponse;
import nginx.clojure.clj.Constants;

@SuppressWarnings("rawtypes")
public class NginxClojureResponse extends NginxSimpleResponse {

	protected Map response;
	
	public NginxClojureResponse() {
	}
	
	public NginxClojureResponse(NginxRequest req, Map response) {
		super(req);
		this.response = response;
		if (response == Constants.ASYNC_TAG) {
			this.type = TYPE_FAKE_ASYNC_TAG;
		}else if (response == Constants.PHRASE_DONE) {
			this.type = TYPE_FAKE_PHASE_DONE;
		}
	}


	public Map getResponse() {
		return response;
	}
	
	public void setResponse(Map response) {
		this.response = response;
	}

	@Override
	public int fetchStatus(int defaultStatus) {
		int status = defaultStatus;
		Object statusObj = response.get(STATUS);
		if (statusObj != null) {
			if (statusObj instanceof Number){
				status = ((Number)statusObj).intValue();
			}else {
				status = Integer.parseInt(statusObj.toString());
			}
		}
		return status;
	}



	@SuppressWarnings("unchecked")
	@Override
	public <K, V> Collection<Entry<K, V>> fetchHeaders() {
		Map headers = ((Map)response.get(HEADERS));
		return headers == null ? null : headers.entrySet();
	}

	@Override
	public Object fetchBody() {
		return response.get(BODY);
	}
}
