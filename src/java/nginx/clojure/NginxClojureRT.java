/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure;

import static nginx.clojure.Constants.*;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

import nginx.clojure.logger.LoggerService;
import nginx.clojure.logger.TinyLogService;
import nginx.clojure.net.NginxClojureSocketFactory;
import nginx.clojure.net.NginxClojureSocketImpl;
import nginx.clojure.wave.JavaAgent;
import sun.misc.Unsafe;
import clojure.lang.IFn;
import clojure.lang.ISeq;
import clojure.lang.Keyword;
import clojure.lang.PersistentArrayMap;
import clojure.lang.RT;
import clojure.lang.Seqable;

public class NginxClojureRT {


	public static final Map ASYNC_TAG = new HashMap(0);
	
	public static final Map PHRASE_DONE = new HashMap(0);
	
	public static long[] MEM_INDEX;
	
	public static Thread NGINX_MAIN_THREAD;
	
	/*use it carefully!!*/
	public static Unsafe UNSAFE = null;
	
	private static List<IFn>  HANDLERS = new ArrayList<IFn>();
	
	//mapping clojure code pointer address to clojure code id 
	private static Map<Long, Integer> CODE_MAP = new HashMap<Long, Integer>();
	
	private static ConcurrentHashMap<Long, Object> POSTED_EVENTS_DATA = new ConcurrentHashMap<Long, Object>();
	
	private static ExecutorService eventDispather;
	
	private static CompletionService<HandlerContext> workers;
	
	//only for testing, e.g. with lein-ring where no coroutine support
	private static ExecutorService threadPoolOnlyForTestingUsage;
	
	private static boolean coroutineEnabled = false;
	
	private static LoggerService log;
	
	public native static long ngx_palloc(long pool, long size);
	
	public native static long ngx_pcalloc(long pool, long size);
	
	public native static long ngx_array_create(long pool, long n, long size);

	public native static long ngx_array_init(long array, long pool, long n, long size);

	public native static long ngx_array_push_n(long array, long n);

	public native static long ngx_list_create(long pool, long n, long size);

	public native static long ngx_list_init(long list, long pool, long n, long size);

	public native static long ngx_list_push(long list);
	
	
	public native static long ngx_create_temp_buf(long r, long size);
	
	public native static long ngx_create_file_buf(long r, long file, long name_len, int last_buf);
	
	public native static long ngx_http_set_content_type(long r);
	
	public native static long ngx_http_send_header(long r);
	
	public native static long ngx_http_output_filter(long r, long chain);
	
	public native static void ngx_http_finalize_request(long r, long rc);

	public native static long ngx_http_clojure_mem_init_ngx_buf(long buf, Object obj, long offset, long len, int last_buf);
	
	public native static long ngx_http_clojure_mem_get_obj_addr(Object obj);
	
	public native static long ngx_http_clojure_mem_get_list_size(long l);
	
	public native static long ngx_http_clojure_mem_get_list_item(long l, long i);
	
	public native static void ngx_http_clojure_mem_copy_to_obj(long src, Object obj, long offset, long len);
	
	public native static void ngx_http_clojure_mem_copy_to_addr(Object obj, long offset, long dest, long len);
	
	public native static long ngx_http_clojure_mem_get_header(long headers_in, long name, long len);
	
	public native static long ngx_http_clojure_mem_get_variable(long r, long name, long varlenPtr);
	
	public native static long ngx_http_clojure_mem_set_variable(long r, long name, long val, long vlen);
	
	public native static void ngx_http_clojure_mem_inc_req_count(long r);
	
	public native static void ngx_http_clojure_mem_continue_current_phase(long r);
	
	public native static long ngx_http_clojure_mem_get_module_ctx_phase(long r);
	
	public native static void ngx_http_clojure_mem_post_event(long r);
	
//	public native static long ngx_http_clojure_mem_get_body_tmp_file(long r);
	
	static {
		//be friendly to lein ring testing
		getLog();
		initUnsafe();
	}
	
	public static String formatVer(long ver) {
		long f = ver / 1000000;
		long s = ver / 1000 - f * 1000;
		long t = ver - s * 1000 - f * 1000000;
		return f + "." + s + "." + t;
	}
	
	public static final class HandlerContext {
		public final LazyRequestMap request;
		public final Map response;
		public final long chain;
		public HandlerContext(LazyRequestMap request, Map response, long chain) {
			this.request =request;
			this.response = response;
			this.chain = chain;
		}
	}
	
	public static final class CoroutineRunner implements Runnable {
		
		final LazyRequestMap request;
		Map response;
		final IFn handler;
		
		
		public CoroutineRunner(IFn handler, LazyRequestMap request) {
			super();
			this.handler = handler;
			this.request = request;
		}

		@SuppressWarnings("rawtypes")
		@Override
		public void run() throws SuspendExecution {
			try {
				response = (Map) handler.invoke(request);
			}catch(Throwable e) {
				response = buildUnhandledExceptionResponse(e);
			}
			
			if (Coroutine.getActiveCoroutine().getResumeCounter() != 1) {
				completeAsyncResponse(request.r, response);
			}
		}
	}
	
	public static final class EventDispatherRunnable implements Runnable {
		
		final CompletionService<HandlerContext> workers;
		
		public EventDispatherRunnable(final CompletionService<HandlerContext> workers) {
			this.workers = workers;
		}
		
		@Override
		public void run() {
			while (true) {
				try {
					Future<HandlerContext> respFuture =  workers.take();
					HandlerContext ctx = respFuture.get();
					savePostEventData(ctx.request.r, ctx);
					ngx_http_clojure_mem_post_event(ctx.request.r);
				} catch (InterruptedException e) {
					log.error("interrupted!", e);
					break;
				} catch (ExecutionException e) {
					log.error("unexpected ExecutionException!", e);
				}
			}
		}
	}
	
	public static void initWorkers(int n) {
		
		if (JavaAgent.db != null) {
			if (JavaAgent.db.isDoNothing()) {
				coroutineEnabled = false;
				log.warn("java agent disabled so we turn off coroutine support!");
				if (n == 0) {
					n = -1;
				}
			}else if (JavaAgent.db.isRunTool()) {
				coroutineEnabled = false;
				log.warn("we just run for generatation of coroutine waving configuration NOT for general cases!!!");
/* 
 * Because sometimes we need to access services provide by the same nginx instance, 
 * e.g. proxyed external http service, so when turn on run tool mode we need thread 
 * pool to make worker not blocked otherwise we can not continue the process of generatation 
 * of coroutine waving configuration.*/				
				if (n < 1) {
					log.warn("enable thread pool mode for run tool mode so that %s", 
							"worker won't be blocked when access services provide by the same nginx instance");
					n = Runtime.getRuntime().availableProcessors() * 2;
				}
			}else {
				log.info("java agent configured so we turn on coroutine support!");
				if (n > 0) {
					log.warn("found jvm_workers = %d, and not = 0 we just ignored!", n);
				}
				n = 0;
			}
		}
		
		if (n == 0) {
			if (JavaAgent.db == null) {
				log.warn("java agent NOT configured so we turn off coroutine support!");
				coroutineEnabled = false;
			}else {
				coroutineEnabled = true;
			}
			try {
				Socket.setSocketImplFactory(new NginxClojureSocketFactory());
			} catch (IOException e) {
				throw new RuntimeException("can not init NginxClojureSocketFactory!", e);
			}
			return;
		}
		if (n < 0) {
			return;
		}
		
		eventDispather = Executors.newSingleThreadExecutor(new ThreadFactory() {
			@Override
			public Thread newThread(Runnable r) {
				return new Thread(r, "nginx-clojure-eventDispather");
			}
		});
		
		workers = new ExecutorCompletionService<HandlerContext>(Executors.newFixedThreadPool(n, new ThreadFactory() {
			final AtomicLong counter = new AtomicLong(0);
			public Thread newThread(Runnable r) {
				return new Thread(r, "nginx-clojure-worker-" + counter.getAndIncrement());
			}
		}));
		
		eventDispather.submit(new EventDispatherRunnable(workers));
	}
	
	public static synchronized ExecutorService initThreadPoolOnlyForTestingUsage() {
		if (threadPoolOnlyForTestingUsage == null) {
			threadPoolOnlyForTestingUsage = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()+2, new ThreadFactory() {
				final AtomicLong counter = new AtomicLong(0);
				public Thread newThread(Runnable r) {
					return new Thread(r, "nginx-clojure-only4test-thread" + counter.getAndIncrement());
				}
			});
		}
		return threadPoolOnlyForTestingUsage;
	}
	
	public static synchronized void initMemIndex(long idxpt) {
		getLog();
		initUnsafe();
		
		//hack mysql jdbc driver to keep from creating connections by reflective invoking the constructor
		try {
			Class mysqljdbcUtilClz = Thread.currentThread().getContextClassLoader().loadClass("com.mysql.jdbc.Util");
			Field  isJdbc4Field = mysqljdbcUtilClz.getDeclaredField("isJdbc4");
			isJdbc4Field.setAccessible(true);
			isJdbc4Field.set(null, false);
		} catch (Throwable e) {
		}
	    
		NGINX_MAIN_THREAD = Thread.currentThread();
		
	    BYTE_ARRAY_OFFSET = UNSAFE.arrayBaseOffset(byte[].class);
	    
	    long[] index = new long[NGX_HTTP_CLOJURE_MEM_IDX_END + 1];
	    for (int i = 0; i < NGX_HTTP_CLOJURE_MEM_IDX_END + 1; i++) {
	    	index[i] = UNSAFE.getLong(idxpt + i * 8);
	    }
	    
	    
		MEM_INDEX = index;
		NGX_HTTP_CLOJURE_UINT_SIZE = MEM_INDEX[NGX_HTTP_CLOJURE_UINT_SIZE_IDX];
		
		NGX_HTTP_CLOJURE_PTR_SIZE = MEM_INDEX[NGX_HTTP_CLOJURE_PTR_SIZE_IDX];
		
		NGX_HTTP_CLOJURE_STR_SIZE = MEM_INDEX[NGX_HTTP_CLOJURE_STR_SIZE_IDX];
		NGX_HTTP_CLOJURE_STR_LEN_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_STR_LEN_IDX];
		NGX_HTTP_CLOJURE_STR_DATA_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_STR_DATA_IDX];
		NGX_HTTP_CLOJURE_SIZET_SIZE = MEM_INDEX[NGX_HTTP_CLOJURE_SIZET_SIZE_IDX];
		NGX_HTTP_CLOJURE_OFFT_SIZE = MEM_INDEX[NGX_HTTP_CLOJURE_OFFT_SIZE_IDX];
		
		NGX_HTTP_CLOJURE_TELT_SIZE = MEM_INDEX[NGX_HTTP_CLOJURE_TELT_SIZE_IDX];
		NGX_HTTP_CLOJURE_TEL_HASH_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_TEL_HASH_IDX];
		NGX_HTTP_CLOJURE_TEL_KEY_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_TEL_KEY_IDX];
		NGX_HTTP_CLOJURE_TEL_VALUE_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_TEL_VALUE_IDX];
		NGX_HTTP_CLOJURE_TEL_LOWCASE_KEY_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_TEL_LOWCASE_KEY_IDX];
		
		NGX_HTTP_CLOJURE_REQT_SIZE = MEM_INDEX[NGX_HTTP_CLOJURE_REQT_SIZE_IDX];
		NGX_HTTP_CLOJURE_REQ_METHOD_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_REQ_METHOD_IDX];
		NGX_HTTP_CLOJURE_REQ_URI_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_REQ_URI_IDX];
		NGX_HTTP_CLOJURE_REQ_ARGS_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_REQ_ARGS_IDX];
		NGX_HTTP_CLOJURE_REQ_HEADERS_IN_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_REQ_HEADERS_IN_IDX];
		NGX_HTTP_CLOJURE_REQ_POOL_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_REQ_POOL_IDX];
		NGX_HTTP_CLOJURE_REQ_HEADERS_OUT_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_REQ_HEADERS_OUT_IDX];
		
		NGX_HTTP_CLOJURE_CHAINT_SIZE = MEM_INDEX[NGX_HTTP_CLOJURE_CHAINT_SIZE_IDX];
		NGX_HTTP_CLOJURE_CHAIN_BUF_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_CHAIN_BUF_IDX];
		NGX_HTTP_CLOJURE_CHAIN_NEXT_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_CHAIN_NEXT_IDX];
		
		NGX_HTTP_CLOJURE_VARIABLET_SIZE = MEM_INDEX[NGX_HTTP_CLOJURE_VARIABLET_SIZE_IDX];
		NGX_HTTP_CLOJURE_CORE_VARIABLES_ADDR = MEM_INDEX[NGX_HTTP_CLOJURE_CORE_VARIABLES_ADDR_IDX];
		NGX_HTTP_CLOJURE_CORE_VARIABLES_LEN = MEM_INDEX[NGX_HTTP_CLOJURE_CORE_VARIABLES_LEN_IDX];
		
		
		NGX_HTTP_CLOJURE_ARRAYT_SIZE = MEM_INDEX[NGX_HTTP_CLOJURE_ARRAYT_SIZE_IDX];
		NGX_HTTP_CLOJURE_ARRAY_ELTS_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_ARRAY_ELTS_IDX];
		NGX_HTTP_CLOJURE_ARRAY_NELTS_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_ARRAY_NELTS_IDX];
		NGX_HTTP_CLOJURE_ARRAY_SIZE_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_ARRAY_SIZE_IDX];
		NGX_HTTP_CLOJURE_ARRAY_NALLOC_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_ARRAY_NALLOC_IDX];
		NGX_HTTP_CLOJURE_ARRAY_POOL_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_ARRAY_POOL_IDX];
		
		NGX_HTTP_CLOJURE_HEADERSIT_SIZE =  MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSIT_SIZE_IDX];
		NGX_HTTP_CLOJURE_HEADERSI_HOST_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSI_HOST_IDX];
		NGX_HTTP_CLOJURE_HEADERSI_CONNECTION_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSI_CONNECTION_IDX];
		NGX_HTTP_CLOJURE_HEADERSI_IF_MODIFIED_SINCE_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSI_IF_MODIFIED_SINCE_IDX];
		NGX_HTTP_CLOJURE_HEADERSI_IF_UNMODIFIED_SINCE_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSI_IF_UNMODIFIED_SINCE_IDX];
		NGX_HTTP_CLOJURE_HEADERSI_USER_AGENT_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSI_USER_AGENT_IDX];
		NGX_HTTP_CLOJURE_HEADERSI_REFERER_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSI_REFERER_IDX];
		NGX_HTTP_CLOJURE_HEADERSI_CONTENT_LENGTH_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSI_CONTENT_LENGTH_IDX];
		NGX_HTTP_CLOJURE_HEADERSI_CONTENT_TYPE_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSI_CONTENT_TYPE_IDX];
		NGX_HTTP_CLOJURE_HEADERSI_RANGE_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSI_RANGE_IDX];
		NGX_HTTP_CLOJURE_HEADERSI_IF_RANGE_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSI_IF_RANGE_IDX];
		NGX_HTTP_CLOJURE_HEADERSI_TRANSFER_ENCODING_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSI_TRANSFER_ENCODING_IDX];
		NGX_HTTP_CLOJURE_HEADERSI_EXPECT_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSI_EXPECT_IDX];

		//#if (NGX_HTTP_GZIP)
		NGX_HTTP_CLOJURE_HEADERSI_ACCEPT_ENCODING_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSI_ACCEPT_ENCODING_IDX];
		NGX_HTTP_CLOJURE_HEADERSI_VIA_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSI_VIA_IDX];
		//#endif

		MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSI_AUTHORIZATION_IDX] =  NGX_HTTP_CLOJURE_HEADERSI_AUTHORIZATION_OFFSET;
		MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSI_KEEP_ALIVE_IDX] =  NGX_HTTP_CLOJURE_HEADERSI_KEEP_ALIVE_OFFSET;

		//#if (NGX_HTTP_PROXY || NGX_HTTP_REALIP || NGX_HTTP_GEO)
		NGX_HTTP_CLOJURE_HEADERSI_X_FORWARDED_FOR_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSI_X_FORWARDED_FOR_IDX];
		//#endif

		//#if (NGX_HTTP_REALIP)
		NGX_HTTP_CLOJURE_HEADERSI_X_REAL_IP_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSI_X_REAL_IP_IDX];
		//#endif

		//#if (NGX_HTTP_HEADERS)
		NGX_HTTP_CLOJURE_HEADERSI_ACCEPT_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSI_ACCEPT_IDX];
		NGX_HTTP_CLOJURE_HEADERSI_ACCEPT_LANGUAGE_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSI_ACCEPT_LANGUAGE_IDX];
		//#endif

		//#if (NGX_HTTP_DAV)
		NGX_HTTP_CLOJURE_HEADERSI_DEPTH_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSI_DEPTH_IDX];
		NGX_HTTP_CLOJURE_HEADERSI_DESTINATION_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSI_DESTINATION_IDX];
		NGX_HTTP_CLOJURE_HEADERSI_OVERWRITE_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSI_OVERWRITE_IDX];
		NGX_HTTP_CLOJURE_HEADERSI_DATE_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSI_DATE_IDX];
		//#endif

		NGX_HTTP_CLOJURE_HEADERSI_USER_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSI_USER_IDX];
		NGX_HTTP_CLOJURE_HEADERSI_PASSWD_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSI_PASSWD_IDX];
		NGX_HTTP_CLOJURE_HEADERSI_COOKIE_OFFSET =MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSI_COOKIE_IDX];
		NGX_HTTP_CLOJURE_HEADERSI_SERVER_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSI_SERVER_IDX];
		NGX_HTTP_CLOJURE_HEADERSI_CONTENT_LENGTH_N_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSI_CONTENT_LENGTH_N_IDX];
		NGX_HTTP_CLOJURE_HEADERSI_KEEP_ALIVE_N_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSI_KEEP_ALIVE_N_IDX];
		NGX_HTTP_CLOJURE_HEADERSI_HEADERS_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSI_HEADERS_IDX];


		/*index for size of ngx_http_headers_out_t */
		NGX_HTTP_CLOJURE_HEADERSOT_SIZE = MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSOT_SIZE_IDX];
		/*field offset index for ngx_http_headers_out_t*/
		NGX_HTTP_CLOJURE_HEADERSO_STATUS_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSO_STATUS_IDX];
		NGX_HTTP_CLOJURE_HEADERSO_STATUS_LINE_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSO_STATUS_LINE_IDX];
		NGX_HTTP_CLOJURE_HEADERSO_SERVER_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSO_SERVER_IDX];
		NGX_HTTP_CLOJURE_HEADERSO_DATE_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSO_DATE_IDX];
		NGX_HTTP_CLOJURE_HEADERSO_CONTENT_LENGTH_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSO_CONTENT_LENGTH_IDX];
		NGX_HTTP_CLOJURE_HEADERSO_CONTENT_ENCODING_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSO_CONTENT_ENCODING_IDX];
		NGX_HTTP_CLOJURE_HEADERSO_LOCATION_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSO_LOCATION_IDX];
		NGX_HTTP_CLOJURE_HEADERSO_REFRESH_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSO_REFRESH_IDX];
		NGX_HTTP_CLOJURE_HEADERSO_LAST_MODIFIED_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSO_LAST_MODIFIED_IDX];
		NGX_HTTP_CLOJURE_HEADERSO_CONTENT_RANGE_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSO_CONTENT_RANGE_IDX];
		NGX_HTTP_CLOJURE_HEADERSO_ACCEPT_RANGES_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSO_ACCEPT_RANGES_IDX];
		NGX_HTTP_CLOJURE_HEADERSO_WWW_AUTHENTICATE_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSO_WWW_AUTHENTICATE_IDX];
		NGX_HTTP_CLOJURE_HEADERSO_EXPIRES_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSO_EXPIRES_IDX];
		NGX_HTTP_CLOJURE_HEADERSO_ETAG_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSO_ETAG_IDX];
		NGX_HTTP_CLOJURE_HEADERSO_OVERRIDE_CHARSET_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSO_OVERRIDE_CHARSET_IDX];
		NGX_HTTP_CLOJURE_HEADERSO_CONTENT_TYPE_LEN_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSO_CONTENT_TYPE_LEN_IDX];
		NGX_HTTP_CLOJURE_HEADERSO_CONTENT_TYPE_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSO_CONTENT_TYPE_IDX];
		NGX_HTTP_CLOJURE_HEADERSO_CHARSET_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSO_CHARSET_IDX];
		NGX_HTTP_CLOJURE_HEADERSO_CONTENT_TYPE_LOWCASE_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSO_CONTENT_TYPE_LOWCASE_IDX];
		NGX_HTTP_CLOJURE_HEADERSO_CONTENT_TYPE_HASH_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSO_CONTENT_TYPE_HASH_IDX];
		NGX_HTTP_CLOJURE_HEADERSO_CACHE_CONTROL_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSO_CACHE_CONTROL_IDX];
		NGX_HTTP_CLOJURE_HEADERSO_CONTENT_LENGTH_N_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSO_CONTENT_LENGTH_N_IDX];
		NGX_HTTP_CLOJURE_HEADERSO_DATE_TIME_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSO_DATE_TIME_IDX];
		NGX_HTTP_CLOJURE_HEADERSO_LAST_MODIFIED_TIME_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSO_LAST_MODIFIED_TIME_IDX];
		NGX_HTTP_CLOJURE_HEADERSO_HEADERS_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSO_HEADERS_IDX];
		
		NGINX_CLOJURE_MODULE_CTX_PHRASE_ID_OFFSET = MEM_INDEX[NGINX_CLOJURE_MODULE_CTX_PHRASE_ID];

		
		NGINX_CLOJURE_RT_WORKERS = MEM_INDEX[NGINX_CLOJURE_RT_WORKERS_ID];
		NGINX_CLOJURE_VER = MEM_INDEX[NGINX_CLOJURE_VER_ID];
		NGINX_VER = MEM_INDEX[NGINX_VER_ID];
		
		if (NGINX_CLOJURE_RT_REQUIRED_LVER > NGINX_CLOJURE_VER) {
			throw new IllegalStateException("NginxClojureRT required version is >=" + formatVer(NGINX_CLOJURE_RT_REQUIRED_LVER) + ", but here is " + formatVer(NGINX_CLOJURE_VER));
		}
		NGINX_CLOJURE_FULL_VER = "nginx-clojure/" + formatVer(NGINX_VER) + "-" + formatVer(NGINX_CLOJURE_RT_VER);
		
		KNOWN_REQ_HEADERS.put("host", NGX_HTTP_CLOJURE_HEADERSI_HOST_OFFSET);
		KNOWN_REQ_HEADERS.put("connection", NGX_HTTP_CLOJURE_HEADERSI_CONNECTION_OFFSET);
		KNOWN_REQ_HEADERS.put("if-modified-since", NGX_HTTP_CLOJURE_HEADERSI_IF_MODIFIED_SINCE_OFFSET);
		KNOWN_REQ_HEADERS.put("if-unmodified-since", NGX_HTTP_CLOJURE_HEADERSI_IF_UNMODIFIED_SINCE_OFFSET);
		KNOWN_REQ_HEADERS.put("user-agent", NGX_HTTP_CLOJURE_HEADERSI_USER_AGENT_OFFSET);
		KNOWN_REQ_HEADERS.put("referer", NGX_HTTP_CLOJURE_HEADERSI_REFERER_OFFSET);
		KNOWN_REQ_HEADERS.put("content-length", NGX_HTTP_CLOJURE_HEADERSI_CONTENT_LENGTH_OFFSET);
		KNOWN_REQ_HEADERS.put("content-type", NGX_HTTP_CLOJURE_HEADERSI_CONTENT_TYPE_OFFSET);
		KNOWN_REQ_HEADERS.put("range", NGX_HTTP_CLOJURE_HEADERSI_RANGE_OFFSET);
		KNOWN_REQ_HEADERS.put("if-range", NGX_HTTP_CLOJURE_HEADERSI_IF_RANGE_OFFSET);
		KNOWN_REQ_HEADERS.put("transfer-encoding", NGX_HTTP_CLOJURE_HEADERSI_TRANSFER_ENCODING_OFFSET);
		KNOWN_REQ_HEADERS.put("expect", NGX_HTTP_CLOJURE_HEADERSI_EXPECT_OFFSET);
		KNOWN_REQ_HEADERS.put("accept-encoding", NGX_HTTP_CLOJURE_HEADERSI_ACCEPT_ENCODING_OFFSET);
		KNOWN_REQ_HEADERS.put("via", NGX_HTTP_CLOJURE_HEADERSI_VIA_OFFSET);
		KNOWN_REQ_HEADERS.put("authorization", NGX_HTTP_CLOJURE_HEADERSI_AUTHORIZATION_OFFSET);
		KNOWN_REQ_HEADERS.put("keep-alive", NGX_HTTP_CLOJURE_HEADERSI_KEEP_ALIVE_OFFSET);
		KNOWN_REQ_HEADERS.put("x-forwarded-for", NGX_HTTP_CLOJURE_HEADERSI_X_FORWARDED_FOR_OFFSET);
		KNOWN_REQ_HEADERS.put("x-real-ip", NGX_HTTP_CLOJURE_HEADERSI_X_REAL_IP_OFFSET);
		KNOWN_REQ_HEADERS.put("accept", NGX_HTTP_CLOJURE_HEADERSI_ACCEPT_OFFSET);

		KNOWN_REQ_HEADERS.put("accept-language", NGX_HTTP_CLOJURE_HEADERSI_ACCEPT_LANGUAGE_OFFSET);
		KNOWN_REQ_HEADERS.put("depth", NGX_HTTP_CLOJURE_HEADERSI_DEPTH_OFFSET);
		KNOWN_REQ_HEADERS.put("destination", NGX_HTTP_CLOJURE_HEADERSI_DESTINATION_OFFSET);
		KNOWN_REQ_HEADERS.put("overwrite", NGX_HTTP_CLOJURE_HEADERSI_OVERWRITE_OFFSET);
		KNOWN_REQ_HEADERS.put("date", NGX_HTTP_CLOJURE_HEADERSI_DATE_OFFSET);

		KNOWN_REQ_HEADERS.put("cookie", NGX_HTTP_CLOJURE_HEADERSI_COOKIE_OFFSET);
		
		

		for (int i = 0; i < NGX_HTTP_CLOJURE_CORE_VARIABLES_LEN; i++) {
			long addr = NGX_HTTP_CLOJURE_CORE_VARIABLES_ADDR + i * NGX_HTTP_CLOJURE_STR_SIZE;
			CORE_VARS.put(fetchNGXString(addr, DEFAULT_ENCODING), addr);
		}
		
		SERVER_PORT_FETCHER = new RequestKnownNameVarFetcher("server_port");
		SERVER_NAME_FETCHER = new RequestKnownNameVarFetcher("server_name");
		REMOTE_ADDR_FETCHER = new RequestKnownNameVarFetcher("remote_addr");
		URI_FETCHER = new RequestKnownOffsetVarFetcher(NGX_HTTP_CLOJURE_REQ_URI_OFFSET);
		QUERY_STRING_FETCHER = new RequestKnownOffsetVarFetcher(NGX_HTTP_CLOJURE_REQ_ARGS_OFFSET);
		SCHEME_FETCHER = new RequestKnownNameVarFetcher("scheme");
		REQUEST_METHOD_FETCHER = new RequestMethodFetcher();
		CONTENT_TYPE_FETCHER = new RequestKnownHeaderFetcher("content-type");
		CHARACTER_ENCODING_FETCHER = new RequestCharacterEncodingFetcher();
		HEADER_FETCHER = new RequestHeadersFetcher();
		BODY_FETCHER = new RequestBodyFetcher();
		
		KNOWN_RESP_HEADERS.put("server", SERVER_PUSHER = new ResponseTableEltHeaderPusher("server", NGX_HTTP_CLOJURE_HEADERSO_SERVER_OFFSET));
		KNOWN_RESP_HEADERS.put("date", new ResponseTableEltHeaderPusher("date", NGX_HTTP_CLOJURE_HEADERSO_DATE_OFFSET));
		KNOWN_RESP_HEADERS.put("content-length", new ResponseTableEltHeaderPusher("content-length", NGX_HTTP_CLOJURE_HEADERSO_CONTENT_LENGTH_OFFSET));
		KNOWN_RESP_HEADERS.put("content-encoding", new ResponseTableEltHeaderPusher("content-encoding", NGX_HTTP_CLOJURE_HEADERSO_CONTENT_ENCODING_OFFSET));
		KNOWN_RESP_HEADERS.put("location", new ResponseTableEltHeaderPusher("location", NGX_HTTP_CLOJURE_HEADERSO_LOCATION_OFFSET));
		KNOWN_RESP_HEADERS.put("refresh", new ResponseTableEltHeaderPusher("refresh", NGX_HTTP_CLOJURE_HEADERSO_REFRESH_OFFSET));
		KNOWN_RESP_HEADERS.put("last-modified", new ResponseTableEltHeaderPusher("last-modified", NGX_HTTP_CLOJURE_HEADERSO_LAST_MODIFIED_OFFSET));
		KNOWN_RESP_HEADERS.put("content-range", new ResponseTableEltHeaderPusher("content-range", NGX_HTTP_CLOJURE_HEADERSO_CONTENT_RANGE_OFFSET));
		KNOWN_RESP_HEADERS.put("accept-ranges", new ResponseTableEltHeaderPusher("accept-ranges", NGX_HTTP_CLOJURE_HEADERSO_ACCEPT_RANGES_OFFSET));
		KNOWN_RESP_HEADERS.put("www-authenticate", new ResponseTableEltHeaderPusher("www-authenticate", NGX_HTTP_CLOJURE_HEADERSO_WWW_AUTHENTICATE_OFFSET));
		KNOWN_RESP_HEADERS.put("expires", new ResponseTableEltHeaderPusher("expires", NGX_HTTP_CLOJURE_HEADERSO_EXPIRES_OFFSET));
		KNOWN_RESP_HEADERS.put("etag", new ResponseTableEltHeaderPusher("etag", NGX_HTTP_CLOJURE_HEADERSO_ETAG_OFFSET));
		KNOWN_RESP_HEADERS.put("cache-control", new ResponseArrayHeaderPusher("cache-control", NGX_HTTP_CLOJURE_HEADERSO_CACHE_CONTROL_OFFSET));
		
		initWorkers((int)NGINX_CLOJURE_RT_WORKERS);
	}

	public static void initUnsafe() {
		if (UNSAFE != null) {
			return;
		}
	    try{
	        Field field = Unsafe.class.getDeclaredField("theUnsafe");
	        field.setAccessible(true);
	        UNSAFE = (Unsafe)field.get(null);
	    }
	    catch (Exception e){
	        throw new RuntimeException(e);
	    }
	}
	
	
	public static synchronized int registerCode(long codePtr, long len) {
		if (CODE_MAP.containsKey(codePtr)) {
			return CODE_MAP.get(codePtr);
		}
		String code = fetchString(codePtr, (int)len, DEFAULT_ENCODING);
		IFn f = (IFn)RT.var("clojure.core", "eval").invoke(RT.var("clojure.core","read-string").invoke(code));
		HANDLERS.add(f);
		return HANDLERS.size() - 1;
	}
	
	/**
	 * convert ngx_str_t to  java String
	 */
	public static final String fetchNGXString(long address, Charset encoding) {
		if (address == 0){
			return null;
		}
		long lenAddr = address + NGX_HTTP_CLOJURE_STR_LEN_OFFSET;
		int len = fetchNGXInt(lenAddr);
		if (len == 0){
			return null;
		}
		return fetchString(address + NGX_HTTP_CLOJURE_STR_DATA_OFFSET, len, encoding);
	}
	
	public static final int pushNGXString(long address, String val, Charset encoding, long pool){
			long lenAddr = address + NGX_HTTP_CLOJURE_STR_LEN_OFFSET;
			long dataAddr = address + NGX_HTTP_CLOJURE_STR_DATA_OFFSET;
			int len = pushString(dataAddr, val, encoding, pool);
			pushNGXInt(lenAddr, len);
			return len;
	}
	
	
	public static final int fetchNGXInt(long address){
		return NGX_HTTP_CLOJURE_UINT_SIZE == 4 ? UNSAFE.getInt(address) : (int)UNSAFE.getLong(address);
	}
	
	public static final void pushNGXInt(long address, int val){
		if (NGX_HTTP_CLOJURE_UINT_SIZE == 4){
			UNSAFE.putInt(address, val);
		}else {
			UNSAFE.putLong(address, val);
		}
	}
	
	public static final void pushNGXOfft(long address, int val){
		if (NGX_HTTP_CLOJURE_OFFT_SIZE == 4){
			UNSAFE.putInt(address, val);
		}else {
			UNSAFE.putLong(address, val);
		}
	}
	
	public static final void pushNGXSizet(long address, int val){
		if (NGX_HTTP_CLOJURE_SIZET_SIZE == 4){
			UNSAFE.putInt(address, val);
		}else {
			UNSAFE.putLong(address, val);
		}
	}
	
	
	//TODO: for better performance to use direct encoder instead of bytes copy
	public static final String fetchString(long address, int size, Charset encoding) {
		byte[] buf = new byte[size];
		ngx_http_clojure_mem_copy_to_obj(UNSAFE.getAddress(address), buf, BYTE_ARRAY_OFFSET, size);
		return new String(buf, encoding);

	}
	
	
	public static final int pushString(long address, String val, Charset encoding, long pool) {
		byte[] bytes = val.getBytes(encoding);
		long strAddr = ngx_palloc(pool, bytes.length);
		UNSAFE.putAddress(address, strAddr);
		ngx_http_clojure_mem_copy_to_addr(bytes, BYTE_ARRAY_OFFSET, strAddr, bytes.length);
		return bytes.length;
	}
	
	public static final String getNGXVariable(long r, String name) {
		if (CORE_VARS.containsKey(name)) {
			return (String) new RequestKnownNameVarFetcher(name).fetch(r, DEFAULT_ENCODING);
		}
		return (String) new RequestUnknownNameVarFetcher(name).fetch(r, DEFAULT_ENCODING);
	}
	
	public static final int setNGXVariable(long r, String name, String val) {
		long np = CORE_VARS.containsKey(name) ? CORE_VARS.get(name) : 0;
		long pool = UNSAFE.getAddress(r + NGX_HTTP_CLOJURE_REQ_POOL_OFFSET);
		if (np == 0) {
			np = ngx_palloc(pool, Constants.NGX_HTTP_CLOJURE_STR_SIZE);
			pushNGXString(np, name, DEFAULT_ENCODING, pool);
		}
		byte[] bytes = val.getBytes(DEFAULT_ENCODING);
		long strAddr = ngx_palloc(pool, bytes.length);
		if (strAddr == 0) {
			throw new OutOfMemoryError("nginx OutOfMemoryError");
		}
		ngx_http_clojure_mem_copy_to_addr(bytes, BYTE_ARRAY_OFFSET, strAddr, bytes.length);
		return (int)ngx_http_clojure_mem_set_variable(r, np, strAddr, bytes.length);
	}
	
	
	public static int eval(final int codeId, final long r) {
		
		if (r == 0) { //by worker init process
			LazyRequestMap fakeReq = new LazyRequestMap(codeId, r, new Object[0]);
			Map resp = handleRequest(fakeReq);
			if (resp != null && resp != ASYNC_TAG && fetchResponseStatus(resp, 200) != 200) {
				log.error("initialize error %s", resp.get(BODY));
				return NGX_HTTP_INTERNAL_SERVER_ERROR;
			}
			return NGX_HTTP_OK;
		}
		
		final LazyRequestMap req = new LazyRequestMap(codeId, r);
		int phase = (int)ngx_http_clojure_mem_get_module_ctx_phase(r);
		
		if (workers == null) {
			Map resp = handleRequest(req);
			if (resp == ASYNC_TAG) {
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
		workers.submit(new Callable<NginxClojureRT.HandlerContext>() {
			@Override
			public HandlerContext call() throws Exception {
				Map resp = handleRequest(req);
				//let output chain built before entering the main thread
				return new HandlerContext(req, resp, resp == PHRASE_DONE ? 0 : buildOutputChain(r, resp));
			}
		});
		return NGX_DONE;
	}
	
	public static Map handleRequest(final LazyRequestMap req) {
		IFn f = HANDLERS.get(req.codeId);
		try{
			
			if (coroutineEnabled) {
				CoroutineRunner coroutineRunner = new CoroutineRunner(f, req);
				Coroutine coroutine = new Coroutine(coroutineRunner);
				coroutine.resume();
				if (coroutine.getState() == Coroutine.State.FINISHED) {
					return coroutineRunner.response;
				}else {
					return ASYNC_TAG;
				}
			}else {
				Map resp = (Map) f.invoke(req);
				return resp;
			}
		}catch(Throwable e){
			return buildUnhandledExceptionResponse(e);
		}finally {
			int bodyIdx = req.index(BODY);
			if (bodyIdx > 0 && req.array[bodyIdx] instanceof Closeable) {
				try {
					((Closeable)req.array[bodyIdx]).close();
				} catch (Throwable e) {
					log.error("can not close Closeable object such as FileInputStream!", e);
				}
			}
		}
	}

	public static Map buildUnhandledExceptionResponse(Throwable e) {
		log.error("server unhandled exception!", e);
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		e.printStackTrace(pw);
		pw.close();
		return new PersistentArrayMap(new Object[] {STATUS, 500, BODY, sw.toString(), HEADERS, new PersistentArrayMap(new Object[] {CONTENT_TYPE.getName(), "text/plain"})});
	}

	
	public static long buildResponseFileBuf(File f, long r, long pool, int isLast, long chain) {
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
	public static long buildResponseInputStreamBuf(InputStream in, long r, long pool, int isLast, long chain) {
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
					ngx_http_clojure_mem_init_ngx_buf(lastBuf, buf, BYTE_ARRAY_OFFSET, pos, 0);
					
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
			
			if (isLast == 1 && lastBuf > 0) {
				//only set last buffer flag 
				ngx_http_clojure_mem_init_ngx_buf(lastBuf, null, 0, 0, 1);
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
	
	public static long buildResponseStringBuf(String s, long r, long pool, int isLast, long chain) {
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
	
	public static long buildResponseItemBuf(long r, long pool, Object item, int isLast, long chain) {

		if (item instanceof File) {
			return buildResponseFileBuf((File)item, r, pool, isLast, chain);
		}else if (item instanceof InputStream) {
			return buildResponseInputStreamBuf((InputStream)item, r, pool, isLast, chain);
		}else if (item instanceof String) {
			return buildResponseStringBuf((String)item, r, pool, isLast, chain);
		}else if ((item instanceof ISeq) || (item instanceof Seqable) || (item instanceof Iterable)) {
			ISeq seq = RT.seq(item);
			long lastChain = 0;
			while (seq != null) {
				Object o = seq.first();
				if (o != null) {
					
					if (lastChain != 0) {
						chain = ngx_palloc(pool, NGX_HTTP_CLOJURE_CHAINT_SIZE);
						if (chain == 0) {
							return 0;
						}
					}
					
					seq = seq.next();
					long subTail = 0;
					if (isLast == 1 && seq == null) {
						subTail = buildResponseItemBuf(r, pool, o, 1, chain);
					}else {
						subTail = buildResponseItemBuf(r, pool, o, 0, chain);
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
		return -NGX_HTTP_INTERNAL_SERVER_ERROR;
	}
	
	public final static String normalizeHeaderName(Object nameObj) {
		String name;
		if (nameObj instanceof String) {
			name = (String)nameObj;
		}else if (nameObj instanceof Keyword) {
			name = ((Keyword)nameObj).getName();
		}else {
			name = nameObj.toString();
		}
		return name == null ? null : name.toLowerCase();
	}
	
	public static int fetchResponseStatus(final Map resp, int defaultStatus) {
		int status = defaultStatus;
		Object statusObj = resp.get(STATUS);
		if (statusObj != null) {
			if (statusObj instanceof Number){
				status = ((Number)statusObj).intValue();
			}else {
				status = Integer.parseInt(statusObj.toString());
			}
		}
		return status;
	}
	
	public static long buildOutputChain(long r, final Map resp) {

		if (resp == null) {
			return -NGX_HTTP_NOT_FOUND;
		}
		try {
			long pool = UNSAFE.getAddress(r + NGX_HTTP_CLOJURE_REQ_POOL_OFFSET);
			int status = fetchResponseStatus(resp, 200);
			long headers_out = r + NGX_HTTP_CLOJURE_REQ_HEADERS_OUT_OFFSET;
			
			Map<Object, Object> headers = (Map<Object, Object>) resp.get(HEADERS);
			String contentType = null;
			String server = null;
			if (headers != null) {
				for (Map.Entry<Object, Object> hen : headers.entrySet()) {
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
					
					ResponseHeaderPusher pusher = KNOWN_RESP_HEADERS.get(name);
					if (pusher == null) {
						pusher = new ResponseUnknownHeaderPusher(name);
					}
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
			
			Object body = resp.get(BODY);
			long chain = 0;
			
			if (body != null) {
				chain = ngx_palloc(pool, NGX_HTTP_CLOJURE_CHAINT_SIZE);
				if (chain == 0) {
					return -NGX_HTTP_INTERNAL_SERVER_ERROR;
				}
				long tailChain = buildResponseItemBuf(r, pool, body, 1, chain);
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
				//header sent yet so we return normal OK
				return -status;
			}
			
			pushNGXInt(headers_out + NGX_HTTP_CLOJURE_HEADERSO_STATUS_OFFSET, status);
			int rc = (int) ngx_http_send_header(r);
			if (rc == NGX_ERROR || rc > NGX_OK) {
				return -rc;
			}
			
			return chain;

		}catch(Throwable e) {
			log.error("server unhandled exception!", e);
			return -NGX_HTTP_INTERNAL_SERVER_ERROR;
		}
	
	}
	
	public static int handlePostEvent(long event) {
		int tag = (int)((0xff00000000000000L & event) >> 56);
		long data = event & 0x00ffffffffffffffL;
		switch (tag) {
		case POST_EVENT_TYPE_HANDLE_RESPONSE:
			return handleResponse(data);
		case POST_EVENT_TYPE_CLOSE_SOCKET:
			try {
				NginxClojureSocketImpl s = (NginxClojureSocketImpl) POSTED_EVENTS_DATA.remove(data);
				s.closeByPostEvent();
				return NGX_OK;
			}catch (Throwable e) {
				log.error("handle post close event error", e);
				return NGX_HTTP_INTERNAL_SERVER_ERROR;
			}
			
		default:
			log.error("handlePostEvent:unknown event tag :%d", tag);
			return NGX_HTTP_INTERNAL_SERVER_ERROR;
		}
	}
	
	public static int handleResponse(long r) {
		HandlerContext ctx = (HandlerContext) POSTED_EVENTS_DATA.remove(r);
		if (ctx.response == PHRASE_DONE) {
			ngx_http_clojure_mem_continue_current_phase(r);
			return NGX_OK;
		}
		long chain = ctx.chain;
		if (chain < 0) {
			ngx_http_finalize_request(r, -chain);
		} else {
			ngx_http_finalize_request(r, ngx_http_output_filter(r, chain));
		}
		return NGX_OK;
	}
	
	public static int handleResponse(long r, final Map resp) {
		if (Thread.currentThread() != NGINX_MAIN_THREAD) {
			throw new RuntimeException("handleResponse can not be called out of nginx clojure main thread!");
		}
		if (resp == PHRASE_DONE) {
			return NGX_DECLINED;
		}
		
		long chain = buildOutputChain(r, resp);
		if (chain < 0) {
			return -(int)chain;
		}
		return (int)ngx_http_output_filter(r, chain);
	}

	public static void completeAsyncResponse(long r, final Map resp) {
		if (r == 0) {
			return;
		}
		
		if (resp == PHRASE_DONE) {
			ngx_http_clojure_mem_continue_current_phase(r);
			return;
		}
		
		int rc = handleResponse(r, resp);
		ngx_http_finalize_request(r, rc);
	}
	
	public static void completeAsyncResponse(long r, int rc) {
		if (r == 0) {
			return;
		}
		ngx_http_finalize_request(r, rc);
	}
	
	private final static void savePostEventData(long id, Object o) {
		while (POSTED_EVENTS_DATA.putIfAbsent(id, o) != null) {
			try {
				Thread.sleep(0);
			} catch (InterruptedException e) {
				log.error("interrupted!", e);
				return;
			}
		}
	}

	/**
	 * When called in the main thread it will be handled directly otherwise it will post a event by pipe let 
	 * main thread  get a chance to handle this response.
	 */
	public static void postResponseEvent(LazyRequestMap req, Map resp) {
		if (Thread.currentThread() == NGINX_MAIN_THREAD) {
			handleResponse(req.r, resp);
		}else {
			HandlerContext ctx = new HandlerContext(req, resp, buildOutputChain(req.r, resp));
			savePostEventData(req.r, ctx);
			ngx_http_clojure_mem_post_event(req.r);
		}
	}
	
	public final static long makeEventAndSaveIt(int type, Object o) {
		long id = ngx_http_clojure_mem_get_obj_addr(o);
		long event = ((long)type) << 56 | id;
		savePostEventData(id, o);
		return event;
	}
	
	public static void postCloseSocketEvent(NginxClojureSocketImpl s) {
		ngx_http_clojure_mem_post_event(makeEventAndSaveIt(POST_EVENT_TYPE_CLOSE_SOCKET, s));
	}
	
	public static LoggerService getLog() {
		//be friendly to junit test
		if (log == null) {
			//standard error stream is redirect to the nginx error log file, so we just use System.err as output stream.
			log = TinyLogService.createDefaultTinyLogService();
		}
		return log;
	}

	public static void setLog(LoggerService log) {
		NginxClojureRT.log = log;
	}
	
	public static final class BatchCallRunner implements Runnable {
		Coroutine parent;
		int[] counter;
		IFn handler;
		int order;
		Object[] results;

		public BatchCallRunner(Coroutine parent, int[] counter, IFn handler,
				int order, Object[] results) {
			super();
			this.parent = parent;
			this.counter = counter;
			this.handler = handler;
			this.order = order;
			this.results = results;
		}

		@Override
		public void run() throws SuspendExecution {
			try {
				results[order] = handler.invoke();
			}catch(Throwable e) {
				log.error("error in sub coroutine", e);
			}
			
			if ( --counter[0] == 0 && parent != null && parent.getState() == Coroutine.State.SUSPENDED) {
				parent.resume();
			}
		}
	}
	
	/**
	 * Execute a batch of IFn instances, e.g. clojure functions, in different new coroutines so we can handle many sockets parallel 
	 * within one request-response cycle.
	 * When there 's no coroutine context, it will turn to use thread pool to make testing with lein-ring easy.
	 * @param fns Runnable instances
	 */
	public static ISeq coBatchCall(ISeq fns) throws SuspendExecution {
		int c = fns.count();
		int[] counter = new int[] {c};
		Object[] results = new Object[c];
		Coroutine parent = Coroutine.getActiveCoroutine();
		
		if (parent == null && (JavaAgent.db == null || !JavaAgent.db.isRunTool())) {
			log.warn("we are not in coroutine enabled context, so we turn to use thread for only testing usage!");
			Future[] futures = new Future[c];
			for (int i = 0; fns != null;  fns = fns.next(), i++) {
				IFn fn = (IFn) fns.first();
				BatchCallRunner bcr = new BatchCallRunner(parent, counter, fn, i, results);
				if (threadPoolOnlyForTestingUsage == null) {
					initThreadPoolOnlyForTestingUsage();
				}
				futures[i] = threadPoolOnlyForTestingUsage.submit(bcr);
			}
			for (Future f : futures) {
				try {
					f.get();
				} catch (Throwable e) {
					log.error("do future failed", e);
				} 
			}
		}else {
			boolean shouldYieldParent = false;
			for (int i = 0; fns != null;  fns = fns.next()) {
				IFn fn = (IFn) fns.first();
				Coroutine co  = new Coroutine(new BatchCallRunner(parent, counter, fn, i++, results));
				co.resume();
				if (co.getState() != Coroutine.State.FINISHED) {
					shouldYieldParent = true;
				}
			}
			
			if (parent != null && shouldYieldParent) {
				Coroutine.yield();
			}
		}

		return RT.seq(results);
	}
	
}
