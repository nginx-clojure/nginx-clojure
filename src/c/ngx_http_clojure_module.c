/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */

#include <ngx_config.h>
#include <ngx_core.h>
#include <ngx_http.h>
#include "ngx_http_clojure_jvm.h"
#include "ngx_http_clojure_mem.h"
#include "ngx_http_clojure_shared_map.h"
#include "ngx_http_clojure_socket.h"

ngx_cycle_t *ngx_http_clojure_global_cycle;

ngx_http_output_header_filter_pt ngx_http_clojure_next_header_filter;
ngx_http_output_body_filter_pt ngx_http_clojure_next_body_filter;
ngx_uint_t ngx_max_balanced_connection_per_worker = 1024;


static char* ngx_http_clojure_set_max_balanced_tcp_connections(ngx_conf_t *cf, ngx_command_t *cmd, void *conf) ;

static void ngx_http_clojure_reset_listening_backlog(ngx_conf_t *cf) ;

static ngx_int_t ngx_http_clojure_check_access_jvm_cp(ngx_http_clojure_main_conf_t *mcf, ngx_core_conf_t *ccf, ngx_log_t *log);

static char* ngx_http_clojure_set_jvm_classpath(ngx_conf_t *cf, ngx_command_t *cmd, void *conf) ;

static char* ngx_http_clojure_set_str_slot_and_enable_init_handler_tag(ngx_conf_t *cf, ngx_command_t *cmd, void *conf) ;

static char* ngx_http_clojure_set_str_slot_and_enable_exit_handler_tag(ngx_conf_t *cf, ngx_command_t *cmd, void *conf);

static char* ngx_http_clojure_set_str_slot_and_enable_content_handler_tag(ngx_conf_t *cf, ngx_command_t *cmd, void *conf) ;

static char* ngx_http_clojure_set_str_slot_and_enable_rewrite_handler_tag(ngx_conf_t *cf, ngx_command_t *cmd, void *conf) ;

static char* ngx_http_clojure_set_str_slot_and_enable_header_filter_tag(ngx_conf_t *cf, ngx_command_t *cmd, void *conf) ;

static char* ngx_http_clojure_set_str_slot_and_enable_body_filter_tag(ngx_conf_t *cf, ngx_command_t *cmd, void *conf) ;

static char* ngx_http_clojure_set_str_slot_and_enable_access_handler_tag(ngx_conf_t *cf, ngx_command_t *cmd, void *conf) ;

static char* ngx_http_clojure_set_always_read_body(ngx_conf_t *cf, ngx_command_t *cmd, void *conf);

static void* ngx_http_clojure_create_loc_conf(ngx_conf_t *cf);

static void * ngx_http_clojure_create_main_conf(ngx_conf_t *cf);

static char* ngx_http_clojure_merge_loc_conf(ngx_conf_t *cf, void *parent, void *child);

static ngx_int_t ngx_http_clojure_module_init(ngx_cycle_t *cycle);

static void ngx_http_clojure_module_exit(ngx_cycle_t *cycle);

static ngx_int_t ngx_http_clojure_process_init(ngx_cycle_t *cycle);

static void ngx_http_clojure_process_exit(ngx_cycle_t *cycle);

#if defined(_WIN32) || defined(WIN32)
static ngx_int_t ngx_http_clojure_quit_master(ngx_cycle_t *cycle);
#endif

static ngx_int_t ngx_http_clojure_postconfiguration(ngx_conf_t *cf);

static ngx_int_t ngx_http_clojure_content_handler(ngx_http_request_t * r);

static ngx_int_t ngx_http_clojure_rewrite_handler(ngx_http_request_t * r);

static ngx_int_t ngx_http_clojure_access_handler(ngx_http_request_t * r) ;

static ngx_int_t ngx_http_clojure_header_filter(ngx_http_request_t * r);

static ngx_int_t ngx_http_clojure_body_filter(ngx_http_request_t * r, ngx_chain_t *chain);

static ngx_int_t ngx_http_clojure_init_jvm_and_mem(ngx_core_conf_t  *ccf, ngx_http_core_srv_conf_t *cscf, ngx_http_clojure_main_conf_t  *mcf, ngx_log_t *log);

static ngx_int_t ngx_http_clojure_init_locations_handlers_helper(ngx_http_core_loc_conf_t *clcf);

static ngx_int_t ngx_http_clojure_init_locations_handlers_in_tree(ngx_http_location_tree_node_t *lt) ;

static ngx_int_t ngx_http_clojure_init_socket(ngx_http_clojure_main_conf_t  *mcf, ngx_log_t *log);

static ngx_int_t ngx_http_clojure_init_clojure_script(ngx_int_t phase, char *type, ngx_str_t *handler_type, ngx_str_t *handler,
		ngx_str_t *code, ngx_array_t *pros, ngx_int_t *pcid, ngx_log_t *log);

static char * ngx_http_clojure_jvm_var_post_handler(ngx_conf_t *cf, void *data, void *conf);

static char * ngx_http_clojure_jvm_options_post_handler(ngx_conf_t *cf, void *data, void *conf);

static u_char * ngx_http_clojure_eval_experssion(ngx_http_clojure_main_conf_t  *mcf, ngx_str_t *exp, ngx_pool_t *pool, size_t *len);

static void ngx_http_clojure_client_body_handler(ngx_http_request_t *r);

/* Sadly JNI_CreateJavaVM doesn't always return error code for bad things(e.g initialized memory is too large),
 * it just suspend and then jvm crash signal will be directly catched by nginx master which will re-initialize
 * it again so then jvms will be created and exit repeatedly and madly.
 * We use this memory shared variable to avoid it.*/
static ngx_atomic_t *ngx_http_clojure_jvm_be_mad_times;

#if defined(NGX_CLOJURE_WORKER_STAT)
ngx_atomic_t *ngx_http_clojure_worker_stats;
ngx_atomic_int_t *ngx_http_clojure_rem_accept_idx;
#endif
/**
 * total number of jvms created
 */
static ngx_atomic_t *ngx_http_clojure_jvm_num;

#if defined(_WIN32) || defined(WIN32)

#pragma data_seg("ngx_http_clojure_shared_memory")
ngx_atomic_t ngx_http_clojure_jvm_be_mad_times_ins = 0;
ngx_atomic_t ngx_http_clojure_jvm_num_ins = 1;
#pragma data_seg()

#pragma comment(linker, "/Section:ngx_http_clojure_shared_memory,RWS")

#else

static ngx_shm_t ngx_http_clojure_shared_memory;

#endif


static ngx_conf_post_t ngx_http_clojure_jvm_var_post = {
	ngx_http_clojure_jvm_var_post_handler
};

static ngx_conf_post_t ngx_http_clojure_jvm_options_post = {
	ngx_http_clojure_jvm_options_post_handler
};

static ngx_command_t ngx_http_clojure_commands[] = {
    {
		 ngx_string("max_balanced_tcp_connections"),
		 NGX_HTTP_MAIN_CONF  | NGX_CONF_TAKE1,
		 ngx_http_clojure_set_max_balanced_tcp_connections,
		 NGX_HTTP_MAIN_CONF_OFFSET,
		 offsetof(ngx_http_clojure_main_conf_t, max_balanced_tcp_connections),
		NULL
    },

    {
		ngx_string("jvm_options"),
		NGX_HTTP_MAIN_CONF | NGX_CONF_TAKE1,
		ngx_conf_set_str_array_slot,
		NGX_HTTP_MAIN_CONF_OFFSET,
		offsetof(ngx_http_clojure_main_conf_t, jvm_options),
		&ngx_http_clojure_jvm_options_post
    },
    {
		ngx_string("jvm_path"),
		NGX_HTTP_MAIN_CONF | NGX_CONF_TAKE1,
		ngx_conf_set_str_slot,
		NGX_HTTP_MAIN_CONF_OFFSET,
		offsetof(ngx_http_clojure_main_conf_t, jvm_path),
		NULL
    },
    {
		ngx_string("jvm_var"),
		NGX_HTTP_MAIN_CONF | NGX_CONF_TAKE2,
		ngx_conf_set_keyval_slot,
		NGX_HTTP_MAIN_CONF_OFFSET,
		offsetof(ngx_http_clojure_main_conf_t, jvm_vars),
		&ngx_http_clojure_jvm_var_post
    },
    {
		ngx_string("jvm_classpath"),
		NGX_HTTP_MAIN_CONF | NGX_CONF_TAKE1,
		ngx_http_clojure_set_jvm_classpath,
		NGX_HTTP_MAIN_CONF_OFFSET,
		0,
		NULL
    },
    {
		ngx_string("jvm_classpath_check"),
		NGX_HTTP_MAIN_CONF | NGX_CONF_TAKE1,
		ngx_conf_set_flag_slot,
		NGX_HTTP_MAIN_CONF_OFFSET,
		offsetof(ngx_http_clojure_main_conf_t, jvm_cp_check),
		NULL
    },
    {
		ngx_string("jvm_workers"),
		NGX_HTTP_MAIN_CONF | NGX_CONF_TAKE1,
		ngx_conf_set_num_slot,
		NGX_HTTP_MAIN_CONF_OFFSET,
		offsetof(ngx_http_clojure_main_conf_t, jvm_workers),
		NULL
    },
    {
		ngx_string("write_page_size"),
		NGX_HTTP_MAIN_CONF | NGX_HTTP_LOC_CONF | NGX_HTTP_SRV_CONF | NGX_CONF_TAKE1,
		ngx_conf_set_size_slot,
		NGX_HTTP_LOC_CONF_OFFSET,
		offsetof(ngx_http_clojure_loc_conf_t, write_page_size),
		NULL
    },
    {
		ngx_string("jvm_handler_type"),
		NGX_HTTP_MAIN_CONF | NGX_CONF_TAKE1,
		ngx_conf_set_str_slot,
		NGX_HTTP_MAIN_CONF_OFFSET,
		offsetof(ngx_http_clojure_main_conf_t, jvm_handler_type),
		NULL
    },
    {
		ngx_string("jvm_init_handler_name"),
		NGX_HTTP_MAIN_CONF | NGX_CONF_TAKE1,
		ngx_http_clojure_set_str_slot_and_enable_init_handler_tag,
		NGX_HTTP_MAIN_CONF_OFFSET,
		offsetof(ngx_http_clojure_main_conf_t, jvm_init_handler_name),
		NULL
    },
    {
		ngx_string("jvm_init_handler_code"),
		NGX_HTTP_MAIN_CONF | NGX_CONF_TAKE1,
		ngx_http_clojure_set_str_slot_and_enable_init_handler_tag,
		NGX_HTTP_MAIN_CONF_OFFSET,
		offsetof(ngx_http_clojure_main_conf_t, jvm_init_handler_code),
		NULL
    },
    {
		ngx_string("jvm_exit_handler_name"),
		NGX_HTTP_MAIN_CONF | NGX_CONF_TAKE1,
		ngx_http_clojure_set_str_slot_and_enable_exit_handler_tag,
		NGX_HTTP_MAIN_CONF_OFFSET,
		offsetof(ngx_http_clojure_main_conf_t, jvm_exit_handler_name),
		NULL
    },
    {
		ngx_string("jvm_exit_handler_code"),
		NGX_HTTP_MAIN_CONF | NGX_CONF_TAKE1,
		ngx_http_clojure_set_str_slot_and_enable_exit_handler_tag,
		NGX_HTTP_MAIN_CONF_OFFSET,
		offsetof(ngx_http_clojure_main_conf_t, jvm_exit_handler_code),
		NULL
    },
    {
		ngx_string("handlers_lazy_init"),
		NGX_HTTP_MAIN_CONF | NGX_HTTP_LOC_CONF | NGX_HTTP_SRV_CONF | NGX_CONF_TAKE1,
		ngx_conf_set_flag_slot,
		NGX_HTTP_LOC_CONF_OFFSET,
		offsetof(ngx_http_clojure_loc_conf_t, handlers_lazy_init),
		NULL
    },
    {
		ngx_string("auto_upgrade_ws"),
		NGX_HTTP_MAIN_CONF | NGX_HTTP_LOC_CONF | NGX_HTTP_SRV_CONF | NGX_CONF_TAKE1,
		ngx_conf_set_flag_slot,
		NGX_HTTP_LOC_CONF_OFFSET,
		offsetof(ngx_http_clojure_loc_conf_t, auto_upgrade_ws),
		NULL
    },
    {
		ngx_string("handler_type"),
		NGX_HTTP_SRV_CONF | NGX_HTTP_LOC_CONF | NGX_CONF_TAKE1,
		ngx_conf_set_str_slot,
		NGX_HTTP_LOC_CONF_OFFSET,
		offsetof(ngx_http_clojure_loc_conf_t, content_handler_type),
		NULL
    },
    {
		ngx_string("handler_name"),
		NGX_HTTP_LOC_CONF | NGX_CONF_TAKE1,
		ngx_http_clojure_set_str_slot_and_enable_content_handler_tag,
		NGX_HTTP_LOC_CONF_OFFSET,
		offsetof(ngx_http_clojure_loc_conf_t, content_handler_name),
		NULL
    },
    {
		ngx_string("handler_code"),
		NGX_HTTP_LOC_CONF | NGX_CONF_TAKE1,
		ngx_http_clojure_set_str_slot_and_enable_content_handler_tag,
		NGX_HTTP_LOC_CONF_OFFSET,
		offsetof(ngx_http_clojure_loc_conf_t, content_handler_code),
		NULL
    },
    {
		ngx_string("content_handler_type"),
		NGX_HTTP_SRV_CONF | NGX_HTTP_LOC_CONF | NGX_CONF_TAKE1,
		ngx_conf_set_str_slot,
		NGX_HTTP_LOC_CONF_OFFSET,
		offsetof(ngx_http_clojure_loc_conf_t, content_handler_type),
		NULL
    },
    {
		ngx_string("content_handler_name"),
		NGX_HTTP_LOC_CONF | NGX_CONF_TAKE1,
		ngx_http_clojure_set_str_slot_and_enable_content_handler_tag,
		NGX_HTTP_LOC_CONF_OFFSET,
		offsetof(ngx_http_clojure_loc_conf_t, content_handler_name),
		NULL
    },
    {
		ngx_string("content_handler_code"),
		NGX_HTTP_LOC_CONF | NGX_CONF_TAKE1,
		ngx_http_clojure_set_str_slot_and_enable_content_handler_tag,
		NGX_HTTP_LOC_CONF_OFFSET,
		offsetof(ngx_http_clojure_loc_conf_t, content_handler_code),
		NULL
    },
    {
		ngx_string("rewrite_handler_type"),
		 NGX_HTTP_LOC_CONF | NGX_CONF_TAKE1,
		ngx_conf_set_str_slot,
		NGX_HTTP_LOC_CONF_OFFSET,
		offsetof(ngx_http_clojure_loc_conf_t, rewrite_handler_type),
		NULL
    },
    {
		ngx_string("rewrite_handler_name"),
		NGX_HTTP_LOC_CONF | NGX_CONF_TAKE1,
		ngx_http_clojure_set_str_slot_and_enable_rewrite_handler_tag,
		NGX_HTTP_LOC_CONF_OFFSET,
		offsetof(ngx_http_clojure_loc_conf_t, rewrite_handler_name),
		NULL
    },
    {
		ngx_string("rewrite_handler_code"),
		NGX_HTTP_LOC_CONF | NGX_CONF_TAKE1,
		ngx_http_clojure_set_str_slot_and_enable_rewrite_handler_tag,
		NGX_HTTP_LOC_CONF_OFFSET,
		offsetof(ngx_http_clojure_loc_conf_t, rewrite_handler_code),
		NULL
    },

    {
		ngx_string("access_handler_type"),
		 NGX_HTTP_LOC_CONF | NGX_CONF_TAKE1,
		ngx_conf_set_str_slot,
		NGX_HTTP_LOC_CONF_OFFSET,
		offsetof(ngx_http_clojure_loc_conf_t, access_handler_type),
		NULL
    },
    {
		ngx_string("access_handler_name"),
		NGX_HTTP_LOC_CONF | NGX_CONF_TAKE1,
		ngx_http_clojure_set_str_slot_and_enable_access_handler_tag,
		NGX_HTTP_LOC_CONF_OFFSET,
		offsetof(ngx_http_clojure_loc_conf_t, access_handler_name),
		NULL
    },
    {
		ngx_string("access_handler_code"),
		NGX_HTTP_LOC_CONF | NGX_CONF_TAKE1,
		ngx_http_clojure_set_str_slot_and_enable_access_handler_tag,
		NGX_HTTP_LOC_CONF_OFFSET,
		offsetof(ngx_http_clojure_loc_conf_t, access_handler_code),
		NULL
    },

    {
		ngx_string("header_filter_type"),
		 NGX_HTTP_LOC_CONF | NGX_CONF_TAKE1,
		ngx_conf_set_str_slot,
		NGX_HTTP_LOC_CONF_OFFSET,
		offsetof(ngx_http_clojure_loc_conf_t, header_filter_type),
		NULL
    },
    {
		ngx_string("header_filter_name"),
		NGX_HTTP_LOC_CONF | NGX_CONF_TAKE1,
		ngx_http_clojure_set_str_slot_and_enable_header_filter_tag,
		NGX_HTTP_LOC_CONF_OFFSET,
		offsetof(ngx_http_clojure_loc_conf_t, header_filter_name),
		NULL
    },
    {
		ngx_string("header_filter_code"),
		NGX_HTTP_LOC_CONF | NGX_CONF_TAKE1,
		ngx_http_clojure_set_str_slot_and_enable_header_filter_tag,
		NGX_HTTP_LOC_CONF_OFFSET,
		offsetof(ngx_http_clojure_loc_conf_t, header_filter_code),
		NULL
    },

    {
		ngx_string("body_filter_type"),
		 NGX_HTTP_LOC_CONF | NGX_CONF_TAKE1,
		ngx_conf_set_str_slot,
		NGX_HTTP_LOC_CONF_OFFSET,
		offsetof(ngx_http_clojure_loc_conf_t, body_filter_type),
		NULL
    },
    {
		ngx_string("body_filter_name"),
		NGX_HTTP_LOC_CONF | NGX_CONF_TAKE1,
		ngx_http_clojure_set_str_slot_and_enable_body_filter_tag,
		NGX_HTTP_LOC_CONF_OFFSET,
		offsetof(ngx_http_clojure_loc_conf_t,  body_filter_name),
		NULL
    },
    {
		ngx_string("body_filter_code"),
		NGX_HTTP_LOC_CONF | NGX_CONF_TAKE1,
		ngx_http_clojure_set_str_slot_and_enable_body_filter_tag,
		NGX_HTTP_LOC_CONF_OFFSET,
		offsetof(ngx_http_clojure_loc_conf_t, body_filter_code),
		NULL
    },
    {
		ngx_string("content_handler_property"),
		NGX_HTTP_LOC_CONF | NGX_CONF_TAKE2,
		ngx_conf_set_keyval_slot,
		NGX_HTTP_LOC_CONF_OFFSET,
		offsetof(ngx_http_clojure_loc_conf_t, content_handler_properties),
		NULL
    },
    {
		ngx_string("rewrite_handler_property"),
		NGX_HTTP_LOC_CONF | NGX_CONF_TAKE2,
		ngx_conf_set_keyval_slot,
		NGX_HTTP_LOC_CONF_OFFSET,
		offsetof(ngx_http_clojure_loc_conf_t, rewrite_handler_properties),
		NULL
    },
    {
		ngx_string("access_handler_property"),
		NGX_HTTP_LOC_CONF | NGX_CONF_TAKE2,
		ngx_conf_set_keyval_slot,
		NGX_HTTP_LOC_CONF_OFFSET,
		offsetof(ngx_http_clojure_loc_conf_t, access_handler_properties),
		NULL
    },
    {
		ngx_string("header_filter_property"),
		NGX_HTTP_LOC_CONF | NGX_CONF_TAKE2,
		ngx_conf_set_keyval_slot,
		NGX_HTTP_LOC_CONF_OFFSET,
		offsetof(ngx_http_clojure_loc_conf_t, header_filter_properties),
		NULL
    },
    {
		ngx_string("body_filter_property"),
		NGX_HTTP_LOC_CONF | NGX_CONF_TAKE2,
		ngx_conf_set_keyval_slot,
		NGX_HTTP_LOC_CONF_OFFSET,
		offsetof(ngx_http_clojure_loc_conf_t, body_filter_properties),
		NULL
    },
    {
		ngx_string("always_read_body"),
		NGX_HTTP_MAIN_CONF | NGX_HTTP_SRV_CONF | NGX_HTTP_LOC_CONF | NGX_CONF_TAKE1,
		ngx_http_clojure_set_always_read_body,
		NGX_HTTP_LOC_CONF_OFFSET,
		0,
		NULL
    },
    {
    	ngx_string("shared_map"),
    	NGX_HTTP_MAIN_CONF | NGX_CONF_TAKE2,
    	ngx_http_clojure_shared_map,
    	0,
    	0,
    	NULL
    },

    ngx_null_command
};

ngx_event_t ngx_http_clojure_reload_delay_event;

static void ngx_http_clojure_reload_delay_event_handler(ngx_event_t *event) {

}

static ngx_http_clojure_header_holder_t ngx_http_clojure_headers_out_holders[] = {
		{ngx_string("Server"), ngx_http_clojure_set_elt_header, offsetof(ngx_http_headers_out_t, server)},
		{ngx_string("Date"), ngx_http_clojure_set_elt_header, offsetof(ngx_http_headers_out_t, date)},
		{ngx_string("Content-Encoding"), ngx_http_clojure_set_elt_header, offsetof(ngx_http_headers_out_t, content_encoding)},
		{ngx_string("Location"), ngx_http_clojure_set_elt_header, offsetof(ngx_http_headers_out_t, location)},
		{ngx_string("Refresh"), ngx_http_clojure_set_elt_header, offsetof(ngx_http_headers_out_t, refresh)},
		{ngx_string("Last-Modified"), ngx_http_clojure_set_elt_header, offsetof(ngx_http_headers_out_t, last_modified)},
		{ngx_string("Content-Range"), ngx_http_clojure_set_elt_header, offsetof(ngx_http_headers_out_t, content_range)},
		{ngx_string("Accept-Ranges"), ngx_http_clojure_set_elt_header, offsetof(ngx_http_headers_out_t, accept_ranges)},
		{ngx_string("WWW-Authenticate"), ngx_http_clojure_set_elt_header, offsetof(ngx_http_headers_out_t, www_authenticate)},
		{ngx_string("Expires"), ngx_http_clojure_set_elt_header, offsetof(ngx_http_headers_out_t, expires)},
		{ngx_string("Etag"), ngx_http_clojure_set_elt_header, offsetof(ngx_http_headers_out_t, etag)},
		{ngx_string("Cache-Control"), ngx_http_clojure_set_array_header, offsetof(ngx_http_headers_out_t, cache_control)},
		{ngx_string("Content-Type"), ngx_http_clojure_set_content_type_header, 0},
		{ngx_string("Content-Length"), ngx_http_clojure_set_content_len_header, 0},
		{ngx_null_string, NULL, 0},
};

static ngx_http_module_t  ngx_http_clojure_module_ctx = {
    NULL,                          /* preconfiguration */
    ngx_http_clojure_postconfiguration, /* postconfiguration */

    ngx_http_clojure_create_main_conf,  /* create main configuration */
    NULL,                          /* init main configuration */

    NULL,                          /* create server configuration */
    NULL,                          /* merge server configuration */

    ngx_http_clojure_create_loc_conf,  /* create location configuration */
    ngx_http_clojure_merge_loc_conf /* merge location configuration */
};

ngx_module_t  ngx_http_clojure_module = {
    NGX_MODULE_V1,
    &ngx_http_clojure_module_ctx, /* module context */
    ngx_http_clojure_commands,   /* module directives */
    NGX_HTTP_MODULE,               /* module type */
    NULL,                          /* init master */
    ngx_http_clojure_module_init,  /* init module */
    ngx_http_clojure_process_init, /* init process */
    NULL,                          /* init thread */
    NULL,                          /* exit thread */
    ngx_http_clojure_process_exit, /* exit process */
    ngx_http_clojure_module_exit,  /* exit master */
    NGX_MODULE_V1_PADDING
};

static ngx_int_t ngx_http_clojure_init_headers_out_holder_hash(ngx_conf_t *cf, ngx_hash_t *headers_out_holder_hash) {
	ngx_hash_init_t hash;
	ngx_array_t headers_out_holders;
	ngx_http_clojure_header_holder_t *header;
	ngx_hash_key_t *hk;

	if (ngx_array_init(&headers_out_holders, cf->temp_pool, 32, sizeof(ngx_hash_key_t)) != NGX_OK) {
		return NGX_ERROR ;
	}

	for (header = ngx_http_clojure_headers_out_holders; header->name.len; header++) {
		hk = ngx_array_push(&headers_out_holders);
		if (hk == NULL) {
			return NGX_ERROR ;
		}

		hk->key = header->name;
		hk->key_hash = ngx_hash_key_lc(header->name.data, header->name.len);
		hk->value = header;
	}

	hash.hash = headers_out_holder_hash;
	hash.key = ngx_hash_key_lc;
	hash.max_size = 512;
	hash.bucket_size = ngx_align(64, ngx_cacheline_size);
	hash.name = "nginx_clojure_headers_out_holder_in_hash";
	hash.pool = cf->pool;
	hash.temp_pool = NULL;

	if (ngx_hash_init(&hash, headers_out_holders.elts, headers_out_holders.nelts) != NGX_OK) {
		return NGX_ERROR ;
	}

	return NGX_OK;
}

static void * ngx_http_clojure_create_main_conf(ngx_conf_t *cf) {
	ngx_http_clojure_main_conf_t *conf;
	conf = ngx_pcalloc(cf->pool, sizeof(ngx_http_clojure_main_conf_t));
	if (conf == NULL) {
		return NGX_CONF_ERROR;
	}


	conf->jvm_path.len = NGX_CONF_UNSET_SIZE;
	conf->jvm_options = NGX_CONF_UNSET_PTR;

	conf->jvm_workers = NGX_CONF_UNSET;
	conf->max_balanced_tcp_connections = NGX_CONF_UNSET;
	conf->jvm_init_handler_id = conf->jvm_exit_handler_id = -1;

	conf->jvm_cp_check = NGX_CONF_UNSET;

	if (ngx_http_clojure_init_headers_out_holder_hash(cf, &conf->headers_out_holder_hash) != NGX_OK) {
		ngx_log_error(NGX_LOG_ERR, cf->log, 0, "can not ngx_http_clojure_init_headers_out_holder_hash");
		return NGX_CONF_ERROR;
	}

	return conf;
}

static void * ngx_http_clojure_create_loc_conf(ngx_conf_t *cf) {
	ngx_http_clojure_loc_conf_t *conf;
	conf = ngx_pcalloc(cf->pool, sizeof(ngx_http_clojure_loc_conf_t));
	if (conf == NULL){
		return NGX_CONF_ERROR;
	}
	conf->handlers_lazy_init = NGX_CONF_UNSET;
	conf->always_read_body = NGX_HTTP_CLOJURE_ALWATS_READ_BODY_UNSET;
	conf->auto_upgrade_ws = NGX_CONF_UNSET;
	conf->content_handler_id = -1;
	conf->rewrite_handler_id = -1;
	conf->header_filter_id = -1;
	conf->body_filter_id = -1;
	conf->access_handler_id = -1;
	conf->write_page_size = NGX_CONF_UNSET_SIZE;
	return conf;
}

static ngx_int_t ngx_http_clojure_init_clojure_script(ngx_int_t phase, char *type, ngx_str_t *handler_type, ngx_str_t *handler, ngx_str_t *code, ngx_array_t *pros,ngx_int_t *pcid , ngx_log_t *log) {
    if (*pcid < 0 && (code->len > 0 || handler->len > 0)) {
    	if (ngx_http_clojure_register_script(phase, handler_type, handler, code, pros, pcid) != NGX_HTTP_CLOJURE_JVM_OK){
    		ngx_log_error(NGX_LOG_ERR, log, 0, "invalid %s %s code : %s", handler_type->data, type, code->len > 0 ? code->data : handler->data);
    		return NGX_HTTP_INTERNAL_SERVER_ERROR;
    	}
    }
    return NGX_HTTP_CLOJURE_JVM_OK;
}

#define NGX_CLOJURE_CONF_LINE_MAX 8192

static u_char * ngx_http_clojure_eval_experssion(ngx_http_clojure_main_conf_t  *mcf, ngx_str_t *exp, ngx_pool_t *pool, size_t *len) {
	ngx_array_t *vars = mcf->jvm_vars;
	if (vars == NULL) {
		*len = exp->len;
		return exp->data;
	} else {
		u_char *sp = exp->data;
		u_char *esp = sp + exp->len;
		u_char tmp[NGX_CLOJURE_CONF_LINE_MAX];
		u_char *dp = tmp;
		u_char *edp = dp + NGX_CLOJURE_CONF_LINE_MAX;
		ngx_keyval_t *kv = vars->elts;
		u_char *rt;

		while (sp != esp && dp != edp) {
			if (*sp == '#' && sp[1] == '{') {
				u_char *ev = ngx_strlchr(sp, esp, '}');
				if (ev != NULL) {
					int vn = (int)vars->nelts;
					sp += 2;
					while (vn--) {
						if (kv[vn].key.len == (size_t)(ev - sp) && ngx_strncmp(kv[vn].key.data, sp, ev - sp) == 0) {
							if ((size_t)(edp - dp) < kv[vn].value.len + 1) {
								return NULL;
							}
							ngx_cpystrn(dp, kv[vn].value.data, kv[vn].value.len + 1);
							sp = ev + 1;
							dp += kv[vn].value.len;
							break;
						}
					}
					if (vn > -1) {
						continue;
					}else {
						*dp++ = '#';
						*dp++ = '{';
					}
				}
			}
			*dp++ = *sp++;
		}
		if (dp == edp) {
			return NULL;
		}

		*len = dp-tmp;
		rt = ngx_palloc(pool, *len+1);
		ngx_cpystrn(rt, tmp, *len+1);
		return rt;
	}
}

static char * ngx_http_clojure_jvm_var_post_handler(ngx_conf_t *cf, void *data, void *conf) {
	ngx_keyval_t *kv = conf;
	ngx_http_clojure_main_conf_t *mcf = ngx_http_conf_get_module_main_conf(cf, ngx_http_clojure_module);
	size_t vlen;
	if (ngx_strnstr(kv->value.data, "#{", kv->value.len) != NULL) {
		kv->value.data = ngx_http_clojure_eval_experssion(mcf, &kv->value, cf->pool, &vlen);
		if (kv->value.data == NULL) {
			ngx_conf_log_error(NGX_LOG_EMERG, cf, 0,
			                       "too long expanded jvm_var \"%s\"",
			                       kv->key.data);
			return NGX_CONF_ERROR;
		}
		kv->value.len = vlen;
	}
	return NGX_CONF_OK;
}

static char * ngx_http_clojure_jvm_options_post_handler(ngx_conf_t *cf, void *data, void *conf) {
	ngx_str_t *v = conf;
	size_t vlen;
	ngx_http_clojure_main_conf_t *mcf = ngx_http_conf_get_module_main_conf(cf, ngx_http_clojure_module);
	if (ngx_strnstr(v->data, "#{", v->len) != NULL) {
		u_char * ev = ngx_http_clojure_eval_experssion(mcf, v, cf->pool, &vlen);
		if (ev == NULL) {
			ngx_conf_log_error(NGX_LOG_EMERG, cf, 0,
					"too long expanded jvm_options \"%*s...\" started",
					    			                                       10, v->data);
			return NGX_CONF_ERROR;
		}
		v->data = ev;
		v->len = vlen;
	}

	if (!ngx_strncmp(v->data, "-Xbootclasspath", sizeof("-Xbootclasspath") -1)
			|| !ngx_strncmp(v->data, "-Djava.ext.dirs", sizeof("-Djava.ext.dirs") -1)) {
		mcf->jvm_cp_is_set = 1;
	}

	if (!ngx_strncmp(v->data, "-Djava.class.path", sizeof("-Djava.class.path") -1)) {
		mcf->jvm_cp_is_set = 1;
		ngx_conf_log_error(NGX_LOG_ALERT, cf, 0, "jvm_options \"-Djava.class.path\" is deprecated please use jvm_classpath which is better.\n"
				"e.g. \t\tjvm_classpath '/my-jars-path1/*:/my-classes'; \n"
				"\tall jars and sub-directory in my-jars-path1 will be set to the jvm classpath.\n");
	}

	return NGX_CONF_OK;
}

static ngx_int_t ngx_http_clojure_init_jvm_and_mem(ngx_core_conf_t  *ccf, ngx_http_core_srv_conf_t *cscf, ngx_http_clojure_main_conf_t  *mcf, ngx_log_t *log) {
	if (ngx_http_clojure_check_jvm() != NGX_HTTP_CLOJURE_JVM_OK){
    	ngx_str_t *elts = mcf->jvm_options->elts;
    	char  **options;
    	char *jvm_path;
    	ngx_uint_t i, j;
    	ngx_uint_t len = mcf->jvm_options->nelts;
    	int rc;
    	size_t vlen;
    	ngx_pool_t *pool = ngx_create_pool(40960, log);

    	if (mcf->jvm_cp) {
    		len += 1;
    	}

    	options = ngx_pcalloc(pool, len * sizeof(char *));
    	if (!options) {
    		ngx_log_error(NGX_LOG_ERR, log, 0, "can not malloc for jvm create options!");
    		return NGX_HTTP_CLOJURE_JVM_ERR_MALLOC;
    	}

    	jvm_path = (char *)mcf->jvm_path.data;

    	for (i = 0; i < len; i++){
    		if (i == len -1 && mcf->jvm_cp) {
    			size_t total = sizeof("-Djava.class.path=")-1;
    			u_char *cp_all;
    			elts = mcf->jvm_cp->elts;

    			for (j = 0; j < mcf->jvm_cp->nelts; j++) {
    				total += elts[j].len+1;
    			}

    			options[i] = ngx_pcalloc(pool, total);
    			cp_all = (u_char*)options[i];
    			cp_all = ngx_cpystrn(cp_all, (u_char*)"-Djava.class.path=", sizeof("-Djava.class.path="));

    			if (cp_all) {
        			for (j = 0; j < mcf->jvm_cp->nelts; j++) {
        				cp_all = ngx_cpystrn(cp_all, elts[j].data, elts[j].len+1);
        				*cp_all = JVM_CP_SEP;
        				cp_all++;
        			}
        			*--cp_all = 0;
        			break;
    			}

    		}else {
        		options[i] = (char *)ngx_http_clojure_eval_experssion(mcf, &elts[i], pool, &vlen);
    		}

    		if (options[i] == NULL) {
    			ngx_log_error(NGX_LOG_EMERG, log, 0,
    			                                       "too long expanded jvm_options \"%*s...\" started",
    			                                       10, elts[i].data);
    			ngx_destroy_pool(pool);
    			return NGX_HTTP_CLOJURE_JVM_ERR;
    		}
    	}

    	rc = ngx_http_clojure_init_jvm(jvm_path, (char **)options, len);


    	if (rc != NGX_HTTP_CLOJURE_JVM_OK) {
    		switch (rc) {
    		case NGX_HTTP_CLOJURE_JVM_ERR_LOAD_LIB :
    			ngx_log_error(NGX_LOG_ERR, log, 0, "can not initialize jvm for load dynamic lib, maybe wrong jvm_path!");
    			break;
    		case NGX_HTTP_CLOJURE_JVM_ERR_MALLOC :
    			ngx_log_error(NGX_LOG_ERR, log, 0, "can not malloc options for initializing jvm!");
    			break;
    		default:
    			ngx_log_error(NGX_LOG_ERR, log, 0, "can not initialize jvm!");
    		}
    		ngx_destroy_pool(pool);
    		return rc;
    	}
    	ngx_destroy_pool(pool);
    }

    if (ngx_http_clojure_check_memory_util() != NGX_HTTP_CLOJURE_JVM_OK){
		if (ngx_http_clojure_init_memory_util(ccf, cscf, mcf, log) != NGX_HTTP_CLOJURE_JVM_OK) {
			ngx_log_error(NGX_LOG_ERR, log, 0, "can not initialize jvm memory util");
			return NGX_HTTP_CLOJURE_JVM_ERR_INIT_MEMIDX;
		}
    }

    if (ngx_http_clojure_init_shared_map_util() != NGX_HTTP_CLOJURE_JVM_OK) {
    	ngx_log_error(NGX_LOG_ERR, log, 0, "can not initialize jvm memory util");
    	return NGX_HTTP_CLOJURE_JVM_ERR_INIT_SHAREDMAP;
    }

    return NGX_HTTP_CLOJURE_JVM_OK;
}

static ngx_int_t ngx_http_clojure_init_socket(ngx_http_clojure_main_conf_t  *mcf, ngx_log_t *log) {
	if (ngx_http_clojure_init_socket_util() != NGX_HTTP_CLOJURE_JVM_OK) {
		ngx_log_error(NGX_LOG_ERR, log, 0, "can not initialize jvm socket util");
		return NGX_HTTP_CLOJURE_JVM_ERR_INIT_SOCKETAPI;
	}
	return NGX_HTTP_CLOJURE_JVM_OK;
}


#define ngx_http_clojure_merge_handler(conf, prev, handler)  \
		if (prev->enable_ ## handler)  {  \
			conf->enable_ ## handler = 1; \
			if (prev->handler ## _name.len )  {  \
				ngx_conf_merge_str_value(conf->handler ## _name,  prev->handler ## _name,  "") \
				if (prev->handler ## _name.data == conf->handler ## _name.data)  {  \
					ngx_conf_merge_str_value(conf->handler ## _type, prev->handler ## _type,   "") \
                } \
            } \
            if (prev->handler ## _code.len) { \
            	ngx_conf_merge_str_value(conf->handler ## _code,  prev->handler ## _code,  "") \
				if (prev->handler ## _code.data == conf->handler ## _code.data)  {  \
					ngx_conf_merge_str_value(conf->handler ## _type,  prev->handler ## _type,  "") \
                } \
           } \
		}  \
		/*We find on win64 with vc2010 debug mode if #handler is "content_handler" below result will be false!
		 *      (uintptr_t)# handler != (uintptr_t) "content_handler"
		 *So for we can debug easily on kinds of platforms we use ngx_strcmp instead.
		 * */ \
		if (!conf->handler ##  _type.len && conf->enable_ ## handler && ngx_strcmp( # handler, "content_handler") != 0) {  \
					if (conf->enable_content_handler || conf->content_handler_type.len) {  \
						ngx_conf_merge_str_value(conf->handler ##  _type,  conf->content_handler_type, "clojure"); \
					} \
		} \
		if (!conf->handler ##  _type.len && conf->enable_ ## handler) {  \
					if (prev->enable_ ## handler || prev->handler ##  _type.len) {  \
						ngx_conf_merge_str_value(conf->handler ##  _type,  prev->handler ##  _type, "clojure"); \
					} \
		} \
		if (!conf->handler ##  _type.len && conf->enable_ ## handler) {  \
					if (prev->enable_content_handler || prev->content_handler_type.len) {  \
						ngx_conf_merge_str_value(conf->handler ##  _type,  prev->content_handler_type, "clojure"); \
					}else { \
						ngx_conf_merge_str_value(conf->handler ##  _type,  mcf->jvm_handler_type, "clojure"); \
					}  \
		}



static char* ngx_http_clojure_merge_loc_conf(ngx_conf_t *cf, void *parent, void *child){
	ngx_http_clojure_loc_conf_t *prev = parent;
	ngx_http_clojure_loc_conf_t *conf = child;
	ngx_http_clojure_main_conf_t *mcf = ngx_http_conf_get_module_main_conf(cf,  ngx_http_clojure_module);
	ngx_http_core_loc_conf_t *clcf = ngx_http_conf_get_module_loc_conf(cf, ngx_http_core_module);

	if (conf->always_read_body == NGX_HTTP_CLOJURE_ALWATS_READ_BODY_UNSET) {
    conf->always_read_body = (prev->always_read_body == NGX_HTTP_CLOJURE_ALWATS_READ_BODY_UNSET)
        ? NGX_HTTP_CLOJURE_BEFORE_CONTENT_HANDLER : prev->always_read_body;
  }

	ngx_conf_merge_value(conf->handlers_lazy_init, prev->handlers_lazy_init, 0);
	ngx_conf_merge_value(conf->auto_upgrade_ws, prev->auto_upgrade_ws, 0);
	ngx_conf_merge_size_value(conf->write_page_size, prev->write_page_size, ngx_pagesize);

#if defined(NGX_CLOJURE_BE_SILENT_WITHOUT_JVM)
	if (mcf->jvm_path.len == NGX_CONF_UNSET_SIZE) {
		mcf->enable_access_handler = mcf->enable_body_filter = mcf->enable_content_handler
				= mcf->enable_header_filter = mcf->enable_init_handler
				= mcf->enable_rewrite_handler = 0;
		conf->enable_access_handler = conf->enable_body_filter = conf->enable_content_handler
				= conf->enable_header_filter = conf->enable_rewrite_handler = 0;
		if (clcf->handler == ngx_http_clojure_content_handler) {
			clcf->handler = NULL;
		}
	}else
#endif
	{
		if (clcf->handler == NULL && prev->enable_content_handler) {
			clcf->handler = ngx_http_clojure_content_handler;
		}
	}

	ngx_http_clojure_merge_handler(conf, prev, content_handler);
	ngx_http_clojure_merge_handler(conf, prev, rewrite_handler);
	ngx_http_clojure_merge_handler(conf, prev, access_handler);
	ngx_http_clojure_merge_handler(conf, prev, header_filter);

	ngx_http_clojure_merge_handler(conf, prev, body_filter);

	return NGX_CONF_OK;
}

static ngx_int_t ngx_http_clojure_module_init(ngx_cycle_t *cycle) {

	ngx_core_conf_t  *ccf = (ngx_core_conf_t *) ngx_get_conf(cycle->conf_ctx, ngx_core_module);
	ngx_http_conf_ctx_t *ctx = (ngx_http_conf_ctx_t *)ngx_get_conf(cycle->conf_ctx, ngx_http_module);
	ngx_http_clojure_main_conf_t *mcf;

#if !(NGX_WIN32)
	ngx_uint_t cl = 8;
	ngx_uint_t ssize = 0;
#endif
	ngx_http_clojure_global_cycle = cycle;

	if (ctx == NULL) {
		// No HTTP block in config
		return NGX_OK;
	}
	mcf = ctx->main_conf[ngx_http_clojure_module.ctx_index];

	if (mcf->jvm_path.len == NGX_CONF_UNSET_SIZE) {
		return NGX_OK;
	}

	/*check whether nginx worker processes can access classpath files or not*/
	if (mcf->jvm_cp_check && ngx_http_clojure_check_access_jvm_cp(mcf, ccf, cycle->log) != NGX_OK) {
		return NGX_ERROR;
	}

#if !(NGX_WIN32)
	ssize = 2;
#if defined(NGX_CLOJURE_WORKER_STAT)
	ssize  +=  1 + NGX_MAX_PROCESSES * 3;
#endif
	ngx_http_clojure_shared_memory.size = cl * ssize;
	ngx_http_clojure_shared_memory.name.len = sizeof("nginx_clojure_shared_zone");
	ngx_http_clojure_shared_memory.name.data = (u_char *) "nginx_clojure_shared_zone";
	ngx_http_clojure_shared_memory.log = cycle->log;

    if (ngx_shm_alloc(&ngx_http_clojure_shared_memory) != NGX_OK) {
        return NGX_ERROR;
    }

    ngx_http_clojure_jvm_be_mad_times = (ngx_atomic_t *) ngx_http_clojure_shared_memory.addr;
    ngx_http_clojure_jvm_num = (ngx_atomic_t *) (ngx_http_clojure_shared_memory.addr+ cl);
    *ngx_http_clojure_jvm_be_mad_times = 0;
    *ngx_http_clojure_jvm_num = 1;

#if defined(NGX_CLOJURE_WORKER_STAT)
    ngx_http_clojure_rem_accept_idx = (ngx_atomic_int_t *) (ngx_http_clojure_shared_memory.addr + cl * 2);
    ngx_http_clojure_worker_stats = (ngx_atomic_t *) (ngx_http_clojure_shared_memory.addr + cl * 3);
//    ngx_memzero(ngx_http_clojure_worker_stats, NGX_MAX_PROCESSES * 8 * 2);
#endif

#else
    ngx_http_clojure_jvm_be_mad_times = &ngx_http_clojure_jvm_be_mad_times_ins;
    ngx_http_clojure_jvm_num = &ngx_http_clojure_jvm_num_ins;
#endif
	ngx_log_error(NGX_LOG_NOTICE, cycle->log, 0, NGINX_CLOJURE_VER);

	if (ngx_http_clojure_pipe_init_by_master(ccf->master ? ccf->worker_processes : 1) != NGX_OK) {
		return NGX_ERROR;
	}

	ngx_http_clojure_reload_delay_event.handler = ngx_http_clojure_reload_delay_event_handler;
	ngx_http_clojure_reload_delay_event.log = cycle->log;

	return NGX_OK;
}

static void ngx_http_clojure_module_exit(ngx_cycle_t *cycle) {
#if !(NGX_WIN32)
	ngx_shm_free(&ngx_http_clojure_shared_memory);
#else
	ngx_http_clojure_jvm_be_mad_times_ins = 0;
	ngx_http_clojure_jvm_num_ins = 1;
#endif
	ngx_http_clojure_global_cycle = NULL;
	ngx_http_clojure_pipe_exit_by_master();
}

#if defined(_WIN32) || defined(WIN32)
static ngx_int_t ngx_http_clojure_quit_master(ngx_cycle_t *cycle) {
	u_long n;
	size_t len;
	ngx_exec_ctx_t ctx;
	char file[MAX_PATH + 1];
	char args[1024];
	STARTUPINFO si;
	PROCESS_INFORMATION pi;

	n = GetModuleFileName(NULL, file, MAX_PATH);

	if (n == 0) {
		ngx_log_error(NGX_LOG_ALERT, cycle->log, ngx_errno, "GetModuleFileName() failed");
		return NGX_INVALID_PID;
	}

	file[n] = '\0';

	ngx_log_debug1(NGX_LOG_DEBUG_CORE, cycle->log, 0, "GetModuleFileName: \"%s\"", file);

	ctx.path = file;
	ctx.name = "worker";
	ctx.args = GetCommandLine();
	ctx.argv = NULL;
	ctx.envp = NULL;
	len = strlen(ctx.args);

	if (len > 1024 - strlen(" -s stop") - 1) {
		ngx_log_error(NGX_LOG_ALERT, cycle->log, ngx_errno, "command line is too long for execute");
		return NGX_ERROR;
	}

	strncpy(args, ctx.args, len);
	strcpy(args+len, " -s stop");
	ctx.args = args;

	ngx_log_error(NGX_LOG_CRIT, cycle->log, ngx_errno,
	                      "ngx_http_clojure_quit_master, file (\"%s\"), arg (\"%s\")", ctx.path, ctx.args);

    ngx_memzero(&si, sizeof(STARTUPINFO));
    si.cb = sizeof(STARTUPINFO);

    ngx_memzero(&pi, sizeof(PROCESS_INFORMATION));

    if (CreateProcess(ctx.path, ctx.args,
                      NULL, NULL, 0, CREATE_NO_WINDOW, NULL, NULL, &si, &pi)
        == 0){
        ngx_log_error(NGX_LOG_CRIT, cycle->log, ngx_errno,
                      "ngx_http_clojure_quit_master (\"%s\") failed", ngx_argv[0]);
        return NGX_ERROR;
    }
    return NGX_OK;
}
#endif

static void ngx_http_clojure_process_exit(ngx_cycle_t *cycle) {
	ngx_http_clojure_main_conf_t *mcf = ngx_http_cycle_get_module_main_conf(cycle, ngx_http_clojure_module);

	if (mcf == NULL) {
		return;
	}

	if (mcf->jvm_disable_all) {
		return;
	}

	if (mcf->enable_exit_handler
			&& ngx_http_clojure_init_clojure_script(NGX_HTTP_INIT_PROCESS_PHASE, "exit-process", &mcf->jvm_handler_type, &mcf->jvm_exit_handler_name,
					&mcf->jvm_exit_handler_code, NULL, &mcf->jvm_exit_handler_id, cycle->log) == NGX_HTTP_CLOJURE_JVM_OK) {
		if (mcf->jvm_exit_handler_id >= 0) {
			(void)ngx_http_clojure_eval(mcf->jvm_exit_handler_id, 0, 0);
		}
	}

	ngx_http_clojure_destroy_memory_util(cycle->log);
	ngx_http_clojure_destory_socket_util();
	ngx_http_clojure_close_jvm();
}

static ngx_int_t ngx_http_clojure_init_locations_handlers_in_tree(ngx_http_location_tree_node_t *lt) {
	ngx_int_t rc;

	if (lt == NULL) {
		return NGX_OK;
	}

	rc = lt->exact ? ngx_http_clojure_init_locations_handlers_helper(lt->exact) : ngx_http_clojure_init_locations_handlers_helper(lt->inclusive);
	if (rc != NGX_OK) {
		return NGX_ERROR;
	}

	rc = ngx_http_clojure_init_locations_handlers_in_tree(lt->left);
	if (rc != NGX_OK) {
		return NGX_ERROR;
	}

	rc = ngx_http_clojure_init_locations_handlers_in_tree(lt->tree);
	if (rc != NGX_OK) {
		return NGX_ERROR;
	}

	rc = ngx_http_clojure_init_locations_handlers_in_tree(lt->right);
	if (rc != NGX_OK) {
		return NGX_ERROR;
	}

	return NGX_OK;
}

#define ngx_http_clojure_init_handler_script(lcf, phase, handler) \
		if (lcf->enable_ ## handler \
							&& ngx_http_clojure_init_clojure_script(phase, # handler, &lcf->handler ## _type, &lcf->handler ## _name, \
									&lcf->handler ## _code, lcf->handler ## _properties, &lcf->handler ## _id, ngx_http_clojure_global_cycle->log) != NGX_HTTP_CLOJURE_JVM_OK) { \
						return NGX_ERROR; \
		}

static ngx_int_t ngx_http_clojure_init_locations_handlers_helper(ngx_http_core_loc_conf_t *clcf) {

	ngx_http_clojure_loc_conf_t *lcf;

	if (clcf != NULL && clcf->loc_conf != NULL) {
		lcf =  clcf->loc_conf[ngx_http_clojure_module.ctx_index];
		ngx_log_debug1(NGX_LOG_NOTICE, clcf->error_log, 0, "init location: %s", clcf->name.data );
		if (!lcf->handlers_lazy_init) {
			ngx_http_clojure_init_handler_script(lcf, NGX_HTTP_REWRITE_PHASE, rewrite_handler);
			ngx_http_clojure_init_handler_script(lcf, NGX_HTTP_ACCESS_PHASE, access_handler);
			ngx_http_clojure_init_handler_script(lcf, NGX_HTTP_CONTENT_PHASE, content_handler);
			ngx_http_clojure_init_handler_script(lcf, NGX_HTTP_HEADER_FILTER_PHASE, header_filter);
			ngx_http_clojure_init_handler_script(lcf, NGX_HTTP_BODY_FILTER_PHASE, body_filter);
		}
	}

#if (NGX_PCRE)
	if (clcf->regex_locations) {
		ngx_http_core_loc_conf_t ** clcfp;
		for (clcfp = clcf->regex_locations; *clcfp; clcfp++) {
			ngx_log_debug1(NGX_LOG_NOTICE, clcf->error_log, 0, "find regex location: %s", (*clcfp)->name.data);
			if (ngx_http_clojure_init_locations_handlers_helper(*clcfp) != NGX_OK) {
				return NGX_ERROR;
			}
		}
	}
#endif

    return ngx_http_clojure_init_locations_handlers_in_tree(clcf->static_locations);
}

static ngx_int_t ngx_http_clojure_init_locations_handlers(ngx_http_core_main_conf_t *cmcf) {
	ngx_http_core_srv_conf_t **cscfp = cmcf->servers.elts;
	ngx_uint_t s;
	for (s = 0; s < cmcf->servers.nelts; s++) {
		ngx_http_core_loc_conf_t *clcf = cscfp[s]->ctx->loc_conf[ngx_http_core_module.ctx_index];
		if (ngx_http_clojure_init_locations_handlers_helper(clcf) != NGX_OK) {
			return NGX_ERROR ;
		}
	}
	return NGX_OK;
}

static ngx_int_t ngx_http_clojure_process_init(ngx_cycle_t *cycle) {
	ngx_http_conf_ctx_t *ctx = (ngx_http_conf_ctx_t *)ngx_get_conf(cycle->conf_ctx, ngx_http_module);
	ngx_int_t rc = 0;

	ngx_http_core_main_conf_t *cmcf;
	ngx_http_core_srv_conf_t *cscf;
	ngx_http_clojure_main_conf_t *mcf;
	ngx_core_conf_t  *ccf = (ngx_core_conf_t *) ngx_get_conf(cycle->conf_ctx, ngx_core_module);
	ngx_int_t jvm_num = 0;

	if (ctx == NULL) {
		// No HTTP block in config
		return NGX_OK;
	}
	cmcf = ctx->main_conf[ngx_http_core_module.ctx_index];
	cscf = ctx->srv_conf[ngx_http_core_module.ctx_index];
	mcf = ctx->main_conf[ngx_http_clojure_module.ctx_index];

	/*Fix issue #64 about proxy cache manger process
	 * We won't initialize jvm unless the current process is worker process or single process*/
	if (ngx_process != NGX_PROCESS_WORKER && ngx_process != NGX_PROCESS_SINGLE) {
		return NGX_OK;
	}

	if (!ngx_http_clojure_is_embeded_by_jse
			&& (mcf->jvm_disable_all || mcf->jvm_path.len == NGX_CONF_UNSET_SIZE) ) {
		return NGX_OK;
	}


#if !(NGX_WIN32)
	if (!ngx_http_clojure_is_embeded_by_jse) {
		ngx_setproctitle("worker process");
	}
#else
	ngx_http_clojure_jvm_be_mad_times = &ngx_http_clojure_jvm_be_mad_times_ins;
	ngx_http_clojure_jvm_num = &ngx_http_clojure_jvm_num_ins;
	/*Initialize a fake shared pool to initialize ngx_slab_max_size (static var in ngx_slab.c)
	 *because we try to avoid Nginx bug on windows where Nginx does not initialize
	 *ngx_slab_max_size correctly with Nginx worker processes*/
	{
      ngx_slab_pool_t *sp = malloc(8192);
	  sp->end = (u_char*)sp + 8192;
	  sp->min_shift = 3;
	  sp->addr = (void*)sp;
	  ngx_slab_init(sp);
	  free(sp);
	}
#endif

	jvm_num = (ngx_int_t)ngx_atomic_fetch_add(ngx_http_clojure_jvm_num, 1);

	if ((ngx_int_t)ngx_atomic_fetch_add(ngx_http_clojure_jvm_be_mad_times, 1) >= ccf->worker_processes) {
		ngx_log_error(NGX_LOG_ERR, cycle->log, 0, "jvm may be mad for wrong options! See hs_err_pid****.log for detail! restarted %d", *ngx_http_clojure_jvm_be_mad_times);
#if defined(_WIN32) || defined(WIN32)
		ngx_terminate = 1;
		if (ngx_process != NGX_PROCESS_SINGLE) {
			ngx_log_error(NGX_LOG_ERR, cycle->log, 0, "we try quit master now!");
			/*We must quit otherwise we'll enter a dead repeatedly case*/
			ngx_http_clojure_quit_master(cycle);
		}
#endif
		return NGX_ERROR;
	}
	{
		ngx_keyval_t *kv = NULL;
		if (mcf->jvm_vars == NULL) {
			mcf->jvm_vars = ngx_array_create(cycle->pool, 1, sizeof(ngx_keyval_t));
		}else {
			ngx_uint_t vi = 0;
			kv = mcf->jvm_vars->elts;
			for (; vi < mcf->jvm_vars->nelts; vi++) {
				if (ngx_strcmp("pno", kv->key.data) == 0) {
					break;
				}
				kv++;
			}
			if (vi == mcf->jvm_vars->nelts) {
				kv = NULL;
			}
		}
		if (kv == NULL) {
			kv = ngx_array_push(mcf->jvm_vars);
		}
		kv->key.data = (u_char*)"pno";
		kv->key.len = ngx_strlen("pno");
		kv->value.data = ngx_pcalloc(cycle->pool, 8);
		ngx_sprintf(kv->value.data, "%d", jvm_num);
		kv->value.len = ngx_strlen(kv->value.data);
	}


    rc = ngx_http_clojure_init_jvm_and_mem(ccf,cscf, mcf, cycle->log);

    if (rc != NGX_HTTP_CLOJURE_JVM_OK){
    	ngx_log_error(NGX_LOG_ERR, cycle->log, 0, "jvm start times %d", *ngx_http_clojure_jvm_be_mad_times);
    	return NGX_ERROR;
    }

    rc = ngx_http_clojure_init_socket(mcf, cycle->log);
    if (rc != NGX_HTTP_CLOJURE_JVM_OK) {
    	return NGX_ERROR;
    }

	if (mcf->enable_init_handler
			&& ngx_http_clojure_init_clojure_script(NGX_HTTP_INIT_PROCESS_PHASE, "init-process", &mcf->jvm_handler_type, &mcf->jvm_init_handler_name,
					&mcf->jvm_init_handler_code, NULL, &mcf->jvm_init_handler_id, cycle->log) != NGX_HTTP_CLOJURE_JVM_OK) {
		return NGX_ERROR;
	}

	if (mcf->enable_exit_handler
				&& ngx_http_clojure_init_clojure_script(NGX_HTTP_EXIT_PROCESS_PHASE, "exit-process", &mcf->jvm_handler_type, &mcf->jvm_exit_handler_name,
						&mcf->jvm_exit_handler_code, NULL, &mcf->jvm_exit_handler_id, cycle->log) != NGX_HTTP_CLOJURE_JVM_OK)  {
		return NGX_ERROR;
	}

	if (ngx_http_clojure_init_locations_handlers(cmcf) != NGX_OK) {
		return NGX_ERROR;
	}

	if (mcf->jvm_init_handler_id >= 0) {
		rc = ngx_http_clojure_eval(mcf->jvm_init_handler_id, 0, 0);
	}

    if (rc > 300) {
    	return NGX_ERROR;
    }

    /*we reset it for nginx normal restart nginx-worker when it crashed normally.*/
    (void)ngx_atomic_fetch_add(ngx_http_clojure_jvm_be_mad_times, -1);

    return NGX_OK;
}

static int ngx_http_clojure_check_little_endian() {
/*
    volatile uint32_t i=0x01234567;
    return (*((uint8_t*)(&i))) == 0x67;
*/
	return ( 1 != htonl(1) );
}

static ngx_int_t ngx_http_clojure_auto_detect_jvm(ngx_conf_t *cf) {
	ngx_http_clojure_main_conf_t *mcf = ngx_http_conf_get_module_main_conf(cf, ngx_http_clojure_module);
	ngx_str_t *elts = mcf->jvm_options->elts;
	int len = mcf->jvm_options->nelts;
	size_t vlen;
	int i;
	ngx_pool_t *pool = ngx_create_pool(40960, cf->log);
	char cmd[40960];
	char result[4096];
	char *option;
	char *p = cmd;
	int c = 0;
	FILE *fd;

	strcpy(p, "java");
	p += sizeof("java") - 1;

	for (i = 0; i < len; i++){
		option = (char *)ngx_http_clojure_eval_experssion(mcf, &elts[i], pool, &vlen);
		if (!ngx_strncmp(option, "-Xbootclasspath", sizeof("-Xbootclasspath") -1)
				|| !ngx_strncmp(option, "-Djava.class.path", sizeof("-Djava.class.path") -1)
				|| !ngx_strncmp(option, "-Djava.ext.dirs", sizeof("-Djava.ext.dirs") -1)) {
			strcpy(p, " ");
			p++;
			ngx_cpystrn((u_char*)p, (u_char*)option, vlen+1);
			p += vlen;
		}
	}
	ngx_destroy_pool(pool);

	if (mcf->jvm_cp != NULL) {
		len = mcf->jvm_cp->nelts;
		elts = mcf->jvm_cp->elts;
		strcpy(p, " -Djava.class.path=");
		p += sizeof(" -Djava.class.path=")-1;
		for (i = 0; i < len; i++) {
			ngx_cpystrn((u_char*)p, elts[i].data, elts[i].len + 1);
			p += elts[i].len;
			*p++ = JVM_CP_SEP;
		}
	}

	strcpy(p, " nginx.clojure.DiscoverJvm");
#if !(NGX_WIN32)
	fd = popen(cmd, "r");
#else
	fd = _popen(cmd, "r");
#endif
	p = result;
	i = 0;
	while (i < (int)sizeof(result)-1 && ((c = fgetc(fd)) != EOF) && c != '\r' && c != '\n') {
		*p++ = c;
		i++;
	}
#if !(NGX_WIN32)
	pclose(fd);
#else
	_pclose(fd);
#endif
	*p = 0;
	p = strrchr(result, '.');
	if (p && (!ngx_strcmp(p, ".so") || !ngx_strcmp(p, ".dll") || !ngx_strcmp(p, ".dylib"))) {
		len = strlen(result);
		mcf->jvm_path.data = ngx_pcalloc(cf->pool, len+1);
		strcpy((char*)mcf->jvm_path.data, result);
		mcf->jvm_path.len = len;
		return NGX_OK;
	}else {
		ngx_log_error(NGX_LOG_ERR, cf->log, 0, "detect jvm error, cmd : %s \n, result : %s", cmd, result);
		return NGX_ERROR;
	}
}


static ngx_int_t ngx_http_clojure_expand_jvm_classpath(ngx_conf_t *cf, ngx_str_t *d, ngx_array_t *cps, ngx_int_t recursive) {
	ngx_dir_t dir;
	ngx_err_t err;
	ngx_str_t *path;
	size_t len;

	if (ngx_open_dir(d, &dir) == NGX_ERROR) {
		err = ngx_errno;

		if (err == NGX_ENOENT || err == NGX_ENOTDIR || err == NGX_ENAMETOOLONG) {
			ngx_conf_log_error(NGX_LOG_EMERG, cf, err, "no such dir: \"%V\"", d);
		} else if (err == NGX_EACCES) {
			ngx_conf_log_error(NGX_LOG_EMERG, cf, err, "no permission to access dir: \"%V\"", d);
		} else {
			ngx_conf_log_error(NGX_LOG_EMERG, cf, err, "can not open dir: \"%V\"", d);
		}
		return NGX_ERROR;
	}

	while (1) {
		ngx_set_errno(0);
		if (ngx_read_dir(&dir) == NGX_ERROR) {
			err = ngx_errno;
			if (err != NGX_ENOMOREFILES) {
				ngx_conf_log_error(NGX_LOG_EMERG, cf, err, ngx_read_dir_n " \"%V\" failed", d);
				return NGX_ERROR;
			}
			break;
		}

		if (ngx_de_name(&dir)[0] == '.') {
			continue;
		}

		len = ngx_de_namelen(&dir);

		ngx_log_debug1(NGX_LOG_DEBUG_HTTP, cf->log, 0,
		                       "jvm cp file: \"%s\"", ngx_de_name(&dir));
		if (recursive && ngx_de_is_dir(&dir)) {
			ngx_str_t tmpd;
			tmpd.data = ngx_de_name(&dir);
			tmpd.len = len;
			if (ngx_http_clojure_expand_jvm_classpath(cf, &tmpd, cps, 0) != NGX_OK) {
				return NGX_ERROR;
			}
		}else {
			path = ngx_array_push(cps);
			path->len = d->len + len;
			path->data = ngx_pnalloc(cps->pool, d->len + len + 1);
			ngx_cpystrn(path->data, d->data, d->len+1);
			ngx_cpystrn(path->data+d->len, ngx_de_name(&dir), len + 1);
		}
	}

	return NGX_OK;
}


#if !(NGX_WIN32)
static int ngx_http_clojure_faccessat(const char *name, ngx_log_t *log) {
	struct stat stats;
	int mode = R_OK;


	if (stat(name, &stats)) {
		return -1;
	}

	if (S_ISDIR(stats.st_mode)) {
		mode |= X_OK;
	}

/*Linux faccessat implementation can incorrectly ignore AT_EACCESS
 * @see https://www.sourceware.org/ml/glibc-bugs/2015-07/msg00118.html
 * MacOSX does not define faccessat*/
#if (NGX_LINUX || NGX_DARWIN)
{
	uid_t uid;
	int granted;

	uid = geteuid();
	if (uid == 0 && ((mode & X_OK) == 0
					  || (stats.st_mode & (S_IXUSR | S_IXGRP | S_IXOTH)))) {
		return 0;
	}
	{
		char tmpbuf[32];
		sprintf(tmpbuf, "%o", stats.st_mode);
		ngx_log_debug2(NGX_LOG_DEBUG_CORE, log, 0, "checking file %s mode %s", name, tmpbuf);
	}


    granted = (
				uid == stats.st_uid ? (unsigned int) (stats.st_mode & (mode << 6)) >> 6 :
				(stats.st_gid == getegid()) ?
						(unsigned int) (stats.st_mode & (mode << 3)) >> 3 : (stats.st_mode & mode));

    if (granted == mode) {
	  return 0;
    }

    ngx_set_errno(EACCES);
    return -1;
}
#else
	return faccessat(AT_FDCWD, name, mode, AT_EACCESS);
#endif
}
#endif

static ngx_int_t ngx_http_clojure_check_access_jvm_cp(ngx_http_clojure_main_conf_t *mcf, ngx_core_conf_t *ccf, ngx_log_t *log) {
	ngx_int_t rc = NGX_OK;
#if !(NGX_WIN32)
	{
		ngx_uint_t i;
		ngx_err_t err;
		ngx_str_t *elts;
		ngx_uid_t ouid = geteuid();
		ngx_gid_t ogid = getegid();
		char *username = ccf->username;
		struct passwd *pw;

		/*TODO: remove this check nwhen we merge -Djava.class.path with jvm_classpath.*/
		if (!mcf->jvm_cp) {
			return rc;
		}

		elts = mcf->jvm_cp->elts;

		ngx_log_debug2(NGX_LOG_DEBUG_CORE, log, 0, "user & group %ud:%ud", ccf->user, ccf->group);

		if (ouid == 0) {
			ngx_log_debug2(NGX_LOG_DEBUG_CORE, log, 0, "seteuid %ud:%ud", ccf->user, ccf->group);
			if (setegid(ccf->group) != 0) {
				err = ngx_errno;
				ngx_log_error(NGX_LOG_EMERG, log, err, "setegid error when check access jvm classpath  by group \"%ud\"", ccf->group);
				return NGX_ERROR;
			}
			if (seteuid(ccf->user) != 0) {
				err = ngx_errno;
				ngx_log_error(NGX_LOG_EMERG, log, err, "seteuid error when check access jvm classpath by os user \"%s\"", username);
				return NGX_ERROR;
			}
			ngx_log_debug2(NGX_LOG_DEBUG_CORE, log, 0, "geteuid now %ud:%ud", geteuid(), getegid());
		}else if (ccf->user == (uid_t) NGX_CONF_UNSET_UINT) {
			pw = getpwuid (ouid);
			username = pw->pw_name;
		}

		for (i = 0; i < mcf->jvm_cp->nelts; i++) {
			ngx_log_debug2(NGX_LOG_DEBUG_CORE, log, 0, "checking %V, nginx user:%s", &elts[i], username);
			if (ngx_http_clojure_faccessat((char *)elts[i].data, log) != 0) {
				err = ngx_errno;
				ngx_log_error(NGX_LOG_EMERG, log, err, "check access jvm classpath file \"%V\" failed by os user \"%s\"", &elts[i], username);
				rc = NGX_ERROR;

				if (err == EACCES) {
					ngx_log_error(NGX_LOG_EMERG, log, 0,
							"it is caused by os user \"%s\" has no direct access permission, "
									"or search permission (viz. x-permission for a directory) is denied "
									"for one of the directories in the path prefix of pathname", username);
				}
				break;
			}
		}

		if (ouid == 0) {
			if (setegid(ogid) != 0) {
				err = ngx_errno;
				ngx_log_error(NGX_LOG_EMERG, log, err, "setegid error when restore gid to \"%ud\"",
						ogid);
				return NGX_ERROR;
			}
			if (seteuid(ouid) != 0) {
				err = ngx_errno;
				ngx_log_error(NGX_LOG_EMERG, log, err, "seteuid error when restore user to\"%ud\"",
						ouid);
				return NGX_ERROR;
			}
			ngx_log_debug2(NGX_LOG_DEBUG_CORE, log, 0, "restore uid %ud:%ud", geteuid(), getegid());
		}
	}
#endif
	return rc;
}

static char* ngx_http_clojure_set_jvm_classpath(ngx_conf_t *cf, ngx_command_t *cmd, void *conf) {
	ngx_http_clojure_main_conf_t *mcf = conf;
	ngx_str_t *value = 1 + (ngx_str_t *)cf->args->elts;
	u_char *start = value->data;
	u_char *pos = start;
	ngx_str_t dir;
	size_t evlen;

	mcf->jvm_cp_is_set = 1;

	if (ngx_strnstr(start, "#{", value->len) != NULL) {
		value->data = pos = start = ngx_http_clojure_eval_experssion(mcf, value, cf->pool, &evlen);
		if (value->data == NULL) {
			ngx_conf_log_error(NGX_LOG_EMERG, cf, 0, "jvm_classpath is too long to expend:  \"%*s...\" started", 10, value->data);
			return NGX_CONF_ERROR;
		}
		value->len = evlen;
	}else {
		evlen = value->len;
	}
	mcf->jvm_cp = ngx_array_create(cf->pool, 1, sizeof(ngx_str_t));
	if (mcf->jvm_cp == NULL) {
		return NGX_CONF_ERROR;
	}

	do {
		pos++;
		if (*pos == JVM_CP_SEP || pos == value->data+evlen) {
			if (pos[-1] == '*') {
				dir.data = start;
				dir.len = pos - start - 1;
				dir.data[dir.len] = 0;
				if (ngx_http_clojure_expand_jvm_classpath(cf, &dir, mcf->jvm_cp, 0) != NGX_OK) {
					return NGX_CONF_ERROR;
				}
			}else {
				ngx_str_t *cp = ngx_array_push(mcf->jvm_cp);
				cp->data = start;
				cp->len = pos - start;
				*pos = 0;
			}
			start = ++pos;
		}
	}while(pos < value->data+evlen);

	return NGX_CONF_OK;
}


static ngx_int_t ngx_http_clojure_postconfiguration(ngx_conf_t *cf) {
	ngx_http_core_main_conf_t *cmcf = ngx_http_conf_get_module_main_conf(cf, ngx_http_core_module);
	ngx_http_clojure_main_conf_t *mcf = ngx_http_conf_get_module_main_conf(cf, ngx_http_clojure_module);
	ngx_http_handler_pt *h;

	ngx_http_clojure_is_little_endian = ngx_http_clojure_check_little_endian();

	if (mcf->jvm_cp_check == NGX_CONF_UNSET) {
		mcf->jvm_cp_check = 1;
	}

	if (mcf->jvm_options == NGX_CONF_UNSET_PTR) {
		mcf->jvm_options = ngx_array_create(cf->pool, 1, sizeof(ngx_str_t));
	}

	if (mcf->max_balanced_tcp_connections > 0) {
		ngx_http_clojure_reset_listening_backlog(cf);
	}


	if ((mcf->enable_access_handler | mcf->enable_body_filter | mcf->enable_content_handler
			| mcf->enable_header_filter | mcf->enable_init_handler | mcf->enable_rewrite_handler) == 0) {
		mcf->jvm_disable_all = 1;
		return NGX_OK;
	}

	if (mcf->jvm_path.len == NGX_CONF_UNSET_SIZE) {
		mcf->jvm_disable_all = 1;
#if defined(NGX_CLOJURE_BE_SILENT_WITHOUT_JVM)
		return NGX_OK;
#else
		ngx_log_error(NGX_LOG_ERR, cf->log, 0, "no jvm_path configured!");
		return NGX_ERROR ;
#endif
	}

	if (!ngx_http_clojure_is_embeded_by_jse && !mcf->jvm_cp_is_set) {
		ngx_log_error(NGX_LOG_ERR, cf->log, 0, "no jvm classpath configured!");
		return NGX_ERROR ;
	}

	if (!ngx_http_clojure_is_embeded_by_jse && !ngx_strcmp(mcf->jvm_path.data, "auto")) {
		if (ngx_http_clojure_auto_detect_jvm(cf) != NGX_OK) {
			ngx_log_error(NGX_LOG_ERR, cf->log, 0, "can not find installed JRE/JDK");
			return NGX_ERROR;
		}
	}


	if (mcf->enable_rewrite_handler) {
		h = ngx_array_push(&cmcf->phases[NGX_HTTP_REWRITE_PHASE].handlers);
		if (h == NULL) {
			ngx_log_error(NGX_LOG_ERR, cf->log, 0, "can not register nginx clojure rewrite handler");
			return NGX_ERROR;
		}

		*h = ngx_http_clojure_rewrite_handler;
	}

	if (mcf->enable_access_handler) {
		h = ngx_array_push(&cmcf->phases[NGX_HTTP_ACCESS_PHASE].handlers);
		if (h == NULL) {
			ngx_log_error(NGX_LOG_ERR, cf->log, 0, "can not register nginx clojure access handler");
			return NGX_ERROR;
		}

		*h = ngx_http_clojure_access_handler;
	}

	if (mcf->enable_header_filter) {
	    ngx_http_clojure_next_header_filter = ngx_http_top_header_filter;
	    ngx_http_top_header_filter = ngx_http_clojure_header_filter;
	}

	if (mcf->enable_body_filter || mcf->enable_header_filter) {
		ngx_http_clojure_next_body_filter = ngx_http_top_body_filter;
		ngx_http_top_body_filter = ngx_http_clojure_body_filter;
	}

	return NGX_OK;
}



static void ngx_http_clojure_client_body_handler(ngx_http_request_t *r) {
	ngx_http_clojure_loc_conf_t  *lcf = ngx_http_get_module_loc_conf(r, ngx_http_clojure_module);
	ngx_http_clojure_module_ctx_t *ctx = ngx_http_get_module_ctx(r, ngx_http_clojure_module);
	ngx_int_t handler_id;
	int rc = NGX_DECLINED;
	int rewrite_phase = 0;
	ctx->client_body_done = 1;

	if (!ctx->async_body_read) {
/*	if (ctx->phrase == NGX_HTTP_REWRITE_PHASE) {
			r->write_event_handler = ngx_http_core_run_phases;
		}*/
		r->main->count --;
		ctx->phase = -1;
		return;
	}

	ctx->async_body_read = 0;

	if (ctx->phase == NGX_HTTP_REWRITE_PHASE) {
		handler_id = lcf->rewrite_handler_id;
		rewrite_phase = 1;
/*	r->write_event_handler = ngx_http_core_run_phases;*/
	}else {
		handler_id = lcf->content_handler_id;
	}

	if (handler_id >= 0)  {
		rc = ngx_http_clojure_eval(handler_id, r, 0);
		ngx_log_debug2(NGX_LOG_DEBUG_HTTP, ngx_http_clojure_global_cycle->log, 0, "ngx clojure rewrite (body done callback) request: %" PRIu64 ", rc: %d", (jlong)(uintptr_t)r, rc);
	}

	if (rewrite_phase) {
		r->main->count --;
		if (rc != NGX_DONE) {
			ctx->phase = ~ctx->phase;
/*		r->write_event_handler(r);*/
			ctx->phase_rc = rc;
			ngx_http_core_run_phases(r);
		}
	}else {
		ngx_http_finalize_request (r , rc);
	}
}



static ngx_int_t ngx_http_clojure_content_handler(ngx_http_request_t * r) {
    ngx_int_t     rc;
    ngx_http_clojure_loc_conf_t  *lcf;
    ngx_http_clojure_module_ctx_t *ctx;

    lcf = ngx_http_get_module_loc_conf(r, ngx_http_clojure_module);

    ngx_http_clojure_init_handler_script(lcf, NGX_HTTP_CONTENT_PHASE, content_handler);

	if ((ctx = ngx_http_get_module_ctx(r, ngx_http_clojure_module)) == NULL) {
			ctx = ngx_palloc(r->pool, sizeof(ngx_http_clojure_module_ctx_t));
			if (ctx == NULL) {
				ngx_log_error(NGX_LOG_ERR, r->connection->log, 0, "OutOfMemory of create ngx_http_clojure_module_ctx_t");
				return NGX_HTTP_INTERNAL_SERVER_ERROR;
			}

			ngx_http_clojure_init_ctx(ctx, -1, r);
			ngx_http_set_ctx(r, ctx, ngx_http_clojure_module);
	}else {
	   ctx->hijacked_or_async = 0;
	}

    if (lcf->always_read_body == NGX_HTTP_CLOJURE_BEFORE_CONTENT_HANDLER
        && (r->method & (NGX_HTTP_POST | NGX_HTTP_PUT | NGX_HTTP_PATCH))) {
    	if (!ctx->client_body_done) {/*maybe done by rewrite handler*/
    		r->request_body_in_single_buf = 1;
    		r->request_body_in_clean_file = 1;
    		r->request_body_in_persistent_file = 1;

    		rc = ngx_http_read_client_request_body(r, ngx_http_clojure_client_body_handler);
        	if (rc == NGX_ERROR || rc >= NGX_HTTP_SPECIAL_RESPONSE) {
        		return rc;
        	}

        	if (rc == NGX_AGAIN) {
        		ctx->async_body_read  = 1;
        		return NGX_DONE;
        	}
    	}
    }else {

    	if (lcf->auto_upgrade_ws) {
    		rc = ngx_http_clojure_websocket_upgrade(r);
    		if (rc != NGX_OK) {
    			return rc;
    		}
    	}

    	if (!(r->method & (NGX_HTTP_POST | NGX_HTTP_PUT | NGX_HTTP_PATCH))) {
        rc = ngx_http_discard_request_body(r);
        if (rc != NGX_OK && rc != NGX_AGAIN) {
           return rc;
        }
    	}

    }

    rc = ngx_http_clojure_eval(lcf->content_handler_id, r, 0);

    if (ctx->hijacked_or_async && (ngx_http_clojure_reload_delay_event.data = (char*)ngx_http_clojure_reload_delay_event.data + 1) == (void*)1) {
      ngx_add_timer(&ngx_http_clojure_reload_delay_event, ngx_current_msec >> 1);
    }

    return rc;
}



static ngx_int_t ngx_http_clojure_rewrite_handler(ngx_http_request_t *r) {
	ngx_int_t rc;
	ngx_http_clojure_module_ctx_t *ctx;
	ngx_http_clojure_loc_conf_t  *lcf = ngx_http_get_module_loc_conf(r, ngx_http_clojure_module);

	ngx_http_clojure_get_ctx(r, ctx);
	ngx_http_clojure_init_handler_script(lcf, NGX_HTTP_REWRITE_PHASE, rewrite_handler);

	/*Once alwarys_read_body enabled, we want to let it  work even if there no java/groovy/clojure rewrite handler*/
	if (lcf->always_read_body == NGX_HTTP_CLOJURE_BEFORE_REWRITE_HANDLER) {
		if (ctx== NULL) {
			if ( ngx_http_clojure_prepare_server_header(r) != NGX_OK ) {
				ngx_log_error(NGX_LOG_ERR, r->connection->log, 0, "ngx_http_clojure_prepare_server_header error");
				return NGX_HTTP_INTERNAL_SERVER_ERROR;
			}
			ctx = ngx_palloc(r->pool, sizeof(ngx_http_clojure_module_ctx_t));
			if (ctx == NULL) {
				ngx_log_error(NGX_LOG_ERR, r->connection->log, 0, "OutOfMemory of create ngx_http_clojure_module_ctx_t");
				return NGX_HTTP_INTERNAL_SERVER_ERROR;
			}

			ngx_http_clojure_init_ctx(ctx, NGX_HTTP_REWRITE_PHASE, r);
			ngx_http_set_ctx(r, ctx, ngx_http_clojure_module);

			if (!ctx->client_body_done) {
				r->request_body_in_single_buf = 1;
				r->request_body_in_clean_file = 1;
				r->request_body_in_persistent_file = 1;
				rc = ngx_http_read_client_request_body(r, ngx_http_clojure_client_body_handler);
		    	if (rc >= NGX_HTTP_SPECIAL_RESPONSE || rc == NGX_ERROR) {
		    		return rc;
		    	}
		    	if (rc == NGX_AGAIN) {
			    	ctx->async_body_read = 1;
			    	return NGX_DONE;
		    	}
			}
		}
	}

	if (!lcf->enable_rewrite_handler || (lcf->rewrite_handler_code.len == 0 && lcf->rewrite_handler_name.len == 0)) {
		if (ctx != NULL && ctx->phase == ~NGX_HTTP_REWRITE_PHASE) {
			ctx->phase = -1;
					ngx_log_debug2(NGX_LOG_DEBUG_HTTP, ngx_http_clojure_global_cycle->log, 0,
							"ngx clojure rewrite (enter again but without real nginx-clojure rewriter) request: %" PRIu64 ", rc: %d", (jlong )(uintptr_t )r, NGX_DECLINED);
		}
		return NGX_DECLINED;
	}

	if (ctx == NULL) {
		if ( ngx_http_clojure_prepare_server_header(r) != NGX_OK ) {
			ngx_log_error(NGX_LOG_ERR, r->connection->log, 0, "ngx_http_clojure_prepare_server_header error");
			return NGX_HTTP_INTERNAL_SERVER_ERROR;
		}
		ctx = ngx_palloc(r->pool, sizeof(ngx_http_clojure_module_ctx_t));
		if (ctx == NULL) {
			ngx_log_error(NGX_LOG_ERR, r->connection->log, 0, "OutOfMemory of create ngx_http_clojure_module_ctx_t");
			return NGX_HTTP_INTERNAL_SERVER_ERROR;
		}

		ngx_http_clojure_init_ctx(ctx, NGX_HTTP_REWRITE_PHASE, r);
		ngx_http_set_ctx(r, ctx, ngx_http_clojure_module);
		rc = ngx_http_clojure_eval(lcf->rewrite_handler_id, r, 0);

    if (rc == NGX_DONE && (ngx_http_clojure_reload_delay_event.data = (char*)ngx_http_clojure_reload_delay_event.data + 1) == (void*)1) {
      ngx_add_timer(&ngx_http_clojure_reload_delay_event, ngx_current_msec >> 1);
    }

		if (rc != NGX_DONE) {
			ctx->phase = -1;
		}
		ngx_log_debug2(NGX_LOG_DEBUG_HTTP, ngx_http_clojure_global_cycle->log, 0, "ngx clojure rewrite (null ctx) request: %" PRIu64 ", rc: %d", (jlong)(uintptr_t)r, rc);
		return rc;
	}else if (++ ctx->handled_couter > 64) { /*reach dead cycle*/
		ngx_log_error(NGX_LOG_ERR, r->connection->log, 0, "too much times by rewrite/access handler %d", ctx->handled_couter);
		ctx->phase = -1;
		return NGX_HTTP_INTERNAL_SERVER_ERROR;
	}else if (ctx->phase == NGX_HTTP_REWRITE_PHASE) { /*enter again but we not finished*/
		ngx_log_debug2(NGX_LOG_DEBUG_HTTP, ngx_http_clojure_global_cycle->log, 0, "ngx clojure rewrite (enter again but we not finished) request: %" PRIu64 ", rc: %d", (jlong)(uintptr_t)r, NGX_DECLINED);
		if (r->write_event_handler == ngx_http_core_run_phases) {
			r->write_event_handler = ngx_http_request_empty_handler;
		}
		return NGX_DONE;
	} else if (ctx->phase == ~NGX_HTTP_REWRITE_PHASE) {
		ctx->phase = -1;
		ngx_log_debug2(NGX_LOG_DEBUG_HTTP, ngx_http_clojure_global_cycle->log, 0,
				"ngx clojure rewrite (enter again) request: %" PRIu64 ", rc: %d", (jlong )(uintptr_t )r, NGX_DECLINED);
		return ctx->phase_rc;
	}else {
	  ctx->hijacked_or_async = 0;
		ctx->phase = NGX_HTTP_REWRITE_PHASE;
		rc = ngx_http_clojure_eval(lcf->rewrite_handler_id, r, 0);

    if (rc == NGX_DONE && (ngx_http_clojure_reload_delay_event.data = (char*)ngx_http_clojure_reload_delay_event.data + 1) == (void*)1) {
      ngx_add_timer(&ngx_http_clojure_reload_delay_event, ngx_current_msec >> 1);
    }

		if (rc != NGX_DONE) {
			ctx->phase = -1;
		}
		ngx_log_debug2(NGX_LOG_DEBUG_HTTP, ngx_http_clojure_global_cycle->log, 0, "ngx clojure rewrite (else) request: %" PRIu64 ", rc: %d", (jlong)(uintptr_t)r, rc);
		return rc;
	}
}

static ngx_int_t ngx_http_clojure_access_handler(ngx_http_request_t * r) {
	ngx_int_t rc;
	ngx_http_clojure_module_ctx_t *ctx;
	ngx_http_clojure_loc_conf_t  *lcf = ngx_http_get_module_loc_conf(r, ngx_http_clojure_module);

	ngx_http_clojure_get_ctx(r, ctx);
	ngx_http_clojure_init_handler_script(lcf, NGX_HTTP_ACCESS_PHASE, access_handler);

	if (!lcf->enable_access_handler || (lcf->access_handler_code.len == 0 && lcf->access_handler_name.len == 0)) {
		if (ctx != NULL && ctx->phase == ~NGX_HTTP_ACCESS_PHASE) {
			ctx->phase = -1;
					ngx_log_debug2(NGX_LOG_DEBUG_HTTP, ngx_http_clojure_global_cycle->log, 0,
							"ngx clojure access (enter again but without real nginx-clojure access) request: %" PRIu64 ", rc: %d", (jlong )(uintptr_t )r, NGX_DECLINED);
		}
		return NGX_DECLINED;
	}

	if (ctx == NULL) {
		if ( ngx_http_clojure_prepare_server_header(r) != NGX_OK ) {
			ngx_log_error(NGX_LOG_ERR, r->connection->log, 0, "ngx_http_clojure_prepare_server_header error");
			return NGX_HTTP_INTERNAL_SERVER_ERROR;
		}
		ctx = ngx_palloc(r->pool, sizeof(ngx_http_clojure_module_ctx_t));
		if (ctx == NULL) {
			ngx_log_error(NGX_LOG_ERR, r->connection->log, 0, "OutOfMemory of create ngx_http_clojure_module_ctx_t");
			return NGX_HTTP_INTERNAL_SERVER_ERROR;
		}

		ngx_http_clojure_init_ctx(ctx, NGX_HTTP_ACCESS_PHASE, r);
		ngx_http_set_ctx(r, ctx, ngx_http_clojure_module);
		rc = ngx_http_clojure_eval(lcf->access_handler_id, r, 0);

    if (rc == NGX_DONE && (ngx_http_clojure_reload_delay_event.data = (char*)ngx_http_clojure_reload_delay_event.data + 1) == (void*)1) {
      ngx_add_timer(&ngx_http_clojure_reload_delay_event, ngx_current_msec >> 1);
    }

		if (rc != NGX_DONE) {
			ctx->phase = -1;
		}
		ngx_log_debug2(NGX_LOG_DEBUG_HTTP, ngx_http_clojure_global_cycle->log, 0, "ngx clojure access (null ctx) request: %" PRIu64 ", rc: %d", (jlong)(uintptr_t)r, rc);
		return rc;
	}else if (++ ctx->handled_couter > 64) { /*reach dead cycle*/
		ngx_log_error(NGX_LOG_ERR, r->connection->log, 0, "too much times by  access handler %d", ctx->handled_couter);
		ctx->phase = -1;
		return NGX_HTTP_INTERNAL_SERVER_ERROR;
	}else if (ctx->phase == NGX_HTTP_ACCESS_PHASE) { /*enter again but we not finished*/
		ngx_log_debug2(NGX_LOG_DEBUG_HTTP, ngx_http_clojure_global_cycle->log, 0, "ngx clojure access (enter again but we not finished) request: %" PRIu64 ", rc: %d", (jlong)(uintptr_t)r, NGX_DECLINED);
		if (r->write_event_handler == ngx_http_core_run_phases) {
			r->write_event_handler = ngx_http_request_empty_handler;
		}
		return NGX_DONE;
	} else if (ctx->phase == ~NGX_HTTP_ACCESS_PHASE) {
		ctx->phase = -1;
		ngx_log_debug2(NGX_LOG_DEBUG_HTTP, ngx_http_clojure_global_cycle->log, 0,
				"ngx clojure access (enter again) request: %" PRIu64 ", rc: %d", (jlong )(uintptr_t )r, NGX_DECLINED);
		return ctx->phase_rc;
	}else {
	  ctx->hijacked_or_async = 0;
		ctx->phase = NGX_HTTP_ACCESS_PHASE;
		rc = ngx_http_clojure_eval(lcf->access_handler_id, r, 0);

    if (rc == NGX_DONE && (ngx_http_clojure_reload_delay_event.data = (char*)ngx_http_clojure_reload_delay_event.data + 1) == (void*)1) {
      ngx_add_timer(&ngx_http_clojure_reload_delay_event, ngx_current_msec >> 1);
    }

		if (rc != NGX_DONE) {
			ctx->phase = -1;
		}
		ngx_log_debug2(NGX_LOG_DEBUG_HTTP, ngx_http_clojure_global_cycle->log, 0, "ngx clojure access (else) request: %" PRIu64 ", rc: %d", (jlong)(uintptr_t)r, rc);
		return rc;
	}
}

static ngx_int_t ngx_http_clojure_header_filter(ngx_http_request_t *r) {

    ngx_int_t rc;
    ngx_http_clojure_loc_conf_t  *lcf;
    ngx_http_clojure_module_ctx_t *ctx;
    ngx_int_t  src_phase;

    ngx_http_clojure_get_ctx(r, ctx);
    lcf = ngx_http_get_module_loc_conf(r, ngx_http_clojure_module);

    ngx_http_clojure_init_handler_script(lcf, NGX_HTTP_HEADER_FILTER_PHASE, header_filter);

	if (!lcf->enable_header_filter || (lcf->header_filter_code.len == 0 && lcf->header_filter_name.len == 0)) {
		if (ctx != NULL && ctx->phase == ~NGX_HTTP_HEADER_FILTER_PHASE) {
			ctx->phase = -1;
			ngx_log_debug2(NGX_LOG_DEBUG_HTTP, ngx_http_clojure_global_cycle->log, 0,
							"ngx clojure header filter (enter again but without real nginx-clojure  header filter) request: %" PRIu64 ", rc: %d", (jlong )(uintptr_t )r,  NGX_OK);
		}
		return ngx_http_clojure_next_header_filter(r);
	}

	if ((ctx = ngx_http_get_module_ctx(r, ngx_http_clojure_module)) == NULL) {
		if (ngx_http_clojure_prepare_server_header(r) != NGX_OK) {
			ngx_log_error(NGX_LOG_ERR, r->connection->log, 0, "ngx_http_clojure_prepare_server_header error");
			return NGX_HTTP_INTERNAL_SERVER_ERROR;
		}
		ctx = ngx_palloc(r->pool, sizeof(ngx_http_clojure_module_ctx_t));
		if (ctx == NULL) {
			ngx_log_error(NGX_LOG_ERR, r->connection->log, 0, "OutOfMemory of create ngx_http_clojure_module_ctx_t");
			return NGX_HTTP_INTERNAL_SERVER_ERROR;
		}

		ngx_http_clojure_init_ctx(ctx, -1, r);
		ngx_http_set_ctx(r, ctx, ngx_http_clojure_module);
	}

	if (ctx->phase == ~NGX_HTTP_HEADER_FILTER_PHASE) {
		ctx->phase = -1;
		 /*this case was  issued  by itself to send  user defined error response to client
		  * so we just turn to next filter*/
		return ngx_http_clojure_next_header_filter(r);
	}

	src_phase = ctx->phase;
	ctx->phase = NGX_HTTP_HEADER_FILTER_PHASE;
	/*if under thread pool mode ctx->phase must be copied in java*/
    rc = ngx_http_clojure_eval(lcf->header_filter_id, r, 0);
    ctx->phase = src_phase;

    if (rc == NGX_DONE && (ngx_http_clojure_reload_delay_event.data = (char*)ngx_http_clojure_reload_delay_event.data + 1) == (void*)1) {
      ngx_add_timer(&ngx_http_clojure_reload_delay_event, ngx_current_msec >> 1);
    }

    if (rc == NGX_DONE && !r->header_sent) {
    	ctx->wait_for_header_filter = 1;
    	rc = NGX_OK;
    }

    return rc;

}

static ngx_int_t ngx_http_clojure_body_filter(ngx_http_request_t *r,  ngx_chain_t *chain) {
  ngx_int_t rc;
  ngx_http_clojure_loc_conf_t  *lcf;
	ngx_http_clojure_module_ctx_t *ctx  = ngx_http_get_module_ctx(r, ngx_http_clojure_module);
	ngx_int_t  src_phase;
	ngx_chain_t **ppchain;

	if (ctx && ctx->ignore_next_response) {
		return NGX_OK;
	}

	if (chain == NULL) {
	  return ngx_http_clojure_filter_continue_next_body_filter(r, NULL);
	}

	lcf = ngx_http_get_module_loc_conf(r, ngx_http_clojure_module);

	ngx_http_clojure_init_handler_script(lcf, NGX_HTTP_BODY_FILTER_PHASE, body_filter);
  if (!lcf->enable_body_filter || (lcf->body_filter_code.len == 0 && lcf->body_filter_name.len == 0)) {
    if (ctx != NULL && ctx->phase == ~NGX_HTTP_BODY_FILTER_PHASE) {
      ctx->phase = -1;
      ngx_log_debug2(NGX_LOG_DEBUG_HTTP, ngx_http_clojure_global_cycle->log, 0,
              "ngx clojure body filter (enter again but without real nginx-clojure body filter) request: %" PRIu64 ", rc: %d", (jlong )(uintptr_t )r,  NGX_OK);
    }
    return ngx_http_clojure_filter_continue_next_body_filter(r, chain);
  }

  if ((ctx = ngx_http_get_module_ctx(r, ngx_http_clojure_module)) == NULL) {
    ctx = ngx_palloc(r->pool, sizeof(ngx_http_clojure_module_ctx_t));
    if (ctx == NULL) {
      ngx_log_error(NGX_LOG_ERR, r->connection->log, 0, "OutOfMemory of create ngx_http_clojure_module_ctx_t");
      return NGX_HTTP_INTERNAL_SERVER_ERROR;
    }

    ngx_http_clojure_init_ctx(ctx, -1, r);
    ngx_http_set_ctx(r, ctx, ngx_http_clojure_module);
  }

  if (ctx->phase == ~NGX_HTTP_BODY_FILTER_PHASE) {
    ctx->phase = -1;
     /*this case was  issued  by itself to send  user defined error response to client
      * so we just turn to next filter*/
    return ngx_http_clojure_filter_continue_next_body_filter(r, chain);
  }

  src_phase = ctx->phase;
  ctx->phase = NGX_HTTP_BODY_FILTER_PHASE;

  ppchain = ngx_pcalloc(r->pool, sizeof(ngx_chain_t *));
  ngx_chain_add_copy(r->pool, ppchain, chain);

  /*if under thread pool mode ctx->phase must be copied in java*/
  rc = ngx_http_clojure_eval(lcf->body_filter_id, r, *ppchain);
  ctx->phase = src_phase;

  if (rc == NGX_DONE && (ngx_http_clojure_reload_delay_event.data = (char*)ngx_http_clojure_reload_delay_event.data + 1) == (void*)1) {
    ngx_add_timer(&ngx_http_clojure_reload_delay_event, ngx_current_msec >> 1);
  }

	return rc;
}


static void ngx_http_clojure_reset_listening_backlog(ngx_conf_t *cf) {
	ngx_event_conf_t *ecf;
	ngx_http_core_main_conf_t *cmcf;

	ecf = (ngx_event_conf_t *) ngx_event_get_conf(cf->cycle->conf_ctx, ngx_event_core_module);
	cmcf = (ngx_http_core_main_conf_t *)ngx_http_conf_get_module_main_conf(cf, ngx_http_core_module);

	if (ecf->accept_mutex && cmcf->ports != NULL) {
	    ngx_uint_t             p, a;
	    ngx_array_t *ports = cmcf->ports;
	    ngx_http_conf_port_t  *port = ports->elts;
	    ngx_http_conf_addr_t  *addr;
	    int backlog =  (ecf->connections -2) / 2;

		for (p = 0; p < ports->nelts; p++) {
			addr = port[p].addrs.elts;
			for (a = 0; a < port[p].addrs.nelts; a++) {
				addr[a].opt.backlog =  backlog;
			}
		}
		ngx_log_error(NGX_LOG_NOTICE, cf->cycle->log , 0,
				"reset listening backlog  to %d",  backlog);
	}

}

static char* ngx_http_clojure_set_max_balanced_tcp_connections(ngx_conf_t *cf, ngx_command_t *cmd, void *conf) {

	ngx_event_conf_t *ecf;
	ngx_core_conf_t  *ccf;
	ngx_http_clojure_main_conf_t *mcf;
	ngx_int_t workers;
	ngx_int_t worker_connections;
	char *set_rc;

	ecf = (ngx_event_conf_t *) ngx_event_get_conf(cf->cycle->conf_ctx, ngx_event_core_module);
	ccf = (ngx_core_conf_t *) ngx_get_conf(cf->cycle->conf_ctx, ngx_core_module);
	mcf = conf;
	workers = 1;


	if ( (set_rc = ngx_conf_set_num_slot(cf, cmd, conf)) != NGX_CONF_OK) {
		return set_rc;
	}

	if (ccf->worker_processes > 1 && ccf->master) {
		workers = ccf->worker_processes;
	}else {
		mcf->max_balanced_tcp_connections = -1;
		return NGX_CONF_OK;
	}

#if defined(NGX_CLOJURE_WORKER_STAT)
	ngx_max_balanced_connection_per_worker = worker_connections = mcf->max_balanced_tcp_connections / workers;
	ngx_log_error(NGX_LOG_NOTICE, cf->cycle->log , 0,
					"ngx_max_balanced_connection_per_worker : %d",  ngx_max_balanced_connection_per_worker);
#else
	/**
	 * When the number of active connections reach 7/8 of free_connections,
	 * the worker will temporarily not accept connections and give the opportunities for other workers,
	 * so the following formula would force nginx's worker not greedy and let less nginx worker to be idle
	 * when all connections are keep-alved http connections.
	 *             (worker_connections - 2) / 8 * 7 * number_of_workers = max_connections
	 * Note that the above are integer multiplication and division operations. (worker_connections - 2) is
	 * because each nginx worker uses two unix pipe, recipient connection is allocated from the entire
	 * connections. one pipe is used by nginx control channel the other is used by nginx-clojure posting
	 * events under thread pool mode.
	 */
	worker_connections = (mcf->max_balanced_tcp_connections * 8) /  (7 * workers) + 2;

	while ( (worker_connections - 2) / 8 * 7 * workers <  mcf->max_balanced_tcp_connections) {
		worker_connections++;
	}

	if (worker_connections < 32) {
		mcf->max_balanced_tcp_connections = 32 * workers * 7 / 8;
		ngx_log_error(NGX_LOG_ERR, cf->log , 0,
				"max_balanced_tcp_connections is too small , it is set to : 32 x workers_processes x 7 / 8 =  %d",
				mcf->max_balanced_tcp_connections);
		worker_connections = 34;
	}
	ngx_log_error(NGX_LOG_NOTICE, cf->cycle->log , 0,
			"reset worker_connections to %d",  worker_connections);
	ecf->connections = worker_connections;
	cf->cycle->connection_n = worker_connections;
#endif

	return NGX_CONF_OK;
}


static char* ngx_http_clojure_set_str_slot_and_enable_init_handler_tag(ngx_conf_t *cf, ngx_command_t *cmd, void *conf) {
	ngx_http_clojure_main_conf_t *mcf = conf;
	mcf->enable_init_handler = 1;
	return ngx_conf_set_str_slot(cf, cmd, conf);
}

static char* ngx_http_clojure_set_str_slot_and_enable_exit_handler_tag(ngx_conf_t *cf, ngx_command_t *cmd, void *conf) {
	ngx_http_clojure_main_conf_t *mcf = conf;
	mcf->enable_exit_handler = 1;
	return ngx_conf_set_str_slot(cf, cmd, conf);
}

static char* ngx_http_clojure_set_str_slot_and_enable_content_handler_tag(ngx_conf_t *cf, ngx_command_t *cmd, void *conf) {
	ngx_http_core_loc_conf_t * clcf;
	ngx_http_clojure_loc_conf_t *lcf = conf;
	ngx_http_clojure_main_conf_t *mcf = ngx_http_conf_get_module_main_conf(cf, ngx_http_clojure_module);
	clcf = ngx_http_conf_get_module_loc_conf(cf, ngx_http_core_module);
	clcf->handler = ngx_http_clojure_content_handler;
	mcf->enable_content_handler = lcf->enable_content_handler  = 1;
	return ngx_conf_set_str_slot(cf, cmd, conf);
}


static char* ngx_http_clojure_set_str_slot_and_enable_rewrite_handler_tag(ngx_conf_t *cf, ngx_command_t *cmd, void *conf) {
	ngx_http_clojure_loc_conf_t *lcf = conf;
	ngx_http_clojure_main_conf_t *mcf = ngx_http_conf_get_module_main_conf(cf, ngx_http_clojure_module);
	mcf->enable_rewrite_handler = lcf->enable_rewrite_handler   = 1;
	return ngx_conf_set_str_slot(cf, cmd, conf);
}

static char* ngx_http_clojure_set_str_slot_and_enable_header_filter_tag(ngx_conf_t *cf, ngx_command_t *cmd, void *conf) {
	ngx_http_clojure_loc_conf_t *lcf = conf;
	ngx_http_clojure_main_conf_t *mcf = ngx_http_conf_get_module_main_conf(cf, ngx_http_clojure_module);
	mcf->enable_header_filter = lcf->enable_header_filter =  1;
	return ngx_conf_set_str_slot(cf, cmd, conf);
}


static char* ngx_http_clojure_set_str_slot_and_enable_body_filter_tag(ngx_conf_t *cf, ngx_command_t *cmd, void *conf) {
	ngx_http_clojure_loc_conf_t *lcf = conf;
	ngx_http_clojure_main_conf_t *mcf = ngx_http_conf_get_module_main_conf(cf, ngx_http_clojure_module);
	mcf->enable_body_filter = lcf->enable_body_filter =  1;
	return ngx_conf_set_str_slot(cf, cmd, conf);
}

static char* ngx_http_clojure_set_str_slot_and_enable_access_handler_tag(ngx_conf_t *cf, ngx_command_t *cmd, void *conf) {
	ngx_http_clojure_loc_conf_t *lcf = conf;
	ngx_http_clojure_main_conf_t *mcf = ngx_http_conf_get_module_main_conf(cf, ngx_http_clojure_module);
	mcf->enable_access_handler = lcf->enable_access_handler =  1;
	return ngx_conf_set_str_slot(cf, cmd, conf);
}

static char* ngx_http_clojure_set_always_read_body(ngx_conf_t *cf, ngx_command_t *cmd, void *conf) {
	ngx_http_clojure_loc_conf_t *lcf = conf;
	ngx_http_clojure_main_conf_t *mcf = ngx_http_conf_get_module_main_conf(cf, ngx_http_clojure_module);
  ngx_str_t *value;

  if (lcf->always_read_body != NGX_HTTP_CLOJURE_ALWATS_READ_BODY_UNSET) {
      return "is duplicate";
  }

  value = cf->args->elts;

  if (ngx_strcasecmp(value[1].data, (u_char *) "on") == 0
      || ngx_strcasecmp(value[1].data, (u_char *) "before_rewrite_handler") == 0) {
    lcf->always_read_body = NGX_HTTP_CLOJURE_BEFORE_REWRITE_HANDLER;
  } else if (ngx_strcasecmp(value[1].data, (u_char *) "off") == 0) {
    lcf->always_read_body = NGX_HTTP_CLOJURE_BEFORE_NONE;
  } else if (ngx_strcasecmp(value[1].data, (u_char *) "before_content_handler") == 0) {
    lcf->always_read_body = NGX_HTTP_CLOJURE_BEFORE_CONTENT_HANDLER;
  } else {
      ngx_conf_log_error(NGX_LOG_EMERG, cf, 0,
                   "invalid value \"%s\" in \"%s\" directive, "
                   "it must be \"on\" , \"off\" , \"before_rewrite_handler\" or \"before_content_handler\"",
                   value[1].data, cmd->name.data);
      return NGX_CONF_ERROR;
  }

	if (lcf->always_read_body == NGX_HTTP_CLOJURE_BEFORE_REWRITE_HANDLER) {
		mcf->enable_rewrite_handler = 1;
	}
	return NGX_CONF_OK;
}
