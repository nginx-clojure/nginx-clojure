/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure;



import java.io.IOException;
import java.lang.reflect.Field;
import java.net.Socket;
import java.nio.ByteBuffer;
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


public class NginxClojureRT extends MiniConstants {



	
	public static long[] MEM_INDEX;
	
	public static Thread NGINX_MAIN_THREAD;
	
	/*use it carefully!!*/
	public static Unsafe UNSAFE = HackUtils.UNSAFE;
	
	private static List<NginxHandler>  HANDLERS = new ArrayList<NginxHandler>();
	
	//mapping clojure code pointer address to clojure code id 
	private static Map<Long, Integer> CODE_MAP = new HashMap<Long, Integer>();
	
	
	
	public static ConcurrentHashMap<Long, Object> POSTED_EVENTS_DATA = new ConcurrentHashMap<Long, Object>();
	
	private static ExecutorService eventDispather;
	
	public static CompletionService<WorkerResponseContext> workers;
	
	//only for testing, e.g. with lein-ring where no coroutine support
	public static ExecutorService threadPoolOnlyForTestingUsage;
	
	public static boolean coroutineEnabled = false;
	
	public static LoggerService log;
	
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

	/**
	 * last_buf can be either of {@link MiniConstants#NGX_CLOJURE_BUF_LAST_OF_NONE} {@link MiniConstants#NGX_CLOJURE_BUF_LAST_OF_CHAIN}, {@link MiniConstants#NGX_CLOJURE_BUF_LAST_OF_RESPONSE}
	 */
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
	
	public native static long ngx_http_clojure_mem_post_event(long e, Object data, long offset);
	
	public native static long ngx_http_clojure_mem_broadcast_event(long e, Object data, long offset, long hasSelf);
	
	public native static long ngx_http_clojure_mem_read_raw_pipe(long p, Object buf, long offset, long len);
	
	public native static long ngx_http_cleanup_add(long r, ChannelListener listener, Object data);
	
	/**
	 * flag can be either of {@link MiniConstants#NGX_CLOJURE_BUF_FLUSH_FLAG} {@link MiniConstants#NGX_CLOJURE_BUF_LAST_FLAG}
	 */
	public native static long ngx_http_hijack_send(long req, Object buf, long offset, long len, int flag);
	
	/**
	 * flag can be either of {@link MiniConstants#NGX_CLOJURE_BUF_FLUSH_FLAG} {@link MiniConstants#NGX_CLOJURE_BUF_LAST_FLAG}
	 */
	public native static long ngx_http_hijack_send_header(long req, int flag);
	
	public native static long ngx_http_hijack_send_chain(long req, long chain, int flag);
	
//	public native static long ngx_http_clojure_mem_get_body_tmp_file(long r);
	
	private static AppEventMessageHandler appEventMessageHandler;
	
	static {
		//be friendly to lein ring testing
		getLog();
		initUnsafe();
	}
	
	public static AppEventMessageHandler getAppEventMessageHandler() {
		return appEventMessageHandler;
	}
	
	public static void setAppEventMessageHandler(AppEventMessageHandler appEventMessageHandler) {
		NginxClojureRT.appEventMessageHandler = appEventMessageHandler;
	}
	
	public static String formatVer(long ver) {
		long f = ver / 1000000;
		long s = ver / 1000 - f * 1000;
		long t = ver - s * 1000 - f * 1000000;
		return f + "." + s + "." + t;
	}
	
	public static final class WorkerResponseContext {
		public final NginxResponse response;
		public final long chain;
		
		public WorkerResponseContext(NginxResponse response, long chain) {
			super();
			this.response = response;
			this.chain = chain;
		}
	}
	
	public static final class HijackEvent {
		public final NginxServerChannel channel;
		public final Object message;
		public final int off;
		public final int len;
		public final int flag;
		
		public HijackEvent(NginxServerChannel channel, Object message, int off, int len, int flag) {
			super();
			this.channel = channel;
			this.message = message;
			this.off = off;
			this.len = len;
			this.flag = flag;
		}
		
	}
	
	public static final class HijackHeaderEvent {
		public final NginxServerChannel channel;
		public final int flag;
		public HijackHeaderEvent(NginxServerChannel channel, int flag) {
			super();
			this.channel = channel;
			this.flag = flag;
		}
	}
	
	public static final class HijackResponseEvent {
		public final NginxServerChannel channel;
		public final NginxResponse response;
		public final long chain;
		public final int flag;
		public HijackResponseEvent(NginxServerChannel channel, NginxResponse response, long chain, int flag) {
			super();
			this.channel = channel;
			this.response = response;
			this.chain = chain;
			this.flag = flag;
		}
	}
	
	
	public static final class EventDispatherRunnable implements Runnable {
		
		final CompletionService<WorkerResponseContext> workers;
		
		public EventDispatherRunnable(final CompletionService<WorkerResponseContext> workers) {
			this.workers = workers;
		}
		
		@Override
		public void run() {
			while (true) {
				try {
					Future<WorkerResponseContext> respFuture =  workers.take();
					WorkerResponseContext ctx = respFuture.get();
					if (ctx.response == NR_ASYNC_TAG) {
						continue;
					}
					long r = ctx.response.request().nativeRequest();
					savePostEventData(r, ctx);
					ngx_http_clojure_mem_post_event(r, null, 0);
				} catch (InterruptedException e) {
					log.error("interrupted!", e);
					break;
				} catch (ExecutionException e) {
					log.error("unexpected ExecutionException!", e);
				}
			}
		}
	}
	
	public  static void savePostEventData(long id, Object o) {
		while (POSTED_EVENTS_DATA.putIfAbsent(id, o) != null) {
			try {
				Thread.sleep(0);
			} catch (InterruptedException e) {
				log.error("interrupted!", e);
				return;
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
		
		log.info("nginx-clojure run on thread pool mode,  coroutineEnabled=false");
		
		eventDispather = Executors.newSingleThreadExecutor(new ThreadFactory() {
			@Override
			public Thread newThread(Runnable r) {
				return new Thread(r, "nginx-clojure-eventDispather");
			}
		});
		
		workers = new ExecutorCompletionService<WorkerResponseContext>(Executors.newFixedThreadPool(n, new ThreadFactory() {
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

		NGX_HTTP_CLOJURE_HEADERSI_AUTHORIZATION_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSI_AUTHORIZATION_IDX];
		NGX_HTTP_CLOJURE_HEADERSI_KEEP_ALIVE_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSI_KEEP_ALIVE_IDX];

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
		REQUEST_METHOD_FETCHER = new RequestMethodStrFetcher();
		CONTENT_TYPE_FETCHER = new RequestKnownHeaderFetcher("content-type");
		CHARACTER_ENCODING_FETCHER = new RequestCharacterEncodingFetcher();
//		HEADER_FETCHER = new RequestHeadersFetcher();
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
		
		//set system properties for build-in nginx handler factories
		System.setProperty(NginxHandlerFactory.NGINX_CLOJURE_HANDLER_FACTORY_SYSTEM_PROPERTY_PREFIX + "java", "nginx.clojure.java.NginxJavaHandlerFactory");
		System.setProperty(NginxHandlerFactory.NGINX_CLOJURE_HANDLER_FACTORY_SYSTEM_PROPERTY_PREFIX + "clojure", "nginx.clojure.clj.NginxClojureHandlerFactory");
		System.setProperty(NginxHandlerFactory.NGINX_CLOJURE_HANDLER_FACTORY_SYSTEM_PROPERTY_PREFIX + "groovy", "nginx.clojure.groovy.NginxGroovyHandlerFactory");
	}

	public static void initUnsafe() {
		if (UNSAFE != null) {
			return;
		}
		UNSAFE = HackUtils.UNSAFE;
	}
	
	
	public static synchronized int registerCode(long typeNStr, long nameNStr, long codeNStr) {
		if (CODE_MAP.containsKey(codeNStr)) {
			return CODE_MAP.get(codeNStr);
		}
		
		if (CODE_MAP.containsKey(nameNStr)) {
			return CODE_MAP.get(nameNStr);
		}
		
		String type = fetchNGXString(typeNStr, DEFAULT_ENCODING);
		String name = fetchNGXString(nameNStr, DEFAULT_ENCODING);
		String code = fetchNGXString(codeNStr,  DEFAULT_ENCODING);
		
		NginxHandler handler = NginxHandlerFactory.fetchHandler(type, name, code);
		HANDLERS.add(handler);
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
		if (len <= 0){
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
			np = ngx_palloc(pool, NGX_HTTP_CLOJURE_STR_SIZE);
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
		return HANDLERS.get(codeId).execute(r);
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

	public final static long makeEventAndSaveIt(long type, Object o) {
		long id = ngx_http_clojure_mem_get_obj_addr(o);
		long event = type << 56 | id;
		savePostEventData(id, o);
		return event;
	}
	
	public static void postCloseSocketEvent(NginxClojureSocketImpl s) {
		ngx_http_clojure_mem_post_event(makeEventAndSaveIt(POST_EVENT_TYPE_CLOSE_SOCKET, s), null, 0);
	}
	
	public static void postHijackSendEvent(NginxServerChannel channel, Object message, int off, int len, int flag) {
		ngx_http_clojure_mem_post_event(
				makeEventAndSaveIt(POST_EVENT_TYPE_HIJACK_SEND,
						new HijackEvent(channel, message, off, len , flag)), null, 0);
	}
	
	public static void postHijackSendHeaderEvent(NginxServerChannel channel, int flag) {
		ngx_http_clojure_mem_post_event(
				makeEventAndSaveIt(POST_EVENT_TYPE_HIJACK_SEND_HEADER,
						new HijackHeaderEvent(channel, flag)), null, 0);
	}
	
	public static void postHijackSendResponseEvent(NginxServerChannel channel, NginxResponse resp, long chain, int flag) {
		ngx_http_clojure_mem_post_event(
				makeEventAndSaveIt(POST_EVENT_TYPE_HIJACK_SEND_RESPONSE,
						new HijackResponseEvent(channel, resp, chain, flag | NGX_CLOJURE_BUF_LAST_FLAG)), null, 0);
	}
	
	private final static byte[] POST_EVENT_BUF = new byte[4096];
	
	public static int handlePostEvent(long event, byte[] body, int off) {
		int tag = (int)((0xff00000000000000L & event) >>> 56);
		long data = event & 0x00ffffffffffffffL;
		if (tag <= POST_EVENT_TYPE_SYSTEM_EVENT_IDX_END) {
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
			case POST_EVENT_TYPE_HIJACK_SEND : {
				HijackEvent hijackEvent = (HijackEvent)POSTED_EVENTS_DATA.remove(data);
				if (hijackEvent.message instanceof ByteBuffer) {
					hijackEvent.channel.send((ByteBuffer)hijackEvent.message, hijackEvent.flag);
				}else {
					hijackEvent.channel.send((byte[])hijackEvent.message, hijackEvent.off, hijackEvent.len, hijackEvent.flag);
				}
				return NGX_OK;
			}
			case POST_EVENT_TYPE_HIJACK_SEND_HEADER : {
				HijackHeaderEvent hijackHeaderEvent = (HijackHeaderEvent)POSTED_EVENTS_DATA.remove(data);
				ngx_http_hijack_send_header(hijackHeaderEvent.channel.request.nativeRequest(), hijackHeaderEvent.flag);
				return NGX_OK;
			}
			case POST_EVENT_TYPE_HIJACK_SEND_RESPONSE : {
				HijackResponseEvent hijackResponseEvent = (HijackResponseEvent)POSTED_EVENTS_DATA.remove(data);
				NginxRequest request = hijackResponseEvent.channel.request;
				request.handler().prepareHeaders(request,hijackResponseEvent.response.fetchStatus(NGX_OK) , hijackResponseEvent.response.fetchHeaders());
				int rc = (int) NginxClojureRT.ngx_http_hijack_send_header(request.nativeRequest(),
						(hijackResponseEvent.flag & NGX_CLOJURE_BUF_IGNORE_FILTER_FLAG) != 0 ? NGX_CLOJURE_BUF_IGNORE_FILTER_FLAG
								: 0);
				if (rc == NGX_OK || rc == NGX_AGAIN) {
					ngx_http_hijack_send_chain(hijackResponseEvent.channel.request.nativeRequest(), hijackResponseEvent.chain, hijackResponseEvent.flag);
				}
				return NGX_OK;
			}
			default:
				log.error("handlePostEvent:unknown event tag :%d", tag);
				return NGX_HTTP_INTERNAL_SERVER_ERROR;
			}
		} else {
			if (appEventMessageHandler == null) {
				log.warn("handlePostEvent:no appEventMessageHandler to handle app message event");
				return NGX_OK;
			}
			if (tag < POST_EVENT_TYPE_COMPLEX_EVENT_IDX_START) {
				appEventMessageHandler.handleSimpleEvent(tag, data);
				return NGX_OK;
			}else {
				appEventMessageHandler.handleComplexEvent(tag, body, off, (int)data);
				return NGX_OK;
			}
		}
	}
	
	/**
	 * called by native code
	 */
	private static int handlePostEvent(long event, long pipe) {
		int tag = (int)((0xff00000000000000L & event) >>> 56);
		long data = event & 0x00ffffffffffffffL;
		if (tag < POST_EVENT_TYPE_COMPLEX_EVENT_IDX_START) {
			return handlePostEvent(event, null, 0);
		} else {
			if (ngx_http_clojure_mem_read_raw_pipe(pipe, POST_EVENT_BUF,
					BYTE_ARRAY_OFFSET, data) != data) {
				return NGX_HTTP_INTERNAL_SERVER_ERROR;
			}
			return handlePostEvent(event, POST_EVENT_BUF, 0);
		}
	}
	
	public static int handleResponse(long r) {
		WorkerResponseContext ctx = (WorkerResponseContext) POSTED_EVENTS_DATA.remove(r);
		if (ctx.response == NR_PHRASE_DONE) {
			ngx_http_clojure_mem_continue_current_phase(r);
			return NGX_OK;
		}
		long chain = ctx.chain;
		NginxRequest req = ctx.response.request();
		if (chain < 0) {
			req.handler().prepareHeaders(req, -(int)chain, ctx.response.fetchHeaders());
			ngx_http_finalize_request(r, -chain);
		} else {
			int status = ctx.response.fetchStatus(NGX_HTTP_OK);
			req.handler().prepareHeaders(req, status, ctx.response.fetchHeaders());
			int rc = (int)ngx_http_send_header(req.nativeRequest());
			if (rc == NGX_ERROR || rc > NGX_OK) {
				ngx_http_finalize_request(r, rc);
			}else {
				ngx_http_finalize_request(r, ngx_http_output_filter(r, chain));
			}
		}
		return NGX_OK;
	}
	
	public static int handleResponse(NginxRequest r, final NginxResponse resp) {
		if (Thread.currentThread() != NGINX_MAIN_THREAD) {
			throw new RuntimeException("handleResponse can not be called out of nginx clojure main thread!");
		}
		if (resp == NR_PHRASE_DONE) {
			return NGX_DECLINED;
		}
		

		if (resp == null) {
			return NGX_HTTP_NOT_FOUND;
		}
		
		NginxHandler handler = r.handler();
		int status = resp.fetchStatus(NGX_HTTP_OK);
		long chain = handler.buildOutputChain(resp);
		if (chain < 0) {
			status = -(int)chain;
			handler.prepareHeaders(r, status, resp.fetchHeaders());
			return status;
		}
		handler.prepareHeaders(r, status, resp.fetchHeaders());
		int rc = (int)ngx_http_send_header(r.nativeRequest());
		if (rc == NGX_ERROR || rc > NGX_OK) {
			return rc;
		}
		return (int)ngx_http_output_filter(r.nativeRequest(), chain);
	}

	public static void completeAsyncResponse(NginxRequest req, final NginxResponse resp) {
		if (req == null) {
			return;
		}
		
		long r = req.nativeRequest();
		if (r == 0) {
			return;
		}
		
		if (resp == NR_PHRASE_DONE) {
			ngx_http_clojure_mem_continue_current_phase(r);
			return;
		}
		
		int rc = handleResponse(req, resp);
		ngx_http_finalize_request(r, rc);
	}
	
	public static void completeAsyncResponse(NginxRequest r, int rc) {
		if (r == null) {
			return;
		}
		completeAsyncResponse(r.nativeRequest(), rc);
	}
	
	public static void completeAsyncResponse(long r, int rc) {
		if (r == 0) {
			return;
		}
		ngx_http_finalize_request(r, rc);
	}


	/**
	 * When called in the main thread it will be handled directly otherwise it will post a event by pipe let 
	 * main thread  get a chance to handle this response.
	 */
	public static void postResponseEvent(NginxRequest req, NginxResponse resp) {
		if (Thread.currentThread() == NGINX_MAIN_THREAD) {
			handleResponse(req, resp);
		}else {
			long r = req.nativeRequest();
			WorkerResponseContext ctx = new WorkerResponseContext(resp, req.handler().buildOutputChain(resp));
			savePostEventData(r, ctx);
			ngx_http_clojure_mem_post_event(r, null, 0);
		}
	}
	
	/**
	 * broadcast simple event to all nginx workers
	 * @param tag must be less than POST_EVENT_TYPE_COMPLEX_EVENT_IDX_START
	 * @param id event id, must be less than 0x0100000000000000L
	 * @param 
	 */
	public static int broadcastEvent(long tag, long id) {
		if (tag >= POST_EVENT_TYPE_COMPLEX_EVENT_IDX_START) {
			throw new IllegalArgumentException("invalid event tag :" + tag);
		}
		if (id >= 0x0100000000000000L) {
			throw new IllegalArgumentException("invalid event id :" + id + ", must be less than 0x0100000000000000L");
		}
		id |= (tag << 56);
		if (Thread.currentThread() == NGINX_MAIN_THREAD) {
			int rt = (int)ngx_http_clojure_mem_broadcast_event(id, null, 0, 0);
			if (rt == 0) {
				return handlePostEvent(id, null, 0);
			}else {
				rt = handlePostEvent(id, null, 0);
			}
			return rt;
		}else {
			return (int)ngx_http_clojure_mem_broadcast_event(id, null, 0, 1);
		}
	}
	
	/**
	 * broadcast event to all nginx workers, message length must be less than PIPE_BUF - 8, generally on Linux/Windows is 4088, on MacosX is 504
	 * message will be truncated if its length exceeds this limitation.
	 * @param tag must be  greater than POST_EVENT_TYPE_COMPLEX_EVENT_IDX_START  and less than POST_EVENT_TYPE_COMPLEX_EVENT_IDX_END
	 * @param body 
	 * @param offset
	 * @param len
	 */
	public static int broadcastEvent(long tag, byte[] body, int offset, int len) {
		if (tag >= 0xff) {
			throw new IllegalArgumentException("invalid event tag :" + tag);
		}
		
		if (tag < POST_EVENT_TYPE_COMPLEX_EVENT_IDX_START) {
			throw new IllegalArgumentException("invalid event tag :" + tag + ", must be greater than POST_EVENT_TYPE_COMPLEX_EVENT_IDX_START");
		}
		long event = (tag << 56) | len;
		
		if (Thread.currentThread() == NGINX_MAIN_THREAD) {
			int rt = (int)ngx_http_clojure_mem_broadcast_event(event, body, BYTE_ARRAY_OFFSET + offset, 0);
			if (rt == 0) {
				rt = (int)handlePostEvent(event, body, offset);
			}else {
				handlePostEvent(event, body, offset);
			}
			return rt;
		}else {
			return (int)ngx_http_clojure_mem_broadcast_event(event, body, BYTE_ARRAY_OFFSET + offset, 1);
		}
	}
	
	/**
	 * broadcast event to all nginx workers, message length must be less than PIPE_BUF - 8, generally on Linux/Windows is 4088, on MacosX is 504
	 * message will be truncated if its length exceeds this limitation.
	 * it is identical to 
	 * <pre>
	 * broadcastEvent(POST_EVENT_TYPE_COMPLEX_EVENT_IDX_START, body, offset, len);
	 * </pre>
	 */
	public static int broadcastEvent(byte[] message, int offset, int len) {
		return broadcastEvent(POST_EVENT_TYPE_COMPLEX_EVENT_IDX_START, message, offset, len);
	}
	
	/**
	 * broadcast event to all nginx workers, message length, viz. message.getBytes("utf-8").length,  must be less than PIPE_BUF - 8,
	 * generally on Linux/Windows is 4088, on MacosX is 504
	 * message will be truncated if its length exceeds this limitation.
	 * it is identical to 
	 * <pre>
     *   byte[] buf = message.getBytes(DEFAULT_ENCODING);
	 *	 return broadcastEvent(tag, buf, 0, buf.length);
	 * </pre>
	 */
	public static int broadcastEvent(long tag, String message) {
		byte[] buf = message.getBytes(DEFAULT_ENCODING);
		return broadcastEvent(tag, buf, 0, buf.length);
	}

	/**
	 * broadcast event to all nginx workers, message length, viz. message.getBytes("utf-8").length,  must be less than PIPE_BUF - 8,
	 * generally on Linux/Windows is 4088, on MacosX is 504
	 * message will be truncated if its length exceeds this limitation.
	 * it is identical to 
	 * <pre>
     *   byte[] buf = message.getBytes(DEFAULT_ENCODING);
	 *	 return broadcastEvent(POST_EVENT_TYPE_COMPLEX_EVENT_IDX_START, buf, 0, buf.length);
	 * </pre>
	 */	
	public static int broadcastEvent(String message) {
		byte[] buf = message.getBytes(DEFAULT_ENCODING);
		return broadcastEvent(POST_EVENT_TYPE_COMPLEX_EVENT_IDX_START, buf, 0, buf.length);
	}
	
	public static final class BatchCallRunner implements Runnable {
		Coroutine parent;
		int[] counter;
		Callable handler;
		int order;
		Object[] results;

		public BatchCallRunner(Coroutine parent, int[] counter, Callable handler,
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
				results[order] = handler.call();
			}catch(Throwable e) {
				log.error("error in sub coroutine", e);
			}
			
			if ( --counter[0] == 0 && parent != null && parent.getState() == Coroutine.State.SUSPENDED) {
				parent.resume();
			}
		}
	}
	
	public static interface AppEventMessageHandler {

		/**
		 * Handle simple event which only has a event id as its data without any message body
		 * @param tag event type tag e.g. @see  {@link MiniConstants#POST_EVENT_TYPE_HANDLE_RESPONSE}
		 * @param data
		 */
		public void handleSimpleEvent(int tag, long data);

		/**
		 * Handle complex event because buf will be reused by next event so this listener must handle it carefully and
		 * do use it out of this invoke scope.
		 * @param tag event type tag e.g. @see  {@link MiniConstants#POST_EVENT_TYPE_HANDLE_RESPONSE}
		 * @param buf message body buffer
		 * @param off offset of message body in the buffer
		 * @param len length of message body 
		 */
		public void handleComplexEvent(int tag, byte[] buf, int off, int len);
	}
	
	public static final Object[] coBatchCall(Callable<Object> ...calls) {

		int c = calls.length;
		int[] counter = new int[] {c};
		Object[] results = new Object[c];
		Coroutine parent = Coroutine.getActiveCoroutine();
		
		if (parent == null && (JavaAgent.db == null || !JavaAgent.db.isRunTool())) {
			log.warn("we are not in coroutine enabled context, so we turn to use thread for only testing usage!");
			Future[] futures = new Future[c];
			for (int i = 0; i < c ; i++) {
				BatchCallRunner bcr = new BatchCallRunner(parent, counter, calls[i], i, results);
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
			for (int i = 0; i < c ; i++) {
				Coroutine co  = new Coroutine(new BatchCallRunner(parent, counter, calls[i], i, results));
				co.resume();
				if (co.getState() != Coroutine.State.FINISHED) {
					shouldYieldParent = true;
				}
			}
			
			if (parent != null && shouldYieldParent) {
				Coroutine.yield();
			}
		}
		return results;
	}
}
