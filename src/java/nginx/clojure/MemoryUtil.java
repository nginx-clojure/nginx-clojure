package nginx.clojure;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sun.misc.Unsafe;
import clojure.lang.IFn;
import clojure.lang.Keyword;
import clojure.lang.RT;

public class MemoryUtil {

	public static int NGX_HTTP_CLOJURE_MEM_IDX_START = 0;

	/* index for size of ngx_uint_t */
	public static int NGX_HTTP_CLOJURE_UINT_SIZE_IDX = 0;
	public static long NGX_HTTP_CLOJURE_UINT_SIZE;
	public static int NGX_HTTP_CLOJURE_PTR_SIZE_IDX = 1;
	public static long NGX_HTTP_CLOJURE_PTR_SIZE;
	public static int  NGX_HTTP_CLOJURE_SIZET_SIZE_IDX = 2;
	public static long NGX_HTTP_CLOJURE_SIZET_SIZE;
	public static int   NGX_HTTP_CLOJURE_OFFT_SIZE_IDX = 3;
	public static long NGX_HTTP_CLOJURE_OFFT_SIZE;
	
	/* index for size of ngx_str_t */
	public static int NGX_HTTP_CLOJURE_STR_SIZE_IDX = 8;
	public static long NGX_HTTP_CLOJURE_STR_SIZE;
	/* field offset index for ngx_str_t */
	public static int NGX_HTTP_CLOJURE_STR_LEN_IDX = 9;
	public static long NGX_HTTP_CLOJURE_STR_LEN_OFFSET;
	public static int NGX_HTTP_CLOJURE_STR_DATA_IDX = 10;
	public static long NGX_HTTP_CLOJURE_STR_DATA_OFFSET;

	/* index for size of ngx_table_elt_t */
	public static int NGX_HTTP_CLOJURE_TELT_SIZE_IDX = 11;
	public static long NGX_HTTP_CLOJURE_TELT_SIZE;
	/* field offset index for ngx_table_elt_t */
	public static int NGX_HTTP_CLOJURE_TELT_HASH_IDX = 12;
	public static long NGX_HTTP_CLOJURE_TELT_HASH_OFFSET;
	public static int NGX_HTTP_CLOJURE_TELT_KEY_IDX = 13;
	public static long NGX_HTTP_CLOJURE_TELT_KEY_OFFSET;
	public static int NGX_HTTP_CLOJURE_TELT_VALUE_IDX = 14;
	public static long NGX_HTTP_CLOJURE_TELT_VALUE_OFFSET;
	public static int NGX_HTTP_CLOJURE_TELT_LOWCASE_KEY_IDX = 15;
	public static long NGX_HTTP_CLOJURE_TELT_LOWCASE_KEY_OFFSET;

	public static int  NGX_HTTP_CLOJURE_CHAIN_SIZE_IDX = 16;
	public static long NGX_HTTP_CLOJURE_CHAIN_SIZE;
	public static int NGX_HTTP_CLOJURE_CHAIN_BUF_IDX = 17;
	public static long NGX_HTTP_CLOJURE_CHAIN_BUF_OFFSET;
	public static int NGX_HTTP_CLOJURE_CHAIN_NEXT_IDX = 18;
	public static long NGX_HTTP_CLOJURE_CHAIN_NEXT_OFFSET;
	
	/* index for size of ngx_http_request_t */
	public static int NGX_HTTP_CLOJURE_REQ_SIZE_IDX = 32;
	public static long NGX_HTTP_CLOJURE_REQ_SIZE;
	/* field offset index for ngx_http_request_t */
	public static int NGX_HTTP_CLOJURE_REQ_METHOD_IDX = 33;
	public static long NGX_HTTP_CLOJURE_REQ_METHOD_OFFSET;
	public static int NGX_HTTP_CLOJURE_REQ_URI_IDX = 34;
	public static long NGX_HTTP_CLOJURE_REQ_URI_OFFSET;
	public static int NGX_HTTP_CLOJURE_REQ_ARGS_IDX = 35;
	public static long NGX_HTTP_CLOJURE_REQ_ARGS_OFFSET;
	public static int NGX_HTTP_CLOJURE_REQ_HEADERS_IN_IDX = 36;
	public static long NGX_HTTP_CLOJURE_REQ_HEADERS_IN_OFFSET;
	public static int NGX_HTTP_CLOJURE_REQ_POOL_IDX = 37;
	public static long NGX_HTTP_CLOJURE_REQ_POOL_OFFSET;
	public static int NGX_HTTP_CLOJURE_REQ_HEADERS_OUT_IDX = 38;
	public static long NGX_HTTP_CLOJURE_REQ_HEADERS_OUT_OFFSET;
	
	/* index for size of ngx_http_headers_in_t */
	public static int NGX_HTTP_CLOJURE_HEADERS_SIZE_IDX = 64;
	public static long NGX_HTTP_CLOJURE_HEADERS_SIZE;
	/* field offset index for ngx_http_headers_in_t */
	public static int NGX_HTTP_CLOJURE_HEADERS_HOST_IDX = 65;
	public static long NGX_HTTP_CLOJURE_HEADERS_HOST_OFFSET;
	public static int NGX_HTTP_CLOJURE_HEADERS_CONTENT_LENGTH_IDX = 66;
	public static long NGX_HTTP_CLOJURE_HEADERS_CONTENT_LENGTH_OFFSET;
	public static int NGX_HTTP_CLOJURE_HEADERS_CONTENT_TYPE_IDX = 67;
	public static long NGX_HTTP_CLOJURE_HEADERS_CONTENT_TYPE_OFFSET;

	
	public static int NGX_HTTP_CLOJURE_HEADERSO_SIZE_IDX = 128;
	public static long NGX_HTTP_CLOJURE_HEADERSO_SIZE;
	/*field offset index for ngx_http_headers_out_t*/
	public static int NGX_HTTP_CLOJURE_HEADERSO_STATUS_IDX = 129;
	public static long NGX_HTTP_CLOJURE_HEADERSO_STATUS_OFFSET;
	public static int NGX_HTTP_CLOJURE_HEADERSO_CONTENT_LENGTH_N_IDX = 130;
	public static long NGX_HTTP_CLOJURE_HEADERSO_CONTENT_LENGTH_N_OFFSET;
	public static int NGX_HTTP_CLOJURE_HEADERSO_CONTENT_TYPE_IDX = 131;
	public static long NGX_HTTP_CLOJURE_HEADERSO_CONTENT_TYPE_OFFSET;
	

	public static int NGX_HTTP_CLOJURE_MEM_IDX_END = 255;
	
	//ngx_core.h
	public static int  NGX_OK       =   0;
	public static int  NGX_ERROR    =  -1;
	public static int  NGX_AGAIN    =  -2;
	public static int  NGX_BUSY     =  -3;
	public static int  NGX_DONE     =  -4;
	public static int  NGX_DECLINED =  -5;
	public static int  NGX_ABORT    =  -6;
	
	
	public static int NGX_HTTP_GET = 0x0002;
	public static int NGX_HTTP_HEAD = 0x0004;
	public static int NGX_HTTP_POST = 0x0008;
	public static int NGX_HTTP_PUT = 0x0010;
	public static int NGX_HTTP_DELETE = 0x0020;
	public static int NGX_HTTP_MKCOL = 0x0040;
	public static int NGX_HTTP_COPY = 0x0080;
	public static int NGX_HTTP_MOVE = 0x0100;
	public static int NGX_HTTP_OPTIONS = 0x0200;
	public static int NGX_HTTP_PROPFIND = 0x0400;
	public static int NGX_HTTP_PROPPATCH = 0x0800;
	public static int NGX_HTTP_LOCK = 0x1000;
	public static int NGX_HTTP_UNLOCK = 0x2000;
	public static int NGX_HTTP_PATCH = 0x4000;
	public static int NGX_HTTP_TRACE = 0x8000;
	
	
	public static int BYTE_ARRAY_OFFSET;

	public static long[] MEM_INDEX;
	
	private static Unsafe unsafe = null;
	
	private static List<IFn>  handlers = new ArrayList<IFn>();
	
	private static Map<Long, Integer> codeToIdMap = new HashMap<>();
	
	
	public native static long ngx_palloc(long pool, long size);
	
	public native static long ngx_pcalloc(long pool, long size);
	
	public native static long ngx_create_temp_buf(long pool, long size);
	
	public native static long ngx_create_file_buf(long r, long file, long name_len);
	
	public native static long ngx_http_send_header(long r);
	
	public native static long ngx_http_output_filter(long r, long chain);

	public native static long ngx_http_clojure_mem_init_ngx_buf(long buf, Object obj, long offset, long len, int last_buf);
	
	public native static long ngx_http_clojure_mem_get_obj_attr(Object obj);
	
	public native static void ngx_http_clojure_mem_copy_to_obj(long src, Object obj, long offset, long len);
	
	public native static void ngx_http_clojure_mem_copy_to_addr(Object obj, long offset, long dest, long len);
	
	
	public static synchronized void initMemIndex(long idxpt) {
		if (unsafe != null) {
			return;
		}
	    try{
	        Field field = Unsafe.class.getDeclaredField("theUnsafe");
	        field.setAccessible(true);
	        unsafe = (Unsafe)field.get(null);
	    }
	    catch (Exception e){
	        throw new RuntimeException(e);
	    }
	    
	    BYTE_ARRAY_OFFSET = unsafe.arrayBaseOffset(byte[].class);
	    
	    long[] index = new long[NGX_HTTP_CLOJURE_MEM_IDX_END + 1];
	    for (int i = 0; i < NGX_HTTP_CLOJURE_MEM_IDX_END + 1; i++) {
	    	index[i] = unsafe.getLong(idxpt + i * 8);
	    }
	    
	    
		MEM_INDEX = index;
		NGX_HTTP_CLOJURE_UINT_SIZE = MEM_INDEX[NGX_HTTP_CLOJURE_UINT_SIZE_IDX];
		
		NGX_HTTP_CLOJURE_STR_SIZE = MEM_INDEX[NGX_HTTP_CLOJURE_STR_SIZE_IDX];
		NGX_HTTP_CLOJURE_STR_LEN_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_STR_LEN_IDX];
		NGX_HTTP_CLOJURE_STR_DATA_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_STR_DATA_IDX];
		NGX_HTTP_CLOJURE_SIZET_SIZE = MEM_INDEX[NGX_HTTP_CLOJURE_SIZET_SIZE_IDX];
		NGX_HTTP_CLOJURE_OFFT_SIZE = MEM_INDEX[NGX_HTTP_CLOJURE_OFFT_SIZE_IDX];
		
		NGX_HTTP_CLOJURE_TELT_SIZE = MEM_INDEX[NGX_HTTP_CLOJURE_TELT_SIZE_IDX];
		NGX_HTTP_CLOJURE_TELT_HASH_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_TELT_HASH_IDX];
		NGX_HTTP_CLOJURE_TELT_KEY_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_TELT_KEY_IDX];
		NGX_HTTP_CLOJURE_TELT_VALUE_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_TELT_VALUE_IDX];
		NGX_HTTP_CLOJURE_TELT_LOWCASE_KEY_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_TELT_LOWCASE_KEY_IDX];
		
		NGX_HTTP_CLOJURE_REQ_SIZE = MEM_INDEX[NGX_HTTP_CLOJURE_REQ_SIZE_IDX];
		NGX_HTTP_CLOJURE_REQ_METHOD_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_REQ_METHOD_IDX];
		NGX_HTTP_CLOJURE_REQ_URI_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_REQ_URI_IDX];
		NGX_HTTP_CLOJURE_REQ_ARGS_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_REQ_ARGS_IDX];
		NGX_HTTP_CLOJURE_REQ_HEADERS_IN_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_REQ_HEADERS_IN_IDX];
		NGX_HTTP_CLOJURE_REQ_POOL_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_REQ_POOL_IDX];
		NGX_HTTP_CLOJURE_REQ_HEADERS_OUT_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_REQ_HEADERS_OUT_IDX];
		
		NGX_HTTP_CLOJURE_HEADERS_SIZE = MEM_INDEX[NGX_HTTP_CLOJURE_HEADERS_SIZE_IDX];
		NGX_HTTP_CLOJURE_HEADERS_HOST_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_HEADERS_HOST_IDX];
		NGX_HTTP_CLOJURE_HEADERS_CONTENT_LENGTH_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_HEADERS_CONTENT_LENGTH_IDX];
		NGX_HTTP_CLOJURE_HEADERS_CONTENT_TYPE_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_HEADERS_CONTENT_TYPE_IDX];
		
		NGX_HTTP_CLOJURE_CHAIN_SIZE = MEM_INDEX[NGX_HTTP_CLOJURE_CHAIN_SIZE_IDX];
		NGX_HTTP_CLOJURE_CHAIN_BUF_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_CHAIN_BUF_IDX];
		NGX_HTTP_CLOJURE_CHAIN_NEXT_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_CHAIN_NEXT_IDX];
		
		NGX_HTTP_CLOJURE_HEADERSO_SIZE = MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSO_SIZE_IDX];
		NGX_HTTP_CLOJURE_HEADERSO_STATUS_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSO_STATUS_IDX];
		NGX_HTTP_CLOJURE_HEADERSO_CONTENT_LENGTH_N_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSO_CONTENT_LENGTH_N_IDX];
		NGX_HTTP_CLOJURE_HEADERSO_CONTENT_TYPE_OFFSET = MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSO_CONTENT_TYPE_IDX];
		
		
		NGX_HTTP_CLOJURE_PTR_SIZE = MEM_INDEX[NGX_HTTP_CLOJURE_PTR_SIZE_IDX];
	}
	
	
	public static synchronized int registerCode(long codePtr, long len) {
		if (codeToIdMap.containsKey(codePtr)) {
			return codeToIdMap.get(codePtr);
		}
		String code = fetchString(codePtr, (int)len, DEFAULT_ENCODING);
		IFn f = (IFn)RT.var("clojure.core", "eval").invoke(RT.var("clojure.core","read-string").invoke(code));
		handlers.add(f);
		return handlers.size() - 1;
	}
	
	/**
	 * convert ngx_str_t to  java String
	 */
	public static final String fetchNGXString(long address, Charset encoding) {
		long lenAddr = address + NGX_HTTP_CLOJURE_STR_LEN_OFFSET;
		int len = fetchNGXInt(lenAddr);
		if (len == 0){
			return "";
		}
		return fetchString(address + NGX_HTTP_CLOJURE_STR_DATA_OFFSET, len, encoding);
	}
	
	public static final void pushNGXString(long address, String val, Charset encoding, long pool){
			long lenAddr = address + NGX_HTTP_CLOJURE_STR_LEN_OFFSET;
			long dataAddr = address + NGX_HTTP_CLOJURE_STR_DATA_OFFSET;
			pushNGXInt(lenAddr, pushString(dataAddr, val, encoding, pool));
	}
	
	
	public static final int fetchNGXInt(long address){
		return NGX_HTTP_CLOJURE_UINT_SIZE == 4 ? unsafe.getInt(address) : (int)unsafe.getLong(address);
	}
	
	public static final void pushNGXInt(long address, int val){
		if (NGX_HTTP_CLOJURE_UINT_SIZE == 4){
			unsafe.putInt(address, val);
		}else {
			unsafe.putLong(address, val);
		}
	}
	
	public static final void pushNGXOfft(long address, int val){
		if (NGX_HTTP_CLOJURE_OFFT_SIZE == 4){
			unsafe.putInt(address, val);
		}else {
			unsafe.putLong(address, val);
		}
	}
	
	
	//TODO: for better performance to use direct encoder instead of bytes copy
	public static final String fetchString(long address, int size, Charset encoding) {
		byte[] buf = new byte[size];
		ngx_http_clojure_mem_copy_to_obj(unsafe.getAddress(address), buf, BYTE_ARRAY_OFFSET, size);
		return new String(buf, encoding);

	}
	
	
	public static final int pushString(long address, String val, Charset encoding, long pool) {
		byte[] bytes;
		bytes = val.getBytes(encoding);
		long strAddr = ngx_palloc(pool, bytes.length);
		unsafe.putAddress(address, strAddr);
		ngx_http_clojure_mem_copy_to_addr(bytes, BYTE_ARRAY_OFFSET, strAddr, bytes.length);
		return bytes.length;
	}
	
	public static  class RequestMap extends HashMap  {
		
		public static final Keyword URI = RT.keyword(null, "uri");
		public static final Keyword REQUEST_METHOD = RT.keyword(null, "request-method");
		public static final Keyword GET = RT.keyword(null, "get");
		public static final Keyword POST = RT.keyword(null, "post");
		public static final Keyword QUERY_STRING = RT.keyword(null, "query-string");
		public static final Keyword[] HTTP_METHODS = {RT.keyword(null, "UNKNOWN"), GET, RT.keyword(null, "HEAD"), POST};
		
		public RequestMap() {
		}
		
		@SuppressWarnings("unchecked")
		public RequestMap(long r) {
			put(URI, fetchNGXString(r + NGX_HTTP_CLOJURE_REQ_URI_OFFSET, DEFAULT_ENCODING));
			int methodIdx = 0;
			int methodCode = fetchNGXInt(r + NGX_HTTP_CLOJURE_REQ_METHOD_OFFSET);
			while (methodCode > 1) {
				methodCode = methodCode >> 1;
				methodIdx ++;
			}
			if (methodIdx >=  HTTP_METHODS.length){
				put(REQUEST_METHOD, HTTP_METHODS[0]);
			}else {
				put(REQUEST_METHOD, HTTP_METHODS[methodIdx]);
			}
			put(QUERY_STRING, fetchNGXString(r + NGX_HTTP_CLOJURE_REQ_ARGS_OFFSET, DEFAULT_ENCODING));
		}
	}
	
	public static final Keyword STATUS = RT.keyword(null, "status");
	public static final Keyword BODY = RT.keyword(null, "body");
	public static final Keyword HEADERS = RT.keyword(null, "headers");
	public static final String DEFAULT_ENCODING_STR = "utf-8";
	public static final Charset DEFAULT_ENCODING = Charset.forName(DEFAULT_ENCODING_STR);
	
	public static int eval(int codeId, long r) {
		IFn f = handlers.get(codeId);
		RequestMap req = new RequestMap(r);
		try{
			long pool = unsafe.getAddress(r + NGX_HTTP_CLOJURE_REQ_POOL_OFFSET);
			Map resp = (Map) f.invoke(req);
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
			pushNGXInt(headers_out + NGX_HTTP_CLOJURE_HEADERSO_STATUS_OFFSET, status);
			Map headers = (Map) resp.get(HEADERS);
			String contentType = (String) headers.get("content-type");
			if (contentType == null){
				contentType = "text/html; charset=UTF-8";
			}
			pushNGXString(headers_out + NGX_HTTP_CLOJURE_HEADERSO_CONTENT_TYPE_OFFSET, contentType, DEFAULT_ENCODING, pool);
			
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
					return 400;
				}
				byte[] bytes = ((File)body).getPath().getBytes();
				long file = ngx_pcalloc(pool, bytes.length+1);
				ngx_http_clojure_mem_copy_to_addr(bytes, BYTE_ARRAY_OFFSET, file, bytes.length);
				b = ngx_create_file_buf(r, file, bytes.length);
				if (b == 0){
					return 500;
				}
			}
			int rc = (int)ngx_http_send_header(r);
			if (rc == NGX_ERROR || rc > NGX_OK){
				return rc;
			}
			long chain = ngx_palloc(pool, NGX_HTTP_CLOJURE_CHAIN_SIZE);
			unsafe.putAddress(chain + NGX_HTTP_CLOJURE_CHAIN_BUF_OFFSET, b);
			unsafe.putAddress(chain + NGX_HTTP_CLOJURE_CHAIN_NEXT_OFFSET, 0);
			return (int)ngx_http_output_filter(r, chain);
		}catch(Throwable e){
			e.printStackTrace();
			return 500;
		}
		
		
	}
}
