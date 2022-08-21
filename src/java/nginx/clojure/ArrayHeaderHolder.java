/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure;

import static nginx.clojure.MiniConstants.DEFAULT_ENCODING;
import static nginx.clojure.MiniConstants.HEADERS_NAMES;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_ARRAY_ELTS_OFFSET;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_ARRAY_NELTS_OFFSET;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_HEADERSO_HEADERS_OFFSET;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_PTR_SIZE;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_TEL_HASH_OFFSET;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_TEL_KEY_OFFSET;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_TEL_VALUE_OFFSET;
import static nginx.clojure.MiniConstants.NGX_OK;
import static nginx.clojure.NginxClojureRT.UNSAFE;
import static nginx.clojure.NginxClojureRT.fetchNGXInt;
import static nginx.clojure.NginxClojureRT.fetchNGXString;
import static nginx.clojure.NginxClojureRT.ngx_array_destory;
import static nginx.clojure.NginxClojureRT.ngx_array_init;
import static nginx.clojure.NginxClojureRT.ngx_array_push_n;
import static nginx.clojure.NginxClojureRT.ngx_http_clojure_mem_shadow_copy_ngx_str;
import static nginx.clojure.NginxClojureRT.pushNGXInt;
import static nginx.clojure.NginxClojureRT.pushNGXString;

import java.util.Arrays;
import java.util.List;



public class ArrayHeaderHolder extends AbstractHeaderHolder {

	public ArrayHeaderHolder(String name, long offset,  long headersOffset) {
		super(name, offset, headersOffset);
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public void push(long h, long pool, Object v) {
		long haddr = h + offset;
		if (haddr == 0){
			throw new RuntimeException("invalid address for set header array value " + v);
		}
		
		clear(h);
		
		List<String> seq = null;
		if (v == null || v instanceof String) {
			String val = (String) v;
			seq = Arrays.asList(val);
		}else if (v instanceof List) {
			seq = (List) v;
		}else if (v.getClass().isArray()){
			seq = (List)Arrays.asList((Object[])v);
		}
		
		int c = seq.size();
		if (c == 0) {
			return;
		}
		
		long lp = UNSAFE.getAddress(haddr + NGX_HTTP_CLOJURE_ARRAY_ELTS_OFFSET);
		
		if (lp != 0) {
			ngx_array_destory(haddr);
		}

		long code = ngx_array_init(haddr, pool, c, NGX_HTTP_CLOJURE_PTR_SIZE);
		if (code != NGX_OK) {
			throw new RuntimeException("can not init ngx array for header, return code:" + code);
		}
		
		lp = ngx_array_push_n(haddr, c);
		if (lp == 0) {
			throw new RuntimeException("can not push ngx array for header");
		}
		
		long pname = HEADERS_NAMES.get(name);
		for (String val : seq) {
			if (val != null) {
				long p = NginxClojureRT.ngx_list_push(h + NGX_HTTP_CLOJURE_HEADERSO_HEADERS_OFFSET);
				if (p == 0) {
					throw new RuntimeException("can not push ngx list for headers");
				}
				pushNGXInt(p + NGX_HTTP_CLOJURE_TEL_HASH_OFFSET, 1);
				ngx_http_clojure_mem_shadow_copy_ngx_str(pname,  p + NGX_HTTP_CLOJURE_TEL_KEY_OFFSET);
				pushNGXString(p + NGX_HTTP_CLOJURE_TEL_VALUE_OFFSET, val, DEFAULT_ENCODING, pool);
				UNSAFE.putAddress(lp, p);
				lp += NGX_HTTP_CLOJURE_PTR_SIZE;
			}
		}
	}

	@Override
	public void clear(long h) {
		long haddr = h + offset;
		if (haddr == 0){
			throw new RuntimeException("invalid address for clear header array value ");
		}
		long lp = UNSAFE.getAddress(haddr + NGX_HTTP_CLOJURE_ARRAY_ELTS_OFFSET);
		if (lp != 0) {
			int c = fetchNGXInt(haddr+NGX_HTTP_CLOJURE_ARRAY_NELTS_OFFSET);
			for (; c > 0; c--) {
				pushNGXInt(UNSAFE.getAddress(lp) + NGX_HTTP_CLOJURE_TEL_HASH_OFFSET, 0);
				lp += NGX_HTTP_CLOJURE_PTR_SIZE;
			}
			pushNGXInt(haddr+NGX_HTTP_CLOJURE_ARRAY_NELTS_OFFSET, 0);
			UNSAFE.putAddress(haddr + NGX_HTTP_CLOJURE_ARRAY_ELTS_OFFSET, 0);
		}
	}
	
	@Override
	public Object fetch(long h) {
		long haddr = h + offset;
		if (haddr == 0){
			throw new RuntimeException("invalid address for fetch header array ");
		}
		long lp = UNSAFE.getAddress(haddr + NGX_HTTP_CLOJURE_ARRAY_ELTS_OFFSET);
		if (lp == 0) {
			return null;
		}
		int c = fetchNGXInt(haddr+NGX_HTTP_CLOJURE_ARRAY_NELTS_OFFSET);
		if (c == 0) {
			return null;
		}
		long tp;
		if (c == 1) {
			tp = UNSAFE.getAddress(lp);
			if (tp == 0) {
				return null;
			}
			return fetchNGXString(tp+NGX_HTTP_CLOJURE_TEL_VALUE_OFFSET, DEFAULT_ENCODING);
		}
		String[] vals = new String[c];
		for (int i = 0; i < c; i++) {
			tp = UNSAFE.getAddress(lp);
			if (tp == 0) {
				return null;
			}
			vals[i] = fetchNGXString(tp+ NGX_HTTP_CLOJURE_TEL_VALUE_OFFSET, DEFAULT_ENCODING);
			lp += NGX_HTTP_CLOJURE_PTR_SIZE;
		}
		return vals;
	}

	@Override
	public boolean exists(long h) {
		long haddr = h + offset;
		if (haddr == 0){
			throw new RuntimeException("invalid address for fetch header array ");
		}
		long lp = UNSAFE.getAddress(haddr + NGX_HTTP_CLOJURE_ARRAY_ELTS_OFFSET);
		if (lp == 0) {
			return false;
		}
		int c = fetchNGXInt(haddr+NGX_HTTP_CLOJURE_ARRAY_NELTS_OFFSET);
		if (c == 0) {
			return false;
		}
		return true;
	}

}
