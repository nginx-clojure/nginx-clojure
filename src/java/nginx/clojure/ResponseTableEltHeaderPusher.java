/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure;

import static nginx.clojure.Constants.DEFAULT_ENCODING;
import static nginx.clojure.Constants.NGX_HTTP_CLOJURE_TEL_VALUE_OFFSET;
import static nginx.clojure.NginxClojureRT.UNSAFE;
import static nginx.clojure.NginxClojureRT.pushNGXString;

public class ResponseTableEltHeaderPusher implements ResponseHeaderPusher {

	protected long offset;
	protected String name;
	
	public ResponseTableEltHeaderPusher(String name, long offset) {
		this.offset = offset;
		this.name = name;
	}
	
	@Override
	public String name() {
		return name;
	}
	
	@Override
	public long knownOffset() {
		return offset;
	}

	@Override
	public void push(long h, long pool, Object v) {
		long p = UNSAFE.getAddress(h + offset);
		if (p == 0){
			p = NginxClojureRT.ngx_list_push(h + Constants.NGX_HTTP_CLOJURE_HEADERSO_HEADERS_OFFSET);
			if (p == 0) {
				throw new RuntimeException("can not push ngx list for headers");
			}
		}
		
		NginxClojureRT.pushNGXInt(p + Constants.NGX_HTTP_CLOJURE_TEL_HASH_OFFSET, 1);
		NginxClojureRT.pushNGXString(p + Constants.NGX_HTTP_CLOJURE_TEL_KEY_OFFSET, name, DEFAULT_ENCODING, pool);
		NginxClojureRT.pushNGXString(p + Constants.NGX_HTTP_CLOJURE_TEL_VALUE_OFFSET, (String)v, DEFAULT_ENCODING, pool);
		UNSAFE.putAddress(h + offset, p);
		
	}

}
