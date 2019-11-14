/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.java;

import static nginx.clojure.MiniConstants.BYTE_ARRAY_OFFSET;
import static nginx.clojure.MiniConstants.DEFAULT_ENCODING;
import static nginx.clojure.MiniConstants.KNOWN_REQ_HEADERS;
import static nginx.clojure.MiniConstants.KNOWN_RESP_HEADERS;
import static nginx.clojure.MiniConstants.NGINX_CLOJURE_CORE_CLIENT_HEADER_MAX_LINE_SIZE;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_GET_HEADER_FLAG_HEADERS_OUT;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_GET_HEADER_FLAG_MERGE_KEY;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_HEADERSI_HEADERS_OFFSET;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_HEADERSO_HEADERS_OFFSET;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_REQ_HEADERS_IN_OFFSET;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_REQ_HEADERS_OUT_OFFSET;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_REQ_POOL_OFFSET;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_TEL_KEY_OFFSET;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_TEL_VALUE_OFFSET;
import static nginx.clojure.NginxClojureRT.UNSAFE;
import static nginx.clojure.NginxClojureRT.fetchNGXString;
import static nginx.clojure.NginxClojureRT.ngx_http_clojure_mem_get_headers_items;
import static nginx.clojure.NginxClojureRT.ngx_http_clojure_mem_get_headers_size;
import static nginx.clojure.NginxClojureRT.pickByteBuffer;
import static nginx.clojure.NginxClojureRT.pickCharBuffer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.LongBuffer;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import nginx.clojure.CaseInsensitiveMap;
import nginx.clojure.NginxClojureRT;
import nginx.clojure.NginxHeaderHolder;
import nginx.clojure.NginxSimpleHandler;
import nginx.clojure.NginxSimpleHandler.SimpleEntry;
import nginx.clojure.UnknownHeaderHolder;
import nginx.clojure.java.PickerPoweredIterator.Picker;


@SuppressWarnings({"rawtypes"})
public class JavaLazyHeaderMap implements Map<String, Object>, Iterable  {
	
	protected long headers;
	protected int size;
	protected int flag;
	protected long pool;
	
	//for thread pool mode safe access header map and apply it later
	protected Map<String, Object> safeCache;
	protected Set<String> updatedHeaders;
	
	public JavaLazyHeaderMap(long r, boolean headersOut) {
		this.headers = r
				+ (headersOut ? NGX_HTTP_CLOJURE_REQ_HEADERS_OUT_OFFSET 
						: NGX_HTTP_CLOJURE_REQ_HEADERS_IN_OFFSET );
		this.flag =  NGX_HTTP_CLOJURE_GET_HEADER_FLAG_MERGE_KEY | (headersOut ? NGX_HTTP_CLOJURE_GET_HEADER_FLAG_HEADERS_OUT : 0);
		this.size = (int) ngx_http_clojure_mem_get_headers_size(headers, flag);
		this.pool = UNSAFE.getAddress(r + NGX_HTTP_CLOJURE_REQ_POOL_OFFSET);
	}
	
	public void enableSafeCache(String[] headers) {
		if (headers == DefinedPrefetch.ALL_HEADERS) {
			safeCache = new CaseInsensitiveMap<Object>(this);
		} else {
			safeCache = new CaseInsensitiveMap<Object>();
			for (String h : headers) {
				safeCache.put(h, get(h));
			}
		}
		
		this.size = safeCache.size();
		
		if ((NGX_HTTP_CLOJURE_GET_HEADER_FLAG_HEADERS_OUT & flag) != 0) {
			updatedHeaders = new LinkedHashSet<>();
		}
	}
	
	@Override
	public Iterator iterator() {
		
		if (safeCache != null) {
			return safeCache.entrySet().iterator();
		}
		
		return new PickerPoweredIterator<>(new Picker<Entry<String, Object>>() {
			@Override
			public java.util.Map.Entry<String, Object> pick(int i) {
				return entry(i);
			}
			@Override
			public int size() {
				return size;
			}
		});
	}
	
	protected SimpleEntry<String, Object> entry(int i) {
		if (i >= size) {
			return null;
		}
		
		ByteBuffer bb = pickByteBuffer();
		int valuesOffset = NGINX_CLOJURE_CORE_CLIENT_HEADER_MAX_LINE_SIZE + BYTE_ARRAY_OFFSET;
		int c = (int)ngx_http_clojure_mem_get_headers_items(headers, i,  flag,  bb.array(),  valuesOffset,  bb.capacity());
		bb.position(NGINX_CLOJURE_CORE_CLIENT_HEADER_MAX_LINE_SIZE);
		LongBuffer lbb =bb.order(ByteOrder.nativeOrder()).asLongBuffer();
		bb.clear();
		Object v;
		long tp;
		if (c <= 0){
			throw new IllegalStateException("[JavaLazyHeaderMap] no entry at position : " + i + ", maybe request is released!");
		}else if (c == 1) {
			bb.limit(NGINX_CLOJURE_CORE_CLIENT_HEADER_MAX_LINE_SIZE);
			tp = lbb.get(0);
			v =  fetchNGXString(tp + NGX_HTTP_CLOJURE_TEL_VALUE_OFFSET, DEFAULT_ENCODING,  bb ,  pickCharBuffer());
		}else {
			String[] vals = new String[c];
			tp = lbb.get(0);
			for (int j = 0; j < c; j++) {
				bb.clear();
				bb.limit(NGINX_CLOJURE_CORE_CLIENT_HEADER_MAX_LINE_SIZE);
				vals[j] = fetchNGXString(lbb.get(j)+ NGX_HTTP_CLOJURE_TEL_VALUE_OFFSET, DEFAULT_ENCODING, bb ,  pickCharBuffer());
			}
			v = vals;
		}
		
		//TODO: support setter of entry
		bb.clear();
		return new SimpleEntry<>(fetchNGXString(tp + NGX_HTTP_CLOJURE_TEL_KEY_OFFSET, DEFAULT_ENCODING, bb ,  pickCharBuffer()), v,  NginxSimpleHandler.readOnlyEntrySetter);
	}
	
	protected String key(int i) {
		if (i >= size) {
			return null;
		}
		ByteBuffer bb = pickByteBuffer();
		int valuesOffset = NGINX_CLOJURE_CORE_CLIENT_HEADER_MAX_LINE_SIZE + BYTE_ARRAY_OFFSET;
		int c = (int)ngx_http_clojure_mem_get_headers_items(headers, i,  flag,  bb.array(),  valuesOffset,  valuesOffset + 8);
		bb.position(NGINX_CLOJURE_CORE_CLIENT_HEADER_MAX_LINE_SIZE);
		LongBuffer lbb =bb.order(ByteOrder.nativeOrder()).asLongBuffer();
		bb.clear();
		if (c <= 0){
			throw new IllegalStateException("[JavaLazyHeaderMap] no entry at position : " + i + ", maybe request is released!");
		}
		bb.limit(NGINX_CLOJURE_CORE_CLIENT_HEADER_MAX_LINE_SIZE);
		return  fetchNGXString(lbb.get(0)+ NGX_HTTP_CLOJURE_TEL_KEY_OFFSET, DEFAULT_ENCODING,  bb ,  pickCharBuffer());
	}
	
	protected Object val(int i) {
		if (i >= size) {
			return null;
		}
		ByteBuffer bb = pickByteBuffer();
		int valuesOffset = NGINX_CLOJURE_CORE_CLIENT_HEADER_MAX_LINE_SIZE + BYTE_ARRAY_OFFSET;
		int c = (int)ngx_http_clojure_mem_get_headers_items(headers, i,  flag,  bb.array(),  valuesOffset,  bb.capacity());
		bb.position(NGINX_CLOJURE_CORE_CLIENT_HEADER_MAX_LINE_SIZE);
		LongBuffer lbb =bb.order(ByteOrder.nativeOrder()).asLongBuffer();
		bb.clear();
		Object v;
		if (c <= 0){
			throw new IllegalStateException("[JavaLazyHeaderMap] no entry at position : " + i + ", maybe request is released!");
		}else if (c == 1) {
			bb.limit(NGINX_CLOJURE_CORE_CLIENT_HEADER_MAX_LINE_SIZE);
			v =  fetchNGXString( lbb.get(0)+ NGX_HTTP_CLOJURE_TEL_VALUE_OFFSET, DEFAULT_ENCODING,  bb ,  pickCharBuffer());
		}else {
			String[] vals = new String[c];
			for (int j = 0; j < c; j++) {
				bb.clear();
				bb.limit(NGINX_CLOJURE_CORE_CLIENT_HEADER_MAX_LINE_SIZE);
				vals[j] = fetchNGXString(lbb.get(j)+ NGX_HTTP_CLOJURE_TEL_VALUE_OFFSET, DEFAULT_ENCODING, bb ,  pickCharBuffer());
			}
			v = vals;
		}
		return v;
	}

	@Override
	public boolean containsKey(Object keyObj) {
//		String key = NginxSimpleHandler.normalizeHeaderNameHelper(keyObj);
		if (keyObj == null) {
			return false;
		}
		
		if (safeCache != null) {
			return safeCache.containsKey(keyObj);
		}
		
		NginxHeaderHolder holder = ((NGX_HTTP_CLOJURE_GET_HEADER_FLAG_HEADERS_OUT & flag) != 0 ?
				KNOWN_RESP_HEADERS : KNOWN_REQ_HEADERS)
				.get(keyObj);

		if (holder == null) {
			holder = new UnknownHeaderHolder((String) keyObj,
					(NGX_HTTP_CLOJURE_GET_HEADER_FLAG_HEADERS_OUT & flag) != 0 ?
							NGX_HTTP_CLOJURE_HEADERSO_HEADERS_OFFSET :
							NGX_HTTP_CLOJURE_HEADERSI_HEADERS_OFFSET);
		}

		return holder.exists(headers);
	}


	@Override
	public int size() {
		return size;
	}

	@Override
	public boolean isEmpty() {
		return size == 0;
	}

	@Override
	public boolean containsValue(Object value) {
		
		if (safeCache != null) {
			return safeCache.containsValue(value);
		}
		
		for (int i = 0; i < size; i++) {
			if (value.equals(val(i))) {
				return true;
			}
		}
		return false;
	}

	@Override
	public Object get(Object keyObj) {
		if (keyObj == null) {
			return false;
		}
		
		if (safeCache != null) {
			return safeCache.get(keyObj);
		}
		
		return unsafeGet(keyObj);
	}

	protected Object unsafeGet(Object keyObj) {
		NginxHeaderHolder holder = ((NGX_HTTP_CLOJURE_GET_HEADER_FLAG_HEADERS_OUT & flag) != 0 ?
				KNOWN_RESP_HEADERS : KNOWN_REQ_HEADERS)
				.get(keyObj);

		if (holder == null) {
			holder = new UnknownHeaderHolder((String) keyObj,
					(NGX_HTTP_CLOJURE_GET_HEADER_FLAG_HEADERS_OUT & flag) != 0 ? NGX_HTTP_CLOJURE_HEADERSO_HEADERS_OFFSET
							: NGX_HTTP_CLOJURE_HEADERSI_HEADERS_OFFSET);
		}
		
		return holder.fetch(headers);
	}

	@Override
	public Object put(String key, Object value) {
		if ((NGX_HTTP_CLOJURE_GET_HEADER_FLAG_HEADERS_OUT & flag) != 0) {
			
			if (safeCache != null) {
				updatedHeaders.add(key);
				return safeCache.put(key, value);
			}
			
			return unsafePut(key, value);
		}else {
			throw new UnsupportedOperationException("put request header  not supported now!");
		}
	}

	protected Object unsafePut(String key, Object value) {
		NginxHeaderHolder holder = KNOWN_RESP_HEADERS.get(key);
		if (holder == null) {
			holder = new UnknownHeaderHolder(key,
					(NGX_HTTP_CLOJURE_GET_HEADER_FLAG_HEADERS_OUT & flag) != 0 ? NGX_HTTP_CLOJURE_HEADERSO_HEADERS_OFFSET
							: NGX_HTTP_CLOJURE_HEADERSI_HEADERS_OFFSET);
		}
		Object old = holder.fetch(headers);
		holder.push(headers, pool, value);
		if (old == null) {
			size ++;
		}
		return old;
	}

	@Override
	public Object remove(Object key) {
		if ((NGX_HTTP_CLOJURE_GET_HEADER_FLAG_HEADERS_OUT & flag) == 0) {
			throw new UnsupportedOperationException("remove request header  not supported now!");
		}
		if (key == null) {
			return null;
		}
		
		if (safeCache != null) {
			updatedHeaders.add(key.toString());
			return safeCache.remove(key);
		}
		
		return unsafeRemove(key);
	
	}

	protected Object unsafeRemove(Object key) {
		NginxHeaderHolder holder = KNOWN_RESP_HEADERS.get(key);
		if (holder == null) {
			holder = new UnknownHeaderHolder((String)key,
					(NGX_HTTP_CLOJURE_GET_HEADER_FLAG_HEADERS_OUT & flag) != 0 ? NGX_HTTP_CLOJURE_HEADERSO_HEADERS_OFFSET
							: NGX_HTTP_CLOJURE_HEADERSI_HEADERS_OFFSET);
		}
		Object old = holder.fetch(headers);
		if (old != null) {
			holder.clear(headers);
			size --;
		}
		return old;
	}

	@Override
	public void putAll(Map<? extends String, ?> m) {
		if ((NGX_HTTP_CLOJURE_GET_HEADER_FLAG_HEADERS_OUT & flag) == 0) {
			throw new UnsupportedOperationException("putAll request header  not supported now!");
		}
		for (Entry en : m.entrySet()) {
			put(en.getKey().toString(), en.getValue());
		}
		
		if (safeCache == null) {
			size = (int) ngx_http_clojure_mem_get_headers_size(headers, flag);
		}
	}

	@Override
	public void clear() {
		if ((NGX_HTTP_CLOJURE_GET_HEADER_FLAG_HEADERS_OUT & flag) == 0) {
			throw new UnsupportedOperationException("clear request header  not supported now!");
		}
		
		if (safeCache != null) {
			safeCache.clear();
			size = 0;
			return;
		}
		
		NginxClojureRT.ngx_http_clear_header_and_reset_ctx_phase(headers - NGX_HTTP_CLOJURE_REQ_HEADERS_OUT_OFFSET, 0);
		size = 0;
	}
	
	private class KeySet extends AbstractSet<String> {

		@Override
		public Iterator<String> iterator() {
			return new PickerPoweredIterator<>(new Picker<String>() {
				@Override
				public String pick(int i) {
					return key(i);
				}
				@Override
				public int size() {
					return size;
				}
			});
		}

		@Override
		public int size() {
			return size;
		}
		
	}
	
	private class ValueSet extends AbstractSet<Object> {

		@Override
		public Iterator<Object> iterator() {
			return new PickerPoweredIterator<>(new Picker<Object>() {
				@Override
				public Object pick(int i) {
					return val(i);
				}
				@Override
				public int size() {
					return size;
				}
			});
		}

		@Override
		public int size() {
			return size;
		}
		
	}

	@Override
	public Set<String> keySet() {
		return safeCache != null ? safeCache.keySet() : new KeySet();
	}

	@Override
	public Collection<Object> values() {
		return safeCache != null ? safeCache.values() : new ValueSet();
	}

	private class EntrySet extends AbstractSet<Entry<String, Object>> {

		@Override
		public Iterator<Entry<String, Object>> iterator() {
			return new PickerPoweredIterator<>(new Picker<Entry<String, Object>>() {
				@Override
				public Entry<String, Object> pick(int i) {
					return entry(i);
				}
				@Override
				public int size() {
					return size;
				}
			});
		}
		@Override
		public int size() {
			return size;
		}
		
	}
	
	@Override
	public Set<java.util.Map.Entry<String, Object>> entrySet() {
		return safeCache != null ? safeCache.entrySet() : new EntrySet();
	}


	public void applyDelayed() {
		if (safeCache != null && !updatedHeaders.isEmpty()) {
			for (String header : updatedHeaders) {
				if (safeCache.containsKey(header)) {
					unsafePut(header, safeCache.get(header));
				} else {
					unsafeRemove(header);
				}
			}
		}
	}

}