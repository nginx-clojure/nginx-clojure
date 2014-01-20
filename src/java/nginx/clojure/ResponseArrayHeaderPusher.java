/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure;

import static nginx.clojure.Constants.DEFAULT_ENCODING;
import static nginx.clojure.NginxClojureRT.UNSAFE;
import clojure.lang.ArraySeq;
import clojure.lang.ISeq;
import clojure.lang.RT;

public class ResponseArrayHeaderPusher implements ResponseHeaderPusher {

	protected long offset;
	protected String name;
	
	public ResponseArrayHeaderPusher(String name, long offset) {
		this.offset = offset;
		this.name = name;
	}
	
	@Override
	public long knownOffset() {
		return offset;
	}

	@Override
	public String name() {
		return name;
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
			return;
		}
		
		long lp = UNSAFE.getAddress(haddr + Constants.NGX_HTTP_CLOJURE_ARRAY_ELTS_OFFSET);
		if (lp == 0) {
			long code = NginxClojureRT.ngx_array_init(haddr, pool, c, Constants.NGX_HTTP_CLOJURE_TELT_SIZE);
			if (code != Constants.NGX_OK) {
				throw new RuntimeException("can not init ngx array for header, return code:" + code);
			}
		}
		
		lp = NginxClojureRT.ngx_array_push_n(haddr, c);
		if (lp == 0) {
			throw new RuntimeException("can not push ngx array for header");
		}
		
		for (int i = 0; i < c; i++) {
			String val = (String) seq.first();
			seq = seq.next();
			if (val != null) {
				long p = NginxClojureRT.ngx_list_push(h + Constants.NGX_HTTP_CLOJURE_HEADERSO_HEADERS_OFFSET);
				if (p == 0) {
					throw new RuntimeException("can not push ngx list for headers");
				}
				NginxClojureRT.pushNGXInt(p + Constants.NGX_HTTP_CLOJURE_TEL_HASH_OFFSET, 1);
				NginxClojureRT.pushNGXString(p + Constants.NGX_HTTP_CLOJURE_TEL_KEY_OFFSET, name, DEFAULT_ENCODING, pool);
				NginxClojureRT.pushNGXString(p + Constants.NGX_HTTP_CLOJURE_TEL_VALUE_OFFSET, val, DEFAULT_ENCODING, pool);
				UNSAFE.putAddress(lp, p);
				lp += Constants.NGX_HTTP_CLOJURE_TELT_SIZE;
			}
		}
	}



}
