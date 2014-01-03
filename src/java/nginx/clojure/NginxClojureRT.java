/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure;

import static nginx.clojure.Constants.*;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import sun.misc.Unsafe;
import clojure.lang.IFn;
import clojure.lang.PersistentArrayMap;
import clojure.lang.RT;

public class NginxClojureRT {


	public static long[] MEM_INDEX;
	

	
	protected static Unsafe UNSAFE = null;
	
	private static List<IFn>  HANDLERS = new ArrayList<IFn>();
	
	//mapping clojure code pointer address to clojure code id 
	private static Map<Long, Integer> CODE_MAP = new HashMap<Long, Integer>();
	
	private static ConcurrentHashMap<Long, Map> REQ_RESP_MAP = new ConcurrentHashMap<Long, Map>();
	
	private static ExecutorService eventDispather;
	
	private static CompletionService<HandlerContext> workers;
	
	public native static long ngx_palloc(long pool, long size);
	
	public native static long ngx_pcalloc(long pool, long size);
	
	public native static long ngx_array_create(long pool, long n, long size);

	public native static long ngx_array_init(long array, long pool, long n, long size);

	public native static long ngx_array_push_n(long array, long n);

	public native static long ngx_list_create(long pool, long n, long size);

	public native static long ngx_list_init(long list, long pool, long n, long size);

	public native static long ngx_list_push(long list);
	
	
	public native static long ngx_create_temp_buf(long pool, long size);
	
	public native static long ngx_create_file_buf(long r, long file, long name_len);
	
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
	
	public native static void ngx_http_clojure_mem_inc_req_count(long r);
	
	public native static void ngx_http_clojure_mem_post_write_event(long r);
	
//	public native static long ngx_http_clojure_mem_get_body_tmp_file(long r);
	
	public static String formatVer(long ver) {
		long f = ver / 1000000;
		long s = ver / 1000 - f * 1000;
		long t = ver - s * 1000 - f * 1000000;
		return f + "." + s + "." + t;
	}
	
	public static final class HandlerContext {
		public final LazyRequestMap request;
		public final Map response;
		public HandlerContext(LazyRequestMap request, Map response) {
			this.request =request;
			this.response = response;
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
					while (REQ_RESP_MAP.putIfAbsent(ctx.request.r, ctx.response) != null) {
							Thread.sleep(0);
					}
//					System.out.println("REQ_RESP_MAP size:" + REQ_RESP_MAP.size());
//					System.out.println("requet jlong value:" + ctx.request.r + ", jint:" + (int)ctx.request.r);
					ngx_http_clojure_mem_post_write_event(ctx.request.r);
				} catch (InterruptedException e) {
					e.printStackTrace();
					break;
				} catch (ExecutionException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	public static void initWorkers(int n) {
		if (n < 1) {
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
	
	public static synchronized void initMemIndex(long idxpt) {
		initUnsafe();
	    
	    BYTE_ARRAY_OFFSET = UNSAFE.arrayBaseOffset(byte[].class);
	    
	    long[] index = new long[NGX_HTTP_CLOJURE_MEM_IDX_END + 1];
	    for (int i = 0; i < NGX_HTTP_CLOJURE_MEM_IDX_END + 1; i++) {
	    	index[i] = UNSAFE.getLong(idxpt + i * 8);
	    }
	    
	    
		MEM_INDEX = index;
		NGX_HTTP_CLOJURE_UINT_SIZE = MEM_INDEX[NGX_HTTP_CLOJURE_UINT_SIZE_IDX];
		
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
		
		NGX_HTTP_CLOJURE_PTR_SIZE = MEM_INDEX[NGX_HTTP_CLOJURE_PTR_SIZE_IDX];
		
		NGINX_CLOJURE_RT_WORKERS = MEM_INDEX[NGINX_CLOJURE_RT_WORKERS_ID];
		NGINX_CLOJURE_VER = MEM_INDEX[NGINX_CLOJURE_VER_ID];
		NGINX_VER = MEM_INDEX[NGINX_VER_ID];
		NGINX_CLOJURE_FULL_VER = "nginx-clojure/" + formatVer(NGINX_VER) + "-" + formatVer(NGINX_CLOJURE_VER);
		
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
		byte[] bytes;
		bytes = val.getBytes(encoding);
		long strAddr = ngx_palloc(pool, bytes.length);
		UNSAFE.putAddress(address, strAddr);
		ngx_http_clojure_mem_copy_to_addr(bytes, BYTE_ARRAY_OFFSET, strAddr, bytes.length);
		return bytes.length;
	}
	
	public static int eval(final int codeId, final long r) {
		
		final LazyRequestMap req = new LazyRequestMap(codeId, r);
		
		if (workers == null) {
			Map resp = handleRequest(req);
			return handleResponse(r, resp);
		}
		
		for (int i = 0; i < req.count(); i++) {
			req.element(i);
		}

		ngx_http_clojure_mem_inc_req_count(r);
		workers.submit(new Callable<NginxClojureRT.HandlerContext>() {
			@Override
			public HandlerContext call() throws Exception {
				Map resp = handleRequest(req);
				return new HandlerContext(req, resp);
			}
		});
		return NGX_DONE;
	}
	
	public static Map handleRequest(final LazyRequestMap req) {
		IFn f = HANDLERS.get(req.codeId);
		try{
			Map resp = (Map) f.invoke(req);
			return resp;
		}catch(Throwable e){
			return new PersistentArrayMap(new Object[] {STATUS, 500, BODY, e});
		}finally {
			if (req.valAt(BODY) instanceof Closeable) {
				try {
					((Closeable)req.valAt(BODY)).close();
				} catch (IOException e) {
					//log to nginx error log file
					e.printStackTrace();
				}
			}
		}
	}
	
	public static int handleResponse(long r) {
//		System.out.println("handleResponse request jlong:" +r + ", jint :" + (int)r);
//		for (Map.Entry<Long, Map> rr : REQ_RESP_MAP.entrySet()) {
			Map resp = REQ_RESP_MAP.remove(r);
			int rc = handleResponse(r, resp);
			ngx_http_finalize_request(r, rc);
//		}
		return NGX_OK;
	}
	
	public static int handleResponse(long r, final Map resp) {
		try {
			long pool = UNSAFE.getAddress(r + NGX_HTTP_CLOJURE_REQ_POOL_OFFSET);
			Object statusObj = resp.get(STATUS);
			int status = 200;
			if (statusObj != null) {
				if (statusObj instanceof Number){
					status = ((Number)statusObj).intValue();
				}else {
					status = Integer.parseInt(statusObj.toString());
				}
			}
			long headers_out = r + NGX_HTTP_CLOJURE_REQ_HEADERS_OUT_OFFSET;
			
			Map<String, Object> headers = (Map<String, Object>) resp.get(HEADERS);
			String contentType = null;
			if (headers != null) {
				contentType = (String) headers.get("content-type");
				for (Map.Entry<String, Object> hen : headers.entrySet()) {
					String name = hen.getKey();
					Object val = hen.getValue();
					if (name == null || val == null || "content-type".equalsIgnoreCase(name) || "content-length".equalsIgnoreCase(name)) {
						continue;
					}
					ResponseHeaderPusher pusher = KNOWN_RESP_HEADERS.get(hen.getKey());
					if (pusher == null) {
						pusher = new ResponseUnknownHeaderPusher(hen.getKey());
					}
					pusher.push(headers_out, pool, val);
				}
			}
			
			if (headers == null || !headers.containsKey("server")) {
				SERVER_PUSHER.push(headers_out, pool, NGINX_CLOJURE_FULL_VER);
			}
			
			if (contentType == null){
				ngx_http_set_content_type(r);
			}else {
				int contentTypeLen = pushNGXString(headers_out + NGX_HTTP_CLOJURE_HEADERSO_CONTENT_TYPE_OFFSET, contentType, DEFAULT_ENCODING, pool);
				//be friendly to gzip module 
				pushNGXSizet(headers_out + NGX_HTTP_CLOJURE_HEADERSO_CONTENT_TYPE_LEN_OFFSET, contentTypeLen);
			}
			
			Object body = resp.get(BODY);
			long b = 0;
			if (body instanceof String) {
				String bodyStr = (String) body;
				byte[] bytes = bodyStr.getBytes(DEFAULT_ENCODING);
				pushNGXOfft(headers_out + NGX_HTTP_CLOJURE_HEADERSO_CONTENT_LENGTH_N_OFFSET, bytes.length);
				b = ngx_create_temp_buf(pool, bytes.length);
				ngx_http_clojure_mem_init_ngx_buf(b, bytes, BYTE_ARRAY_OFFSET, bytes.length, 1);
			}else if (body instanceof File) {
				if (! ((File)body).exists() ) {
					return 404;
				}else {
					byte[] bytes = ((File)body).getPath().getBytes();
					long file = ngx_pcalloc(pool, bytes.length+1);
					ngx_http_clojure_mem_copy_to_addr(bytes, BYTE_ARRAY_OFFSET, file, bytes.length);
					b = ngx_create_file_buf(r, file, bytes.length);
					if (b == 0){
						return 500;
					}
				}
			}
			pushNGXInt(headers_out + NGX_HTTP_CLOJURE_HEADERSO_STATUS_OFFSET, status);
			int rc = (int)ngx_http_send_header(r);
			if (rc == NGX_ERROR || rc > NGX_OK){
				return rc;
			}
			long chain = ngx_palloc(pool, NGX_HTTP_CLOJURE_CHAINT_SIZE);
			UNSAFE.putAddress(chain + NGX_HTTP_CLOJURE_CHAIN_BUF_OFFSET, b);
			UNSAFE.putAddress(chain + NGX_HTTP_CLOJURE_CHAIN_NEXT_OFFSET, 0);
			return (int)ngx_http_output_filter(r, chain);

		}catch(Throwable e) {
			e.printStackTrace();
			return 500;
		}
	}
	
}
