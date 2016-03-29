package nginx.clojure.java;

import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_HEADERSO_STATUS_LINE_OFFSET;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_HEADERSO_STATUS_OFFSET;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_REQ_HEADERS_OUT_OFFSET;
import static nginx.clojure.NginxClojureRT.fetchNGXInt;
import static nginx.clojure.NginxClojureRT.pushNGXInt;
import static nginx.clojure.NginxClojureRT.pushNGXString;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import nginx.clojure.ChannelCloseAdapter;
import nginx.clojure.NginxFilterRequest;
import nginx.clojure.NginxHandler;

public class NginxJavaFilterRequest extends NginxJavaRequest implements NginxFilterRequest, Cloneable {

	/**
	 * native ngx_chain_t
	 */
	protected long c;
	
	/**
	 * native headers_out
	 */
	protected long ho;
	
	protected Map<String, Object> responseHeaders;
	
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
				creq = (NginxJavaFilterRequest) req.clone();
				creq.c = c;
			} catch (CloneNotSupportedException e) {
			}
		}
		return creq;
	}
	
	public NginxJavaFilterRequest(NginxHandler handler, long r, long c)  {
		super(handler, r);
		this.c = c;
//		long pool = NginxClojureRT.UNSAFE.getAddress(r + NGX_HTTP_CLOJURE_REQ_POOL_OFFSET);
		ho = r + NGX_HTTP_CLOJURE_REQ_HEADERS_OUT_OFFSET;
		responseHeaders = new JavaLazyHeaderMap(r, true);
		
		if (c > 0) { //body filter request
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
	
	public long nativeChain() {
		return c;
	}
	
	@Override
	protected Object clone() throws CloneNotSupportedException {
		return super.clone();
	}

}
