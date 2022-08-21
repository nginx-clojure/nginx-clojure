/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.java;

import static nginx.clojure.MiniConstants.BODY;
import static nginx.clojure.MiniConstants.NGX_HTTP_BODY_FILTER_PHASE;
import static nginx.clojure.MiniConstants.NGX_HTTP_HEADER_FILTER_PHASE;
import static nginx.clojure.MiniConstants.NGX_HTTP_NOT_FOUND;
import static nginx.clojure.NginxClojureRT.log;
import static nginx.clojure.java.Constants.ASYNC_TAG;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import nginx.clojure.Configurable;
import nginx.clojure.MiniConstants;
import nginx.clojure.NginxChainWrappedInputStream;
import nginx.clojure.NginxClojureRT;
import nginx.clojure.NginxHttpServerChannel;
import nginx.clojure.NginxRequest;
import nginx.clojure.NginxResponse;
import nginx.clojure.NginxSimpleHandler;

public class NginxJavaHandler extends NginxSimpleHandler {

	protected NginxJavaRingHandler ringHandler;
	protected NginxJavaHeaderFilter headerFilter;
	protected NginxJavaBodyFilter bodyFilter;
	
	protected static ConcurrentLinkedQueue<NginxJavaRequest> pooledRequests = new ConcurrentLinkedQueue<NginxJavaRequest>();
	
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
	
	public NginxJavaHandler(NginxJavaBodyFilter bodyFilter) {
		super();
		this.bodyFilter = bodyFilter;
	}

	@Override
	protected long defaultChainFlag(NginxResponse response) {
		if (response instanceof NginxJavaBodyFilterChunkResponse) {
			return response.isLast() ? 
					MiniConstants.NGX_CHAIN_FILTER_CHUNK_HAS_LAST : MiniConstants.NGX_CHAIN_FILTER_CHUNK_NO_LAST;
		}
		return super.defaultChainFlag(response);
	}

	@Override
	public NginxRequest makeRequest(long r, long c) {
		if (r == 0) {
			return new NginxJavaRequest(-1, this, r, new Object[0]) {
				@Override
				public long nativeCount() {
					return 0;
				}
			};
		}
		int phase = (int)NginxClojureRT.ngx_http_clojure_mem_get_module_ctx_phase(r);
		NginxJavaRequest req;
		switch (phase) {
		case NGX_HTTP_HEADER_FILTER_PHASE : 
			req = new NginxJavaFilterRequest(phase, this, r, c);
			break;
		case NGX_HTTP_BODY_FILTER_PHASE:
			req = NginxJavaFilterRequest.cloneExisted(r, c);
			if (req == null) {
				req = new NginxJavaFilterRequest(phase, this, r, c);
			}
			break;
		default :
			req = pooledRequests.poll();
			if (req == null) {
				req =  new NginxJavaRequest(phase, this, r);
			}else {
				req.reset(r, this);
			}
		}
		return req.phase(phase);
	}
	
	@Override
	public NginxResponse process(NginxRequest req) throws IOException {
		NginxJavaRequest r = (NginxJavaRequest)req;
		long nr = r.nativeRequest();
		try{
			Object resp;
			switch (req.phase()) {
			case NGX_HTTP_HEADER_FILTER_PHASE:
				NginxJavaFilterRequest freq = (NginxJavaFilterRequest)r;
				resp = headerFilter.doFilter(freq.responseStatus(), freq, freq.responseHeaders());
				break;
			case NGX_HTTP_BODY_FILTER_PHASE:
				NginxJavaFilterRequest breq = (NginxJavaFilterRequest)r;
				NginxChainWrappedInputStream chunk = breq.body;
				try {
					Object[] chunkedResp = bodyFilter.doFilter(breq, chunk, chunk.isLast());
					if (!breq.isHijacked()) {
						return new NginxJavaBodyFilterChunkResponse(breq, chunkedResp);
					}else {
						return toNginxResponse(r, ASYNC_TAG);
					}
				}finally{
					chunk.close();
				}
				
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
					NginxClojureRT.log.error("%s:%s can not close Closeable object such as FileInputStream!",nr, r.nativeRequest() , e);
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

		if (req.isHijacked()) {
			NginxHttpServerChannel channel = req.channel();
			channel.setIgnoreFilter(ignoreFilter);
			return channel;
		}

		if (log.isDebugEnabled()) {
			log.debug("#%s: hijack at %s", req.nativeRequest(), req.uri());
		}

		((NginxJavaRequest) req).hijacked = true;
		// content phase we need increase r->count to make request not to be
		// released in current event cycle.
		if (Thread.currentThread() == NginxClojureRT.NGINX_MAIN_THREAD && (req.phase() == -1
				|| req.phase() == NGX_HTTP_HEADER_FILTER_PHASE || req.phase() == NGX_HTTP_BODY_FILTER_PHASE)) {
			NginxClojureRT.ngx_http_clojure_mem_inc_req_count(req.nativeRequest(), 1);
		}

		if (log.isDebugEnabled()) {
			log.debug("#%s: hijacked at %s, lns:%s", req.nativeRequest(), req.uri(),
					req.listeners() == null ? 0 : req.listeners().size());
		}

		return ((NginxJavaRequest) req).channel = new NginxHttpServerChannel(req, ignoreFilter);
	}


	@Override
	public void config(Map<String, String> properties) {
		super.config(properties);
		if (ringHandler != null) {
			if (ringHandler instanceof Configurable) {
				Configurable cr = (Configurable) ringHandler;
				cr.config(properties);
			} else {
				NginxClojureRT.log.warn("%s is not an instance of nginx.clojure.Configurable, so properties will be ignored!",
						ringHandler.getClass());
			}
		} else if (headerFilter != null) {
			if (headerFilter instanceof Configurable) {
				Configurable cr = (Configurable) headerFilter;
				cr.config(properties);
			} else {
				NginxClojureRT.log.warn("%s is not an instance of nginx.clojure.Configurable, so properties will be ignored!",
						headerFilter.getClass());
			}
		} else {
			if (bodyFilter instanceof Configurable) {
				Configurable cr = (Configurable) bodyFilter;
				cr.config(properties);
			} else {
				NginxClojureRT.log.warn("%s is not an instance of nginx.clojure.Configurable, so properties will be ignored!",
						bodyFilter.getClass());
			}
		}
		
	}

	protected void returnToRequestPool(NginxJavaRequest r) {
		NginxClojureRT.log.debug("returnToRequestPool %s, c %s, phase %s", r.r, r.nativeCount, r.phase);;
		pooledRequests.add(r);
	}
	
	/* (non-Javadoc)
	 * @see nginx.clojure.NginxSimpleHandler#headersNeedPrefetch()
	 */
	@Override
	public String[] headersNeedPrefetch() {
		if (ringHandler != null) {
			return ringHandler.headersNeedPrefetch();
		} else if (headerFilter != null) {
			return headerFilter.headersNeedPrefetch();
		} else {
			return bodyFilter.headersNeedPrefetch();
		}
	}

	/* (non-Javadoc)
	 * @see nginx.clojure.NginxSimpleHandler#variablesNeedPrefetch()
	 */
	@Override
	public String[] variablesNeedPrefetch() {
		if (ringHandler != null) {
			return ringHandler.variablesNeedPrefetch();
		} else if (headerFilter != null) {
			return headerFilter.variablesNeedPrefetch();
		} else {
			return bodyFilter.variablesNeedPrefetch();
		}
	}
	
	/* (non-Javadoc)
	 * @see nginx.clojure.NginxSimpleHandler#responseHeadersNeedPrefetch()
	 */
	@Override
	public String[] responseHeadersNeedPrefetch() {
		if (ringHandler != null) {
			return ringHandler.responseHeadersNeedPrefetch();
		} else if (headerFilter != null) {
			return headerFilter.responseHeadersNeedPrefetch();
		} else {
			return bodyFilter.responseHeadersNeedPrefetch();
		}
	}
}
