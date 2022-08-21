/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.clj;

import static nginx.clojure.MiniConstants.NGX_HTTP_BODY_FILTER_PHASE;
import static nginx.clojure.MiniConstants.NGX_HTTP_HEADER_FILTER_PHASE;
import static nginx.clojure.MiniConstants.NGX_HTTP_NOT_FOUND;
import static nginx.clojure.MiniConstants.NGX_HTTP_NO_CONTENT;
import static nginx.clojure.NginxClojureRT.log;
import static nginx.clojure.clj.Constants.ASYNC_TAG;
import static nginx.clojure.clj.Constants.BODY;
import static nginx.clojure.clj.Constants.KNOWN_RESP_HEADERS;
import static nginx.clojure.clj.Constants.STATUS;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import clojure.lang.IFn;
import clojure.lang.IMeta;
import clojure.lang.IPersistentMap;
import clojure.lang.ISeq;
import clojure.lang.Keyword;
import clojure.lang.RT;
import clojure.lang.Seqable;
import nginx.clojure.MiniConstants;
import nginx.clojure.NginxChainWrappedInputStream;
import nginx.clojure.NginxClojureRT;
import nginx.clojure.NginxHeaderHolder;
import nginx.clojure.NginxHttpServerChannel;
import nginx.clojure.NginxRequest;
import nginx.clojure.NginxResponse;
import nginx.clojure.NginxSimpleHandler;
import nginx.clojure.java.ArrayMap;
import nginx.clojure.java.DefinedPrefetch;

public class NginxClojureHandler extends NginxSimpleHandler {

	public static ArrayMap<Keyword, Object> NOT_FOUND_RESPONSE = ArrayMap.create(STATUS, NGX_HTTP_NOT_FOUND);
	
	protected static ConcurrentLinkedQueue<LazyRequestMap> pooledRequests = new ConcurrentLinkedQueue<LazyRequestMap>();
	
	protected IFn ringHandler;
	protected IFn headerFilter;
	protected StringFacedClojureBodyFilter bodyFilter;
	
	public NginxClojureHandler() {
	}
	
	public NginxClojureHandler(IFn ringHandler, IFn headerFilter) {
		this.ringHandler = ringHandler;
		this.headerFilter = headerFilter;
	}
	
	public NginxClojureHandler(IFn bodyFilter) {
		this.bodyFilter = new StringFacedClojureBodyFilter(bodyFilter);
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
			return new LazyRequestMap(this, r, null, new Object[0]) {
				 @Override
				public long nativeCount() {
					return 0;
				}
			}; 
		}
		int phase = (int)NginxClojureRT.ngx_http_clojure_mem_get_module_ctx_phase(r);
		LazyRequestMap req;
		switch (phase) {
		case NGX_HTTP_HEADER_FILTER_PHASE : 
			req = new LazyFilterRequestMap(this, r, c);
			break;
		case NGX_HTTP_BODY_FILTER_PHASE:
			req = LazyFilterRequestMap.cloneExisted(r, c);
			if (req == null) {
				req = new LazyFilterRequestMap(this, r, c);
			}
			break;
		default :
			req = pooledRequests.poll();
			if (req == null) {
				req =  new LazyRequestMap(this, r);
			}else {
				req.reset(r, this);
			}
		}
		return req.phase(phase);
	}
	
	@Override
	protected long defaultChainFlag(NginxResponse response) {
		if (response instanceof NginxClojureBodyFilterChunkResponse) {
			return response.isLast() ? 
					MiniConstants.NGX_CHAIN_FILTER_CHUNK_HAS_LAST : MiniConstants.NGX_CHAIN_FILTER_CHUNK_NO_LAST;
		}
		return super.defaultChainFlag(response);
	}
	
	@SuppressWarnings("rawtypes")
	@Override
	public NginxResponse process(NginxRequest req) throws IOException {
		LazyRequestMap r = (LazyRequestMap)req;
		try{
			Map resp;
			switch (req.phase()) {
			case NGX_HTTP_HEADER_FILTER_PHASE:
				LazyFilterRequestMap freq = (LazyFilterRequestMap)r;
				resp = (Map) headerFilter.invoke(freq.responseStatus(), freq, freq.responseHeaders());
				break;
			case NGX_HTTP_BODY_FILTER_PHASE:
				LazyFilterRequestMap breq = (LazyFilterRequestMap) r;
				NginxChainWrappedInputStream chunk = breq.body;
				try{
					Map chunkedResp = bodyFilter.invoke(breq, chunk, chunk.isLast());
					if (!breq.isHijacked()) {
						return new NginxClojureBodyFilterChunkResponse(breq, chunkedResp);
					} else {
						return toNginxResponse(r, ASYNC_TAG);
					}
				}finally{
					chunk.close();
				}
			default:
				 resp = (Map) ringHandler.invoke(req);
			}
			return req.isHijacked() ? toNginxResponse(r, ASYNC_TAG) : toNginxResponse(r, resp);
		}finally {
			int bodyIdx = r.index(BODY);
			if (bodyIdx > 0) {
				try {
					Object body = r.element(bodyIdx);
					if (body != null && body instanceof Closeable) {
						((Closeable)body).close();
					}
				} catch (Throwable e) {
					NginxClojureRT.log.error("can not close Closeable object such as FileInputStream!", e);
				}
			}
		}
	}
	
	@SuppressWarnings("rawtypes")
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

		if (req.isHijacked()) {
			NginxHttpServerChannel channel = req.channel();
			channel.setIgnoreFilter(ignoreFilter);
			return channel;
		}

		if (log.isDebugEnabled()) {
			log.debug("#%s: hijack at %s", req.nativeRequest(), req.uri());
		}

		LazyRequestMap cljReq = ((LazyRequestMap) req);
		cljReq.hijackTag[0] = 1;
		if (Thread.currentThread() == NginxClojureRT.NGINX_MAIN_THREAD && (req.phase() == -1
				|| req.phase() == NGX_HTTP_HEADER_FILTER_PHASE || req.phase() == NGX_HTTP_BODY_FILTER_PHASE)) {
			NginxClojureRT.ngx_http_clojure_mem_inc_req_count(req.nativeRequest(), 1);
		}

		NginxHttpServerChannel channel = cljReq.channel = new NginxHttpServerChannel(req, ignoreFilter);

		if (cljReq != cljReq.rawRequestMap) {
			cljReq.rawRequestMap.channel = channel;
		}

		if (log.isDebugEnabled()) {
			log.debug("#%s: hijacked at %s, lns:%s", req.nativeRequest(), req.uri(),
					req.listeners() == null ? 0 : req.listeners().size());
		}

		return channel;

	}

	protected void returnToRequestPool(LazyRequestMap lazyRequestMap) {
		pooledRequests.add(lazyRequestMap);
	}
	
	private String[] getPrefetchMeta(IFn f, String key, String[] defaultVal) {
		if (f instanceof IMeta) {
			IMeta m = (IMeta) f;
			IPersistentMap map = m.meta();
			if (map == null) {
				return defaultVal;
			}
			
			@SuppressWarnings("unchecked")
			List<String> val = (List<String>)map.valAt(key);
			return val == null ? defaultVal : val.toArray(new String[val.size()]);
		}
		
		return defaultVal;
	}

	/* (non-Javadoc)
	 * @see nginx.clojure.NginxSimpleHandler#headersNeedPrefetch()
	 */
	@Override
	public String[] headersNeedPrefetch() {
		if (ringHandler != null) {
			return getPrefetchMeta(ringHandler, "headersNeedPrefetch", DefinedPrefetch.ALL_HEADERS);
		} else if (headerFilter != null) {
			return getPrefetchMeta(headerFilter, "headersNeedPrefetch", DefinedPrefetch.ALL_HEADERS);
		} else {
			return getPrefetchMeta(bodyFilter.bodyFilter, "headersNeedPrefetch", DefinedPrefetch.ALL_HEADERS);
		}
	}

	/* (non-Javadoc)
	 * @see nginx.clojure.NginxSimpleHandler#variablesNeedPrefetch()
	 */
	@Override
	public String[] variablesNeedPrefetch() {
		if (ringHandler != null) {
			return getPrefetchMeta(ringHandler, "variablesNeedPrefetch", DefinedPrefetch.NO_VARS);
		} else if (headerFilter != null) {
			return getPrefetchMeta(headerFilter, "variablesNeedPrefetch", DefinedPrefetch.NO_VARS);
		} else {
			return getPrefetchMeta(bodyFilter.bodyFilter, "variablesNeedPrefetch", DefinedPrefetch.NO_VARS);
		}
	}
	
	/* (non-Javadoc)
	 * @see nginx.clojure.NginxSimpleHandler#responseHeadersNeedPrefetch()
	 */
	@Override
	public String[] responseHeadersNeedPrefetch() {
		if (ringHandler != null) {
			return getPrefetchMeta(ringHandler, "responseHeadersNeedPrefetch", DefinedPrefetch.NO_HEADERS);
		} else if (headerFilter != null) {
			return getPrefetchMeta(headerFilter, "responseHeadersNeedPrefetch", DefinedPrefetch.ALL_HEADERS);
		} else {
			return getPrefetchMeta(bodyFilter.bodyFilter, "responseHeadersNeedPrefetch", DefinedPrefetch.ALL_HEADERS);
		}
	}

}
