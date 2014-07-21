/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */

#include <ngx_config.h>
#include <ngx_core.h>
#include <ngx_http.h>
#include "ngx_http_clojure_jvm.h"
#include "ngx_http_clojure_mem.h"
#include "ngx_http_clojure_socket.h"

ngx_conf_t *ngx_http_clojure_global_ngx_conf;
ngx_cycle_t *ngx_http_clojure_global_cycle;

typedef struct {
    ngx_array_t *jvm_options;
    ngx_str_t jvm_path;
    ngx_int_t jvm_workers;
    ngx_flag_t enable;
    ngx_flag_t always_read_body;
    ngx_str_t handler_type;
    ngx_str_t handler_code;
    ngx_int_t handler_id;
    ngx_str_t handler_name;
    ngx_str_t rewrite_handler_code;
    ngx_int_t rewrite_handler_id;
    ngx_str_t rewrite_handler_name;
} ngx_http_clojure_loc_conf_t;



static char* ngx_http_clojure_set_str_slot_and_enable_tag(ngx_conf_t *cf, ngx_command_t *cmd, void *conf);

static char* ngx_http_clojure(ngx_conf_t *cf, ngx_command_t *cmd, void *conf);

static void* ngx_http_clojure_create_loc_conf(ngx_conf_t *cf);

static char* ngx_http_clojure_merge_loc_conf(ngx_conf_t *cf, void *parent, void *child);

static ngx_int_t ngx_http_clojure_module_init(ngx_cycle_t *cycle);

static ngx_int_t ngx_http_clojure_process_init(ngx_cycle_t *cycle);

#if defined(_WIN32) || defined(WIN32)
static ngx_int_t ngx_http_clojure_quit_master(ngx_cycle_t *cycle);
#endif

static ngx_int_t ngx_http_clojure_postconfiguration(ngx_conf_t *cf);

static ngx_int_t ngx_http_clojure_handler(ngx_http_request_t * r);

static ngx_int_t ngx_http_clojure_rewrite_handler(ngx_http_request_t * r);

static ngx_int_t ngx_http_clojure_init_jvm_and_mem(ngx_http_clojure_loc_conf_t  *lcf, ngx_log_t *log);

static ngx_int_t ngx_http_clojure_init_socket(ngx_http_clojure_loc_conf_t  *lcf, ngx_log_t *log);

static ngx_int_t ngx_http_clojure_init_clojure_script(char *type, ngx_str_t *handler_type, ngx_str_t *handler, ngx_str_t *code, ngx_int_t *pcid , ngx_log_t *log);
static void ngx_http_clojure_client_body_handler(ngx_http_request_t *r);

/* Sadly JNI_CreateJavaVM doesn't always return error code for bad things(e.g initialized memory is too large),
 * it just suspend and then jvm crash signal will be directly catched by nginx master which will re-initialize
 * it again so then jvms will be created and exit repeatedly and madly.
 * We use this memory shared variable to avoid it.*/
static ngx_atomic_t *ngx_http_clojure_jvm_be_mad_times;

#if defined(_WIN32) || defined(WIN32)

#pragma data_seg("ngx_http_clojure_shared_memory")
ngx_atomic_t ngx_http_clojure_jvm_be_mad_times_ins = 0;
#pragma data_seg()

#pragma comment(linker, "/Section:ngx_http_clojure_shared_memory,RWS")

#else

static ngx_shm_t ngx_http_clojure_shared_memory;

#endif




static ngx_command_t ngx_http_clojure_commands[] = {
	{
		ngx_string("clojure"),
		NGX_HTTP_MAIN_CONF | NGX_HTTP_LOC_CONF|NGX_CONF_NOARGS,
		ngx_http_clojure,
		NGX_HTTP_LOC_CONF_OFFSET,
		0,
		NULL
	},
    {
		ngx_string("jvm_options"),
		NGX_HTTP_MAIN_CONF | NGX_CONF_TAKE1,
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
		ngx_string("jvm_workers"),
		NGX_HTTP_MAIN_CONF | NGX_CONF_TAKE1,
		ngx_conf_set_num_slot,
		NGX_HTTP_LOC_CONF_OFFSET,
		offsetof(ngx_http_clojure_loc_conf_t, jvm_workers),
		NULL
    },
    {
		ngx_string("clojure_code"),
		NGX_HTTP_MAIN_CONF | NGX_HTTP_LOC_CONF | NGX_CONF_TAKE1,
		ngx_conf_set_str_slot,
		NGX_HTTP_LOC_CONF_OFFSET,
		offsetof(ngx_http_clojure_loc_conf_t, handler_code),
		NULL
    },

    {
		ngx_string("clojure_rewrite_code"),
		NGX_HTTP_LOC_CONF | NGX_CONF_TAKE1,
		ngx_http_clojure_set_str_slot_and_enable_tag,
		NGX_HTTP_LOC_CONF_OFFSET,
		offsetof(ngx_http_clojure_loc_conf_t, rewrite_handler_code),
		NULL
    },

    {
		ngx_string("handler_type"),
		NGX_HTTP_MAIN_CONF | NGX_HTTP_LOC_CONF | NGX_CONF_TAKE1,
		ngx_conf_set_str_slot,
		NGX_HTTP_LOC_CONF_OFFSET,
		offsetof(ngx_http_clojure_loc_conf_t, handler_type),
		NULL
    },

    {
		ngx_string("handler_name"),
		NGX_HTTP_MAIN_CONF | NGX_HTTP_LOC_CONF | NGX_CONF_TAKE1,
		ngx_http_clojure_set_str_slot_and_enable_tag,
		NGX_HTTP_LOC_CONF_OFFSET,
		offsetof(ngx_http_clojure_loc_conf_t, handler_name),
		NULL
    },

    {
		ngx_string("handler_code"),
		NGX_HTTP_MAIN_CONF | NGX_HTTP_LOC_CONF | NGX_CONF_TAKE1,
		ngx_http_clojure_set_str_slot_and_enable_tag,
		NGX_HTTP_LOC_CONF_OFFSET,
		offsetof(ngx_http_clojure_loc_conf_t, handler_code),
		NULL
    },

    {
		ngx_string("rewrite_handler_name"),
		NGX_HTTP_LOC_CONF | NGX_CONF_TAKE1,
		ngx_conf_set_str_slot,
		NGX_HTTP_LOC_CONF_OFFSET,
		offsetof(ngx_http_clojure_loc_conf_t, rewrite_handler_name),
		NULL
    },

    {
		ngx_string("rewrite_handler_code"),
		NGX_HTTP_LOC_CONF | NGX_CONF_TAKE1,
		ngx_conf_set_str_slot,
		NGX_HTTP_LOC_CONF_OFFSET,
		offsetof(ngx_http_clojure_loc_conf_t, rewrite_handler_code),
		NULL
    },

    {
		ngx_string("always_read_body"),
		NGX_HTTP_LOC_CONF | NGX_CONF_TAKE1,
		ngx_conf_set_flag_slot,
		NGX_HTTP_LOC_CONF_OFFSET,
		offsetof(ngx_http_clojure_loc_conf_t, always_read_body),
		NULL
    },

    ngx_null_command
};

static ngx_http_module_t  ngx_http_clojure_module_ctx = {
    NULL,                          /* preconfiguration */
    ngx_http_clojure_postconfiguration, /* postconfiguration */

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
    ngx_http_clojure_process_init, /* init process */
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
	conf->jvm_path.len = NGX_CONF_UNSET_SIZE;
	conf->jvm_options = NGX_CONF_UNSET_PTR;
	conf->jvm_workers = NGX_CONF_UNSET;
	conf->always_read_body = NGX_CONF_UNSET;
	conf->handler_id = -1;
	conf->rewrite_handler_id = -1;
	return conf;
}

static ngx_int_t ngx_http_clojure_init_clojure_script(char *type, ngx_str_t *handler_type, ngx_str_t *handler, ngx_str_t *code, ngx_int_t *pcid , ngx_log_t *log) {
    if (*pcid < 0 && (code->len > 0 || handler->len > 0)) {
    	if (ngx_http_clojure_register_script(handler_type, handler, code, pcid) != NGX_HTTP_CLOJURE_JVM_OK){
    		ngx_log_error(NGX_LOG_ERR, log, 0, "invalid clojure %s code : %s", type, code->len > 0 ? code->data : handler->data);
    		return NGX_HTTP_INTERNAL_SERVER_ERROR;
    	}
    }
    return NGX_HTTP_CLOJURE_JVM_OK;
}



static ngx_int_t ngx_http_clojure_init_jvm_and_mem(ngx_http_clojure_loc_conf_t  *lcf, ngx_log_t *log) {
	if (ngx_http_clojure_check_jvm() != NGX_HTTP_CLOJURE_JVM_OK){
    	ngx_str_t *elts = lcf->jvm_options->elts;
    	char  **options;
    	char *jvm_path;
    	int i;
    	int len = lcf->jvm_options->nelts;
    	int rc;

    	options = malloc(len * sizeof(char *));
    	if (!options) {
    		ngx_log_error(NGX_LOG_ERR, log, 0, "can not malloc for jvm create options!");
    		return NGX_HTTP_CLOJURE_JVM_ERR_MALLOC;
    	}

    	jvm_path = (char *)lcf->jvm_path.data;

    	for (i = 0; i < len; i++){
    		options[i] = (char *)elts[i].data;
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
    		free(options);
    		return rc;
    	}
    	free(options);
    }

    if (ngx_http_clojure_check_memory_util() != NGX_HTTP_CLOJURE_JVM_OK){
		if (ngx_http_clojure_init_memory_util(lcf->jvm_workers, log) != NGX_HTTP_CLOJURE_JVM_OK) {
			ngx_log_error(NGX_LOG_ERR, log, 0, "can not initialize jvm memory util");
			return NGX_HTTP_CLOJURE_JVM_ERR_INIT_MEMIDX;
		}
    }
    return NGX_HTTP_CLOJURE_JVM_OK;
}

static ngx_int_t ngx_http_clojure_init_socket(ngx_http_clojure_loc_conf_t  *lcf, ngx_log_t *log) {
	if (ngx_http_clojure_init_socket_util() != NGX_HTTP_CLOJURE_JVM_OK) {
		ngx_log_error(NGX_LOG_ERR, log, 0, "can not initialize jvm socket util");
		return NGX_HTTP_CLOJURE_JVM_ERR_INIT_SOCKETAPI;
	}
	return NGX_HTTP_CLOJURE_JVM_OK;
}


static char* ngx_http_clojure_merge_loc_conf(ngx_conf_t *cf, void *parent, void *child){
	ngx_http_clojure_loc_conf_t *prev = parent;
	ngx_http_clojure_loc_conf_t *conf = child;
	conf->jvm_options = prev->jvm_options;
	conf->jvm_path = prev->jvm_path;
	conf->jvm_workers = prev->jvm_workers;
	ngx_conf_merge_value(conf->always_read_body, prev->always_read_body, 0);
	return NGX_CONF_OK;
}

static ngx_int_t ngx_http_clojure_module_init(ngx_cycle_t *cycle) {

	ngx_http_clojure_global_cycle = cycle;

#if !(NGX_WIN32)
	ngx_http_clojure_shared_memory.size = 8;
	ngx_http_clojure_shared_memory.name.len = sizeof("nginx_clojure_shared_zone");
	ngx_http_clojure_shared_memory.name.data = (u_char *) "nginx_clojure_shared_zone";
	ngx_http_clojure_shared_memory.log = cycle->log;

    if (ngx_shm_alloc(&ngx_http_clojure_shared_memory) != NGX_OK) {
        return NGX_ERROR;
    }

    ngx_http_clojure_jvm_be_mad_times = (ngx_atomic_t *) ngx_http_clojure_shared_memory.addr;
    *ngx_http_clojure_jvm_be_mad_times = 0;
#else
    ngx_http_clojure_jvm_be_mad_times = &ngx_http_clojure_jvm_be_mad_times_ins;
#endif
	ngx_log_error(NGX_LOG_NOTICE, cycle->log, 0, NGINX_CLOJURE_VER);

	return NGX_OK;
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

	if (len > 1024 - strlen("-s stop") - 1) {
		ngx_log_error(NGX_LOG_ALERT, cycle->log, ngx_errno, "command line is too long for execute");
		return NGX_ERROR;
	}

	strncpy(args, ctx.args, len);
	strcpy(args+len, "-s stop");
	ctx.args = args;

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

static ngx_int_t ngx_http_clojure_process_init(ngx_cycle_t *cycle) {
	ngx_http_conf_ctx_t *ctx = (ngx_http_conf_ctx_t *)ngx_get_conf(cycle->conf_ctx, ngx_http_module);
	ngx_int_t rc = 0;
/*	ngx_http_core_main_conf_t *hcmcf = ngx_http_cycle_get_module_main_conf(cycle, ngx_http_core_module);*/
	ngx_http_clojure_loc_conf_t *mcf = ctx->loc_conf[ngx_http_clojure_module.ctx_index];
	ngx_core_conf_t  *ccf = (ngx_core_conf_t *) ngx_get_conf(ngx_cycle->conf_ctx, ngx_core_module);

#if !(NGX_WIN32)
	ngx_setproctitle("worker process");
#else
	ngx_http_clojure_jvm_be_mad_times = &ngx_http_clojure_jvm_be_mad_times_ins;
#endif

	if ((ngx_int_t)ngx_atomic_fetch_add(ngx_http_clojure_jvm_be_mad_times, 1) >= ccf->worker_processes) {
		ngx_log_error(NGX_LOG_ERR, cycle->log, 0, "jvm may be mad for wrong options! See hs_err_pid****.log for detail! restarted %d", *ngx_http_clojure_jvm_be_mad_times);
#if defined(_WIN32) || defined(WIN32)
		ngx_terminate = 1;
		ngx_log_error(NGX_LOG_ERR, cycle->log, 0, "we try quit master now!");
		/*We must quit otherwise we'll enter a dead repeatedly case*/
		ngx_http_clojure_quit_master(cycle);
#endif
		return NGX_ERROR;
	}

    rc = ngx_http_clojure_init_jvm_and_mem(mcf, cycle->log);

    if (rc != NGX_HTTP_CLOJURE_JVM_OK){
    	ngx_log_error(NGX_LOG_ERR, cycle->log, 0, "jvm start times %d", *ngx_http_clojure_jvm_be_mad_times);
    	return NGX_ERROR;
    }

    /*we reset it for nginx normal restart nginx-worker when it crashed normally.*/
    (void)ngx_atomic_fetch_add(ngx_http_clojure_jvm_be_mad_times, -1);


    rc = ngx_http_clojure_init_socket(mcf, cycle->log);
    if (rc != NGX_HTTP_CLOJURE_JVM_OK) {
    	return NGX_ERROR;
    }

    if (mcf->enable && ngx_http_clojure_init_clojure_script("init-process", &mcf->handler_type, &mcf->handler_name, &mcf->handler_code, &mcf->handler_id, cycle->log) != NGX_HTTP_CLOJURE_JVM_OK) {
    	return NGX_ERROR;
    }

    if (mcf->handler_id >= 0) {
    	rc = ngx_http_clojure_eval(mcf->handler_id, 0);
    }

    if (rc > 300) {
    	return NGX_ERROR;
    }
    return NGX_OK;
}

static ngx_int_t   ngx_http_clojure_postconfiguration(ngx_conf_t *cf) {

	ngx_http_core_main_conf_t *cmcf = ngx_http_conf_get_module_main_conf(cf, ngx_http_core_module);
	ngx_http_clojure_loc_conf_t *lcf = ngx_http_conf_get_module_loc_conf(cf, ngx_http_clojure_module);
	ngx_http_handler_pt *h;

	if (lcf->jvm_path.len == NGX_CONF_UNSET_SIZE) {
		ngx_log_error(NGX_LOG_ERR, cf->log, 0, "no jvm_path configured!");
		return NGX_ERROR ;
	}

	if (lcf->jvm_options == NGX_CONF_UNSET_PTR) {
		ngx_log_error(NGX_LOG_ERR, cf->log, 0, "no jvm_options configured!");
		return NGX_ERROR ;
	}

	h = ngx_array_push(&cmcf->phases[NGX_HTTP_REWRITE_PHASE].handlers);
	if (h == NULL) {
		ngx_log_error(NGX_LOG_ERR, cf->log, 0, "can not register nginx clojure rewrite handler");
		return NGX_ERROR;
	}

	*h = ngx_http_clojure_rewrite_handler;

	return NGX_OK;
}



static void ngx_http_clojure_client_body_handler(ngx_http_request_t *r) {
	ngx_http_clojure_loc_conf_t  *lcf = ngx_http_get_module_loc_conf(r, ngx_http_clojure_module);
	int rc = ngx_http_clojure_eval(lcf->handler_id, r);
	ngx_http_finalize_request (r , rc);
}



static ngx_int_t ngx_http_clojure_handler(ngx_http_request_t * r) {
    ngx_int_t     rc;
    ngx_http_clojure_loc_conf_t  *lcf;


    lcf = ngx_http_get_module_loc_conf(r, ngx_http_clojure_module);
/*  ngx_http_core_main_conf_t  *cmcf = ngx_http_get_module_main_conf(r, ngx_http_core_module);*/

/*move to init process
    rc = ngx_http_clojure_init_jvm_and_mem(lcf, ngx_http_clojure_global_cycle->log);
    if (rc != NGX_HTTP_CLOJURE_JVM_OK){
    	return rc;
    }

    rc = ngx_http_clojure_init_socket(lcf, ngx_http_clojure_global_cycle->log);
    if (rc != NGX_HTTP_CLOJURE_JVM_OK){
    	return rc;
    }*/
    rc = ngx_http_clojure_init_clojure_script("content handler", &lcf->handler_type, &lcf->handler_name, &lcf->handler_code, &lcf->handler_id, ngx_http_clojure_global_cycle->log);
	if (rc != NGX_HTTP_CLOJURE_JVM_OK) {
		return rc;
	}

    if (lcf->always_read_body || (r->method & (NGX_HTTP_POST | NGX_HTTP_PUT | NGX_HTTP_PATCH))) {
		r->request_body_in_single_buf = 1;
		r->request_body_in_clean_file = 1;
		r->request_body_in_persistent_file = 1;
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
    	rc = ngx_http_clojure_eval(lcf->handler_id, r);
    }
    //for debug
    //ngx_log_error(NGX_LOG_DEBUG, r->connection->log, 0, "finished one request \n==============================================================\n\n");

    return rc;
}

static ngx_int_t ngx_http_clojure_rewrite_handler(ngx_http_request_t * r) {
	ngx_int_t rc;
	ngx_http_clojure_module_ctx_t *ctx;
	ngx_http_clojure_loc_conf_t  *lcf = ngx_http_get_module_loc_conf(r, ngx_http_clojure_module);

	if (!lcf->enable || (lcf->rewrite_handler_code.len == 0 && lcf->rewrite_handler_name.len == 0)) {
		return NGX_DECLINED;
	}

	rc = ngx_http_clojure_init_clojure_script("rewrite handler", &lcf->handler_type, &lcf->rewrite_handler_name, &lcf->rewrite_handler_code, &lcf->rewrite_handler_id, ngx_http_clojure_global_cycle->log);
	if (rc != NGX_HTTP_CLOJURE_JVM_OK) {
			return rc;
	}

	if ((ctx = ngx_http_get_module_ctx(r, ngx_http_clojure_module)) == NULL) {
		ctx = ngx_palloc(r->pool, sizeof(ngx_http_clojure_module_ctx_t));
		if (ctx == NULL) {
			ngx_log_error(NGX_LOG_ERR, r->connection->log, 0, "OutOfMemory of create ngx_http_clojure_module_ctx_t");
			return NGX_HTTP_INTERNAL_SERVER_ERROR;
		}
		ctx->handled_couter = 1;
		ctx->phrase = NGX_HTTP_REWRITE_PHASE;
		ngx_http_set_ctx(r, ctx, ngx_http_clojure_module);
		rc = ngx_http_clojure_eval(lcf->rewrite_handler_id, r);
		if (rc != NGX_DONE) {
			ctx->phrase = -1;
		}
		return rc;
	}else if (++ ctx->handled_couter > 32) { /*reach dead cycle*/
		ngx_log_error(NGX_LOG_ERR, r->connection->log, 0, "too much times by rewrite/access handler %d", ctx->handled_couter);
		ctx->phrase = -1;
		return NGX_HTTP_INTERNAL_SERVER_ERROR;
	}else if (ctx->phrase == NGX_HTTP_REWRITE_PHASE) { /*enter again*/
		ctx->phrase = -1;
		return NGX_DECLINED;
	}else {
		ctx->phrase = NGX_HTTP_REWRITE_PHASE;
		rc = ngx_http_clojure_eval(lcf->rewrite_handler_id, r);
		if (rc != NGX_DONE) {
			ctx->phrase = -1;
		}
		return rc;
	}
}

static char* ngx_http_clojure(ngx_conf_t *cf, ngx_command_t *cmd, void *conf) {
	ngx_http_core_loc_conf_t * clcf;
	ngx_http_clojure_loc_conf_t *lcf = conf;

	clcf = ngx_http_conf_get_module_loc_conf(cf, ngx_http_core_module);
	clcf->handler = ngx_http_clojure_handler;
	lcf->enable = 1;
	lcf->handler_type.data = (u_char *)"clojure";
	lcf->handler_type.len = sizeof("clojure")-1;
	return NGX_CONF_OK;
}

static char* ngx_http_clojure_set_str_slot_and_enable_tag(ngx_conf_t *cf, ngx_command_t *cmd, void *conf) {
	ngx_http_core_loc_conf_t * clcf;
	ngx_http_clojure_loc_conf_t *lcf = conf;

	clcf = ngx_http_conf_get_module_loc_conf(cf, ngx_http_core_module);
	clcf->handler = ngx_http_clojure_handler;
	lcf->enable = 1;
	return ngx_conf_set_str_slot(cf, cmd, conf);
}
