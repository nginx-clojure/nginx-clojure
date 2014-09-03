/*
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 */
#include <ngx_config.h>
#include "ngx_http_clojure_mem.h"
#include "ngx_http_clojure_jvm.h"
#include <ngx_http_config.h>


extern ngx_module_t  ngx_http_clojure_module;

static JavaVM *jvm = NULL;
static JNIEnv *jvm_env = NULL;
static jclass nc_rt_class;
static jmethodID nc_rt_eval_mid;
static jmethodID nc_rt_register_code_mid;
static jmethodID nc_rt_handle_post_event_mid;
static jmethodID nc_rt_channel_listener_onclose_mid;

static int nc_ngx_workers;
static ngx_socket_t nc_ngx_worker_pipes_fds[NGX_MAX_PROCESSES][2];
static ngx_socket_t nc_jvm_worker_pipe_fds[2];

static  ngx_str_t  ngx_http_clojure_core_variables_names[] = {
		ngx_string("http_host"),
		ngx_string("http_user_agent"),
		ngx_string("http_referer"),
		ngx_string("http_via"),
		ngx_string("http_x_forwarded_for"),
		ngx_string("http_cookie"),
		ngx_string("content_length"),
		ngx_string("content_type"),
		ngx_string("host"),
		ngx_string("binary_remote_addr"),
		ngx_string("remote_addr"),
		ngx_string("remote_port"),
		ngx_string("server_addr"),
		ngx_string("server_port"),
		ngx_string("server_protocol"),
		ngx_string("scheme"),
		ngx_string("request_uri"),
		ngx_string("uri"),
		ngx_string("document_uri"),
		ngx_string("request"),
		ngx_string("document_root"),
		ngx_string("realpath_root"),
		ngx_string("query_string"),
		ngx_string("args"),
	    ngx_string("is_args"),
	    ngx_string("request_filename"),
	    ngx_string("server_name"),
	    ngx_string("request_method"),
	    ngx_string("remote_user"),
	    ngx_string("body_bytes_sent"),
	    ngx_string("request_completion"),
	    ngx_string("request_body"),
	    ngx_string("request_body_file"),
	    ngx_string("sent_http_content_type"),
	    ngx_string("sent_http_content_length"),
	    ngx_string("sent_http_location"),
	    ngx_string("sent_http_last_modified"),
	    ngx_string("sent_http_connection"),
	    ngx_string("sent_http_keep_alive"),
	    ngx_string("sent_http_transfer_encoding"),
	    ngx_string("sent_http_cache_control"),
	    ngx_string("limit_rate"),
	    ngx_string("nginx_version"),
	    ngx_string("hostname"),
	    ngx_string("pid")
};

typedef struct {
	jobject listener;
	jobject data;
} ngx_http_clojure_clean_up_data_t;

static void ngx_http_clojure_check_broken_connection(ngx_http_request_t *r, ngx_event_t *ev);

static void ngx_http_clojure_rd_check_broken_connection(ngx_http_request_t *r);

/*static void ngx_http_clojure_wt_check_broken_connection(ngx_http_request_t *r);*/

static jlong JNICALL jni_ngx_palloc (JNIEnv *env, jclass cls, jlong pool, jlong size) {
	return (uintptr_t)ngx_palloc((ngx_pool_t *)(uintptr_t)pool, (size_t)size);
}

static jlong JNICALL jni_ngx_pcalloc (JNIEnv *env, jclass cls, jlong pool, jlong size) {
	return (uintptr_t)ngx_pcalloc((ngx_pool_t *)(uintptr_t)pool, (size_t)size);
}

static jlong JNICALL jni_ngx_array_create(JNIEnv *env, jclass cls, jlong pool, jlong n, jlong size) {
	return (uintptr_t)ngx_array_create((ngx_pool_t *)(uintptr_t)pool, (ngx_uint_t)n, (size_t)size);
}

static jlong JNICALL jni_ngx_array_init(JNIEnv *env, jclass cls, jlong array, jlong pool, jlong n, jlong size) {
	return (jlong)ngx_array_init((ngx_array_t *)(uintptr_t)array, (ngx_pool_t *)(uintptr_t)pool, (ngx_uint_t)n, (size_t)size);
}

static jlong JNICALL jni_ngx_array_push_n(JNIEnv *env, jclass cls, jlong array, jlong n) {
	return (uintptr_t)ngx_array_push_n((ngx_array_t *)(uintptr_t)array,  (ngx_uint_t)n);
}


static jlong JNICALL jni_ngx_list_create(JNIEnv *env, jclass cls, jlong pool, jlong n, jlong size) {
	return (uintptr_t)ngx_list_create((ngx_pool_t *)(uintptr_t)pool, (ngx_uint_t)n, (size_t)size);
}

static jlong JNICALL jni_ngx_list_init(JNIEnv *env, jclass cls, jlong list, jlong pool, jlong n, jlong size) {
	return (jlong)ngx_list_init((ngx_list_t *)(uintptr_t)list, (ngx_pool_t *)(uintptr_t)pool, (ngx_uint_t)n, (size_t)size);
}

static jlong JNICALL jni_ngx_list_push(JNIEnv *env, jclass cls, jlong list) {
	return (uintptr_t)ngx_list_push((ngx_list_t *)(uintptr_t)list);
}


static jlong JNICALL jni_ngx_create_temp_buf (JNIEnv *env, jclass cls, jlong r, jlong size) {
	ngx_http_request_t *req = (ngx_http_request_t *)(uintptr_t) r;

	if (req->headers_out.content_length_n < 0 ) {
		req->headers_out.content_length_n = size;
	}else {
		req->headers_out.content_length_n += size;
	}

	/*
	 * If File and String are in the same ISeq of one response body,
	 * we should clear the last_modified_time.
	 */
	req->headers_out.last_modified_time = -2;
	req->headers_out.last_modified = NULL;

	return (uintptr_t)ngx_create_temp_buf(req->pool, (size_t)size);
}


static jlong JNICALL jni_ngx_create_file_buf (JNIEnv *env, jclass cls, jlong r, jlong file, jlong name_len, jint last_buf) {
	ngx_http_request_t *req = (ngx_http_request_t *) (uintptr_t)r;
	ngx_buf_t *b;
	ngx_str_t path; // = {(ngx_int_t)name_len, (u_char *)file};
	ngx_open_file_info_t of;
	ngx_http_core_loc_conf_t  *clcf = ngx_http_get_module_loc_conf(req, ngx_http_core_module);
	ngx_uint_t level;
	ngx_log_t *log = req->connection->log;

	/*make VS 2010 happy*/
	path.data = (u_char *)(uintptr_t)file;
	path.len = (ngx_int_t)name_len;

	/*just like http_static module */

	ngx_memzero(&of, sizeof(ngx_open_file_info_t));

	of.read_ahead = clcf->read_ahead;
	of.directio = clcf->directio;
	of.valid = clcf->open_file_cache_valid;
	of.min_uses = clcf->open_file_cache_min_uses;
	of.errors = clcf->open_file_cache_errors;
	of.events = clcf->open_file_cache_events;

	if (ngx_open_cached_file(clcf->open_file_cache, &path, &of, req->pool) != NGX_OK) {
		ngx_int_t rc = 0;

		switch (of.err) {

		case 0:
			return -NGX_HTTP_INTERNAL_SERVER_ERROR;

		case NGX_ENOENT:
		case NGX_ENOTDIR:
		case NGX_ENAMETOOLONG:

			level = NGX_LOG_ERR;
			rc = NGX_HTTP_NOT_FOUND;
			break;

		case NGX_EACCES:

			level = NGX_LOG_ERR;
			rc = NGX_HTTP_FORBIDDEN;
			break;

		default:

			level = NGX_LOG_CRIT;
			rc = NGX_HTTP_INTERNAL_SERVER_ERROR;
			break;
		}

		if (rc != NGX_HTTP_NOT_FOUND || clcf->log_not_found) {
			ngx_log_error(level, log, of.err, "%s \"%s\" failed", of.failed,
					path.data);
		}

		return -rc;
	}

	if (of.is_dir) {
		return -NGX_HTTP_NOT_FOUND;
	}

#if !(NGX_WIN32) /* the not regular files are probably Unix specific */

    if (!of.is_file) {
        ngx_log_error(NGX_LOG_CRIT, log, 0,
                      "\"%s\" is not a regular file", path.data);

        return -NGX_HTTP_NOT_FOUND;
    }

#endif

    req->allow_ranges = 1;

    b = ngx_pcalloc(req->pool, sizeof(ngx_buf_t));
    if (b == NULL) {
        return -NGX_HTTP_INTERNAL_SERVER_ERROR;
    }

    b->file = ngx_pcalloc(req->pool, sizeof(ngx_file_t));
    if (b->file == NULL) {
        return -NGX_HTTP_INTERNAL_SERVER_ERROR;
    }

    b->file_pos = 0;
    b->file_last = of.size;

    b->in_file = b->file_last ? 1: 0;

    if (last_buf & NGX_BUF_LAST_OF_RESPONSE) {
    	b->last_buf = b->last_in_chain = 1;
    }else {
    	b->last_in_chain = last_buf & NGX_BUF_LAST_OF_CHAIN;
    }

    b->file->fd = of.fd;
    b->file->name = path;
    b->file->log = log;
    b->file->directio = of.is_directio;

    if (req->headers_out.content_length_n < 0) {
		req->headers_out.content_length_n = of.size;
	} else {
		req->headers_out.content_length_n += of.size;
	}

    if (req->headers_out.last_modified_time != -2 && req->headers_out.last_modified_time < of.mtime) {
    	req->headers_out.last_modified_time = of.mtime;
    }
	return (uintptr_t)b;
}

static jlong JNICALL jni_ngx_http_set_content_type(JNIEnv *env, jclass cls, jlong r) {
	return ngx_http_set_content_type((ngx_http_request_t *)(uintptr_t)r);
}

static jlong JNICALL jni_ngx_http_send_header (JNIEnv *env, jclass cls, jlong r) {
	ngx_http_request_t *req = (ngx_http_request_t *)(uintptr_t) r;
	if (req->headers_out.last_modified_time == -2) {
		req->headers_out.last_modified_time = -1;
	}
	return ngx_http_send_header(req);
}

static jlong JNICALL jni_ngx_http_output_filter (JNIEnv *env, jclass cls, jlong r, jlong chain) {
	return ngx_http_output_filter((ngx_http_request_t *)(uintptr_t)r, (ngx_chain_t *)(uintptr_t)chain);
}

#define NGX_CLOJURE_REUSABLE_PAGE_SIZE 4096
#define NGX_CLOJURE_BUF_LAST_FLAG 0x01
#define NGX_CLOJURE_BUF_FLUSH_FLAG 0x02
#define NGX_CLOJURE_BUF_IGNORE_FILTER_FLAG 0x04

static ngx_chain_t * ngx_http_clojure_get_and_copy_bufs(ngx_pool_t *p, ngx_chain_t **free, char *src, size_t len,  ngx_int_t flag) {
	ngx_chain_t *cl;
	ngx_chain_t **ll = &cl;
	ngx_buf_t *b = NULL;
	while (len) {
		if (*free) {
			*ll = *free;
			*free = (*ll)->next;
			b = (*ll)->buf;
			b->last_in_chain = b->last_buf = 0;
			b->temporary = 1;
			if (len > NGX_CLOJURE_REUSABLE_PAGE_SIZE) {
				ngx_memcpy(b->pos, src, NGX_CLOJURE_REUSABLE_PAGE_SIZE);
				b->last += NGX_CLOJURE_REUSABLE_PAGE_SIZE;
				src += NGX_CLOJURE_REUSABLE_PAGE_SIZE;
				len -= NGX_CLOJURE_REUSABLE_PAGE_SIZE;
			} else {
				ngx_memcpy(b->pos, src, len);
				b->last += len;
				len = 0;
			}
			ll = &(*ll)->next;

		}else {
	    	ngx_bufs_t bufs;
	    	bufs.size = NGX_CLOJURE_REUSABLE_PAGE_SIZE;
	    	bufs.num = len / NGX_CLOJURE_REUSABLE_PAGE_SIZE;
	    	if (len % NGX_CLOJURE_REUSABLE_PAGE_SIZE != 0) {
	    		bufs.num ++;
	    	}
	    	*ll = ngx_create_chain_of_bufs(p, &bufs);
	    	if (*ll == NULL) {
	    		return NULL;
	    	}
	    	ngx_memcpy((*ll)->buf->pos, src, len);
	    	for (;  *ll; ll = &(*ll)->next) {
	    		b = (*ll)->buf;
	    		if ((*ll)->next || (len % NGX_CLOJURE_REUSABLE_PAGE_SIZE) == 0) {
	    			b->last += NGX_CLOJURE_REUSABLE_PAGE_SIZE;
	    		}else {
	    			b->last += (len % NGX_CLOJURE_REUSABLE_PAGE_SIZE);
	    		}
	    		b->tag = (ngx_buf_tag_t) &ngx_http_clojure_module;
	    	}
	    	break;
	    }
	}
	*ll = NULL;
	if (b) {
		b->last_in_chain = 1;
		if (flag & NGX_CLOJURE_BUF_LAST_FLAG) {
			b->last_buf = 1;
		}
		if (flag & NGX_CLOJURE_BUF_FLUSH_FLAG) {
			b->flush = 1;
		}
	}

	return cl;
}

/*copy from ngx_http_request.c*/
static void
ngx_http_close_request(ngx_http_request_t *r, ngx_int_t rc)
{
    ngx_connection_t  *c;

    r = r->main;
    c = r->connection;

    ngx_log_debug2(NGX_LOG_DEBUG_HTTP, c->log, 0,
                   "http request count:%d blk:%d", r->count, r->blocked);

    if (r->count == 0) {
        ngx_log_error(NGX_LOG_ALERT, c->log, 0, "http request count is zero");
    }

    r->count--;

    if (r->count || r->blocked) {
        return;
    }

#if (NGX_HTTP_SPDY)
    if (r->spdy_stream) {
        ngx_http_spdy_close_stream(r->spdy_stream, rc);
        return;
    }
#endif

    ngx_http_free_request(r, rc);
    ngx_http_close_connection(c);
}

static void ngx_http_clojure_hijack_writer(ngx_http_request_t *r) {
	int rc;
	ngx_event_t *wev;
	ngx_connection_t *c;
	ngx_http_core_loc_conf_t *clcf;
	ngx_http_clojure_module_ctx_t *ctx;

	ctx = (ngx_http_clojure_module_ctx_t *)ngx_http_get_module_ctx(r, ngx_http_clojure_module);
	c = r->connection;
	wev = c->write;

	ngx_log_debug2(NGX_LOG_DEBUG_HTTP, wev->log, 0,
			"clojure module hijack writer: \"%V?%V\"", &r->uri, &r->args);

	clcf = ngx_http_get_module_loc_conf(r->main, ngx_http_core_module);

	if (wev->timedout) {
		if (!wev->delayed) {
			ngx_log_error(NGX_LOG_INFO, c->log, NGX_ETIMEDOUT, "client timed out");
			c->timedout = 1;

			ngx_http_finalize_request(r, NGX_HTTP_REQUEST_TIME_OUT);
			return;
		}

		wev->timedout = 0;
		wev->delayed = 0;

		if (!wev->ready) {
			ngx_add_timer(wev, clcf->send_timeout);

			if (ngx_handle_write_event(wev, clcf->send_lowat) != NGX_OK) {
				ngx_http_close_request(r, 0);
			}

			return;
		}

	}

	if (wev->delayed || r->aio) {
		ngx_log_debug0(NGX_LOG_DEBUG_HTTP, wev->log, 0, "clojure module hijack writer delayed");

		if (ngx_handle_write_event(wev, clcf->send_lowat) != NGX_OK) {
			ngx_http_close_request(r, 0);
		}

		return;
	}

	rc = ctx->ignore_filters ? ngx_http_write_filter(r, NULL) : ngx_http_output_filter(r, NULL);

	ngx_log_debug3(NGX_LOG_DEBUG_HTTP, c->log, 0,
			"clojure module hijack writer output filter: %d, \"%V?%V\"", rc, &r->uri, &r->args);

	if (rc == NGX_ERROR) {
		ngx_http_finalize_request(r, rc);
		return;
	}

	if (r->buffered || r->postponed || (r == r->main && c->buffered)) {

		if (!wev->delayed) {
			ngx_add_timer(wev, clcf->send_timeout);
		}

		if (ngx_handle_write_event(wev, clcf->send_lowat) != NGX_OK) {
			ngx_http_close_request(r, 0);
		}

		return;
	}

	ngx_log_debug2(NGX_LOG_DEBUG_HTTP, wev->log, 0,
			"clojure module hijack writer done: \"%V?%V\"", &r->uri, &r->args);

	if (ctx->last_buf_meeted) {
		ngx_http_close_request(r, 0);
		return;
	}
	r->write_event_handler = ngx_http_request_empty_handler;
}


/*
 * *****************************************************************************
 * START of copy from ngx_http_header_filter_module.c
 * *****************************************************************************
 * */
static char ngx_http_server_string[] = "Server: nginx" CRLF;
static char ngx_http_server_full_string[] = "Server: " NGINX_VER CRLF;


static ngx_str_t ngx_http_status_lines[] = {

    ngx_string("200 OK"),
    ngx_string("201 Created"),
    ngx_string("202 Accepted"),
    ngx_null_string,  /* "203 Non-Authoritative Information" */
    ngx_string("204 No Content"),
    ngx_null_string,  /* "205 Reset Content" */
    ngx_string("206 Partial Content"),

    /* ngx_null_string, */  /* "207 Multi-Status" */

#define NGX_HTTP_LAST_2XX  207
#define NGX_HTTP_OFF_3XX   (NGX_HTTP_LAST_2XX - 200)

    /* ngx_null_string, */  /* "300 Multiple Choices" */

    ngx_string("301 Moved Permanently"),
    ngx_string("302 Moved Temporarily"),
    ngx_string("303 See Other"),
    ngx_string("304 Not Modified"),
    ngx_null_string,  /* "305 Use Proxy" */
    ngx_null_string,  /* "306 unused" */
    ngx_string("307 Temporary Redirect"),

#define NGX_HTTP_LAST_3XX  308
#define NGX_HTTP_OFF_4XX   (NGX_HTTP_LAST_3XX - 301 + NGX_HTTP_OFF_3XX)

    ngx_string("400 Bad Request"),
    ngx_string("401 Unauthorized"),
    ngx_string("402 Payment Required"),
    ngx_string("403 Forbidden"),
    ngx_string("404 Not Found"),
    ngx_string("405 Not Allowed"),
    ngx_string("406 Not Acceptable"),
    ngx_null_string,  /* "407 Proxy Authentication Required" */
    ngx_string("408 Request Time-out"),
    ngx_string("409 Conflict"),
    ngx_string("410 Gone"),
    ngx_string("411 Length Required"),
    ngx_string("412 Precondition Failed"),
    ngx_string("413 Request Entity Too Large"),
    ngx_null_string,  /* "414 Request-URI Too Large", but we never send it
                       * because we treat such requests as the HTTP/0.9
                       * requests and send only a body without a header
                       */
    ngx_string("415 Unsupported Media Type"),
    ngx_string("416 Requested Range Not Satisfiable"),

    /* ngx_null_string, */  /* "417 Expectation Failed" */
    /* ngx_null_string, */  /* "418 unused" */
    /* ngx_null_string, */  /* "419 unused" */
    /* ngx_null_string, */  /* "420 unused" */
    /* ngx_null_string, */  /* "421 unused" */
    /* ngx_null_string, */  /* "422 Unprocessable Entity" */
    /* ngx_null_string, */  /* "423 Locked" */
    /* ngx_null_string, */  /* "424 Failed Dependency" */

#define NGX_HTTP_LAST_4XX  417
#define NGX_HTTP_OFF_5XX   (NGX_HTTP_LAST_4XX - 400 + NGX_HTTP_OFF_4XX)

    ngx_string("500 Internal Server Error"),
    ngx_string("501 Method Not Implemented"),
    ngx_string("502 Bad Gateway"),
    ngx_string("503 Service Temporarily Unavailable"),
    ngx_string("504 Gateway Time-out"),

    ngx_null_string,        /* "505 HTTP Version Not Supported" */
    ngx_null_string,        /* "506 Variant Also Negotiates" */
    ngx_string("507 Insufficient Storage"),
    /* ngx_null_string, */  /* "508 unused" */
    /* ngx_null_string, */  /* "509 unused" */
    /* ngx_null_string, */  /* "510 Not Extended" */

#define NGX_HTTP_LAST_5XX  508

};


static ngx_int_t
ngx_http_header_filter(ngx_http_request_t *r)
{
    u_char                    *p;
    size_t                     len;
    ngx_str_t                  host, *status_line;
    ngx_buf_t                 *b;
    ngx_uint_t                 status, i, port;
    ngx_chain_t                out;
    ngx_list_part_t           *part;
    ngx_table_elt_t           *header;
    ngx_connection_t          *c;
    ngx_http_core_loc_conf_t  *clcf;
    ngx_http_core_srv_conf_t  *cscf;
    struct sockaddr_in        *sin;
#if (NGX_HAVE_INET6)
    struct sockaddr_in6       *sin6;
#endif
    u_char                     addr[NGX_SOCKADDR_STRLEN];

    if (r->header_sent) {
        return NGX_OK;
    }

    r->header_sent = 1;

    if (r != r->main) {
        return NGX_OK;
    }

    if (r->http_version < NGX_HTTP_VERSION_10) {
        return NGX_OK;
    }

    if (r->method == NGX_HTTP_HEAD) {
        r->header_only = 1;
    }

    if (r->headers_out.last_modified_time != -1) {
        if (r->headers_out.status != NGX_HTTP_OK
            && r->headers_out.status != NGX_HTTP_PARTIAL_CONTENT
            && r->headers_out.status != NGX_HTTP_NOT_MODIFIED)
        {
            r->headers_out.last_modified_time = -1;
            r->headers_out.last_modified = NULL;
        }
    }

    len = sizeof("HTTP/1.x ") - 1 + sizeof(CRLF) - 1
          /* the end of the header */
          + sizeof(CRLF) - 1;

    /* status line */

    if (r->headers_out.status_line.len) {
        len += r->headers_out.status_line.len;
        status_line = &r->headers_out.status_line;
#if (NGX_SUPPRESS_WARN)
        status = 0;
#endif

    } else {

        status = r->headers_out.status;

        if (status >= NGX_HTTP_OK
            && status < NGX_HTTP_LAST_2XX)
        {
            /* 2XX */

            if (status == NGX_HTTP_NO_CONTENT) {
                r->header_only = 1;
                ngx_str_null(&r->headers_out.content_type);
                r->headers_out.last_modified_time = -1;
                r->headers_out.last_modified = NULL;
                r->headers_out.content_length = NULL;
                r->headers_out.content_length_n = -1;
            }

            status -= NGX_HTTP_OK;
            status_line = &ngx_http_status_lines[status];
            len += ngx_http_status_lines[status].len;

        } else if (status >= NGX_HTTP_MOVED_PERMANENTLY
                   && status < NGX_HTTP_LAST_3XX)
        {
            /* 3XX */

            if (status == NGX_HTTP_NOT_MODIFIED) {
                r->header_only = 1;
            }

            status = status - NGX_HTTP_MOVED_PERMANENTLY + NGX_HTTP_OFF_3XX;
            status_line = &ngx_http_status_lines[status];
            len += ngx_http_status_lines[status].len;

        } else if (status >= NGX_HTTP_BAD_REQUEST
                   && status < NGX_HTTP_LAST_4XX)
        {
            /* 4XX */
            status = status - NGX_HTTP_BAD_REQUEST
                            + NGX_HTTP_OFF_4XX;

            status_line = &ngx_http_status_lines[status];
            len += ngx_http_status_lines[status].len;

        } else if (status >= NGX_HTTP_INTERNAL_SERVER_ERROR
                   && status < NGX_HTTP_LAST_5XX)
        {
            /* 5XX */
            status = status - NGX_HTTP_INTERNAL_SERVER_ERROR
                            + NGX_HTTP_OFF_5XX;

            status_line = &ngx_http_status_lines[status];
            len += ngx_http_status_lines[status].len;

        } else {
            len += NGX_INT_T_LEN;
            status_line = NULL;
        }
    }

    clcf = ngx_http_get_module_loc_conf(r, ngx_http_core_module);

    if (r->headers_out.server == NULL) {
        len += clcf->server_tokens ? sizeof(ngx_http_server_full_string) - 1:
                                     sizeof(ngx_http_server_string) - 1;
    }

    if (r->headers_out.date == NULL) {
        len += sizeof("Date: Mon, 28 Sep 1970 06:00:00 GMT" CRLF) - 1;
    }

    if (r->headers_out.content_type.len) {
        len += sizeof("Content-Type: ") - 1
               + r->headers_out.content_type.len + 2;

        if (r->headers_out.content_type_len == r->headers_out.content_type.len
            && r->headers_out.charset.len)
        {
            len += sizeof("; charset=") - 1 + r->headers_out.charset.len;
        }
    }

    if (r->headers_out.content_length == NULL
        && r->headers_out.content_length_n >= 0)
    {
        len += sizeof("Content-Length: ") - 1 + NGX_OFF_T_LEN + 2;
    }

    if (r->headers_out.last_modified == NULL
        && r->headers_out.last_modified_time != -1)
    {
        len += sizeof("Last-Modified: Mon, 28 Sep 1970 06:00:00 GMT" CRLF) - 1;
    }

    c = r->connection;

    if (r->headers_out.location
        && r->headers_out.location->value.len
        && r->headers_out.location->value.data[0] == '/')
    {
        r->headers_out.location->hash = 0;

        if (clcf->server_name_in_redirect) {
            cscf = ngx_http_get_module_srv_conf(r, ngx_http_core_module);
            host = cscf->server_name;

        } else if (r->headers_in.server.len) {
            host = r->headers_in.server;

        } else {
            host.len = NGX_SOCKADDR_STRLEN;
            host.data = addr;

            if (ngx_connection_local_sockaddr(c, &host, 0) != NGX_OK) {
                return NGX_ERROR;
            }
        }

        switch (c->local_sockaddr->sa_family) {

#if (NGX_HAVE_INET6)
        case AF_INET6:
            sin6 = (struct sockaddr_in6 *) c->local_sockaddr;
            port = ntohs(sin6->sin6_port);
            break;
#endif
#if (NGX_HAVE_UNIX_DOMAIN)
        case AF_UNIX:
            port = 0;
            break;
#endif
        default: /* AF_INET */
            sin = (struct sockaddr_in *) c->local_sockaddr;
            port = ntohs(sin->sin_port);
            break;
        }

        len += sizeof("Location: https://") - 1
               + host.len
               + r->headers_out.location->value.len + 2;

        if (clcf->port_in_redirect) {

#if (NGX_HTTP_SSL)
            if (c->ssl)
                port = (port == 443) ? 0 : port;
            else
#endif
                port = (port == 80) ? 0 : port;

        } else {
            port = 0;
        }

        if (port) {
            len += sizeof(":65535") - 1;
        }

    } else {
        ngx_str_null(&host);
        port = 0;
    }

    if (r->chunked) {
        len += sizeof("Transfer-Encoding: chunked" CRLF) - 1;
    }

    if (r->keepalive) {
        len += sizeof("Connection: keep-alive" CRLF) - 1;

        /*
         * MSIE and Opera ignore the "Keep-Alive: timeout=<N>" header.
         * MSIE keeps the connection alive for about 60-65 seconds.
         * Opera keeps the connection alive very long.
         * Mozilla keeps the connection alive for N plus about 1-10 seconds.
         * Konqueror keeps the connection alive for about N seconds.
         */

        if (clcf->keepalive_header) {
            len += sizeof("Keep-Alive: timeout=") - 1 + NGX_TIME_T_LEN + 2;
        }

    } else {
        len += sizeof("Connection: closed" CRLF) - 1;
    }

#if (NGX_HTTP_GZIP)
    if (r->gzip_vary) {
        if (clcf->gzip_vary) {
            len += sizeof("Vary: Accept-Encoding" CRLF) - 1;

        } else {
            r->gzip_vary = 0;
        }
    }
#endif

    part = &r->headers_out.headers.part;
    header = part->elts;

    for (i = 0; /* void */; i++) {

        if (i >= part->nelts) {
            if (part->next == NULL) {
                break;
            }

            part = part->next;
            header = part->elts;
            i = 0;
        }

        if (header[i].hash == 0) {
            continue;
        }

        len += header[i].key.len + sizeof(": ") - 1 + header[i].value.len
               + sizeof(CRLF) - 1;
    }

    b = ngx_create_temp_buf(r->pool, len);
    if (b == NULL) {
        return NGX_ERROR;
    }

    /* "HTTP/1.x " */
    b->last = ngx_cpymem(b->last, "HTTP/1.1 ", sizeof("HTTP/1.x ") - 1);

    /* status line */
    if (status_line) {
        b->last = ngx_copy(b->last, status_line->data, status_line->len);

    } else {
        b->last = ngx_sprintf(b->last, "%ui", status);
    }
    *b->last++ = CR; *b->last++ = LF;

    if (r->headers_out.server == NULL) {
        if (clcf->server_tokens) {
            p = (u_char *) ngx_http_server_full_string;
            len = sizeof(ngx_http_server_full_string) - 1;

        } else {
            p = (u_char *) ngx_http_server_string;
            len = sizeof(ngx_http_server_string) - 1;
        }

        b->last = ngx_cpymem(b->last, p, len);
    }

    if (r->headers_out.date == NULL) {
        b->last = ngx_cpymem(b->last, "Date: ", sizeof("Date: ") - 1);
        b->last = ngx_cpymem(b->last, ngx_cached_http_time.data,
                             ngx_cached_http_time.len);

        *b->last++ = CR; *b->last++ = LF;
    }

    if (r->headers_out.content_type.len) {
        b->last = ngx_cpymem(b->last, "Content-Type: ",
                             sizeof("Content-Type: ") - 1);
        p = b->last;
        b->last = ngx_copy(b->last, r->headers_out.content_type.data,
                           r->headers_out.content_type.len);

        if (r->headers_out.content_type_len == r->headers_out.content_type.len
            && r->headers_out.charset.len)
        {
            b->last = ngx_cpymem(b->last, "; charset=",
                                 sizeof("; charset=") - 1);
            b->last = ngx_copy(b->last, r->headers_out.charset.data,
                               r->headers_out.charset.len);

            /* update r->headers_out.content_type for possible logging */

            r->headers_out.content_type.len = b->last - p;
            r->headers_out.content_type.data = p;
        }

        *b->last++ = CR; *b->last++ = LF;
    }

    if (r->headers_out.content_length == NULL
        && r->headers_out.content_length_n >= 0)
    {
        b->last = ngx_sprintf(b->last, "Content-Length: %O" CRLF,
                              r->headers_out.content_length_n);
    }

    if (r->headers_out.last_modified == NULL
        && r->headers_out.last_modified_time != -1)
    {
        b->last = ngx_cpymem(b->last, "Last-Modified: ",
                             sizeof("Last-Modified: ") - 1);
        b->last = ngx_http_time(b->last, r->headers_out.last_modified_time);

        *b->last++ = CR; *b->last++ = LF;
    }

    if (host.data) {

        p = b->last + sizeof("Location: ") - 1;

        b->last = ngx_cpymem(b->last, "Location: http",
                             sizeof("Location: http") - 1);

#if (NGX_HTTP_SSL)
        if (c->ssl) {
            *b->last++ ='s';
        }
#endif

        *b->last++ = ':'; *b->last++ = '/'; *b->last++ = '/';
        b->last = ngx_copy(b->last, host.data, host.len);

        if (port) {
            b->last = ngx_sprintf(b->last, ":%ui", port);
        }

        b->last = ngx_copy(b->last, r->headers_out.location->value.data,
                           r->headers_out.location->value.len);

        /* update r->headers_out.location->value for possible logging */

        r->headers_out.location->value.len = b->last - p;
        r->headers_out.location->value.data = p;
        ngx_str_set(&r->headers_out.location->key, "Location");

        *b->last++ = CR; *b->last++ = LF;
    }

    if (r->chunked) {
        b->last = ngx_cpymem(b->last, "Transfer-Encoding: chunked" CRLF,
                             sizeof("Transfer-Encoding: chunked" CRLF) - 1);
    }

    if (r->keepalive) {
        b->last = ngx_cpymem(b->last, "Connection: keep-alive" CRLF,
                             sizeof("Connection: keep-alive" CRLF) - 1);

        if (clcf->keepalive_header) {
            b->last = ngx_sprintf(b->last, "Keep-Alive: timeout=%T" CRLF,
                                  clcf->keepalive_header);
        }

    } else {
        b->last = ngx_cpymem(b->last, "Connection: close" CRLF,
                             sizeof("Connection: close" CRLF) - 1);
    }

#if (NGX_HTTP_GZIP)
    if (r->gzip_vary) {
        b->last = ngx_cpymem(b->last, "Vary: Accept-Encoding" CRLF,
                             sizeof("Vary: Accept-Encoding" CRLF) - 1);
    }
#endif

    part = &r->headers_out.headers.part;
    header = part->elts;

    for (i = 0; /* void */; i++) {

        if (i >= part->nelts) {
            if (part->next == NULL) {
                break;
            }

            part = part->next;
            header = part->elts;
            i = 0;
        }

        if (header[i].hash == 0) {
            continue;
        }

        b->last = ngx_copy(b->last, header[i].key.data, header[i].key.len);
        *b->last++ = ':'; *b->last++ = ' ';

        b->last = ngx_copy(b->last, header[i].value.data, header[i].value.len);
        *b->last++ = CR; *b->last++ = LF;
    }

    ngx_log_debug2(NGX_LOG_DEBUG_HTTP, c->log, 0,
                   "%*s", (size_t) (b->last - b->pos), b->pos);

    /* the end of HTTP header */
    *b->last++ = CR; *b->last++ = LF;

    r->header_size = b->last - b->pos;

    if (r->header_only) {
        b->last_buf = 1;
    }

    out.buf = b;
    out.next = NULL;

    return ngx_http_write_filter(r, &out);
}
/*
 * *****************************************************************************
 * END of copy from ngx_http_header_filter_module.c
 * *****************************************************************************
 * */

static ngx_int_t  ngx_http_clojure_hijack_send_chain(ngx_http_request_t *r, ngx_chain_t *in, ngx_int_t flag) {
	ngx_http_clojure_module_ctx_t *ctx;
	ngx_int_t rc;
	ngx_chain_t **ll;

	if (r->pool == NULL) {
		ngx_log_error(NGX_LOG_ERR, ngx_http_clojure_global_cycle->log, 0,
						"ngx_http_clojure_hijack_send:"
						"can not send message because the request was closed");
		return NGX_ERROR;
	}

	if (in == NULL) {
		return NGX_ERROR;
	}

	ctx = (ngx_http_clojure_module_ctx_t *) ngx_http_get_module_ctx(r, ngx_http_clojure_module);
	if (flag & NGX_CLOJURE_BUF_IGNORE_FILTER_FLAG) {
		ctx->ignore_filters = 1;
	} else {
		ctx->ignore_filters = 0;
	}

	if (flag & NGX_CLOJURE_BUF_LAST_FLAG) {
		ctx->last_buf_meeted = 1;
	}

	if (flag & NGX_CLOJURE_BUF_LAST_FLAG || flag & NGX_CLOJURE_BUF_FLUSH_FLAG) {
		for (ll = &in; (*ll)->next; ll = &(*ll)->next);
		if (flag & NGX_CLOJURE_BUF_LAST_FLAG) {
			(*ll)->buf->last_buf = 1;
		}
		if (flag & NGX_CLOJURE_BUF_FLUSH_FLAG) {
			(*ll)->buf->flush = 1;
		}
	}

	rc = ctx->ignore_filters ? ngx_http_write_filter(r, in) : ngx_http_output_filter(r, in);


	if (rc == NGX_AGAIN) {
		if (r->write_event_handler != ngx_http_clojure_hijack_writer) {
			r->write_event_handler = ngx_http_clojure_hijack_writer;
		}
		r->read_event_handler = ngx_http_clojure_rd_check_broken_connection;
		if (!r->connection->write->active) {
			(void)ngx_handle_write_event(r->connection->write, 0);
		}
	}else if (rc != NGX_OK) {
		return rc;
	}else {
		if (ctx->last_buf_meeted) {
			ngx_http_close_request(r, 0);
			return NGX_OK;
		}
		r->read_event_handler = ngx_http_clojure_rd_check_broken_connection;
		r->write_event_handler = ngx_http_request_empty_handler;
	}

	if (!r->connection->read->active) {
		(void)ngx_handle_read_event(r->connection->read, 0);
	}

#if nginx_version >= 1001004
	ngx_chain_update_chains(r->pool, &ctx->free, &ctx->busy, &in, (ngx_buf_tag_t)&ngx_http_clojure_module);
#else
	ngx_chain_update_chains(&ctx->free, &ctx->busy, &in, (ngx_buf_tag_t)&ngx_http_clojure_module);
#endif
	return NGX_OK;
}

static ngx_int_t  ngx_http_clojure_hijack_send(ngx_http_request_t *r, char *message, size_t len, ngx_int_t flag) {
	ngx_http_clojure_module_ctx_t *ctx;
	ngx_chain_t *in;

	if (r->pool == NULL) {
		ngx_log_error(NGX_LOG_ERR, ngx_http_clojure_global_cycle->log, 0,
						"ngx_http_clojure_hijack_send:"
						"can not send message because the request was closed");
		return NGX_ERROR;
	}

	ctx = (ngx_http_clojure_module_ctx_t *) ngx_http_get_module_ctx(r, ngx_http_clojure_module);
	if (flag & NGX_CLOJURE_BUF_IGNORE_FILTER_FLAG) {
		ctx->ignore_filters = 1;
	} else {
		ctx->ignore_filters = 0;
	}

	if (len == 0) {
		if (flag & NGX_CLOJURE_BUF_LAST_FLAG || flag & NGX_CLOJURE_BUF_FLUSH_FLAG) {
			in = ngx_http_clojure_get_and_copy_bufs(r->pool, &ctx->free, "n", 1, flag);
			if (in == NULL) {
				ngx_log_error(NGX_LOG_ERR, r->connection->log, 0,
						"ngx_http_clojure_hijack_send:"
						"not enough memory, ngx_http_clojure_get_and_copy_bufs fail");
				return NGX_ERROR;
			}
			in->buf->pos = in->buf->last;
			in->buf->temporary = 0;
		}else {
			return NGX_ERROR;
		}
	}else {
		in = ngx_http_clojure_get_and_copy_bufs(r->pool, &ctx->free, message, len, flag);
		if (in == NULL) {
			ngx_log_error(NGX_LOG_ERR, r->connection->log, 0,
					"ngx_http_clojure_hijack_send:"
					"not enough memory, ngx_http_clojure_get_and_copy_bufs fail");
			return NGX_ERROR;
		}
	}

	return ngx_http_clojure_hijack_send_chain(r, in, flag);
}

static jlong JNICALL jni_ngx_http_hijack_send_header(JNIEnv *env, jclass cls, jlong req, jint flag) {
	ngx_http_request_t *r = (ngx_http_request_t *)(uintptr_t)req;
	ngx_http_clojure_module_ctx_t *ctx;
	ngx_int_t rc;

	if (r->pool == NULL) {
		ngx_log_error(NGX_LOG_ERR, ngx_http_clojure_global_cycle->log, 0, "jni_ngx_http_hijack_send_header:"
				"can not send header because the request was closed");
		return NGX_ERROR;
	}

	ctx = (ngx_http_clojure_module_ctx_t *)ngx_http_get_module_ctx(r, ngx_http_clojure_module);
	if (flag & NGX_CLOJURE_BUF_IGNORE_FILTER_FLAG) {
		ctx->ignore_filters = 1;
	}else {
		ctx->ignore_filters = 0;
	}
	rc = ctx->ignore_filters ? ngx_http_header_filter(r) : ngx_http_send_header(r);

	if (rc == NGX_OK || rc == NGX_AGAIN) {
		if (flag & NGX_CLOJURE_BUF_LAST_FLAG || flag & NGX_CLOJURE_BUF_FLUSH_FLAG) {
			rc = ngx_http_clojure_hijack_send(r, 0, 0, flag);
			if (rc != NGX_OK) {
				ngx_http_finalize_request(r, rc);
			}
		}
		return rc;
	}else {
		ngx_http_finalize_request(r, rc);
		return rc;
	}
}

static jlong JNICALL jni_ngx_http_hijack_send(JNIEnv *env, jclass cls, jlong req, jobject obj, jlong offset, jlong len, jint flag) {

	ngx_int_t rc = ngx_http_clojure_hijack_send((ngx_http_request_t *) (uintptr_t) req,
			ngx_http_clojure_abs_off_addr(obj, offset), len, flag);
	if (rc != NGX_OK) {
		ngx_http_finalize_request((ngx_http_request_t *)(uintptr_t)req, rc);
	}
	return rc;
}

static jlong JNICALL jni_ngx_http_hijack_send_chain(JNIEnv *env, jclass cls, jlong req, jlong chain,  jint flag) {
	ngx_int_t rc = ngx_http_clojure_hijack_send_chain((ngx_http_request_t *)(uintptr_t)req, (ngx_chain_t *)(uintptr_t)chain, flag);
	if (rc != NGX_OK) {
		ngx_http_finalize_request((ngx_http_request_t *)(uintptr_t)req, rc);
	}
	return rc;
}

static void ngx_http_clojure_cleanup_handler(void *data) {
	ngx_http_clojure_clean_up_data_t *cd = (ngx_http_clojure_clean_up_data_t *)data;
	(*jvm_env)->CallVoidMethod(jvm_env, cd->listener, nc_rt_channel_listener_onclose_mid, cd->data);
	exception_handle(0 == 0, jvm_env,
			(*jvm_env)->DeleteGlobalRef(jvm_env, cd->listener);
	        (*jvm_env)->DeleteGlobalRef(jvm_env, cd->data);
	        return);
	(*jvm_env)->DeleteGlobalRef(jvm_env, cd->listener);
	(*jvm_env)->DeleteGlobalRef(jvm_env, cd->data);
}

static void ngx_http_clojure_check_broken_connection(ngx_http_request_t *r, ngx_event_t *ev) {
	int n;
	char buf[1];
	ngx_err_t err;
	ngx_int_t event;
	ngx_connection_t *c;

	ngx_log_debug2(NGX_LOG_DEBUG_HTTP, ev->log, 0, "http upstream check client, write event:%d, \"%V\"", ev->write, &r->uri);

	c = r->connection;

	if (c->error) {
		if ((ngx_event_flags & NGX_USE_LEVEL_EVENT) && ev->active) {

			event = ev->write ? NGX_WRITE_EVENT : NGX_READ_EVENT;

			if (ngx_del_event(ev, event, 0) != NGX_OK) {
				ngx_http_finalize_request(r, NGX_HTTP_INTERNAL_SERVER_ERROR);
				return;
			}
		}
		ngx_http_finalize_request(r, NGX_HTTP_CLIENT_CLOSED_REQUEST);
		return;
	}

#if (NGX_HTTP_SPDY)
	if (r->spdy_stream) {
		return;
	}
#endif

#if (NGX_HAVE_KQUEUE)

	if (ngx_event_flags & NGX_USE_KQUEUE_EVENT) {

		if (!ev->pending_eof) {
			return;
		}

		ev->eof = 1;
		c->error = 1;

		if (ev->kq_errno) {
			ev->error = 1;
		}

		ngx_log_error(NGX_LOG_INFO, ev->log, ev->kq_errno,
				"kevent() reported that client prematurely closed "
				"connection");

		ngx_http_finalize_request(r, NGX_HTTP_CLIENT_CLOSED_REQUEST);
		return;
	}

#endif

#if (NGX_HAVE_EPOLLRDHUP)

	if ((ngx_event_flags & NGX_USE_EPOLL_EVENT) && ev->pending_eof) {
		socklen_t len;

		ev->eof = 1;
		c->error = 1;

		err = 0;
		len = sizeof(ngx_err_t);

		/*
		 * BSDs and Linux return 0 and set a pending error in err
		 * Solaris returns -1 and sets errno
		 */

		if (getsockopt(c->fd, SOL_SOCKET, SO_ERROR, (void *) &err, &len) == -1) {
			err = ngx_socket_errno;
		}

		if (err) {
			ev->error = 1;
		}

		ngx_log_error(NGX_LOG_INFO, ev->log, err, "epoll_wait() reported that client prematurely closed "
				"connection");
		ngx_http_finalize_request(r, NGX_HTTP_CLIENT_CLOSED_REQUEST);
		return;
	}

#endif

	n = recv(c->fd, buf, 1, MSG_PEEK);

	err = ngx_socket_errno;

	ngx_log_debug1(NGX_LOG_DEBUG_HTTP, ev->log, err, "http clojure module recv(): %d", n);

	if (ev->write && (n >= 0 || err == NGX_EAGAIN)) {
		return;
	}

	if ((ngx_event_flags & NGX_USE_LEVEL_EVENT) && ev->active) {

		event = ev->write ? NGX_WRITE_EVENT : NGX_READ_EVENT;

		if (ngx_del_event(ev, event, 0) != NGX_OK) {
			ngx_http_finalize_request(r, NGX_HTTP_INTERNAL_SERVER_ERROR);
			return;
		}
	}

	if (n > 0) {
		return;
	}

	if (n == -1) {
		if (err == NGX_EAGAIN) {
			return;
		}

		ev->error = 1;

	} else { /* n == 0 */
		err = 0;
	}

	ev->eof = 1;
	c->error = 1;

	ngx_log_error(NGX_LOG_INFO, ev->log, err, "client prematurely closed connection");

	ngx_http_finalize_request(r, NGX_HTTP_CLIENT_CLOSED_REQUEST);
}

static void ngx_http_clojure_rd_check_broken_connection(ngx_http_request_t *r){
    ngx_http_clojure_check_broken_connection(r, r->connection->read);
}

/*static void ngx_http_clojure_wt_check_broken_connection(ngx_http_request_t *r){
    ngx_http_clojure_check_broken_connection(r, r->connection->write);
}*/

static jlong JNICALL jni_ngx_http_cleanup_add(JNIEnv *env, jclass cls, jlong req, jobject listener, jobject data) {
	ngx_http_request_t *r = (ngx_http_request_t *)(uintptr_t)req;
	ngx_http_cleanup_t *cu = ngx_http_cleanup_add(r, sizeof(ngx_http_clojure_clean_up_data_t));
	cu->handler = ngx_http_clojure_cleanup_handler;
	((ngx_http_clojure_clean_up_data_t *)cu->data)->listener = (*env)->NewGlobalRef(env, listener);
	exception_handle(((ngx_http_clojure_clean_up_data_t *)cu->data)->listener == NULL, env, return NGX_HTTP_CLOJURE_JVM_ERR);
	((ngx_http_clojure_clean_up_data_t *)cu->data)->data = (*env)->NewGlobalRef(env, data);
	exception_handle(((ngx_http_clojure_clean_up_data_t *)cu->data)->data == NULL, env, return NGX_HTTP_CLOJURE_JVM_ERR);
	return NGX_OK;
}

static void JNICALL jni_ngx_http_finalize_request (JNIEnv *env, jclass cls, jlong r , jlong rc) {
	ngx_http_finalize_request((ngx_http_request_t *)(uintptr_t)r, (ngx_int_t)rc);
}

static jlong JNICALL jni_ngx_http_clojure_mem_init_ngx_buf(JNIEnv *env, jclass cls, jlong buf, jobject obj, jlong offset, jlong len, jint last_buf) {
	ngx_buf_t *b = (ngx_buf_t *)(uintptr_t)buf;

	if (len > 0) {
		ngx_memcpy(b->pos, ngx_http_clojure_abs_off_addr(obj, offset), len);
		b->last = b->pos + len;
	}

	if (last_buf & NGX_BUF_LAST_OF_RESPONSE) {
		b->last_buf = b->last_in_chain = 1;
	}else {
		b->last_in_chain = last_buf & NGX_BUF_LAST_OF_CHAIN;
	}
	return (uintptr_t)b;
}

static jlong JNICALL jni_ngx_http_clojure_mem_get_obj_addr(JNIEnv *env, jclass cls, jobject obj){
	return obj ? (*(uintptr_t*)obj) : 0;
}

static jlong JNICALL jni_ngx_http_clojure_mem_get_list_size(JNIEnv *env, jclass cls, jlong l) {
	ngx_list_t *list = (ngx_list_t *)(uintptr_t)l;
	ngx_list_part_t *part = &list->part;
	jlong c = 0;

	while (part != NULL) {
		c += part->nelts;
		part = part->next;
	}
	return c;
}

static jlong JNICALL jni_ngx_http_clojure_mem_get_list_item(JNIEnv *env, jclass cls, jlong l, jlong i) {
	ngx_list_t *list = (ngx_list_t *)(uintptr_t)l;
	ngx_list_part_t *part = &list->part;

	while (part != NULL && (size_t)i > part->nelts) {
		i -= part->nelts;
		part = part->next;
	}
	if (part == NULL) {
		return 0;
	}
	return (uintptr_t)  part->elts + i * list->size;

}

static void JNICALL jni_ngx_http_clojure_mem_copy_to_obj(JNIEnv *env, jclass cls, jlong src, jobject obj, jlong offset, jlong len) {
	memcpy(ngx_http_clojure_abs_off_addr(obj, offset), (void *)(uintptr_t)src, len);
}

static void JNICALL jni_ngx_http_clojure_mem_copy_to_addr(JNIEnv *env, jclass cls, jobject src, jlong offset, jlong dest, jlong len) {
	memcpy((void*)(uintptr_t)dest, ngx_http_clojure_abs_off_addr(src, offset), len);
}

/*
 * this function is slow for iterate all headers so it should be only used to get unknown headers
 */
static jlong JNICALL jni_ngx_http_clojure_mem_get_header(JNIEnv *env, jclass cls, jlong headers_in, jlong name, jlong len) {
    ngx_list_part_t *part = &((ngx_http_headers_in_t *)(uintptr_t) headers_in)->headers.part;
    ngx_table_elt_t *h = part->elts;
    ngx_uint_t i = 0;

    for (i = 0; /* void */ ; i++) {
        if (i >= part->nelts) {
            if (part->next == NULL) {
                break;
            }

            part = part->next;
            h = part->elts;
            i = 0;
        }

        if ((size_t)len != h[i].key.len || ngx_strcasecmp((u_char *)(uintptr_t)name, h[i].key.data) != 0) {
            continue;
        }
        return (uintptr_t)&h[i];
    }
    return 0;
}

static jlong JNICALL jni_ngx_http_clojure_mem_get_variable(JNIEnv *env, jclass cls,  jlong r, jlong nname, jlong varlenPtr) {
	ngx_http_request_t *req = (ngx_http_request_t *)(uintptr_t) r;
	ngx_str_t *name = (ngx_str_t *)(uintptr_t)nname;
	ngx_http_variable_value_t *vp = ngx_http_get_variable(req, name, ngx_hash_key(name->data, name->len));
	if (vp == NULL || vp->not_found) {
		return 0;
	}
	*((u_int *)(uintptr_t)varlenPtr) = vp->len;
	return (uintptr_t)vp;
}

static jlong JNICALL jni_ngx_http_clojure_mem_set_variable(JNIEnv *env, jclass cls,  jlong r, jlong nname, jlong val, jlong vlen) {
	ngx_http_request_t *req = (ngx_http_request_t *) (uintptr_t) r;
	ngx_str_t *name = (ngx_str_t *) (uintptr_t) nname;
	ngx_http_variable_t *v;
	ngx_http_variable_value_t *vv;
	ngx_http_core_main_conf_t *cmcf;

    cmcf = ngx_http_get_module_main_conf(req, ngx_http_core_module);

    v = ngx_hash_find(&cmcf->variables_hash, ngx_hash_key(name->data, name->len), name->data, name->len);

    if (v) {
		if (!(v->flags & NGX_HTTP_VAR_CHANGEABLE)) {
			return NGX_HTTP_CLOJURE_MEM_ERR_VAR_UNCHANGABLE;
		}
        if (v->flags & NGX_HTTP_VAR_INDEXED) {
            vv = ngx_http_get_flushed_variable(req, v->index);
            vv->len = (unsigned)vlen;
            vv->data = (u_char *)(uintptr_t)val;
            return NGX_OK;
        } else {
            vv = ngx_palloc(req->pool, sizeof(ngx_http_variable_value_t));
            if (vv == NULL) {
            	return NGX_HTTP_CLOJURE_MEM_ERR_MALLOC;
            }
            vv->len = (unsigned)vlen;
            vv->data = (u_char *)(uintptr_t)val;
            v->set_handler(req, vv, v->data);
            return NGX_OK;
        }
    }
    return NGX_HTTP_CLOJURE_MEM_ERR_VAR_NOT_FOUND;
}

static void JNICALL jni_ngx_http_clojure_mem_inc_req_count(JNIEnv *env, jclass cls, jlong r) {
	ngx_http_request_t *req = (ngx_http_request_t *)(uintptr_t) r;
	req->main->count ++;
}

static void JNICALL jni_ngx_http_clojure_mem_continue_current_phase(JNIEnv *env, jclass cls, jlong r) {
	ngx_http_request_t *req = (ngx_http_request_t *)(uintptr_t) r;
	req->write_event_handler(req);
}

static jlong JNICALL jni_ngx_http_clojure_mem_get_module_ctx_phase(JNIEnv *env, jclass cls, jlong r) {
	ngx_http_request_t *req = (ngx_http_request_t *)(uintptr_t) r;
	ngx_http_clojure_module_ctx_t *ctx = ngx_http_get_module_ctx(req, ngx_http_clojure_module);
	return ctx == NULL ? -1 : (jlong)ctx->phrase;
}

#define log_debug0(log, msg) do{ \
	ngx_log_debug0(NGX_LOG_DEBUG_HTTP, log, 0, msg); \
}while(0);

#define log_debug1(log, msg, a1) do{ \
	ngx_log_debug1(NGX_LOG_DEBUG_HTTP, log, 0, msg, a1); \
}while(0);

#if defined(_WIN32) || defined(WIN32)
#define log_debug2(log, msg, a1, a2) do{ \
	ngx_log_debug2(NGX_LOG_DEBUG_HTTP, log, 0, msg, a1, a2); \
	if (log->log_level & NGX_LOG_DEBUG_HTTP) { \
		printf(msg, a1, a2);\
	} \
}while(0);
#else
#define log_debug2(log, msg, a1, a2) do{ \
	ngx_log_debug2(NGX_LOG_DEBUG_HTTP, log, 0, msg, a1, a2); \
}while(0);
#endif


//we now use request_body_file variable
//static jlong JNICALL jni_ngx_http_clojure_mem_get_body_tmp_file(JNIEnv *env, jclass cls, jlong r) {
//	return &((ngx_http_request_t *) r)->request_body->temp_file->file.name;
//}

static int ngx_http_clojure_init_memory_util_flag = NGX_HTTP_CLOJURE_JVM_ERR;





static int ngx_http_clojure_pipe(ngx_socket_t fds[2]) {

#if defined(_WIN32) || defined(WIN32)
	SOCKET s;
	struct sockaddr_in serv_addr;
	int len = sizeof( serv_addr );

	fds[0] = fds[1] = INVALID_SOCKET;

	if ( ( s = socket( AF_INET, SOCK_STREAM, 0 ) ) == INVALID_SOCKET ) {
		log_debug1(ngx_http_clojure_global_cycle->log, "ngx clojure:ngx_http_clojure_pipe failed to create socket: %ui", WSAGetLastError());
		return -1;
	}

	memset( &serv_addr, 0, sizeof( serv_addr ) );
	serv_addr.sin_family = AF_INET;
	serv_addr.sin_port = htons(0);
	serv_addr.sin_addr.s_addr = htonl(INADDR_LOOPBACK);

	if (bind(s, (SOCKADDR *) & serv_addr, len) == SOCKET_ERROR) {
		log_debug1(ngx_http_clojure_global_cycle->log, "ngx clojure:ngx_http_clojure_pipe failed to bind: %ui", WSAGetLastError());
		closesocket(s);
		return -1;
	}

	if (listen(s, 1) == SOCKET_ERROR) {
		log_debug1(ngx_http_clojure_global_cycle->log, "ngx clojure:ngx_http_clojure_pipe failed to listen: %ui", WSAGetLastError());
		closesocket(s);
		return -1;
	}

	if (getsockname(s, (SOCKADDR *) & serv_addr, &len) == SOCKET_ERROR) {
		log_debug1(ngx_http_clojure_global_cycle->log, "ngx clojure:ngx_http_clojure_pipe failed to getsockname: %ui", WSAGetLastError());
		closesocket(s);
		return -1;
	}

	if ((fds[1] = socket(PF_INET, SOCK_STREAM, 0)) == INVALID_SOCKET) {
		log_debug1(ngx_http_clojure_global_cycle->log, "ngx clojure:ngx_http_clojure_pipe failed to create socket 2: %ui", WSAGetLastError());
		closesocket(s);
		return -1;
	}

	if (connect(fds[1], (SOCKADDR *) & serv_addr, len) == SOCKET_ERROR) {
		log_debug1(ngx_http_clojure_global_cycle->log, "ngx clojure:ngx_http_clojure_pipe failed to connect socket: %ui", WSAGetLastError());
		closesocket(s);
		return -1;
	}

	if ((fds[0] = accept(s, (SOCKADDR *) & serv_addr, &len)) == INVALID_SOCKET) {
		log_debug1(ngx_http_clojure_global_cycle->log, "ngx clojure:ngx_http_clojure_pipe failed to accept socket 2: %ui", WSAGetLastError());
		closesocket(fds[1]);
		fds[1] = INVALID_SOCKET;
		closesocket(s);
		return -1;
	}
	closesocket(s);
#else
	if (pipe(fds) != 0) {
		ngx_log_error(NGX_LOG_ERR, ngx_http_clojure_global_cycle->log, ngx_errno,
		                          "ngx clojure:ngx_http_clojure_pipe failed to invoke pipe(fds)");
		return -1;
	}
#endif
	if (ngx_nonblocking(nc_jvm_worker_pipe_fds[0]) == -1) {
		ngx_log_error(NGX_LOG_ERR, ngx_http_clojure_global_cycle->log, errno, "ngx clojure create worker_pipe at ngx_nonblocking(fds[0]) failed");
		return -1;
	}

	if (ngx_nonblocking(nc_jvm_worker_pipe_fds[1]) == -1) {
		ngx_log_error(NGX_LOG_ERR, ngx_http_clojure_global_cycle->log, errno, "ngx clojure: create worker_pipe at ngx_nonblocking(fds[1]) failed");
		return -1;
	}
	return 0;
}

static int ngx_http_clojure_pipe_read(ngx_socket_t fd, void *buf, size_t size) {


#if defined(_WIN32) || defined(WIN32)
	int ret = recv(fd, buf, size, 0);

	if (ret < 0) {
		int err = WSAGetLastError();
		/* EOF (win32 socket based implementation) or Resource temporarily unavailable. */
		if (err == WSAECONNRESET || err == WSAEWOULDBLOCK) {
			ret = 0;
		}else {
			ngx_log_error(NGX_LOG_ERR, ngx_http_clojure_global_cycle->log, 0,
								"ngx clojure: can not recv, returns -1, err: %d:%s",err, strerror(err));
		}
	}
	return ret;

#else
	ssize_t rdc = 0;
AGAIN:
	if ((rdc = read(fd, buf, size)) < 0){
			if (errno == EINTR) {
				log_debug0(ngx_http_clojure_global_cycle->log, "ngx clojure: ngx clojure read event interrupted system call, try again");
				goto AGAIN;
			}else if (errno == EAGAIN) {
				return 0;
			}
			ngx_log_error(NGX_LOG_ERR, ngx_http_clojure_global_cycle->log, 0,
					"ngx clojure: can not recv, returns -1, err: %d:%s",errno, strerror(errno));
	}
	return rdc;
#endif

}

static int ngx_http_clojure_pipe_write(ngx_socket_t fd, void *buf, size_t size) {

#if defined(_WIN32) || defined(WIN32)
	return send(fd, buf, size, 0);
#else
	return write(fd, buf, size);
#endif

}

static int ngx_http_clojure_pipe_close(ngx_socket_t fd) {

#if defined(_WIN32) || defined(WIN32)
	return closesocket(fd);
#else
	return close(fd);
#endif

}

static ngx_int_t ngx_http_clojure_post_event(ngx_socket_t fd, void *e, size_t size) {
	/*TODO: handle EGAIN*/
	ngx_int_t wc;
#if !(NGX_WIN32)
	if (size > PIPE_BUF) {
		size = PIPE_BUF; /*cut off to make sure atomic write & read of a whole event */
	}
#endif
	wc = ngx_http_clojure_pipe_write(fd, e, size);
	if (wc != (ngx_int_t)size) {
		ngx_log_error(NGX_LOG_ERR, ngx_http_clojure_global_cycle->log, 0,
				"jni_ngx_http_clojure_mem_post_event write count : %zu < %zu",
				wc, 8);
		return wc;
	}
	return 0;
}

static ngx_int_t ngx_http_clojure_broadcast_event(void *e, size_t size, int has_self) {
#if !(NGX_WIN32)
	int i = 0;
	int s = 0;
	int rc = 0;
	for (i = 0; i < nc_ngx_workers; i++) {
		while (ngx_processes[s].channel[0] == -1) {
			s++;
		}

		if (s == ngx_process_slot && !has_self) {
			s++;
			continue;
		}

		if (rc == 0) { /*only store first error code*/
			rc = ngx_http_clojure_post_event(nc_ngx_worker_pipes_fds[s][1], e, size);
		}else {
			ngx_http_clojure_post_event(nc_ngx_worker_pipes_fds[s][1], e, size);
		}
		s++;
	}
	return rc;
#else
	/*so far on windows nginx supports only one worker to accpet http requests
	 *see known_issues from http://nginx.org/en/docs/windows.html#known_issues)
	 *TODO: use shared socket handle between workers to git rid of this limitation*/
	if (has_self) {
		return ngx_http_clojure_post_event(nc_jvm_worker_pipe_fds[1], e, size);
	}
	return NGX_OK;
#endif
}

#define ngx_http_clojure_mem_complex_event_buf_helper(buf, e, data, off, len) \
	do { \
		char *src = ngx_http_clojure_abs_off_addr(data, off); \
		len = (int)(0xffffLL & e); \
		if (len > (int)sizeof(buf) - 8) { \
				len = sizeof(buf) - 8; \
		} \
		e &= 0xff00000000000000LL; \
		e |= len; \
		memcpy(buf, &e, 8); \
		memcpy(buf + 8, src, len); \
    }while(0)

static jlong JNICALL jni_ngx_http_clojure_mem_post_event(JNIEnv *env, jclass cls, jlong e, jobject data, jlong off) {
	/*sizeof(jlong) is zero on win32 vc2010, so we have to use const 8*/
	int rc;
	if (0x8000000000000000LL & e) {
#if !(NGX_WIN32)
	    char buf[PIPE_BUF];
#else
	    char buf[4096];
#endif
		int len;
		ngx_http_clojure_mem_complex_event_buf_helper(buf, e, data, off, len);
		rc = ngx_http_clojure_post_event(nc_jvm_worker_pipe_fds[1], buf, len + 8);
		log_debug2(ngx_http_clojure_global_cycle->log, "ngx clojure: ngx clojure post event %" PRIu64 ", rc:%d", e,  rc);
	}else {
		rc = ngx_http_clojure_post_event(nc_jvm_worker_pipe_fds[1], &e, 8);
		log_debug2(ngx_http_clojure_global_cycle->log, "ngx clojure: ngx clojure post event %" PRIu64 ", rc:%d", e,  rc);
	}
	return (jlong)rc;
}


static jlong JNICALL jni_ngx_http_clojure_mem_broadcast_event(JNIEnv *env, jclass cls, jlong e, jobject data, jlong off,jlong has_self) {
	/*sizeof(jlong) is zero on win32 vc2010, so we have to use const 8*/
	int rc;
	if (0x8000000000000000LL & e) {
#if !(NGX_WIN32)
	    char buf[PIPE_BUF];
#else
	    char buf[4096];
#endif
		int len;
		ngx_http_clojure_mem_complex_event_buf_helper(buf, e, data, off, len);
		rc = ngx_http_clojure_broadcast_event(buf, len + 8, (int)has_self);
		log_debug2(ngx_http_clojure_global_cycle->log, "ngx clojure: ngx clojure broadcast event %" PRIu64 ", rc:%d", e,  rc);
	}else {
		rc = ngx_http_clojure_broadcast_event(&e, 8, (int)has_self);
		log_debug2(ngx_http_clojure_global_cycle->log, "ngx clojure: ngx clojure broadcast event %" PRIu64 ", rc:%d", e,  rc);
	}
	return (jlong)rc;
}

static jlong JNICALL jni_ngx_http_clojure_mem_read_raw_pipe(JNIEnv *env, jclass cls, jlong fd, jobject buf, jlong off, jlong len) {
	return (jlong)ngx_http_clojure_pipe_read((ngx_socket_t)fd, ngx_http_clojure_abs_off_addr(buf, off), (size_t)len);
}


static int ngx_http_clojure_handle_post_event(jlong r) {
/*	JNIEnv *env;
	(*jvm)->AttachCurrentThread(jvm, (void**)&env, NULL);
	*/
	int rc = (*jvm_env)->CallStaticIntMethod(jvm_env, nc_rt_class,  nc_rt_handle_post_event_mid, r, (jlong)nc_jvm_worker_pipe_fds[0]);
	exception_handle(1, jvm_env, return NGX_ERROR);
	return rc;
}


static void ngx_http_clojure_jvm_worker_post_event_handler(ngx_event_t *e) {
	jlong rp;
	ngx_int_t rc;
	ssize_t rdc = 0;
	log_debug0(ngx_http_clojure_global_cycle->log, "ngx clojure: ngx clojure read event ......");

	e->ready = 0;
	do {
		/*sizeof(jlong) is zero on win32 vc2010, so we have to use const 8*/
		if ((rdc = ngx_http_clojure_pipe_read(nc_jvm_worker_pipe_fds[0], &rp, 8))
				< 0) {
			return;
		} else if (rdc > 0) {
			log_debug2(ngx_http_clojure_global_cycle->log,
					"ngx clojure: ngx clojure read event %" PRIu64 ", size: %d",
					rp, rdc);
			rc = ngx_http_clojure_handle_post_event(rp);
			if (rc != NGX_OK) {
				ngx_log_error(NGX_LOG_ERR, e->log, 0,
						"ngx clojure: ngx_http_clojure_handle_post_event failed, rc=%d", rc);
			}
		}
	} while (rdc != 0);

}

static  ngx_int_t ngx_http_clojure_jvm_worker_pipe_init(ngx_log_t *log) {
	ngx_connection_t *c;
	ngx_int_t rc;

	if (!nc_jvm_worker_pipe_fds[0]) {
		rc = ngx_http_clojure_pipe(nc_jvm_worker_pipe_fds);
		if (rc != 0) {
			ngx_log_error(NGX_LOG_ERR, log, 0, "ngx clojure: create worker_pipe failed");
			return NGX_ERROR;
		}
	}

	/*before v0.2.5 fd-1 is only used by another thread from thread pool so it needn't be nonblocking
	  but now fd-1 is also be used by another nginx worker process.*/
	if (ngx_nonblocking(nc_jvm_worker_pipe_fds[0]) == -1) {
		ngx_log_error(NGX_LOG_ERR, log, 0, "ngx clojure create worker_pipe at ngx_nonblocking(fds[0]) failed");
		return NGX_ERROR;
	}

	ngx_log_error(NGX_LOG_NOTICE, log, 0, "ngx clojure: init pipe for jvm worker, fds: %d, %d", nc_jvm_worker_pipe_fds[0], nc_jvm_worker_pipe_fds[1]);

	c = ngx_get_connection(nc_jvm_worker_pipe_fds[0], log);
	if (c == NULL) {
		ngx_http_clojure_pipe_close(nc_jvm_worker_pipe_fds[1]);
		ngx_http_clojure_pipe_close(nc_jvm_worker_pipe_fds[0]);
		ngx_log_error(NGX_LOG_ERR, log, 0, "ngx clojure: create worker_pipe at ngx_get_connection failed");
		return NGX_ERROR;
	}

	c->recv = ngx_recv;
	c->send = ngx_send;
	c->recv_chain = ngx_recv_chain;
	c->send_chain = ngx_send_chain;
	c->log = log;
	c->read->log = log;
	c->write->log = log;
	c->data = NULL;
	c->read->handler = ngx_http_clojure_jvm_worker_post_event_handler;

	rc = ngx_handle_read_event(c->read, 0);
	if (rc != NGX_OK) {
		ngx_log_error(NGX_LOG_ERR, log, 0, "ngx clojure: create worker_pipe at ngx_handle_read_event failed");
	}
	return rc;
}

int ngx_http_clojure_pipe_init_by_master(int workers) {
#if !(NGX_WIN32)
	int s = 0;
	int i = 0;
	nc_ngx_workers = workers;
	for (i = 0; i < workers; i++) {
		for (; s < ngx_last_process; s++) {
			if (ngx_processes[s].pid == -1) {
				break;
			}
		}
		if (!nc_ngx_worker_pipes_fds[s][0]) {
			if (ngx_http_clojure_pipe(nc_ngx_worker_pipes_fds[s]) != 0) {
				return NGX_ERROR;
			}
		}
		s++;
	}
#else
	nc_ngx_workers = workers;
#endif
	return NGX_OK;
}

static int ngx_http_clojure_pipe_init_by_worker(ngx_log_t *log) {
#if !(NGX_WIN32)
	nc_jvm_worker_pipe_fds[0] = nc_ngx_worker_pipes_fds[ngx_process_slot][0];
	nc_jvm_worker_pipe_fds[1] = nc_ngx_worker_pipes_fds[ngx_process_slot][1];
#endif
	return ngx_http_clojure_jvm_worker_pipe_init(log);
}

int ngx_http_clojure_check_memory_util() {
	return ngx_http_clojure_init_memory_util_flag;
}

int ngx_http_clojure_init_memory_util(ngx_int_t jvm_workers, ngx_log_t *log) {
	jlong MEM_INDEX[NGX_HTTP_CLOJURE_MEM_IDX_END];
	JNIEnv *env;
	JNINativeMethod nms[] = {
			{"ngx_palloc", "(JJ)J", jni_ngx_palloc},
			{"ngx_pcalloc", "(JJ)J", jni_ngx_pcalloc},
			{"ngx_array_create", "(JJJ)J",jni_ngx_array_create},
			{"ngx_array_init", "(JJJJ)J", jni_ngx_array_init},
			{"ngx_array_push_n", "(JJ)J", jni_ngx_array_push_n},
			{"ngx_list_create", "(JJJ)J", jni_ngx_list_create},
			{"ngx_list_init", "(JJJJ)J", jni_ngx_list_init},
			{"ngx_list_push", "(J)J", jni_ngx_list_push},
			{"ngx_create_temp_buf", "(JJ)J", jni_ngx_create_temp_buf},
			{"ngx_create_file_buf", "(JJJI)J", jni_ngx_create_file_buf},
			{"ngx_http_set_content_type", "(J)J", jni_ngx_http_set_content_type},
			{"ngx_http_send_header", "(J)J", jni_ngx_http_send_header},
			{"ngx_http_output_filter", "(JJ)J", jni_ngx_http_output_filter},
			{"ngx_http_finalize_request", "(JJ)V", jni_ngx_http_finalize_request},
			{"ngx_http_clojure_mem_init_ngx_buf", "(JLjava/lang/Object;JJI)J", jni_ngx_http_clojure_mem_init_ngx_buf}, //jlong buf, jlong obj, jlong offset, jlong len, jint last_buf
			{"ngx_http_clojure_mem_get_obj_addr", "(Ljava/lang/Object;)J", jni_ngx_http_clojure_mem_get_obj_addr},
			{"ngx_http_clojure_mem_get_list_size", "(J)J", jni_ngx_http_clojure_mem_get_list_size},
			{"ngx_http_clojure_mem_get_list_item", "(JJ)J", jni_ngx_http_clojure_mem_get_list_item},
			{"ngx_http_clojure_mem_copy_to_obj", "(JLjava/lang/Object;JJ)V", jni_ngx_http_clojure_mem_copy_to_obj},
			{"ngx_http_clojure_mem_copy_to_addr", "(Ljava/lang/Object;JJJ)V", jni_ngx_http_clojure_mem_copy_to_addr},
			{"ngx_http_clojure_mem_get_header", "(JJJ)J", jni_ngx_http_clojure_mem_get_header},
			{"ngx_http_clojure_mem_get_variable", "(JJJ)J", jni_ngx_http_clojure_mem_get_variable},
			{"ngx_http_clojure_mem_set_variable", "(JJJJ)J", jni_ngx_http_clojure_mem_set_variable},
			{"ngx_http_clojure_mem_inc_req_count", "(J)V", jni_ngx_http_clojure_mem_inc_req_count},
			{"ngx_http_clojure_mem_continue_current_phase", "(J)V", jni_ngx_http_clojure_mem_continue_current_phase},
			{"ngx_http_clojure_mem_get_module_ctx_phase", "(J)J", jni_ngx_http_clojure_mem_get_module_ctx_phase},
			{"ngx_http_clojure_mem_post_event", "(JLjava/lang/Object;J)J", jni_ngx_http_clojure_mem_post_event},
			{"ngx_http_clojure_mem_broadcast_event", "(JLjava/lang/Object;JJ)J", jni_ngx_http_clojure_mem_broadcast_event},
			{"ngx_http_clojure_mem_read_raw_pipe", "(JLjava/lang/Object;JJ)J", jni_ngx_http_clojure_mem_read_raw_pipe},
			{"ngx_http_hijack_send", "(JLjava/lang/Object;JJI)J", jni_ngx_http_hijack_send},
			{"ngx_http_hijack_send_header", "(JI)J", jni_ngx_http_hijack_send_header},
			{"ngx_http_hijack_send_chain", "(JJI)J", jni_ngx_http_hijack_send_chain},
			{"ngx_http_cleanup_add", "(JLnginx/clojure/ChannelListener;Ljava/lang/Object;)J", jni_ngx_http_cleanup_add}
//			{"ngx_http_clojure_mem_get_body_tmp_file", "(J)J", jni_ngx_http_clojure_mem_get_body_tmp_file}
	};
	jmethodID nc_rt_init_mid;

	ngx_http_clojure_get_jvm(&jvm);

	if (ngx_http_clojure_init_memory_util_flag == NGX_HTTP_CLOJURE_JVM_OK) {
		return NGX_HTTP_CLOJURE_JVM_OK;
	}

	if (ngx_http_clojure_pipe_init_by_worker(log) != NGX_OK) {
		return NGX_HTTP_CLOJURE_JVM_ERR_INIT_PIPE;
	}

	memset(MEM_INDEX, -1, NGX_HTTP_CLOJURE_MEM_IDX_END * sizeof(jlong));
	MEM_INDEX[NGX_HTTP_CLOJURE_UINT_SIZE_IDX] = NGX_HTTP_CLOJURE_UINT_SIZE;
	MEM_INDEX[NGX_HTTP_CLOJURE_PTR_SIZE_IDX] = NGX_HTTP_CLOJURE_PTR_SIZE;
	MEM_INDEX[NGX_HTTP_CLOJURE_STRT_SIZE_IDX] = 	NGX_HTTP_CLOJURE_STRT_SIZE;
	MEM_INDEX[NGX_HTTP_CLOJURE_STR_LEN_IDX] =	NGX_HTTP_CLOJURE_STR_LEN_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_STR_DATA_IDX] = NGX_HTTP_CLOJURE_STR_DATA_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_SIZET_SIZE_IDX] = NGX_HTTP_CLOJURE_SIZET_SIZE;
	MEM_INDEX[NGX_HTTP_CLOJURE_OFFT_SIZE_IDX] = NGX_HTTP_CLOJURE_OFFT_SIZE;

	MEM_INDEX[NGX_HTTP_CLOJURE_TELT_SIZE_IDX] = NGX_HTTP_CLOJURE_TELT_SIZE;
	MEM_INDEX[NGX_HTTP_CLOJURE_TEL_HASH_IDX] = NGX_HTTP_CLOJURE_TEL_HASH_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_TEL_KEY_IDX] = NGX_HTTP_CLOJURE_TEL_KEY_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_TEL_VALUE_IDX] = NGX_HTTP_CLOJURE_TEL_VALUE_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_TEL_LOWCASE_KEY_IDX] = NGX_HTTP_CLOJURE_TEL_LOWCASE_KEY_OFFSET;

	MEM_INDEX[NGX_HTTP_CLOJURE_CHAINT_SIZE_IDX] = NGX_HTTP_CLOJURE_CHAINT_SIZE;
	MEM_INDEX[NGX_HTTP_CLOJURE_CHAIN_BUF_IDX] = NGX_HTTP_CLOJURE_CHAIN_BUF_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_CHAIN_NEXT_IDX] = NGX_HTTP_CLOJURE_CHAIN_NEXT_OFFSET;

	MEM_INDEX[NGX_HTTP_CLOJURE_VARIABLET_SIZE_IDX] = NGX_HTTP_CLOJURE_VARIABLET_SIZE;
	MEM_INDEX[NGX_HTTP_CLOJURE_CORE_VARIABLES_ADDR_IDX] = NGX_HTTP_CLOJURE_CORE_VARIABLES_ADDR;
	MEM_INDEX[NGX_HTTP_CLOJURE_CORE_VARIABLES_LEN_IDX] = NGX_HTTP_CLOJURE_CORE_VARIABLES_LEN;

	MEM_INDEX[NGX_HTTP_CLOJURE_ARRAYT_SIZE_IDX] = NGX_HTTP_CLOJURE_ARRAYT_SIZE;
	MEM_INDEX[NGX_HTTP_CLOJURE_ARRAY_ELTS_IDX] = NGX_HTTP_CLOJURE_ARRAY_ELTS_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_ARRAY_NELTS_IDX] = NGX_HTTP_CLOJURE_ARRAY_NELTS_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_ARRAY_SIZE_IDX] = NGX_HTTP_CLOJURE_ARRAY_SIZE_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_ARRAY_NALLOC_IDX] = NGX_HTTP_CLOJURE_ARRAY_NALLOC_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_ARRAY_POOL_IDX] = NGX_HTTP_CLOJURE_ARRAY_POOL_OFFSET;

	MEM_INDEX[NGX_HTTP_CLOJURE_REQT_SIZE_IDX] = NGX_HTTP_CLOJURE_REQT_SIZE;
	MEM_INDEX[NGX_HTTP_CLOJURE_REQ_METHOD_IDX] = NGX_HTTP_CLOJURE_REQ_METHOD_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_REQ_URI_IDX] = NGX_HTTP_CLOJURE_REQ_URI_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_REQ_ARGS_IDX] = NGX_HTTP_CLOJURE_REQ_ARGS_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_REQ_HEADERS_IN_IDX] = NGX_HTTP_CLOJURE_REQ_HEADERS_IN_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_REQ_POOL_IDX] = NGX_HTTP_CLOJURE_REQ_POOL_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_REQ_HEADERS_OUT_IDX] = NGX_HTTP_CLOJURE_REQ_HEADERS_OUT_OFFSET;


	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSIT_SIZE_IDX] =  NGX_HTTP_CLOJURE_HEADERSIT_SIZE;
	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSI_HOST_IDX] =  NGX_HTTP_CLOJURE_HEADERSI_HOST_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSI_CONNECTION_IDX] = NGX_HTTP_CLOJURE_HEADERSI_CONNECTION_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSI_IF_MODIFIED_SINCE_IDX] = NGX_HTTP_CLOJURE_HEADERSI_IF_MODIFIED_SINCE_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSI_IF_UNMODIFIED_SINCE_IDX] =  NGX_HTTP_CLOJURE_HEADERSI_IF_UNMODIFIED_SINCE_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSI_USER_AGENT_IDX] =   NGX_HTTP_CLOJURE_HEADERSI_USER_AGENT_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSI_REFERER_IDX] =  NGX_HTTP_CLOJURE_HEADERSI_REFERER_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSI_CONTENT_LENGTH_IDX] =  NGX_HTTP_CLOJURE_HEADERSI_CONTENT_LENGTH_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSI_CONTENT_TYPE_IDX] =  NGX_HTTP_CLOJURE_HEADERSI_CONTENT_TYPE_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSI_RANGE_IDX] =  NGX_HTTP_CLOJURE_HEADERSI_RANGE_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSI_IF_RANGE_IDX] =  NGX_HTTP_CLOJURE_HEADERSI_IF_RANGE_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSI_TRANSFER_ENCODING_IDX] =  NGX_HTTP_CLOJURE_HEADERSI_TRANSFER_ENCODING_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSI_EXPECT_IDX] =  NGX_HTTP_CLOJURE_HEADERSI_EXPECT_OFFSET;

	#if (NGX_HTTP_GZIP)
	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSI_ACCEPT_ENCODING_IDX] =  NGX_HTTP_CLOJURE_HEADERSI_ACCEPT_ENCODING_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSI_VIA_IDX] =  NGX_HTTP_CLOJURE_HEADERSI_VIA_OFFSET;
	#endif

	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSI_AUTHORIZATION_IDX] =  NGX_HTTP_CLOJURE_HEADERSI_AUTHORIZATION_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSI_KEEP_ALIVE_IDX] =  NGX_HTTP_CLOJURE_HEADERSI_KEEP_ALIVE_OFFSET;

	#if (NGX_HTTP_PROXY || NGX_HTTP_REALIP || NGX_HTTP_GEO)
	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSI_X_FORWARDED_FOR_IDX] =  NGX_HTTP_CLOJURE_HEADERSI_X_FORWARDED_FOR_OFFSET;
	#endif

	#if (NGX_HTTP_REALIP)
	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSI_X_REAL_IP_IDX] = NGX_HTTP_CLOJURE_HEADERSI_X_REAL_IP_OFFSET;
	#endif

	#if (NGX_HTTP_HEADERS)
	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSI_ACCEPT_IDX] = NGX_HTTP_CLOJURE_HEADERSI_ACCEPT_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSI_ACCEPT_LANGUAGE_IDX] = NGX_HTTP_CLOJURE_HEADERSI_ACCEPT_LANGUAGE_OFFSET;
	#endif

	#if (NGX_HTTP_DAV)
	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSI_DEPTH_IDX] = NGX_HTTP_CLOJURE_HEADERSI_DEPTH_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSI_DESTINATION_IDX] =  NGX_HTTP_CLOJURE_HEADERSI_DESTINATION_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSI_OVERWRITE_IDX] =  NGX_HTTP_CLOJURE_HEADERSI_OVERWRITE_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSI_DATE_IDX] =  NGX_HTTP_CLOJURE_HEADERSI_DATE_OFFSET;
	#endif

	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSI_USER_IDX] =  NGX_HTTP_CLOJURE_HEADERSI_USER_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSI_PASSWD_IDX] =  NGX_HTTP_CLOJURE_HEADERSI_PASSWD_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSI_COOKIE_IDX] =  NGX_HTTP_CLOJURE_HEADERSI_COOKIE_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSI_SERVER_IDX] =  NGX_HTTP_CLOJURE_HEADERSI_SERVER_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSI_CONTENT_LENGTH_N_IDX] =  NGX_HTTP_CLOJURE_HEADERSI_CONTENT_LENGTH_N_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSI_KEEP_ALIVE_N_IDX] =  NGX_HTTP_CLOJURE_HEADERSI_KEEP_ALIVE_N_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSI_HEADERS_IDX] = NGX_HTTP_CLOJURE_HEADERSI_HEADERS_OFFSET;


	/*index for size of ngx_http_headers_out_t */
	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSOT_SIZE_IDX] = NGX_HTTP_CLOJURE_HEADERSOT_SIZE;
	/*field offset index for ngx_http_headers_out_t*/
	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSO_STATUS_IDX] =  NGX_HTTP_CLOJURE_HEADERSO_STATUS_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSO_STATUS_LINE_IDX] =  NGX_HTTP_CLOJURE_HEADERSO_STATUS_LINE_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSO_SERVER_IDX] =  NGX_HTTP_CLOJURE_HEADERSO_SERVER_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSO_DATE_IDX] =  NGX_HTTP_CLOJURE_HEADERSO_DATE_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSO_CONTENT_LENGTH_IDX] =  NGX_HTTP_CLOJURE_HEADERSO_CONTENT_LENGTH_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSO_CONTENT_ENCODING_IDX] =  NGX_HTTP_CLOJURE_HEADERSO_CONTENT_ENCODING_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSO_LOCATION_IDX] =  NGX_HTTP_CLOJURE_HEADERSO_LOCATION_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSO_REFRESH_IDX] =  NGX_HTTP_CLOJURE_HEADERSO_REFRESH_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSO_LAST_MODIFIED_IDX] =  NGX_HTTP_CLOJURE_HEADERSO_LAST_MODIFIED_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSO_CONTENT_RANGE_IDX] =  NGX_HTTP_CLOJURE_HEADERSO_CONTENT_RANGE_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSO_ACCEPT_RANGES_IDX] =  NGX_HTTP_CLOJURE_HEADERSO_ACCEPT_RANGES_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSO_WWW_AUTHENTICATE_IDX] =  NGX_HTTP_CLOJURE_HEADERSO_WWW_AUTHENTICATE_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSO_EXPIRES_IDX] =  NGX_HTTP_CLOJURE_HEADERSO_EXPIRES_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSO_ETAG_IDX] =  NGX_HTTP_CLOJURE_HEADERSO_ETAG_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSO_OVERRIDE_CHARSET_IDX] =  NGX_HTTP_CLOJURE_HEADERSO_OVERRIDE_CHARSET_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSO_CONTENT_TYPE_LEN_IDX] =  NGX_HTTP_CLOJURE_HEADERSO_CONTENT_TYPE_LEN_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSO_CONTENT_TYPE_IDX] =  NGX_HTTP_CLOJURE_HEADERSO_CONTENT_TYPE_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSO_CHARSET_IDX] =  NGX_HTTP_CLOJURE_HEADERSO_CHARSET_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSO_CONTENT_TYPE_LOWCASE_IDX] =  NGX_HTTP_CLOJURE_HEADERSO_CONTENT_TYPE_LOWCASE_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSO_CONTENT_TYPE_HASH_IDX] =  NGX_HTTP_CLOJURE_HEADERSO_CONTENT_TYPE_HASH_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSO_CACHE_CONTROL_IDX] =  NGX_HTTP_CLOJURE_HEADERSO_CACHE_CONTROL_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSO_CONTENT_LENGTH_N_IDX] =  NGX_HTTP_CLOJURE_HEADERSO_CONTENT_LENGTH_N_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSO_DATE_TIME_IDX] =  NGX_HTTP_CLOJURE_HEADERSO_DATE_TIME_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSO_LAST_MODIFIED_TIME_IDX] =  NGX_HTTP_CLOJURE_HEADERSO_LAST_MODIFIED_TIME_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSO_HEADERS_IDX] =  NGX_HTTP_CLOJURE_HEADERSO_HEADERS_OFFSET;

	MEM_INDEX[NGINX_CLOJURE_RT_WORKERS_ID] = jvm_workers;
	MEM_INDEX[NGINX_VER_ID] = nginx_version;
	MEM_INDEX[NGINX_CLOJURE_VER_ID] = nginx_clojure_ver;

//	(*jvm)->AttachCurrentThread(jvm, (void**)&env, NULL);
	ngx_http_clojure_get_env(&jvm_env);

	if (jvm_env == NULL) {
		return NGX_HTTP_CLOJURE_JVM_ERR_INIT_SOCKETAPI;
	}

	env = jvm_env;

    /*check nginx-clojure.jar version*/
	{
		jclass mc_class;
		jfieldID frtver;
		int rtver;
		mc_class = (*env)->FindClass(env, "nginx/clojure/MiniConstants");
		exception_handle(mc_class == NULL, env, return NGX_HTTP_CLOJURE_JVM_ERR);
		frtver = (*env)->GetStaticFieldID(env, mc_class, "NGINX_CLOJURE_RT_VER", "J");
		exception_handle(frtver == NULL, env, return NGX_HTTP_CLOJURE_JVM_ERR);
		rtver = (int)(*env)->GetStaticLongField(env, mc_class, frtver);
		exception_handle(0 == 0, env, return NGX_HTTP_CLOJURE_JVM_ERR);
		if (nginx_clojure_required_rt_lver > (int)rtver) {
			int f = rtver / 1000000;
			int s = rtver / 1000 - f * 1000;
			int t = rtver - s * 1000 - f * 1000000;
			int nf = nginx_clojure_required_rt_lver / 1000000;
			int ns = nginx_clojure_required_rt_lver / 1000 - nf * 1000;
			int nt = nginx_clojure_required_rt_lver - ns * 1000 - nf * 1000000;
			ngx_log_error(NGX_LOG_ERR, ngx_http_clojure_global_cycle->log, 0,
								"too low version jar of nginx-clojure, we need at least %d.%d.%d, but meet %d.%d.%d", nf, ns, nt, f, s, t);
			return NGX_HTTP_CLOJURE_JVM_ERR;
		}
	}

	nc_rt_class = (*env)->FindClass(env, "nginx/clojure/NginxClojureRT");
	exception_handle(nc_rt_class == NULL, env, return NGX_HTTP_CLOJURE_JVM_ERR);


	(*env)->RegisterNatives(env, nc_rt_class, nms, sizeof(nms) / sizeof(JNINativeMethod));
	exception_handle(0 == 0, env, return NGX_HTTP_CLOJURE_JVM_ERR);

	nc_rt_register_code_mid = (*env)->GetStaticMethodID(env, nc_rt_class, "registerCode", "(JJJ)I");
	exception_handle(nc_rt_register_code_mid == NULL, env, return NGX_HTTP_CLOJURE_JVM_ERR);

	nc_rt_eval_mid = (*env)->GetStaticMethodID(env, nc_rt_class, "eval", "(IJ)I");
	exception_handle(nc_rt_eval_mid == NULL, env, return NGX_HTTP_CLOJURE_JVM_ERR);

	nc_rt_init_mid = (*env)->GetStaticMethodID(env, nc_rt_class,"initMemIndex", "(J)V");
	exception_handle(nc_rt_init_mid == NULL, env, return NGX_HTTP_CLOJURE_JVM_ERR);

	nc_rt_handle_post_event_mid = (*env)->GetStaticMethodID(env, nc_rt_class,"handlePostEvent", "(JJ)I");
	exception_handle(nc_rt_handle_post_event_mid == NULL, env, return NGX_HTTP_CLOJURE_JVM_ERR);

	{
		jclass nc_cleanup_listener_class = (*env)->FindClass(env, "nginx/clojure/ChannelListener");
		exception_handle(nc_cleanup_listener_class == NULL, env, return NGX_HTTP_CLOJURE_JVM_ERR);
		nc_rt_channel_listener_onclose_mid = (*env)->GetMethodID(env, nc_cleanup_listener_class, "onClose", "(Ljava/lang/Object;)V");
		exception_handle(nc_rt_channel_listener_onclose_mid == NULL, env, return NGX_HTTP_CLOJURE_JVM_ERR);
	}

	(*env)->CallStaticVoidMethod(env, nc_rt_class, nc_rt_init_mid, MEM_INDEX);

	exception_handle(1, env, return NGX_HTTP_CLOJURE_JVM_ERR);
	return ngx_http_clojure_init_memory_util_flag = NGX_HTTP_CLOJURE_JVM_OK;
}




int ngx_http_clojure_register_script(ngx_str_t *handler_type, ngx_str_t *handler, ngx_str_t *code, ngx_int_t *pcid) {
	JNIEnv *env = jvm_env;
	*pcid = (int)(*env)->CallStaticIntMethod(env, nc_rt_class, nc_rt_register_code_mid, (jlong)(uintptr_t)handler_type, (jlong)(uintptr_t)handler, (jlong)(uintptr_t)code);
	if ((*env)->ExceptionOccurred(env)) {
		*pcid = -1;
		(*env)->ExceptionDescribe(env);
		(*env)->ExceptionClear(env);
		return NGX_HTTP_CLOJURE_JVM_ERR;
	}
	return NGX_HTTP_CLOJURE_JVM_OK;
}

int ngx_http_clojure_eval(int cid, void *r) {
	JNIEnv *env = jvm_env;
	int rc;
	log_debug1(ngx_http_clojure_global_cycle->log, "ngx clojure eval request: %ul", (uintptr_t)r);
	log_debug2(ngx_http_clojure_global_cycle->log, "ngx clojure eval request to jlong: %" PRIu64 ", size: %d", (jlong)(uintptr_t)r, 8);
	rc = (*env)->CallStaticIntMethod(env, nc_rt_class,  nc_rt_eval_mid, (jint)cid, (jlong)(uintptr_t)r);
	exception_handle(1, env, return 500);
	return rc;
}
