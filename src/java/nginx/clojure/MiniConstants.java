/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

/**
 * Mini constants needed Nginx-Clojure Basic Platform
 * @author Zhang,Yuexiang (xfeep)
 *
 */
public class MiniConstants {


	/**
	 * Ring Spec (1.1) Strings : https://github.com/ring-clojure/ring/blob/master/SPEC
	 */
	public static final String SERVER_PORT = "server-port"; 
	public static final String SERVER_NAME = "server-name";
	public static final String REMOTE_ADDR = "remote-addr";
	public static final String URI = "uri";
	public static final String QUERY_STRING = "query-string";
	public static final String SCHEME = "scheme";
	public static final String REQUEST_METHOD = "request-method";
	public static final String CONTENT_TYPE = "content-type";
	public static final String CHARACTER_ENCODING = "character-encoding";
	public static final String SSL_CLIENT_CERT = "ssl-client-cert";
	public static final String HEADERS = "headers";
	public static final String BODY = "body";
	
	/**
	 * HTTP methods
	 * */
	public static final String UNKNOWN = "UNKNOWN";
	public static final String GET = "get";
	public static final String HEAD = "head";
	public static final String POST = "post";
	public static final String PUT = "put";
	public static final String DELETE = "delete";
	public static final String MKCOL = "mkcol";
	public static final String COPY = "copy";
	public static final String MOVE = "move";
	public static final String OPTIONS = "options";
	public static final String PROPFIND = "propfind";
	public static final String PROPPATCH = "proppatch";
	public static final String LOCK = "lock";
	public static final String UNLOCK = "unlock";
	public static final String PATCH = "patch";
	public static final String TRACE = "trace";
	
	
	
	public static final String[] HTTP_METHODS = { UNKNOWN, GET, HEAD,
			POST, PUT, DELETE, MKCOL, COPY, MOVE, OPTIONS, PROPFIND,
			PROPPATCH, LOCK, UNLOCK, PATCH, TRACE };
	
	
	
	public static Map<String, NginxHeaderHolder> KNOWN_REQ_HEADERS = new CaseInsensitiveMap<NginxHeaderHolder>();
	
	public static Map<String, NginxHeaderHolder> KNOWN_RESP_HEADERS = new CaseInsensitiveMap<NginxHeaderHolder>();
	
	public static NginxHeaderHolder RESP_CONTENT_TYPE_HOLDER;
	
	public static Map<String, Long> MIME_TYPES = new HashMap<String, Long>(); 
	
	public static Map<String, Long> CORE_VARS = new CaseInsensitiveMap<Long>();
	
	public static Map<String, Long> HEADERS_NAMES = new CaseInsensitiveMap<Long>();
	
	public static final String STATUS_STR = "status";
//	public static final String BODY = RT.keyword(null, "body");
//	public static final String HEADERS = RT.keyword(null, "headers");
	public static final String DEFAULT_ENCODING_STR = "utf-8";
	public static final Charset DEFAULT_ENCODING = Charset.forName(DEFAULT_ENCODING_STR);
	
	public static final int NGX_CLOJURE_BUF_LAST_OF_NONE = 0;
	public static final int NGX_CLOJURE_BUF_LAST_OF_CHAIN = 1;
	public static final int NGX_CLOJURE_BUF_LAST_OF_RESPONSE = 2;
	
	public static final int NGX_CHAIN_FILTER_CHUNK_NO_LAST = -1;
	public static final int NGX_CHAIN_FILTER_CHUNK_HAS_LAST = -2;
	
	public static final int NGX_CLOJURE_BUF_LAST_FLAG = 0x01;
	public static final int NGX_CLOJURE_BUF_FLUSH_FLAG = 0x02;
	public static final int NGX_CLOJURE_BUF_IGNORE_FILTER_FLAG = 0x04;
	public static final int NGX_CLOJURE_BUF_FILE_FLAG = 0x08;
	public static final int NGX_CLOJURE_BUF_MEM_FLAG = 0x10;
	
	/**
	 * this constant hints whether we send java.lang.String or bytes (byte[], ByteBuffer) from app level
	 */
	public static final int NGX_CLOJURE_BUF_APP_MSGTXT = 0x08;
	
	/**
	 * System Event : 0x00 ~ 0x1f
	 * App Event : 0x20 ~ 0xff
	 * Simple  Event : 0x00 ~ 0x7f, only event id (7Byte), no message body
	 * Complex Event : 0x80 ~ 0xff
	 */
	public static final int POST_EVENT_TYPE_SYSTEM_EVENT_IDX_START = 0;
	public static final int POST_EVENT_TYPE_HANDLE_RESPONSE = 0;
	public static final int POST_EVENT_TYPE_CLOSE_SOCKET = 0x01;
	public static final int POST_EVENT_TYPE_HIJACK_SEND = 0x02;
	public static final int POST_EVENT_TYPE_HIJACK_SEND_HEADER = 0x03;
	public static final int POST_EVENT_TYPE_HIJACK_SEND_RESPONSE = 0x04;
	public static final int POST_EVENT_TYPE_HIJACK_WRITE = 0x05;
	public static final int POST_EVENT_TYPE_PUB = 0x1e;
	public static final int POST_EVENT_TYPE_POLL_TASK = 0x1f;
	public static final int POST_EVENT_TYPE_SYSTEM_EVENT_IDX_END = 0x1f;
	public static final int POST_EVENT_TYPE_APPICATION_EVENT_IDX_START = 0x20;
	public static final int POST_EVENT_TYPE_COMPLEX_EVENT_IDX_START = 0x80;
	public static final int POST_EVENT_TYPE_COMPLEX_EVENT_IDX_END = 0xff;
	
	
	public static int BYTE_ARRAY_OFFSET;
	public static long STRING_CHAR_ARRAY_OFFSET;
	public static long STRING_OFFSET_OFFSET;
	
	
	public static final int  NGX_HTTP_CLOJURE_GET_HEADER_FLAG_HEADERS_OUT = 1;
	public static final int  NGX_HTTP_CLOJURE_GET_HEADER_FLAG_MERGE_KEY = 2;
	
	/*Thess consts won't be final until we think they are really stable. 
	 * So that it can avoid some java compiler just use literal integer to replace where it is used.*/
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
	public static int   NGX_HTTP_CLOJURE_BUFFER_SIZE_IDX = 4;
	public static long NGX_HTTP_CLOJURE_BUFFER_SIZE;
	
	
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
	public static int NGX_HTTP_CLOJURE_TEL_HASH_IDX = 12;
	public static long NGX_HTTP_CLOJURE_TEL_HASH_OFFSET;
	public static int NGX_HTTP_CLOJURE_TEL_KEY_IDX = 13;
	public static long NGX_HTTP_CLOJURE_TEL_KEY_OFFSET;
	public static int NGX_HTTP_CLOJURE_TEL_VALUE_IDX = 14;
	public static long NGX_HTTP_CLOJURE_TEL_VALUE_OFFSET;
	public static int NGX_HTTP_CLOJURE_TEL_LOWCASE_KEY_IDX = 15;
	public static long NGX_HTTP_CLOJURE_TEL_LOWCASE_KEY_OFFSET;

	public static int  NGX_HTTP_CLOJURE_CHAINT_SIZE_IDX = 16;
	public static long NGX_HTTP_CLOJURE_CHAINT_SIZE;
	public static int NGX_HTTP_CLOJURE_CHAIN_BUF_IDX = 17;
	public static long NGX_HTTP_CLOJURE_CHAIN_BUF_OFFSET;
	public static int NGX_HTTP_CLOJURE_CHAIN_NEXT_IDX = 18;
	public static long NGX_HTTP_CLOJURE_CHAIN_NEXT_OFFSET;
	
	public static int NGX_HTTP_CLOJURE_VARIABLET_SIZE_IDX = 19;
	public static long NGX_HTTP_CLOJURE_VARIABLET_SIZE;
	
	public static int NGX_HTTP_CLOJURE_CORE_VARIABLES_ADDR_IDX = 20;
	public static long NGX_HTTP_CLOJURE_CORE_VARIABLES_ADDR; 
	public static int NGX_HTTP_CLOJURE_HEADERS_NAMES_ADDR_IDX  = 21;
	public static long NGX_HTTP_CLOJURE_HEADERS_NAMES_ADDR;
	
	
	public static int  NGX_HTTP_CLOJURE_ARRAYT_SIZE_IDX = 22;
	public static long NGX_HTTP_CLOJURE_ARRAYT_SIZE;
	public static int  NGX_HTTP_CLOJURE_ARRAY_ELTS_IDX = 23;
	public static long NGX_HTTP_CLOJURE_ARRAY_ELTS_OFFSET;
	public static int  NGX_HTTP_CLOJURE_ARRAY_NELTS_IDX = 24;
	public static long NGX_HTTP_CLOJURE_ARRAY_NELTS_OFFSET;
	public static int  NGX_HTTP_CLOJURE_ARRAY_SIZE_IDX = 25;
	public static long NGX_HTTP_CLOJURE_ARRAY_SIZE_OFFSET;
	public static int  NGX_HTTP_CLOJURE_ARRAY_NALLOC_IDX = 26;
	public static long NGX_HTTP_CLOJURE_ARRAY_NALLOC_OFFSET;
	public static int  NGX_HTTP_CLOJURE_ARRAY_POOL_IDX = 27;
	public static long NGX_HTTP_CLOJURE_ARRAY_POOL_OFFSET;
	
	public static int NGX_HTTP_CLOJURE_KEYVALT_SIZE_IDX = 28;
	public static long NGX_HTTP_CLOJURE_KEYVALT_SIZE;
	public static int  NGX_HTTP_CLOJURE_KEYVALT_KEY_IDX = 29;
	public static long NGX_HTTP_CLOJURE_KEYVALT_KEY_OFFSET;
	public static int  NGX_HTTP_CLOJURE_KEYVALT_VALUE_IDX = 30;
	public static long NGX_HTTP_CLOJURE_KEYVALT_VALUE_OFFSET;
	
	/* index for size of ngx_http_request_t */
	public static int NGX_HTTP_CLOJURE_REQT_SIZE_IDX = 32;
	public static long NGX_HTTP_CLOJURE_REQT_SIZE;
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
	
	public static int NGX_HTTP_CLOJURE_MIME_TYPES_ADDR_IDX = 63;
	public static long  NGX_HTTP_CLOJURE_MIME_TYPES_ADDR;
	
	/*index for size of ngx_http_headers_in_t */
	public static int  NGX_HTTP_CLOJURE_HEADERSIT_SIZE_IDX = 64;
	public static long NGX_HTTP_CLOJURE_HEADERSIT_SIZE;
	/*field offset index for ngx_http_headers_in_t*/
	public static int  NGX_HTTP_CLOJURE_HEADERSI_HOST_IDX = 65;
	public static long NGX_HTTP_CLOJURE_HEADERSI_HOST_OFFSET;
	public static int  NGX_HTTP_CLOJURE_HEADERSI_CONNECTION_IDX = 66;
	public static long NGX_HTTP_CLOJURE_HEADERSI_CONNECTION_OFFSET;
	public static int  NGX_HTTP_CLOJURE_HEADERSI_IF_MODIFIED_SINCE_IDX = 67;
	public static long NGX_HTTP_CLOJURE_HEADERSI_IF_MODIFIED_SINCE_OFFSET;
	public static int  NGX_HTTP_CLOJURE_HEADERSI_IF_UNMODIFIED_SINCE_IDX = 68;
	public static long NGX_HTTP_CLOJURE_HEADERSI_IF_UNMODIFIED_SINCE_OFFSET;
	public static int  NGX_HTTP_CLOJURE_HEADERSI_USER_AGENT_IDX = 69;
	public static long NGX_HTTP_CLOJURE_HEADERSI_USER_AGENT_OFFSET;
	public static int  NGX_HTTP_CLOJURE_HEADERSI_REFERER_IDX  = 70;
	public static long NGX_HTTP_CLOJURE_HEADERSI_REFERER_OFFSET;
	public static int  NGX_HTTP_CLOJURE_HEADERSI_CONTENT_LENGTH_IDX  = 71;
	public static long NGX_HTTP_CLOJURE_HEADERSI_CONTENT_LENGTH_OFFSET;
	public static int  NGX_HTTP_CLOJURE_HEADERSI_CONTENT_TYPE_IDX  = 72;
	public static long NGX_HTTP_CLOJURE_HEADERSI_CONTENT_TYPE_OFFSET;
	public static int  NGX_HTTP_CLOJURE_HEADERSI_RANGE_IDX  = 73;
	public static long NGX_HTTP_CLOJURE_HEADERSI_RANGE_OFFSET ;
	public static int  NGX_HTTP_CLOJURE_HEADERSI_IF_RANGE_IDX  = 74;
	public static long NGX_HTTP_CLOJURE_HEADERSI_IF_RANGE_OFFSET;
	public static int  NGX_HTTP_CLOJURE_HEADERSI_TRANSFER_ENCODING_IDX  = 75;
	public static long NGX_HTTP_CLOJURE_HEADERSI_TRANSFER_ENCODING_OFFSET;
	public static int  NGX_HTTP_CLOJURE_HEADERSI_EXPECT_IDX  = 76;
	public static long NGX_HTTP_CLOJURE_HEADERSI_EXPECT_OFFSET;

	//#if (NGX_HTTP_GZIP)
	public static int  NGX_HTTP_CLOJURE_HEADERSI_ACCEPT_ENCODING_IDX = 77;
	public static long NGX_HTTP_CLOJURE_HEADERSI_ACCEPT_ENCODING_OFFSET;
	public static int  NGX_HTTP_CLOJURE_HEADERSI_VIA_IDX  = 78;
	public static long NGX_HTTP_CLOJURE_HEADERSI_VIA_OFFSET;
	//#endif

	public static int  NGX_HTTP_CLOJURE_HEADERSI_AUTHORIZATION_IDX  = 79;
	public static long NGX_HTTP_CLOJURE_HEADERSI_AUTHORIZATION_OFFSET;
	public static int  NGX_HTTP_CLOJURE_HEADERSI_KEEP_ALIVE_IDX  = 80;
	public static long NGX_HTTP_CLOJURE_HEADERSI_KEEP_ALIVE_OFFSET ;

	//#if (NGX_HTTP_PROXY || NGX_HTTP_REALIP || NGX_HTTP_GEO)
	public static int  NGX_HTTP_CLOJURE_HEADERSI_X_FORWARDED_FOR_IDX  = 81;
	public static long NGX_HTTP_CLOJURE_HEADERSI_X_FORWARDED_FOR_OFFSET ;
	//#endif

	//#if (NGX_HTTP_REALIP)
	public static int NGX_HTTP_CLOJURE_HEADERSI_X_REAL_IP_IDX  = 82;
	public static long NGX_HTTP_CLOJURE_HEADERSI_X_REAL_IP_OFFSET;
	//#endif

	//#if (NGX_HTTP_HEADERS)
	public static int  NGX_HTTP_CLOJURE_HEADERSI_ACCEPT_IDX = 83;
	public static long NGX_HTTP_CLOJURE_HEADERSI_ACCEPT_OFFSET;
	public static int  NGX_HTTP_CLOJURE_HEADERSI_ACCEPT_LANGUAGE_IDX = 84;
	public static long NGX_HTTP_CLOJURE_HEADERSI_ACCEPT_LANGUAGE_OFFSET;
	//#endif

	//#if (NGX_HTTP_DAV)
	public static int  NGX_HTTP_CLOJURE_HEADERSI_DEPTH_IDX = 85;
	public static long NGX_HTTP_CLOJURE_HEADERSI_DEPTH_OFFSET;
	public static int  NGX_HTTP_CLOJURE_HEADERSI_DESTINATION_IDX = 86;
	public static long NGX_HTTP_CLOJURE_HEADERSI_DESTINATION_OFFSET ;
	public static int  NGX_HTTP_CLOJURE_HEADERSI_OVERWRITE_IDX  = 87;
	public static long NGX_HTTP_CLOJURE_HEADERSI_OVERWRITE_OFFSET;
	public static int  NGX_HTTP_CLOJURE_HEADERSI_DATE_IDX  = 88;
	public static long NGX_HTTP_CLOJURE_HEADERSI_DATE_OFFSET ;
	//#endif

	public static int  NGX_HTTP_CLOJURE_HEADERSI_USER_IDX  = 89;
	public static long NGX_HTTP_CLOJURE_HEADERSI_USER_OFFSET ;
	public static int NGX_HTTP_CLOJURE_HEADERSI_PASSWD_IDX  = 90;
	public static long NGX_HTTP_CLOJURE_HEADERSI_PASSWD_OFFSET ;
	public static int NGX_HTTP_CLOJURE_HEADERSI_COOKIE_IDX  = 91;
	public static long NGX_HTTP_CLOJURE_HEADERSI_COOKIE_OFFSET ;
	public static int NGX_HTTP_CLOJURE_HEADERSI_SERVER_IDX  = 92;
	public static long NGX_HTTP_CLOJURE_HEADERSI_SERVER_OFFSET ;
	public static int NGX_HTTP_CLOJURE_HEADERSI_CONTENT_LENGTH_N_IDX = 93;
	public static long NGX_HTTP_CLOJURE_HEADERSI_CONTENT_LENGTH_N_OFFSET ;
	public static int NGX_HTTP_CLOJURE_HEADERSI_KEEP_ALIVE_N_IDX  = 94;
	public static long NGX_HTTP_CLOJURE_HEADERSI_KEEP_ALIVE_N_OFFSET ;
	public static int NGX_HTTP_CLOJURE_HEADERSI_HEADERS_IDX  = 95;
	public static long NGX_HTTP_CLOJURE_HEADERSI_HEADERS_OFFSET ;


	/*index for size of ngx_http_headers_out_t */
	public static int NGX_HTTP_CLOJURE_HEADERSOT_SIZE_IDX = 128;
	public static long NGX_HTTP_CLOJURE_HEADERSOT_SIZE;
	/*field offset index for ngx_http_headers_out_t*/
	public static int NGX_HTTP_CLOJURE_HEADERSO_STATUS_IDX  = 129;
	public static long NGX_HTTP_CLOJURE_HEADERSO_STATUS_OFFSET;
	public static int NGX_HTTP_CLOJURE_HEADERSO_STATUS_LINE_IDX  = 130;
	public static long NGX_HTTP_CLOJURE_HEADERSO_STATUS_LINE_OFFSET;
	public static int NGX_HTTP_CLOJURE_HEADERSO_SERVER_IDX  = 131;
	public static long NGX_HTTP_CLOJURE_HEADERSO_SERVER_OFFSET;
	public static int NGX_HTTP_CLOJURE_HEADERSO_DATE_IDX  = 132;
	public static long NGX_HTTP_CLOJURE_HEADERSO_DATE_OFFSET;
	public static int NGX_HTTP_CLOJURE_HEADERSO_CONTENT_LENGTH_IDX  = 133;
	public static long NGX_HTTP_CLOJURE_HEADERSO_CONTENT_LENGTH_OFFSET;
	public static int NGX_HTTP_CLOJURE_HEADERSO_CONTENT_ENCODING_IDX  = 134;
	public static long NGX_HTTP_CLOJURE_HEADERSO_CONTENT_ENCODING_OFFSET;
	public static int NGX_HTTP_CLOJURE_HEADERSO_LOCATION_IDX  = 135;
	public static long NGX_HTTP_CLOJURE_HEADERSO_LOCATION_OFFSET;
	public static int NGX_HTTP_CLOJURE_HEADERSO_REFRESH_IDX  = 136;
	public static long NGX_HTTP_CLOJURE_HEADERSO_REFRESH_OFFSET;
	public static int NGX_HTTP_CLOJURE_HEADERSO_LAST_MODIFIED_IDX  = 137;
	public static long NGX_HTTP_CLOJURE_HEADERSO_LAST_MODIFIED_OFFSET;
	public static int NGX_HTTP_CLOJURE_HEADERSO_CONTENT_RANGE_IDX  = 138;
	public static long NGX_HTTP_CLOJURE_HEADERSO_CONTENT_RANGE_OFFSET;
	public static int NGX_HTTP_CLOJURE_HEADERSO_ACCEPT_RANGES_IDX  = 139;
	public static long NGX_HTTP_CLOJURE_HEADERSO_ACCEPT_RANGES_OFFSET;
	public static int NGX_HTTP_CLOJURE_HEADERSO_WWW_AUTHENTICATE_IDX  = 140;
	public static long NGX_HTTP_CLOJURE_HEADERSO_WWW_AUTHENTICATE_OFFSET;
	public static int NGX_HTTP_CLOJURE_HEADERSO_EXPIRES_IDX  = 141;
	public static long NGX_HTTP_CLOJURE_HEADERSO_EXPIRES_OFFSET;
	public static int NGX_HTTP_CLOJURE_HEADERSO_ETAG_IDX  = 142;
	public static long NGX_HTTP_CLOJURE_HEADERSO_ETAG_OFFSET;
	public static int NGX_HTTP_CLOJURE_HEADERSO_OVERRIDE_CHARSET_IDX  = 143;
	public static long NGX_HTTP_CLOJURE_HEADERSO_OVERRIDE_CHARSET_OFFSET ;
	public static int NGX_HTTP_CLOJURE_HEADERSO_CONTENT_TYPE_LEN_IDX  = 144;
	public static long NGX_HTTP_CLOJURE_HEADERSO_CONTENT_TYPE_LEN_OFFSET ;
	public static int NGX_HTTP_CLOJURE_HEADERSO_CONTENT_TYPE_IDX  = 145;
	public static long NGX_HTTP_CLOJURE_HEADERSO_CONTENT_TYPE_OFFSET;
	public static int NGX_HTTP_CLOJURE_HEADERSO_CHARSET_IDX  = 146;
	public static long NGX_HTTP_CLOJURE_HEADERSO_CHARSET_OFFSET;
	public static int NGX_HTTP_CLOJURE_HEADERSO_CONTENT_TYPE_LOWCASE_IDX  = 147;
	public static long NGX_HTTP_CLOJURE_HEADERSO_CONTENT_TYPE_LOWCASE_OFFSET;
	public static int NGX_HTTP_CLOJURE_HEADERSO_CONTENT_TYPE_HASH_IDX  = 148;
	public static long NGX_HTTP_CLOJURE_HEADERSO_CONTENT_TYPE_HASH_OFFSET;
	public static int NGX_HTTP_CLOJURE_HEADERSO_CACHE_CONTROL_IDX  = 149;
	public static long NGX_HTTP_CLOJURE_HEADERSO_CACHE_CONTROL_OFFSET;
	public static int NGX_HTTP_CLOJURE_HEADERSO_CONTENT_LENGTH_N_IDX  = 150;
	public static long NGX_HTTP_CLOJURE_HEADERSO_CONTENT_LENGTH_N_OFFSET;
	public static int NGX_HTTP_CLOJURE_HEADERSO_DATE_TIME_IDX  = 151;
	public static long NGX_HTTP_CLOJURE_HEADERSO_DATE_TIME_OFFSET;
	public static int NGX_HTTP_CLOJURE_HEADERSO_LAST_MODIFIED_TIME_IDX = 152;
	public static long NGX_HTTP_CLOJURE_HEADERSO_LAST_MODIFIED_TIME_OFFSET;
	public static int NGX_HTTP_CLOJURE_HEADERSO_HEADERS_IDX  = 153;
	public static long NGX_HTTP_CLOJURE_HEADERSO_HEADERS_OFFSET;

	public static int NGX_WORKER_PROCESSORS_NUM_ID = 250;
	public static long NGX_WORKER_PROCESSORS_NUM;
	
//	public static int NGINX_CLOJURE_MODULE_CTX_PHRASE_ID = 251;
//	public static long NGINX_CLOJURE_MODULE_CTX_PHRASE_ID_OFFSET;
	
	public static int NGINX_CLOJURE_RT_WORKERS_ID = 252;
	public static long NGINX_CLOJURE_RT_WORKERS;
	public static int NGINX_VER_ID = 253;
	public static long NGINX_VER;
	public static int NGINX_CLOJURE_VER_ID = 254;
	public static long NGINX_CLOJURE_VER;
	public static String NGINX_CLOJURE_FULL_VER;

	//these two will be updated to NGX_HTTP_CLOJURE_BUFFER_SIZE
	public static int NGINX_CLOJURE_CORE_CLIENT_HEADER_MAX_SIZE = 1024 * 8;
	
	public static int NGINX_CLOJURE_CORE_CLIENT_HEADER_MAX_LINE_SIZE = NGINX_CLOJURE_CORE_CLIENT_HEADER_MAX_SIZE/2;
	
	
	public static int NGX_HTTP_CLOJURE_MEM_IDX_END = 255;
	
	//nginx clojure java runtime required the lowest version of nginx-clojure c module
	public  static long NGINX_CLOJURE_RT_REQUIRED_LVER = 5002;
	public  static long NGINX_CLOJURE_RT_VER = 5002;
	
	//ngx_core.h
	public final static int  NGX_OK       =   0;
	public final static int  NGX_ERROR    =  -1;
	public final static int  NGX_AGAIN    =  -2;
	public final static int  NGX_BUSY     =  -3;
	public final static int  NGX_DONE     =  -4;
	public final static int  NGX_DECLINED =  -5;
	public final static int  NGX_ABORT    =  -6;
	
    public final static int NGX_HTTP_POST_READ_PHASE = 0;
    public final static int NGX_HTTP_SERVER_REWRITE_PHASE = 1;
    public final static int NGX_HTTP_FIND_CONFIG_PHASE =2;
    public final static int NGX_HTTP_REWRITE_PHASE = 3;
    public final static int NGX_HTTP_POST_REWRITE_PHASE = 4;
    public final static int NGX_HTTP_PREACCESS_PHASE = 5;
    public final static int NGX_HTTP_ACCESS_PHASE = 6;
    public final static int NGX_HTTP_POST_ACCESS_PHASE = 7;
    public final static int NGX_HTTP_TRY_FILES_PHASE = 8;
    public final static int NGX_HTTP_CONTENT_PHASE = 9;
    public final static int NGX_HTTP_LOG_PHASE = 10;
    
    //fake phase for filter
    public final static int NGX_HTTP_INIT_PROCESS_PHASE= 17;
    public final static int NGX_HTTP_HEADER_FILTER_PHASE= 18;
    public final static int NGX_HTTP_BODY_FILTER_PHASE= 19;
    public final static int NGX_HTTP_EXIT_PROCESS_PHASE= 20;
    
    /*fake chain for header filter*/
    public final static int NGX_HTTP_HEADER_FILTER = -1;
    public final static int NGX_HTTP_HEADER_FILTER_IN_THREADPOOL = -2;
    
	
	//ngx_http_request.h
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
	

	public static int NGX_HTTP_CONTINUE = 100;
	public static int NGX_HTTP_SWITCHING_PROTOCOLS = 101;
	public static int NGX_HTTP_PROCESSING = 102;

	public static int NGX_HTTP_OK = 200;
	public static int NGX_HTTP_CREATED = 201;
	public static int NGX_HTTP_ACCEPTED = 202;
	public static int NGX_HTTP_NO_CONTENT = 204;
	public static int NGX_HTTP_PARTIAL_CONTENT = 206;

	public static int NGX_HTTP_SPECIAL_RESPONSE = 300;
	public static int NGX_HTTP_MOVED_PERMANENTLY = 301;
	public static int NGX_HTTP_MOVED_TEMPORARILY = 302;
	public static int NGX_HTTP_SEE_OTHER = 303;
	public static int NGX_HTTP_NOT_MODIFIED = 304;
	public static int NGX_HTTP_TEMPORARY_REDIRECT = 307;

	public static int NGX_HTTP_BAD_REQUEST = 400;
	public static int NGX_HTTP_UNAUTHORIZED = 401;
	public static int NGX_HTTP_FORBIDDEN = 403;
	public static int NGX_HTTP_NOT_FOUND = 404;
	public static int NGX_HTTP_NOT_ALLOWED = 405;
	public static int NGX_HTTP_REQUEST_TIME_OUT = 408;
	public static int NGX_HTTP_CONFLICT = 409;
	public static int NGX_HTTP_LENGTH_REQUIRED = 411;
	public static int NGX_HTTP_PRECONDITION_FAILED = 412;
	public static int NGX_HTTP_REQUEST_ENTITY_TOO_LARGE = 413;
	public static int NGX_HTTP_REQUEST_URI_TOO_LARGE = 414;
	public static int NGX_HTTP_UNSUPPORTED_MEDIA_TYPE = 415;
	public static int NGX_HTTP_RANGE_NOT_SATISFIABLE = 416;

	/* Nginx own HTTP codes */
	/* The special code to close connection without any response */
	public static int NGX_HTTP_CLOSE = 444;

	public static int NGX_HTTP_NGINX_CODES = 494;

	public static int NGX_HTTP_REQUEST_HEADER_TOO_LARGE = 494;

	public static int NGX_HTTPS_CERT_ERROR = 495;
	public static int NGX_HTTPS_NO_CERT = 496;

	/*
	 * We use the special code for the plain HTTP requests that are sent to
	 * HTTPS port to distinguish it from 4XX in an error page redirection
	 */
	public static int NGX_HTTP_TO_HTTPS = 497;

	/* 498 is the canceled code for the requests with invalid host name */

	/*
	 * HTTP does not define the code for the case when a client closed the
	 * connection while we are processing its request so we introduce own code
	 * to log such situation when a client has closed the connection before we
	 * even try to send the HTTP header to it
	 */
	public static int NGX_HTTP_CLIENT_CLOSED_REQUEST = 499;

	public static int NGX_HTTP_INTERNAL_SERVER_ERROR = 500;
	public static int NGX_HTTP_NOT_IMPLEMENTED = 501;
	public static int NGX_HTTP_BAD_GATEWAY = 502;
	public static int NGX_HTTP_SERVICE_UNAVAILABLE = 503;
	public static int NGX_HTTP_GATEWAY_TIME_OUT = 504;
	public static int NGX_HTTP_INSUFFICIENT_STORAGE = 507;
	
	
	public static final int  NGX_HTTP_CLOJURE_CHANNEL_EVENT_CLOSE = 0;
	public static final int  NGX_HTTP_CLOJURE_CHANNEL_EVENT_CONNECT = 1;
	public static final int  NGX_HTTP_CLOJURE_CHANNEL_EVENT_READ = 2;
	public static final int  NGX_HTTP_CLOJURE_CHANNEL_EVENT_WRITE = 4;
	public static final int  NGX_HTTP_CLOJURE_CHANNEL_EVENT_MSGREMAIN = 8;
	public static final int  NGX_HTTP_CLOJURE_CHANNEL_EVENT_MSGTEXT = 16;
	public static final int  NGX_HTTP_CLOJURE_CHANNEL_EVENT_MSGBIN = 32;
	public static final int  NGX_HTTP_CLOJURE_CHANNEL_EVENT_MSGCLOSE = 64;
	public static final int  NGX_HTTP_CLOJURE_CHANNEL_EVENT_MSGFIRST = 128;
	
	public static final int NGX_HTTP_CLOJURE_EVENT_HANDLER_FLAG_READ = 1;
	public static final int NGX_HTTP_CLOJURE_EVENT_HANDLER_FLAG_WRITE = 2;
	public static final int NGX_HTTP_CLOJURE_EVENT_HANDLER_FLAG_NOKEEPALIVE = 4;
	

//	public static final String HEADERS = RT.keyword(null, "headers");
//	public static final String BODY = RT.keyword(null, "body");
	
	//these consts are initialized by MemoryUtil.initMemIndex
	public static RequestVarFetcher SERVER_PORT_FETCHER;// = new RequestKnownNameVarFetcher("server_port");
	
	public static RequestVarFetcher SERVER_NAME_FETCHER;// = new RequestKnownNameVarFetcher("server_name");
	
	public static RequestVarFetcher REMOTE_ADDR_FETCHER;// = new RequestKnownNameVarFetcher("remote_addr");
	
	public static RequestVarFetcher URI_FETCHER;// = new RequestKnownOffsetVarFetcher(NGX_HTTP_CLOJURE_REQ_URI_OFFSET);
	
	public static RequestVarFetcher QUERY_STRING_FETCHER;// = new RequestKnownOffsetVarFetcher(NGX_HTTP_CLOJURE_REQ_ARGS_OFFSET);
	
	public static RequestVarFetcher SCHEME_FETCHER;//= new RequestKnownNameVarFetcher("scheme");

	public static RequestVarFetcher REQUEST_METHOD_FETCHER;// = new RequestMethodFetcher();
	
	public static RequestVarFetcher CONTENT_TYPE_FETCHER;// = new RequestKnownHeaderFetcher("content-type");
	
	public static RequestVarFetcher CHARACTER_ENCODING_FETCHER;// = new RequestCharacterEncodingFetcher();
	
//	public static RequestVarFetcher HEADER_FETCHER;// = new RequestHeaderFetcher();
	
	public static RequestVarFetcher BODY_FETCHER;// = new RequestBodyFetcher();
	
	
	public static final int MODE_DEFAULT = 0;
	public static final int MODE_THREAD = 1;
	public static final int MODE_COROUTINE = 2;
	
	public static final String REQUEST_FORECE_PREFETCH_ALL_PROPERTIES = "fore-prefetch-all-properties";

}
