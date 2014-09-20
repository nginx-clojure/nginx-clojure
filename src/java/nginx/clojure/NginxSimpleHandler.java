package nginx.clojure;

import static nginx.clojure.MiniConstants.BYTE_ARRAY_OFFSET;
import static nginx.clojure.MiniConstants.CONTENT_TYPE;
import static nginx.clojure.MiniConstants.DEFAULT_ENCODING;
import static nginx.clojure.MiniConstants.NGINX_CLOJURE_FULL_VER;
import static nginx.clojure.MiniConstants.NGX_DONE;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_CHAINT_SIZE;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_CHAIN_BUF_OFFSET;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_CHAIN_NEXT_OFFSET;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_HEADERSO_CONTENT_TYPE_LEN_OFFSET;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_HEADERSO_CONTENT_TYPE_OFFSET;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_HEADERSO_STATUS_OFFSET;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_REQ_HEADERS_OUT_OFFSET;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_REQ_POOL_OFFSET;
import static nginx.clojure.MiniConstants.NGX_HTTP_INTERNAL_SERVER_ERROR;
import static nginx.clojure.MiniConstants.NGX_HTTP_NO_CONTENT;
import static nginx.clojure.MiniConstants.NGX_HTTP_OK;
import static nginx.clojure.MiniConstants.SERVER_PUSHER;
import static nginx.clojure.NginxClojureRT.UNSAFE;
import static nginx.clojure.NginxClojureRT.coroutineEnabled;
import static nginx.clojure.NginxClojureRT.handleResponse;
import static nginx.clojure.NginxClojureRT.log;
import static nginx.clojure.NginxClojureRT.ngx_create_file_buf;
import static nginx.clojure.NginxClojureRT.ngx_create_temp_buf;
import static nginx.clojure.NginxClojureRT.ngx_http_clojure_mem_copy_to_addr;
import static nginx.clojure.NginxClojureRT.ngx_http_clojure_mem_get_module_ctx_phase;
import static nginx.clojure.NginxClojureRT.ngx_http_clojure_mem_inc_req_count;
import static nginx.clojure.NginxClojureRT.ngx_http_clojure_mem_init_ngx_buf;
import static nginx.clojure.NginxClojureRT.ngx_http_set_content_type;
import static nginx.clojure.NginxClojureRT.ngx_palloc;
import static nginx.clojure.NginxClojureRT.ngx_pcalloc;
import static nginx.clojure.NginxClojureRT.pushNGXInt;
import static nginx.clojure.NginxClojureRT.pushNGXSizet;
import static nginx.clojure.NginxClojureRT.pushNGXString;
import static nginx.clojure.NginxClojureRT.workers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;

import nginx.clojure.NginxClojureRT.WorkerResponseContext;
import nginx.clojure.java.Constants;
import nginx.clojure.java.NginxJavaResponse;


public abstract class NginxSimpleHandler implements NginxHandler {

	public abstract NginxRequest makeRequest(long r);
	
	@Override
	public int execute(final long r) {
		
		if (r == 0) { //by worker init process
			NginxResponse resp = handleRequest(makeRequest(0));
			if (resp != null && resp.type() == NginxResponse.TYPE_FAKE_ASYNC_TAG && resp.fetchStatus(200) != 200) {
				log.error("initialize error %s", resp);
				return NGX_HTTP_INTERNAL_SERVER_ERROR;
			}
			return NGX_HTTP_OK;
		}
		
		final NginxRequest req = makeRequest(r);
		int phase = (int)ngx_http_clojure_mem_get_module_ctx_phase(r);
		
		if (workers == null) {
			NginxResponse resp = handleRequest(req);
			if (resp.type() == NginxResponse.TYPE_FAKE_ASYNC_TAG) {
				if (phase == -1) { //from content handler invoking 
					ngx_http_clojure_mem_inc_req_count(r);
				}
				return NGX_DONE;
			}
			return handleResponse(req, resp);
		}
		
		//for safe access with another thread
		req.prefetchAll();

		if (phase == -1) { //from content handler invoking 
			ngx_http_clojure_mem_inc_req_count(r);
		}
		workers.submit(new Callable<NginxClojureRT.WorkerResponseContext>() {
			@Override
			public WorkerResponseContext call() throws Exception {
				NginxResponse resp = handleRequest(req);
				//let output chain built before entering the main thread
				return new WorkerResponseContext(resp, req);
			}
		});
		return NGX_DONE;
	}
	

	
	public static NginxResponse handleRequest(final NginxRequest req) {
		try{
			
			if (coroutineEnabled) {
				CoroutineRunner coroutineRunner = new CoroutineRunner(req);
				Coroutine coroutine = new Coroutine(coroutineRunner);
				coroutine.resume();
				if (coroutine.getState() == Coroutine.State.FINISHED) {
					return coroutineRunner.response;
				}else {
					return new NginxJavaResponse(req, Constants.ASYNC_TAG);
				}
			}else {
				return req.handler().process(req);
			}
		}catch(Throwable e){
			log.error("server unhandled exception!", e);
			return buildUnhandledExceptionResponse(req, e);
		}
	}
	
	public static class SimpleEntry<K, V> implements Entry<K, V> {

		public K key;
		public V value;
		
		public SimpleEntry(K key, V value) {
			this.key = key;
			this.value = value;
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
			return value;
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
		
		@Override
		public <K, V> Collection<Entry<K, V>> fetchHeaders() {
			return (List)Arrays.asList(new SimpleEntry(CONTENT_TYPE, "text/plain"));
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

	
	public static final class CoroutineRunner implements Runnable {
		
		final NginxRequest request;
		NginxResponse response;
		
		
		public CoroutineRunner(NginxRequest request) {
			super();
			this.request = request;
		}

		@SuppressWarnings("rawtypes")
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
	}
	
	@Override
	public ResponseHeaderPusher fetchResponseHeaderPusher(String name) {
		ResponseHeaderPusher pusher = MiniConstants.KNOWN_RESP_HEADERS.get(name);
		if (pusher == null) {
			pusher = new ResponseUnknownHeaderPusher(name);
		}
		return pusher;
	}

	@Override
	public <K, V> long prepareHeaders(NginxRequest req, long status, Collection<Map.Entry<K, V>> headers) {
		long r = req.nativeRequest();
		long pool = NginxClojureRT.UNSAFE.getAddress(r + NGX_HTTP_CLOJURE_REQ_POOL_OFFSET);
		long headers_out = r + NGX_HTTP_CLOJURE_REQ_HEADERS_OUT_OFFSET;
		
		String contentType = null;
		String server = null;
		if (headers != null) {
			for (Map.Entry<?, ?> hen : headers) {
				Object nameObj = hen.getKey();
				Object val = hen.getValue();
				
				if (nameObj == null || val == null) {
					continue;
				}
				
				String name = normalizeHeaderName(nameObj);
				
				if (name == null || name.length() == 0 || "content-length".equals(name)) {
					continue;
				}
				
				if ("content-type".equals(name)) {
					if (val != null) {
						contentType = (String)val;
					}
					continue;
				}else if ("server".equals(name)) {
					server = (String)val;
					continue;
				}
				
				ResponseHeaderPusher pusher = fetchResponseHeaderPusher(name);
				pusher.push(headers_out, pool, val);
			}
		}
		
		if (server == null) {
			server = NGINX_CLOJURE_FULL_VER;
		}
		
		SERVER_PUSHER.push(headers_out, pool, server);
		
		if (contentType == null){
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
			long pool = UNSAFE.getAddress(r + NGX_HTTP_CLOJURE_REQ_POOL_OFFSET);
			int status = response.fetchStatus(NGX_HTTP_OK);

			
			Object body = response.fetchBody();
			long chain = 0;
			
			if (body != null) {
				chain = ngx_palloc(pool, NGX_HTTP_CLOJURE_CHAINT_SIZE);
				if (chain == 0) {
					return -NGX_HTTP_INTERNAL_SERVER_ERROR;
				}
				long tailChain = buildResponseItemBuf(r, pool, body, MiniConstants.NGX_CLOJURE_BUF_LAST_OF_RESPONSE, chain);
				if (tailChain == 0) {
					return -NGX_HTTP_INTERNAL_SERVER_ERROR;
				}else if (tailChain < 0 && tailChain != -204) {
					return tailChain;
				}
				if (tailChain == -NGX_HTTP_NO_CONTENT) {
					chain = -NGX_HTTP_NO_CONTENT;
				}else {
					UNSAFE.putAddress(tailChain + NGX_HTTP_CLOJURE_CHAIN_NEXT_OFFSET, 0);
				}
			}else {
				chain = -NGX_HTTP_NO_CONTENT;
			}
			
			if (chain == -NGX_HTTP_NO_CONTENT) {
				if (status == NGX_HTTP_OK) {
					status = NGX_HTTP_NO_CONTENT;
				}
				return -status;
			}
			
			return chain;

		}catch(Throwable e) {
			log.error("server unhandled exception!", e);
			return -NGX_HTTP_INTERNAL_SERVER_ERROR;
		}
	}
	
	protected  long buildResponseFileBuf(File f, long r, long pool, int isLast, long chain) {
		byte[] bytes = f.getPath().getBytes();
		long file = ngx_pcalloc(pool, bytes.length + 1);
		ngx_http_clojure_mem_copy_to_addr(bytes, BYTE_ARRAY_OFFSET, file, bytes.length);
		long rc = ngx_create_file_buf(r, file, bytes.length, isLast);
		
		if (rc <= 0) {
			return rc;
		}
		
		UNSAFE.putAddress(chain + NGX_HTTP_CLOJURE_CHAIN_BUF_OFFSET, rc);
		
		return chain;
	}
	
	//TODO: optimize handling inputstream with large lazy data
	protected  long buildResponseInputStreamBuf(InputStream in, long r, long pool, int isLast, long chain) {
		try {
			long lastBuf = 0;
			long lastChain = 0;
			
			while (true) {
				//TODO: buffer size should be the same as nginx buffer size
				byte[] buf = new byte[1024 * 32];
				int c = 0;
				int pos = 0;
				
				do {
					c = in.read(buf, pos, buf.length - pos);
					if (c > 0) {
						pos += c;
					}
				}while (c >= 0 && pos < buf.length);
				
				if (pos > 0) {
					lastBuf = ngx_create_temp_buf(r, pos);
					if (lastBuf <= 0) {
						return lastBuf;
					}
					ngx_http_clojure_mem_init_ngx_buf(lastBuf, buf, BYTE_ARRAY_OFFSET, pos, MiniConstants.NGX_CLOJURE_BUF_LAST_OF_NONE);
					
					if (lastChain != 0) {
						chain = ngx_palloc(pool, NGX_HTTP_CLOJURE_CHAINT_SIZE);
						if (chain == 0) {
							return 0;
						}
					}
					
					UNSAFE.putAddress(chain + NGX_HTTP_CLOJURE_CHAIN_BUF_OFFSET, lastBuf);
					
					if (lastChain != 0) {
						UNSAFE.putAddress(lastChain + NGX_HTTP_CLOJURE_CHAIN_NEXT_OFFSET, chain);
					}
					lastChain = chain;
				}
				
				if (c < 0) {
					break;
				}
				
				
			}
			
			if (isLast != MiniConstants.NGX_CLOJURE_BUF_LAST_OF_NONE && lastBuf > 0) {
				//only set last buffer flag 
				ngx_http_clojure_mem_init_ngx_buf(lastBuf, null, 0, 0, isLast);
			}
			
			//empty InputStream
			if (lastChain == 0) {
				return -NGX_HTTP_NO_CONTENT;
			}
			
			return lastChain;
			
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
	
	protected  long buildResponseStringBuf(String s, long r, long pool, int isLast, long chain) {
		if (s == null) {
			return 0;
		}
		
		if (s.length() == 0) {
			return -204;
		}
		
		byte[] bytes = s.getBytes(DEFAULT_ENCODING);
		long b = ngx_create_temp_buf(r, bytes.length);
		
		if (b <= 0) {
			return b;
		}
		
		ngx_http_clojure_mem_init_ngx_buf(b, bytes, BYTE_ARRAY_OFFSET, bytes.length, isLast);
		
		UNSAFE.putAddress(chain + NGX_HTTP_CLOJURE_CHAIN_BUF_OFFSET, b);
		
		return chain;
	}
	
	protected  long buildResponseIterableBuf(Iterable iterable, long r, long pool, int isLast, long chain) {
		if (iterable == null) {
			return 0;
		}
		
		Iterator i = iterable.iterator();
		if (!i.hasNext()) {
			return -204;
		}

		long lastChain = 0;
		while (i.hasNext()) {
			Object o = i.next();
			if (o != null) {
				
				if (lastChain != 0) {
					chain = ngx_palloc(pool, NGX_HTTP_CLOJURE_CHAINT_SIZE);
					if (chain == 0) {
						return 0;
					}
				}
				
				long subTail = 0;
				if (isLast != MiniConstants.NGX_CLOJURE_BUF_LAST_OF_NONE && !i.hasNext()) {
					subTail = buildResponseItemBuf(r, pool, o, isLast, chain);
				}else {
					subTail = buildResponseItemBuf(r, pool, o, MiniConstants.NGX_CLOJURE_BUF_LAST_OF_NONE, chain);
				}
				if (subTail <= 0 && subTail != -NGX_HTTP_NO_CONTENT) {
					return subTail;
				}
				if (lastChain != 0 && subTail != -NGX_HTTP_NO_CONTENT) {
					UNSAFE.putAddress(lastChain + NGX_HTTP_CLOJURE_CHAIN_NEXT_OFFSET, chain);
				}
				if (subTail != -NGX_HTTP_NO_CONTENT) {
					lastChain = subTail;
				}
			}
		}
		return lastChain;
	}
	
	
	protected  long buildResponseItemBuf(long r, long pool, Object item, int isLast, long chain) {

		if (item instanceof File) {
			return buildResponseFileBuf((File)item, r, pool, isLast, chain);
		}else if (item instanceof InputStream) {
			return buildResponseInputStreamBuf((InputStream)item, r, pool, isLast, chain);
		}else if (item instanceof String) {
			return buildResponseStringBuf((String)item, r, pool, isLast, chain);
		} 
		return buildResponseComplexItemBuf(r, pool, item, isLast, chain);
	}
	
	protected long buildResponseComplexItemBuf(long r, long pool, Object item, int isLast, long chain) {
		if (item == null) {
			return 0;
		}
		if (item instanceof Iterable) {
			return buildResponseIterableBuf((Iterable)item, r, pool, isLast, chain);
		}else if (item.getClass().isArray()) {
			return buildResponseIterableBuf(Arrays.asList((Object[])item), r, pool, isLast, chain);
		}
		return -NGX_HTTP_INTERNAL_SERVER_ERROR;
	}
	
	protected  String normalizeHeaderName(Object nameObj) {
		return normalizeHeaderNameHelper(nameObj);
	}

	public static String normalizeHeaderNameHelper(Object nameObj) {

		String name;
		if (nameObj instanceof String) {
			name = (String)nameObj;
		}else {
			name = nameObj.toString();
		}
		return name == null ? null : name.toLowerCase();
	
	}
}
