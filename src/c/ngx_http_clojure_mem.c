/*
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 */
#include <ngx_config.h>
#include "ngx_http_clojure_mem.h"
#include "ngx_http_clojure_jvm.h"

//static ngx_str_t NGX_HTTP_CLOJURE_FULL_VER_STR = ngx_string(NGINX_VER " & " NGINX_CLOJURE_VER);

static JavaVM *jvm = NULL;
static jclass nc_rt_class;
static jmethodID nc_rt_eval_mid;
static jmethodID nc_rt_register_code_mid;

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

static jlong JNICALL jni_ngx_palloc (JNIEnv *env, jclass cls, jlong pool, jlong size) {
	return (uintptr_t)ngx_palloc((ngx_pool_t *)pool, (size_t)size);
}

static jlong JNICALL jni_ngx_pcalloc (JNIEnv *env, jclass cls, jlong pool, jlong size) {
	return (uintptr_t)ngx_pcalloc((ngx_pool_t *)pool, (size_t)size);
}

static jlong JNICALL jni_ngx_array_create(JNIEnv *env, jclass cls, jlong pool, jlong n, jlong size) {
	return (uintptr_t)ngx_array_create((ngx_pool_t *)pool, (ngx_uint_t)n, (size_t)size);
}

static jlong JNICALL jni_ngx_array_init(JNIEnv *env, jclass cls, jlong array, jlong pool, jlong n, jlong size) {
	return (jlong)ngx_array_init((ngx_array_t *)array, (ngx_pool_t *)pool, (ngx_uint_t)n, (size_t)size);
}

static jlong JNICALL jni_ngx_array_push_n(JNIEnv *env, jclass cls, jlong array, jlong n) {
	return (uintptr_t)ngx_array_push_n((ngx_array_t *)array,  (ngx_uint_t)n);
}


static jlong JNICALL jni_ngx_list_create(JNIEnv *env, jclass cls, jlong pool, jlong n, jlong size) {
	return (uintptr_t)ngx_list_create((ngx_pool_t *)pool, (ngx_uint_t)n, (size_t)size);
}

static jlong JNICALL jni_ngx_list_init(JNIEnv *env, jclass cls, jlong list, jlong pool, jlong n, jlong size) {
	return (jlong)ngx_list_init((ngx_list_t *)list, (ngx_pool_t *)pool, (ngx_uint_t)n, (size_t)size);
}

static jlong JNICALL jni_ngx_list_push(JNIEnv *env, jclass cls, jlong list) {
	return (uintptr_t)ngx_list_push((ngx_list_t *)list);
}


static jlong JNICALL jni_ngx_create_temp_buf (JNIEnv *env, jclass cls, jlong pool, jlong size) {
	return (uintptr_t)ngx_create_temp_buf((ngx_pool_t *)pool, (size_t)size);
}


static jlong JNICALL jni_ngx_create_file_buf (JNIEnv *env, jclass cls, jlong r, jlong file, jlong name_len) {
	ngx_http_request_t *req = (ngx_http_request_t *) r;
	ngx_buf_t *b = ngx_pcalloc(req->pool, sizeof(ngx_buf_t));
	ngx_pool_cleanup_t *cln;
	ngx_pool_cleanup_file_t *clnf;

//	b->last_buf = 1;
	b->in_file = 1;
	b->file = ngx_pcalloc(req->pool, sizeof(ngx_file_t));
	b->file->fd = ngx_open_file((u_char *)file, NGX_FILE_RDONLY | NGX_FILE_NONBLOCK, NGX_FILE_OPEN, 0);

	if (b->file->fd <= 0) {
		return 0;
	}

	b->file->log = req->connection->log;
	b->file->name.data = (u_char *)file;
	b->file->name.len = name_len;

	if (ngx_file_info((u_char *)file, &b->file->info) == NGX_FILE_ERROR) {
		return 0;
	}

	req->headers_out.content_length_n = ngx_file_size(&b->file->info);//b->file->info.st_size;
	b->file_pos = 0;
	b->file_last = req->headers_out.content_length_n;
	
	//be friendly to gzip module
	//TODO:use core module configuration
	//now we use send file by default
	b->file->directio = 0;
    b->in_file = b->file_last ? 1: 0;
    b->last_buf = (req == req->main) ? 1: 0;
    b->last_in_chain = 1;
    req->headers_out.last_modified_time = ngx_file_mtime(&b->file->info);
    req->allow_ranges = 1;

	cln = ngx_pool_cleanup_add(req->pool, sizeof(ngx_pool_cleanup_file_t));
	if (cln == NULL) {
		return 0;
	}
	cln->handler = ngx_pool_cleanup_file;
	clnf = cln->data;
	clnf->fd = b->file->fd;
	clnf->name = b->file->name.data;
	clnf->log = req->pool->log;

	return (uintptr_t)b;
}

static jlong JNICALL jni_ngx_http_set_content_type(JNIEnv *env, jclass cls, jlong r) {
	return ngx_http_set_content_type((ngx_http_request_t *)r);
}

static jlong JNICALL jni_ngx_http_send_header (JNIEnv *env, jclass cls, jlong r) {
	return ngx_http_send_header((ngx_http_request_t *)r);
}

static jlong JNICALL jni_ngx_http_output_filter (JNIEnv *env, jclass cls, jlong r, jlong chain) {
	return ngx_http_output_filter((ngx_http_request_t *)r, (ngx_chain_t *)chain);
}

static jlong JNICALL jni_ngx_http_clojure_mem_init_ngx_buf(JNIEnv *env, jclass cls, jlong buf, jobject obj, jlong offset, jlong len, jint last_buf) {
	ngx_buf_t * b = (ngx_buf_t *)buf;
	ngx_memcpy(b->pos, (char *)(*(uintptr_t*)obj) + offset, len);
	b->last = b->pos + len;
	b->last_buf = last_buf;
	return (uintptr_t)b;
}

static jlong JNICALL jni_ngx_http_clojure_mem_get_obj_addr(JNIEnv *env, jclass cls, jobject obj){
	return (*(uintptr_t*)obj);
}

static jlong JNICALL jni_ngx_http_clojure_mem_get_list_size(JNIEnv *env, jclass cls, jlong l) {
	ngx_list_t *list = (ngx_list_t *)l;
	ngx_list_part_t *part = &list->part;
	jlong c = 0;

	while (part != NULL) {
		c += part->nelts;
		part = part->next;
	}
	return c;
}

static jlong JNICALL jni_ngx_http_clojure_mem_get_list_item(JNIEnv *env, jclass cls, jlong l, jlong i) {
	ngx_list_t *list = (ngx_list_t *)l;
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
	char *dst = (char *)(*(uintptr_t*)obj);
	memcpy(dst+offset, (void *)src, len);
}

static void JNICALL jni_ngx_http_clojure_mem_copy_to_addr(JNIEnv *env, jclass cls, jobject src, jlong offset, jlong dest, jlong len) {
	void *srcptr = (char *)(*(uintptr_t*)src) + offset;
	memcpy((void*)dest, srcptr, len);
}

/*
 * this function is slow for iterate all headers so it should be only used to get unknown headers
 */
static jlong JNICALL jni_ngx_http_clojure_mem_get_header(JNIEnv *env, jclass cls, jlong headers_in, jlong name, jlong len) {
    ngx_list_part_t *part = &((ngx_http_headers_in_t *) headers_in)->headers.part;
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

        if ((size_t)len != h[i].key.len || ngx_strcasecmp((u_char *)name, h[i].key.data) != 0) {
            continue;
        }
        return (uintptr_t)&h[i];
    }
    return 0;
}

static jlong JNICALL jni_ngx_http_clojure_mem_get_variable(JNIEnv *env, jclass cls,  jlong r, jlong nname, jlong varlenPtr) {
	ngx_http_request_t *req = (ngx_http_request_t *) r;
	ngx_str_t *name = (ngx_str_t *)nname;
	ngx_http_variable_value_t *vp = ngx_http_get_variable(req, name, ngx_hash_key(name->data, name->len));
	if (vp->not_found) {
		return 0;
	}
	*((u_int *)varlenPtr) = vp->len;
	return (uintptr_t)vp;
}

//we now use request_body_file variable
//static jlong JNICALL jni_ngx_http_clojure_mem_get_body_tmp_file(JNIEnv *env, jclass cls, jlong r) {
//	return &((ngx_http_request_t *) r)->request_body->temp_file->file.name;
//}

static int ngx_http_clojure_init_memory_util_flag = NGX_HTTP_CLOJURE_JVM_ERR;

int ngx_http_clojure_check_memory_util() {
	return ngx_http_clojure_init_memory_util_flag;
}

int ngx_http_clojure_init_memory_util() {
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
			{"ngx_create_file_buf", "(JJJ)J", jni_ngx_create_file_buf},
			{"ngx_http_set_content_type", "(J)J", jni_ngx_http_set_content_type},
			{"ngx_http_send_header", "(J)J", jni_ngx_http_send_header},
			{"ngx_http_output_filter", "(JJ)J", jni_ngx_http_output_filter},
			{"ngx_http_clojure_mem_init_ngx_buf", "(JLjava/lang/Object;JJI)J", jni_ngx_http_clojure_mem_init_ngx_buf}, //jlong buf, jlong obj, jlong offset, jlong len, jint last_buf
			{"ngx_http_clojure_mem_get_obj_addr", "(Ljava/lang/Object;)J", jni_ngx_http_clojure_mem_get_obj_addr},
			{"ngx_http_clojure_mem_get_list_size", "(J)J", jni_ngx_http_clojure_mem_get_list_size},
			{"ngx_http_clojure_mem_get_list_item", "(JJ)J", jni_ngx_http_clojure_mem_get_list_item},
			{"ngx_http_clojure_mem_copy_to_obj", "(JLjava/lang/Object;JJ)V", jni_ngx_http_clojure_mem_copy_to_obj},
			{"ngx_http_clojure_mem_copy_to_addr", "(Ljava/lang/Object;JJJ)V", jni_ngx_http_clojure_mem_copy_to_addr},
			{"ngx_http_clojure_mem_get_header", "(JJJ)J", jni_ngx_http_clojure_mem_get_header},
			{"ngx_http_clojure_mem_get_variable", "(JJJ)J", jni_ngx_http_clojure_mem_get_variable},
//			{"ngx_http_clojure_mem_get_body_tmp_file", "(J)J", jni_ngx_http_clojure_mem_get_body_tmp_file}
	};
	jmethodID nc_rt_init_mid;

	ngx_http_clojure_get_jvm(&jvm);

	if (ngx_http_clojure_init_memory_util_flag == NGX_HTTP_CLOJURE_JVM_OK) {
		return NGX_HTTP_CLOJURE_JVM_OK;
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

	MEM_INDEX[NGINX_VER_ID] = nginx_version;
	MEM_INDEX[NGINX_CLOJURE_VER_ID] = nginx_clojure_ver;

	(*jvm)->AttachCurrentThread(jvm, (void**)&env, NULL);
	nc_rt_class = (*env)->FindClass(env, "nginx/clojure/NginxClojureRT");
	exception_handle(nc_rt_class == NULL, env, return NGX_HTTP_CLOJURE_JVM_ERR);



	(*env)->RegisterNatives(env, nc_rt_class, nms, sizeof(nms) / sizeof(JNINativeMethod));
	exception_handle(0 == 0, env, return NGX_HTTP_CLOJURE_JVM_ERR);
	nc_rt_register_code_mid = (*env)->GetStaticMethodID(env, nc_rt_class, "registerCode", "(JJ)I");
	nc_rt_eval_mid = (*env)->GetStaticMethodID(env, nc_rt_class, "eval", "(IJ)I");
	nc_rt_init_mid = (*env)->GetStaticMethodID(env, nc_rt_class,"initMemIndex", "(J)V");
	exception_handle(nc_rt_init_mid == NULL, env, return NGX_HTTP_CLOJURE_JVM_OK);





	(*env)->CallStaticVoidMethod(env, nc_rt_class, nc_rt_init_mid, MEM_INDEX);
	return ngx_http_clojure_init_memory_util_flag = NGX_HTTP_CLOJURE_JVM_OK;
}

int ngx_http_clojure_register_script(u_char **script, size_t len, ngx_int_t *cid) {
	JNIEnv *env;
	jint rc = (*jvm)->AttachCurrentThread(jvm, (void**)&env, NULL);
	if (rc < 0){
		return NGX_HTTP_CLOJURE_JVM_ERR;
	}
	*cid = (int)(*env)->CallStaticIntMethod(env, nc_rt_class, nc_rt_register_code_mid, (jlong)script, (jlong)len);
	if ((*env)->ExceptionOccurred(env)) {
		*cid = -1;
		(*env)->ExceptionDescribe(env);
		(*env)->ExceptionClear(env);
		return NGX_HTTP_CLOJURE_JVM_ERR;
	}
	return NGX_HTTP_CLOJURE_JVM_OK;
}

int ngx_http_clojure_eval(int cid, void *r) {
	JNIEnv *env;
	(*jvm)->AttachCurrentThread(jvm, (void**)&env, NULL);
	return (*env)->CallStaticIntMethod(env, nc_rt_class,  nc_rt_eval_mid, (jint)cid, (uintptr_t)r);
}
