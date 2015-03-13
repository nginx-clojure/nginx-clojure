/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.clj;

import static nginx.clojure.MiniConstants.BODY_FETCHER;
import static nginx.clojure.MiniConstants.CHARACTER_ENCODING_FETCHER;
import static nginx.clojure.MiniConstants.CONTENT_TYPE_FETCHER;
import static nginx.clojure.MiniConstants.DEFAULT_ENCODING;
import static nginx.clojure.MiniConstants.QUERY_STRING_FETCHER;
import static nginx.clojure.MiniConstants.REMOTE_ADDR_FETCHER;
import static nginx.clojure.MiniConstants.SCHEME_FETCHER;
import static nginx.clojure.MiniConstants.SERVER_NAME_FETCHER;
import static nginx.clojure.MiniConstants.SERVER_PORT_FETCHER;
import static nginx.clojure.MiniConstants.URI_FETCHER;
import static nginx.clojure.clj.Constants.BODY;
import static nginx.clojure.clj.Constants.CHARACTER_ENCODING;
import static nginx.clojure.clj.Constants.CONTENT_TYPE;
import static nginx.clojure.clj.Constants.HEADERS;
import static nginx.clojure.clj.Constants.HEADER_FETCHER;
import static nginx.clojure.clj.Constants.QUERY_STRING;
import static nginx.clojure.clj.Constants.REMOTE_ADDR;
import static nginx.clojure.clj.Constants.REQUEST_METHOD;
import static nginx.clojure.clj.Constants.REQUEST_METHOD_FETCHER;
import static nginx.clojure.clj.Constants.SCHEME;
import static nginx.clojure.clj.Constants.SERVER_NAME;
import static nginx.clojure.clj.Constants.SERVER_PORT;
import static nginx.clojure.clj.Constants.URI;

import java.util.Iterator;
import java.util.Map;

import nginx.clojure.ChannelListener;
import nginx.clojure.NginxClojureRT;
import nginx.clojure.NginxHandler;
import nginx.clojure.NginxHttpServerChannel;
import nginx.clojure.NginxRequest;
import nginx.clojure.RequestVarFetcher;
import clojure.lang.AFn;
import clojure.lang.ASeq;
import clojure.lang.Counted;
import clojure.lang.IMapEntry;
import clojure.lang.IPersistentCollection;
import clojure.lang.IPersistentMap;
import clojure.lang.IPersistentVector;
import clojure.lang.ISeq;
import clojure.lang.MapEntry;
import clojure.lang.Obj;
import clojure.lang.RT;
import clojure.lang.Util;

public   class LazyRequestMap extends AFn  implements NginxRequest, IPersistentMap {
	
	protected long r;
	protected Object[] array;
	protected NginxHandler handler;
	protected NginxHttpServerChannel channel;
	protected byte[] hijackTag;
	protected int phase = -1;
	protected volatile boolean released = false;
	
	public final static LazyRequestMap EMPTY_MAP = new LazyRequestMap(null, 0, null, new Object[0]);
	
	private final  static ChannelListener<LazyRequestMap> requestListener  = new  ChannelListener<LazyRequestMap> (){
		@Override
		public void onClose(LazyRequestMap data) {
			data.released = true;
			if (NginxClojureRT.log.isDebugEnabled()) {
				NginxClojureRT.log.debug("#%d: request %s released!", data.r, data.valAt(URI));
			}
		}
		
		@Override
		public void onConnect(long status, LazyRequestMap data) {
		}
	};
	
	public LazyRequestMap(NginxHandler handler, long r, byte[] hijackTag, Object[] array) {
		this.handler = handler;
		this.r = r;
		this.array = array;
		this.hijackTag = hijackTag;
		if (r != 0) {
			NginxClojureRT.ngx_http_cleanup_add(r, requestListener, this);
		}
	}
	
	@SuppressWarnings("unchecked")
	public LazyRequestMap(NginxHandler handler, long r) {
		//TODO: SSL_CLIENT_CERT
		this(handler, r, new byte[]{0}, new Object[] {
				URI, URI_FETCHER,
				BODY, BODY_FETCHER,
				HEADERS, HEADER_FETCHER,
				
				SERVER_PORT,SERVER_PORT_FETCHER,
				SERVER_NAME, SERVER_NAME_FETCHER,
				REMOTE_ADDR, REMOTE_ADDR_FETCHER,
				
				QUERY_STRING, QUERY_STRING_FETCHER,
				SCHEME, SCHEME_FETCHER,
				REQUEST_METHOD, REQUEST_METHOD_FETCHER,
				CONTENT_TYPE, CONTENT_TYPE_FETCHER,
				CHARACTER_ENCODING, CHARACTER_ENCODING_FETCHER,
		});
	}
	
	public void prefetchAll() {
		int len = count();
		for (int i = 0; i < len; i++) {
			element(i*2);
		}
	}
	
	
	protected int index(Object key) {
		for (int i = 0; i < array.length; i+=2){
			if (key == array[i]) {
				return i;
			}
		}
		return -1;
	}
	

	@Override
	public Iterator iterator() {
		return new Iterator<MapEntry>() {
			
			int i = 0;

			@Override
			public boolean hasNext() {
				return i < array.length -2;
			}

			@Override
			public MapEntry next() {
				return new MapEntry(array[i++], array[i++]);
			}

			@Override
			public void remove() {
				throw Util.runtimeException("remove not supported");
			}
		};
	}

	@Override
	public IMapEntry entryAt(Object key) {
		Object v = valAt(key);
		if (v == null) {
			return null;
		}
		return new MapEntry(key, v);
	}

	@Override
	public int count() {
		return array.length/2;
	}

	@Override
	public IPersistentCollection cons(Object o) {
		if (o instanceof Map.Entry) {
			Map.Entry e = (Map.Entry) o;

			return assoc(e.getKey(), e.getValue());
		} else if (o instanceof IPersistentVector) {
			IPersistentVector v = (IPersistentVector) o;
			if (v.count() != 2)
				throw new IllegalArgumentException(
						"Vector arg to map conj must be a pair");
			return assoc(v.nth(0), v.nth(1));
		}

		IPersistentMap ret = this;
		for (ISeq es = RT.seq(o); es != null; es = es.next()) {
			Map.Entry e = (Map.Entry) es.first();
			ret = ret.assoc(e.getKey(), e.getValue());
		}
		return ret;
	}

	@Override
	public IPersistentCollection empty() {
		return EMPTY_MAP;
	}

	@Override
	public boolean equiv(Object o) {
		return o == this;
	}

	
	static class ArrayMapSeq extends ASeq implements Counted{
		final int i;
		final LazyRequestMap reqMap;

		ArrayMapSeq(LazyRequestMap reqMap, int i){
			this.reqMap = reqMap;
			this.i = i;
		}

		public ArrayMapSeq(IPersistentMap meta, LazyRequestMap reqMap, int i){
			super(meta);
			this.reqMap = reqMap;
			this.i = i;
		}

		public Object first(){
			return new MapEntry(reqMap.array[i],reqMap.element(i));
		}

		public ISeq next(){
			if(i + 2 < reqMap.array.length)
				return new ArrayMapSeq(reqMap, i + 2);
			return null;
		}

		public int count(){
			return (reqMap.array.length - i) / 2;
		}

		public Obj withMeta(IPersistentMap meta){
			return new ArrayMapSeq(meta, reqMap, i);
		}
	}
	
	@Override
	public ISeq seq() {
		return new ArrayMapSeq(this, 0);
	}

	protected Object element(int i) {
		Object o = array[i+1];
		if (o instanceof RequestVarFetcher) {
			if (released) {
				return null;
			}
			if (Thread.currentThread() != NginxClojureRT.NGINX_MAIN_THREAD) {
				throw new IllegalAccessError("fetching lazy value of " + array[i] + " in LazyRequestMap can only be called in main thread, please pre-access it in main thread OR call LazyRequestMap.prefetchAll() first in main thread");
			}
			RequestVarFetcher rf = (RequestVarFetcher) o;
			array[i+1] = null;
			Object rt = rf.fetch(r, DEFAULT_ENCODING);
			array[i+1] = rt;
//			System.out.println("LazyRequestMap, key=" + rt);
			return rt;
		}
//		System.out.println("LazyRequestMap, key=" + array[i] + ", val=" + o);
		return o;
	}
	
	@Override
	public Object valAt(Object key) {
		int i = index(key);
		if (i == -1) {
			return null;
		}
		return element(i);
	}

	@Override
	public Object valAt(Object key, Object notFound) {
		Object val = valAt(key);
		return val == null ? notFound : val;
	}

	@Override
	public IPersistentMap assoc(Object key, Object val) {
		int i = index(key);
		if (i != -1) {
			array[i+1] = val;
			return this;
		}
		Object[] newArray = new Object[array.length + 2];
		System.arraycopy(array, 0, newArray, 0, array.length);
		newArray[array.length] = key;
		newArray[array.length+1] = val;
		return new LazyRequestMap(handler, r,  this.hijackTag, newArray);
	}

	@Override
	public IPersistentMap assocEx(Object key, Object val) {
		Object old = valAt(key);
		if (old != null) {
			 throw Util.runtimeException("Key already present");
		}
		return assoc(key, val);
	}

	@Override
	public IPersistentMap without(Object key) {
		int i = index(key);
		if (i == -1) {
			return this;
		}else {
			if (array.length == 2) {
				return EMPTY_MAP;
			}
			Object[] newArray = new Object[array.length - 2];
			if (i > 0) {
				System.arraycopy(array, 0, newArray, 0, i);
			}
			System.arraycopy(array, i + 2, newArray, i, array.length - i - 2);
			return new LazyRequestMap(handler, r , this.hijackTag, newArray);
		}
	}
	
	public long nativeRequest() {
		return r;
	}

	@Override
	public boolean containsKey(Object key) {
		return index(key) != -1;
	}
	
	@Override
	public Object invoke(Object key) {
		return valAt(key);
	}
	
	@Override
	public Object invoke(Object key, Object notFound) {
		return valAt(key, notFound);
	}

	
	@Override
	public NginxHandler handler() {
		return handler;
	}
	
	@Override
	public NginxHttpServerChannel channel() {
		if (hijackTag == null || hijackTag[0] == 0) {
			NginxClojureRT.UNSAFE.throwException(new IllegalAccessException("not hijacked!"));
		}
		return channel;
	}

	@Override
	public boolean isHijacked() {
		return hijackTag != null && hijackTag[0] == 1;
	}

	@Override
	public boolean isReleased() {
		return released;
	}

	@Override
	public int phase() {
		return phase;
	}
	
	protected LazyRequestMap phase(int phase) {
		this.phase = phase;
		return this;
	}
	
	@Override
	public String toString() {
		return String.format("request {id : %d,  uri: %s}", r, element(0));
	}
}