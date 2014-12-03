/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.clj;

import static nginx.clojure.MiniConstants.NGX_HTTP_NOT_FOUND;
import static nginx.clojure.MiniConstants.NGX_HTTP_NO_CONTENT;
import static nginx.clojure.NginxClojureRT.log;
import static nginx.clojure.NginxClojureRT.ngx_http_clojure_mem_get_module_ctx_phase;
import static nginx.clojure.NginxClojureRT.processId;
import static nginx.clojure.clj.Constants.ASYNC_TAG;
import static nginx.clojure.clj.Constants.BODY;
import static nginx.clojure.clj.Constants.KNOWN_RESP_HEADERS;
import static nginx.clojure.clj.Constants.STATUS;
import static nginx.clojure.clj.Constants.URI;

import java.io.Closeable;
import java.util.Map;

import nginx.clojure.NginxClojureRT;
import nginx.clojure.NginxHeaderHolder;
import nginx.clojure.NginxHttpServerChannel;
import nginx.clojure.NginxRequest;
import nginx.clojure.NginxResponse;
import nginx.clojure.NginxSimpleHandler;
import nginx.clojure.java.ArrayMap;
import clojure.lang.IFn;
import clojure.lang.ISeq;
import clojure.lang.Keyword;
import clojure.lang.RT;
import clojure.lang.Seqable;

public class NginxClojureHandler extends NginxSimpleHandler {

	public static ArrayMap<Keyword, Object> NOT_FOUND_RESPONSE = ArrayMap.create(STATUS, NGX_HTTP_NOT_FOUND);
	
	private IFn ringHandler;
	
	public NginxClojureHandler() {
	}
	
	public NginxClojureHandler(IFn ringHandler) {
		this.ringHandler = ringHandler;
	}
	
	public static  String normalizeHeaderNameHelper(Object nameObj) {
		String name;
		if (nameObj instanceof String) {
			name = (String)nameObj;
		}else if (nameObj instanceof Keyword) {
			name = ((Keyword)nameObj).getName();
		}else {
			name = nameObj.toString();
		}
		return name;
	}
	
	@Override
	protected  String normalizeHeaderName(Object nameObj) {
		return normalizeHeaderNameHelper(nameObj);
	}

	@Override
	public NginxRequest makeRequest(long r,  long c) {
		if (r == 0) {
			return new LazyRequestMap(this, r, null, new Object[0]); 
		}
		return new LazyRequestMap(this, r).phase((int)ngx_http_clojure_mem_get_module_ctx_phase(r));
	}
	
	@Override
	public NginxResponse process(NginxRequest req) {
		LazyRequestMap r = (LazyRequestMap)req;
		try{
			Map resp = (Map) ringHandler.invoke(r);
			return req.isHijacked() ? toNginxResponse(r, ASYNC_TAG) : toNginxResponse(r, resp);
		}finally {
			int bodyIdx = r.index(BODY);
			if (bodyIdx > 0 && r.array[bodyIdx] instanceof Closeable) {
				try {
					((Closeable)r.array[bodyIdx]).close();
				} catch (Throwable e) {
					log.error("can not close Closeable object such as FileInputStream!", e);
				}
			}
		}
	}
	
	public  NginxResponse toNginxResponse(NginxRequest req, Object resp) {
		if (resp == null) {
			return new NginxClojureResponse(req, NOT_FOUND_RESPONSE );
		}
		if (resp instanceof NginxResponse) {
			return (NginxResponse) resp;
		}
		return new NginxClojureResponse(req, (Map)resp);
	}
	
	@Override
	public void completeAsyncResponse(NginxRequest req, Object resp) {
		NginxClojureRT.completeAsyncResponse(req, toNginxResponse(req, resp));
	}

	@Override
	public NginxHeaderHolder fetchResponseHeaderPusher(String name) {
		NginxHeaderHolder pusher = KNOWN_RESP_HEADERS.get(name);
		if (pusher == null) {
			pusher = new ResponseUnknownHeaderPusher(name);
		}
		return pusher;
	}
	
	@Override
	protected long buildResponseComplexItemBuf(long r, Object item, long preChain) {
		if ((item instanceof ISeq) || (item instanceof Seqable) || (item instanceof Iterable)) {
			ISeq seq = RT.seq(item);
			long chain = preChain;
			long first = 0;
			while (seq != null) {
				Object o = seq.first();
				if (o != null) {
					long rc  = buildResponseItemBuf(r, o, chain);
					if (rc <= 0) {
						if (rc != -NGX_HTTP_NO_CONTENT) {
							return rc;
						}
					}else {
						chain = rc;
						if (first == 0) {
							first = chain;
						}
					}
					seq = seq.next();
				}
			}
			return preChain == 0 ? (first == 0 ? -NGX_HTTP_NO_CONTENT : first)  : chain;
		}
		return super.buildResponseComplexItemBuf(r, item, preChain);
	}

	@Override
	public NginxHttpServerChannel hijack(NginxRequest req, boolean ignoreFilter) {
		if (log.isDebugEnabled()) {
			log.debug("#%s: hijack at %s", processId, ((LazyRequestMap)req).valAt(URI));
		}
		((LazyRequestMap)req).hijackTag[0] = 1;
		return ((LazyRequestMap)req).channel = new NginxHttpServerChannel(req, ignoreFilter);
	}

}
