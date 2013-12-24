/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure;

import static nginx.clojure.NginxClojureRT.fetchNGXInt;
import static nginx.clojure.NginxClojureRT.fetchNGXString;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

import clojure.lang.Keyword;
import clojure.lang.RT;

public class Constants {

	/**
	 * Ring Spec (1.1) Keywords : https://github.com/ring-clojure/ring/blob/master/SPEC
	 */
	public static final Keyword SERVER_PORT = RT.keyword(null, "server-port");
	public static final Keyword SERVER_NAME = RT.keyword(null, "server-name");
	public static final Keyword REMOTE_ADDR = RT.keyword(null, "remote-addr");
	public static final Keyword URI = RT.keyword(null, "uri");
	public static final Keyword QUERY_STRING = RT.keyword(null, "query-string");
	public static final Keyword SCHEME = RT.keyword(null, "scheme");
	public static final Keyword REQUEST_METHOD = RT.keyword(null, "request-method");
	public static final Keyword CONTENT_TYPE = RT.keyword(null, "content-type");
	public static final Keyword CHARACTER_ENCODING = RT.keyword(null, "character-encoding");
	public static final Keyword SSL_CLIENT_CERT = RT.keyword(null, "ssl-client-cert");
	public static final Keyword HEADERS = RT.keyword(null, "headers");
	public static final Keyword BODY = RT.keyword(null, "body");
	
	public static final Keyword UNKNOWN = RT.keyword(null, "UNKNOWN");
	public static final Keyword GET = RT.keyword(null, "get");
	public static final Keyword HEAD = RT.keyword(null, "head");
	public static final Keyword POST = RT.keyword(null, "post");
	public static final Keyword PUT = RT.keyword(null, "put");
	public static final Keyword DELETE = RT.keyword(null, "delete");
	public static final Keyword MKCOL = RT.keyword(null, "mkcol");
	public static final Keyword COPY = RT.keyword(null, "copy");
	public static final Keyword MOVE = RT.keyword(null, "move");
	public static final Keyword OPTIONS = RT.keyword(null, "options");
	public static final Keyword PROPFIND = RT.keyword(null, "propfind");
	public static final Keyword PROPPATCH = RT.keyword(null, "proppatch");
	public static final Keyword LOCK = RT.keyword(null, "lock");
	public static final Keyword UNLOCK = RT.keyword(null, "unlock");
	public static final Keyword PATCH = RT.keyword(null, "patch");
	public static final Keyword TRACE = RT.keyword(null, "trace");
	
	
	
	public static final Keyword[] HTTP_METHODS = { UNKNOWN, GET, HEAD,
			POST, PUT, DELETE, MKCOL, COPY, MOVE, OPTIONS, PROPFIND,
			PROPPATCH, LOCK, UNLOCK, PATCH, TRACE };
	
	public static Map<String, Long> KNOWN_REQ_HEADERS = new HashMap<String, Long>();
	
	public static Map<String, ResponseHeaderPusher> KNOWN_RESP_HEADERS = new HashMap<String, ResponseHeaderPusher>();
	
	public static Map<String, Long> CORE_VARS = new HashMap<String, Long>();
	
	public static final Keyword STATUS = RT.keyword(null, "status");
//	public static final Keyword BODY = RT.keyword(null, "body");
//	public static final Keyword HEADERS = RT.keyword(null, "headers");
	public static final String DEFAULT_ENCODING_STR = "utf-8";
	public static final Charset DEFAULT_ENCODING = Charset.forName(DEFAULT_ENCODING_STR);
	
	
	public static int BYTE_ARRAY_OFFSET;
	
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
	public static int NGX_HTTP_CLOJURE_CORE_VARIABLES_LEN_IDX  = 21;
	public static long NGX_HTTP_CLOJURE_CORE_VARIABLES_LEN;
	
	
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

	public static int NGINX_VER_ID = 253;
	public static long NGINX_VER;
	public static int NGINX_CLOJURE_VER_ID = 254;
	public static long NGINX_CLOJURE_VER;
	public static String NGINX_CLOJURE_FULL_VER;

	
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
	

//	public static final Keyword HEADERS = RT.keyword(null, "headers");
//	public static final Keyword BODY = RT.keyword(null, "body");
	
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
	
	public static RequestVarFetcher HEADER_FETCHER;// = new RequestHeaderFetcher();
	
	public static RequestVarFetcher BODY_FETCHER;// = new RequestBodyFetcher();
	
	
	public static ResponseTableEltHeaderPusher SERVER_PUSHER;
	
}
