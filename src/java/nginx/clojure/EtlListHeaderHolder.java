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
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_TEL_NEXT_OFFSET;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;



public class EtlListHeaderHolder extends AbstractHeaderHolder {

	public EtlListHeaderHolder(String name, long offset,  long headersOffset) {
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
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public void push(long h, long pool, Object v) {		
		
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
			clear(h);
			return;
		}
		
		long lp = h + offset;
		long p = UNSAFE.getAddress(lp);
		long pname = HEADERS_NAMES.get(name);
		
		if (p == 0) {
			for (String val : seq) {
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
			for (String val : seq) {
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
	public void clear(long h) {
		long p = UNSAFE.getAddress(h + offset);
		if (p != 0) {
			NginxClojureRT.pushNGXInt(p + NGX_HTTP_CLOJURE_TEL_HASH_OFFSET, 0);
			UNSAFE.putAddress(h + offset, 0);
		}
	}
	
	@Override
	public Object fetch(long h) {
		long p = UNSAFE.getAddress(h + offset);
		if (p == 0) {
			return null;
		}
		
		ArrayList<String> list = new ArrayList<String>(2);
		while (p != 0) {
			list.add(fetchNGXString(p + NGX_HTTP_CLOJURE_TEL_VALUE_OFFSET , DEFAULT_ENCODING));
			p = UNSAFE.getAddress(p + NGX_HTTP_CLOJURE_TEL_NEXT_OFFSET);
		}
		return list.size() == 1 ? list.get(0) : list.toArray(new String[list.size()]);
	}

	@Override
	public boolean exists(long h) {
		return h != 0 && UNSAFE.getAddress(h + offset) != 0;
	}

}
