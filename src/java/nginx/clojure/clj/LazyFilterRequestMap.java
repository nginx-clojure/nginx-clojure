package nginx.clojure.clj;

import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_HEADERSO_STATUS_OFFSET;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_REQ_HEADERS_OUT_OFFSET;
import static nginx.clojure.NginxClojureRT.fetchNGXInt;
import static nginx.clojure.NginxClojureRT.pushNGXInt;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import nginx.clojure.ChannelCloseAdapter;
import nginx.clojure.NginxChainWrappedInputStream;
import nginx.clojure.NginxFilterRequest;
import nginx.clojure.NginxHandler;
import nginx.clojure.Stack;

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
	 * @see nginx.clojure.clj.LazyRequestMap#prefetchAll()
	 */
	@Override
	public void prefetchAll() {
		super.prefetchAll();
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
		this.released = true;
		this.channel = null;
		System.arraycopy(default_request_array, 0, array, 0, default_request_array.length);
		validLen = default_request_array.length;
		if (array.length > validLen) {
			Stack.fillNull(array, validLen, array.length - validLen);
		}
		if (listeners != null) {
			listeners.clear();
		}
//		((NginxClojureHandler)handler).returnToRequestPool(this);
	}
}
