package nginx.clojure.java;

import static nginx.clojure.MiniConstants.NGX_HTTP_HEADER_FILTER_PHASE;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_HEADERSO_STATUS_LINE_OFFSET;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_HEADERSO_STATUS_OFFSET;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_REQ_HEADERS_OUT_OFFSET;
import static nginx.clojure.NginxClojureRT.fetchNGXInt;
import static nginx.clojure.NginxClojureRT.pushNGXInt;
import static nginx.clojure.NginxClojureRT.pushNGXString;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import nginx.clojure.ChannelCloseAdapter;
import nginx.clojure.NginxChainWrappedInputStream;
import nginx.clojure.NginxClojureRT;
import nginx.clojure.NginxFilterRequest;
import nginx.clojure.NginxHandler;

public class NginxJavaFilterRequest extends NginxJavaRequest implements NginxFilterRequest, Cloneable {

	/**
	 * native ngx_chain_t
	 */
	protected long c;
	
	protected NginxChainWrappedInputStream body;
	
	/**
	 * native headers_out
	 */
	protected long ho;
	
	protected Map<String, Object> responseHeaders;
	
	protected NginxJavaFilterRequest origin;
	
	protected final static Map<Long, NginxJavaFilterRequest> bodyFilterRequests = new ConcurrentHashMap<Long, NginxJavaFilterRequest>();
	
	protected final static ChannelCloseAdapter<Long> bodyFilterRequestsCleaner = new ChannelCloseAdapter<Long>() {
		
		@Override
		public void onClose(Long r) throws IOException {
			bodyFilterRequests.remove(r);
		}
	};
	
	public static NginxJavaFilterRequest cloneExisted(long r, long c) {
		NginxJavaFilterRequest req = bodyFilterRequests.get(r);
		NginxJavaFilterRequest creq = null;
		if (req != null) {
			try {
				NginxClojureRT.log.debug("clone existed filter request %d, c=%d", r, c);
				creq = (NginxJavaFilterRequest) req.clone();
				creq.c = c;
				if (c > 0) {
					creq.body = new NginxChainWrappedInputStream(creq, c);
				} else {
					creq.body = null;
				}
			} catch (CloneNotSupportedException e) {
			} catch (IOException e) {
				throw new RuntimeException("can not build body r:" + r +", c=" + c, e);
			}
		}
		return creq;
	}
	
	public NginxJavaFilterRequest(int phase, NginxHandler handler, long r, long c)  {
		super(phase, handler, r);
		this.c = c;
//		long pool = NginxClojureRT.UNSAFE.getAddress(r + NGX_HTTP_CLOJURE_REQ_POOL_OFFSET);
		ho = r + NGX_HTTP_CLOJURE_REQ_HEADERS_OUT_OFFSET;
		responseHeaders = new JavaLazyHeaderMap(r, true);
		
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
	
	@Override
	public int responseStatus() {
		return fetchNGXInt(ho + NGX_HTTP_CLOJURE_HEADERSO_STATUS_OFFSET);
	}

	public NginxJavaFilterRequest responseStatus(int status) {
		pushNGXInt(ho + NGX_HTTP_CLOJURE_HEADERSO_STATUS_OFFSET, status );
		pushNGXString(ho + NGX_HTTP_CLOJURE_HEADERSO_STATUS_LINE_OFFSET, null, null, 0);
		return this;
	}
	
	@Override
	public Map<String, Object> responseHeaders() {
		return responseHeaders;
	}
	
	/* (non-Javadoc)
	 * @see nginx.clojure.java.NginxJavaRequest#reset(long, nginx.clojure.NginxHandler)
	 */
	@Override
	public void reset(long r, NginxHandler handler) {
		if (origin == null) {
			super.reset(r, handler);
		} else {
			throw new UnsupportedOperationException("cloned filter request should not be reset!");
		}
	}
	
	public long nativeChain() {
		return c;
	}
	
	@Override
	protected Object clone() throws CloneNotSupportedException {
		NginxJavaFilterRequest req = (NginxJavaFilterRequest) super.clone();
		req.origin = this;
		req.array = null;
		return req;
	}
	
	/* (non-Javadoc)
	 * @see nginx.clojure.java.NginxJavaRequest#key(int)
	 */
	@Override
	public String key(int i) {
		if (origin == null) {
			return super.key(i);	
		}
		return origin.key(i);
	}
	
	/* (non-Javadoc)
	 * @see nginx.clojure.java.NginxJavaRequest#val(int)
	 */
	@Override
	public Object val(int i) {
		if (origin == null) {
			return super.val(i);	
		}
		return origin.val(i);
	}
	
	/* (non-Javadoc)
	 * @see nginx.clojure.java.NginxJavaRequest#index(java.lang.Object)
	 */
	@Override
	protected int index(Object key) {
		if (origin == null) {
			return super.index(key);
		}
		return origin.index(key);
	}
	
	/* (non-Javadoc)
	 * @see nginx.clojure.java.NginxJavaRequest#prefetchAll(java.lang.String[], java.lang.String[])
	 */
	@Override
	public void prefetchAll(String[] headers, String[] variables, String[] outHeaders) {
		if (origin == null) {
			super.prefetchAll(headers, variables, outHeaders);
		}

		if (phase == NGX_HTTP_HEADER_FILTER_PHASE) {
			if (responseHeaders instanceof JavaLazyHeaderMap) {
				((JavaLazyHeaderMap)responseHeaders).enableSafeCache(outHeaders);
			}
		} else {
			if (responseHeaders instanceof JavaLazyHeaderMap) {
				((JavaLazyHeaderMap)responseHeaders).enableSafeCache(outHeaders);
				responseHeaders = Collections.unmodifiableMap(responseHeaders);
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
	
	@Override
	public void tagReleased() {
		NginxClojureRT.log.debug("tag filter request! %d", r);
		this.released = true;
		this.channel = null;
		if (listeners != null) {
			listeners.clear();
		}
		
//		if (origin == null) {
//			System.arraycopy(default_request_array, 0, array, 0, default_request_array.length);			
//		}
//		((NginxJavaHandler)handler).returnToRequestPool(this);
	}
	
	@Override
	public int size() {
		if (origin != null) {
			return origin.size();
		}
		return super.size();
	}

	@Override
	public boolean isEmpty() {
		if (origin != null) {
			return origin.isEmpty();
		}
		return super.isEmpty();		
	}

	@Override
	public boolean containsKey(Object key) {
		if (origin != null) {
			return origin.containsKey(key);
		}
		return super.containsKey(key);		
	}

	@Override
	public boolean containsValue(Object value) {
		if (origin != null) {
			return origin.containsValue(value);
		}
		return super.containsValue(value);
	}

	@Override
	public Object get(Object key) {
		if (origin != null) {
			return origin.get(key);
		}
		return super.get(key);	
	}

	@Override
	public Object put(String key, Object value) {
		if (origin != null) {
			return origin.put(key, value);
		}
		return super.put(key, value);
	}

	@Override
	public Object remove(Object key) {
		if (origin != null) {
			return origin.remove(key);
		}
		return super.remove(key);
	}

	@Override
	public void putAll(Map<? extends String, ? extends Object> m) {
		if (origin != null) {
			origin.putAll(m);
			return;
		}
		super.putAll(m);
	}

	@Override
	public void clear() {
		if (origin != null) {
			origin.clear();
			return;
		}
		super.clear();
	}

	@Override
	public Set<String> keySet() {
		if (origin != null) {
			return origin.keySet();
		}
		return super.keySet();
	}

	@Override
	public Collection<Object> values() {
		if (origin != null) {
			return origin.values();
		}
		return super.values();
	}

	@Override
	public Set<java.util.Map.Entry<String, Object>> entrySet() {
		if (origin != null) {
			return origin.entrySet();
		}
		return super.entrySet();
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
	 * @see nginx.clojure.java.NginxJavaRequest#applyDelayed()
	 */
	@Override
	public void applyDelayed() {
		if (responseHeaders instanceof JavaLazyHeaderMap) {
			((JavaLazyHeaderMap)responseHeaders).applyDelayed();
		}
	}

}
