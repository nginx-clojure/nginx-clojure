#include <ngx_config.h>
#include "ngx_http_clojure_mem.h"
#include "ngx_http_clojure_shared_map.h"


static char *x(ngx_conf_t *cf, ngx_command_t *cmd, void *conf);
static ngx_int_t x_handler(ngx_http_request_t *r);

/**
 * This module provided directive: x.
 *
 */
static ngx_command_t x_commands[] = {

    { ngx_string("x"), /* directive */
      NGX_HTTP_LOC_CONF|NGX_CONF_NOARGS, /* location context and takes
                                            no arguments*/
      x, /* configuration setup function */
      0, /* No offset. Only one context is supported. */
      0, /* No offset when storing the module configuration on struct. */
      NULL},

    ngx_null_command /* command termination */
};


static ngx_int_t my_handler_code_id = 0;

static ngx_str_t java_type = ngx_string("java");
static ngx_str_t java_handler = ngx_string("example.MyHandler");
static ngx_str_t my_array_var = ngx_string("my_array");

/* The module context. */
static ngx_http_module_t x_module_ctx = {
    NULL, /* preconfiguration */
    NULL, /* postconfiguration */

    NULL, /* create main configuration */
    NULL, /* init main configuration */

    NULL, /* create server configuration */
    NULL, /* merge server configuration */

    NULL, /* create location configuration */
    NULL /* merge location configuration */
};

/* Module definition. */
ngx_module_t x_module = {
    NGX_MODULE_V1,
    &x_module_ctx, /* module context */
    x_commands, /* module directives */
    NGX_HTTP_MODULE, /* module type */
    NULL, /* init master */
	NULL, /* init module */
    NULL, /* init process */
    NULL, /* init thread */
    NULL, /* exit thread */
    NULL, /* exit process */
    NULL, /* exit master */
    NGX_MODULE_V1_PADDING
};


static ngx_int_t x_handler(ngx_http_request_t *r)
{
    ngx_buf_t *b;
    ngx_chain_t out;
    ngx_http_clojure_module_ctx_t *ctx;
    ngx_http_variable_value_t *my_array_vp;

    if (my_handler_code_id == 0) {
    	int rc = 0;

    	/*initialize MyHandler*/
    	rc = ngx_http_clojure_register_script(0, &java_type, &java_handler, 0, 0, &my_handler_code_id);

    	if (rc != NGX_OK) { /*error*/
    		return rc;
    	}
    }


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

	/*set my_array*/
    my_array_vp = ngx_http_get_variable(r, &my_array_var,
			ngx_hash_key(my_array_var.data, my_array_var.len));
	if (my_array_vp == NULL || my_array_vp->not_found) {
		ngx_log_error(NGX_LOG_ERR, r->connection->log, 0, "can not found variable my_array!");
		return NGX_HTTP_INTERNAL_SERVER_ERROR;
	}
	my_array_vp->len = sizeof("hello, world") -1;
	my_array_vp->data = (u_char*)"hello, world";

	/*invoke java handler which will get the value of variable $my_array and convert every char to upper case and
	 * set the new value back to the variable $my_array*/
    ngx_http_clojure_eval(my_handler_code_id, r, 0);
    r->main->count --;

    r->headers_out.content_type.len = sizeof("text/plain") - 1;
    r->headers_out.content_type.data = (u_char *) "text/plain";

    b = ngx_pcalloc(r->pool, sizeof(ngx_buf_t));

    out.buf = b;
    out.next = NULL;

    b->pos = my_array_vp->data;
    b->last = my_array_vp->data + my_array_vp->len;
    b->memory = 1;
    b->last_buf = 1;

    r->headers_out.status = NGX_HTTP_OK;
    r->headers_out.content_length_n = my_array_vp->len;
    ngx_http_send_header(r);

    return ngx_http_output_filter(r, &out);
}


static char *x(ngx_conf_t *cf, ngx_command_t *cmd, void *conf)
{
    ngx_http_core_loc_conf_t *clcf; /* pointer to core location configuration */

    /* Install the x handler. */
    clcf = ngx_http_conf_get_module_loc_conf(cf, ngx_http_core_module);
    clcf->handler = x_handler;

    return NGX_CONF_OK;
} /* x_module */
