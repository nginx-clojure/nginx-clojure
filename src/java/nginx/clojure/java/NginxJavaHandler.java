/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.java;

import static nginx.clojure.MiniConstants.BODY;
import static nginx.clojure.MiniConstants.NGX_HTTP_BODY_FILTER_PHASE;
import static nginx.clojure.MiniConstants.NGX_HTTP_HEADER_FILTER_PHASE;
import static nginx.clojure.MiniConstants.NGX_HTTP_NOT_FOUND;
import static nginx.clojure.java.Constants.ASYNC_TAG;

import java.io.Closeable;

import nginx.clojure.NginxClojureRT;
import nginx.clojure.NginxHttpServerChannel;
import nginx.clojure.NginxRequest;
import nginx.clojure.NginxResponse;
import nginx.clojure.NginxSimpleHandler;

public class NginxJavaHandler extends NginxSimpleHandler {

	protected NginxJavaRingHandler ringHandler;
	protected NginxJavaHeaderFilter headerFilter;
	
	public static Object[] NOT_FOUND_RESPONSE = new Object[] {NGX_HTTP_NOT_FOUND, null, null};
	
	public NginxJavaHandler() {
	}
	
	
	public NginxJavaHandler(NginxJavaRingHandler ringHandler) {
		super();
		this.ringHandler = ringHandler;
	}
	
	public NginxJavaHandler(NginxJavaHeaderFilter headerFilter) {
		super();
		this.headerFilter = headerFilter;
	}


	@Override
	public NginxRequest makeRequest(long r, long c) {
		if (r == 0) {
			return new NginxJavaRequest(this, ringHandler, r, new Object[0]);
		}
		int phase = (int)NginxClojureRT.ngx_http_clojure_mem_get_module_ctx_phase(r);
		NginxJavaRequest req;
		switch (phase) {
		case NGX_HTTP_HEADER_FILTER_PHASE : 
		case NGX_HTTP_BODY_FILTER_PHASE:
			req = new NginxJavaFilterRequest(this, ringHandler, r, c);
			break;
		default :
			req =  new NginxJavaRequest(this, ringHandler, r);
		}
		return req.phase(phase);
	}
	
	@Override
	public NginxResponse process(NginxRequest req) {
		NginxJavaRequest r = (NginxJavaRequest)req;
		try{
			Object resp;
			switch (req.phase()) {
			case NGX_HTTP_HEADER_FILTER_PHASE:
				NginxJavaFilterRequest freq = (NginxJavaFilterRequest)r;
				resp = headerFilter.doFilter(freq.responseStatus(), freq, freq.responseHeaders());
				break;
			case NGX_HTTP_BODY_FILTER_PHASE:
				throw new UnsupportedOperationException("body filter has not been supported yet!");
			default:
				 resp = ringHandler.invoke((NginxJavaRequest)req);
			}
			return r.isHijacked() ? toNginxResponse(r, ASYNC_TAG) : toNginxResponse(r, resp);
		}finally {
			int bodyIdx = r.index(BODY);
			if (bodyIdx > 0) {
				try {
					Object body = r.val(bodyIdx);
					if (body != null && body instanceof Closeable) {
						((Closeable)body).close();
					}
				} catch (Throwable e) {
					NginxClojureRT.log.error("can not close Closeable object such as FileInputStream!", e);
				}
			}
		}
	}

	@Override
	public  NginxResponse toNginxResponse(NginxRequest req, Object resp) {
		
		if (resp == null) {
			return new NginxJavaResponse(req, NOT_FOUND_RESPONSE);
		}
		
		if (resp instanceof NginxResponse) {
			return (NginxResponse)resp;
		}
		return new NginxJavaResponse(req, (Object[])resp);
	}
	
	@Override
	public void completeAsyncResponse(NginxRequest req, Object resp) {
		NginxClojureRT.completeAsyncResponse(req, toNginxResponse(req, resp));
	}


	@Override
	public NginxHttpServerChannel hijack(NginxRequest req, boolean ignoreFilter) {
		((NginxJavaRequest)req).hijacked = true;
		return ((NginxJavaRequest)req).channel = new NginxHttpServerChannel(req, ignoreFilter);
	}

}
