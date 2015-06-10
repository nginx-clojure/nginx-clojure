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

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import nginx.clojure.ChannelListener;
import nginx.clojure.MessageListener;
import nginx.clojure.MiniConstants;
import nginx.clojure.NginxClojureRT;
import nginx.clojure.NginxHandler;
import nginx.clojure.NginxHttpServerChannel;
import nginx.clojure.NginxRequest;
import nginx.clojure.NginxSimpleHandler;
import nginx.clojure.NginxSimpleHandler.SimpleEntry;
import nginx.clojure.RawMessageListener;
import nginx.clojure.RequestVarFetcher;
import nginx.clojure.java.PickerPoweredIterator.Picker;
import nginx.clojure.net.NginxClojureAsynSocket;

public class NginxJavaRequest implements NginxRequest, Map<String, Object> {

	protected long r;
	NginxHandler handler;
	protected NginxJavaRingHandler ringHandler;
	protected Object[] array;
	protected boolean hijacked = false;
	protected NginxHttpServerChannel channel;
	protected int phase = -1;
	protected volatile boolean released = false;
	protected List<java.util.AbstractMap.SimpleEntry<Object, ChannelListener<Object>>> listeners;
	
	
	public final  static ChannelListener<NginxRequest> requestListener  = new RawMessageListener<NginxRequest>(){
		@Override
		public void onClose(NginxRequest req) {
			if (req.isReleased()) {
				return;
			}
			req.tagReleased();
			if (NginxClojureRT.log.isDebugEnabled()) {
				NginxClojureRT.log.debug("#%d: request %s onClose!", req.nativeRequest(), req.uri());
			}
			List<java.util.AbstractMap.SimpleEntry<Object, ChannelListener<Object>>> listeners = req.listeners();
			if (listeners != null) {
				for (int i = listeners.size() - 1; i > -1; i--) {
					java.util.AbstractMap.SimpleEntry<Object, ChannelListener<Object>> en = listeners.get(i);
					try {
						en.getValue().onClose(en.getKey());
					}catch(Throwable e) {
						NginxClojureRT.log.error(String.format("#%d: onClose Error!", req.nativeRequest()), e);
					}
				}
			}
		}
		
		public void onClose(NginxRequest req, long message) {
			if (req.isReleased()) {
				return;
			}
			req.tagReleased();
			int size = (int) (( message >> 48 ) & 0xffff) - 2;
			long address = message << 16 >> 16;
			int status = 0;
			if (size >= 0) {
				status = (0xffff & (NginxClojureRT.UNSAFE.getByte(NginxClojureRT.UNSAFE.getAddress(address)) << 8))
						| (0xff & NginxClojureRT.UNSAFE.getByte(NginxClojureRT.UNSAFE.getAddress(address)+1));
			}
			
			if (NginxClojureRT.log.isDebugEnabled()) {
				NginxClojureRT.log.debug("#%d: request %s onClose2, status=%d", req.nativeRequest(), req.uri(), status);
			}
			List<java.util.AbstractMap.SimpleEntry<Object, ChannelListener<Object>>> listeners = req.listeners();
			if (listeners != null) {
				ByteBuffer bb = NginxClojureRT.pickByteBuffer();
				CharBuffer cb = NginxClojureRT.pickCharBuffer();
				String txt = null;
				
				if (size > 0) {
					txt = NginxClojureRT.fetchStringValidPart(address, 2, size, MiniConstants.DEFAULT_ENCODING, bb, cb);
					int invalidNum = bb.remaining();
					if (NginxClojureRT.log.isDebugEnabled()) {
						NginxClojureRT.getLog().debug("onClose fetchStringValidPart : %d", invalidNum);
					}
					NginxClojureRT.UNSAFE.putAddress(address, NginxClojureRT.UNSAFE.getAddress(address) - invalidNum);
				}
				
				for (int i = listeners.size() - 1; i > -1; i--) {
					java.util.AbstractMap.SimpleEntry<Object, ChannelListener<Object>> en = listeners.get(i);
					try {
						ChannelListener<Object> l = en.getValue();
						if (l instanceof MessageListener) {
							((MessageListener) l).onClose(en.getKey(), status, txt);
						}
					}catch(Throwable e) {
						NginxClojureRT.log.error(String.format("#%d: onWrite Error!", req.nativeRequest()), e);
					}
				}
			}
		}

		@Override
		public void onConnect(long status, NginxRequest req) {
			if (NginxClojureRT.log.isDebugEnabled()) {
				NginxClojureRT.log.debug("#%d: request %s onConnect, status=%d", req.nativeRequest(), req.uri(), status);
			}
			List<java.util.AbstractMap.SimpleEntry<Object, ChannelListener<Object>>> listeners = req.listeners();
			if (listeners != null) {
				for (int i = listeners.size() - 1; i > -1; i--) {
					java.util.AbstractMap.SimpleEntry<Object, ChannelListener<Object>> en = listeners.get(i);
					try {
						en.getValue().onConnect(status, req);
					}catch(Throwable e) {
						NginxClojureRT.log.error(String.format("#%d: onRead Error!", req.nativeRequest()), e);
					}
				}
			}
		}

		@Override
		public void onRead(long status, NginxRequest req) {
			if (NginxClojureRT.log.isDebugEnabled()) {
				NginxClojureRT.log.debug("#%d: request %s onRead, status=%d", req.nativeRequest(), req.uri(), status);
			}
			List<java.util.AbstractMap.SimpleEntry<Object, ChannelListener<Object>>> listeners = req.listeners();
			if (listeners != null) {
				for (int i = listeners.size() - 1; i > -1; i--) {
					java.util.AbstractMap.SimpleEntry<Object, ChannelListener<Object>> en = listeners.get(i);
					try {
						en.getValue().onRead(status, en.getKey());
					}catch(Throwable e) {
						NginxClojureRT.log.error(String.format("#%d: onRead Error!", req.nativeRequest()), e);
					}
				}
			}
		}

		@Override
		public void onWrite(long status, NginxRequest req) {
			if (NginxClojureRT.log.isDebugEnabled()) {
				NginxClojureRT.log.debug("#%d: request %s onWrite, status=%d", req.nativeRequest(), req.uri(), status);
			}
			List<java.util.AbstractMap.SimpleEntry<Object, ChannelListener<Object>>> listeners = req.listeners();
			if (listeners != null) {
				for (int i = listeners.size() - 1; i > -1; i--) {
					java.util.AbstractMap.SimpleEntry<Object, ChannelListener<Object>> en = listeners.get(i);
					try {
						en.getValue().onWrite(status, en.getKey());
					}catch(Throwable e) {
						NginxClojureRT.log.error(String.format("#%d: onWrite Error!", req.nativeRequest()), e);
					}
				}
			}
		}
		
		@Override
		public void onBinaryMessage(NginxRequest req, long message, boolean remining, boolean first) {
			int size = (int) (( message >> 48 ) & 0xffff);
			if (NginxClojureRT.log.isDebugEnabled()) {
				NginxClojureRT.log.debug("#%d: request %s onBinaryMessage! size=%d, rem=%s, first=%s, pm=%d", req.nativeRequest(), req.uri(), size, remining, first, NginxClojureRT.UNSAFE.getAddress(message << 16 >> 16));
			}
			if (size <= 0 && !first && remining) {
				return;
			}
			List<java.util.AbstractMap.SimpleEntry<Object, ChannelListener<Object>>> listeners = req.listeners();
			if (listeners != null) {
				for (int i = listeners.size() - 1; i > -1; i--) {
					java.util.AbstractMap.SimpleEntry<Object, ChannelListener<Object>> en = listeners.get(i);
					try {
						ChannelListener<Object> l = en.getValue();
						if (l instanceof MessageListener) {
							ByteBuffer bb = ByteBuffer.allocate(size);
							NginxClojureRT.ngx_http_clojure_mem_copy_to_obj(NginxClojureRT.UNSAFE.getAddress(message << 16 >> 16), bb.array(), MiniConstants.BYTE_ARRAY_OFFSET, size);
							bb.limit(size);
							((MessageListener) l).onBinaryMessage(en.getKey(), bb, remining);
						}
					}catch(Throwable e) {
						NginxClojureRT.log.error(String.format("#%d: onWrite Error!", req.nativeRequest()), e);
					}
				}
			}
		
		}
		
		@Override
		public void onTextMessage(NginxRequest req, long message, boolean remining, boolean first) {
			int size = (int) (( message >> 48 ) & 0xffff);
			if (NginxClojureRT.log.isDebugEnabled()) {
				NginxClojureRT.log.debug("#%d: request %s onTextMessage! size=%d, rem=%s, first=%s, pm=%d", req.nativeRequest(), req.uri(), size, remining, first, NginxClojureRT.UNSAFE.getAddress(message << 16 >> 16));
			}
			List<java.util.AbstractMap.SimpleEntry<Object, ChannelListener<Object>>> listeners = req.listeners();
			if (listeners != null) {
				ByteBuffer bb = NginxClojureRT.pickByteBuffer();
				CharBuffer cb = NginxClojureRT.pickCharBuffer();
				long address = message << 16 >> 16;
				String txt = NginxClojureRT.fetchStringValidPart(address, 0,  size, MiniConstants.DEFAULT_ENCODING, bb, cb);
				int invalidNum = bb.remaining();
				if (NginxClojureRT.log.isDebugEnabled()) {
					NginxClojureRT.getLog().debug("onTextMessage fetchStringValidPart : %d", invalidNum);
				}
				NginxClojureRT.UNSAFE.putAddress(address, NginxClojureRT.UNSAFE.getAddress(address) - invalidNum);
				if (txt.length() > 0 || first || !remining) {
					if ( (txt.length() == 0 || !remining) && invalidNum != 0) {
						return;
					}
					for (int i = listeners.size() - 1; i > -1; i--) {
						java.util.AbstractMap.SimpleEntry<Object, ChannelListener<Object>> en = listeners.get(i);
						try {
							ChannelListener<Object> l = en.getValue();
							if (l instanceof MessageListener) {
								((MessageListener) l).onTextMessage(en.getKey(), txt, remining);
							}
						}catch(Throwable e) {
							NginxClojureRT.log.error(String.format("#%d: onWrite Error!", req.nativeRequest()), e);
						}
					}
				}
			}
		}
	};
	
	public NginxJavaRequest(NginxHandler handler, NginxJavaRingHandler ringHandler, long r, Object[] array) {
		this.r = r;
		this.handler = handler;
		this.array = array;
		this.ringHandler = ringHandler;
		if (r != 0) {
			NginxClojureRT.ngx_http_clojure_add_listener(r, requestListener, this, 1);
		}
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
		if (NginxClojureRT.log.isDebugEnabled()) {
			get(URI);
		}
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
			if (released) {
				return null;
			}
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
		return new SimpleEntry<String, Object>(key(i), val(i), NginxSimpleHandler.readOnlyEntrySetter);
	}
	

	public int setVariable(String name, String value) {
		return NginxClojureRT.setNGXVariable(r, name, value);
	}
	
	public String getVariable(String name) {
		return NginxClojureRT.getNGXVariable(r, name);
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
	
	@Override
	public int phase() {
		return phase;
	}
	
	protected NginxJavaRequest phase(int phase) {
		this.phase = phase;
		return this;
	}
	
	public boolean isWebSocket() {
		return NginxClojureRT.ngx_http_clojure_mem_get_module_ctx_upgrade(r) == 1;
	}
	
	@Override
	public String toString() {
		return String.format("request {id : %d,  uri: %s}", r, val(0));
	}

	@Override
	public <T> void addListener(T data, ChannelListener<T> listener) {
		if (listeners == null) {
			listeners = new ArrayList<java.util.AbstractMap.SimpleEntry<Object, ChannelListener<Object>>>(1);
		}
		listeners.add(new java.util.AbstractMap.SimpleEntry<Object, ChannelListener<Object>>(data, (ChannelListener)listener));
		if (isWebSocket()) { //handshake was complete so we need call onConnect manually
			try {
				listener.onConnect(NginxClojureAsynSocket.NGX_HTTP_CLOJURE_SOCKET_OK, data);
			} catch (Throwable e) {
				NginxClojureRT.log.error(String.format("#%d: onConnect Error!", r), e);
			}
		}
	}
	
	@Override
	public void tagReleased() {
		this.released = true;
	}

	@Override
	public List<java.util.AbstractMap.SimpleEntry<Object, ChannelListener<Object>>> listeners() {
		return listeners;
	}
	
	@Override
	public String uri() {
		return (String) get(URI);
	}
	
	@Override
	public NginxHttpServerChannel hijack(boolean ignoreFilter) {
		return handler.hijack(this, ignoreFilter);
	}
}
