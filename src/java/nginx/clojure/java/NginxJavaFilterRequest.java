package nginx.clojure.java;

import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_HEADERSO_STATUS_OFFSET;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_REQ_HEADERS_OUT_OFFSET;
import static nginx.clojure.NginxClojureRT.fetchNGXInt;
import static nginx.clojure.NginxClojureRT.pushNGXInt;

import java.util.Map;

import nginx.clojure.NginxFilterRequest;
import nginx.clojure.NginxHandler;

public class NginxJavaFilterRequest extends NginxJavaRequest implements NginxFilterRequest {

	/**
	 * native ngx_chain_t
	 */
	protected long c;
	
	/**
	 * native headers_out
	 */
	protected long ho;
	
	protected Map<String, Object> responseHeaders;
	
	public NginxJavaFilterRequest(NginxHandler handler, long r, long c)  {
		super(handler, r);
//		long pool = NginxClojureRT.UNSAFE.getAddress(r + NGX_HTTP_CLOJURE_REQ_POOL_OFFSET);
		ho = r + NGX_HTTP_CLOJURE_REQ_HEADERS_OUT_OFFSET;
		responseHeaders = new JavaLazyHeaderMap(r, true);
	}
	
	@Override
	public int responseStatus() {
		return fetchNGXInt(ho + NGX_HTTP_CLOJURE_HEADERSO_STATUS_OFFSET);
	}

	public NginxJavaFilterRequest responseStatus(int status) {
		pushNGXInt(ho + NGX_HTTP_CLOJURE_HEADERSO_STATUS_OFFSET, status );
		return this;
	}
	
	@Override
	public Map<String, Object> responseHeaders() {
		return responseHeaders;
	}

}
