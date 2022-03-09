/*
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 */

#ifndef NGX_HTTP_CLOJURE_MEM_H_
#define NGX_HTTP_CLOJURE_MEM_H_

#include <nginx.h>
#include <ngx_http.h>
#if (NGX_HAVE_SHA1 || nginx_version >= 1011002)
#include <ngx_sha1.h>
#endif

#if (NGX_ZLIB)
#include <zlib.h>
#endif


#if defined(_WIN32) || defined(WIN32)

#pragma warning (disable : 4305)
#pragma warning (disable : 4244)
#pragma warning (disable : 4152)
#pragma warning (disable : 4130)

#ifndef PRIu64
#define PRIu64 "I64u"
#endif

typedef unsigned __int32 uint32_t;
typedef unsigned __int8 uint8_t;
typedef unsigned __int64 uint64_t;

#define JVM_CP_SEP ';'
#define JVM_CP_SEP_S ";"

#else
#define __STDC_FORMAT_MACROS
#include <inttypes.h>
#define JVM_CP_SEP ':'
#define JVM_CP_SEP_S ":"
#endif

#define nginx_clojure_ver  5003 /*0.5.3*/

/*the least jar version required*/
#define nginx_clojure_required_rt_lver 5002

#define NGINX_CLOJURE_VER_NUM_STR "0.5.3"

#define NGINX_CLOJURE_VER "nginx-clojure/" NGINX_CLOJURE_VER_NUM_STR

/*fake phase for filter*/
#define NGX_HTTP_INIT_PROCESS_PHASE  17
#define NGX_HTTP_HEADER_FILTER_PHASE  18
#define NGX_HTTP_BODY_FILTER_PHASE  19
#define NGX_HTTP_EXIT_PROCESS_PHASE  20

/*fake chain for header filter*/
#define NGX_HTTP_HEADER_FILTER -1
#define NGX_HTTP_HEADER_FILTER_IN_THREADPOOL -2

typedef struct {
	ngx_str_t name;
	ngx_http_header_handler_pt handler;
	ngx_uint_t offset;
} ngx_http_clojure_header_holder_t;

typedef struct {
	ngx_int_t max_balanced_tcp_connections;
	ngx_array_t *jvm_options;
	ngx_array_t *jvm_vars;
	ngx_array_t *jvm_cp;
	ngx_array_t *shared_maps;
	ngx_str_t jvm_path;
	ngx_int_t jvm_workers;
	ngx_flag_t jvm_cp_check;
	/*either of -Xbootclasspath, -Djava.ext.dirs, -Djava.class.path or jvm_classpath is set*/
	unsigned jvm_cp_is_set : 1;
	unsigned jvm_disable_all : 1;
	unsigned enable_init_handler : 1;
	unsigned enable_exit_handler : 1;
	unsigned enable_content_handler :1;
	unsigned enable_rewrite_handler :1;
	unsigned enable_header_filter :1;
	unsigned enable_body_filter :1;
	unsigned enable_access_handler : 1;
	unsigned enable_log_handler : 1;
	ngx_str_t jvm_handler_type;
	ngx_str_t jvm_init_handler_code;
	ngx_int_t jvm_init_handler_id;
	ngx_str_t jvm_init_handler_name;
	ngx_str_t jvm_exit_handler_code;
	ngx_int_t jvm_exit_handler_id;
	ngx_str_t jvm_exit_handler_name;
	ngx_hash_t headers_out_holder_hash;
} ngx_http_clojure_main_conf_t;

typedef struct {
	unsigned enable_content_handler :1;
	unsigned enable_rewrite_handler :1;
	unsigned enable_header_filter :1;
	unsigned enable_body_filter :1;
	unsigned enable_access_handler : 1;
#define NGX_HTTP_CLOJURE_ALWATS_READ_BODY_UNSET 0
#define NGX_HTTP_CLOJURE_BEFORE_REWRITE_HANDLER 1
#define NGX_HTTP_CLOJURE_BEFORE_ACCESS_HANDLER 2
#define NGX_HTTP_CLOJURE_BEFORE_CONTENT_HANDLER 3
#define NGX_HTTP_CLOJURE_BEFORE_NONE 4
	unsigned always_read_body : 3;
	unsigned enable_log_handler : 1;
	ngx_flag_t auto_upgrade_ws;
	ngx_flag_t handlers_lazy_init;
	ngx_str_t content_handler_type;
	ngx_str_t content_handler_code;
	ngx_int_t content_handler_id;
	ngx_str_t content_handler_name;
	ngx_str_t rewrite_handler_type;
	ngx_str_t rewrite_handler_code;
	ngx_int_t rewrite_handler_id;
	ngx_str_t rewrite_handler_name;
	ngx_str_t header_filter_type;
	ngx_str_t header_filter_code;
	ngx_int_t header_filter_id;
	ngx_str_t header_filter_name;
	ngx_str_t body_filter_type;
	ngx_str_t body_filter_code;
	ngx_int_t body_filter_id;
	ngx_str_t body_filter_name;
	ngx_str_t access_handler_type;
	ngx_str_t access_handler_code;
	ngx_int_t access_handler_id;
	ngx_str_t access_handler_name;
  ngx_str_t log_handler_type;
  ngx_str_t log_handler_code;
  ngx_int_t log_handler_id;
  ngx_str_t log_handler_name;
	ngx_array_t *content_handler_properties;
	ngx_array_t *rewrite_handler_properties;
	ngx_array_t *access_handler_properties;
	ngx_array_t *header_filter_properties;
	ngx_array_t *body_filter_properties;
	ngx_array_t *log_handler_properties;
	size_t write_page_size;
} ngx_http_clojure_loc_conf_t;

typedef struct ngx_http_clojure_listener_node_s {
	void *listener;
	void *data;
	struct ngx_http_clojure_listener_node_s *next;
} ngx_http_clojure_listener_node_t;

typedef struct {
/*for write*/
	unsigned ffm : 1; /*first write frame of a message*/
/*for read*/
	unsigned fin : 1; /*data frame FIN tag*/
#define NGX_HTTP_CLOJURE_WEBSOCKET_OPCODE_CONT  0x0
#define NGX_HTTP_CLOJURE_WEBSOCKET_OPCODE_TEXT  0x1
#define NGX_HTTP_CLOJURE_WEBSOCKET_OPCODE_BIN   0x2
#define NGX_HTTP_CLOJURE_WEBSOCKET_OPCODE_CLOSE 0x8
#define NGX_HTTP_CLOJURE_WEBSOCKET_OPCODE_PING  0x9
#define NGX_HTTP_CLOJURE_WEBSOCKET_OPCODE_PONG  0xa
	unsigned opcode : 4;
	unsigned ltxt : 1; /*the last data frame is a text frame*/
	unsigned cont : 1; /*original opcode is 0, viz. continuation frame*/
	unsigned mask : 1;
	unsigned mpos : 2; /*mask position in mcode*/
#define NGX_HTTP_CLOJURE_WEBSOCKET_PARSE_START 0
#define NGX_HTTP_CLOJURE_WEBSOCKET_PARSE_LEN 1
#define NGX_HTTP_CLOJURE_WEBSOCKET_PARSE_MASK 2
#define NGX_HTTP_CLOJURE_WEBSOCKET_PARSE_DATA 3
	unsigned pstate : 2; /*start/len/mask/data*/
	unsigned left : 2; /*left bytes length from last decoding*/

#if (NGX_ZLIB)
/*for premessage-deflate --websocket compression extension*/
	unsigned premsg_deflate   : 1;
	unsigned compressed       : 1;
	unsigned in_no_ctx_takeover : 1;
	unsigned out_no_ctx_takeover : 1;
	unsigned part_written : 1;
	z_stream zin;
	z_stream zout;
/*end for premessage-deflate*/
#endif

	u_char *  left_pos; /*left bytes pointer from last decoding*/
	char mcode[4]; /*mask code*/
	uint64_t len;
	ngx_chain_t *rchain; /*buffer for read*/

} ngx_http_clojure_websocket_ctx_t;

typedef struct {
	ngx_int_t phase;
	ngx_int_t phase_rc;
	ngx_int_t handled_couter;
	ngx_chain_t *free;
	ngx_chain_t *busy;
	/*these two members only used by hijack send & read, default is 0*/
	unsigned last_buf_meeted : 1;
	unsigned ignore_filters : 1;
	unsigned async_body_read : 1;
	unsigned client_body_done : 1;
	unsigned wait_for_header_filter : 1;
	unsigned pending_body_filter : 1;
	unsigned ignore_next_response : 1;
	unsigned hijacked_or_async : 1;
	unsigned set_reload_delay : 1;
#define NGX_HTTP_CLOJURE_EVENT_HANDLER_FLAG_READ 1
#define NGX_HTTP_CLOJURE_EVENT_HANDLER_FLAG_WRITE 2
#define NGX_HTTP_CLOJURE_EVENT_HANDLER_FLAG_NOKEEPALIVE 4
	/*1 READ, 2 WRITE, 4 NOKEEPALIVE*/
	unsigned event_handler_flag : 3;
	ngx_http_clojure_websocket_ctx_t *wsctx;
	ngx_chain_t *wchain; /*buffer for write*/
	/*for filter under thread pool mode or coroutine mode*/
	ngx_chain_t *pending;
	ngx_http_clojure_listener_node_t *listeners;
	ngx_http_request_t *r;
} ngx_http_clojure_module_ctx_t;

#define ngx_http_clojure_init_ctx(ctx, p, r) \
		ctx->handled_couter = 1; \
		ctx->phase = p; \
		ctx->last_buf_meeted = 0; \
		ctx->wchain = ctx->busy = ctx->free = ctx->pending = NULL; \
		ctx->ignore_filters = 0; \
		ctx->client_body_done = 0; \
		ctx->async_body_read = 0 ; \
		ctx->wait_for_header_filter = 0 ;\
		ctx->pending_body_filter = 0 ; \
		ctx->ignore_next_response = 0; \
		ctx->set_reload_delay = 0; \
		ctx->hijacked_or_async = 0; \
		ctx->event_handler_flag = 0; \
		ctx->wsctx = 0; \
		ctx->listeners = 0; \
		ctx->r = r;



#define NGX_HTTP_CLOJURE_GET_HEADER_FLAG_HEADERS_OUT 1
#define NGX_HTTP_CLOJURE_GET_HEADER_FLAG_MERGE_KEY 2

#define NGX_HTTP_CLOJURE_MEM_IDX_START 0

/*index for size of ngx_uint_t */
#define NGX_HTTP_CLOJURE_UINT_SIZE_IDX 0
#define NGX_HTTP_CLOJURE_UINT_SIZE sizeof(ngx_uint_t)

#define NGX_HTTP_CLOJURE_PTR_SIZE_IDX 1
#define NGX_HTTP_CLOJURE_PTR_SIZE sizeof(void *)

#define NGX_HTTP_CLOJURE_SIZET_SIZE_IDX 2
#define NGX_HTTP_CLOJURE_SIZET_SIZE sizeof(size_t)

#define NGX_HTTP_CLOJURE_OFFT_SIZE_IDX 3
#define NGX_HTTP_CLOJURE_OFFT_SIZE sizeof(off_t)

#define NGX_HTTP_CLOJURE_BUFFER_SIZE_IDX 4


/*index for size of ngx_str_t */
#define NGX_HTTP_CLOJURE_STRT_SIZE_IDX 8
#define NGX_HTTP_CLOJURE_STRT_SIZE sizeof(ngx_str_t)
/*field offset index for ngx_str_t*/
#define NGX_HTTP_CLOJURE_STR_LEN_IDX 9
#define NGX_HTTP_CLOJURE_STR_LEN_OFFSET offsetof(ngx_str_t,len)
#define NGX_HTTP_CLOJURE_STR_DATA_IDX 10
#define NGX_HTTP_CLOJURE_STR_DATA_OFFSET offsetof(ngx_str_t,data)


/*index for size of ngx_table_elt_t */
#define NGX_HTTP_CLOJURE_TELT_SIZE_IDX 11
#define NGX_HTTP_CLOJURE_TELT_SIZE sizeof(ngx_table_elt_t)
/*field offset index for ngx_table_elt_t*/
#define NGX_HTTP_CLOJURE_TEL_HASH_IDX 12
#define NGX_HTTP_CLOJURE_TEL_HASH_OFFSET offsetof(ngx_table_elt_t,hash)
#define NGX_HTTP_CLOJURE_TEL_KEY_IDX 13
#define NGX_HTTP_CLOJURE_TEL_KEY_OFFSET offsetof(ngx_table_elt_t,key)
#define NGX_HTTP_CLOJURE_TEL_VALUE_IDX 14
#define NGX_HTTP_CLOJURE_TEL_VALUE_OFFSET offsetof(ngx_table_elt_t,value)
#define NGX_HTTP_CLOJURE_TEL_LOWCASE_KEY_IDX 15
#define NGX_HTTP_CLOJURE_TEL_LOWCASE_KEY_OFFSET offsetof(ngx_table_elt_t,lowcase_key)

#define NGX_HTTP_CLOJURE_CHAINT_SIZE_IDX 16
#define NGX_HTTP_CLOJURE_CHAINT_SIZE sizeof(ngx_chain_t)
#define NGX_HTTP_CLOJURE_CHAIN_BUF_IDX  17
#define NGX_HTTP_CLOJURE_CHAIN_BUF_OFFSET  offsetof(ngx_chain_t, buf)
#define NGX_HTTP_CLOJURE_CHAIN_NEXT_IDX  18
#define NGX_HTTP_CLOJURE_CHAIN_NEXT_OFFSET  offsetof(ngx_chain_t, next)


extern ngx_cycle_t *ngx_http_clojure_global_cycle;

#define NGX_HTTP_CLOJURE_VARIABLET_SIZE_IDX 19
#define NGX_HTTP_CLOJURE_VARIABLET_SIZE sizeof(ngx_http_variable_t)
#define NGX_HTTP_CLOJURE_CORE_VARIABLES_ADDR_IDX 20
#define NGX_HTTP_CLOJURE_CORE_VARIABLES_ADDR (uintptr_t)ngx_http_clojure_core_variables_names
#define NGX_HTTP_CLOJURE_HEADERS_NAMES_ADDR_IDX 21
#define NGX_HTTP_CLOJURE_HEADERS_NAMES_ADDR (uintptr_t)ngx_http_clojure_headers_names



#define NGX_HTTP_CLOJURE_ARRAYT_SIZE_IDX 22
#define NGX_HTTP_CLOJURE_ARRAYT_SIZE sizeof(ngx_array_t)
#define NGX_HTTP_CLOJURE_ARRAY_ELTS_IDX 23
#define NGX_HTTP_CLOJURE_ARRAY_ELTS_OFFSET offsetof(ngx_array_t, elts)
#define NGX_HTTP_CLOJURE_ARRAY_NELTS_IDX 24
#define NGX_HTTP_CLOJURE_ARRAY_NELTS_OFFSET offsetof(ngx_array_t, nelts)
#define NGX_HTTP_CLOJURE_ARRAY_SIZE_IDX 25
#define NGX_HTTP_CLOJURE_ARRAY_SIZE_OFFSET offsetof(ngx_array_t, size)
#define NGX_HTTP_CLOJURE_ARRAY_NALLOC_IDX 26
#define NGX_HTTP_CLOJURE_ARRAY_NALLOC_OFFSET offsetof(ngx_array_t, nalloc)
#define NGX_HTTP_CLOJURE_ARRAY_POOL_IDX 27
#define NGX_HTTP_CLOJURE_ARRAY_POOL_OFFSET offsetof(ngx_array_t, pool)

#define NGX_HTTP_CLOJURE_KEYVALT_SIZE_IDX 28
#define NGX_HTTP_CLOJURE_KEYVALT_SIZE sizeof(ngx_keyval_t)
#define NGX_HTTP_CLOJURE_KEYVALT_KEY_IDX 29
#define NGX_HTTP_CLOJURE_KEYVALT_KEY_OFFSET offsetof(ngx_keyval_t, key)
#define NGX_HTTP_CLOJURE_KEYVALT_VALUE_IDX 30
#define NGX_HTTP_CLOJURE_KEYVALT_VALUE_OFFSET offsetof(ngx_keyval_t, value)

/*index for size of ngx_http_request_t */
#define NGX_HTTP_CLOJURE_REQT_SIZE_IDX 32
#define NGX_HTTP_CLOJURE_REQT_SIZE sizeof(ngx_http_request_t)
/*field offset index for ngx_http_request_t*/
#define NGX_HTTP_CLOJURE_REQ_METHOD_IDX  33
#define NGX_HTTP_CLOJURE_REQ_METHOD_OFFSET offsetof(ngx_http_request_t, method)
#define NGX_HTTP_CLOJURE_REQ_URI_IDX  34
#define NGX_HTTP_CLOJURE_REQ_URI_OFFSET offsetof(ngx_http_request_t, uri)
#define NGX_HTTP_CLOJURE_REQ_ARGS_IDX  35
#define NGX_HTTP_CLOJURE_REQ_ARGS_OFFSET offsetof(ngx_http_request_t, args)
#define NGX_HTTP_CLOJURE_REQ_HEADERS_IN_IDX  36
#define NGX_HTTP_CLOJURE_REQ_HEADERS_IN_OFFSET offsetof(ngx_http_request_t, headers_in)
#define NGX_HTTP_CLOJURE_REQ_POOL_IDX 37
#define NGX_HTTP_CLOJURE_REQ_POOL_OFFSET offsetof(ngx_http_request_t, pool)
#define NGX_HTTP_CLOJURE_REQ_HEADERS_OUT_IDX  38
#define NGX_HTTP_CLOJURE_REQ_HEADERS_OUT_OFFSET offsetof(ngx_http_request_t, headers_out)
#define NGX_HTTP_CLOJURE_REQ_METHOD_NAME_IDX 39
#define NGX_HTTP_CLOJURE_REQ_METHOD_NAME_OFFSET offsetof(ngx_http_request_t, method_name)


#define NGX_HTTP_CLOJURE_MIME_TYPES_ADDR_IDX 63
#define NGX_HTTP_CLOJURE_MIME_TYPES_ADDR  (uintptr_t)ngx_http_clojure_mime_types

/*index for size of ngx_http_headers_in_t */
#define NGX_HTTP_CLOJURE_HEADERSIT_SIZE_IDX 64
#define NGX_HTTP_CLOJURE_HEADERSIT_SIZE sizeof(ngx_http_headers_in_t)
/*field offset index for ngx_http_headers_in_t*/
#define NGX_HTTP_CLOJURE_HEADERSI_HOST_IDX  65
#define NGX_HTTP_CLOJURE_HEADERSI_HOST_OFFSET offsetof(ngx_http_headers_in_t, host)
#define NGX_HTTP_CLOJURE_HEADERSI_CONNECTION_IDX  66
#define NGX_HTTP_CLOJURE_HEADERSI_CONNECTION_OFFSET offsetof(ngx_http_headers_in_t, connection)
#define NGX_HTTP_CLOJURE_HEADERSI_IF_MODIFIED_SINCE_IDX  67
#define NGX_HTTP_CLOJURE_HEADERSI_IF_MODIFIED_SINCE_OFFSET offsetof(ngx_http_headers_in_t, if_modified_since)
#define NGX_HTTP_CLOJURE_HEADERSI_IF_UNMODIFIED_SINCE_IDX  68
#define NGX_HTTP_CLOJURE_HEADERSI_IF_UNMODIFIED_SINCE_OFFSET offsetof(ngx_http_headers_in_t, if_unmodified_since)
#define NGX_HTTP_CLOJURE_HEADERSI_USER_AGENT_IDX  69
#define NGX_HTTP_CLOJURE_HEADERSI_USER_AGENT_OFFSET offsetof(ngx_http_headers_in_t, user_agent)
#define NGX_HTTP_CLOJURE_HEADERSI_REFERER_IDX  70
#define NGX_HTTP_CLOJURE_HEADERSI_REFERER_OFFSET offsetof(ngx_http_headers_in_t, referer)
#define NGX_HTTP_CLOJURE_HEADERSI_CONTENT_LENGTH_IDX  71
#define NGX_HTTP_CLOJURE_HEADERSI_CONTENT_LENGTH_OFFSET offsetof(ngx_http_headers_in_t, content_length)
#define NGX_HTTP_CLOJURE_HEADERSI_CONTENT_TYPE_IDX  72
#define NGX_HTTP_CLOJURE_HEADERSI_CONTENT_TYPE_OFFSET offsetof(ngx_http_headers_in_t, content_type)
#define NGX_HTTP_CLOJURE_HEADERSI_RANGE_IDX  73
#define NGX_HTTP_CLOJURE_HEADERSI_RANGE_OFFSET offsetof(ngx_http_headers_in_t, range)
#define NGX_HTTP_CLOJURE_HEADERSI_IF_RANGE_IDX  74
#define NGX_HTTP_CLOJURE_HEADERSI_IF_RANGE_OFFSET offsetof(ngx_http_headers_in_t, if_range)
#define NGX_HTTP_CLOJURE_HEADERSI_TRANSFER_ENCODING_IDX  75
#define NGX_HTTP_CLOJURE_HEADERSI_TRANSFER_ENCODING_OFFSET offsetof(ngx_http_headers_in_t, transfer_encoding)
#define NGX_HTTP_CLOJURE_HEADERSI_EXPECT_IDX  76
#define NGX_HTTP_CLOJURE_HEADERSI_EXPECT_OFFSET offsetof(ngx_http_headers_in_t, expect)

#if (NGX_HTTP_GZIP)
#define NGX_HTTP_CLOJURE_HEADERSI_ACCEPT_ENCODING_IDX  77
#define NGX_HTTP_CLOJURE_HEADERSI_ACCEPT_ENCODING_OFFSET offsetof(ngx_http_headers_in_t, accept_encoding)
#define NGX_HTTP_CLOJURE_HEADERSI_VIA_IDX  78
#define NGX_HTTP_CLOJURE_HEADERSI_VIA_OFFSET offsetof(ngx_http_headers_in_t, via)
#endif

#define NGX_HTTP_CLOJURE_HEADERSI_AUTHORIZATION_IDX  79
#define NGX_HTTP_CLOJURE_HEADERSI_AUTHORIZATION_OFFSET offsetof(ngx_http_headers_in_t, authorization)
#define NGX_HTTP_CLOJURE_HEADERSI_KEEP_ALIVE_IDX  80
#define NGX_HTTP_CLOJURE_HEADERSI_KEEP_ALIVE_OFFSET offsetof(ngx_http_headers_in_t, keep_alive)

#if (NGX_HTTP_X_FORWARDED_FOR)
#define NGX_HTTP_CLOJURE_HEADERSI_X_FORWARDED_FOR_IDX  81
#define NGX_HTTP_CLOJURE_HEADERSI_X_FORWARDED_FOR_OFFSET offsetof(ngx_http_headers_in_t, x_forwarded_for)
#endif

#if (NGX_HTTP_REALIP)
#define NGX_HTTP_CLOJURE_HEADERSI_X_REAL_IP_IDX  82
#define NGX_HTTP_CLOJURE_HEADERSI_X_REAL_IP_OFFSET offsetof(ngx_http_headers_in_t, x_real_ip)
#endif

#if (NGX_HTTP_HEADERS)
#define NGX_HTTP_CLOJURE_HEADERSI_ACCEPT_IDX  83
#define NGX_HTTP_CLOJURE_HEADERSI_ACCEPT_OFFSET offsetof(ngx_http_headers_in_t, accept)
#define NGX_HTTP_CLOJURE_HEADERSI_ACCEPT_LANGUAGE_IDX  84
#define NGX_HTTP_CLOJURE_HEADERSI_ACCEPT_LANGUAGE_OFFSET offsetof(ngx_http_headers_in_t, accept_language)
#endif

#if (NGX_HTTP_DAV)
#define NGX_HTTP_CLOJURE_HEADERSI_DEPTH_IDX  85
#define NGX_HTTP_CLOJURE_HEADERSI_DEPTH_OFFSET offsetof(ngx_http_headers_in_t, depth)
#define NGX_HTTP_CLOJURE_HEADERSI_DESTINATION_IDX  86
#define NGX_HTTP_CLOJURE_HEADERSI_DESTINATION_OFFSET offsetof(ngx_http_headers_in_t, destination)
#define NGX_HTTP_CLOJURE_HEADERSI_OVERWRITE_IDX  87
#define NGX_HTTP_CLOJURE_HEADERSI_OVERWRITE_OFFSET offsetof(ngx_http_headers_in_t, overwrite)
#define NGX_HTTP_CLOJURE_HEADERSI_DATE_IDX  88
#define NGX_HTTP_CLOJURE_HEADERSI_DATE_OFFSET offsetof(ngx_http_headers_in_t, date)
#endif

#define NGX_HTTP_CLOJURE_HEADERSI_USER_IDX  89
#define NGX_HTTP_CLOJURE_HEADERSI_USER_OFFSET offsetof(ngx_http_headers_in_t, user)
#define NGX_HTTP_CLOJURE_HEADERSI_PASSWD_IDX  90
#define NGX_HTTP_CLOJURE_HEADERSI_PASSWD_OFFSET offsetof(ngx_http_headers_in_t, passwd)
#define NGX_HTTP_CLOJURE_HEADERSI_COOKIE_IDX  91
#define NGX_HTTP_CLOJURE_HEADERSI_COOKIE_OFFSET offsetof(ngx_http_headers_in_t, cookies)
#define NGX_HTTP_CLOJURE_HEADERSI_SERVER_IDX  92
#define NGX_HTTP_CLOJURE_HEADERSI_SERVER_OFFSET offsetof(ngx_http_headers_in_t, server)
#define NGX_HTTP_CLOJURE_HEADERSI_CONTENT_LENGTH_N_IDX  93
#define NGX_HTTP_CLOJURE_HEADERSI_CONTENT_LENGTH_N_OFFSET offsetof(ngx_http_headers_in_t, content_length_n)
#define NGX_HTTP_CLOJURE_HEADERSI_KEEP_ALIVE_N_IDX  94
#define NGX_HTTP_CLOJURE_HEADERSI_KEEP_ALIVE_N_OFFSET offsetof(ngx_http_headers_in_t, keep_alive_n)
#define NGX_HTTP_CLOJURE_HEADERSI_HEADERS_IDX  95
#define NGX_HTTP_CLOJURE_HEADERSI_HEADERS_OFFSET offsetof(ngx_http_headers_in_t, headers)


/*index for size of ngx_http_headers_out_t */
#define NGX_HTTP_CLOJURE_HEADERSOT_SIZE_IDX 128
#define NGX_HTTP_CLOJURE_HEADERSOT_SIZE sizeof(ngx_http_headers_out_t)
/*field offset index for ngx_http_headers_out_t*/
#define NGX_HTTP_CLOJURE_HEADERSO_STATUS_IDX  129
#define NGX_HTTP_CLOJURE_HEADERSO_STATUS_OFFSET offsetof(ngx_http_headers_out_t, status)
#define NGX_HTTP_CLOJURE_HEADERSO_STATUS_LINE_IDX  130
#define NGX_HTTP_CLOJURE_HEADERSO_STATUS_LINE_OFFSET offsetof(ngx_http_headers_out_t, status_line)
#define NGX_HTTP_CLOJURE_HEADERSO_SERVER_IDX  131
#define NGX_HTTP_CLOJURE_HEADERSO_SERVER_OFFSET offsetof(ngx_http_headers_out_t, server)
#define NGX_HTTP_CLOJURE_HEADERSO_DATE_IDX  132
#define NGX_HTTP_CLOJURE_HEADERSO_DATE_OFFSET offsetof(ngx_http_headers_out_t, date)
#define NGX_HTTP_CLOJURE_HEADERSO_CONTENT_LENGTH_IDX  133
#define NGX_HTTP_CLOJURE_HEADERSO_CONTENT_LENGTH_OFFSET offsetof(ngx_http_headers_out_t, content_length)
#define NGX_HTTP_CLOJURE_HEADERSO_CONTENT_ENCODING_IDX  134
#define NGX_HTTP_CLOJURE_HEADERSO_CONTENT_ENCODING_OFFSET offsetof(ngx_http_headers_out_t, content_encoding)
#define NGX_HTTP_CLOJURE_HEADERSO_LOCATION_IDX  135
#define NGX_HTTP_CLOJURE_HEADERSO_LOCATION_OFFSET offsetof(ngx_http_headers_out_t, location)
#define NGX_HTTP_CLOJURE_HEADERSO_REFRESH_IDX  136
#define NGX_HTTP_CLOJURE_HEADERSO_REFRESH_OFFSET offsetof(ngx_http_headers_out_t, refresh)
#define NGX_HTTP_CLOJURE_HEADERSO_LAST_MODIFIED_IDX  137
#define NGX_HTTP_CLOJURE_HEADERSO_LAST_MODIFIED_OFFSET offsetof(ngx_http_headers_out_t, last_modified)
#define NGX_HTTP_CLOJURE_HEADERSO_CONTENT_RANGE_IDX  138
#define NGX_HTTP_CLOJURE_HEADERSO_CONTENT_RANGE_OFFSET offsetof(ngx_http_headers_out_t, content_range)
#define NGX_HTTP_CLOJURE_HEADERSO_ACCEPT_RANGES_IDX  139
#define NGX_HTTP_CLOJURE_HEADERSO_ACCEPT_RANGES_OFFSET offsetof(ngx_http_headers_out_t, accept_ranges)
#define NGX_HTTP_CLOJURE_HEADERSO_WWW_AUTHENTICATE_IDX  140
#define NGX_HTTP_CLOJURE_HEADERSO_WWW_AUTHENTICATE_OFFSET offsetof(ngx_http_headers_out_t, www_authenticate)
#define NGX_HTTP_CLOJURE_HEADERSO_EXPIRES_IDX  141
#define NGX_HTTP_CLOJURE_HEADERSO_EXPIRES_OFFSET offsetof(ngx_http_headers_out_t, expires)
#define NGX_HTTP_CLOJURE_HEADERSO_ETAG_IDX  142
#define NGX_HTTP_CLOJURE_HEADERSO_ETAG_OFFSET offsetof(ngx_http_headers_out_t, etag)
#define NGX_HTTP_CLOJURE_HEADERSO_OVERRIDE_CHARSET_IDX  143
#define NGX_HTTP_CLOJURE_HEADERSO_OVERRIDE_CHARSET_OFFSET offsetof(ngx_http_headers_out_t, override_charset)
#define NGX_HTTP_CLOJURE_HEADERSO_CONTENT_TYPE_LEN_IDX  144
#define NGX_HTTP_CLOJURE_HEADERSO_CONTENT_TYPE_LEN_OFFSET offsetof(ngx_http_headers_out_t, content_type_len)
#define NGX_HTTP_CLOJURE_HEADERSO_CONTENT_TYPE_IDX  145
#define NGX_HTTP_CLOJURE_HEADERSO_CONTENT_TYPE_OFFSET offsetof(ngx_http_headers_out_t, content_type)
#define NGX_HTTP_CLOJURE_HEADERSO_CHARSET_IDX  146
#define NGX_HTTP_CLOJURE_HEADERSO_CHARSET_OFFSET offsetof(ngx_http_headers_out_t, charset)
#define NGX_HTTP_CLOJURE_HEADERSO_CONTENT_TYPE_LOWCASE_IDX  147
#define NGX_HTTP_CLOJURE_HEADERSO_CONTENT_TYPE_LOWCASE_OFFSET offsetof(ngx_http_headers_out_t, content_type_lowcase)
#define NGX_HTTP_CLOJURE_HEADERSO_CONTENT_TYPE_HASH_IDX  148
#define NGX_HTTP_CLOJURE_HEADERSO_CONTENT_TYPE_HASH_OFFSET offsetof(ngx_http_headers_out_t, content_type_hash)
#define NGX_HTTP_CLOJURE_HEADERSO_CACHE_CONTROL_IDX  149
#define NGX_HTTP_CLOJURE_HEADERSO_CACHE_CONTROL_OFFSET offsetof(ngx_http_headers_out_t, cache_control)
#define NGX_HTTP_CLOJURE_HEADERSO_CONTENT_LENGTH_N_IDX  150
#define NGX_HTTP_CLOJURE_HEADERSO_CONTENT_LENGTH_N_OFFSET offsetof(ngx_http_headers_out_t, content_length_n)
#define NGX_HTTP_CLOJURE_HEADERSO_DATE_TIME_IDX  151
#define NGX_HTTP_CLOJURE_HEADERSO_DATE_TIME_OFFSET offsetof(ngx_http_headers_out_t, cache_control)
#define NGX_HTTP_CLOJURE_HEADERSO_LAST_MODIFIED_TIME_IDX  152
#define NGX_HTTP_CLOJURE_HEADERSO_LAST_MODIFIED_TIME_OFFSET offsetof(ngx_http_headers_out_t, cache_control)
#define NGX_HTTP_CLOJURE_HEADERSO_HEADERS_IDX  153
#define NGX_HTTP_CLOJURE_HEADERSO_HEADERS_OFFSET offsetof(ngx_http_headers_out_t, headers)

#define NGX_WORKER_PROCESSORS_NUM_ID 250

/*ngx_http_clojure_module_ctx_t idx*/
#define NGINX_CLOJURE_MODULE_CTX_PHRASE_ID 251
#define NGINX_CLOJURE_MODULE_CTX_PHRASE_ID_OFFSET offset(ngx_http_clojure_module_ctx_t, phrase)


#define NGINX_CLOJURE_RT_WORKERS_ID 252
#define NGINX_VER_ID 253
#define NGINX_CLOJURE_VER_ID 254
#define NGX_HTTP_CLOJURE_MEM_IDX_END 255


#define NGX_HTTP_CLOJURE_MEM_ERR_VAR_NOT_FOUND 32
#define NGX_HTTP_CLOJURE_MEM_ERR_VAR_UNCHANGABLE 33
#define NGX_HTTP_CLOJURE_MEM_ERR_MALLOC 34


#define NGX_BUF_LAST_OF_NONE  0
#define NGX_BUF_LAST_OF_CHAIN  1
#define NGX_BUF_LAST_OF_RESPONSE 2

#define ngx_http_clojure_add_const_header(headers, hn, hv) \
	do { \
		ngx_table_elt_t *hxx = ngx_list_push(&headers); \
		if (hxx == NULL) { \
			return NGX_ERROR; \
		} \
		hxx->hash = 1; \
		ngx_str_set(&hxx->key, hn); \
		ngx_str_set(&hxx->value, hv);  \
	} while(0)


#define ngx_http_clojure_get_header(headers, hn, /*out*/hr) \
	do { \
		ngx_list_part_t *part = &headers.part; \
		ngx_table_elt_t *h = part->elts; \
		ngx_uint_t i; \
	    for (i = 0; /* void */ ; i++) { \
	        if (i >= part->nelts) { \
	            if (part->next == NULL) { \
	                break; \
	            } \
	            part = part->next; \
	            h = part->elts; \
	            i = 0; \
	        } \
	        if (h[i].hash && sizeof(hn) - 1 == h[i].key.len && ngx_strcasecmp((u_char*)hn, h[i].key.data) == 0) { \
	        	hr = &h[i]; \
	        } \
	    } \
	} while(0)


#define nc_htonll(val) \
  (ngx_http_clojure_is_little_endian ? \
		  ((((uint64_t)htonl((uint32_t)(val))) << 32) \
		             | htonl((uint32_t)(val >> 32))) \
		: (val))

#define nc_ntohll(val) \
  (ngx_http_clojure_is_little_endian ? \
		  ((((uint64_t)ntohl((uint32_t)(val))) << 32) \
		             | ntohl((uint32_t)((val) >> 32))) \
		: (val))


int ngx_http_clojure_check_memory_util();

int ngx_http_clojure_pipe_init_by_master(int workers);

int ngx_http_clojure_pipe_exit_by_master();

ngx_int_t ngx_http_clojure_mem_wakeup_event_loop();

/*
 *
 */
int ngx_http_clojure_init_memory_util(ngx_core_conf_t  *ccf, ngx_http_core_srv_conf_t *cscf, ngx_http_clojure_main_conf_t  *mcf, ngx_log_t *log);

int ngx_http_clojure_destroy_memory_util(ngx_log_t *log);


int ngx_http_clojure_register_script(ngx_int_t phase, ngx_str_t *handler_type,
		ngx_str_t *handler, ngx_str_t *code, ngx_array_t *pros, ngx_int_t *pcid);

int ngx_http_clojure_eval(int cid, ngx_http_request_t *r, ngx_chain_t *c);

ngx_int_t ngx_http_clojure_hijack_send_header(ngx_http_request_t *r, ngx_int_t flag);

ngx_int_t ngx_http_clojure_filter_continue_next_body_filter(ngx_http_request_t *r, ngx_chain_t *in);

ngx_int_t ngx_http_clojure_prepare_server_header(ngx_http_request_t *r);

ngx_int_t ngx_http_clojure_websocket_upgrade(ngx_http_request_t * r);

ngx_int_t ngx_http_clojure_set_elt_header(ngx_http_request_t *r, ngx_table_elt_t *h, ngx_uint_t offset);
ngx_int_t ngx_http_clojure_set_array_header(ngx_http_request_t *r, ngx_table_elt_t *h, ngx_uint_t offset);
ngx_int_t ngx_http_clojure_set_content_type_header(ngx_http_request_t *r, ngx_table_elt_t *h, ngx_uint_t offset);
ngx_int_t ngx_http_clojure_set_content_len_header(ngx_http_request_t *r, ngx_table_elt_t *h, ngx_uint_t offset);

void ngx_http_clojure_cleanup_handler(void *data);

#define NGX_HTTP_CLOJURE_RELOAD_DELAY_MAX_TIME 30000 /*30 seconds*/

void ngx_http_clojure_try_set_reload_delay_timer(ngx_http_clojure_module_ctx_t *ctx, char  *func_name);

void ngx_http_clojure_try_unset_reload_delay_timer(ngx_http_clojure_module_ctx_t *ctx, char *func_name);

extern ngx_module_t  ngx_http_clojure_module;

extern ngx_http_output_header_filter_pt ngx_http_clojure_next_header_filter;
extern ngx_http_output_body_filter_pt ngx_http_clojure_next_body_filter;

extern int ngx_http_clojure_is_little_endian;

extern ngx_event_t ngx_http_clojure_reload_delay_event;

#define ngx_http_clojure_get_ctx(r, octx)  \
	octx = (r)->ctx[ngx_http_clojure_module.ctx_index]; \
  if (!octx && (r)->pool)  { \
    	 ngx_http_cleanup_t *cln = r->cleanup; \
    	 while (cln) { \
    		 if (cln->handler == ngx_http_clojure_cleanup_handler) { \
    			 octx = cln->data; \
    			 if ( (r)->ctx[ngx_http_clojure_module.ctx_index] == NULL ) { \
    				 (r)->ctx[ngx_http_clojure_module.ctx_index] = octx; \
           } \
    			 break; \
    		 } \
    		 cln = cln->next; \
    	 } \
  }


#endif /* NGX_HTTP_CLOJURE_MEM_H_ */
