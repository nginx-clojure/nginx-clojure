/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.clj;

import static nginx.clojure.MiniConstants.DEFAULT_ENCODING;
import static nginx.clojure.MiniConstants.HEADERS_NAMES;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_HEADERSO_HEADERS_OFFSET;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_TEL_HASH_OFFSET;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_TEL_KEY_OFFSET;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_TEL_VALUE_OFFSET;
import static nginx.clojure.NginxClojureRT.ngx_http_clojure_mem_shadow_copy_ngx_str;
import static nginx.clojure.NginxClojureRT.ngx_list_push;
import static nginx.clojure.NginxClojureRT.pushNGXInt;
import static nginx.clojure.NginxClojureRT.pushNGXString;
import clojure.lang.ArraySeq;
import clojure.lang.ISeq;

public class ResponseUnknownHeaderPusher extends nginx.clojure.UnknownHeaderHolder {

	
	public ResponseUnknownHeaderPusher(String name) {
		super(name, NGX_HTTP_CLOJURE_HEADERSO_HEADERS_OFFSET);
	}
	

	@Override
	public void push(long h, long pool, Object v) {
		
		clear(h);
		
		ISeq seq = null;
		if (v instanceof String) {
			String val = (String) v;
			seq = ArraySeq.create(val);
		}else if (v instanceof ISeq) {
			seq = (ISeq) v;
		}else {
			seq = ArraySeq.create(String.valueOf(v));
		}
		
		int c = seq.count();
		if (c == 0) {
			return;
		}
		
		long lpname = 0;
		Long  pname = HEADERS_NAMES.get(name);
		if (pname != null) {
			lpname = pname;
		}
		
		for (int i = 0; i < c; i++) {
			String val = (String) seq.first();
			seq = seq.next();

			if (val != null) {
				long p = ngx_list_push(h + headersOffset);
				if (p == 0) {
					throw new RuntimeException("can not push ngx list for headers");
				}
				pushNGXInt(p + NGX_HTTP_CLOJURE_TEL_HASH_OFFSET, 1);
				if (lpname != 0) {
					ngx_http_clojure_mem_shadow_copy_ngx_str(lpname,  p + NGX_HTTP_CLOJURE_TEL_KEY_OFFSET);
				}else {
					pushNGXString(p + NGX_HTTP_CLOJURE_TEL_KEY_OFFSET, name, DEFAULT_ENCODING, pool);
					lpname = p + NGX_HTTP_CLOJURE_TEL_KEY_OFFSET; //UNSAFE.getAddress();
				}
				pushNGXString(p + NGX_HTTP_CLOJURE_TEL_VALUE_OFFSET, val, DEFAULT_ENCODING, pool);
			}
		
		}
	}

}
