/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.clj;

import static nginx.clojure.MiniConstants.DEFAULT_ENCODING;
import static nginx.clojure.MiniConstants.HEADERS_NAMES;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_ARRAY_ELTS_OFFSET;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_HEADERSO_HEADERS_OFFSET;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_PTR_SIZE;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_TEL_HASH_OFFSET;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_TEL_KEY_OFFSET;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_TEL_NEXT_OFFSET;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_TEL_VALUE_OFFSET;
import static nginx.clojure.MiniConstants.NGX_OK;
import static nginx.clojure.NginxClojureRT.UNSAFE;
import static nginx.clojure.NginxClojureRT.ngx_http_clojure_mem_shadow_copy_ngx_str;
import static nginx.clojure.NginxClojureRT.pushNGXInt;
import static nginx.clojure.NginxClojureRT.pushNGXString;

import nginx.clojure.EtlListHeaderHolder;
import nginx.clojure.NginxClojureRT;
import clojure.lang.ArraySeq;
import clojure.lang.ISeq;
import clojure.lang.RT;

public class SeqEtlHeaderHolder extends EtlListHeaderHolder {

	
	public SeqEtlHeaderHolder(String name, long offset,  long headersOffset) {
		super(name, offset, headersOffset);
	}
	

	
	@Override
	public void push(long h, long pool, Object v) {
		long haddr = h + offset;
		if (haddr == 0){
			throw new RuntimeException("invalid address for set header array value " + v);
		}
		
		ISeq seq = null;
		if (v instanceof String) {
			String val = (String) v;
			seq = ArraySeq.create(val);
		}else if (v instanceof ISeq) {
			seq = (ISeq) v;
		}else {
			seq = RT.seq(v);
		}
		
		int c = seq.count();
		if (c == 0) {
			clear(h);
			return;
		}
		
		long lp = h + offset;
		long p = UNSAFE.getAddress(lp);
		long pname = HEADERS_NAMES.get(name);
		
		if (p == 0) {
			for (int i = 0; i < c; i++) {
				String val = (String) seq.first();
				seq = seq.next();
				if (val != null) {
					p = NginxClojureRT.ngx_list_push(h + NGX_HTTP_CLOJURE_HEADERSO_HEADERS_OFFSET);
					if (p == 0) {
						throw new RuntimeException("can not push ngx etl list for headers");
					}
					UNSAFE.putAddress(lp, p);
					pushNGXInt(p + NGX_HTTP_CLOJURE_TEL_HASH_OFFSET, 1);
					ngx_http_clojure_mem_shadow_copy_ngx_str(pname,  p + NGX_HTTP_CLOJURE_TEL_KEY_OFFSET);
					pushNGXString(p + NGX_HTTP_CLOJURE_TEL_VALUE_OFFSET, val, DEFAULT_ENCODING, pool);
					lp = p + NGX_HTTP_CLOJURE_TEL_NEXT_OFFSET;
				}
			}
		} else {
			for (int i = 0; i < c; i++) {
				String val = (String) seq.first();
				seq = seq.next();
				if (val != null) {
					if (p == 0) {
						p = NginxClojureRT.ngx_list_push(h + NGX_HTTP_CLOJURE_HEADERSO_HEADERS_OFFSET);
						if (p == 0) {
							throw new RuntimeException("can not push ngx etl list for headers");
						}
						UNSAFE.putAddress(lp, p);
						
					}
					pushNGXInt(p + NGX_HTTP_CLOJURE_TEL_HASH_OFFSET, 1);
					ngx_http_clojure_mem_shadow_copy_ngx_str(pname,  p + NGX_HTTP_CLOJURE_TEL_KEY_OFFSET);
					pushNGXString(p + NGX_HTTP_CLOJURE_TEL_VALUE_OFFSET, val, DEFAULT_ENCODING, pool);
					lp = p + NGX_HTTP_CLOJURE_TEL_NEXT_OFFSET;
					p = UNSAFE.getAddress(lp);
				}
			}
		}
		
		UNSAFE.putAddress(lp, 0);
	}

	@Override
	public Object fetch(long h) {
		Object v =  super.fetch(h);
		if (v != null && v.getClass().isArray()) {
			return RT.seq(v);
		}
		return v;
	}

}
