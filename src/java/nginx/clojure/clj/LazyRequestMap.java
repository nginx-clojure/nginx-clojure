/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.clj;

import static nginx.clojure.MiniConstants.BODY_FETCHER;
import static nginx.clojure.MiniConstants.CHARACTER_ENCODING_FETCHER;
import static nginx.clojure.MiniConstants.CONTENT_TYPE_FETCHER;
import static nginx.clojure.MiniConstants.DEFAULT_ENCODING;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_HEADERSI_HEADERS_OFFSET;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_REQ_HEADERS_IN_OFFSET;
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
import static nginx.clojure.clj.Constants.WEBSOCKET;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

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
import nginx.clojure.ChannelListener;
import nginx.clojure.Coroutine;
import nginx.clojure.MiniConstants;
import nginx.clojure.NginxClojureRT;
import nginx.clojure.NginxHandler;
import nginx.clojure.NginxHttpServerChannel;
import nginx.clojure.NginxRequest;
import nginx.clojure.RequestVarFetcher;
import nginx.clojure.Stack;
import nginx.clojure.UnknownHeaderHolder;
import nginx.clojure.java.DefinedPrefetch;
import nginx.clojure.java.NginxJavaRequest;
import nginx.clojure.java.RequestRawMessageAdapter;
import nginx.clojure.java.RequestRawMessageAdapter.RequestOrderedRunnable;
import nginx.clojure.net.NginxClojureAsynSocket;

public   class LazyRequestMap extends AFn implements NginxRequest, IPersistentMap {
	
	
	protected final static Object[] default_request_array = new Object[] {
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
		
		WEBSOCKET, new RequestVarFetcher() {
			public Object fetch(long r, Charset encoding) {
				return "websocket".equals(new UnknownHeaderHolder("Upgrade", NGX_HTTP_CLOJURE_HEADERSI_HEADERS_OFFSET).fetch(r+NGX_HTTP_CLOJURE_REQ_HEADERS_IN_OFFSET));
			}
		}
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
	
	protected int validLen;
	protected long r;
	protected Object[] array;
	protected NginxHandler handler;
	protected NginxHttpServerChannel channel;
	protected byte[] hijackTag;
	protected int phase = -1;
	protected int evalCount = 0;
	protected int nativeCount = -1;
	protected volatile boolean released = false;
	protected List<java.util.AbstractMap.SimpleEntry<Object, ChannelListener<Object>>> listeners;
	protected LazyRequestMap rawRequestMap; // it is make by nginx-clojure inner handler not from assoc()
	
	protected Map<String, String> prefetchedVariables;
	protected Set<String> updatedVariables;
	
	public final static LazyRequestMap EMPTY_MAP = new LazyRequestMap(null, 0, null, new Object[0]);
	
	private final  static ChannelListener<NginxRequest> requestListener  = NginxJavaRequest.requestListener;
	
	public LazyRequestMap(NginxHandler handler, long r, byte[] hijackTag, Object[] array) {
		this.handler = handler;
		this.r = r;
		this.array = array;
		this.hijackTag = hijackTag;
		this.listeners = new ArrayList<>();
		this.rawRequestMap = this;
		if (r != 0) {
			NginxClojureRT.addListener(r, requestListener, this.rawRequestMap, 1);
		}
		validLen = array.length;
	}
	
	public LazyRequestMap(NginxHandler handler, long r) {
		//TODO: SSL_CLIENT_CERT
		this(handler, r, new byte[]{0}, default_request_array.clone());
		if (NginxClojureRT.log.isDebugEnabled()) {
			valAt(URI);
		}
	}
	
	private LazyRequestMap(LazyRequestMap or, Object[] a) {
		this.handler = or.handler;
		this.r = or.r;
		this.listeners = or.listeners;
		this.array = a;
		this.hijackTag = or.hijackTag;
		this.rawRequestMap = or.rawRequestMap;
		this.channel = or.channel;
		this.evalCount = or.evalCount;
		this.prefetchedVariables = or.prefetchedVariables;
		this.updatedVariables = or.updatedVariables;
		this.nativeCount = or.nativeCount;
		validLen = a.length;
	}
	
	public void reset(long r, NginxClojureHandler handler) {
		this.r = r;
		this.released = false;
		this.evalCount = 0;
		this.hijackTag[0] = 0;
		phase = -1;
		this.handler = handler;
		this.rawRequestMap = this;
		
		//move to tag released
//		if (this.prefetchedVariables != null) {
//			this.prefetchedVariables.clear();
//		}
		if (r != 0) {
			NginxClojureRT.addListener(r, requestListener, this.rawRequestMap, 1);
			nativeCount = -1;
		}
	}
	
	public void prefetchAll() {
		prefetchAll(DefinedPrefetch.ALL_HEADERS, DefinedPrefetch.NO_VARS, DefinedPrefetch.NO_HEADERS);
	}
	
	/* (non-Javadoc)
	 * @see nginx.clojure.NginxRequest#prefetchAll(java.lang.String[], java.lang.String[])
	 */
	@Override
	public void prefetchAll(String[] headers, String[] variables, String[] outHeaders) {
		for (int i = 0; i < validLen; i += 2) {
			Object v = element(i);
			if (v instanceof LazyHeaderMap) {
				LazyHeaderMap lazyHeaderMap = (LazyHeaderMap)v;
				lazyHeaderMap.enableSafeCache(headers);
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
		for (int i = 0; i < validLen; i+=2){
			if (key == array[i]) {
				return i;
			}
		}
		return -1;
	}
	

	@SuppressWarnings("rawtypes")
	@Override
	public Iterator iterator() {
		return new Iterator<MapEntry>() {
			
			int i = 0;

			@Override
			public boolean hasNext() {
				return i < validLen - 2;
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
		return validLen >> 1;
	}

	@SuppressWarnings("rawtypes")
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

	
	@SuppressWarnings("serial")
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
			if(i + 2 < reqMap.validLen)
				return new ArrayMapSeq(reqMap, i + 2);
			return null;
		}

		public int count(){
			return (reqMap.validLen - i) >> 1;
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
				throw new IllegalAccessError("fetching lazy value of " + array[i-1] + " in LazyRequestMap can only be called in main thread, please pre-access it in main thread OR call LazyRequestMap.prefetchAll() first in main thread");
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

	public NginxRequest upsert(Object key, Object val) {
		int i = index(key);
		if (i != -1) {
			array[i+1] = val;
			return this;
		}
		if (validLen < array.length) {
			array[validLen++] = key;
			array[validLen++] = val;
			return this;
		}else {
			Object[] newArray = new Object[array.length + 8];
			System.arraycopy(array, 0, newArray, 0, array.length);
			newArray[array.length] = key;
			newArray[array.length+1] = val;
			validLen += 2;
			this.array = newArray;
			return this;
		}
	}
	
	@Override
	public IPersistentMap assoc(Object key, Object val) {
		int i = index(key);
		Object[] newArray = null;
		
		if (i != -1) {
			if (element(i) == val) {
				return this;
			}
			newArray = array.clone();
			newArray[i+1] = val;
			return new LazyRequestMap(this, newArray);
		}
		
		newArray = Arrays.copyOf(array, array.length + 2);
		newArray[array.length] = key;
		newArray[array.length + 1] = val;
		return new LazyRequestMap(this, newArray);
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
		
		if (i >= 0) {
			int newlen = array.length - 2;
			if (newlen == 0) {
				return new LazyRequestMap(this, EMPTY_MAP.array);	
			}
			
			Object[] newArray = new Object[newlen];
			for (int s = 0, d = 0; s < array.length; s += 2) {
				if (array[s] != key) {
					newArray[d] = array[s];
					newArray[d + 1] = array[s + 1];
					d += 2;
				}
			}
			return new LazyRequestMap(this, newArray);
		}
		
		return this;
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
		return this.rawRequestMap.released;
	}

	@Override
	public int phase() {
		return phase;
	}
	
	public boolean isWebSocket() {
		return (Boolean) valAt(WEBSOCKET);
	}
	
	protected NginxRequest phase(int phase) {
		this.phase = phase;
		return this;
	}
	
	@Override
	public String toString() {
		return String.format("request {id : %d,  uri: %s}", r, element(0));
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public <T> void addListener(final T data, final ChannelListener<T> listener) {
		if (listeners == null) {
			listeners = new ArrayList<java.util.AbstractMap.SimpleEntry<Object, ChannelListener<Object>>>(1);
		}
		listeners.add(new java.util.AbstractMap.SimpleEntry<Object, ChannelListener<Object>>(data, (ChannelListener)listener));
		
//		if (isWebSocket()) { //handshake was complete so we need call onConnect manually
			 //handshake was complete so we need call onConnect manually
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
				NginxClojureRT.workerExecutorService.submit(new RequestOrderedRunnable("onConnect2", action, LazyRequestMap.this));
			}
		
//		}
	}

	@Override
	public void tagReleased() {
		
		if (NginxClojureRT.log.isDebugEnabled()) {
			NginxClojureRT.log.debug("[%d] tag released!", r);	
		}
		
		this.released =  true;
		this.channel = null;
				
		if (this == this.rawRequestMap) {
			System.arraycopy(default_request_array, 0, array, 0, default_request_array.length);
			validLen = default_request_array.length;
			
			if (array.length > validLen) {
				Stack.fillNull(array, validLen, array.length - validLen);
			}
			
			if (listeners != null) {
				listeners.clear();
			}
			
			if (this.prefetchedVariables != null) {
				this.prefetchedVariables = null;
				this.updatedVariables = null;
			}
						
			((NginxClojureHandler)handler).returnToRequestPool(this);
		} else {
			this.rawRequestMap.tagReleased();
		}
	}
	
	/* (non-Javadoc)
	 * @see nginx.clojure.NginxRequest#markReqeased()
	 */
	@Override
	public void markReqeased() {
		if (NginxClojureRT.log.isDebugEnabled()) {
			NginxClojureRT.log.debug("[%d] mark request!", r);	
		}
		
		this.released = true;
		
		if (this != this.rawRequestMap) {
			this.rawRequestMap.markReqeased();
		}
	}

	@Override
	public List<java.util.AbstractMap.SimpleEntry<Object, ChannelListener<Object>>> listeners() {
		return listeners;
	}
	
	@Override
	public String uri() {
		return (String) valAt(URI);
	}
	
	@Override
	public NginxHttpServerChannel hijack(boolean ignoreFilter) {
		return handler.hijack(this, ignoreFilter);
	}
	
	public long nativeCount() {
		return nativeCount;
	}
	
	public long nativeCount(long c) {
		int old = nativeCount;
		nativeCount = (int)c;
		return old;
	}
	
	public int refreshNativeCount() {
		return nativeCount = (int)NginxClojureRT.ngx_http_clojure_mem_inc_req_count(r, 0);
	}
	
	@Override
	public int getAndIncEvalCount() {
		return evalCount++;
	}

	@Override
	public int setVariable(String name, String value) {
		if (prefetchedVariables != null) {
			prefetchedVariables.put(name, value);
			updatedVariables.add(name);
			return 0;
		}
		
		return NginxClojureRT.setNGXVariable(r, name, value);
	}
	
	@Override
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
	
	@Override
	public long discardRequestBody() {
		return NginxClojureRT.discardRequestBody(r);
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