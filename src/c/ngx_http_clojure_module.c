/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */

#include <ngx_config.h>
#include <ngx_core.h>
#include <ngx_http.h>
#include "ngx_http_clojure_jvm.h"
#include "ngx_http_clojure_mem.h"

static char* ngx_http_clojure(ngx_conf_t *cf, ngx_command_t *cmd, void *conf);

static void* ngx_http_clojure_create_loc_conf(ngx_conf_t *cf);

static char* ngx_http_clojure_merge_loc_conf(ngx_conf_t *cf, void *parent, void *child);

static ngx_int_t ngx_http_clojure_module_init(ngx_cycle_t *cycle);


typedef struct {
    ngx_array_t *jvm_options;
    ngx_str_t jvm_path;
    ngx_str_t clojure_code;
    ngx_flag_t enable;
    ngx_int_t clojure_code_id;
} ngx_http_clojure_loc_conf_t;

static ngx_command_t ngx_http_clojure_commands[] = {
	{
		ngx_string("clojure"),
		NGX_HTTP_LOC_CONF|NGX_CONF_NOARGS,
		ngx_http_clojure,
		NGX_HTTP_LOC_CONF_OFFSET,
		0,
		NULL
	},
    {
		ngx_string("jvm_options"),
		NGX_HTTP_MAIN_CONF |NGX_HTTP_LOC_CONF | NGX_CONF_TAKE1,
		ngx_conf_set_str_array_slot,
		NGX_HTTP_LOC_CONF_OFFSET,
		offsetof(ngx_http_clojure_loc_conf_t, jvm_options),
		NULL
    },
    {
		ngx_string("jvm_path"),
		NGX_HTTP_MAIN_CONF | NGX_CONF_TAKE1,
		ngx_conf_set_str_slot,
		NGX_HTTP_LOC_CONF_OFFSET,
		offsetof(ngx_http_clojure_loc_conf_t, jvm_path),
		NULL
    },
    {
		ngx_string("clojure_code"),
		NGX_HTTP_LOC_CONF | NGX_CONF_TAKE1,
		ngx_conf_set_str_slot,
		NGX_HTTP_LOC_CONF_OFFSET,
		offsetof(ngx_http_clojure_loc_conf_t, clojure_code),
		NULL
    },

    ngx_null_command
};

static ngx_http_module_t  ngx_http_clojure_module_ctx = {
    NULL,                          /* preconfiguration */
    NULL,                          /* postconfiguration */

    NULL,                          /* create main configuration */
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
    NULL,                          /* init process */
    NULL,                          /* init thread */
    NULL,                          /* exit thread */
    NULL,                          /* exit process */
    NULL,                          /* exit master */
    NGX_MODULE_V1_PADDING
};

static void * ngx_http_clojure_create_loc_conf(ngx_conf_t *cf) {
	ngx_http_clojure_loc_conf_t *conf;
	ngx_http_clojure_global_ngx_conf = cf;
	conf = ngx_pcalloc(cf->pool, sizeof(ngx_http_clojure_loc_conf_t));
	if (conf == NULL){
		return NGX_CONF_ERROR;
	}
	conf->jvm_options = NGX_CONF_UNSET_PTR;
	conf->clojure_code_id = -1;
	//conf->clojure_script = ngx_null_string;
	return conf;
}


static ngx_int_t ngx_http_clojure_init_jvm_and_mem(ngx_http_clojure_loc_conf_t  *lcf, ngx_log_t *log) {
    if (ngx_http_clojure_check_jvm() != NGX_HTTP_CLOJURE_JVM_OK){
    	ngx_str_t *elts = lcf->jvm_options->elts;
    	char * options[NGX_HTTP_CLOJURE_JVM_MAX_OPTS];
    	int i;
    	int len = lcf->jvm_options->nelts;
    	if (len > NGX_HTTP_CLOJURE_JVM_MAX_OPTS) {
    		len = NGX_HTTP_CLOJURE_JVM_MAX_OPTS;
    		ngx_log_error(NGX_LOG_WARN, log, 0, "tow many jvm_options, truncate it to %d", NGX_HTTP_CLOJURE_JVM_MAX_OPTS);
    	}
    	for (i = 0; i < len; i++){
    		options[i] = (char *)elts[i].data;
    	}
    	if (ngx_http_clojure_init_jvm((char *)lcf->jvm_path.data, options, len) != NGX_HTTP_CLOJURE_JVM_OK) {
    		ngx_log_error(NGX_LOG_ERR, log, 0, "can not initialize jvm");
    		return NGX_HTTP_INTERNAL_SERVER_ERROR;
    	}
    }

    if (ngx_http_clojure_check_memory_util() != NGX_HTTP_CLOJURE_JVM_OK){
		if (ngx_http_clojure_init_memory_util() != NGX_HTTP_CLOJURE_JVM_OK) {
			ngx_log_error(NGX_LOG_ERR, log, 0, "can not initialize jvm memory util");
			return NGX_HTTP_INTERNAL_SERVER_ERROR;
		}
    }

    if (lcf != NULL && lcf->clojure_code_id < 0) {
    	if (ngx_http_clojure_register_script(&lcf->clojure_code.data, lcf->clojure_code.len, &(lcf->clojure_code_id)) != NGX_HTTP_CLOJURE_JVM_OK){
    		return NGX_HTTP_INTERNAL_SERVER_ERROR;
    	}
    }
    return NGX_HTTP_CLOJURE_JVM_OK;
}


static char* ngx_http_clojure_merge_loc_conf(ngx_conf_t *cf, void *parent, void *child){
	ngx_http_clojure_loc_conf_t *prev = parent;
	ngx_http_clojure_loc_conf_t *conf = child;
	conf->jvm_options = prev->jvm_options;
	conf->jvm_path = prev->jvm_path;

//	if (conf->enable) {
//		ngx_int_t rc = ngx_http_clojure_init_jvm_and_mem(conf, cf->log);
//		if (rc != NGX_HTTP_CLOJURE_JVM_OK){
//		    return NGX_CONF_ERROR;
//		}
//	}

	return NGX_CONF_OK;
}

ngx_int_t ngx_http_clojure_module_init(ngx_cycle_t *cycle) {
	ngx_log_error(NGX_LOG_NOTICE, cycle->log, 0, NGINX_CLOJURE_VER);
	return NGX_OK;
}

ngx_conf_t *ngx_http_clojure_global_ngx_conf;

static void ngx_http_clojure_client_body_handler(ngx_http_request_t *r) {
	ngx_http_clojure_loc_conf_t  *lcf = ngx_http_get_module_loc_conf(r, ngx_http_clojure_module);
	int rc = ngx_http_clojure_eval(lcf->clojure_code_id, r);
	ngx_http_finalize_request (r , rc);
}

static ngx_int_t ngx_http_clojure_handler(ngx_http_request_t * r) {
    ngx_int_t     rc;
    ngx_http_clojure_loc_conf_t  *lcf;


    //only handle GET or HEAD, we have not support POST now.
//    if (!(r->method & (NGX_HTTP_GET|NGX_HTTP_HEAD))) {
//    	return NGX_HTTP_NOT_ALLOWED;
//    }


//    rc = ngx_http_discard_request_body(r);
//    if (rc != NGX_OK && rc != NGX_AGAIN) {
//        return rc;
//    }


    lcf = ngx_http_get_module_loc_conf(r, ngx_http_clojure_module);
//    ngx_http_core_main_conf_t  *cmcf = ngx_http_get_module_main_conf(r, ngx_http_core_module);


    rc = ngx_http_clojure_init_jvm_and_mem(lcf, r->connection->log);
    if (rc != NGX_HTTP_CLOJURE_JVM_OK){
    	return rc;
    }

    if (r->method & (NGX_HTTP_POST|NGX_HTTP_PUT)) {
    	if (ngx_strcmp("application/x-www-form-urlencoded", r->headers_in.content_type->value.data) != 0) {
    		r->request_body_in_file_only = 1;
    		r->request_body_in_clean_file = 1;
    		r->request_body_in_persistent_file = 1;
    	}
    	rc = ngx_http_read_client_request_body(r, ngx_http_clojure_client_body_handler);
    	if (rc >= NGX_HTTP_SPECIAL_RESPONSE) {
    		return rc;
    	}
    	return NGX_DONE;
    }else {
    	rc = ngx_http_discard_request_body(r);
    	if (rc != NGX_OK && rc != NGX_AGAIN) {
    	        return rc;
    	}
    	rc = ngx_http_clojure_eval(lcf->clojure_code_id, r);
    }
    //for debug
    //ngx_log_error(NGX_LOG_DEBUG, r->connection->log, 0, "finished one request \n==============================================================\n\n");

    return rc;
}

static char* ngx_http_clojure(ngx_conf_t *cf, ngx_command_t *cmd, void *conf) {
	ngx_http_core_loc_conf_t * clcf;
	ngx_http_clojure_loc_conf_t *lcf = conf;

	clcf = ngx_http_conf_get_module_loc_conf(cf, ngx_http_core_module);
	clcf->handler = ngx_http_clojure_handler;
	lcf->enable = 1;

	return NGX_CONF_OK;
}
