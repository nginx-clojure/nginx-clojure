/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.clj;

import static nginx.clojure.MiniConstants.DEFAULT_ENCODING;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_HEADERSO_HEADERS_OFFSET;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_TEL_HASH_OFFSET;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_TEL_KEY_OFFSET;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_TEL_VALUE_OFFSET;
import nginx.clojure.NginxClojureRT;
import nginx.clojure.ResponseHeaderPusher;
import clojure.lang.ArraySeq;
import clojure.lang.ISeq;

public class ResponseUnknownHeaderPusher implements ResponseHeaderPusher {

	protected String name;
	
	public ResponseUnknownHeaderPusher(String name) {
		this.name = name;
	}
	
	@Override
	public String name() {
		return name;
	}
	
	@Override
	public long knownOffset() {
		return -1;
	}

	@Override
	public void push(long h, long pool, Object v) {
		
		ISeq seq = null;
		if (v instanceof String) {
			String val = (String) v;
			seq = ArraySeq.create(val);
		}else if (v instanceof ISeq) {
			seq = (ISeq) v;
		}
		
		int c = seq.count();
		if (c == 0) {
			return;
		}
		
		
		for (int i = 0; i < c; i++) {
			String val = (String) seq.first();
			seq = seq.next();
			if (val != null) {
				long p = NginxClojureRT.ngx_list_push(h + NGX_HTTP_CLOJURE_HEADERSO_HEADERS_OFFSET);
				if (p == 0) {
					throw new RuntimeException("can not push ngx list for headers");
				}
				NginxClojureRT.pushNGXInt(p + NGX_HTTP_CLOJURE_TEL_HASH_OFFSET, 1);
				NginxClojureRT.pushNGXString(p + NGX_HTTP_CLOJURE_TEL_KEY_OFFSET, name, DEFAULT_ENCODING, pool);
				NginxClojureRT.pushNGXString(p + NGX_HTTP_CLOJURE_TEL_VALUE_OFFSET, val, DEFAULT_ENCODING, pool);
			}
		}
	}

}
