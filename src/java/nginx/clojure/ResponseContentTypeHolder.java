package nginx.clojure;

import static nginx.clojure.MiniConstants.DEFAULT_ENCODING;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_HEADERSO_CONTENT_TYPE_LEN_OFFSET;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_HEADERSO_CONTENT_TYPE_OFFSET;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_HEADERSO_HEADERS_OFFSET;
import static nginx.clojure.NginxClojureRT.pushNGXSizet;
import static nginx.clojure.NginxClojureRT.pushNGXString;

public class ResponseContentTypeHolder extends NgxStringHeaderHolder {
	
	public ResponseContentTypeHolder() {
		super("Content-Type", NGX_HTTP_CLOJURE_HEADERSO_CONTENT_TYPE_OFFSET, NGX_HTTP_CLOJURE_HEADERSO_HEADERS_OFFSET);
	}

	@Override
	public void push(long h, long pool, Object v) {
		int contentTypeLen = pushNGXString(h + NGX_HTTP_CLOJURE_HEADERSO_CONTENT_TYPE_OFFSET, (String)v, DEFAULT_ENCODING, pool);
		//be friendly to gzip module 
		pushNGXSizet(h + NGX_HTTP_CLOJURE_HEADERSO_CONTENT_TYPE_LEN_OFFSET, contentTypeLen);
	}

	@Override
	public void clear(long h) {
		pushNGXString(h + NGX_HTTP_CLOJURE_HEADERSO_CONTENT_TYPE_OFFSET, null, DEFAULT_ENCODING, 0);
		pushNGXSizet(h + NGX_HTTP_CLOJURE_HEADERSO_CONTENT_TYPE_LEN_OFFSET, 0);
	}
}