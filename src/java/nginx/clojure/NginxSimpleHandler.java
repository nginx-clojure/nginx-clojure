package nginx.clojure;

import static nginx.clojure.MiniConstants.BYTE_ARRAY_OFFSET;
import static nginx.clojure.MiniConstants.CONTENT_TYPE;
import static nginx.clojure.MiniConstants.DEFAULT_ENCODING;
import static nginx.clojure.MiniConstants.KNOWN_RESP_HEADERS;
import static nginx.clojure.MiniConstants.NGX_DONE;
import static nginx.clojure.MiniConstants.NGX_HTTP_BODY_FILTER_PHASE;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_HEADERSO_CONTENT_TYPE_LEN_OFFSET;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_HEADERSO_CONTENT_TYPE_OFFSET;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_HEADERSO_HEADERS_OFFSET;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_HEADERSO_STATUS_OFFSET;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_REQ_HEADERS_OUT_OFFSET;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_REQ_POOL_OFFSET;
import static nginx.clojure.MiniConstants.NGX_HTTP_CONTENT_PHASE;
import static nginx.clojure.MiniConstants.NGX_HTTP_HEADER_FILTER_PHASE;
import static nginx.clojure.MiniConstants.NGX_HTTP_INTERNAL_SERVER_ERROR;
import static nginx.clojure.MiniConstants.NGX_HTTP_LOG_PHASE;
import static nginx.clojure.MiniConstants.NGX_HTTP_NO_CONTENT;
import static nginx.clojure.MiniConstants.NGX_HTTP_OK;
import static nginx.clojure.MiniConstants.NGX_HTTP_SWITCHING_PROTOCOLS;
import static nginx.clojure.MiniConstants.NGX_OK;
import static nginx.clojure.MiniConstants.RESP_CONTENT_TYPE_HOLDER;
import static nginx.clojure.NginxClojureRT.UNSAFE;
import static nginx.clojure.NginxClojureRT.coroutineEnabled;
import static nginx.clojure.NginxClojureRT.handleResponse;
import static nginx.clojure.NginxClojureRT.log;
import static nginx.clojure.NginxClojureRT.ngx_http_clojure_mem_build_file_chain;
import static nginx.clojure.NginxClojureRT.ngx_http_clojure_mem_build_temp_chain;
import static nginx.clojure.NginxClojureRT.ngx_http_clojure_mem_inc_req_count;
import static nginx.clojure.NginxClojureRT.ngx_http_set_content_type;
import static nginx.clojure.NginxClojureRT.pickByteBuffer;
import static nginx.clojure.NginxClojureRT.pushNGXInt;
import static nginx.clojure.NginxClojureRT.pushNGXSizet;
import static nginx.clojure.NginxClojureRT.pushNGXString;
import static nginx.clojure.NginxClojureRT.workers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;

import nginx.clojure.Coroutine.FinishAwaredRunnable;
import nginx.clojure.NginxClojureRT.WorkerResponseContext;
import nginx.clojure.java.Constants;
import nginx.clojure.java.DefinedPrefetch;
import nginx.clojure.java.NginxJavaResponse;
import sun.nio.ch.DirectBuffer;
import sun.nio.cs.ThreadLocalCoders;


public abstract class NginxSimpleHandler implements NginxHandler, Configurable {
	
	protected static ConcurrentLinkedQueue<Coroutine> pooledCoroutines = new ConcurrentLinkedQueue<>();
	
	protected static ConcurrentHashMap<Long, Future<WorkerResponseContext>> lastRequestEvalFutures = new ConcurrentHashMap<>();
	
	private static final boolean ONLY_CONTENT_HENADLER_SUPPORT_THREADS = Boolean.parseBoolean(System.getProperty("nc.threads.only_for_content", "false"));

	public abstract NginxRequest makeRequest(long r, long c);
	
	public abstract String[] headersNeedPrefetch();
	
	public abstract String[] variablesNeedPrefetch();
	
	public abstract String[] responseHeadersNeedPrefetch();
	
	protected boolean forcePrefetchAllProperties = false;
	
	@Override
	public void config(Map<String, String> properties) {
		forcePrefetchAllProperties = "true".equalsIgnoreCase(properties.get(MiniConstants.REQUEST_FORECE_PREFETCH_ALL_PROPERTIES));
	}
	
	@Override
	public int execute(final long r, final long c) {
		
		if (r == 0) { //by worker init process
			NginxResponse resp = handleRequest(makeRequest(0, 0));
			if (resp != null && resp.type() == NginxResponse.TYPE_FAKE_ASYNC_TAG && resp.fetchStatus(200) != 200) {
				log.error("initialize error %s", resp);
				return NGX_HTTP_INTERNAL_SERVER_ERROR;
			}
			return NGX_HTTP_OK;
		}
		
		final NginxRequest req = makeRequest(r, c);
		final int phase = req.phase();
		boolean isWebSocket = req.isWebSocket();
		
		if (forcePrefetchAllProperties) {
			//for safe access with another thread
			req.prefetchAll(DefinedPrefetch.ALL_HEADERS,
					variablesNeedPrefetch() == DefinedPrefetch.NO_VARS ? DefinedPrefetch.CORE_VARS : variablesNeedPrefetch(),
							responseHeadersNeedPrefetch());
		}
		
		if (workers == null || (isWebSocket && phase == -1)
				|| (phase != NGX_HTTP_CONTENT_PHASE && ONLY_CONTENT_HENADLER_SUPPORT_THREADS)) {
			if (isWebSocket) {
				req.uri();
			}
			NginxResponse resp = handleRequest(req);
			
			if (phase == NGX_HTTP_LOG_PHASE) {
				return NGX_OK;
			}
			
			if (resp.type() == NginxResponse.TYPE_FAKE_ASYNC_TAG) {
/*				
 *          the equivalent complete check is :
 *				!req.isReleased()  //skip released requests
 *				&& !( req.isHijacked() && (phase == -1 || phase == NGX_HTTP_HEADER_FILTER_PHASE))  //skips those increased hijacked requests 
 *				&& (phase == -1 || phase == NGX_HTTP_HEADER_FILTER_PHASE)  //must be content handler
 */				
				if (!req.isReleased()) {
					if (!req.isHijacked() 
							&& (phase == -1 || phase == NGX_HTTP_HEADER_FILTER_PHASE
							                || phase == NGX_HTTP_BODY_FILTER_PHASE)) {
						long oldCount = ngx_http_clojure_mem_inc_req_count(r, 1);
						if (oldCount < 0) {
							return (int)oldCount;
						} else {
							req.nativeCount(oldCount + 1);
						}
					}
					
					if (!forcePrefetchAllProperties && !coroutineEnabled) {
						//for safe access with another thread
						req.prefetchAll(headersNeedPrefetch(), variablesNeedPrefetch(), responseHeadersNeedPrefetch());		
					}
					
					if (phase == NGX_HTTP_LOG_PHASE) {
						req.markReqeased();
					}
					
				}
				
				return NGX_DONE;
			}
			return handleResponse(req, resp);
		}
		
		//with thread pool mode we need make it safe
		if (!forcePrefetchAllProperties) {
			req.prefetchAll(headersNeedPrefetch(), variablesNeedPrefetch(), responseHeadersNeedPrefetch());		
		}
		
		if (phase == -1 || phase == NGX_HTTP_HEADER_FILTER_PHASE 
				|| phase == NGX_HTTP_BODY_FILTER_PHASE
				) { // -1 means from content handler invoking 
			long oldCount = ngx_http_clojure_mem_inc_req_count(r, 1);
			if (oldCount < 0) {
				return (int)oldCount;
			} else {
				req.nativeCount(oldCount + 1);
			}
		}
		
		final Future<WorkerResponseContext> lastFuture = lastRequestEvalFutures.get(req.nativeRequest());
		Future<WorkerResponseContext> future = workers.submit(() -> {
			NginxClojureRT.getLog().debug("req %s, c %s, phase %s", req.nativeRequest(), req.nativeCount(), req.phase());
			if (lastFuture != null) {
				lastFuture.get();
			}
			NginxResponse resp = handleRequest(req);
			//let output chain built before entering the main thread
			return new WorkerResponseContext(resp, req);
		});
		lastRequestEvalFutures.put(req.nativeRequest(), future);

		if (phase == NGX_HTTP_LOG_PHASE) {
			req.markReqeased();
		}
		
		return NGX_DONE;
	}
	

	
	public static NginxResponse handleRequest(final NginxRequest req) {
		try {
			if (coroutineEnabled) {
				Coroutine coroutine = pooledCoroutines.poll();
				CoroutineRunner coroutineRunner;
				if (coroutine == null) {
					coroutineRunner = new CoroutineRunner(req);
					coroutine = new Coroutine(coroutineRunner);
				} else {
					coroutine.reset();
					coroutineRunner = (CoroutineRunner) coroutine.getProto();
					coroutineRunner.request = req;
				}

				coroutine.resume();
				if (coroutine.getState() == Coroutine.State.FINISHED) {
					return coroutineRunner.response;
				} else {
					return new NginxJavaResponse(req, Constants.ASYNC_TAG);
				}
			} else {
				return req.handler().process(req);
			}
		} catch (Throwable e) {
			log.error("server unhandled exception!", e);
			return buildUnhandledExceptionResponse(req, e);
		}
	}
	
	public interface SimpleEntrySetter<T> {
		T setValue(T value);
	}
	
	public final static SimpleEntrySetter<Object> readOnlyEntrySetter = value -> {
		throw new UnsupportedOperationException("read only entry can not set!");
	};
	
	public static class SimpleEntry<K, V> implements Entry<K, V> {

		public K key;
		public V value;
		public SimpleEntrySetter<V> setter;
		
		public SimpleEntry(K key, V value, SimpleEntrySetter<V> simpleEntrySetter) {
			this.key = key;
			this.value = value;
			this.setter = simpleEntrySetter;
		}
		
		@Override
		public K getKey() {
			return key;
		}

		@Override
		public V getValue() {
			return value;
		}

		@Override
		public V setValue(V value) {
			return setter.setValue(value);
		}
	}
	
	
	public static class NginxUnhandledExceptionResponse extends NginxSimpleResponse {
		
		Throwable err;
		NginxRequest r;
		
		public NginxUnhandledExceptionResponse(NginxRequest r, Throwable e) {
			this.err = e;
			this.r = r;
			if (r.isReleased()) {
				this.type = TYPE_FATAL;
			}else {
				this.type = TYPE_ERROR;
			}
		}
		
		@Override
		public int fetchStatus(int defaultStatus) {
			return 500;
		}
		
		@SuppressWarnings({ "unchecked", "rawtypes" })
		@Override
		public <K, V> Collection<Entry<K, V>> fetchHeaders() {
			return (List)Arrays.asList(new SimpleEntry(CONTENT_TYPE, "text/plain", readOnlyEntrySetter));
		}
		@Override
		public Object fetchBody() {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			err.printStackTrace(pw);
			pw.close();
			return sw.toString();
		}
		@Override
		public NginxRequest request() {
			return r;
		}
	}

	public static NginxResponse buildUnhandledExceptionResponse(NginxRequest r, Throwable e) {
		return new NginxUnhandledExceptionResponse(r, e);
	}

	
	public static final class CoroutineRunner implements FinishAwaredRunnable {
		
		NginxRequest request;
		NginxResponse response;
		
		
		public CoroutineRunner(NginxRequest request) {
			super();
			this.request = request;
		}

		@Override
		public void run() throws SuspendExecution {
			try {
				response = request.handler().process(request);
			}catch(Throwable e) {
				response = buildUnhandledExceptionResponse(request, e);
				log.error("unhandled exception in coroutine", e);
			}
			
			if (Coroutine.getActiveCoroutine().getResumeCounter() != 1) {
				request.handler().completeAsyncResponse(request, response);
			}
		}

		@Override
		public void onFinished(Coroutine c) {
			pooledCoroutines.add(c);
		}
	}
	
	@Override
	public NginxHeaderHolder fetchResponseHeaderPusher(String name) {
		NginxHeaderHolder pusher = KNOWN_RESP_HEADERS.get(name);
		if (pusher == null) {
			pusher = new UnknownHeaderHolder(name, NGX_HTTP_CLOJURE_HEADERSO_HEADERS_OFFSET);
		}
		return pusher;
	}

	@Override
	public <K, V> long prepareHeaders(NginxRequest req, long status, Collection<Map.Entry<K, V>> headers) {
		long r = req.nativeRequest();
		long pool = UNSAFE.getAddress(r + NGX_HTTP_CLOJURE_REQ_POOL_OFFSET);
		long headers_out = r + NGX_HTTP_CLOJURE_REQ_HEADERS_OUT_OFFSET;
		
		String contentType = null;
		if (headers != null) {
			for (Map.Entry<?, ?> hen : headers) {
				Object nameObj = hen.getKey();
				Object val = hen.getValue();
				
				if (nameObj == null || val == null) {
					continue;
				}
				
				String name = normalizeHeaderName(nameObj);
				if (name == null || name.length() == 0) {
					continue;
				}
				
				NginxHeaderHolder pusher = fetchResponseHeaderPusher(name);
				if (pusher == RESP_CONTENT_TYPE_HOLDER) {
					if (val instanceof String) {
						contentType = (String)val;
					}else { //TODO:support another types 
						
					}
				}
				pusher.push(headers_out, pool, val);
			}
		}
		
		if (contentType == null && status != NGX_HTTP_SWITCHING_PROTOCOLS){
			ngx_http_set_content_type(r);
		}else {
			int contentTypeLen = pushNGXString(headers_out + NGX_HTTP_CLOJURE_HEADERSO_CONTENT_TYPE_OFFSET, contentType, DEFAULT_ENCODING, pool);
			//be friendly to gzip module 
			pushNGXSizet(headers_out + NGX_HTTP_CLOJURE_HEADERSO_CONTENT_TYPE_LEN_OFFSET, contentTypeLen);
		}
		
		pushNGXInt(headers_out + NGX_HTTP_CLOJURE_HEADERSO_STATUS_OFFSET, (int)status);
		return r;
	}

	
	@Override
	public long buildOutputChain(NginxResponse response) {
		long r = response.request().nativeRequest();
		try {
//			long pool = UNSAFE.getAddress(r + NGX_HTTP_CLOJURE_REQ_POOL_OFFSET);
			int status = response.fetchStatus(NGX_HTTP_OK);

			
			Object body = response.fetchBody();
			long chain = defaultChainFlag(response);
			
			if (body != null) {
				chain = buildResponseItemBuf(r, body, chain);
				if (chain == 0) {
					return -NGX_HTTP_INTERNAL_SERVER_ERROR;
				}else if (chain < 0 && chain != -204) {
					return chain;
				}
			}else {
				chain = -NGX_HTTP_NO_CONTENT;
			}
			
			if (chain == -NGX_HTTP_NO_CONTENT) {
				if (response.type() == NginxResponse.TYPE_FAKE_BODY_FILTER_TAG) {
					if (response.isLast()) {
						chain = ngx_http_clojure_mem_build_temp_chain(r, defaultChainFlag(response), null, 0, 0);
					}else {
						return 0;
					}
				}else {
					if (status == NGX_HTTP_OK) {
						status = NGX_HTTP_NO_CONTENT;
					}
					return -status;
				}
			}
			
			return chain;

		}catch(Throwable e) {
			log.error("server unhandled exception!", e);
			return -NGX_HTTP_INTERNAL_SERVER_ERROR;
		}
	}
	
	protected  long defaultChainFlag(NginxResponse response) {
		return 0;
	}
	
	protected  long buildResponseFileBuf(File f, long r, long chain) {
		ByteBuffer b = HackUtils.encode(f.getPath(), DEFAULT_ENCODING, pickByteBuffer());
		if (b.remaining() < b.capacity()) {
			b.array()[b.remaining()] = 0; // for file name in c language is ended with '\0'
		}
		chain = ngx_http_clojure_mem_build_file_chain(r, chain, b.array(), BYTE_ARRAY_OFFSET, b.remaining(), Thread.currentThread() == NginxClojureRT.NGINX_MAIN_THREAD);
		if (chain <= 0) {
			return chain;
		}
		return chain;
	}
	
	//TODO: optimize handling inputstream with large lazy data
	protected  long buildResponseInputStreamBuf(InputStream in, long r,  final long preChain) {
		try {
			long chain = preChain;
			long first = 0;
			byte[] buf = pickByteBuffer().array();
			while (true) {
				int c = 0;
				int pos = 0;
				do {
					c = in.read(buf, pos, buf.length - pos);
					if (c > 0) {
						pos += c;
					}
				}while (c >= 0 && pos < buf.length);
				
				if (pos > 0) {
					chain = ngx_http_clojure_mem_build_temp_chain(r, chain, buf, BYTE_ARRAY_OFFSET, pos);
					if (chain <= 0) {
						return chain;
					}
					if (first == 0) {
						first = chain;
					}
				}
				
				if (c < 0) {
					break;
				}
			}
			
			return preChain <= 0 ? (first == 0 ? -NGX_HTTP_NO_CONTENT : first)  : chain;
		}catch(IOException e) {
			log.error("can not read from InputStream", e);
			return -500; 
		}finally {
			try {
				in.close();
			} catch (IOException e) {
				log.error("can not close  InputStream", e);
			}
		}
	}
	
	protected long buildResponseStringBuf(String s, long r,  final long preChain) {
		if (s == null) {
			return 0;
		}

		if (s.length() == 0) {
			return -NGX_HTTP_NO_CONTENT;
		}

		CharsetEncoder charsetEncoder = ThreadLocalCoders.encoderFor(DEFAULT_ENCODING)
				.onMalformedInput(CodingErrorAction.REPLACE).onUnmappableCharacter(CodingErrorAction.REPLACE);
		ByteBuffer bb = pickByteBuffer();
		CharBuffer cb = CharBuffer.wrap(s);
		charsetEncoder.reset();
		CoderResult result = CoderResult.UNDERFLOW;
		long first = 0;
		long chain = preChain;
		do {
			result = charsetEncoder.encode(cb, bb, true);
			if (result == CoderResult.OVERFLOW) {
				bb.flip();
				chain = ngx_http_clojure_mem_build_temp_chain(r, chain, bb.array(), BYTE_ARRAY_OFFSET, bb.remaining());
				if (chain <= 0) {
					return chain;
				}
				bb.clear();
				if (first == 0) {
					first = chain;
				}
			} else if (result == CoderResult.UNDERFLOW) {
				break;
			} else {
				log.error("%s can not decode string : %s", result.toString(), s);
				return -NGX_HTTP_INTERNAL_SERVER_ERROR;
			}
		} while (true);

		while (charsetEncoder.flush(bb) == CoderResult.OVERFLOW) {
			bb.flip();
			chain = ngx_http_clojure_mem_build_temp_chain(r, chain, bb.array(), BYTE_ARRAY_OFFSET, bb.remaining());
			if (chain <= 0) {
				return chain;
			}
			if (first == 0) {
				first = chain;
			}
			bb.clear();
		}

		bb.flip();
		if (bb.hasRemaining()) {
			chain = ngx_http_clojure_mem_build_temp_chain(r, chain, bb.array(), BYTE_ARRAY_OFFSET, bb.remaining());
			if (chain <= 0) {
				return chain;
			}
			if (first == 0) {
				first = chain;
			}
			bb.clear();
		}

		return preChain <= 0 ? first : chain ;
	}
	
	protected long buildResponseByteBufferBuf(ByteBuffer b, long r,  final long preChain) {
		if (b == null) {
			return 0;
		}

		if (!b.hasRemaining()) {
			return -NGX_HTTP_NO_CONTENT;
		}
		
		long chain = b.isDirect() ?
				ngx_http_clojure_mem_build_temp_chain(r, preChain, null, ((DirectBuffer)b).address()+b.position(), b.remaining()) :
				ngx_http_clojure_mem_build_temp_chain(r, preChain, b.array(), BYTE_ARRAY_OFFSET, b.remaining());

		
		b.position(b.limit());
		
		return chain;
	}
	
	protected long buildResponseByteArrayBuf(byte[] b, long r,  final long preChain) {
		if (b == null) {
			return 0;
		}

		if (b.length == 0) {
			return -NGX_HTTP_NO_CONTENT;
		}
		
		return ngx_http_clojure_mem_build_temp_chain(r, preChain, b, BYTE_ARRAY_OFFSET, b.length);
	}
	
	protected  long buildResponseIterableBuf(@SuppressWarnings("rawtypes") Iterable iterable, long r,  long preChain) {
		if (iterable == null) {
			return 0;
		}
		
		@SuppressWarnings("rawtypes")
		Iterator i = iterable.iterator();
		if (!i.hasNext()) {
			return -204;
		}

		long chain = preChain;
		long first = 0;
		while (i.hasNext()) {
			Object o = i.next();
			if (o != null) {
				long rc  = buildResponseItemBuf(r, o, chain);
				if (rc <= 0) {
					if (rc != -NGX_HTTP_NO_CONTENT) {
						return rc;
					}
				}else {
					chain = rc;
					if (first == 0) {
						first = chain;
					}
				}
			}
		}
		return preChain <= 0 ? (first == 0 ? -NGX_HTTP_NO_CONTENT : first)  : chain;
	}
	
	
	protected  long buildResponseItemBuf(long r, Object item, long chain) {

		if (item instanceof File) {
			return buildResponseFileBuf((File)item, r, chain);
		}else if (item instanceof NginxChainWrappedInputStream) {
			 return buildNginxChainWrappedInputStreamItemBuf(r, (NginxChainWrappedInputStream)item, chain);
		}else if (item instanceof InputStream) {
			return buildResponseInputStreamBuf((InputStream)item, r, chain);
		}else if (item instanceof String) {
			return buildResponseStringBuf((String)item, r, chain);
		}else if (item instanceof ByteBuffer) {
			return buildResponseByteBufferBuf((ByteBuffer)item, r, chain);
		}else if (item instanceof byte[]) {
			return buildResponseByteArrayBuf((byte[])item, r, chain);
		}
		return buildResponseComplexItemBuf(r, item, chain);
	}
	
	protected long buildNginxChainWrappedInputStreamItemBuf(long r, NginxChainWrappedInputStream item, long chain) {
		return item.chain;
	}
	
	@SuppressWarnings("rawtypes")
	protected long buildResponseComplexItemBuf(long r, Object item, long chain) {
		if (item == null) {
			return 0;
		}
		if (item instanceof Iterable) {
			return buildResponseIterableBuf((Iterable)item, r, chain);
		}else if (item.getClass().isArray()) {
			return buildResponseIterableBuf(Arrays.asList((Object[])item), r, chain);
		}
		return -NGX_HTTP_INTERNAL_SERVER_ERROR;
	}
	
	protected  String normalizeHeaderName(Object nameObj) {
		return normalizeHeaderNameHelper(nameObj);
	}

	public static String normalizeHeaderNameHelper(Object nameObj) {
		return nameObj instanceof String ? (String)nameObj : nameObj.toString();
	}
}
