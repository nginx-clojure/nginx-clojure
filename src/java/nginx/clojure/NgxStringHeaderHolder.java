package nginx.clojure;

import static nginx.clojure.MiniConstants.DEFAULT_ENCODING;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_STR_LEN_OFFSET;
import static nginx.clojure.NginxClojureRT.fetchNGXInt;
import static nginx.clojure.NginxClojureRT.fetchNGXString;
import static nginx.clojure.NginxClojureRT.pushNGXString;

public class NgxStringHeaderHolder extends AbstractHeaderHolder {
	
	public NgxStringHeaderHolder() {
	}
	
	public NgxStringHeaderHolder(String name, long offset, long headersOffset) {
		super(name, offset, headersOffset);
	}

	@Override
	public void push(long h, long pool, Object v) {
		pushNGXString(h + offset, pickString(v), DEFAULT_ENCODING, pool);
	}

	@Override
	public void clear(long h) {
		pushNGXString(h + offset, null, DEFAULT_ENCODING, 0);
	}

	@Override
	public Object fetch(long h) {
		return fetchNGXString(h + offset, DEFAULT_ENCODING);
	}

	@Override
	public boolean exists(long h) {
		if (h == 0){
			return false;
		}
		long lenAddr = h + NGX_HTTP_CLOJURE_STR_LEN_OFFSET;
		int len = fetchNGXInt(lenAddr);
		if (len <= 0){
			return false;
		}
		return true;
	}

}
