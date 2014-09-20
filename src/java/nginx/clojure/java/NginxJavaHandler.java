/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.java;

import static nginx.clojure.MiniConstants.BODY;
import static nginx.clojure.java.Constants.ASYNC_TAG;

import java.io.Closeable;

import nginx.clojure.NginxClojureRT;
import nginx.clojure.NginxHttpServerChannel;
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
			return new NginxJavaRequest(this, ringHandler, r, new Object[0]);
		}
		return new NginxJavaRequest(this, ringHandler, r);
	}
	
	@Override
	public NginxResponse process(NginxRequest req) {
		NginxJavaRequest r = (NginxJavaRequest)req;
		try{
			Object resp = ringHandler.invoke(r);
			return req.isHijacked() ? toNginxResponse(req, ASYNC_TAG) : toNginxResponse(req, resp);
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
			return null;
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
