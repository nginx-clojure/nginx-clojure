package nginx.clojure;

import static nginx.clojure.MiniConstants.*;
import static nginx.clojure.MiniConstants.NGX_DONE;
import static nginx.clojure.MiniConstants.NGX_HTTP_INTERNAL_SERVER_ERROR;
import static nginx.clojure.MiniConstants.NGX_HTTP_OK;
import static nginx.clojure.MiniConstants.NR_ASYNC_TAG;
import static nginx.clojure.MiniConstants.NR_PHRASE_DONE;
import static nginx.clojure.NginxClojureRT.completeAsyncResponse;
import static nginx.clojure.NginxClojureRT.coroutineEnabled;
import static nginx.clojure.NginxClojureRT.handleResponse;
import static nginx.clojure.NginxClojureRT.log;
import static nginx.clojure.NginxClojureRT.ngx_http_clojure_mem_get_module_ctx_phase;
import static nginx.clojure.NginxClojureRT.ngx_http_clojure_mem_inc_req_count;
import static nginx.clojure.NginxClojureRT.workers;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.Callable;

import nginx.clojure.NginxClojureRT.WorkerResponseContext;


public abstract class NginxSimpleHandler implements NginxHandler {

	public abstract NginxRequest makeRequest(long r);
	
	@Override
	public int execute(final long r) {
		
		if (r == 0) { //by worker init process
			NginxResponse resp = handleRequest(makeRequest(0));
			if (resp != null && resp != NR_ASYNC_TAG && resp.fetchStatus(200) != 200) {
				log.error("initialize error %s", resp);
				return NGX_HTTP_INTERNAL_SERVER_ERROR;
			}
			return NGX_HTTP_OK;
		}
		
		final NginxRequest req = makeRequest(r);
		int phase = (int)ngx_http_clojure_mem_get_module_ctx_phase(r);
		
		if (workers == null) {
			NginxResponse resp = handleRequest(req);
			if (resp == NR_ASYNC_TAG) {
				if (phase == -1) { //from content handler invoking 
					ngx_http_clojure_mem_inc_req_count(r);
				}
				return NGX_DONE;
			}
			return handleResponse(r, resp);
		}
		
		//for safe access with another thread
		req.prefetchAll();

		ngx_http_clojure_mem_inc_req_count(r);
		workers.submit(new Callable<NginxClojureRT.WorkerResponseContext>() {
			@Override
			public WorkerResponseContext call() throws Exception {
				NginxResponse resp = handleRequest(req);
				//let output chain built before entering the main thread
				return new WorkerResponseContext(r, resp, resp == NR_PHRASE_DONE ? 0 : resp.buildOutputChain(r));
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
					return NR_ASYNC_TAG;
				}
			}else {
				return req.process();
			}
		}catch(Throwable e){
			log.error("server unhandled exception!", e);
			return buildUnhandledExceptionResponse(e);
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
		
		public NginxUnhandledExceptionResponse(Throwable e) {
			this.err = e;
		}
		
		@Override
		public int fetchStatus(int defaultStatus) {
			return 500;
		}
		
		@Override
		public Collection<Entry> fetchHeaders() {
			return (Collection<Entry>)(List)Arrays.asList(new SimpleEntry(CONTENT_TYPE, "text/plain"));
		}
		@Override
		public Object fetchBody() {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			err.printStackTrace(pw);
			pw.close();
			return sw.toString();
		}
	}

	public static NginxResponse buildUnhandledExceptionResponse(Throwable e) {
		return new NginxUnhandledExceptionResponse(e);
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
				response = request.process();
			}catch(Throwable e) {
				response = buildUnhandledExceptionResponse(e);
			}
			
			if (Coroutine.getActiveCoroutine().getResumeCounter() != 1) {
				completeAsyncResponse(request.nativeRequest(), response);
			}
		}
	}
}
