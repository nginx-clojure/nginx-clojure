/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.clj;

import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_CHAINT_SIZE;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_CHAIN_NEXT_OFFSET;
import static nginx.clojure.MiniConstants.NGX_HTTP_NO_CONTENT;
import static nginx.clojure.MiniConstants.NR_ASYNC_TAG;
import static nginx.clojure.MiniConstants.NR_PHRASE_DONE;
import static nginx.clojure.NginxClojureRT.UNSAFE;
import static nginx.clojure.NginxClojureRT.ngx_palloc;
import static nginx.clojure.clj.Constants.ASYNC_TAG;
import static nginx.clojure.clj.Constants.BODY;
import static nginx.clojure.clj.Constants.PHRASE_DONE;

import java.io.Closeable;
import java.util.Map;

import nginx.clojure.MiniConstants;
import nginx.clojure.NginxClojureRT;
import nginx.clojure.NginxRequest;
import nginx.clojure.NginxResponse;
import nginx.clojure.NginxServerChannel;
import nginx.clojure.NginxSimpleHandler;
import nginx.clojure.ResponseHeaderPusher;
import nginx.clojure.ResponseUnknownHeaderPusher;
import clojure.lang.IFn;
import clojure.lang.ISeq;
import clojure.lang.Keyword;
import clojure.lang.RT;
import clojure.lang.Seqable;

public class NginxClojureHandler extends NginxSimpleHandler {

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
		return name == null ? null : name.toLowerCase();
	}
	
	@Override
	protected  String normalizeHeaderName(Object nameObj) {
		return normalizeHeaderNameHelper(nameObj);
	}

	@Override
	public NginxRequest makeRequest(long r) {
		if (r == 0) {
			return new LazyRequestMap(this, r, new Object[0]); 
		}
		return new LazyRequestMap(this, r);
	}
	
	@Override
	public NginxResponse process(NginxRequest req) {
		LazyRequestMap r = (LazyRequestMap)req;
		try{
			Map resp = (Map) ringHandler.invoke(r);
			return toNginxResponse(r, resp);
		}finally {
			int bodyIdx = r.index(BODY);
			if (bodyIdx > 0 && r.array[bodyIdx] instanceof Closeable) {
				try {
					((Closeable)r.array[bodyIdx]).close();
				} catch (Throwable e) {
					NginxClojureRT.log.error("can not close Closeable object such as FileInputStream!", e);
				}
			}
		}
	}
	
	public  NginxResponse toNginxResponse(NginxRequest req, Object resp) {
		if (req.isHijacked()) {
			return NR_ASYNC_TAG;
		}
		
		if (resp == ASYNC_TAG) {
			return NR_ASYNC_TAG;
		}
		if (resp == PHRASE_DONE) {
			return NR_PHRASE_DONE;
		}
		if (resp == null) {
			return null;
		}
		return new NginxClojureResponse(req, (Map)resp);
	}
	
	@Override
	public void completeAsyncResponse(NginxRequest req, Object resp) {
		NginxClojureRT.completeAsyncResponse(req, toNginxResponse(req, resp));
	}

	@Override
	public ResponseHeaderPusher fetchResponseHeaderPusher(String name) {
		ResponseHeaderPusher pusher = Constants.KNOWN_RESP_HEADERS.get(name);
		if (pusher == null) {
			pusher = new ResponseUnknownHeaderPusher(name);
		}
		return pusher;
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
					if (isLast != MiniConstants.NGX_CLOJURE_BUF_LAST_OF_NONE && seq == null) {
						subTail = buildResponseItemBuf(r, pool, o, isLast, chain);
					}else {
						subTail = buildResponseItemBuf(r, pool, o, MiniConstants.NGX_CLOJURE_BUF_LAST_OF_NONE, chain);
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
	public NginxServerChannel hijack(NginxRequest req, boolean ignoreFilter) {
		((LazyRequestMap)req).hijacked = true;
		return new NginxServerChannel(req, ignoreFilter);
	}

}
