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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

import nginx.clojure.ChannelListener;
import nginx.clojure.Coroutine;
import nginx.clojure.MiniConstants;
import nginx.clojure.NginxClojureRT;
import nginx.clojure.NginxHandler;
import nginx.clojure.NginxHttpServerChannel;
import nginx.clojure.NginxRequest;
import nginx.clojure.NginxSimpleHandler;
import nginx.clojure.NginxSimpleHandler.SimpleEntry;
import nginx.clojure.RequestVarFetcher;
import nginx.clojure.java.PickerPoweredIterator.Picker;
import nginx.clojure.java.RequestRawMessageAdapter.RequestOrderedRunnable;
import nginx.clojure.net.NginxClojureAsynSocket;


public class NginxJavaRequest implements NginxRequest, Map<String, Object> {

	//TODO: SSL_CLIENT_CERT
	protected final static Object[] default_request_array = new Object[] { 
		    URI, URI_FETCHER, 
		    BODY, BODY_FETCHER, 
		    HEADERS, HEADER_FETCHER,
			SERVER_PORT, SERVER_PORT_FETCHER, 
			SERVER_NAME, SERVER_NAME_FETCHER, 
			REMOTE_ADDR, REMOTE_ADDR_FETCHER,
			QUERY_STRING, QUERY_STRING_FETCHER, 
			SCHEME, SCHEME_FETCHER, 
			REQUEST_METHOD, REQUEST_METHOD_FETCHER, 
			CONTENT_TYPE, CONTENT_TYPE_FETCHER, 
			CHARACTER_ENCODING, CHARACTER_ENCODING_FETCHER
	};
	
	public static void fixDefaultRequestArray() {
		if (default_request_array[1] == null) {
			int i = 0;
			default_request_array[(i++ << 1) + 1] = URI_FETCHER; 
			default_request_array[(i++ << 1) + 1] = BODY_FETCHER; 
			default_request_array[(i++ << 1) + 1] = HEADER_FETCHER; 
			default_request_array[(i++ << 1) + 1] = SERVER_PORT_FETCHER; 
			default_request_array[(i++ << 1) + 1] = SERVER_NAME_FETCHER; 
			default_request_array[(i++ << 1) + 1] = REMOTE_ADDR_FETCHER; 
			default_request_array[(i++ << 1) + 1] = QUERY_STRING_FETCHER; 
			default_request_array[(i++ << 1) + 1] = SCHEME_FETCHER; 
			default_request_array[(i++ << 1) + 1] = REQUEST_METHOD_FETCHER; 
			default_request_array[(i++ << 1) + 1] = CONTENT_TYPE_FETCHER; 
			default_request_array[(i++ << 1) + 1] = CHARACTER_ENCODING_FETCHER;
		}
	}
	
	protected long r;
	NginxHandler handler;
	protected Object[] array;
	protected boolean hijacked = false;
	protected NginxHttpServerChannel channel;
	protected int phase = -1;
	protected int evalCount = 0;
	protected int nativeCount = -1;
	protected volatile boolean released = false;
	protected List<java.util.AbstractMap.SimpleEntry<Object, ChannelListener<Object>>> listeners;
	protected Map<String, String> prefetchedVariables;
	protected Set<String> updatedVariables;
	
	public final  static ChannelListener<NginxRequest> requestListener  = new RequestRawMessageAdapter();
	
	public NginxJavaRequest(int phase, NginxHandler handler, long r, Object[] array) {
		this.r = r;
		this.handler = handler;
		this.array = array;
		this.phase = phase;
		if (r != 0) {
			NginxClojureRT.addListener(r, requestListener, this, phase == -1 ? 1 : 0);
		}
	}
	
	public NginxJavaRequest(int phase, NginxHandler handler, long r) {
		this(phase, handler, r, new Object[default_request_array.length]);
		System.arraycopy(default_request_array, 0, array, 0, default_request_array.length);
		if (NginxClojureRT.log.isDebugEnabled()) {
			get(URI);
		}
	}
	
	public void reset(long r, NginxHandler handler) {
		this.r = r;
		this.released = false;
		this.hijacked = false;
		this.handler = handler;
		phase = -1;
		if (r != 0) {
			NginxClojureRT.addListener(r, requestListener, this, 1);
			nativeCount = -1;
		}
	}
	
	public long nativeCount(long c) {
		int old = nativeCount;
		nativeCount = (int)c;
		return old;
	}
	
	public int refreshNativeCount() {
		return nativeCount = (int)NginxClojureRT.ngx_http_clojure_mem_inc_req_count(r, 0);
	}
	
	public void prefetchAll() {
		prefetchAll(DefinedPrefetch.ALL_HEADERS, DefinedPrefetch.NO_VARS, DefinedPrefetch.NO_HEADERS);
	}
	
	/* (non-Javadoc)
	 * @see nginx.clojure.NginxRequest#prefetchAll(java.lang.String[], java.lang.String[])
	 */
	@Override
	public void prefetchAll(String[] headers, String[] variables, String[] outHeaders) {
		int len = array.length >> 1;
		for (int i = 0; i < len; i++) {
			Object v = val(i);
			if (v instanceof JavaLazyHeaderMap) {
				((JavaLazyHeaderMap)v).enableSafeCache(headers);
			}
		}

		if (variables == DefinedPrefetch.CORE_VARS) {
			variables = MiniConstants.CORE_VARS.keySet().toArray(new String[MiniConstants.CORE_VARS.size()]);
		}
		
		prefetchedVariables = new HashMap<>(variables.length);
		
		for (String variable : variables) {
			prefetchedVariables.put(variable, getVariable(variable));
		}
		
		updatedVariables = new LinkedHashSet<>();
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
				NginxClojureRT.getLog().warn("val at released request %s, idx %d", r, i);
				return null;
			}
			if (Thread.currentThread() != NginxClojureRT.NGINX_MAIN_THREAD) {
				throw new IllegalAccessError("fetching lazy value of " + array[i-1] + " in NginxJavaRequest can only be called in main thread, please pre-access it in main thread OR call NginxJavaRequest.prefetchAll() first in main thread");
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
		
		if (prefetchedVariables != null) {
			prefetchedVariables.put(name, value);
			updatedVariables.add(name);
			return 0;
		}
		
		return NginxClojureRT.setNGXVariable(r, name, value);
	}
	
	public String getVariable(String name) {
		
		if (prefetchedVariables != null && prefetchedVariables.containsKey(name)) {
			return prefetchedVariables.get(name);
		}
		
		if (Thread.currentThread() != NginxClojureRT.NGINX_MAIN_THREAD) {
			FutureTask<String> task = new FutureTask<String>(new Callable<String>() {
				@Override
				public String call() throws Exception {
					if (released) {
						throw new IllegalAccessException("request was released when fetch variable " + name);
					}
					return NginxClojureRT.unsafeGetNGXVariable(r, name);
				}
			});
			NginxClojureRT.postPollTaskEvent(task);
			try {
				return task.get();
			} catch (InterruptedException e) {
				throw new RuntimeException("getNGXVariable " + name + " error", e);
			} catch (ExecutionException e) {
				throw new RuntimeException("getNGXVariable " + name + " error", e.getCause());
			}
		} else {
			
			return NginxClojureRT.unsafeGetNGXVariable(r, name);
		}
	}
	
	/* (non-Javadoc)
	 * @see nginx.clojure.NginxRequest#getVariable(java.lang.String, java.lang.String)
	 */
	@Override
	public String getVariable(String name, String defaultVal) {
		String v = getVariable(name);
		return (v == null || v.isEmpty()) ? defaultVal : v;
	}
	
	public long discardRequestBody() {
		return NginxClojureRT.discardRequestBody(r);
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
	
	public long nativeCount() {
		return nativeCount;
	}
	
	@Override
	public String toString() {
		return String.format("request {id : %d,  uri: %s}", r, val(0));
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public <T> void addListener(final T data, final ChannelListener<T> listener) {
		if (listeners == null) {
			listeners = new ArrayList<java.util.AbstractMap.SimpleEntry<Object, ChannelListener<Object>>>(1);
		}
		listeners.add(new java.util.AbstractMap.SimpleEntry<Object, ChannelListener<Object>>(data, (ChannelListener)listener));
//		if (isWebSocket()) { //handshake was complete so we need call onConnect manually
			Runnable action = new Coroutine.FinishAwaredRunnable() {
				@Override
				public void run() {
					try {
						listener.onConnect(NginxClojureAsynSocket.NGX_HTTP_CLOJURE_SOCKET_OK, data);
					} catch (Throwable e) {
						NginxClojureRT.log.error(String.format("#%d: onConnect Error!", r), e);
					}				
				}
				@Override
				public void onFinished(Coroutine c) {
					RequestRawMessageAdapter.pooledCoroutines.add(c);
				}
			};
			
			if (NginxClojureRT.coroutineEnabled && Coroutine.getActiveCoroutine() == null) {
				Coroutine coroutine = RequestRawMessageAdapter.pooledCoroutines.poll();
				if (coroutine == null) {
					coroutine = new Coroutine(action);
				}else {
					coroutine.reset(action);
				}
				coroutine.resume();
			}else if (NginxClojureRT.workers == null) {
				action.run();
			}else {
				NginxClojureRT.workerExecutorService.submit(new RequestOrderedRunnable("onConnect2", action, NginxJavaRequest.this));
			}
//		}
	}
	
	@Override
	public void tagReleased() {
		if (NginxClojureRT.log.isDebugEnabled()) {
			NginxClojureRT.log.debug("[%d] tag released!", r);	
		}
		
		this.released = true;
		this.channel = null;
		System.arraycopy(default_request_array, 0, array, 0, default_request_array.length);
		
		if (listeners != null) {
			listeners.clear();
		}
		
		if (prefetchedVariables != null) {
			prefetchedVariables = null;
			updatedVariables = null;
		}
		
		((NginxJavaHandler)handler).returnToRequestPool(this);
	}
	
	public void markReqeased() {
		if (NginxClojureRT.log.isDebugEnabled()) {
			NginxClojureRT.log.debug("[%d] mark request!", r);	
		}
		this.released = true;
	}

	@Override
	public List<java.util.AbstractMap.SimpleEntry<Object, ChannelListener<Object>>> listeners() {
		return listeners;
	}
	
	@Override
	public String uri() {
		return  get(URI).toString();
	}
	
	@Override
	public NginxHttpServerChannel hijack(boolean ignoreFilter) {
		return handler.hijack(this, ignoreFilter);
	}
	
	@Override
	public int getAndIncEvalCount() {
		return evalCount++;
	}
	
	/* (non-Javadoc)
	 * @see nginx.clojure.NginxRequest#applyDelayed()
	 */
	@Override
	public void applyDelayed() {
		if (updatedVariables != null) {
			for (String var : updatedVariables) {
				NginxClojureRT.unsafeSetNginxVariable(r, var, prefetchedVariables.get(var));
			}
		}
	}
}
