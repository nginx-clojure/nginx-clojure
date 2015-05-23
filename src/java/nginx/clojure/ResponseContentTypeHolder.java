package nginx.clojure;

import static nginx.clojure.MiniConstants.*;
import static nginx.clojure.NginxClojureRT.ngx_http_clojure_mem_shadow_copy_ngx_str;
import static nginx.clojure.NginxClojureRT.pushNGXSizet;
import static nginx.clojure.NginxClojureRT.pushNGXString;

public class ResponseContentTypeHolder extends NgxStringHeaderHolder {
	
	public ResponseContentTypeHolder() {
		super("Content-Type", NGX_HTTP_CLOJURE_HEADERSO_CONTENT_TYPE_OFFSET, NGX_HTTP_CLOJURE_HEADERSO_HEADERS_OFFSET);
	}

	@Override
	public void push(long h, long pool, Object v) {
		String s = pickString(v);
		Long ll = s == null ? null : MIME_TYPES.get(s);
		if (ll != null) {
			ngx_http_clojure_mem_shadow_copy_ngx_str(ll.longValue(), h + NGX_HTTP_CLOJURE_HEADERSO_CONTENT_TYPE_OFFSET);
			//be friendly to gzip module 
			pushNGXSizet(h + NGX_HTTP_CLOJURE_HEADERSO_CONTENT_TYPE_LEN_OFFSET, s.length());
		}else {
			int contentTypeLen = pushNGXString(h + NGX_HTTP_CLOJURE_HEADERSO_CONTENT_TYPE_OFFSET, s, DEFAULT_ENCODING, pool);
			//be friendly to gzip module 
			pushNGXSizet(h + NGX_HTTP_CLOJURE_HEADERSO_CONTENT_TYPE_LEN_OFFSET, contentTypeLen);
		}
	}

	@Override
	public void clear(long h) {
		pushNGXString(h + NGX_HTTP_CLOJURE_HEADERSO_CONTENT_TYPE_OFFSET, null, DEFAULT_ENCODING, 0);
		pushNGXSizet(h + NGX_HTTP_CLOJURE_HEADERSO_CONTENT_TYPE_LEN_OFFSET, 0);
	}
}