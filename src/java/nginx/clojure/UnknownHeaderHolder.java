/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure;

import static nginx.clojure.MiniConstants.BYTE_ARRAY_OFFSET;
import static nginx.clojure.MiniConstants.DEFAULT_ENCODING;
import static nginx.clojure.MiniConstants.HEADERS_NAMES;
import static nginx.clojure.MiniConstants.NGINX_CLOJURE_CORE_CLIENT_HEADER_MAX_LINE_SIZE;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_TEL_HASH_OFFSET;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_TEL_KEY_OFFSET;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_TEL_VALUE_OFFSET;
import static nginx.clojure.NginxClojureRT.fetchNGXString;
import static nginx.clojure.NginxClojureRT.ngx_http_clojure_mem_get_header;
import static nginx.clojure.NginxClojureRT.ngx_http_clojure_mem_shadow_copy_ngx_str;
import static nginx.clojure.NginxClojureRT.ngx_list_push;
import static nginx.clojure.NginxClojureRT.pickByteBuffer;
import static nginx.clojure.NginxClojureRT.pickCharBuffer;
import static nginx.clojure.NginxClojureRT.pushNGXInt;
import static nginx.clojure.NginxClojureRT.pushNGXString;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.LongBuffer;
import java.util.Arrays;
import java.util.List;


public class UnknownHeaderHolder implements NginxHeaderHolder {

	protected String name;
	protected long headersOffset;
	
	public UnknownHeaderHolder(String name, long headersOffset) {
		this.name = name;
		this.headersOffset = headersOffset;
	}
	
	@Override
	public String name() {
		return name;
	}
	
	@Override
	public long knownOffset() {
		return -1;
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
		}else {
			seq = Arrays.asList(String.valueOf(v));
		}
		
		clear(h);
		
		int c = seq.size();
		if (c == 0) {
			return;
		}
		long lpname = 0;
		Long  pname = HEADERS_NAMES.get(name);
		if (pname != null) {
			lpname = pname;
		}
		for (Object val : seq) {
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
					lpname = p + NGX_HTTP_CLOJURE_TEL_KEY_OFFSET;
				}
				pushNGXString(p + NGX_HTTP_CLOJURE_TEL_VALUE_OFFSET, val.toString(), DEFAULT_ENCODING, pool);
			}
		}
	}
	
	@Override
	public void clear(long h) {
		ByteBuffer kbb = HackUtils.encode(name, DEFAULT_ENCODING,  pickByteBuffer());
		int nameLen = kbb.remaining();
		int valuesOffset = NGINX_CLOJURE_CORE_CLIENT_HEADER_MAX_LINE_SIZE + BYTE_ARRAY_OFFSET;
		byte[] array = kbb.array();
		h += headersOffset;
		int c = (int)ngx_http_clojure_mem_get_header(h,  array, BYTE_ARRAY_OFFSET ,  nameLen,  valuesOffset,  kbb.capacity());
		kbb.clear();
		kbb.position(NGINX_CLOJURE_CORE_CLIENT_HEADER_MAX_LINE_SIZE);
		LongBuffer lbb =kbb.order(ByteOrder.nativeOrder()).asLongBuffer();
		
		for (; c > 0; c--) {
			pushNGXInt(lbb.get() + NGX_HTTP_CLOJURE_TEL_HASH_OFFSET, 0);
		}
	}

	@Override
	public Object fetch(long h) {

		ByteBuffer kbb = HackUtils.encode(name, DEFAULT_ENCODING,  pickByteBuffer());
		int nameLen = kbb.remaining();
		int valuesOffset = NGINX_CLOJURE_CORE_CLIENT_HEADER_MAX_LINE_SIZE + BYTE_ARRAY_OFFSET;
		byte[] array = kbb.array();
		int c = (int)ngx_http_clojure_mem_get_header(h + headersOffset,  array, BYTE_ARRAY_OFFSET ,  nameLen,  valuesOffset,  kbb.capacity());
		kbb.clear();
		kbb.position(NGINX_CLOJURE_CORE_CLIENT_HEADER_MAX_LINE_SIZE);
		LongBuffer lbb =kbb.order(ByteOrder.nativeOrder()).asLongBuffer();
		kbb.clear();
		if (c == 0){
			return null;
		}else if (c == 1) {
			kbb.limit(NGINX_CLOJURE_CORE_CLIENT_HEADER_MAX_LINE_SIZE);
			return  fetchNGXString(lbb.get()+ NGX_HTTP_CLOJURE_TEL_VALUE_OFFSET, DEFAULT_ENCODING, kbb ,  pickCharBuffer());
		}else {
			String[] vals = new String[c];
			valuesOffset = 0;
			for (int i = 0; i < c; i++) {
				kbb.clear();
				kbb.limit(NGINX_CLOJURE_CORE_CLIENT_HEADER_MAX_LINE_SIZE);
				vals[i] = fetchNGXString(lbb.get(i)+ NGX_HTTP_CLOJURE_TEL_VALUE_OFFSET, DEFAULT_ENCODING, kbb ,  pickCharBuffer());
			}
			return vals;
		}
	
	}

	@Override
	public long headersOffset() {
		return headersOffset;
	}

	@Override
	public boolean exists(long h) {
		ByteBuffer kbb = HackUtils.encode(name, DEFAULT_ENCODING,  pickByteBuffer());
		int nameLen = kbb.remaining();
		int valuesOffset = NGINX_CLOJURE_CORE_CLIENT_HEADER_MAX_LINE_SIZE + BYTE_ARRAY_OFFSET;
		byte[] array = kbb.array();
		return ngx_http_clojure_mem_get_header(h + headersOffset,  array, BYTE_ARRAY_OFFSET ,  nameLen,  valuesOffset,  valuesOffset + 8) != 0;
	}

}
