/*
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 */

#ifndef NGX_HTTP_CLOJURE_MEM_H_
#define NGX_HTTP_CLOJURE_MEM_H_

#include <ngx_http.h>

#define NGX_HTTP_CLOJURE_MEM_IDX_START 0

/*index for size of ngx_uint_t */
#define NGX_HTTP_CLOJURE_UINT_SIZE_IDX 0
#define NGX_HTTP_CLOJURE_UINT_SIZE sizeof(ngx_uint_t)

#define NGX_HTTP_CLOJURE_PTR_SIZE_IDX 1
#define NGX_HTTP_CLOJURE_PTR_SIZE sizeof(void *)

#define NGX_HTTP_CLOJURE_SIZET_SIZE_IDX 2
#define NGX_HTTP_CLOJURE_SIZET_SIZE sizeof(size_t)

/*index for size of ngx_str_t */
#define NGX_HTTP_CLOJURE_STR_SIZE_IDX 8
#define NGX_HTTP_CLOJURE_STR_SIZE sizeof(ngx_str_t)
/*field offset index for ngx_str_t*/
#define NGX_HTTP_CLOJURE_STR_LEN_IDX 9
#define NGX_HTTP_CLOJURE_STR_LEN_OFFSET offsetof(ngx_str_t,len)
#define NGX_HTTP_CLOJURE_STR_DATA_IDX 10
#define NGX_HTTP_CLOJURE_STR_DATA_OFFSET offsetof(ngx_str_t,data)


/*index for size of ngx_table_elt_t */
#define NGX_HTTP_CLOJURE_TELT_SIZE_IDX 11
#define NGX_HTTP_CLOJURE_TELT_SIZE sizeof(ngx_table_elt_t)
/*field offset index for ngx_table_elt_t*/
#define NGX_HTTP_CLOJURE_TELT_HASH_IDX 12
#define NGX_HTTP_CLOJURE_TELT_HASH_OFFSET offsetof(ngx_table_elt_t,hash)
#define NGX_HTTP_CLOJURE_TELT_KEY_IDX 13
#define NGX_HTTP_CLOJURE_TELT_KEY_OFFSET offsetof(ngx_table_elt_t,key)
#define NGX_HTTP_CLOJURE_TELT_VALUE_IDX 14
#define NGX_HTTP_CLOJURE_TELT_VALUE_OFFSET offsetof(ngx_table_elt_t,value)
#define NGX_HTTP_CLOJURE_TELT_LOWCASE_KEY_IDX 15
#define NGX_HTTP_CLOJURE_TELT_LOWCASE_KEY_OFFSET offsetof(ngx_table_elt_t,lowcase_key)

#define NGX_HTTP_CLOJURE_CHAIN_SIZE_IDX 16
#define NGX_HTTP_CLOJURE_CHAIN_SIZE sizeof(ngx_chain_t)
#define NGX_HTTP_CLOJURE_CHAIN_BUF_IDX  17
#define NGX_HTTP_CLOJURE_CHAIN_BUF_OFFSET  offsetof(ngx_chain_t, buf)
#define NGX_HTTP_CLOJURE_CHAIN_NEXT_IDX  18
#define NGX_HTTP_CLOJURE_CHAIN_NEXT_OFFSET  offsetof(ngx_chain_t, next)

/*index for size of ngx_http_request_t */
#define NGX_HTTP_CLOJURE_REQ_SIZE_IDX 32
#define NGX_HTTP_CLOJURE_REQ_SIZE sizeof(ngx_http_request_t)
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

/*index for size of ngx_http_headers_in_t */
#define NGX_HTTP_CLOJURE_HEADERS_SIZE_IDX 64
#define NGX_HTTP_CLOJURE_HEADERS_SIZE sizeof(ngx_http_headers_in_t)
/*field offset index for ngx_http_headers_in_t*/
#define NGX_HTTP_CLOJURE_HEADERS_HOST_IDX  65
#define NGX_HTTP_CLOJURE_HEADERS_HOST_OFFSET offsetof(ngx_http_headers_in_t, host)
#define NGX_HTTP_CLOJURE_HEADERS_CONTENT_LENGTH_IDX  66
#define NGX_HTTP_CLOJURE_HEADERS_CONTENT_LENGTH_OFFSET offsetof(ngx_http_headers_in_t, content_length)
#define NGX_HTTP_CLOJURE_HEADERS_CONTENT_TYPE_IDX  67
#define NGX_HTTP_CLOJURE_HEADERS_CONTENT_TYPE_OFFSET offsetof(ngx_http_headers_in_t, content_type)



/*index for size of ngx_http_headers_out_t */
#define NGX_HTTP_CLOJURE_HEADERSO_SIZE_IDX 128
#define NGX_HTTP_CLOJURE_HEADERSO_SIZE sizeof(ngx_http_headers_out_t)
/*field offset index for ngx_http_headers_out_t*/
#define NGX_HTTP_CLOJURE_HEADERSO_STATUS_IDX  129
#define NGX_HTTP_CLOJURE_HEADERSO_STATUS_OFFSET offsetof(ngx_http_headers_out_t, status)
#define NGX_HTTP_CLOJURE_HEADERSO_CONTENT_LENGTH_N_IDX  130
#define NGX_HTTP_CLOJURE_HEADERSO_CONTENT_LENGTH_N_OFFSET offsetof(ngx_http_headers_out_t, content_length_n)
#define NGX_HTTP_CLOJURE_HEADERSO_CONTENT_TYPE_IDX  131
#define NGX_HTTP_CLOJURE_HEADERSO_CONTENT_TYPE_OFFSET offsetof(ngx_http_headers_out_t, content_type)


#define NGX_HTTP_CLOJURE_MEM_IDX_END 255


int ngx_http_clojure_check_memory_util();

/*
 *
 */
int ngx_http_clojure_init_memory_util();

int ngx_http_clojure_register_script(char *script, size_t len, ngx_int_t *cid);

int ngx_http_clojure_eval(int handle, void *r);

#endif /* NGX_HTTP_CLOJURE_MEM_H_ */
