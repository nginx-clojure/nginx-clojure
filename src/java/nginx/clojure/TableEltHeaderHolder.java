/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure;

import static nginx.clojure.MiniConstants.DEFAULT_ENCODING;
import static nginx.clojure.MiniConstants.HEADERS_NAMES;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_TEL_HASH_OFFSET;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_TEL_KEY_OFFSET;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_TEL_VALUE_OFFSET;
import static nginx.clojure.NginxClojureRT.UNSAFE;
import static nginx.clojure.NginxClojureRT.fetchNGXString;
import static nginx.clojure.NginxClojureRT.ngx_http_clojure_mem_shadow_copy_ngx_str;
import static nginx.clojure.NginxClojureRT.pushNGXInt;
import static nginx.clojure.NginxClojureRT.pushNGXString;

public class TableEltHeaderHolder extends AbstractHeaderHolder {

	
	public TableEltHeaderHolder(String name, long offset, long headersOffset) {
		this.offset = offset;
		this.name = name;
		this.headersOffset = headersOffset;
		if (offset < 0) {
			throw new IllegalArgumentException("offset of " + name + " is invalid, must >=0 but meets  " + offset );
		}
		if (headersOffset < 0) {
			throw new IllegalArgumentException("headersOffset of " + name + " is invalid, must >=0 but meets  " + headersOffset );
		}
	}

	@Override
	public void push(long h, long pool, Object v) {
		long p = UNSAFE.getAddress(h + offset);
		if (p == 0){
			p = NginxClojureRT.ngx_list_push(h + headersOffset);
			if (p == 0) {
				throw new RuntimeException("can not push ngx list for headers");
			}
			pushNGXInt(p + NGX_HTTP_CLOJURE_TEL_HASH_OFFSET, 1);
			ngx_http_clojure_mem_shadow_copy_ngx_str(HEADERS_NAMES.get(name), p + NGX_HTTP_CLOJURE_TEL_KEY_OFFSET);
		}
		pushNGXString(p + NGX_HTTP_CLOJURE_TEL_VALUE_OFFSET, pickString(v), DEFAULT_ENCODING, pool);
		UNSAFE.putAddress(h + offset, p);
		
	}

	@Override
	public void clear(long h) {
		long p = UNSAFE.getAddress(h + offset);
		if (p != 0) {
			NginxClojureRT.pushNGXInt(p + NGX_HTTP_CLOJURE_TEL_HASH_OFFSET, 0);
			UNSAFE.putAddress(h+offset, 0);
		}
	}
	@Override
	public Object fetch(long h) {
		long p = UNSAFE.getAddress(h + offset);
		if (p == 0) {
			return null;
		}
		return fetchNGXString(p +NGX_HTTP_CLOJURE_TEL_VALUE_OFFSET , DEFAULT_ENCODING);
	}

	@Override
	public boolean exists(long h) {
		return h != 0 && UNSAFE.getAddress(h + offset) != 0;
	}

}
