/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.clj;

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

import java.util.Iterator;

import nginx.clojure.RequestKnownHeaderFetcher;
import clojure.lang.AFn;
import clojure.lang.ASeq;
import clojure.lang.Counted;
import clojure.lang.IMapEntry;
import clojure.lang.IPersistentCollection;
import clojure.lang.IPersistentMap;
import clojure.lang.ISeq;
import clojure.lang.MapEntry;
import clojure.lang.Obj;
import clojure.lang.PersistentArrayMap;

public class LazyHeaderMap extends AFn implements IPersistentMap  {
	
	private long headersPointer;
	private int size;
	
	public LazyHeaderMap(long headersPointer) {
		this.headersPointer = headersPointer;
		this.size = (int)ngx_http_clojure_mem_get_list_size(headersPointer + NGX_HTTP_CLOJURE_HEADERSI_HEADERS_OFFSET);
	}
	
	@Override
	public Iterator<MapEntry> iterator() {
		return new Iterator<MapEntry>() {
			int i = 0;
			@Override
			public boolean hasNext() {
				return i < size -1;
			}

			@Override
			public MapEntry next() {
				return element(i++);
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException("remove not supported now!");
			}
			
		};
	}
	
	public MapEntry element(int i) {
		if (i >= size) {
			return null;
		}
		long itemAddr = ngx_http_clojure_mem_get_list_item(headersPointer + NGX_HTTP_CLOJURE_HEADERSI_HEADERS_OFFSET, i);
//		System.out.println("LazyHeaderMap: i = " + i + ", addr:" + itemAddr + ", total=" + size);
		String key = fetchNGXString(itemAddr + NGX_HTTP_CLOJURE_TEL_KEY_OFFSET, DEFAULT_ENCODING).toLowerCase();
		String val = fetchNGXString(itemAddr + NGX_HTTP_CLOJURE_TEL_VALUE_OFFSET, DEFAULT_ENCODING);
//		System.out.println("LazyHeaderMap: i = " + i + ", key:" + key + ", val:" + val);
		return new MapEntry(key, val);
	}

	@Override
	public boolean containsKey(Object keyObj) {
		String key = NginxClojureHandler.normalizeHeaderName(keyObj);
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
	public IMapEntry entryAt(Object key) {
		Object val = valAt(key);
		return val == null ? null : new MapEntry(key, val);
	}

	@Override
	public int count() {
		return size;
	}

	@Override
	public IPersistentCollection cons(Object o) {
		throw new UnsupportedOperationException("cons not supported now!");
	}

	@Override
	public IPersistentCollection empty() {
		//TODO: empty by jni
		return PersistentArrayMap.EMPTY;
	}

	@Override
	public boolean equiv(Object o) {
		return o == this;
	}

	static class LazyHeaderSeq extends ASeq implements Counted{
		
		LazyHeaderMap h;
		int i;
		
		public LazyHeaderSeq(LazyHeaderMap h, int i) {
			this.h = h;
			this.i = i;
		}
		
		@Override
		public Object first() {
			return h.element(i);
		}

		@Override
		public ISeq next() {
			if (i < h.size -1) {
				return new LazyHeaderSeq(h, i+1);
			}
			return null;
		}

		@Override
		public Obj withMeta(IPersistentMap meta) {
			throw new UnsupportedOperationException("withMeta not supported now!");
		}
		
		@Override
		public int count() {
			return h.size - i;
		}
	}
	
	@Override
	public ISeq seq() {
		return new LazyHeaderSeq(this, 0);
	}

	@Override
	public Object valAt(Object keyObj) {
		if (keyObj == null) {
			return null;
		}
		String key = NginxClojureHandler.normalizeHeaderName(keyObj);
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
	public Object valAt(Object key, Object notFound) {
		Object val = valAt(key);
		return val == null ? notFound : val;
	}

	@Override
	public IPersistentMap assoc(Object key, Object val) {
		throw new UnsupportedOperationException("assoc not supported now!");
	}

	@Override
	public IPersistentMap assocEx(Object key, Object val) {
		throw new UnsupportedOperationException("assocEx not supported now!");
	}

	@Override
	public IPersistentMap without(Object key) {
		throw new UnsupportedOperationException("without not supported now!");
	}
	
	@Override
	public  Object invoke(Object key) {
		return valAt(key);
	}

}
