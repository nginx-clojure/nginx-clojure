package nginx.clojure;

import java.util.Iterator;

import clojure.lang.AFn;
import clojure.lang.IMapEntry;
import clojure.lang.IPersistentCollection;
import clojure.lang.IPersistentMap;
import clojure.lang.ISeq;
import clojure.lang.MapEntry;
import static nginx.clojure.MemoryUtil.*;
import static nginx.clojure.Constants.*;

public class LazyHeaderMap extends AFn implements IPersistentMap  {

	private long headersPointer;
	
	public LazyHeaderMap(long headersPointer) {
		this.headersPointer = headersPointer;
	}
	
	@Override
	public Iterator iterator() {
		throw new UnsupportedOperationException("iterator not supported now!");
	}

	@Override
	public boolean containsKey(Object key) {
		if (key == null) {
			return false;
		}
		Long p = KNOWN_REQ_HEADERS.get(key);
		if (p != null && p.longValue() != -1) {
			return true;
		}
		byte[] kbs = key.toString().getBytes();
		return 0 != ngx_http_clojure_mem_get_header(headersPointer, ngx_http_clojure_mem_get_obj_attr(kbs) + BYTE_ARRAY_OFFSET , kbs.length);
	}

	@Override
	public IMapEntry entryAt(Object key) {
		Object val = valAt(key);
		return val == null ? null : new MapEntry(key, val);
	}

	@Override
	public int count() {
		throw new UnsupportedOperationException("count not supported now!");
	}

	@Override
	public IPersistentCollection cons(Object o) {
		throw new UnsupportedOperationException("cons not supported now!");
	}

	@Override
	public IPersistentCollection empty() {
		throw new UnsupportedOperationException("empty not supported now!");
	}

	@Override
	public boolean equiv(Object o) {
		return false;
	}

	@Override
	public ISeq seq() {
		throw new UnsupportedOperationException("seq not supported now!");
	}

	@Override
	public Object valAt(Object key) {
		if (key == null) {
			return null;
		}
		Long p = KNOWN_REQ_HEADERS.get(key);
		String val = null;
		if (p != null && p.longValue() != -1) {
			if (p.longValue() == NGX_HTTP_CLOJURE_HEADERSI_COOKIE_OFFSET) {
				throw new UnsupportedOperationException("cookie not supported now!");
			}else {
				val = fetchNGXString(UNSAFE.getAddress(headersPointer + p.longValue()) + NGX_HTTP_CLOJURE_TELT_VALUE_OFFSET, DEFAULT_ENCODING);
			}
		}else {
			byte[] kbs = key.toString().getBytes();
			long hp = ngx_http_clojure_mem_get_header(headersPointer, ngx_http_clojure_mem_get_obj_attr(kbs) + BYTE_ARRAY_OFFSET , kbs.length);
			if (hp == 0){
				val = null;
			}else {
				val = fetchNGXString(hp + NGX_HTTP_CLOJURE_TELT_VALUE_OFFSET, DEFAULT_ENCODING);
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
