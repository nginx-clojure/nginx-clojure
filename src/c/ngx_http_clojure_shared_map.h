/*
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 */

#ifndef NGX_HTTP_CLOJURE_SHARED_MAP_H_
#define NGX_HTTP_CLOJURE_SHARED_MAP_H_

#include <nginx.h>
#include <ngx_http.h>

#if defined(__GNUC__) && (__GNUC__ >= 4)
  #define NGX_CLOJURE_ATTR_MAY_ALIAS __attribute__((__may_alias__))
#else
  #define NGX_CLOJURE_ATTR_MAY_ALIAS
#endif

#define NGX_CLOJURE_SHARED_MAP_OK 0
#define NGX_CLOJURE_SHARED_MAP_OUT_OF_MEM 1
#define NGX_CLOJURE_SHARED_MAP_NOT_FOUND 2
#define NGX_CLOJURE_SHARED_MAP_INVLAID_KEY_TYPE 3
#define NGX_CLOJURE_SHARED_MAP_INVLAID_VALUE_TYPE 4


extern ngx_cycle_t *ngx_http_clojure_global_cycle;

#define NGX_CLOJURE_SHARED_MAP_NAME_MAX_LEN 255

#define NGX_CLOJURE_SHARED_MAP_JINT 0
#define NGX_CLOJURE_SHARED_MAP_JLONG 1
#define NGX_CLOJURE_SHARED_MAP_JSTRING 2
#define NGX_CLOJURE_SHARED_MAP_JBYTEA 3
#define NGX_CLOJURE_SHARED_MAP_JOBJECT 4

struct ngx_http_clojure_shared_map_ctx_s;

typedef struct ngx_http_clojure_shared_map_ctx_s ngx_http_clojure_shared_map_ctx_t;

typedef void (*ngx_http_clojure_shared_map_val_handler)(uint8_t /*vtype*/, const void * /*val*/, size_t /*vsize*/,
		void* /*handler_data*/);

typedef ngx_int_t (*ngx_http_clojure_shared_map_init_f)(ngx_conf_t * /*cf*/, ngx_http_clojure_shared_map_ctx_t * /*ctx*/);

typedef ngx_int_t (*ngx_http_clojure_shared_map_get_entry_f)(ngx_http_clojure_shared_map_ctx_t * /*ctx*/, uint8_t /*ktype*/,
		const u_char * /*key*/, size_t /*klen*/, ngx_http_clojure_shared_map_val_handler /*val_handler*/, void * /*handler_data*/);

/**
 * returns :
 * (1) NGX_CLOJURE_SHARED_MAP_OK if key is found.
 * (2) NGX_CLOJURE_SHARED_MAP_NOT_FOUND if key is not found.
 * (3) NGX_CLOJURE_SHARED_MAP_OUT_OF_MEM if there's no memory
 * (4) NGX_CLOJURE_SHARED_MAP_INVLAID_KEY_TYPE if key type is not supported
 */
typedef ngx_int_t (*ngx_http_clojure_shared_map_put_entry_f)(ngx_http_clojure_shared_map_ctx_t * /*ctx*/, uint8_t /*ktype*/,
			const u_char * /*key*/, size_t /*klen*/, uint8_t /*vtype*/, const void * /*val*/, size_t /*vlen*/,
			ngx_http_clojure_shared_map_val_handler /*val_handler*/, void * /*handler_data*/);

typedef ngx_int_t (*ngx_http_clojure_shared_map_remove_entry_f)(ngx_http_clojure_shared_map_ctx_t * /*ctx*/, uint8_t /*ktype*/,
		const u_char * /*key*/, size_t /*klen*/, ngx_http_clojure_shared_map_val_handler /*val_handler*/, void * /*handler_data*/);

typedef ngx_int_t (*ngx_http_clojure_shared_map_size_f)(ngx_http_clojure_shared_map_ctx_t * /*ctx*/);

typedef ngx_int_t (*ngx_http_clojure_shared_map_clear_f)(ngx_http_clojure_shared_map_ctx_t * /*ctx*/);

typedef struct {
	const char* name;
	ngx_http_clojure_shared_map_init_f init;
	ngx_http_clojure_shared_map_get_entry_f get;
	ngx_http_clojure_shared_map_put_entry_f put;
	ngx_http_clojure_shared_map_put_entry_f put_if_absent;
	ngx_http_clojure_shared_map_remove_entry_f remove;
	ngx_http_clojure_shared_map_size_f size;
	ngx_http_clojure_shared_map_clear_f clear;
} ngx_http_clojure_shared_map_impl_t;


struct ngx_http_clojure_shared_map_ctx_s {
	ngx_str_t name;
	ngx_log_t *log;
	ngx_array_t *arguments;
	void *impl_ctx;
	ngx_http_clojure_shared_map_impl_t *impl;
};

ngx_http_clojure_shared_map_ctx_t* ngx_http_clojure_shared_map_get_map(u_char *name, size_t len);

char * ngx_http_clojure_shared_map(ngx_conf_t *cf, ngx_command_t *cmd, void *conf);

int ngx_http_clojure_init_shared_map_util();

#endif
