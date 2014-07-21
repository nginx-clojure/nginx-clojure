/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.clj;

import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_CHAINT_SIZE;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_CHAIN_NEXT_OFFSET;
import static nginx.clojure.MiniConstants.NGX_HTTP_NO_CONTENT;
import static nginx.clojure.NginxClojureRT.UNSAFE;
import static nginx.clojure.NginxClojureRT.ngx_palloc;
import static nginx.clojure.clj.Constants.BODY;
import static nginx.clojure.clj.Constants.HEADERS;
import static nginx.clojure.clj.Constants.STATUS;

import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;

import nginx.clojure.NginxSimpleResponse;
import nginx.clojure.ResponseHeaderPusher;
import nginx.clojure.ResponseUnknownHeaderPusher;
import clojure.lang.ISeq;
import clojure.lang.RT;
import clojure.lang.Seqable;

public class NginxClojureResponse extends NginxSimpleResponse {

	protected Map response;
	
	public NginxClojureResponse() {
	}
	
	public NginxClojureResponse(Map response) {
		super();
		this.response = response;
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



	@Override
	public Collection<Entry> fetchHeaders() {
		Map headers = ((Map)response.get(HEADERS));
		return headers == null ? null : headers.entrySet();
	}

	@Override
	public Object fetchBody() {
		return response.get(BODY);
	}
	
	protected  String normalizeHeaderName(Object nameObj) {
		return NginxClojureHandler.normalizeHeaderName(nameObj);
	}
	
	@Override
	protected long buildResponseComplexItemBuf(long r, long pool, Object item,
			int isLast, long chain) {
		if ((item instanceof ISeq) || (item instanceof Seqable) || (item instanceof Iterable)) {
			ISeq seq = RT.seq(item);
			long lastChain = 0;
			while (seq != null) {
				Object o = seq.first();
				if (o != null) {
					
					if (lastChain != 0) {
						chain = ngx_palloc(pool, NGX_HTTP_CLOJURE_CHAINT_SIZE);
						if (chain == 0) {
							return 0;
						}
					}
					
					seq = seq.next();
					long subTail = 0;
					if (isLast == 1 && seq == null) {
						subTail = buildResponseItemBuf(r, pool, o, 1, chain);
					}else {
						subTail = buildResponseItemBuf(r, pool, o, 0, chain);
					}
					if (subTail <= 0 && subTail != -NGX_HTTP_NO_CONTENT) {
						return subTail;
					}
					if (lastChain != 0 && subTail != -NGX_HTTP_NO_CONTENT) {
						UNSAFE.putAddress(lastChain + NGX_HTTP_CLOJURE_CHAIN_NEXT_OFFSET, chain);
					}
					if (subTail != -NGX_HTTP_NO_CONTENT) {
						lastChain = subTail;
					}
				}
			}
			return lastChain;
		}
		return super.buildResponseComplexItemBuf(r, pool, item, isLast, chain);
	}
	
	@Override
	protected ResponseHeaderPusher fetchResponseHeaderPusher(String name) {
		ResponseHeaderPusher pusher = Constants.KNOWN_RESP_HEADERS.get(name);
		if (pusher == null) {
			pusher = new ResponseUnknownHeaderPusher(name);
		}
		return pusher;
	}

}
