package nginx.clojure.clj;

import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_HEADERSO_STATUS_OFFSET;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_REQ_HEADERS_OUT_OFFSET;
import static nginx.clojure.MiniConstants.NGX_HTTP_HEADER_FILTER_PHASE;
import static nginx.clojure.NginxClojureRT.fetchNGXInt;
import static nginx.clojure.NginxClojureRT.pushNGXInt;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import clojure.lang.IMapEntry;
import clojure.lang.IPersistentCollection;
import clojure.lang.IPersistentMap;
import clojure.lang.ISeq;
import nginx.clojure.ChannelCloseAdapter;
import nginx.clojure.NginxChainWrappedInputStream;
import nginx.clojure.NginxFilterRequest;
import nginx.clojure.NginxHandler;

public class LazyFilterRequestMap extends LazyRequestMap implements NginxFilterRequest, Cloneable {

	/**
	 * native ngx_chain_t
	 */
	protected long c;
	
	protected NginxChainWrappedInputStream body;
	
	/**
	 * native headers_out
	 */
	protected long ho;
	
	protected LazyHeaderMap responseHeaders;
	
	protected LazyFilterRequestMap origin;
	
    protected final static Map<Long, LazyFilterRequestMap> bodyFilterRequests = new ConcurrentHashMap<Long, LazyFilterRequestMap>();
	
	protected final static ChannelCloseAdapter<Long> bodyFilterRequestsCleaner = new ChannelCloseAdapter<Long>() {
		
		@Override
		public void onClose(Long r) throws IOException {
			bodyFilterRequests.remove(r);
		}
	};
	
	public static LazyFilterRequestMap cloneExisted(long r, long c) {
		LazyFilterRequestMap req = bodyFilterRequests.get(r);
		LazyFilterRequestMap creq = null;
		if (req != null) {
			try {
				creq = (LazyFilterRequestMap) req.clone();
				creq.array = null;
				creq.origin = req;
				creq.c = c;
				if (c > 0) {
					creq.body = new NginxChainWrappedInputStream(creq, c);
				} else {
					creq.body = null;
				}
			} catch (CloneNotSupportedException e) {
				e.printStackTrace();//should never happen
			} catch (IOException e) {
				e.printStackTrace();//should never happen
			}
		}
		return creq;
	}
	
	public LazyFilterRequestMap(NginxHandler handler, long r, long c) {
		super(handler, r);
		this.c = c;
		this.ho =  r + NGX_HTTP_CLOJURE_REQ_HEADERS_OUT_OFFSET;
		responseHeaders = new LazyHeaderMap(r,  true);
		if (c > 0) { //body filter request
			try {
				body = new NginxChainWrappedInputStream(this, c);
			} catch (IOException e) {
				throw new RuntimeException("can not build body r:" + r +", c=" + c, e);
			}
			bodyFilterRequests.put(r, this);
			this.addListener(r, bodyFilterRequestsCleaner);
		}
	}
	
	/* (non-Javadoc)
	 * @see nginx.clojure.clj.LazyRequestMap#reset(long, nginx.clojure.clj.NginxClojureHandler)
	 */
	@Override
	public void reset(long r, NginxClojureHandler handler) {
		if (origin == null) {
			super.reset(r, handler);			
		} else {
			throw new UnsupportedOperationException("cloned filter request should not be reset!");
		}
	}

	@Override
	public int responseStatus() {
		return fetchNGXInt(ho + NGX_HTTP_CLOJURE_HEADERSO_STATUS_OFFSET);
	}

	public LazyFilterRequestMap responseStatus(int status) {
		pushNGXInt(ho + NGX_HTTP_CLOJURE_HEADERSO_STATUS_OFFSET, status );
		return this;
	}

	@Override
	public Map<String, Object> responseHeaders() {
		return responseHeaders;
	}
	
	/* (non-Javadoc)
	 * @see nginx.clojure.clj.LazyRequestMap#prefetchAll(java.lang.String[], java.lang.String[])
	 */
	@Override
	public void prefetchAll(String[] headers, String[] variables, String[] outHeaders) {
		if (origin == null) {
			super.prefetchAll(headers, variables, outHeaders);	
		}
		
		if (phase == NGX_HTTP_HEADER_FILTER_PHASE) {
			if (responseHeaders instanceof LazyHeaderMap) {
				((LazyHeaderMap)responseHeaders).enableSafeCache(outHeaders);
			}
		} else {
			if (responseHeaders instanceof LazyHeaderMap) {
				((LazyHeaderMap)responseHeaders).enableSafeCache(outHeaders);
			}
		}
		
		if (body != null) {
			try {
				body.prefetchNativeData();
			} catch (IOException e) {
				throw new RuntimeException("can not prefetch native data", e);
			}			
		}
	}
	
	/* (non-Javadoc)
	 * @see nginx.clojure.clj.LazyRequestMap#index(java.lang.Object)
	 */
	@Override
	protected int index(Object key) {
		if (origin == null) {
			return super.index(key);	
		}
		return origin.index(key);
	}
	
	/* (non-Javadoc)
	 * @see nginx.clojure.clj.LazyRequestMap#iterator()
	 */
	@SuppressWarnings("rawtypes")
	@Override
	public Iterator iterator() {
		if (origin == null) {
			return super.iterator();			
		}
		return origin.iterator();
	}
	
	@Override
	public IMapEntry entryAt(Object key) {
		if (origin == null) {
			return super.entryAt(key);
		}
		return origin.entryAt(key);
	}

	@Override
	public int count() {
		if (origin == null) {
			return super.count();
		}
		return origin.count();
	}
	
	/* (non-Javadoc)
	 * @see nginx.clojure.clj.LazyRequestMap#cons(java.lang.Object)
	 */
	@Override
	public IPersistentCollection cons(Object o) {
		if (origin == null) {
			return super.cons(o);			
		}
		return origin.cons(o);
	}
	
	/* (non-Javadoc)
	 * @see nginx.clojure.clj.LazyRequestMap#seq()
	 */
	@Override
	public ISeq seq() {
		if (origin == null) {
			return super.seq();			
		}
		return origin.seq();
	}
	
	/* (non-Javadoc)
	 * @see nginx.clojure.clj.LazyRequestMap#element(int)
	 */
	@Override
	protected Object element(int i) {
		if (origin == null) {
			return super.element(i);
		}
		return origin.element(i);
	}
	
	/* (non-Javadoc)
	 * @see nginx.clojure.clj.LazyRequestMap#valAt(java.lang.Object)
	 */
	@Override
	public Object valAt(Object key) {
		if (origin == null) {
			return super.valAt(key);			
		}
		return origin.valAt(key);
	}
	
	/* (non-Javadoc)
	 * @see nginx.clojure.clj.LazyRequestMap#valAt(java.lang.Object, java.lang.Object)
	 */
	@Override
	public Object valAt(Object key, Object notFound) {
		if (origin == null) {
			return super.valAt(key, notFound);
		}
		return origin.valAt(key, notFound);
	}
	
	/* (non-Javadoc)
	 * @see nginx.clojure.clj.LazyRequestMap#assoc(java.lang.Object, java.lang.Object)
	 */
	@Override
	public IPersistentMap assoc(Object key, Object val) {
		if (origin == null) {
			return super.assoc(key, val);
		}
		return origin.assoc(key, val);
	}
	
	
	/* (non-Javadoc)
	 * @see nginx.clojure.clj.LazyRequestMap#assocEx(java.lang.Object, java.lang.Object)
	 */
	@Override
	public IPersistentMap assocEx(Object key, Object val) {
		if (origin == null) {
			return super.assocEx(key, val);	
		}
		return origin.assocEx(key, val);
	}
	
	/* (non-Javadoc)
	 * @see nginx.clojure.clj.LazyRequestMap#without(java.lang.Object)
	 */
	@Override
	public IPersistentMap without(Object key) {
		if (origin == null) {
			return super.without(key);
		}
		return origin.without(key);
	}
	
	@Override
	public void tagReleased() {
		this.released = true;
		this.channel = null;
		if (listeners != null) {
			listeners.clear();
		}
		
//		if (origin == null) {
//			System.arraycopy(default_request_array, 0, array, 0, default_request_array.length);
//			validLen = default_request_array.length;
//			if (array.length > validLen) {
//				Stack.fillNull(array, validLen, array.length - validLen);
//			}
//		}
//		((NginxClojureHandler)handler).returnToRequestPool(this);
	}
	
	/* (non-Javadoc)
	 * @see nginx.clojure.NginxFilterRequest#chunkChain()
	 */
	@Override
	public long chunkChain() {
		return c;
	}
	
	/* (non-Javadoc)
	 * @see nginx.clojure.NginxFilterRequest#isLast()
	 */
	@Override
	public boolean isLast() {
		return body != null && body.isLast();
	}
	
	/* (non-Javadoc)
	 * @see nginx.clojure.clj.LazyRequestMap#applyDelayed()
	 */
	@Override
	public void applyDelayed() {
		if (responseHeaders instanceof LazyHeaderMap) {
			((LazyHeaderMap)responseHeaders).applyDelayed();
		}
	}
}
