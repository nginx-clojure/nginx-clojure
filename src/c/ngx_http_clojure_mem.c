/*
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 */
#include <ngx_config.h>
#include "ngx_http_clojure_mem.h"
#include "ngx_http_clojure_jvm.h"
#include <ngx_http_config.h>


extern ngx_module_t  ngx_http_clojure_module;

int ngx_http_clojure_is_little_endian = 1;

static JavaVM *jvm = NULL;
static JNIEnv *jvm_env = NULL;
static jclass nc_rt_class;
static jmethodID nc_rt_eval_mid;
static jmethodID nc_rt_destory_mid;
static jmethodID nc_rt_register_code_mid;
static jmethodID nc_rt_handle_post_event_mid;
static jmethodID nc_rt_handle_channel_event_mid;

static int nc_ngx_workers;
static ngx_socket_t nc_ngx_worker_pipes_fds[NGX_MAX_PROCESSES][2];
static ngx_socket_t nc_jvm_worker_pipe_fds[2];

static ngx_str_t ngx_http_clojure_core_variables_names[] = {
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
    ngx_string("pid"),
    ngx_null_string
};

static ngx_str_t ngx_http_clojure_headers_names[] = {
		ngx_string("Accept"),
		ngx_string("Accept-Encoding"),
		ngx_string("Accept-Language"),
		ngx_string("Accept-Ranges"),
		ngx_string("Authorization"),
		ngx_string("Cache-Control"),
		ngx_string("Connection"),
		ngx_string("Content-Encoding"),
		ngx_string("Content-Length"),
		ngx_string("Content-Range"),
		ngx_string("Content-Type"),
		ngx_string("Cookie"),
		ngx_string("Date"),
		ngx_string("Depth"),
		ngx_string("Destination"),
		ngx_string("Etag"),
		ngx_string("Expect"),
		ngx_string("Expires"),
		ngx_string("Host"),
		ngx_string("If-Modified-Since"),
		ngx_string("If-Range"),
		ngx_string("If-Unmodified-Since"),
		ngx_string("Keep-Alive"),
		ngx_string("Last-Modified"),
		ngx_string("Location"),
		ngx_string("Overwrite"),
		ngx_string("Range"),
		ngx_string("Referer"),
		ngx_string("Refresh"),
		ngx_string("Server"),
		ngx_string("Transfer-Encoding"),
		ngx_string("User-Agent"),
		ngx_string("Via"),
		ngx_string("WWW-Authenticate"),
		ngx_string("X-Forwarded-For"),
		ngx_string("X-Real-Ip"),
		ngx_null_string
};

static ngx_str_t ngx_http_clojure_mime_types[] = {
		ngx_string("application/json"),
		ngx_string("application/x-javascript"),
		ngx_string("text/css"),
		ngx_string("text/html"),
		ngx_string("text/javascript"),
		ngx_string("text/json"),
		ngx_string("text/xml"),
		ngx_string("text/plain"),
		ngx_null_string
};

/*1000 indicates a normal closure, meaning that the purpose
 * for which the connection was established has been fulfilled.*/
static u_char WS_CLOSE_NORMAL_CLOSURE[] = { 0x03, 0xe8 };

/*1001 indicates that an endpoint is "going away", such as
 * a server going down or a browser having navigated away from a page.
static u_char WS_CLOSE_GOING_AWAY[] = { 0x03, 0xe9 };*/

/*1002 indicates that an endpoint is terminating the
 * connection due to a protocol error.*/
static u_char WS_CLOSE_PROTOCOL_ERROR[] = { 0x03, 0xea };

/*1003 indicates that an endpoint is terminating the
 * connection because it has received a type of data
 * it cannot accept (e.g., an endpoint that understands
 * only text data MAY send this if it receives a binary message).
static u_char WS_CLOSE_CANNOT_ACCEPT[] = { 0x03, 0xeb };*/

/*1006 is a reserved value and MUST NOT be set as a status code
 * in a Close control frame by an endpoint
static u_char WS_CLOSE_CLOSED_ABNORMALLY[] = { 0x03, 0xee };*/

/*1007 indicates that an endpoint is terminating the
 * connection because it has received data within a message
 * that was not consistent with the type of the message
 *  (e.g., non-UTF-8 data within a text message).*/
static u_char WS_CLOSE_NOT_CONSISTENT[] = { 0x03, 0xef };

/*1009 indicates that an endpoint is terminating the connection
 * because it has received a message that is too big for it to process.
static u_char WS_CLOSE_TOO_BIG[] = { 0x03, 0xf1 };*/

/*1010 indicates that an endpoint (client) is terminating the connection
 * because it has expected the server to negotiate one or more extension,
 * but the server didn't return them in the response message of the WebSocket handshake.
static u_char WS_CLOSE_NO_EXTENSION[] = { 0x03, 0xf2 };*/

/*1011 indicates that a server is terminating the connection
 * because it encountered an unexpected condition that prevented it from fulfilling the request.*/
#if (NGX_ZLIB)
static u_char WS_CLOSE_UNEXPECTED_CONDITION[] = { 0x03, 0xf3 };
#endif
/*1012 indicates that the service will be restarted.
static u_char WS_CLOSE_SERVICE_RESTART[] = { 0x03, 0xf4 };*/

/*1013 indicates that the service is experiencing overload
static u_char WS_CLOSE_TRY_AGAIN_LATER[] = { 0x03, 0xf5 };*/

/*1015 is a reserved value and MUST NOT be set as a status code in a Close control frame by an endpoint.
static u_char WS_CLOSE_TLS_HANDSHAKE_FAILURE[] = { 0x03, 0xf7 };*/
#if (NGX_ZLIB)
static u_char WS_PMCE_TAIL_DATA[] = {0x00, 0x00, 0xff, 0xff};
#endif

ngx_int_t ngx_http_clojure_set_elt_header(ngx_http_request_t *r, ngx_table_elt_t *h, ngx_uint_t offset) {
    ngx_table_elt_t  **ph;

    ph = (ngx_table_elt_t **) ((char *) &r->headers_out + offset);

    if (*ph == NULL) {
        *ph = h;
    }

    return NGX_OK;
}


ngx_int_t ngx_http_clojure_set_array_header(ngx_http_request_t *r, ngx_table_elt_t *h, ngx_uint_t offset) {
	ngx_array_t *headers;
	ngx_table_elt_t **ph;

	headers = (ngx_array_t *) ((char *) &r->headers_out + offset);

	if (headers->elts == NULL) {
		if (ngx_array_init(headers, r->pool, 1, sizeof(ngx_table_elt_t *)) != NGX_OK) {
			return NGX_ERROR;
		}
	}

	ph = ngx_array_push(headers);
	if (ph == NULL) {
		return NGX_ERROR;
	}

	*ph = h;
	return NGX_OK;
}

ngx_int_t ngx_http_clojure_set_content_type_header(ngx_http_request_t *r, ngx_table_elt_t *h, ngx_uint_t offset) {
    r->headers_out.content_length = h;
    r->headers_out.content_type_len = h->value.len;
    return NGX_OK;
}

ngx_int_t ngx_http_clojure_set_content_len_header(ngx_http_request_t *r, ngx_table_elt_t *h, ngx_uint_t offset) {
	r->headers_out.content_length = h;
	r->headers_out.content_length_n = ngx_atoof(h->value.data, h->value.len);
	return NGX_OK;
}


static ngx_int_t ngx_http_clojure_check_broken_connection(ngx_http_request_t *r, ngx_event_t *ev);

static void ngx_http_clojure_rd_check_broken_connection(ngx_http_request_t *r);

static void nji_ngx_http_clojure_hijack_read_handler(ngx_http_request_t *r);

static void nji_ngx_http_clojure_hijack_write_handler(ngx_http_request_t *r);

ngx_int_t ngx_http_clojure_prepare_server_header(ngx_http_request_t *r) {
	ngx_table_elt_t *h = r->headers_out.server;
	if (h == NULL) {
	    h = ngx_list_push(&r->headers_out.headers);
	    if (h == NULL) {
	        return NGX_ERROR;
	    }
	    h->hash = 1;
	    r->headers_out.server = h;
	    ngx_str_set(&h->key, "Server");
	}
	if (((ngx_http_core_loc_conf_t *)ngx_http_get_module_loc_conf(r, ngx_http_core_module))->server_tokens) {
	    ngx_str_set(&h->value, NGINX_CLOJURE_VER);
	}else {
		ngx_str_set(&h->value, "nginx-clojure");
	}
	return NGX_OK;
}


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

static void JNICALL jni_ngx_array_destory(JNIEnv *env, jclass cls, jlong array) {
	ngx_array_destroy((ngx_array_t *)(uintptr_t)array);
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

static jlong JNICALL jni_ngx_create_temp_buf_by_jstring (JNIEnv *env, jclass cls, jlong req, jstring jstr,  jint last_buf) {
	ngx_http_request_t *r = (ngx_http_request_t *)(uintptr_t) req;

	jsize ch_size = (*env)->GetStringLength(env, jstr);
	jsize utf8_size = (*env)->GetStringUTFLength(env, jstr);
	ngx_buf_t *b = ngx_create_temp_buf(r->pool, (size_t)utf8_size);

	if (b == NULL) {
		return 0;
	}

	(*env)->GetStringUTFRegion(env, jstr, 0, ch_size, (char *)b->pos);
	b->last = b->pos + utf8_size;

	if (last_buf & NGX_BUF_LAST_OF_RESPONSE) {
		b->last_buf = b->last_in_chain = 1;
	}else {
		b->last_in_chain = last_buf & NGX_BUF_LAST_OF_CHAIN;
	}

	if (r->headers_out.content_length_n < 0 ) {
		r->headers_out.content_length_n = utf8_size;
	}else {
		r->headers_out.content_length_n += utf8_size;
	}

	/*
	 * If File and String are in the same ISeq of one response body,
	 * we should clear the last_modified_time.
	 */
	r->headers_out.last_modified_time = -2;
	r->headers_out.last_modified = NULL;

	return (uintptr_t)b;
}


static jlong JNICALL jni_ngx_create_temp_buf_by_obj(JNIEnv *env, jclass cls, jlong req, jobject obj, jlong off, jlong len,  jint last_buf) {
    ngx_http_request_t *r = (ngx_http_request_t *) (uintptr_t) req;
    ngx_buf_t *b;

    if (len == 0) {
        return 0;
    }

    b = ngx_calloc_buf(r->pool);
    if (b == NULL) {
        return 0;
    }

    b->start = (u_char *) ngx_http_clojure_abs_off_addr(obj, off);
    b->pos = b->start;
    b->last = b->start + len;
    b->end = b->last;
    b->memory = 1;

    if (last_buf & NGX_BUF_LAST_OF_RESPONSE) {
        b->last_buf = b->last_in_chain = 1;
    } else {
        b->last_in_chain = last_buf & NGX_BUF_LAST_OF_CHAIN;
    }

    if (r->headers_out.content_length_n < 0) {
        r->headers_out.content_length_n = len;
    } else {
        r->headers_out.content_length_n += len;
    }

    /*
     * If File and String are in the same ISeq of one response body,
     * we should clear the last_modified_time.
     */
    r->headers_out.last_modified_time = -2;
    r->headers_out.last_modified = NULL;

    return (uintptr_t) b;
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

static jlong JNICALL jni_ngx_http_send_header (JNIEnv *env, jclass cls, jlong req) {
	ngx_http_request_t *r = (ngx_http_request_t *)(uintptr_t) req;
	if (r->headers_out.last_modified_time == -2) {
		r->headers_out.last_modified_time = -1;
	}
	if ( ngx_http_clojure_prepare_server_header(r) != NGX_OK ) {
		return NGX_ERROR;
	}
	return ngx_http_send_header(r);
}

static void JNICALL jni_ngx_http_clear_header_and_reset_ctx_phase(JNIEnv *env, jclass cls, jlong req,  jlong phase, jboolean clear_header) {
	ngx_http_request_t *r = (ngx_http_request_t *)(uintptr_t) req;
	ngx_http_clojure_module_ctx_t *ctx = ngx_http_get_module_ctx(r, ngx_http_clojure_module);

	if (clear_header) {
	  ngx_http_clean_header(r);
	}

	r->err_status = 0;

	if (ctx && phase) {
			ctx->phase = (ngx_int_t)phase;
			if (ctx->phase == ~ NGX_HTTP_HEADER_FILTER_PHASE) {
				ctx->wait_for_header_filter = 0;
			}
	}
}

static void JNICALL jni_ngx_http_ignore_next_response(JNIEnv *env, jclass cls, jlong req) {
	ngx_http_request_t *r = (ngx_http_request_t *)(uintptr_t) req;
	ngx_http_clojure_module_ctx_t *ctx = ngx_http_get_module_ctx(r, ngx_http_clojure_module);
	if (ctx) {
		ctx->ignore_next_response = 1;
	}
}

static jlong JNICALL jni_ngx_http_output_filter (JNIEnv *env, jclass cls, jlong r, jlong chain) {
	return ngx_http_output_filter((ngx_http_request_t *)(uintptr_t)r, (ngx_chain_t *)(uintptr_t)chain);
}


#define NGX_CLOJURE_BUF_LAST_FLAG 0x01
#define NGX_CLOJURE_BUF_FLUSH_FLAG 0x02
#define NGX_CLOJURE_BUF_IGNORE_FILTER_FLAG 0x04
#define NGX_CLOJURE_BUF_FILE_FLAG 0x08
#define NGX_CLOJURE_BUF_MEM_FLAG 0x10
/*this constant hints whether we send java.lang.String or bytes (byte[], ByteBuffer) from app level*/
#define NGX_CLOJURE_BUF_APP_MSGTXT 0x08
#define NGX_CLOJURE_BUF_WEBSOCKET_FRAME 0x10
#define NGX_CLOJURE_BUF_WEBSOCKET_CONTINUE_FRAME 0x20
#define NGX_CLOJURE_BUF_WEBSOCKET_CLOSE_FRAME 0x40
#define NGX_CLOJURE_BUF_WEBSOCKET_PING_FRAME 0x80
#define NGX_CLOJURE_BUF_WEBSOCKET_PONG_FRAME 0x80

#define set_ws_frame_header(b, head_size, copy_size,rem, flag) { \
	if (!rem && (flag & NGX_CLOJURE_BUF_FLUSH_FLAG)) { /*FIN*/ \
		b->last[0] = 0x80; \
	}else { \
		b->last[0] = 0; \
	} \
	if (flag & NGX_CLOJURE_BUF_WEBSOCKET_CLOSE_FRAME) { \
		b->last[0] |= 0x08; \
	}else if (flag & NGX_CLOJURE_BUF_WEBSOCKET_PONG_FRAME) { \
		b->last[0] |= 0x0a; \
	}else if (!(flag & NGX_CLOJURE_BUF_WEBSOCKET_CONTINUE_FRAME)) { \
		b->last[0] |= (flag & NGX_CLOJURE_BUF_APP_MSGTXT) ? 0x01 : 0x02; \
	} \
	if (head_size == 2) { \
		b->last[1] = (uint16_t)copy_size; \
	}else { \
		b->last[1] = 126; \
		*((uint16_t *) b->last + 1) = htons(copy_size); \
	} \
}

static ngx_chain_t * ngx_http_clojure_get_and_copy_bufs(size_t page_size, ngx_pool_t *p, ngx_chain_t **free, u_char *src, size_t len,  ngx_int_t flag) {
	ngx_chain_t *cl;
	ngx_chain_t **ll = &cl;
	ngx_buf_t *b = NULL;
	size_t head_size = 0;
	size_t copy_size;
	do {
		if (flag & NGX_CLOJURE_BUF_WEBSOCKET_FRAME) {
			head_size = len > 125 ? 4 : 2;
		}
		if (*free) {
			*ll = *free;
			*free = (*ll)->next;
			b = (*ll)->buf;
			b->last_in_chain = b->last_buf = 0;
			b->temporary = 1;

			copy_size = len + head_size > page_size ? page_size - head_size : len;
			len -= copy_size;
			if (src) {
				ngx_memcpy(b->last + head_size, src, copy_size);
				if (head_size) {
					set_ws_frame_header(b, head_size, copy_size,len,flag);
				}
				b->last += head_size;
				b->last += copy_size;
				src += copy_size;
			}
			ll = &(*ll)->next;
			ngx_log_debug1(NGX_LOG_DEBUG_HTTP, p->log, 0, "[ngx_http_clojure_get_and_copy_bufs] hit free, len; %d", copy_size);
		}else {
	    	ngx_bufs_t bufs;
	    	bufs.size = page_size;
	    	bufs.num = len / (page_size - head_size);
			ngx_log_debug1(NGX_LOG_DEBUG_HTTP, p->log, 0, "[ngx_http_clojure_get_and_copy_bufs] new buffer, len; %d", len);
	    	if (len % (page_size - head_size) != 0 || !len) {
	    		bufs.num ++;
	    	}
	    	*ll = ngx_create_chain_of_bufs(p, &bufs);
	    	if (*ll == NULL) {
	    		return NULL;
	    	}
	    	if (src && !head_size) {
	    		ngx_memcpy((*ll)->buf->last, src, len);
	    	}
	    	for (;  *ll; ll = &(*ll)->next) {
	    		if ( flag & NGX_CLOJURE_BUF_WEBSOCKET_FRAME ) {
	    			head_size = len > 125 ? 4 : 2;
	    		}
	    		b = (*ll)->buf;
	    		copy_size = len + head_size > page_size ? page_size - head_size : len;
	    		if (src) {
		    		if (head_size) {
		    			ngx_memcpy(b->last + head_size, src, copy_size);
		    			src += copy_size;
		    			len -= copy_size;
		    			set_ws_frame_header(b, head_size, copy_size,len,flag);
		    		}
		    		b->last += head_size;
		    		b->last += copy_size;
	    		}
	    		if (!src || !head_size) {
	    			len -= copy_size;
	    		}
	    		b->tag = (ngx_buf_tag_t) &ngx_http_clojure_module;
	    		flag |= NGX_CLOJURE_BUF_WEBSOCKET_CONTINUE_FRAME;
	    	}
	    	break;
	    }

		flag |= NGX_CLOJURE_BUF_WEBSOCKET_CONTINUE_FRAME;
	}while (len);

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

static void ngx_http_clojure_hijack_async_timeout_handler(ngx_http_request_t *r) {
	ngx_connection_t *c = r->connection;
	ngx_event_t *wev = c->write;

	if (wev->timedout) {
		ngx_log_error(NGX_LOG_INFO, c->log, NGX_ETIMEDOUT, "async timed out");
		c->timedout = 1;
		//TODO: fire timeout event for java level
		ngx_http_finalize_request(r, NGX_HTTP_REQUEST_TIME_OUT);
		return;
	}
}

static void ngx_http_clojure_hijack_writer(ngx_http_request_t *r) {
	int rc;
	ngx_event_t *wev;
	ngx_connection_t *c;
	ngx_http_core_loc_conf_t *clcf;
	ngx_http_clojure_module_ctx_t *ctx;

	ngx_http_clojure_get_ctx(r, ctx);
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

	if (wev->timer_set) {
		ngx_del_timer(wev);
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

		if (ctx->wsctx) {
			r->read_event_handler = nji_ngx_http_clojure_hijack_read_handler;
		}
		return;
	}

	ngx_log_debug2(NGX_LOG_DEBUG_HTTP, wev->log, 0,
			"clojure module hijack writer done: \"%V?%V\"", &r->uri, &r->args);

	if (ctx->last_buf_meeted) {
		ngx_http_finalize_request(r, NGX_OK);
		return;
	}

	if (ctx->event_handler_flag) {
		if (ctx->event_handler_flag & NGX_HTTP_CLOJURE_EVENT_HANDLER_FLAG_READ) {
			r->read_event_handler = nji_ngx_http_clojure_hijack_read_handler;
			if (!c->read->active && ngx_handle_read_event(c->read, 0) != NGX_OK) {
				ngx_http_finalize_request(r, NGX_ERROR);
				return;
			}
		}

		if (ctx->event_handler_flag & NGX_HTTP_CLOJURE_EVENT_HANDLER_FLAG_WRITE) {
			r->write_event_handler = nji_ngx_http_clojure_hijack_write_handler;
			if (!c->write->active) {
				c->write->ready = 0; /*clear write ready flag because websocket is obviously write ready after handshake.*/
				if (ngx_handle_write_event(c->write, clcf->send_lowat) != NGX_OK) {
					ngx_http_finalize_request(r, NGX_ERROR);
					return;
				}
			}
		}
	}else {
		r->write_event_handler = ngx_http_request_empty_handler;
	}
}


/*
 * *****************************************************************************
 * START of copy from ngx_http_header_filter_module.c
 * *****************************************************************************
 * */
static char ngx_http_server_string[] = "Server: nginx" CRLF;
static char ngx_http_server_full_string[] = "Server: " NGINX_VER CRLF;


static ngx_str_t ngx_http_status_lines[] = {

	ngx_string("100 Continue"),
	ngx_string("101 Switching Protocols"),

#define NGX_HTTP_LAST_1XX 102
#define NGX_HTTP_OFF_2XX (NGX_HTTP_LAST_1XX - 100)

    ngx_string("200 OK"),
    ngx_string("201 Created"),
    ngx_string("202 Accepted"),
    ngx_null_string,  /* "203 Non-Authoritative Information" */
    ngx_string("204 No Content"),
    ngx_null_string,  /* "205 Reset Content" */
    ngx_string("206 Partial Content"),

    /* ngx_null_string, */  /* "207 Multi-Status" */

#define NGX_HTTP_LAST_2XX  207
#define NGX_HTTP_OFF_3XX   (NGX_HTTP_LAST_2XX - 200 + NGX_HTTP_OFF_2XX)

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
    ngx_http_clojure_module_ctx_t *ctx;

    ctx = (ngx_http_clojure_module_ctx_t *)ngx_http_get_module_ctx(r, ngx_http_clojure_module);

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

    if (r->err_status) {
		r->headers_out.status = r->err_status;
		r->headers_out.status_line.len = 0;
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

            status = status - NGX_HTTP_OK + NGX_HTTP_OFF_2XX;
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

        } else if (status >= NGX_HTTP_CONTINUE && status < NGX_HTTP_LAST_1XX) {
        	/* 1XX */
        	status = status - NGX_HTTP_CONTINUE;
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

    } else if (!ctx->event_handler_flag){
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

    } else if (!ctx->event_handler_flag) {
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
	ngx_connection_t *c;
	ngx_http_core_loc_conf_t *clcf;

	if (r->pool == NULL) {
		ngx_log_error(NGX_LOG_ERR, ngx_http_clojure_global_cycle->log, 0,
						"ngx_http_clojure_hijack_send 1:"
						"can not send message because the request was closed");
		return NGX_ERROR;
	}

	if (in == NULL) {
		return NGX_ERROR;
	}

	c = r->connection;
	ngx_http_clojure_get_ctx(r, ctx);
	clcf = ngx_http_get_module_loc_conf(r, ngx_http_core_module);

	if (flag & NGX_CLOJURE_BUF_IGNORE_FILTER_FLAG) {
		ctx->ignore_filters = 1;
	} else {
		ctx->ignore_filters = 0;
	}

	if (flag & NGX_CLOJURE_BUF_LAST_FLAG) {
		ctx->last_buf_meeted = 1;
	}

	if ((flag & NGX_CLOJURE_BUF_LAST_FLAG) || (flag & NGX_CLOJURE_BUF_FLUSH_FLAG)) {
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

		if (ctx->wsctx) {
			r->read_event_handler = nji_ngx_http_clojure_hijack_read_handler;
		}else {
			if (!c->write->delayed) {
				ngx_add_timer(c->write, clcf->send_timeout);
			}
			r->read_event_handler = ngx_http_clojure_rd_check_broken_connection;
		}

		if (!c->write->active) {
			(void)ngx_handle_write_event(r->connection->write, 0);
		}
	} else if (rc != NGX_OK) {
		return rc;
	} else {
		if (ctx->last_buf_meeted) {
			ngx_http_finalize_request(r, NGX_OK);
			return NGX_OK;
		}

		if (ctx->event_handler_flag) {
			if (ctx->event_handler_flag & NGX_HTTP_CLOJURE_EVENT_HANDLER_FLAG_READ) {
				r->read_event_handler = nji_ngx_http_clojure_hijack_read_handler;
				if (!c->read->active && ngx_handle_read_event(c->read, 0) != NGX_OK) {
					return NGX_ERROR;
				}
			}

			if (ctx->event_handler_flag & NGX_HTTP_CLOJURE_EVENT_HANDLER_FLAG_WRITE) {
				r->write_event_handler = nji_ngx_http_clojure_hijack_write_handler;
				if (!c->write->active) {
					c->write->ready = 0; /*clear write ready flag because websocket is obviously write ready after handshake.*/
					if (ngx_handle_write_event(c->write, clcf->send_lowat) != NGX_OK) {
						return NGX_ERROR;
					}
				}
			}
		} else {
			r->read_event_handler = ngx_http_clojure_rd_check_broken_connection;
			r->write_event_handler = ngx_http_request_empty_handler;
		}
	}

	if ((flag & NGX_CLOJURE_BUF_LAST_FLAG) || (flag & NGX_CLOJURE_BUF_FLUSH_FLAG)) {
#if nginx_version >= 1001004
	ngx_chain_update_chains(r->pool, &ctx->free, &ctx->busy, &in, (ngx_buf_tag_t)&ngx_http_clojure_module);
#else
	ngx_chain_update_chains(&ctx->free, &ctx->busy, &in, (ngx_buf_tag_t)&ngx_http_clojure_module);
#endif
	}
	return NGX_OK;
}

#define recompute_ws_flag(part_written, flag) \
	((part_written) ? (flag | NGX_CLOJURE_BUF_WEBSOCKET_CONTINUE_FRAME) : (flag & ~NGX_CLOJURE_BUF_WEBSOCKET_CONTINUE_FRAME))

static ngx_int_t  ngx_http_clojure_hijack_send(ngx_http_request_t *r, u_char *message, size_t len, ngx_int_t flag) {
	ngx_http_clojure_module_ctx_t *ctx;
	ngx_chain_t *in;
	size_t page_size;

#if (NGX_ZLIB)
	int need_reset_deflate_ctx = 0;
#endif

	if (r->pool == NULL) {
		if (message) { /*message can be NULL if send a close event without additional data*/
			ngx_log_error(NGX_LOG_ERR, ngx_http_clojure_global_cycle->log, 0,
									"ngx_http_clojure_hijack_send 2:"
									"can not send message because the request was closed");
		}
		return NGX_ERROR;
	}

	ngx_http_clojure_get_ctx(r, ctx);

	if (ctx == NULL) { /*ctx was cleared by finalize_xxx*/
		ngx_log_error(NGX_LOG_ERR, ngx_http_clojure_global_cycle->log, 0,
								"ngx_http_clojure_hijack_send 3:"
								"can not send message because the request is closing");
		return NGX_OK;
	}

	if (r->connection->log->log_level & NGX_LOG_DEBUG_HTTP) {
	  ngx_str_t msg;
	  msg.data = message;
	  msg.len = len;
	  ngx_log_error(NGX_LOG_ERR, r->connection->log, 0, "[%" PRIu64  "] hijack send : {%V}, len %d, flag : %d", (uintptr_t)r , &msg, len, flag);
	}

	page_size = ((ngx_http_clojure_loc_conf_t *)ngx_http_get_module_loc_conf(r, ngx_http_clojure_module))->write_page_size;

	if (flag & NGX_CLOJURE_BUF_IGNORE_FILTER_FLAG) {
		ctx->ignore_filters = 1;
	} else {
		ctx->ignore_filters = 0;
	}


	if (ctx->wsctx) {
		ngx_http_clojure_websocket_ctx_t *wsctx = ctx->wsctx;
		flag |= NGX_CLOJURE_BUF_WEBSOCKET_FRAME;
		if (flag & NGX_CLOJURE_BUF_LAST_FLAG) {
			flag |= NGX_CLOJURE_BUF_WEBSOCKET_CLOSE_FRAME;
			flag |= NGX_CLOJURE_BUF_FLUSH_FLAG;
			if (!message) {
				message = WS_CLOSE_NORMAL_CLOSURE;
				len = sizeof(WS_CLOSE_NORMAL_CLOSURE);
			}
		}
		if (!(flag & (NGX_CLOJURE_BUF_WEBSOCKET_CLOSE_FRAME | NGX_CLOJURE_BUF_WEBSOCKET_PONG_FRAME))) {
			if (!wsctx->ffm) {
				flag |= NGX_CLOJURE_BUF_WEBSOCKET_CONTINUE_FRAME;
			} else {
#if (NGX_ZLIB)
				need_reset_deflate_ctx = 1;
#endif
				wsctx->ffm = 0;
			}
			if (flag & NGX_CLOJURE_BUF_FLUSH_FLAG) {
				wsctx->ffm = 1;
			}
#if (NGX_ZLIB)
			if (wsctx->premsg_deflate) {
				ngx_chain_t *deflate_chain = NULL;
				ngx_chain_t **pchain = &deflate_chain;
				ngx_chain_t *tmp_chain;
				size_t head_size = 0;
				int zrc;
				size_t out_size = 0;

				if (wsctx->out_no_ctx_takeover && need_reset_deflate_ctx && deflateReset(&wsctx->zout)) {
					ngx_log_error(NGX_LOG_ERR, r->connection->log, 0, "deflateReset error");
					return NGX_ERROR;
				}
				need_reset_deflate_ctx = 0;
				wsctx->zout.avail_in = len;
				wsctx->zout.next_in = message;
				do {
					tmp_chain = ngx_http_clojure_get_and_copy_bufs(page_size, r->pool, &ctx->free, 0, page_size, 0);
					if (tmp_chain == NULL) {
						ngx_log_error(NGX_LOG_ERR, r->connection->log, 0, "ngx_http_clojure_hijack_send:"
								"not enough memory, ngx_http_clojure_get_and_copy_bufs fail");
						return NGX_ERROR;
					}
					wsctx->zout.avail_out = page_size - 4; /*4 bytes reserved for header*/
					tmp_chain->buf->last += 4;
					wsctx->zout.next_out = tmp_chain->buf->last;

					zrc = deflate(&wsctx->zout, (flag & NGX_CLOJURE_BUF_FLUSH_FLAG) ? Z_SYNC_FLUSH : Z_NO_FLUSH);
					if (zrc != Z_OK) {
						ngx_log_error(NGX_LOG_ERR, r->connection->log, 0, "deflate error: %d", zrc);
						return NGX_ERROR;
					}

					out_size = (size_t)(wsctx->zout.next_out - tmp_chain->buf->last);
					if (!out_size) {
						tmp_chain->buf->last = tmp_chain->buf->pos;
#if nginx_version >= 1001004
						ngx_chain_update_chains(r->pool, &ctx->free, &ctx->busy, &tmp_chain,
								(ngx_buf_tag_t) &ngx_http_clojure_module);
#else
						ngx_chain_update_chains(&ctx->free, &ctx->busy, &tmp_chain, (ngx_buf_tag_t)&ngx_http_clojure_module);
#endif
						if (!deflate_chain) {
							return NGX_OK;
						}
						break;
					}

					ngx_log_debug2(NGX_LOG_DEBUG_HTTP, ngx_http_clojure_global_cycle->log, 0, "[ngx_http_clojure_hijack_send] deflate size %d, isflush %d", out_size, (flag & NGX_CLOJURE_BUF_FLUSH_FLAG));
					tmp_chain->buf->last = wsctx->zout.next_out;
					(*pchain) = tmp_chain;
					pchain = &tmp_chain->next;
				}while(wsctx->zout.avail_out == 0);

				tmp_chain = (ngx_chain_t *)(((uintptr_t)pchain) - offsetof(ngx_chain_t, next));
				if (flag & NGX_CLOJURE_BUF_FLUSH_FLAG) {
					tmp_chain->buf->last -= sizeof(WS_PMCE_TAIL_DATA);
					out_size -= sizeof(WS_PMCE_TAIL_DATA);
				}

				if (ctx->wchain) {
					ngx_buf_t *b = ctx->wchain->buf;
					out_size = deflate_chain->buf->last - deflate_chain->buf->pos - 4;
					head_size = out_size > 125 ? 4 : 2;
					 /*optimize for small message, merge it into last chain*/
					if (!deflate_chain->next && (size_t) (b->end - b->last) >= out_size + head_size) {
						set_ws_frame_header(b, head_size, out_size, !(flag & NGX_CLOJURE_BUF_FLUSH_FLAG), recompute_ws_flag(wsctx->part_written, flag));
						wsctx->part_written = 1;
						b->last += head_size;
						ngx_memcpy(b->last, deflate_chain->buf->pos + 4, out_size);
						b->last += out_size;
						if (flag & NGX_CLOJURE_BUF_LAST_FLAG) {
							wsctx->part_written = 0;
							b->last_buf = 1;
						}
						if (flag & NGX_CLOJURE_BUF_FLUSH_FLAG) {
							wsctx->part_written = 0;
							b->flush = 1;
						}
						deflate_chain->buf->pos = deflate_chain->buf->last;
#if nginx_version >= 1001004
						ngx_chain_update_chains(r->pool, &ctx->free, &ctx->busy, &deflate_chain,
								(ngx_buf_tag_t) &ngx_http_clojure_module);
#else
						ngx_chain_update_chains(&ctx->free, &ctx->busy, &deflate_chain, (ngx_buf_tag_t)&ngx_http_clojure_module);
#endif
						goto TRY_SEND;
					}
				}

				tmp_chain = deflate_chain;
				do {
					out_size = tmp_chain->buf->last - tmp_chain->buf->pos - 4;
					head_size = out_size > 125 ? 4 : 2;

					if (head_size != 4) {
						tmp_chain->buf->pos += 2;
					}
					tmp_chain->buf->last = tmp_chain->buf->pos;
					set_ws_frame_header(tmp_chain->buf, head_size, out_size, tmp_chain->next, recompute_ws_flag(wsctx->part_written, flag));
					if (!wsctx->part_written) {
						tmp_chain->buf->pos[0] |= 0x40; /*rev1=1 for PMCE*/
					}
					wsctx->part_written = 1;
					tmp_chain->buf->last += head_size;
					tmp_chain->buf->last += out_size;

					if (!tmp_chain->next) {
						if (flag & NGX_CLOJURE_BUF_LAST_FLAG) {
							wsctx->part_written = 0;
							tmp_chain->buf->last_buf = 1;
						}
						if (flag & NGX_CLOJURE_BUF_FLUSH_FLAG) {
							wsctx->part_written = 0;
							tmp_chain->buf->flush = 1;
						}
						break;
					}
					tmp_chain = tmp_chain->next;
				}while(1);

				tmp_chain->buf->flush = 1; /*flush data to reduce memory usage*/

				if (ctx->wchain) {
					ctx->wchain->next = deflate_chain;
				}else {
					ctx->wchain = deflate_chain;
				}

				goto TRY_SEND;
			}
#endif
		}
	}

	/*non premessage deflate messsages*/
	/*optimize for small message, merge it into last chain*/
	if (ctx->wchain) {
		ngx_buf_t *b = ctx->wchain->buf;
		size_t head_size = 0;

		if (flag & NGX_CLOJURE_BUF_WEBSOCKET_FRAME) {
			/*we only use head_size when b->end - b->last >= len so it can't be 8*/
			head_size = len > 125 ? 4 : 2;
		}

		if ((size_t)(b->end - b->last) >= len + head_size) {
			if (head_size) {
				set_ws_frame_header(b, head_size, len, 0, flag);
				b->last += head_size;
			}
			ngx_memcpy(b->last, message, len);
			b->last += len;
			if (flag & NGX_CLOJURE_BUF_LAST_FLAG) {
				b->last_buf = 1;
				if (ngx_buf_size(b) == 0) {
				  b->temporary = 0;
				}
			}
			if (flag & NGX_CLOJURE_BUF_FLUSH_FLAG) {
				b->flush = 1;
				if (ngx_buf_size(b) == 0) {
				  b->temporary = 0;
				}
			}
			goto TRY_SEND;
		}
	}

	in = ngx_http_clojure_get_and_copy_bufs(page_size, r->pool, &ctx->free, message, len, flag);
	if (in == NULL) {
			ngx_log_error(NGX_LOG_ERR, r->connection->log, 0,
					"ngx_http_clojure_hijack_send:"
					"not enough memory, ngx_http_clojure_get_and_copy_bufs fail");
			return NGX_ERROR;
	}
	if (!len && !message && ((flag & NGX_CLOJURE_BUF_LAST_FLAG) || (flag & NGX_CLOJURE_BUF_FLUSH_FLAG) )) {
		in->buf->temporary = 0;
	}
	if (ctx->wchain) {
		ctx->wchain->next = in;
	}else {
		ctx->wchain = in;
	}

TRY_SEND :
	if ((flag & NGX_CLOJURE_BUF_LAST_FLAG) || (flag & NGX_CLOJURE_BUF_FLUSH_FLAG) || ctx->wchain->next) {
		in = ctx->wchain;
		ctx->wchain = NULL;
		return ngx_http_clojure_hijack_send_chain(r, in, flag);
	}

	return NGX_OK;

}


static void nji_ngx_http_clojure_hijack_fire_channel_event(jint type, jlong flag, ngx_http_clojure_module_ctx_t *ctx) {
	ngx_http_clojure_listener_node_t *l = ctx->listeners;
	void *ls;
	void *d;
	ngx_connection_t *c = ctx->r->connection;
	while (l) {
		ls = l->listener;
		d = l->data;
		(*jvm_env)->CallStaticVoidMethod(jvm_env, nc_rt_class, nc_rt_handle_channel_event_mid, type, flag, l->data,
				l->listener);
		if ((*jvm_env)->ExceptionOccurred(jvm_env)) {
			(*jvm_env)->ExceptionDescribe(jvm_env);
			(*jvm_env)->ExceptionClear(jvm_env);
		}
		if (c->destroyed) {
			return;
		}
		if (type == NGX_HTTP_CLOJURE_CHANNEL_EVENT_CLOSE) {
			(*jvm_env)->DeleteGlobalRef(jvm_env, ls);
			(*jvm_env)->DeleteGlobalRef(jvm_env, d);
			ctx->listeners = l->next;
		}
		l = l->next;
	}
}



/*valid code (from 1000 ~ 1015) bits: 1111000111110000*/
#define is_valid_ws_close_code(c) \
	( ((c) > 999 && (c) < 1016 && ((0xf1f0 >> (1015-(c))) & 1)) || ((c) > 2999 && (c) < 5000))


#define check_buf_data_enough(b, l, cnt) \
	if ((size_t)(b->last - b->pos) < (size_t)l) { \
		if (b->end - b->pos < 20) { \
			if (wsctx->left) { \
				ngx_memmove(b->start, wsctx->left_pos, b->last - wsctx->left_pos); \
				b->last -= wsctx->left_pos - b->start; \
				b->pos -= wsctx->left_pos - b->start; \
				wsctx->left_pos = b->start; \
			}else { \
				ngx_memmove(b->start, b->pos, b->last - b->pos); \
				b->last = b->start + (b->last - b->pos); \
				b->pos = b->start; \
			} \
		} \
		goto cnt; \
	}

#define goto_close(msg) \
	type |= NGX_HTTP_CLOJURE_CHANNEL_EVENT_MSGCLOSE; \
	close_msg = msg; \
	goto SEND_CLOSE_FRAME


static void nji_ngx_http_clojure_hijack_read_handler(ngx_http_request_t *r) {

	ngx_connection_t *c = r->connection;
	ngx_http_clojure_module_ctx_t *ctx;
	ngx_http_clojure_websocket_ctx_t *wsctx;
	jlong flag = NGX_HTTP_CLOJURE_SOCKET_OK;
	u_char *close_msg = WS_CLOSE_NORMAL_CLOSURE;
	size_t page_size;

	page_size = ((ngx_http_clojure_loc_conf_t *)ngx_http_get_module_loc_conf(r, ngx_http_clojure_module))->write_page_size;

	ngx_http_clojure_get_ctx(r, ctx);
	wsctx = ctx->wsctx;

	if (c->read->timedout) {
		flag = NGX_HTTP_CLOJURE_SOCKET_ERR_READ_TIMEOUT;
	}else if (c->read->timer_set) {
		ngx_del_timer(c->read);
	}

TOP_WHILE :
	while (wsctx && !c->destroyed) {
		ngx_int_t rc;
		size_t size;
		ngx_buf_t *buf;
		int start_with_header = 0; /*current received block contains current message header*/
		if (wsctx->rchain == NULL) {
			wsctx->rchain = ngx_http_clojure_get_and_copy_bufs(page_size, r->pool, &ctx->free, NULL, page_size, 0);
		}
		flag = NGX_HTTP_CLOJURE_SOCKET_OK;
		buf = wsctx->rchain->buf;
/*		if (wsctx->len || buf->last == buf->pos || (wsctx->left && !wsctx->len))*/
		{
			rc = c->recv(c, buf->last, buf->end - buf->last);
			ngx_log_debug2(NGX_LOG_DEBUG_HTTP, r->connection->log, 0, "nji_ngx_http_clojure_hijack_read_handler recv rc=%d, wsctx->len=%d", rc, wsctx->len);

			if (rc == 0) {
				flag = NGX_HTTP_CLOJURE_SOCKET_ERR_RESET;
				break;
			} else if (rc == NGX_AGAIN) {
				return;
			} else if (rc < 0) {
				flag = NGX_HTTP_CLOJURE_SOCKET_ERR_READ;
				break;
			}
		}
		buf->last += rc;
		do{
			jint type;
			type = NGX_HTTP_CLOJURE_CHANNEL_EVENT_READ;
			switch (wsctx->pstate) {
			case NGX_HTTP_CLOJURE_WEBSOCKET_PARSE_START:
				check_buf_data_enough(buf, 2 + wsctx->left, TOP_WHILE);
				start_with_header = 1;
				if (buf->pos == wsctx->left_pos) {
					buf->pos += wsctx->left;
				}
				wsctx->opcode = buf->pos[0] & 0x0f;

#if (NGX_ZLIB)
				if (wsctx->premsg_deflate && wsctx->fin) {
				  /*the last frame is FIN frame so we need update compressed flag*/
				  wsctx->compressed = (buf->pos[0] & 0x40) >> 6;
				}
#endif

				if (wsctx->fin || (buf->pos[0] & 0x0f) == NGX_HTTP_CLOJURE_WEBSOCKET_OPCODE_CLOSE) { /*last frame is FIN frame*/
					wsctx->cont = 0;
				}else {
					wsctx->cont = 1;
				}

				/*check opcode*/
				if (wsctx->opcode == NGX_HTTP_CLOJURE_WEBSOCKET_OPCODE_CONT && wsctx->fin) {
					goto_close(WS_CLOSE_PROTOCOL_ERROR);
				}else if (wsctx->opcode != NGX_HTTP_CLOJURE_WEBSOCKET_OPCODE_CONT && !wsctx->fin && !(wsctx->opcode & 0x8)) {
					goto_close(WS_CLOSE_PROTOCOL_ERROR);
				}

				if ( !(wsctx->opcode & 0x8) ) { /*is not control frame*/
					wsctx->fin = (buf->pos[0] >> 7) & 1;
				} else {
          if ((!(buf->pos[0] >> 7)) & 1) { /*control frame must be FIN*/
						goto_close(WS_CLOSE_PROTOCOL_ERROR);
					}

				}

				/*check rsv*/
#if (NGX_ZLIB)
				if (wsctx->premsg_deflate && !(wsctx->opcode & 0x8)) { /*non control frame meets premessage deflate*/
					if ((buf->pos[0] & 0x30) || (wsctx->cont && (buf->pos[0] & 0x40)))  { /*RSV2 RSV3 is 1 or non-fin message have RSV1 == 1*/
						goto_close(WS_CLOSE_PROTOCOL_ERROR);
					}
				} else
#endif
				{
					if (buf->pos[0] & 0x70) {
						goto_close(WS_CLOSE_PROTOCOL_ERROR);
					}
				}

				wsctx->mask = (buf->pos[1] >> 7) & 1;
				wsctx->mpos = 0;
				wsctx->len = buf->pos[1] & 0x7f;
				buf->pos += 2;
				wsctx->pstate = NGX_HTTP_CLOJURE_WEBSOCKET_PARSE_LEN;
				/* no break */
				/*fallthrough*/
			case NGX_HTTP_CLOJURE_WEBSOCKET_PARSE_LEN:
				if (wsctx->opcode & 0x8) {
					if (wsctx->len > 125) { /*control frame payload length < 126 */
						goto_close(WS_CLOSE_PROTOCOL_ERROR);
					}
				}
				if (wsctx->len == 126) {
					check_buf_data_enough(buf, 2, TOP_WHILE);
					wsctx->len = ntohs(*((uint16_t*)buf->pos));
					buf->pos += 2;
				}else if (wsctx->len == 127) {
					check_buf_data_enough(buf, 8, TOP_WHILE);
					wsctx->len = nc_ntohll(*((uint64_t*)buf->pos));
					buf->pos += 8;
				}
				wsctx->pstate = NGX_HTTP_CLOJURE_WEBSOCKET_PARSE_MASK;
				/* no break */
				/*fallthrough*/
			case NGX_HTTP_CLOJURE_WEBSOCKET_PARSE_MASK:
				if (wsctx->mask) {
					check_buf_data_enough(buf, 4, TOP_WHILE);
					ngx_memcpy(wsctx->mcode, buf->pos, 4);
					buf->pos += 4;
				}
				wsctx->pstate = NGX_HTTP_CLOJURE_WEBSOCKET_PARSE_DATA;
				/* no break */
				/*fallthrough*/
			case NGX_HTTP_CLOJURE_WEBSOCKET_PARSE_DATA:

				/*when buf->end == buf->last we need handle data at once because there's no free space to read more,
				 *otherwise if remaining data length < wsctx->len we can goto TOP_WHILE to take more data*/
				if (buf->end != buf->last || (wsctx->opcode & 0x8)) {
					if (buf->pos != wsctx->left_pos) {
						check_buf_data_enough(buf, wsctx->len, TOP_WHILE);
					}else {
						check_buf_data_enough(buf, wsctx->len + wsctx->left, TOP_WHILE);
					}
				}else if (buf->pos == wsctx->left_pos && buf->last - buf->pos == (int)wsctx->left) {
					/*only undecoded data left, we must shrink it and goto TOP_WHILE to take more data*/
					check_buf_data_enough(buf, wsctx->len + wsctx->left, TOP_WHILE);
				}

				if (buf->pos == wsctx->left_pos) {
					buf->pos += wsctx->left;
				}

				size = buf->last - buf->pos;

				if (wsctx->opcode == NGX_HTTP_CLOJURE_WEBSOCKET_OPCODE_CLOSE) {
					type |= NGX_HTTP_CLOJURE_CHANNEL_EVENT_MSGCLOSE;
					wsctx->left = 0;
					wsctx->left_pos = NULL;
					if (wsctx->len && (wsctx->len < 2 || wsctx->len > 125)) {
						goto_close(WS_CLOSE_PROTOCOL_ERROR);
					}
				}else if (wsctx->opcode == NGX_HTTP_CLOJURE_WEBSOCKET_OPCODE_PONG) {
					buf->pos += wsctx->len;
					wsctx->pstate = NGX_HTTP_CLOJURE_WEBSOCKET_PARSE_START;
					continue;
				}else if (wsctx->left && !(wsctx->opcode & 0x8)) {
					wsctx->len += wsctx->left;
					size += wsctx->left;
					if (buf->pos != wsctx->left_pos) { /*last left undecoded data is from last frame*/
						buf->pos = buf->pos - wsctx->left; //if (buf->pos != wsctx->left_pos)
						ngx_memmove(buf->pos, wsctx->left_pos, wsctx->left);
					}
				}

				if (size > wsctx->len) {
					size = wsctx->len;
				}

/*				if (buf->last == buf->end || wsctx->len == size)*/
				{
					u_char *pc;
					u_char *end;

					if (!wsctx->cont && start_with_header) {
						type |= NGX_HTTP_CLOJURE_CHANNEL_EVENT_MSGFIRST;
					}
					if (!wsctx->fin || wsctx->len > size) {
						type |= NGX_HTTP_CLOJURE_CHANNEL_EVENT_MSGREMAIN;
					}
					switch (wsctx->opcode) {
					case NGX_HTTP_CLOJURE_WEBSOCKET_OPCODE_TEXT:
						type |= NGX_HTTP_CLOJURE_CHANNEL_EVENT_MSGTEXT;
						wsctx->ltxt = 1;
						break;
					case NGX_HTTP_CLOJURE_WEBSOCKET_OPCODE_BIN:
						type |= NGX_HTTP_CLOJURE_CHANNEL_EVENT_MSGBIN;
						wsctx->ltxt = 0;
						break;
					case NGX_HTTP_CLOJURE_WEBSOCKET_OPCODE_CLOSE:
						type |= NGX_HTTP_CLOJURE_CHANNEL_EVENT_MSGCLOSE;
						break;
					case NGX_HTTP_CLOJURE_WEBSOCKET_OPCODE_CONT:
						type |= (wsctx->ltxt ?  NGX_HTTP_CLOJURE_CHANNEL_EVENT_MSGTEXT : NGX_HTTP_CLOJURE_CHANNEL_EVENT_MSGBIN);
						break;
					case NGX_HTTP_CLOJURE_WEBSOCKET_OPCODE_PING:
						type |= NGX_HTTP_CLOJURE_CHANNEL_EVENT_MSGPONG;
						break;
					default:/*invalid opcode*/
						goto_close(WS_CLOSE_PROTOCOL_ERROR);
					}

					pc = buf->pos;

					/* We should skip the remaining bytes of last decoding.
					 * Only data frame need do so.*/
					if (wsctx->left && !(wsctx->opcode & 0x8)) {
						pc += wsctx->left;
					}

					for (end = buf->pos + size; pc != end; pc++) {
						*pc ^= wsctx->mcode[wsctx->mpos];
						wsctx->mpos = (wsctx->mpos + 1) % 4;
					}

					if (wsctx->opcode == NGX_HTTP_CLOJURE_WEBSOCKET_OPCODE_CLOSE && wsctx->len) {
						int ccode = (buf->pos[0] << 8) | buf->pos[1];
						if (!is_valid_ws_close_code(ccode)) {
							goto_close(WS_CLOSE_PROTOCOL_ERROR);
						}
					} else if (type & NGX_HTTP_CLOJURE_CHANNEL_EVENT_MSGPONG) {
						rc = ngx_http_clojure_hijack_send(r, buf->pos, (size_t)wsctx->len,
								NGX_CLOJURE_BUF_WEBSOCKET_PONG_FRAME
						       | NGX_CLOJURE_BUF_FLUSH_FLAG);
						if (rc != NGX_OK && !c->destroyed) {
							ngx_http_finalize_request(r, rc);
							return;
						}
						buf->pos += wsctx->len;
						wsctx->pstate = NGX_HTTP_CLOJURE_WEBSOCKET_PARSE_START;
						continue;
					}

					wsctx->len -= size;
					pc = buf->pos;
#if (NGX_ZLIB)
					if (wsctx->compressed && !(wsctx->opcode & 0x8)) { /*PMCEs operate only on data messages.*/
						u_char deflate_buf[8192];
						u_char *deflate_buf_pos;
						int zrc = 0;
						jint dtype;
						jint typec = type; /*a copy of variable type, we maybe modify it later*/
						int first_block = start_with_header;
						size_t uncompressed_size = 0;
						int tail_appended = 0;
						if (wsctx->in_no_ctx_takeover && (typec & NGX_HTTP_CLOJURE_CHANNEL_EVENT_MSGFIRST) && inflateReset(&wsctx->zin)) {
							ngx_log_error(NGX_LOG_ERR, r->connection->log, 0, "inflateReset error!");
							goto_close(WS_CLOSE_UNEXPECTED_CONDITION);
						}
						wsctx->zin.avail_in = size - wsctx->left; /*remaining bytes of last decoding have been uncompressed already*/
						wsctx->zin.next_in = buf->pos + wsctx->left;

						do {
							dtype = typec;
							if (wsctx->left && wsctx->left_pos != deflate_buf) {
								ngx_memcpy(deflate_buf, wsctx->left_pos, wsctx->left);
							}
							wsctx->zin.avail_out = sizeof(deflate_buf) - wsctx->left;
							deflate_buf_pos = deflate_buf + wsctx->left;
							wsctx->zin.next_out = deflate_buf_pos;
							zrc = inflate(&wsctx->zin, Z_NO_FLUSH);
							if (zrc == Z_STREAM_END) {
								if (!wsctx->fin || wsctx->zin.avail_in) {
									goto_close(WS_CLOSE_PROTOCOL_ERROR);
								}
								break;
							}else if (zrc == Z_BUF_ERROR) {
								goto TRY_APPENDING_TAIL;
							}else if (zrc != Z_OK && zrc != Z_BUF_ERROR) {
								ngx_log_error(NGX_LOG_ERR, r->connection->log, 0, "inflate error : %d", zrc);
								goto_close(WS_CLOSE_UNEXPECTED_CONDITION);
							}

							if (wsctx->zin.avail_in) {
								dtype |= NGX_HTTP_CLOJURE_CHANNEL_EVENT_MSGREMAIN;
							}

							uncompressed_size = (size_t)(wsctx->zin.next_out - deflate_buf_pos);
							if (uncompressed_size || ((first_block || !tail_appended) && wsctx->fin)) { /*we need give a chance to last empty fin frame*/
								deflate_buf_pos = deflate_buf;
								uncompressed_size += wsctx->left;

								nji_ngx_http_clojure_hijack_fire_channel_event(dtype,
										(uintptr_t) &deflate_buf_pos | ((uint64_t) uncompressed_size << 48), ctx);
								typec &= ~NGX_HTTP_CLOJURE_CHANNEL_EVENT_MSGFIRST;
								if (deflate_buf - deflate_buf_pos > 3) {
									goto_close(WS_CLOSE_NOT_CONSISTENT);
								}
								wsctx->left = deflate_buf - deflate_buf_pos;
								if (wsctx->left) {
									deflate_buf_pos += uncompressed_size;
									ngx_memmove(deflate_buf, deflate_buf_pos, wsctx->left);
									wsctx->left_pos = deflate_buf;
								}
							}

							first_block = 0;

							if (wsctx->zin.avail_out == 0) {
								if (!wsctx->zin.avail_in) {
									goto TRY_APPENDING_TAIL;
								}
								continue;
							}

							if (wsctx->zin.avail_in != 0) {
								continue;
							}
TRY_APPENDING_TAIL:
							if (tail_appended || (type & NGX_HTTP_CLOJURE_CHANNEL_EVENT_MSGREMAIN)) {
								break;
							}
							wsctx->zin.avail_in = sizeof(WS_PMCE_TAIL_DATA);
							wsctx->zin.next_in = WS_PMCE_TAIL_DATA;
							tail_appended = 1;
						}while(1);

						/*copy remaining bytes of last decoding to buf->pos*/
						if (wsctx->left) {
							buf->pos -= wsctx->left;
							ngx_memcpy(buf->pos + size, wsctx->left_pos, wsctx->left);
						}

					}else
#endif
					{
						nji_ngx_http_clojure_hijack_fire_channel_event(type, (uintptr_t)&buf->pos | ((uint64_t)size << 48) , ctx);
					}

					if (c->destroyed) {
						return;
					}

					if (type & NGX_HTTP_CLOJURE_CHANNEL_EVENT_MSGCLOSE) {
						if (pc != buf->pos) {
							goto_close(WS_CLOSE_NOT_CONSISTENT);
						}
						/* On thread pool mode we need let listener get a chance to handle client close event?*/
						/*goto SEND_CLOSE_FRAME;*/
						return;
					}

					if (pc - buf->pos > 3 || ( (pc - buf->pos) && wsctx->fin && !wsctx->len)) {
						goto_close(WS_CLOSE_NOT_CONSISTENT);
					}

					wsctx->left = pc - buf->pos;

/*					if (wsctx->left) {
						ngx_log_error(NGX_LOG_ERR, r->connection->log, 0,
								"nji_ngx_http_clojure_hijack_read_handler wsctx->left=%d", wsctx->left);
					}*/

					buf->pos += size;

					if (!wsctx->len) {
						wsctx->pstate = NGX_HTTP_CLOJURE_WEBSOCKET_PARSE_START;
					}
					if (buf->pos != buf->last) { /*we have read the next frame or there are remaining bytes after last decoding*/
						if (wsctx->left) {
							wsctx->left_pos = buf->pos;
						}
					}else {
SEND_CLOSE_FRAME:
#if nginx_version >= 1001004
						ngx_chain_update_chains(r->pool, &ctx->free, &ctx->busy, &wsctx->rchain,
								(ngx_buf_tag_t) &ngx_http_clojure_module);
#else
						ngx_chain_update_chains(&ctx->free, &ctx->busy, &wsctx->rchain, (ngx_buf_tag_t)&ngx_http_clojure_module);
#endif
						wsctx->left_pos = NULL;
						buf = NULL;

						if (type & NGX_HTTP_CLOJURE_CHANNEL_EVENT_MSGCLOSE) {
//							char close_msg[] = { 0x03, 0xe8 }; /*Close: Normal Closure (1000)*/
							rc = ngx_http_clojure_hijack_send(r, close_msg, 2,
									NGX_CLOJURE_BUF_WEBSOCKET_CLOSE_FRAME
							       | NGX_CLOJURE_BUF_FLUSH_FLAG
							       | NGX_CLOJURE_BUF_LAST_FLAG);
							if (rc != NGX_OK && r->pool) {
								ngx_http_finalize_request(r, rc);
							}
							return;
						}else {
							goto TOP_WHILE;
						}
					}
				}
				continue;
			default :
				break;
			}
		}while(1);
	}

	if (!c->destroyed) {
		if (wsctx && wsctx->rchain) {
		  wsctx->rchain->buf->pos = wsctx->rchain->buf->last; /*prepare to recycle it*/
#if nginx_version >= 1001004
		  ngx_chain_update_chains(r->pool, &ctx->free, &ctx->busy, &wsctx->rchain, (ngx_buf_tag_t) &ngx_http_clojure_module);
#else
		  ngx_chain_update_chains(&ctx->free, &ctx->busy, &wsctx->rchain, (ngx_buf_tag_t)&ngx_http_clojure_module);
#endif
		}
		nji_ngx_http_clojure_hijack_fire_channel_event(NGX_HTTP_CLOJURE_CHANNEL_EVENT_READ, flag, ctx);
		if (flag != NGX_HTTP_CLOJURE_SOCKET_OK && !c->destroyed) {
			ngx_http_finalize_request(r, NGX_HTTP_REQUEST_TIME_OUT);
		}
	}

}

static void nji_ngx_http_clojure_hijack_write_handler(ngx_http_request_t *r) {
	ngx_connection_t *c = r->connection;
	ngx_http_clojure_module_ctx_t *ctx;
	jlong flag = NGX_HTTP_CLOJURE_SOCKET_OK;

	ngx_http_clojure_get_ctx(r, ctx);

	if (c->write->timedout) {
		flag = NGX_HTTP_CLOJURE_SOCKET_ERR_WRITE_TIMEOUT;
	}else if (c->write->timer_set) {
		ngx_del_timer(c->write);
	}


	nji_ngx_http_clojure_hijack_fire_channel_event(NGX_HTTP_CLOJURE_CHANNEL_EVENT_WRITE, flag, ctx);

	/*If the write handler didn't do any writing, we need to delete this event for level/select/poll event to avoid
	 * foolish repeated write event notification*/
	if (c->write->ready) {
		(void) ngx_handle_write_event(c->write, 0);
	}

	if (flag != NGX_HTTP_CLOJURE_SOCKET_OK) {
		ngx_http_finalize_request(r, NGX_HTTP_REQUEST_TIME_OUT);
	}
}

#if ((NGX_HAVE_SHA1 || nginx_version >= 1011002) && NGX_ZLIB)

static void *ngx_http_clojure_websocket_alloc(void *opaque, u_int items, u_int size) {
	ngx_http_clojure_module_ctx_t *ctx = (ngx_http_clojure_module_ctx_t *)opaque;
	/*TODO: optimize for large buffer to avoid frequently syscalls such as sbrk(), mmap() etc.*/
	return ngx_palloc(ctx->r->pool, items * size);
}

static void ngx_http_clojure_websocket_free(void *opaque, void *address) {
}

#endif

ngx_int_t ngx_http_clojure_websocket_upgrade(ngx_http_request_t * r) {
	ngx_http_clojure_module_ctx_t *ctx;
#if (NGX_HAVE_SHA1 || nginx_version >= 1011002)
  ngx_http_clojure_websocket_ctx_t *wsctx = NULL;
  ngx_int_t rc = NGX_OK;
  ngx_table_elt_t *key = NULL;
	ngx_table_elt_t *accept;
	ngx_table_elt_t *cver = NULL;
	ngx_sha1_t   sha1_ctx;
	u_char degest[21];
    ngx_str_t sha1_val;
    sha1_val.len = sizeof(degest) - 1;
    sha1_val.data = (u_char *) degest;

    ctx = ngx_http_get_module_ctx(r, ngx_http_clojure_module);

    if (r->header_sent) {
        ngx_log_error(NGX_LOG_ERR, r->connection->log, 0, "header was already sent, auto_upgrade_ws is on?");
        if (ctx->wsctx) {
        	return NGX_OK;
        }
        rc = NGX_HTTP_BAD_REQUEST;
        goto UPGRADE_DONE;
    }

    if (r->method != NGX_HTTP_GET || r->headers_in.upgrade == NULL
    			|| ngx_strcasecmp(r->headers_in.upgrade->value.data, (u_char*)"websocket") != 0) {
    	rc = NGX_HTTP_BAD_REQUEST;
    	goto UPGRADE_DONE;
    }

	ngx_http_clojure_add_const_header(r->headers_out.headers, "Sec-WebSocket-Version", "13");

	ngx_http_clojure_get_header(r->headers_in.headers, "Sec-WebSocket-Version", cver);
	if (cver == NULL || ngx_atoi(cver->value.data, cver->value.len) != 13) {
		rc = NGX_HTTP_BAD_REQUEST;
		goto UPGRADE_DONE;
	}

	ngx_http_clojure_get_header(r->headers_in.headers, "Sec-WebSocket-Key", key);
	if (key == NULL) {
		rc = NGX_HTTP_BAD_REQUEST;
		goto UPGRADE_DONE;
	}

	r->headers_out.status = NGX_HTTP_SWITCHING_PROTOCOLS;
	ngx_http_clojure_add_const_header(r->headers_out.headers, "Upgrade", "websocket");
	ngx_http_clojure_add_const_header(r->headers_out.headers, "Connection", "upgrade");

	accept = ngx_list_push(&r->headers_out.headers);
	accept->hash = 1;
	ngx_str_set(&accept->key, "Sec-WebSocket-Accept");
	accept->value.len = 28;
	accept->value.data = ngx_palloc(r->pool, 28);

    ngx_sha1_init(&sha1_ctx);
    ngx_sha1_update(&sha1_ctx, key->value.data, key->value.len);
    ngx_sha1_update(&sha1_ctx, "258EAFA5-E914-47DA-95CA-C5AB0DC85B11", 36);
    ngx_sha1_final(degest, &sha1_ctx);

    ngx_encode_base64(&accept->value, &sha1_val);
    wsctx = ctx->wsctx;

    if (wsctx == NULL) {
#if (NGX_ZLIB)
    	ngx_table_elt_t *in_extensions = NULL;
    	ngx_table_elt_t *out_extensions = NULL;
    	int in_max_window_bits = 15;
    	int out_max_window_bits = 15;
    	/*TODO: negotiate in_max_window_bits & out_max_window_bits, e.g. "permessage-deflate;server_max_window_bits=11;client_max_window_bits=11"*/
    	char out_extensions_tmp_value[1024] = "permessage-deflate";
#endif
    	wsctx = ngx_pcalloc(r->pool, sizeof(ngx_http_clojure_websocket_ctx_t));
		  wsctx->ffm = 1;
		  wsctx->fin = 1;
#if (NGX_ZLIB)
    	ngx_http_clojure_get_header(r->headers_in.headers, "Sec-WebSocket-Extensions", in_extensions);
    	if (in_extensions != NULL && ngx_strcasestrn(in_extensions->value.data, "permessage-deflate", 18-1)) {
    		wsctx->premsg_deflate = 1;
    		wsctx->compressed = 0;
    		if (ngx_strcasestrn(in_extensions->value.data, "client_no_context_takeover", 26-1)) {
    			wsctx->out_no_ctx_takeover = 1;
    			sprintf(out_extensions_tmp_value+strlen(out_extensions_tmp_value), ";%s", "client_no_context_takeover");
    		}
    		if (ngx_strcasestrn(in_extensions->value.data, "server_no_context_takeover", 26-1)) {
    		    wsctx->in_no_ctx_takeover = 1;
    		    sprintf(out_extensions_tmp_value+strlen(out_extensions_tmp_value), ";%s", "server_no_context_takeover");
    		}
    		/*TODO: negotiate in_max_window_bits & out_max_window_bits*/
    		out_extensions = ngx_list_push(&r->headers_out.headers);
    		out_extensions->hash = 1;
    		ngx_str_set(&out_extensions->key, "Sec-WebSocket-Extensions");
    		out_extensions->value.data = ngx_pcalloc(r->pool, strlen(out_extensions_tmp_value)+1);
    		out_extensions->value.len = strlen(out_extensions_tmp_value);
    		ngx_memcpy(out_extensions->value.data, out_extensions_tmp_value, out_extensions->value.len+1);
    		wsctx->zin.zalloc = wsctx->zout.zalloc = ngx_http_clojure_websocket_alloc;
    		wsctx->zin.zfree  = wsctx->zout.zfree  = ngx_http_clojure_websocket_free;
    		wsctx->zin.opaque = wsctx->zout.opaque = ctx;

			if ((rc = deflateInit2(&wsctx->zout, Z_DEFAULT_COMPRESSION, Z_DEFLATED,
					-out_max_window_bits, 8, Z_DEFAULT_STRATEGY)) != Z_OK
					|| (rc = inflateInit2(&wsctx->zin, -in_max_window_bits)) != Z_OK) {
				ngx_log_error(NGX_LOG_ERR, r->connection->log, 0, "can not initialize deflate context, code=%d", rc);
				rc = NGX_HTTP_INTERNAL_SERVER_ERROR;
				goto UPGRADE_DONE;
			}
    	}
#endif

    }

    rc = ngx_http_clojure_hijack_send_header(r, 0);

UPGRADE_DONE:
    if (ctx->hijacked_or_async) {
        if (rc == NGX_HTTP_BAD_REQUEST || rc == NGX_HTTP_INTERNAL_SERVER_ERROR) {
        	ngx_http_finalize_request(r, rc);
        }
    }
    ctx->wsctx = wsctx;
    return rc;
#else
    ctx = ngx_http_get_module_ctx(r, ngx_http_clojure_module);
    ngx_log_error(NGX_LOG_ERR, r->connection->log, 0, "nginx-clojure websocket support need SHA1 enabled");
    if (ctx->hijacked_or_async) {
    	ngx_http_finalize_request(r, 500);
    }
    return NGX_ERROR;
#endif
}

static jlong JNICALL jni_ngx_http_clojure_websocket_upgrade(JNIEnv *env, jclass cls, jlong req, jint flag) {
	ngx_http_request_t * r = (ngx_http_request_t *)(uintptr_t)req;
	if (!flag) {
		if (r->headers_in.upgrade == NULL
    			|| ngx_strcasecmp(r->headers_in.upgrade->value.data, (u_char*)"websocket") != 0) {
			return NGX_HTTP_BAD_REQUEST;
		}
	}
	return ngx_http_clojure_websocket_upgrade(r);
}

/**
 * flag can be either of or combined of
 * 0
 * NGX_HTTP_CLOJURE_EVENT_HANDLER_FLAG_READ  1
 * NGX_HTTP_CLOJURE_EVENT_HANDLER_FLAG_WRITE 2
 */
static void JNICALL jni_ngx_http_hijack_turn_on_event_handler(JNIEnv *env, jclass cls, jlong req, jint flag) {
	ngx_http_request_t *r = (ngx_http_request_t *)(uintptr_t)req;
	ngx_http_clojure_module_ctx_t *ctx;
	ngx_connection_t *c = r->connection;
	ngx_http_core_loc_conf_t *clcf;

	ngx_http_clojure_get_ctx(r, ctx);
	clcf = ngx_http_get_module_loc_conf(r, ngx_http_core_module);
	ctx->event_handler_flag = flag;

	c->log->action = "upgraded connection";
	if (flag & NGX_HTTP_CLOJURE_EVENT_HANDLER_FLAG_NOKEEPALIVE) {
		r->keepalive = 0;

		if (clcf->tcp_nodelay) {
			int tcp_nodelay = 1;

			if (c->tcp_nodelay == NGX_TCP_NODELAY_UNSET) {
				ngx_log_debug0(NGX_LOG_DEBUG_HTTP, c->log, 0, "tcp_nodelay");

				if (setsockopt(c->fd, IPPROTO_TCP, TCP_NODELAY, (const void *) &tcp_nodelay, sizeof(int)) == -1) {
					ngx_connection_error(c, ngx_socket_errno, "setsockopt(TCP_NODELAY) failed");
					ngx_http_finalize_request(r, NGX_ERROR);
					return;
				}
				c->tcp_nodelay = NGX_TCP_NODELAY_SET;
			}
		}
	}

	if (r->buffered || (r == r->main && c->buffered)) {
		if (r->write_event_handler != ngx_http_clojure_hijack_writer) {
			r->write_event_handler = ngx_http_clojure_hijack_writer;
		}
	} else {
		if (flag & NGX_HTTP_CLOJURE_EVENT_HANDLER_FLAG_READ) {
			r->read_event_handler = nji_ngx_http_clojure_hijack_read_handler;
			if (!c->read->active && ngx_handle_read_event(c->read, 0) != NGX_OK) {
				ngx_http_finalize_request(r, NGX_ERROR);
				return;
			}
		}

		if (flag & NGX_HTTP_CLOJURE_EVENT_HANDLER_FLAG_WRITE) {
			r->write_event_handler = nji_ngx_http_clojure_hijack_write_handler;
			if (!c->write->active) {
				c->write->ready = 0; /*clear write ready flag because websocket is obviously write ready after handshake.*/
				if (ngx_handle_write_event(c->write, clcf->send_lowat) != NGX_OK) {
					ngx_http_finalize_request(r, NGX_ERROR);
					return;
				}
			}
		}
	}
}

/*Originally this function is from ngx_http_request_body.c because it is static so we have to copy it here*/
static ngx_int_t ngx_http_test_expect(ngx_http_request_t *r) {
  ngx_int_t n;
  ngx_str_t *expect;

  if (r->expect_tested || r->headers_in.expect == NULL || r->http_version < NGX_HTTP_VERSION_11) {
    return NGX_OK;
  }

  r->expect_tested = 1;

  expect = &r->headers_in.expect->value;

  if (expect->len != sizeof("100-continue") - 1
      || ngx_strncasecmp(expect->data, (u_char *) "100-continue", sizeof("100-continue") - 1) != 0) {
    return NGX_OK;
  }

  ngx_log_debug0(NGX_LOG_DEBUG_HTTP, r->connection->log, 0, "send 100 Continue");

  n = r->connection->send(r->connection, (u_char *) "HTTP/1.1 100 Continue" CRLF CRLF,
      sizeof("HTTP/1.1 100 Continue" CRLF CRLF) - 1);

  if (n == sizeof("HTTP/1.1 100 Continue" CRLF CRLF) - 1) {
    return NGX_OK;
  }

  /* we assume that such small packet should be send successfully */

  return NGX_ERROR;
}

static jlong JNICALL jni_ngx_http_hijack_read(JNIEnv *env, jclass cls, jlong req, jobject buf, jlong off, jlong len) {
	ngx_http_request_t *r = (ngx_http_request_t *)(uintptr_t)req;
	ngx_connection_t *c;
	ngx_int_t rc;
	ngx_int_t prrlen = 0;
	ngx_http_clojure_loc_conf_t *lcf;
	ngx_http_clojure_module_ctx_t *ctx;
	u_char* pbuf = (u_char*)ngx_http_clojure_abs_off_addr(buf, off);

	if (!r->pool) {
		return NGX_HTTP_CLOJURE_SOCKET_ERR_READ;
	}

	ngx_http_clojure_get_ctx(r, ctx);
	lcf = ngx_http_get_module_loc_conf(r, ngx_http_clojure_module);

	if (!ctx->client_body_done && !ctx->wsctx && lcf->always_read_body == NGX_HTTP_CLOJURE_BEFORE_NONE) {
	  if (ngx_http_test_expect(r) != NGX_OK) {
	    return NGX_HTTP_CLOJURE_SOCKET_ERR_READ;
	  }

	  prrlen = r->header_in->last > r->header_in->pos;
	  if (prrlen) {
	    if (prrlen > len) {
	      prrlen = len;
	    }
	    ngx_memcpy(pbuf, r->header_in->pos, prrlen);
	    r->header_in->pos += prrlen;
	    pbuf += prrlen;

	    if (r->header_in->pos == r->header_in->last) {
	      ctx->client_body_done = 1;
	    }else {
	      return len;
	    }
	  }else {
	    ctx->client_body_done = 1;
	  }
	}

	c = r->connection;
/*	clcf = ngx_http_get_module_loc_conf(r, ngx_http_core_module);*/
	rc = c->recv(c, pbuf, len - prrlen);

	if (rc == NGX_AGAIN) {
/*websocket should have infinite timeout */
/*		if (clcf->client_body_timeout > 0) {
			ngx_add_timer(c->read, clcf->client_body_timeout);
		}*/

		return NGX_HTTP_CLOJURE_SOCKET_ERR_AGAIN;
	}else if (rc < 0) {
		return NGX_HTTP_CLOJURE_SOCKET_ERR_READ;
	}
	/*TODO: if rc == 0 we need release the request ? */
	return rc + prrlen;

}

static jlong JNICALL jni_ngx_http_hijack_write(JNIEnv *env, jclass cls, jlong req, jobject buf, jlong off, jlong len) {
	ngx_http_request_t *r = (ngx_http_request_t *) (uintptr_t) req;
	ngx_connection_t *c;
	ngx_int_t rc;
	ngx_http_core_loc_conf_t *clcf;

	if (!r->pool) {
		return NGX_HTTP_CLOJURE_SOCKET_ERR_WRITE;
	}

	c = r->connection;
	clcf = ngx_http_get_module_loc_conf(r, ngx_http_core_module);
	rc = c->send(c, (u_char*)ngx_http_clojure_abs_off_addr(buf, off), len);

	if (rc == 0 || rc == NGX_AGAIN) {
		/*Because if connected immediately successfully or we have deleted this event in
		 * ngx_http_clojure_socket_upstream_handler the write event was not registered
		 * so we need register it here.*/
		if (!c->write->active) {
			(void) ngx_handle_write_event(c->write, 0);
		}
       /*Although tomcat has its own management about websocekt write timeout
        * we still give a chance to set it at nginx.conf*/
		clcf = ngx_http_get_module_loc_conf(r, ngx_http_core_module);
		if (clcf->send_timeout > 0) {
			ngx_add_timer(c->write, clcf->send_timeout);
		}
		rc = NGX_HTTP_CLOJURE_SOCKET_ERR_AGAIN;
	} else if (rc < 0) {
		rc = NGX_HTTP_CLOJURE_SOCKET_ERR_WRITE;
	}
	return rc;
}

ngx_int_t ngx_http_clojure_hijack_send_header(ngx_http_request_t *r, ngx_int_t flag) {
	ngx_http_clojure_module_ctx_t *ctx;
	ngx_int_t rc;
	ngx_connection_t *c;
	ngx_http_core_loc_conf_t *clcf;

	if (r->header_sent) {
		ngx_log_error(NGX_LOG_ERR, ngx_http_clojure_global_cycle->log, 0, "jni_ngx_http_hijack_send_header:"
						"header already sent");
		return NGX_ERROR;
	}

	if (r->pool == NULL) {
		ngx_log_error(NGX_LOG_ERR, ngx_http_clojure_global_cycle->log, 0, "jni_ngx_http_hijack_send_header:"
				"can not send header because the request was closed");
		return NGX_ERROR;
	}

	ngx_http_clojure_get_ctx(r, ctx);
	clcf = ngx_http_get_module_loc_conf(r, ngx_http_core_module);
	c = r->connection;

	if (r->headers_out.status == NGX_HTTP_SWITCHING_PROTOCOLS) {
		ctx->event_handler_flag = (NGX_HTTP_CLOJURE_EVENT_HANDLER_FLAG_READ | NGX_HTTP_CLOJURE_EVENT_HANDLER_FLAG_WRITE);
		flag |= NGX_CLOJURE_BUF_FLUSH_FLAG;
		flag |= NGX_CLOJURE_BUF_IGNORE_FILTER_FLAG;
		r->keepalive = 0;
		c->log->action = "upgraded connection";

		if (clcf->tcp_nodelay) {
			int tcp_nodelay = 1;

			if (c->tcp_nodelay == NGX_TCP_NODELAY_UNSET) {
				ngx_log_debug0(NGX_LOG_DEBUG_HTTP, c->log, 0, "tcp_nodelay");

				if (setsockopt(c->fd, IPPROTO_TCP, TCP_NODELAY, (const void *) &tcp_nodelay, sizeof(int)) == -1) {
					ngx_connection_error(c, ngx_socket_errno, "setsockopt(TCP_NODELAY) failed");
					ngx_http_finalize_request(r, NGX_ERROR);
					return NGX_ERROR;
				}

				c->tcp_nodelay = NGX_TCP_NODELAY_SET;
			}
		}
	}

	if (flag & NGX_CLOJURE_BUF_IGNORE_FILTER_FLAG) {
		ctx->ignore_filters = 1;
	}else {
		ctx->ignore_filters = 0;
	}

	if ( ngx_http_clojure_prepare_server_header(r) != NGX_OK) {
		return NGX_ERROR;
	}

	rc = ctx->ignore_filters ? ngx_http_header_filter(r) : ngx_http_send_header(r);

	if (rc == NGX_OK || rc == NGX_AGAIN) {
		if ((flag & NGX_CLOJURE_BUF_LAST_FLAG) || (flag & NGX_CLOJURE_BUF_FLUSH_FLAG)) {
			rc = ngx_http_clojure_hijack_send(r, 0, 0, flag);
			if (c->destroyed) {
				return NGX_OK; /*TODO: return NGX_DONE?*/
			}
			if (rc == NGX_ERROR) {
				return rc;
			}
			if (rc != NGX_OK) {
				if (ctx->hijacked_or_async) ngx_http_finalize_request(r, rc);
				return rc;
			}
			if (ctx->wsctx) {
				nji_ngx_http_clojure_hijack_fire_channel_event(NGX_HTTP_CLOJURE_CHANNEL_EVENT_CONNECT, 0, ctx);
			}
		}

		return rc;
	}else {
		if (ctx->hijacked_or_async) ngx_http_finalize_request(r, rc);
		return rc;
	}
}

ngx_int_t ngx_http_hijack_send_header_by_buf(ngx_http_request_t *r, ngx_buf_t *b, ngx_int_t flag) {
	ngx_http_status_t status;
	ngx_int_t rc;
	ngx_table_elt_t *h;
	ngx_http_clojure_header_holder_t *hh;
	ngx_http_clojure_main_conf_t *cmcf;

	/* gcc 4.1.2 will give warning about 'ngx_http_status_t status = {0}' */
	ngx_memzero(&status, sizeof(ngx_http_status_t));

	/*if not ignore nginx filters we need parse the headers*/
	if ( !(flag & NGX_CLOJURE_BUF_IGNORE_FILTER_FLAG) ) {
		if (ngx_http_parse_status_line(r, b, &status) != NGX_OK) {
			return NGX_ERROR;
		}

		r->headers_out.status = status.code;
/*		r->headers_out.status_line.data = status.start;
		r->headers_out.status_line.len = status.end - status.start;*/

		cmcf = ngx_http_get_module_main_conf(r, ngx_http_clojure_module);

	    for ( ;; ) {

	        rc = ngx_http_parse_header_line(r, b, 1);

	        if (rc == NGX_OK) {

	            /* a header line has been parsed successfully */

	            h = ngx_list_push(&r->headers_out.headers);
	            if (h == NULL) {
	                return NGX_ERROR;
	            }

	            h->hash = r->header_hash;

	            h->key.len = r->header_name_end - r->header_name_start;
	            h->value.len = r->header_end - r->header_start;

	            h->key.data = ngx_pnalloc(r->pool,
	                               h->key.len + 1 + h->value.len + 1 + h->key.len);
	            if (h->key.data == NULL) {
	                return NGX_ERROR;
	            }

	            h->value.data = h->key.data + h->key.len + 1;
	            h->lowcase_key = h->key.data + h->key.len + 1 + h->value.len + 1;

	            ngx_memcpy(h->key.data, r->header_name_start, h->key.len);
	            h->key.data[h->key.len] = '\0';
	            ngx_memcpy(h->value.data, r->header_start, h->value.len);
	            h->value.data[h->value.len] = '\0';

	            if (h->key.len == r->lowcase_index) {
	                ngx_memcpy(h->lowcase_key, r->lowcase_header, h->key.len);

	            } else {
	                ngx_strlow(h->lowcase_key, h->key.data, h->key.len);
	            }

	            hh = ngx_hash_find(&cmcf->headers_out_holder_hash, h->hash,
	                               h->lowcase_key, h->key.len);

	            if (hh && hh->handler(r, h, hh->offset) != NGX_OK) {
	                return NGX_ERROR;
	            }

	            continue;
	        }

	        if (rc == NGX_HTTP_PARSE_HEADER_DONE) {
	            /* a whole header has been parsed successfully */
	            break;
	        }

	        /* there was error while a header line parsing */
	        ngx_log_error(NGX_LOG_ERR, r->connection->log, 0,
	                      "server sent invalid header");
	        return NGX_ERROR;
	    }

	    ngx_http_clojure_hijack_send_header(r, flag);
	} else {
		return ngx_http_clojure_hijack_send(r, b->start, b->last-b->start, flag);
	}

	rc = status.code;
	return rc;
}

static jlong JNICALL jni_ngx_http_hijack_send_header_by_buf(JNIEnv *env, jclass cls, jlong req, jobject buf, jlong offset, jlong len, jint flag) {
	ngx_http_request_t *r = (ngx_http_request_t *)(uintptr_t) req;
	ngx_buf_t b;
	/* gcc 4.1.2 will give warning about 'ngx_buf_t b = {0}' */
	ngx_memzero(&b, sizeof(ngx_buf_t));
	b.start = b.pos = (u_char *)ngx_http_clojure_abs_off_addr(buf, offset);
	b.end = b.last = b.start + (size_t)len;
	b.recycled = 1;
	return (jlong)ngx_http_hijack_send_header_by_buf(r, &b, flag);
}

static jlong JNICALL jni_ngx_http_hijack_send_header(JNIEnv *env, jclass cls, jlong req, jint flag) {
	return ngx_http_clojure_hijack_send_header((ngx_http_request_t *)(uintptr_t)req, flag);
}

static jlong JNICALL jni_ngx_http_hijack_send(JNIEnv *env, jclass cls, jlong req, jobject obj, jlong offset, jlong len, jint flag) {

	ngx_int_t rc = ngx_http_clojure_hijack_send((ngx_http_request_t *) (uintptr_t) req,
			ngx_http_clojure_abs_off_addr(obj, offset), len, flag);
	if (rc != NGX_OK && rc != NGX_DONE) {
		ngx_http_finalize_request((ngx_http_request_t *)(uintptr_t)req, rc);
	}
	return rc;
}

static jlong JNICALL jni_ngx_http_hijack_send_chain(JNIEnv *env, jclass cls, jlong req, jlong chain,  jint flag) {
	ngx_int_t rc = ngx_http_clojure_hijack_send_chain((ngx_http_request_t *)(uintptr_t)req, (ngx_chain_t *)(uintptr_t)chain, flag);
	if (rc != NGX_OK && rc != NGX_DONE) {
		ngx_http_finalize_request((ngx_http_request_t *)(uintptr_t)req, rc);
	}
	return rc;
}

static void JNICALL jni_ngx_http_hijack_set_async_timeout(JNIEnv *env, jclass cls, jlong req, jlong timeout) {
	ngx_http_request_t *r = (ngx_http_request_t *) (uintptr_t) req;
	if (r->pool) {
		ngx_connection_t *c = r->connection;
		if (c->write->timer_set) {
			ngx_del_timer(c->write);
		}
		r->write_event_handler = ngx_http_clojure_hijack_async_timeout_handler;
		ngx_add_timer(c->write, (ngx_msec_t)timeout);
	}
}

void ngx_http_clojure_cleanup_handler(void *data) {
  ngx_http_clojure_try_unset_reload_delay_timer((ngx_http_clojure_module_ctx_t *)data, "ngx_http_clojure_cleanup_handler");
  nji_ngx_http_clojure_hijack_fire_channel_event(NGX_HTTP_CLOJURE_CHANNEL_EVENT_CLOSE, 0, data);
}

static ngx_int_t ngx_http_clojure_check_broken_connection(ngx_http_request_t *r, ngx_event_t *ev) {
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
				return 1;
			}
		}
		ngx_http_finalize_request(r, NGX_HTTP_CLIENT_CLOSED_REQUEST);
		return 1;
	}

#if (NGX_HTTP_SPDY)
	if (r->spdy_stream) {
		return 0;
	}
#endif

#if (NGX_HAVE_KQUEUE)

	if (ngx_event_flags & NGX_USE_KQUEUE_EVENT) {

		if (!ev->pending_eof) {
			return 0;
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
		return 1;
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
		return 1;
	}

#endif

	n = recv(c->fd, buf, 1, MSG_PEEK);

	err = ngx_socket_errno;

	ngx_log_debug1(NGX_LOG_DEBUG_HTTP, ev->log, err, "http clojure module recv(): %d", n);

	if (ev->write && (n >= 0 || err == NGX_EAGAIN)) {
		return 0;
	}

	if ((ngx_event_flags & NGX_USE_LEVEL_EVENT) && ev->active) {

		event = ev->write ? NGX_WRITE_EVENT : NGX_READ_EVENT;

		if (ngx_del_event(ev, event, 0) != NGX_OK) {
			ngx_http_finalize_request(r, NGX_HTTP_INTERNAL_SERVER_ERROR);
			return 1;
		}
	}

	if (n > 0) {
		return 0;
	}

	if (n == -1) {
		if (err == NGX_EAGAIN) {
			return 0;
		}

		ev->error = 1;

	} else { /* n == 0 */
		err = 0;
	}

	ev->eof = 1;
	c->error = 1;

	ngx_log_error(NGX_LOG_INFO, ev->log, err, "client prematurely closed connection");

	ngx_http_finalize_request(r, NGX_HTTP_CLIENT_CLOSED_REQUEST);
	return 1;
}

static void ngx_http_clojure_rd_check_broken_connection(ngx_http_request_t *r){
    (void)ngx_http_clojure_check_broken_connection(r, r->connection->read);
}

/*static void ngx_http_clojure_wt_check_broken_connection(ngx_http_request_t *r){
    ngx_http_clojure_check_broken_connection(r, r->connection->write);
}*/


static jlong JNICALL jni_ngx_http_clojure_add_listener(JNIEnv *env, jclass cls, jlong req, jobject listener, jobject data, jint replace) {
	ngx_http_request_t *r = (ngx_http_request_t *)(uintptr_t)req;
	ngx_http_cleanup_t *cu;
	ngx_http_clojure_module_ctx_t *ctx;
	ngx_http_clojure_listener_node_t **ll;

	ngx_http_clojure_get_ctx(r, ctx);

	if (ctx == NULL) {
		ngx_log_error(NGX_LOG_ERR, r->connection->log, 0, "nginx-clojure ctx is cleaned");
		return NGX_ERROR;
	}

	ll = &ctx->listeners;

	if (*ll == NULL) {
		cu = ngx_http_cleanup_add(r, 0);
		cu->handler = ngx_http_clojure_cleanup_handler;
		cu->data = ctx;
	}


	while (*ll) {
		if (replace && ((*env)->IsSameObject(env, (*ll)->listener, listener))) {
			if ((*ll)->data) {
				(*env)->DeleteGlobalRef(env, (*ll)->data);
			}
			(*ll)->data = (*env)->NewGlobalRef(env, data);
			exception_handle((*ll)->data == NULL, env, return NGX_HTTP_CLOJURE_JVM_ERR);
			return NGX_OK;
		}
		ll = &(*ll)->next;
	}

	*ll = ngx_palloc(r->pool, sizeof(ngx_http_clojure_listener_node_t));
	if (data) {
		(*ll)->data = (*env)->NewGlobalRef(env, data);
		exception_handle((*ll)->data == NULL, env, return NGX_HTTP_CLOJURE_JVM_ERR);
	}
	(*ll)->listener = (*env)->NewGlobalRef(env, listener);
	exception_handle((*ll)->listener, env, (*env)->DeleteGlobalRef(env, (jobject)(*ll)->data); return NGX_HTTP_CLOJURE_JVM_ERR);
	(*ll)->next = NULL;

	return NGX_OK;
}

static void JNICALL jni_ngx_http_finalize_request (JNIEnv *env, jclass cls, jlong req , jlong rc) {
	ngx_http_request_t *r = (ngx_http_request_t *)(uintptr_t)req;
	ngx_http_clojure_module_ctx_t *ctx;
	ngx_http_clojure_get_ctx(r, ctx);

	if (!r || !ctx || !r->pool) {
	  ngx_log_error(NGX_LOG_ERR, r->connection->log, 0, "nginx-clojure ctx is cleaned, r=%" PRIu64, (uintptr_t)r);
	  return;
	}

	if (!r->header_sent) {
		(void)ngx_http_clojure_prepare_server_header(r);
	}
	ngx_http_finalize_request(r, (ngx_int_t)rc);
}

static void JNICALL jni_ngx_http_filter_finalize_request(JNIEnv *env, jclass cls, jlong req , jlong rc) {
	ngx_http_filter_finalize_request( (ngx_http_request_t *)(uintptr_t)req,  &ngx_http_clojure_module ,  (ngx_int_t)rc);
}

ngx_int_t ngx_http_clojure_filter_continue_next_body_filter(ngx_http_request_t *r, ngx_chain_t *in) {
	ngx_http_clojure_module_ctx_t *ctx;

	ngx_http_clojure_get_ctx(r, ctx);

	if (ctx && ctx->wait_for_header_filter) {
		ctx->pending_body_filter = 1;
		ngx_chain_add_copy(r->pool, &ctx->pending, in);
		return NGX_OK;
	}
	return ngx_http_clojure_next_body_filter(r, in);
}

static jlong JNICALL jni_ngx_http_filter_continue_next(JNIEnv *env, jclass cls, jlong req, jlong chain, jlong old_chain) {
  ngx_http_request_t *r = (ngx_http_request_t*) (uintptr_t) req;
  ngx_http_clojure_module_ctx_t *ctx;
  ngx_chain_t *in = (ngx_chain_t *) (uintptr_t) chain;
  ngx_chain_t *old_in = (ngx_chain_t*)(uintptr_t) old_chain;
  ngx_int_t rc;

  ngx_http_clojure_get_ctx(r, ctx);

  ngx_http_clojure_try_unset_reload_delay_timer(ctx, "jni_ngx_http_filter_continue_next");

  ngx_log_debug2(NGX_LOG_DEBUG_HTTP, r->connection->log, 0,
                             "jni_ngx_http_filter_continue_next, chain=%" PRIu64 ", old_chain=%" PRIu64, chain, old_chain);

  if (chain < 0) { /*header filter*/
    rc = ngx_http_clojure_next_header_filter(r);

    if (rc == NGX_ERROR || rc > NGX_OK || r->header_only) {
      return rc;
    }

    ctx->wait_for_header_filter = 0;
    if (ctx->pending_body_filter) {
      rc = ngx_http_clojure_next_body_filter(r, ctx->pending);
    }


    if (r->upstream && (chain == NGX_HTTP_HEADER_FILTER_IN_THREADPOOL) && r->upstream->peer.connection) {
      r->upstream->read_event_handler(r, r->upstream);
      r->write_event_handler(r);
    }

    return rc;
  } else {
    int len = 0;
    ngx_chain_t *ci = in;
    int is_last = 0;
    while (ci) {
      if (ci->buf->last_buf) {
        is_last = 1;
      }
      len += ngx_buf_size(ci->buf);
      ci = ci->next;
    }

    ngx_log_debug3(NGX_LOG_DEBUG_HTTP, r->connection->log, 0,
                                 "jni_ngx_http_filter_continue_next, chain=%" PRIu64 ", size=%d, is_last=%d", chain, len, is_last);
    rc = ngx_http_clojure_filter_continue_next_body_filter(r, in);

    if (!is_last && old_in) {
        /*Mark them as consumed*/
        while (old_in) {
          ngx_log_debug5(NGX_LOG_DEBUG_HTTP, r->connection->log, 0, "make consumed, r=%" PRIu64 ", size=%d flush=%d last=%d count=%d",
              (uintptr_t)r, ngx_buf_size(old_in->buf), old_in->buf->flush, old_in->buf->last_in_chain, r->count);
          old_in->buf->pos = old_in->buf->last;
          old_in->buf->file_pos = old_in->buf->file_last;
          old_in = old_in->next;
        }

        if (!ctx->wait_for_header_filter) {
          if (r->upstream && r->count > 1 && r->upstream->peer.connection) {
            ngx_chain_update_chains(r->pool, &r->upstream->free_bufs, &r->upstream->busy_bufs, &r->upstream->out_bufs, r->upstream->output.tag);
            r->upstream->read_event_handler(r, r->upstream);
            r->write_event_handler(r);
          }
        }
    }

    return rc;
  }
}

static jlong JNICALL jni_ngx_http_discard_request_body(JNIEnv *env, jclass cls, jlong req) {
  ngx_http_request_t *r = (ngx_http_request_t*) (uintptr_t) req;
  return ngx_http_discard_request_body(r);
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

#define NGX_CHAIN_FILTER_CHUNK_NO_LAST -1
#define NGX_CHAIN_FILTER_CHUNK_HAS_LAST -2

static jlong JNICALL jni_ngx_http_clojure_mem_build_temp_chain(JNIEnv *env, jclass cls, jlong req , jlong prevChain, jobject obj, jlong offset, jlong len) {
	ngx_chain_t *pre = (ngx_chain_t*)(uintptr_t)prevChain;
	ngx_http_request_t *r = (ngx_http_request_t *)(uintptr_t)req;
	ngx_buf_t *b;
	ngx_chain_t *cl;

	if (!r->pool) {
		return NGX_ERROR;
	}

	if (prevChain < 0) {
	  pre = NULL;
	}

	if (pre != NULL) {
		while (pre->next != NULL) {
			pre = pre->next;
		}
	}

  if (prevChain >= 0) {
    if (r->headers_out.content_length_n < 0) {
      r->headers_out.content_length_n = len;
    } else {
      r->headers_out.content_length_n += len;
    }

    /*
     * If File and String are in the same ISeq of one response body,
     * we should clear the last_modified_time.
     */
    r->headers_out.last_modified_time = -2;
    r->headers_out.last_modified = NULL;
  }

	b = ngx_create_temp_buf(r->pool, (size_t)len);
	if (b == NULL){
		return 0;
	}

	 cl = ngx_palloc(r->pool, sizeof(ngx_chain_t));
	 if (cl == NULL) {
		 return 0;
	 }

	 cl->buf = b;

	 if (len > 0) {
		 ngx_memcpy(b->pos, ngx_http_clojure_abs_off_addr(obj, offset), len);
		 b->last = b->pos + len;
	 }

	 if (pre != NULL) {
		 cl->next = pre->next;
		 pre->next = cl;
		 b->last_in_chain = pre->buf->last_in_chain;
		 b->last_buf = pre->buf->last_buf;
		 pre->buf->last_in_chain = 0;
		 pre->buf->last_buf = 0;
	 }else {
		 cl->next = NULL;
		 b->last_in_chain = 1;
		 b->last_buf = prevChain == NGX_CHAIN_FILTER_CHUNK_NO_LAST ? 0 : 1;
	 }

	 if (b->last_buf && ngx_buf_size(b) == 0) {
	   b->temporary = 0;
	 }
	 return (uintptr_t)cl;
}


static jlong JNICALL jni_ngx_http_clojure_mem_build_file_chain(JNIEnv *env, jclass cls, jlong req , jlong prevChain, jobject  file, jlong offset, jlong len, jboolean safe) {
	ngx_chain_t *pre = (ngx_chain_t*)(uintptr_t)prevChain;
	ngx_http_request_t *r = (ngx_http_request_t *)(uintptr_t)req;
	ngx_buf_t *b;
	ngx_chain_t *cl;
	ngx_str_t path;
	ngx_open_file_info_t of;
	ngx_http_core_loc_conf_t  *clcf;
	ngx_uint_t level;
	ngx_log_t *log = r->connection->log;

	if (!r->pool) {
		return NGX_ERROR;
	}

  if (prevChain < 0) {
    pre = NULL;
  }

	clcf = ngx_http_get_module_loc_conf(r, ngx_http_core_module);

	if (pre != NULL) {
		while (pre->next != NULL) {
			pre = pre->next;
		}
	}

	/*make VS 2010 happy*/
	path.data = (u_char *)ngx_http_clojure_abs_off_addr(file, offset);
	path.len = (ngx_int_t)len;

	/*just like http_static module */

	ngx_memzero(&of, sizeof(ngx_open_file_info_t));

	of.read_ahead = clcf->read_ahead;
	of.directio = clcf->directio;
	of.valid = clcf->open_file_cache_valid;
	of.min_uses = clcf->open_file_cache_min_uses;
	of.errors = clcf->open_file_cache_errors;
	of.events = clcf->open_file_cache_events;

	if (ngx_open_cached_file(safe ? clcf->open_file_cache : NULL, &path, &of, r->pool) != NGX_OK) {
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

    r->allow_ranges = 1;

    b = ngx_pcalloc(r->pool, sizeof(ngx_buf_t));
    if (b == NULL) {
        return -NGX_HTTP_INTERNAL_SERVER_ERROR;
    }

    b->file = ngx_pcalloc(r->pool, sizeof(ngx_file_t));
    if (b->file == NULL) {
        return -NGX_HTTP_INTERNAL_SERVER_ERROR;
    }

    b->file_pos = 0;
    b->file_last = of.size;

    b->in_file = b->file_last ? 1: 0;

    b->file->fd = of.fd;
    b->file->name = path;
    b->file->log = log;
    b->file->directio = of.is_directio;

    if (prevChain >= 0) {
      if (r->headers_out.content_length_n < 0) {
        r->headers_out.content_length_n = of.size;
      } else {
        r->headers_out.content_length_n += of.size;
      }

      if (r->headers_out.last_modified_time != -2 && r->headers_out.last_modified_time < of.mtime) {
        r->headers_out.last_modified_time = of.mtime;
      }
    }

	 cl = ngx_palloc(r->pool, sizeof(ngx_chain_t));
	 if (cl == NULL) {
		 return 0;
	 }

	 cl->buf = b;

	 if (pre != NULL) {
		 cl->next = pre->next;
		 pre->next = cl;
		 b->last_in_chain = pre->buf->last_in_chain;
		 b->last_buf = pre->buf->last_buf;
		 pre->buf->last_in_chain = 0;
		 pre->buf->last_buf = 0;
	 }else {
		 cl->next = NULL;
		 b->last_in_chain = 1;
		 b->last_buf = prevChain == NGX_CHAIN_FILTER_CHUNK_NO_LAST ? 0 : 1;
	 }

	 return (uintptr_t)cl;
}

static jlong JNICALL jni_ngx_http_clojure_mem_get_chain_info(JNIEnv *env, jclass cls, jlong chain, jobject buf, jlong offset, jlong len) {
  ngx_chain_t *cl = (ngx_chain_t *)(uintptr_t)chain;
  uint64_t *pnum = ngx_http_clojure_abs_off_addr(buf, offset);
  uint64_t *pinfo = pnum + 1;
  uint8_t flag;
  uint64_t n = 0;

  len -= 8; /*keep room for stream number*/

  if (chain == 0 || len < 16) {
    return NGX_ERROR;
  }

  while (cl && len >= 16) {
    flag = 0;

    if (cl->buf->last_buf) {
      flag |= NGX_CLOJURE_BUF_LAST_FLAG;
    }

    if (cl->buf->file) {
      uint16_t nameLen = (uint16_t)cl->buf->file->name.len;
      if (len < nameLen + 16) {
        *pnum = n;
        return (uintptr_t)cl;
      }

      flag |= NGX_CLOJURE_BUF_FILE_FLAG;
      *pinfo++ = (uint64_t)flag << 56 | (cl->buf->file_last - cl->buf->file_pos);
      len -= 8;
      *pinfo++ = (uint64_t)nameLen << 48 | cl->buf->file_pos;
      len -= 8;
      *pinfo++ = (uint64_t)cl->buf->file->fd;
      len -= 8;
      ngx_memcpy((char*)(uintptr_t)pinfo, cl->buf->file->name.data, nameLen);
      len -= nameLen;
      pinfo = (uint64_t *)((uintptr_t)pinfo + nameLen);
    }else {
      flag |= NGX_CLOJURE_BUF_MEM_FLAG;
      *pinfo++ = (uint64_t)flag << 56 | (cl->buf->last - cl->buf->pos);
      len -= 8;
      *pinfo++ = (uint64_t)(uintptr_t) cl->buf->pos;
      len -= 8;
    }

    n++;
    cl = cl->next;
  }

  *pnum = n;
  return 0;
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

static jlong JNICALL jni_ngx_http_clojure_mem_copy_header_buf(JNIEnv *env, jclass cls, jlong req, jobject buf, jlong offset, jlong len) {
	ngx_buf_t *header_buf = ((ngx_http_request_t *)(uintptr_t)req)->header_in;
	char *dst = ngx_http_clojure_abs_off_addr(buf, offset);
	jlong n = header_buf->last - header_buf->start;
	char *end = dst;
	if (n > len) {
		n = len;
	}
	ngx_memcpy(dst, header_buf->start, (size_t)n);
	end += (size_t)n;
	while ( dst != end -1) {
		if (*dst == 0) {
			if (*(dst+1) == '\n') {
				*dst = '\r';
			}else {
				*dst = ':';
			}
		}
		dst++;
	}
	return n;
}

static jlong JNICALL jni_ngx_http_clojure_mem_get_headers_size(JNIEnv *env, jclass cls, jlong header, jint flag) {
	ngx_http_headers_in_t *hin;
	ngx_http_headers_out_t *hout;
	ngx_list_t *list;
	ngx_list_part_t *part;
	jlong c = 0;
	ngx_table_elt_t *h;
	ngx_str_t *hn = NULL;

	if (flag & NGX_HTTP_CLOJURE_GET_HEADER_FLAG_HEADERS_OUT) {
		hout = (ngx_http_headers_out_t *)(uintptr_t)header;
		list = &hout->headers;
		if (hout->content_type.len) {
			c++;
		}
	}else {
		hin = (ngx_http_headers_in_t *)(uintptr_t)header;
		list = &hin->headers;
	}

	part = &list->part;

	while (part != NULL) {
		for (h = (ngx_table_elt_t *)part->elts; h - (ngx_table_elt_t *)part->elts < (ngx_int_t)part->nelts; h++) {
			if (h->hash ) {
				if (!hn || hn->len != h->key.len ||  ( hn->data != h->key.data && ngx_strcasecmp(hn->data, h->key.data) )) {
					hn = &h->key;
					c++;
				}else if (flag & NGX_HTTP_CLOJURE_GET_HEADER_FLAG_MERGE_KEY) {
					h->key.data = hn->data;
				}
			}
		}
		part = part->next;
	}
	return c;
}

static jlong JNICALL jni_ngx_http_clojure_mem_get_headers_items(JNIEnv *env, jclass cls, jlong header, jlong i,  jint flag,  jobject buf,   jlong off, jlong maxoff) {
	ngx_http_headers_in_t *hin;
	ngx_http_headers_out_t *hout;
	ngx_list_t *list;
	ngx_list_part_t *part;
	ngx_table_elt_t *h;
	jlong *pvalue = (jlong *)ngx_http_clojure_abs_off_addr(buf, off);
	ngx_str_t *hn = NULL;
	jlong c = 0;
	ngx_http_request_t *r;

	if (flag & NGX_HTTP_CLOJURE_GET_HEADER_FLAG_HEADERS_OUT) {
		hout = (ngx_http_headers_out_t *)(uintptr_t)header;
		r = (ngx_http_request_t *)((uintptr_t)header - NGX_HTTP_CLOJURE_REQ_HEADERS_OUT_OFFSET);
		if (!r->pool) {
			return NGX_ERROR;
		}
		list = &hout->headers;
		if (hout->content_type.len) {
			if (i == 0) {
				*pvalue = (uintptr_t)( pvalue + 1 );
				h = (ngx_table_elt_t *)(uintptr_t)*pvalue;
				h->key.data = (u_char*)"Content-Type";
				h->key.len = sizeof("Content-Type") - 1;
				h->value.data = hout->content_type.data;
				h->value.len = hout->content_type.len;
				return 1;
			}
			i--;
		}
	}else {
		r = (ngx_http_request_t *)((uintptr_t)header - NGX_HTTP_CLOJURE_REQ_HEADERS_IN_OFFSET);
		if (!r->pool) {
			return NGX_ERROR;
		}
		hin = (ngx_http_headers_in_t *)(uintptr_t)header;
		list = &hin->headers;
	}
	part = &list->part;

	while (part != NULL) {
		for (h = (ngx_table_elt_t *)part->elts; h - (ngx_table_elt_t *)part->elts < (ngx_int_t)part->nelts; h++) {
			if (h->hash ) {
				if (!hn || hn->len != h->key.len ||  (hn->data != h->key.data && ((flag & NGX_HTTP_CLOJURE_GET_HEADER_FLAG_MERGE_KEY) ||  ngx_strcasecmp(hn->data, h->key.data) ))) {
					hn = &h->key;
					i--;
				}
				if (i == -1) {
					c++;
					*(pvalue++) =  (jlong) (uintptr_t)h;
					off += 8;
					if (off >= maxoff) {
						return c;
					}
				}
				if (i < -1) {
					return c;
				}
			}
		}
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

static void JNICALL jni_ngx_http_clojure_mem_shadow_copy_ngx_str(JNIEnv *env, jclass cls, jlong ls, jlong lt) {
	if (ls) {
		((ngx_str_t *)(uintptr_t)lt)->data = ((ngx_str_t *)(uintptr_t)ls)->data;
		((ngx_str_t *)(uintptr_t)lt)->len = ((ngx_str_t *)(uintptr_t)ls)->len;
	}else {
		((ngx_str_t *)(uintptr_t)lt)->data = 0;
		((ngx_str_t *)(uintptr_t)lt)->len = 0;
	}
}

/*
 * this function is slow for iterate all headers so it should be only used to get unknown headers
 */
static jlong JNICALL jni_ngx_http_clojure_mem_get_header(JNIEnv *env, jclass cls, jlong headers, jobject buf,  jlong nameOffset, jlong nameLen,  jlong valuesOffset, jlong bufMaxOffset) {
    ngx_list_part_t *part = &((ngx_list_t *)(uintptr_t) headers)->part;
    ngx_table_elt_t *h = part->elts;
    ngx_uint_t i = 0;
    u_char * cname = (u_char *)ngx_http_clojure_abs_off_addr(buf, nameOffset);
    jlong *pvalue = (jlong *)ngx_http_clojure_abs_off_addr(buf, valuesOffset);
    jlong c = 0;


    for (i = 0; /* void */ ; i++) {
        if (i >= part->nelts) {
            if (part->next == NULL) {
                break;
            }

            part = part->next;
            h = part->elts;
            i = 0;
        }

        if (!h[i].hash || (size_t)nameLen != h[i].key.len || ngx_strcasecmp(cname, h[i].key.data) != 0) {
        	if (c) {
        		return c;
        	}
            continue;
        }

        c ++;
        *(pvalue++) =  (jlong) (uintptr_t)&h[i];
        valuesOffset += 8;
        if (bufMaxOffset <= valuesOffset) {
        	return c;
        }
    }
    return c;
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

static jlong JNICALL jni_ngx_http_clojure_mem_get_request_body(JNIEnv *env, jclass cls, jlong req,  jobject buf, jlong off, jlong limit) {
	ngx_http_request_t *r = (ngx_http_request_t *)(uintptr_t) req;
	if (!r->request_body) {
		return 0;
	}

	if (r->request_body->temp_file) {
		if (r->request_body->temp_file->file.name.len > (size_t)limit) {
			ngx_log_error(NGX_LOG_ERR, r->connection->log, 0,
							"[jni_ngx_http_clojure_mem_get_request_body] too large of file name, len= %d, limit=%d",
							r->request_body->temp_file->file.name.len,  limit);
			return 0;
		}
		ngx_memcpy(ngx_http_clojure_abs_off_addr(buf, off),  r->request_body->temp_file->file.name.data, r->request_body->temp_file->file.name.len);
		return -(jlong)r->request_body->temp_file->file.name.len;
	}

	if (r->request_body->bufs) {
		ngx_chain_t  *cl = r->request_body->bufs;
		jlong *vp = (jlong *)ngx_http_clojure_abs_off_addr(buf, off);
		jlong len = 0;
		/*although we always set  r->request_body_in_single_buf=1, but some client (e.g. clj-http) will pre-send some body along header buffer
		 * so nginx maybe has two bufs in request body*/
		while (cl) {
			*vp = (jlong)(cl->buf->last - cl->buf->pos);
			len +=  *vp++;
			*vp++ = (jlong)(uintptr_t)cl->buf->pos;
			cl = cl->next;
		}
		*vp = 0;
		return len;
	}

	return 0;
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
        }else if (!v->set_handler) {
        	return NGX_HTTP_CLOJURE_MEM_ERR_VAR_NOT_FOUND;
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

static jlong JNICALL jni_ngx_http_clojure_mem_inc_req_count(JNIEnv *env, jclass cls, jlong req, jlong detal) {
	ngx_http_request_t *r = (ngx_http_request_t *)(uintptr_t) req;
	ngx_http_clojure_module_ctx_t *ctx;
	int n = 0;
	ngx_http_clojure_get_ctx(r, ctx);
	if (ctx && r->pool) {
		jlong old = n = r->main->count;
		n += (int)detal;
		if (detal == 1) {
			ctx->hijacked_or_async = 1;
		}
		r->main->count = n;
		ngx_log_debug2(NGX_LOG_DEBUG_HTTP, r->connection->log, 0, "jni_ngx_http_clojure_mem_inc_req_count, old : %d, new : %d", old, n);
		return old;
	}
	ngx_log_error(NGX_LOG_ALERT, ngx_http_clojure_global_cycle->log, 0, "jni_ngx_http_clojure_mem_inc_req_count invoke on a released request!");
	return -1;
}

static void JNICALL jni_ngx_http_clojure_mem_continue_current_phase(JNIEnv *env, jclass cls, jlong req, jlong rc) {
	ngx_http_request_t *r = (ngx_http_request_t *)(uintptr_t) req;
	ngx_http_clojure_module_ctx_t *ctx;
	ngx_http_clojure_get_ctx(r, ctx);
	if (!ctx) {
		ngx_log_error(NGX_LOG_ALERT, ngx_http_clojure_global_cycle->log, 0, "jni_ngx_http_clojure_mem_continue_current_phase invoke on a released request!");
		return;
	}

	ngx_http_clojure_try_unset_reload_delay_timer(ctx, "jni_ngx_http_clojure_mem_continue_current_phase");

	ngx_log_debug4(NGX_LOG_DEBUG_HTTP, r->connection->log, 0,
	                   "[jni_ngx_http_clojure_mem_continue_current_phase] uri:%s count:%d brd:%d rc:%d", r->uri.data, r->count, r->buffered, rc);
	ctx->phase = ~ctx->phase;
	ctx->phase_rc = rc;
	if (r->write_event_handler == ngx_http_request_empty_handler) {
		r->write_event_handler = ngx_http_core_run_phases;
	}
	ngx_http_core_run_phases(r);
}

static jlong JNICALL jni_ngx_http_clojure_mem_get_module_ctx_phase(JNIEnv *env, jclass cls, jlong req) {
	ngx_http_request_t *r = (ngx_http_request_t *)(uintptr_t) req;
	ngx_http_clojure_module_ctx_t *ctx;
	ngx_http_clojure_get_ctx(r, ctx);
	return ctx == NULL ? -1 : (jlong)ctx->phase;
}

static jlong JNICALL jni_ngx_http_clojure_mem_get_module_ctx_upgrade(JNIEnv *env, jclass cls, jlong req) {
	ngx_http_request_t *r = (ngx_http_request_t *)(uintptr_t) req;
	ngx_http_clojure_module_ctx_t *ctx;
	ngx_http_clojure_get_ctx(r, ctx);
	return ctx == NULL ? 0 : r->headers_out.status == NGX_HTTP_SWITCHING_PROTOCOLS;
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
	if (ngx_nonblocking(fds[0]) == -1) {
		ngx_log_error(NGX_LOG_ERR, ngx_http_clojure_global_cycle->log, errno, "ngx clojure create worker_pipe at ngx_nonblocking(fds[0]) failed");
		return -1;
	}

	if (ngx_nonblocking(fds[1]) == -1) {
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

ngx_int_t ngx_http_clojure_post_event(ngx_socket_t fd, void *e, size_t size) {
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

		if (nc_ngx_worker_pipes_fds[s][1] == 0) {
			ngx_log_error(NGX_LOG_ERR, ngx_http_clojure_global_cycle->log, 0,
					"when broadcast_event find pipe[%d] fd == 0 which is unexpected, "
					"skipping this fd now, ngx_process_slot=%d", s, ngx_process_slot);
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

#define ngx_http_clojure_mem_complex_event_buf_helper(buf, e, src, len) \
	do { \
		len = (int)(0xffffLL & e); \
		if (len > (int)sizeof(buf) - 8) { \
				len = sizeof(buf) - 8; \
		} \
		e &= 0xff00000000000000LL; \
		e |= len; \
		memcpy(buf, &e, 8); \
		memcpy(buf + 8, src, len); \
    }while(0)

ngx_int_t ngx_http_clojure_mem_wakeup_event_loop() {
	jlong e = 0;
	if (nc_jvm_worker_pipe_fds[1]) {
		return ngx_http_clojure_post_event(nc_jvm_worker_pipe_fds[1], &e, 8);
	}
	return 1;
}

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
		ngx_http_clojure_mem_complex_event_buf_helper(buf, e, ngx_http_clojure_abs_off_addr(data, off), len);
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
		ngx_http_clojure_mem_complex_event_buf_helper(buf, e, ngx_http_clojure_abs_off_addr(data, off), len);
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
						"ngx clojure: ngx_http_clojure_handle_post_event failed,rp=%" PRIu64 ", rc=%d", rp, rc);
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
		ngx_log_debug2(NGX_LOG_DEBUG_HTTP, ngx_http_clojure_global_cycle->log, 0, "in master, ngx_last_process:%d, s:%d", ngx_last_process, s);
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

int ngx_http_clojure_pipe_exit_by_master() {
#if !(NGX_WIN32)
	int s = 0;
	int i = 0;
	for (i = 0; i < nc_ngx_workers; i++) {
		for (; s < ngx_last_process; s++) {
			if (ngx_processes[s].pid == -1) {
				break;
			}
		}
		if (nc_ngx_worker_pipes_fds[s][0]) {
			ngx_http_clojure_pipe_close(nc_ngx_worker_pipes_fds[s][0]);
			ngx_http_clojure_pipe_close(nc_ngx_worker_pipes_fds[s][1]);
			nc_ngx_worker_pipes_fds[s][0] = 0;
			nc_ngx_worker_pipes_fds[s][1] = 0;
		}
		s++;
	}
#else
	if (nc_jvm_worker_pipe_fds[0]) {
		ngx_http_clojure_pipe_close(nc_jvm_worker_pipe_fds[0]);
		ngx_http_clojure_pipe_close(nc_jvm_worker_pipe_fds[1]);
		nc_jvm_worker_pipe_fds[0] = nc_jvm_worker_pipe_fds[1] = 0;
	}
#endif
	return NGX_OK;
}

static int ngx_http_clojure_pipe_init_by_worker(ngx_log_t *log) {
#if !(NGX_WIN32)
	nc_jvm_worker_pipe_fds[0] = nc_ngx_worker_pipes_fds[ngx_process_slot][0];
	nc_jvm_worker_pipe_fds[1] = nc_ngx_worker_pipes_fds[ngx_process_slot][1];
	ngx_log_debug3(NGX_LOG_DEBUG_HTTP, log , 0, "ngx_process_slot:%d, fd1:%d, fd2:%d", ngx_process_slot, nc_jvm_worker_pipe_fds[0], nc_jvm_worker_pipe_fds[1]);
#endif
	return ngx_http_clojure_jvm_worker_pipe_init(log);
}

int ngx_http_clojure_check_memory_util() {
	return ngx_http_clojure_init_memory_util_flag;
}

int ngx_http_clojure_init_memory_util(ngx_core_conf_t  *ccf, ngx_http_core_srv_conf_t *cscf, ngx_http_clojure_main_conf_t  *mcf, ngx_log_t *log) {
	jlong MEM_INDEX[NGX_HTTP_CLOJURE_MEM_IDX_END];
	size_t buf_size;
	JNIEnv *env;
	JNINativeMethod nms[] = {
			{"ngx_palloc", "(JJ)J", jni_ngx_palloc},
			{"ngx_pcalloc", "(JJ)J", jni_ngx_pcalloc},
			{"ngx_array_create", "(JJJ)J",jni_ngx_array_create},
			{"ngx_array_init", "(JJJJ)J", jni_ngx_array_init},
			{"ngx_array_destory", "(J)V", jni_ngx_array_destory},
			{"ngx_array_push_n", "(JJ)J", jni_ngx_array_push_n},
			{"ngx_list_create", "(JJJ)J", jni_ngx_list_create},
			{"ngx_list_init", "(JJJJ)J", jni_ngx_list_init},
			{"ngx_list_push", "(J)J", jni_ngx_list_push},
			{"ngx_create_temp_buf", "(JJ)J", jni_ngx_create_temp_buf},
			{"ngx_create_temp_buf_by_jstring", "(JLjava/lang/String;I)J" , jni_ngx_create_temp_buf_by_jstring},
			{"ngx_create_temp_buf_by_obj", "(JLjava/lang/Object;JJI)J", jni_ngx_create_temp_buf_by_obj}, //JNIEnv *env, jclass cls, jlong req, jobject obj, jlong offset, jlong len,  jint last_buf
			{"ngx_create_file_buf", "(JJJI)J", jni_ngx_create_file_buf},
			{"ngx_http_set_content_type", "(J)J", jni_ngx_http_set_content_type},
			{"ngx_http_send_header", "(J)J", jni_ngx_http_send_header},
			{"ngx_http_clear_header_and_reset_ctx_phase", "(JJZ)V", jni_ngx_http_clear_header_and_reset_ctx_phase},
			{"ngx_http_ignore_next_response", "(J)V", jni_ngx_http_ignore_next_response},
			{"ngx_http_output_filter", "(JJ)J", jni_ngx_http_output_filter},
			{"ngx_http_finalize_request", "(JJ)V", jni_ngx_http_finalize_request},
			{"ngx_http_filter_finalize_request", "(JJ)V", jni_ngx_http_filter_finalize_request},
			{"ngx_http_filter_continue_next", "(JJJ)J",  jni_ngx_http_filter_continue_next},
			{"ngx_http_discard_request_body", "(J)J", jni_ngx_http_discard_request_body},
			{"ngx_http_clojure_mem_init_ngx_buf", "(JLjava/lang/Object;JJI)J", jni_ngx_http_clojure_mem_init_ngx_buf}, //jlong buf, jlong obj, jlong offset, jlong len, jint last_buf
			{"ngx_http_clojure_mem_build_temp_chain", "(JJLjava/lang/Object;JJ)J", jni_ngx_http_clojure_mem_build_temp_chain},
			{"ngx_http_clojure_mem_build_file_chain", "(JJLjava/lang/Object;JJZ)J", jni_ngx_http_clojure_mem_build_file_chain} ,
			{"ngx_http_clojure_mem_get_chain_info", "(JLjava/lang/Object;JJ)J", jni_ngx_http_clojure_mem_get_chain_info},
			{"ngx_http_clojure_mem_get_obj_addr", "(Ljava/lang/Object;)J", jni_ngx_http_clojure_mem_get_obj_addr},
			{"ngx_http_clojure_mem_get_list_size", "(J)J", jni_ngx_http_clojure_mem_get_list_size},
			{"ngx_http_clojure_mem_get_list_item", "(JJ)J", jni_ngx_http_clojure_mem_get_list_item},
			{"ngx_http_clojure_mem_get_headers_size", "(JI)J", jni_ngx_http_clojure_mem_get_headers_size},
			{"ngx_http_clojure_mem_get_headers_items", "(JJILjava/lang/Object;JJ)J", jni_ngx_http_clojure_mem_get_headers_items},
			{"ngx_http_clojure_mem_copy_to_obj", "(JLjava/lang/Object;JJ)V", jni_ngx_http_clojure_mem_copy_to_obj},
			{"ngx_http_clojure_mem_copy_to_addr", "(Ljava/lang/Object;JJJ)V", jni_ngx_http_clojure_mem_copy_to_addr},
			{"ngx_http_clojure_mem_shadow_copy_ngx_str", "(JJ)V",  jni_ngx_http_clojure_mem_shadow_copy_ngx_str},
			{"ngx_http_clojure_mem_copy_header_buf","(JLjava/lang/Object;JJ)J", jni_ngx_http_clojure_mem_copy_header_buf},
			{"ngx_http_clojure_mem_get_header", "(JLjava/lang/Object;JJJJ)J", jni_ngx_http_clojure_mem_get_header},
			{"ngx_http_clojure_mem_get_request_body", "(JLjava/lang/Object;JJ)J", jni_ngx_http_clojure_mem_get_request_body},
			{"ngx_http_clojure_mem_get_variable", "(JJJ)J", jni_ngx_http_clojure_mem_get_variable},
			{"ngx_http_clojure_mem_set_variable", "(JJJJ)J", jni_ngx_http_clojure_mem_set_variable},
			{"ngx_http_clojure_mem_inc_req_count", "(JJ)J", jni_ngx_http_clojure_mem_inc_req_count},
			{"ngx_http_clojure_mem_continue_current_phase", "(JJ)V", jni_ngx_http_clojure_mem_continue_current_phase},
			{"ngx_http_clojure_mem_get_module_ctx_phase", "(J)J", jni_ngx_http_clojure_mem_get_module_ctx_phase},
			{"ngx_http_clojure_mem_get_module_ctx_upgrade", "(J)J", jni_ngx_http_clojure_mem_get_module_ctx_upgrade},
			{"ngx_http_clojure_mem_post_event", "(JLjava/lang/Object;J)J", jni_ngx_http_clojure_mem_post_event},
			{"ngx_http_clojure_mem_broadcast_event", "(JLjava/lang/Object;JJ)J", jni_ngx_http_clojure_mem_broadcast_event},
			{"ngx_http_clojure_mem_read_raw_pipe", "(JLjava/lang/Object;JJ)J", jni_ngx_http_clojure_mem_read_raw_pipe},
			{"ngx_http_hijack_send", "(JLjava/lang/Object;JJI)J", jni_ngx_http_hijack_send},
			{"ngx_http_hijack_send_header", "(JI)J", jni_ngx_http_hijack_send_header},
			{"ngx_http_hijack_send_header", "(JLjava/lang/Object;JJI)J", jni_ngx_http_hijack_send_header_by_buf},
			{"ngx_http_hijack_send_chain", "(JJI)J", jni_ngx_http_hijack_send_chain},
			{"ngx_http_hijack_set_async_timeout", "(JJ)V", jni_ngx_http_hijack_set_async_timeout},
			{"ngx_http_clojure_add_listener", "(JLnginx/clojure/ChannelListener;Ljava/lang/Object;I)J", jni_ngx_http_clojure_add_listener},
			{"ngx_http_clojure_websocket_upgrade", "(JI)J", jni_ngx_http_clojure_websocket_upgrade},
			{"ngx_http_hijack_turn_on_event_handler", "(JI)V", jni_ngx_http_hijack_turn_on_event_handler},
			{"ngx_http_hijack_read", "(JLjava/lang/Object;JJ)J", jni_ngx_http_hijack_read},
			{"ngx_http_hijack_write", "(JLjava/lang/Object;JJ)J", jni_ngx_http_hijack_write},
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

	/*sizeof(jlong) is zero on win32 vc2010, so we have to use const 8*/
	memset(MEM_INDEX, -1, NGX_HTTP_CLOJURE_MEM_IDX_END * 8);
	MEM_INDEX[NGX_HTTP_CLOJURE_UINT_SIZE_IDX] = NGX_HTTP_CLOJURE_UINT_SIZE;
	MEM_INDEX[NGX_HTTP_CLOJURE_PTR_SIZE_IDX] = NGX_HTTP_CLOJURE_PTR_SIZE;
	MEM_INDEX[NGX_HTTP_CLOJURE_STRT_SIZE_IDX] = 	NGX_HTTP_CLOJURE_STRT_SIZE;
	MEM_INDEX[NGX_HTTP_CLOJURE_STR_LEN_IDX] =	NGX_HTTP_CLOJURE_STR_LEN_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_STR_DATA_IDX] = NGX_HTTP_CLOJURE_STR_DATA_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_SIZET_SIZE_IDX] = NGX_HTTP_CLOJURE_SIZET_SIZE;
	MEM_INDEX[NGX_HTTP_CLOJURE_OFFT_SIZE_IDX] = NGX_HTTP_CLOJURE_OFFT_SIZE;

	buf_size = cscf->client_header_buffer_size;

	if (buf_size == 0 || buf_size == NGX_CONF_UNSET_SIZE || buf_size < cscf->large_client_header_buffers.size) {
		buf_size = cscf->large_client_header_buffers.size;
	}

	if (buf_size == 0 || buf_size == NGX_CONF_UNSET_SIZE
			|| (buf_size < 8192
					&& (cscf->large_client_header_buffers.size == 0
							|| cscf->large_client_header_buffers.size == NGX_CONF_UNSET_SIZE))) {
		buf_size = 8192;
	}

	MEM_INDEX[NGX_HTTP_CLOJURE_BUFFER_SIZE_IDX] = buf_size;
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
	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERS_NAMES_ADDR_IDX] = NGX_HTTP_CLOJURE_HEADERS_NAMES_ADDR;

	MEM_INDEX[NGX_HTTP_CLOJURE_ARRAYT_SIZE_IDX] = NGX_HTTP_CLOJURE_ARRAYT_SIZE;
	MEM_INDEX[NGX_HTTP_CLOJURE_ARRAY_ELTS_IDX] = NGX_HTTP_CLOJURE_ARRAY_ELTS_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_ARRAY_NELTS_IDX] = NGX_HTTP_CLOJURE_ARRAY_NELTS_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_ARRAY_SIZE_IDX] = NGX_HTTP_CLOJURE_ARRAY_SIZE_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_ARRAY_NALLOC_IDX] = NGX_HTTP_CLOJURE_ARRAY_NALLOC_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_ARRAY_POOL_IDX] = NGX_HTTP_CLOJURE_ARRAY_POOL_OFFSET;

	MEM_INDEX[NGX_HTTP_CLOJURE_KEYVALT_SIZE_IDX] = NGX_HTTP_CLOJURE_KEYVALT_SIZE;
	MEM_INDEX[NGX_HTTP_CLOJURE_KEYVALT_KEY_IDX] = NGX_HTTP_CLOJURE_KEYVALT_KEY_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_KEYVALT_VALUE_IDX] = NGX_HTTP_CLOJURE_KEYVALT_VALUE_OFFSET;

	MEM_INDEX[NGX_HTTP_CLOJURE_REQT_SIZE_IDX] = NGX_HTTP_CLOJURE_REQT_SIZE;
	MEM_INDEX[NGX_HTTP_CLOJURE_REQ_METHOD_IDX] = NGX_HTTP_CLOJURE_REQ_METHOD_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_REQ_URI_IDX] = NGX_HTTP_CLOJURE_REQ_URI_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_REQ_ARGS_IDX] = NGX_HTTP_CLOJURE_REQ_ARGS_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_REQ_HEADERS_IN_IDX] = NGX_HTTP_CLOJURE_REQ_HEADERS_IN_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_REQ_POOL_IDX] = NGX_HTTP_CLOJURE_REQ_POOL_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_REQ_HEADERS_OUT_IDX] = NGX_HTTP_CLOJURE_REQ_HEADERS_OUT_OFFSET;

	MEM_INDEX[NGX_HTTP_CLOJURE_MIME_TYPES_ADDR_IDX] = NGX_HTTP_CLOJURE_MIME_TYPES_ADDR;

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

	#if (NGX_HTTP_X_FORWARDED_FOR)
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

	MEM_INDEX[NGX_WORKER_PROCESSORS_NUM_ID] = ccf->master ? ccf->worker_processes : 1;

	MEM_INDEX[NGINX_CLOJURE_RT_WORKERS_ID] = mcf->jvm_workers;
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

	nc_rt_register_code_mid = (*env)->GetStaticMethodID(env, nc_rt_class, "registerCode", "(IJJJJ)I");
	exception_handle(nc_rt_register_code_mid == NULL, env, return NGX_HTTP_CLOJURE_JVM_ERR);

	nc_rt_eval_mid = (*env)->GetStaticMethodID(env, nc_rt_class, "eval", "(IJJ)I");
	exception_handle(nc_rt_eval_mid == NULL, env, return NGX_HTTP_CLOJURE_JVM_ERR);

	nc_rt_init_mid = (*env)->GetStaticMethodID(env, nc_rt_class,"initMemIndex", "(J)V");
	exception_handle(nc_rt_init_mid == NULL, env, return NGX_HTTP_CLOJURE_JVM_ERR);

	nc_rt_destory_mid = (*env)->GetStaticMethodID(env, nc_rt_class,"destoryMemIndex", "()V");
	exception_handle(nc_rt_destory_mid == NULL, env, return NGX_HTTP_CLOJURE_JVM_ERR);

	nc_rt_handle_post_event_mid = (*env)->GetStaticMethodID(env, nc_rt_class,"handlePostEvent", "(JJ)I");
	exception_handle(nc_rt_handle_post_event_mid == NULL, env, return NGX_HTTP_CLOJURE_JVM_ERR);

	{
		nc_rt_handle_channel_event_mid = (*env)->GetStaticMethodID(env, nc_rt_class, "handleChannelEvent", "(IJLjava/lang/Object;Lnginx/clojure/ChannelListener;)V");
		exception_handle(nc_rt_handle_channel_event_mid == NULL, env, return NGX_HTTP_CLOJURE_JVM_ERR);
	}

	(*env)->CallStaticVoidMethod(env, nc_rt_class, nc_rt_init_mid, (jlong)(uintptr_t)MEM_INDEX);

	exception_handle(1, env, return NGX_HTTP_CLOJURE_JVM_ERR);
	return ngx_http_clojure_init_memory_util_flag = NGX_HTTP_CLOJURE_JVM_OK;
}

int ngx_http_clojure_destroy_memory_util(ngx_log_t *log) {
	JNIEnv *env;
	ngx_http_clojure_get_env(&jvm_env);

	if (jvm_env == NULL) {
		return NGX_HTTP_CLOJURE_JVM_ERR_INIT_SOCKETAPI;
	}

	ngx_http_clojure_init_memory_util_flag = NGX_HTTP_CLOJURE_JVM_ERR;

	env = jvm_env;
	(*env)->CallStaticVoidMethod(env, nc_rt_class, nc_rt_destory_mid);
	exception_handle(1, env, return NGX_HTTP_CLOJURE_JVM_ERR);

	return 0;
}



int ngx_http_clojure_register_script(ngx_int_t phase, ngx_str_t *handler_type,
		ngx_str_t *handler, ngx_str_t *code, ngx_array_t *pros, ngx_int_t *pcid) {
	JNIEnv *env = jvm_env;
	*pcid = (int)(*env)->CallStaticIntMethod(env, nc_rt_class, nc_rt_register_code_mid, (jint)phase,
			(jlong)(uintptr_t)handler_type, (jlong)(uintptr_t)handler, (jlong)(uintptr_t)code, (jlong)(uintptr_t)pros);
	if ((*env)->ExceptionOccurred(env)) {
		*pcid = -1;
		(*env)->ExceptionDescribe(env);
		(*env)->ExceptionClear(env);
		return NGX_HTTP_CLOJURE_JVM_ERR;
	}
	return NGX_HTTP_CLOJURE_JVM_OK;
}

int ngx_http_clojure_eval(int cid, ngx_http_request_t *r, ngx_chain_t *c) {
	JNIEnv *env = jvm_env;
	int rc;
/*	log_debug1(ngx_http_clojure_global_cycle->log, "ngx clojure eval request: %ul", (uintptr_t)r);*/
	log_debug2(ngx_http_clojure_global_cycle->log, "ngx clojure eval request to jlong: %" PRIu64 ", size: %d", (jlong)(uintptr_t)r, 8);
	rc = (*env)->CallStaticIntMethod(env, nc_rt_class,  nc_rt_eval_mid, (jint)cid, (jlong)(uintptr_t)r, (jlong)(uintptr_t)c);
	log_debug2(ngx_http_clojure_global_cycle->log, "ngx clojure eval request to jlong: %" PRIu64 ", rc: %d", (jlong)(uintptr_t)r, rc);
	exception_handle(1, env, return 500);
	return rc;
}

void ngx_http_clojure_try_set_reload_delay_timer(ngx_http_clojure_module_ctx_t *ctx, char  *func_name) {
  if (!ctx->set_reload_delay) {
    ctx->set_reload_delay = 1;
    if (( ++((ngx_connection_t*)ngx_http_clojure_reload_delay_event.data)->requests == 1) ) {
      ngx_log_debug3(NGX_LOG_DEBUG_EVENT, ngx_http_clojure_reload_delay_event.log,  0,  "%s nc event timer add: %d: %M", func_name, ngx_event_ident(ngx_http_clojure_reload_delay_event.data), ngx_http_clojure_reload_delay_event.timer.key);
      ngx_add_timer(&ngx_http_clojure_reload_delay_event, NGX_HTTP_CLOJURE_RELOAD_DELAY_MAX_TIME);
    }
  }
}

void ngx_http_clojure_try_unset_reload_delay_timer(ngx_http_clojure_module_ctx_t *ctx, char *func_name) {
  if (ctx->set_reload_delay) {
    ctx->set_reload_delay = 0;
    if (( --((ngx_connection_t*)ngx_http_clojure_reload_delay_event.data)->requests == 0) ) {
          ngx_log_debug3(NGX_LOG_DEBUG_EVENT, ngx_http_clojure_reload_delay_event.log,  0,  "%s nc event timer del: %d: %M", func_name, ngx_event_ident(ngx_http_clojure_reload_delay_event.data), ngx_http_clojure_reload_delay_event.timer.key);
          if (ngx_http_clojure_reload_delay_event.timer_set) { /*need skip timer who was deleted and expired*/
            ngx_del_timer(&ngx_http_clojure_reload_delay_event);
          }
        }
  }
}
