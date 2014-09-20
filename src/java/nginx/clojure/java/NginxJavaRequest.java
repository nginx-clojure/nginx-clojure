/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.java;

import static nginx.clojure.MiniConstants.BODY;
import static nginx.clojure.MiniConstants.BODY_FETCHER;
import static nginx.clojure.MiniConstants.CHARACTER_ENCODING;
import static nginx.clojure.MiniConstants.CHARACTER_ENCODING_FETCHER;
import static nginx.clojure.MiniConstants.CONTENT_TYPE;
import static nginx.clojure.MiniConstants.CONTENT_TYPE_FETCHER;
import static nginx.clojure.MiniConstants.DEFAULT_ENCODING;
import static nginx.clojure.MiniConstants.HEADERS;
import static nginx.clojure.MiniConstants.QUERY_STRING;
import static nginx.clojure.MiniConstants.QUERY_STRING_FETCHER;
import static nginx.clojure.MiniConstants.REMOTE_ADDR;
import static nginx.clojure.MiniConstants.REMOTE_ADDR_FETCHER;
import static nginx.clojure.MiniConstants.REQUEST_METHOD;
import static nginx.clojure.MiniConstants.REQUEST_METHOD_FETCHER;
import static nginx.clojure.MiniConstants.SCHEME;
import static nginx.clojure.MiniConstants.SCHEME_FETCHER;
import static nginx.clojure.MiniConstants.SERVER_NAME;
import static nginx.clojure.MiniConstants.SERVER_NAME_FETCHER;
import static nginx.clojure.MiniConstants.SERVER_PORT;
import static nginx.clojure.MiniConstants.SERVER_PORT_FETCHER;
import static nginx.clojure.MiniConstants.URI;
import static nginx.clojure.MiniConstants.URI_FETCHER;
import static nginx.clojure.java.Constants.HEADER_FETCHER;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import nginx.clojure.ChannelListener;
import nginx.clojure.NginxClojureRT;
import nginx.clojure.NginxHandler;
import nginx.clojure.NginxHttpServerChannel;
import nginx.clojure.NginxRequest;
import nginx.clojure.NginxSimpleHandler.SimpleEntry;
import nginx.clojure.RequestVarFetcher;
import nginx.clojure.java.PickerPoweredIterator.Picker;

public class NginxJavaRequest implements NginxRequest, Map<String, Object> {

	protected long r;
	NginxHandler handler;
	protected NginxJavaRingHandler ringHandler;
	protected Object[] array;
	protected boolean hijacked = false;
	protected NginxHttpServerChannel channel;
	
	protected volatile boolean released = false;
	
	
	private final  static ChannelListener<NginxJavaRequest> requestListener  = new  ChannelListener<NginxJavaRequest> (){
		@Override
		public void onClose(NginxJavaRequest data) {
			data.released = true;
		}
		
		@Override
		public void onConnect(long status, NginxJavaRequest data) {
		}
	};
	
	public NginxJavaRequest(NginxHandler handler, NginxJavaRingHandler ringHandler, long r, Object[] array) {
		this.r = r;
		this.handler = handler;
		this.array = array;
		this.ringHandler = ringHandler;
	}
	
	@SuppressWarnings("unchecked")
	public NginxJavaRequest(NginxHandler handler, NginxJavaRingHandler ringHandler, long r) {
		//TODO: SSL_CLIENT_CERT
		this(handler, ringHandler, r, new Object[] {
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
		int len = array.length >> 1;
		for (int i = 0; i < len; i++) {
			val(i);
		}
	}
	
	
	protected int index(Object key) {
		for (int i = 0; i < array.length; i+=2){
			if (key == array[i]) {
				return i >> 1;
			}
		}
		return -1;
	}
	
	
	public String key(int i) {
		return (String) array[i << 1];
	}
	
	public Object val(int i) {
		i = (i << 1) + 1;
		Object o = array[i];
		if (o instanceof RequestVarFetcher) {
			if (Thread.currentThread() != NginxClojureRT.NGINX_MAIN_THREAD) {
				throw new IllegalAccessError("fetching lazy value of " + array[i] + " in LazyRequestMap can only be called in main thread, please pre-access it in main thread OR call LazyRequestMap.prefetchAll() first in main thread");
			}
			RequestVarFetcher rf = (RequestVarFetcher) o;
			array[i] = null;
			Object rt = rf.fetch(r, DEFAULT_ENCODING);
			array[i] = rt;
			return rt;
		}
		return o;
	}

	public SimpleEntry<String, Object> entry(int i) {
		return new SimpleEntry<String, Object>(key(i), val(i));
	}
	

	
	public long nativeRequest() {
		return r;
	}

	@Override
	public boolean containsKey(Object key) {
		return index(key) != -1;
	}

	@Override
	public int size() {
		return array.length >> 1;
	}

	@Override
	public boolean isEmpty() {
		return array.length == 0;
	}

	@Override
	public boolean containsValue(Object value) {
		int size = size();
		for (int i = 0; i < size; i++) {
			if (value.equals(val(i))) {
				return true;
			}
		}
		return false;
	}

	@Override
	public Object get(Object key) {
		int i = index(key);
		return i == -1 ? null : val(i);
	}

	@Override
	public Object put(String key, Object val) {
		int i = index(key);
		if (i != -1) {
			i = (i << 1) + 1;
			Object old = array[i];
			array[i] = val;
			return old;
		}
		Object[] newArray = new Object[array.length + 2];
		System.arraycopy(array, 0, newArray, 0, array.length);
		newArray[array.length] = key;
		newArray[array.length+1] = val;
		this.array = newArray;
		return null;
	}

	@Override
	public Object remove(Object key) {
		int i = index(key);
		if (i == -1) {
			return null;
		}else {
			Object old = val(i);
			i <<= 1;
			Object[] newArray = new Object[array.length - 2];
			if (i > 0) {
				System.arraycopy(array, 0, newArray, 0, i);
			}
			System.arraycopy(array, i + 2, newArray, i, array.length - i - 2);
			this.array = newArray;
			return old;
		}
	
	}

	@Override
	public void putAll(Map<? extends String, ? extends Object> m) {
		for (Entry<? extends String, ? extends Object> entry : m.entrySet()) {
			put(entry.getKey(), entry.getValue());
		}
	}

	@Override
	public void clear() {
		this.array = new Object[0];
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
					return array.length >> 1;
				}
			});
		}

		@Override
		public int size() {
			return  array.length >> 1;
		}
		
	}
	
	private class ValueSet extends AbstractSet<Object> {

		@Override
		public Iterator<Object> iterator() {
			return new PickerPoweredIterator<Object>(new Picker<Object>() {
				@Override
				public Object pick(int i) {
					return val(i);
				}
				@Override
				public int size() {
					return array.length >> 1;
				}
			});
		}

		@Override
		public int size() {
			return array.length >> 1;
		}
	}
		
	
	@Override
	public Set<String> keySet() {
		return new KeySet();
	}

	@Override
	public Collection<Object> values() {
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
					return array.length >> 1;
				}
			});
		}
		@Override
		public int size() {
			return array.length >> 1;
		}
		
	}

	@Override
	public Set<java.util.Map.Entry<String, Object>> entrySet() {
		return new EntrySet();
	}

	@Override
	public NginxHandler handler() {
		return handler;
	}

	@Override
	public boolean isHijacked() {
		return hijacked;
	}

	@Override
	public NginxHttpServerChannel channel() {
		if (!hijacked) {
			NginxClojureRT.UNSAFE.throwException(new IllegalAccessException("not hijacked!"));
		}
		return channel;
	}

	@Override
	public boolean isReleased() {
		return released;
	}
}
