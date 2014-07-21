/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.java;

import static nginx.clojure.MiniConstants.BYTE_ARRAY_OFFSET;
import static nginx.clojure.MiniConstants.DEFAULT_ENCODING;
import static nginx.clojure.MiniConstants.KNOWN_REQ_HEADERS;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_HEADERSI_COOKIE_OFFSET;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_HEADERSI_HEADERS_OFFSET;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_REQ_HEADERS_IN_OFFSET;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_TEL_KEY_OFFSET;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_TEL_VALUE_OFFSET;
import static nginx.clojure.NginxClojureRT.UNSAFE;
import static nginx.clojure.NginxClojureRT.fetchNGXString;
import static nginx.clojure.NginxClojureRT.ngx_http_clojure_mem_get_header;
import static nginx.clojure.NginxClojureRT.ngx_http_clojure_mem_get_list_item;
import static nginx.clojure.NginxClojureRT.ngx_http_clojure_mem_get_list_size;
import static nginx.clojure.NginxClojureRT.ngx_http_clojure_mem_get_obj_addr;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import nginx.clojure.NginxSimpleHandler.SimpleEntry;
import nginx.clojure.NginxSimpleResponse;
import nginx.clojure.RequestKnownHeaderFetcher;
import nginx.clojure.java.PickerPoweredIterator.Picker;


public class JavaLazyHeaderMap implements Map<String, Object>, Iterable<Entry<String, Object>>  {
	
	private long headersPointer;
	private int size;
	
	public JavaLazyHeaderMap(long headersPointer) {
		this.headersPointer = headersPointer;
		this.size = (int)ngx_http_clojure_mem_get_list_size(headersPointer + NGX_HTTP_CLOJURE_HEADERSI_HEADERS_OFFSET);
	}
	
	@Override
	public Iterator<Entry<String, Object>> iterator() {
		return new PickerPoweredIterator<Map.Entry<String,Object>>(new Picker<Entry<String, Object>>() {
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
	
	public SimpleEntry entry(int i) {
		if (i >= size) {
			return null;
		}
		long itemAddr = ngx_http_clojure_mem_get_list_item(headersPointer + NGX_HTTP_CLOJURE_HEADERSI_HEADERS_OFFSET, i);
//		System.out.println("LazyHeaderMap: i = " + i + ", addr:" + itemAddr + ", total=" + size);
		String key = fetchNGXString(itemAddr + NGX_HTTP_CLOJURE_TEL_KEY_OFFSET, DEFAULT_ENCODING).toLowerCase();
		String val = fetchNGXString(itemAddr + NGX_HTTP_CLOJURE_TEL_VALUE_OFFSET, DEFAULT_ENCODING);
//		System.out.println("LazyHeaderMap: i = " + i + ", key:" + key + ", val:" + val);
		return new SimpleEntry(key, val);
	}
	
	public String key(int i) {
		if (i >= size) {
			return null;
		}
		long itemAddr = ngx_http_clojure_mem_get_list_item(headersPointer + NGX_HTTP_CLOJURE_HEADERSI_HEADERS_OFFSET, i);
		return fetchNGXString(itemAddr + NGX_HTTP_CLOJURE_TEL_KEY_OFFSET, DEFAULT_ENCODING).toLowerCase();
	}
	
	public String val(int i) {
		if (i >= size) {
			return null;
		}
		long itemAddr = ngx_http_clojure_mem_get_list_item(headersPointer + NGX_HTTP_CLOJURE_HEADERSI_HEADERS_OFFSET, i);
		return fetchNGXString(itemAddr + NGX_HTTP_CLOJURE_TEL_VALUE_OFFSET, DEFAULT_ENCODING);
	}

	@Override
	public boolean containsKey(Object keyObj) {
		String key = NginxSimpleResponse.headerNameToNormalized(keyObj);
		if (key == null) {
			return false;
		}
		Long p = KNOWN_REQ_HEADERS.get(key);
		if (p != null && p.longValue() != -1) {
			return true;
		}
		byte[] kbs = key.toString().getBytes();
		return 0 != ngx_http_clojure_mem_get_header(headersPointer, ngx_http_clojure_mem_get_obj_addr(kbs) + BYTE_ARRAY_OFFSET , kbs.length);
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
			return null;
		}
		String key = NginxSimpleResponse.headerNameToNormalized(keyObj);
		if (key == null) {
			return null;
		}
		Long p = KNOWN_REQ_HEADERS.get(key);
		String val = null;
		if (p != null && p.longValue() != -1) {
			if (p.longValue() == NGX_HTTP_CLOJURE_HEADERSI_COOKIE_OFFSET) {
				val = (String)RequestKnownHeaderFetcher.cookieFetcher.fetch(headersPointer - NGX_HTTP_CLOJURE_REQ_HEADERS_IN_OFFSET, DEFAULT_ENCODING);
			}else {
				long hp = UNSAFE.getAddress(headersPointer + p.longValue());
				if (hp != 0) {
					val = fetchNGXString(hp + NGX_HTTP_CLOJURE_TEL_VALUE_OFFSET, DEFAULT_ENCODING);
				}
			}
		}else {
			byte[] kbs = key.getBytes();
			long hp = ngx_http_clojure_mem_get_header(headersPointer, ngx_http_clojure_mem_get_obj_addr(kbs) + BYTE_ARRAY_OFFSET , kbs.length);
			if (hp != 0) {
				val = fetchNGXString(hp + NGX_HTTP_CLOJURE_TEL_VALUE_OFFSET, DEFAULT_ENCODING);
			}
		}
		return val;
	
	}

	@Override
	public Object put(String key, Object value) {
		throw new UnsupportedOperationException("put not supported now!");
	}

	@Override
	public Object remove(Object key) {
		throw new UnsupportedOperationException("remove not supported now!");
	}

	@Override
	public void putAll(Map<? extends String, ? extends Object> m) {
		throw new UnsupportedOperationException("putAll not supported now!");
	}

	@Override
	public void clear() {
		throw new UnsupportedOperationException("clear not supported now!");
	}
	
	private class KeySet extends AbstractSet<String> {

		@Override
		public Iterator<String> iterator() {
			return new PickerPoweredIterator<String>(new Picker<String>() {
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
	
	private class ValueSet extends AbstractSet<String> {

		@Override
		public Iterator<String> iterator() {
			return new PickerPoweredIterator<String>(new Picker<String>() {
				@Override
				public String pick(int i) {
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
		return new KeySet();
	}

	@Override
	public Collection values() {
		return new ValueSet();
	}

	private class EntrySet extends AbstractSet<Entry<String, Object>> {

		@Override
		public Iterator<Entry<String, Object>> iterator() {
			return new PickerPoweredIterator<Entry<String, Object>>(new Picker<Entry<String, Object>>() {
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
		return new EntrySet();
	}

}