package nginx.clojure.clj;

import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_HEADERSO_STATUS_OFFSET;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_REQ_HEADERS_OUT_OFFSET;
import static nginx.clojure.NginxClojureRT.fetchNGXInt;
import static nginx.clojure.NginxClojureRT.pushNGXInt;

import java.util.Map;

import nginx.clojure.NginxFilterRequest;
import nginx.clojure.NginxHandler;

public class LazyFilterRequestMap extends LazyRequestMap implements NginxFilterRequest {

	/**
	 * native ngx_chain_t
	 */
	protected long c;
	
	/**
	 * native headers_out
	 */
	protected long ho;
	
	protected LazyHeaderMap responseHeaders;
	
	public LazyFilterRequestMap(NginxHandler handler, long r, long c) {
		super(handler, r);
		this.c = c;
		this.ho =  r + NGX_HTTP_CLOJURE_REQ_HEADERS_OUT_OFFSET;
		responseHeaders = new LazyHeaderMap(r,  true);
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

}
