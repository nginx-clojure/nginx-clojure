/*
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 */

#ifndef NGX_HTTP_CLOJURE_MEM_H_
#define NGX_HTTP_CLOJURE_MEM_H_

#include <nginx.h>
#include <ngx_http.h>



#if defined(_WIN32) || defined(WIN32)

#pragma warning (disable : 4305)
#pragma warning (disable : 4244)
#pragma warning (disable : 4152)
#pragma warning (disable : 4130)

#ifndef PRIu64
#define PRIu64 "I64u"
#endif

#else
#define __STDC_FORMAT_MACROS
#include <inttypes.h>
#endif

#define nginx_clojure_ver  3000 /*0.3.0*/

/*the least jar version required*/
#define nginx_clojure_required_rt_lver 3000

#define NGINX_CLOJURE_VER_NUM_STR "0.3.0"

#define NGINX_CLOJURE_VER "nginx-clojure/" NGINX_CLOJURE_VER_NUM_STR

/*fake phase for filter*/
#define NGX_HTTP_INIT_PROCESS_PHASE  17
#define NGX_HTTP_HEADER_FILTER_PHASE  18
#define NGX_HTTP_BODY_FILTER_PHASE  19
#define NGX_HTTP_EXIT_PROCESS_PHASE  20

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
	/*for filter under thread pool mode or coroutine mode*/
	ngx_chain_t *pending;
} ngx_http_clojure_module_ctx_t;

#define ngx_http_clojure_init_ctx(ctx, p) \
		ctx->handled_couter = 1; \
		ctx->phase = p; \
		ctx->last_buf_meeted = 0; \
		ctx->busy = ctx->free = ctx->pending = NULL; \
		ctx->ignore_filters = 0; \
		ctx->client_body_done = 0; \
		ctx->async_body_read = 0 ; \
		ctx->wait_for_header_filter = 0 ;\
		ctx->pending_body_filter = 0 ; \
		ctx->ignore_next_response = 0

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

#if (NGX_HTTP_PROXY || NGX_HTTP_REALIP || NGX_HTTP_GEO)
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

int ngx_http_clojure_check_memory_util();

int ngx_http_clojure_pipe_init_by_master(int workers);


/*
 *
 */
int ngx_http_clojure_init_memory_util(ngx_int_t jvm_workers, ngx_log_t *log);

int ngx_http_clojure_register_script(ngx_int_t phase, ngx_str_t *handler_type, ngx_str_t *handler, ngx_str_t *code, ngx_int_t *pcid);

int ngx_http_clojure_eval(int cid, ngx_http_request_t *r, ngx_chain_t *c);

ngx_int_t ngx_http_clojure_filter_continue_next_body_filter(ngx_http_request_t *r, ngx_chain_t *in);

ngx_int_t ngx_http_clojure_prepare_server_header(ngx_http_request_t *r);

extern ngx_module_t  ngx_http_clojure_module;

extern ngx_http_output_header_filter_pt ngx_http_clojure_next_header_filter;
extern ngx_http_output_body_filter_pt ngx_http_clojure_next_body_filter;

#endif /* NGX_HTTP_CLOJURE_MEM_H_ */
