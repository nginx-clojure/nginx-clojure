/*
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 */
#include "ngx_http_clojure_mem.h"
#include "ngx_http_clojure_jvm.h"

static JavaVM *jvm = NULL;
static jclass mem_util_class;
static jmethodID mem_util_eval_mid;
static jmethodID mem_util_register_code_mid;

static jlong JNICALL jni_ngx_palloc (JNIEnv *env, jclass cls, jlong pool, jlong size) {
	return (uintptr_t)ngx_palloc((ngx_pool_t *)pool, (size_t)size);
}

static jlong JNICALL jni_ngx_pcalloc (JNIEnv *env, jclass cls, jlong pool, jlong size) {
	return (uintptr_t)ngx_pcalloc((ngx_pool_t *)pool, (size_t)size);
}

static jlong JNICALL jni_ngx_create_temp_buf (JNIEnv *env, jclass cls, jlong pool, jlong size) {
	return (uintptr_t)ngx_create_temp_buf((ngx_pool_t *)pool, (size_t)size);
}


static jlong JNICALL jni_ngx_create_file_buf (JNIEnv *env, jclass cls, jlong r, jlong file, jlong name_len) {
	ngx_http_request_t *req = (ngx_http_request_t *) r;
	ngx_buf_t *b = ngx_pcalloc(req->pool, sizeof(ngx_buf_t));
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

	req->headers_out.content_length_n = b->file->info.st_size;
	b->file_pos = 0;
	b->file_last = b->file->info.st_size;

	ngx_pool_cleanup_t *cln = ngx_pool_cleanup_add(req->pool, sizeof(ngx_pool_cleanup_file_t));
	if (cln == NULL) {
		return 0;
	}
	cln->handler = ngx_pool_cleanup_file;
	ngx_pool_cleanup_file_t *clnf = cln->data;
	clnf->fd = b->file->fd;
	clnf->name = b->file->name.data;
	clnf->log = req->pool->log;

	return (uintptr_t)b;
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

static jlong JNICALL jni_ngx_http_clojure_mem_get_obj_attr(JNIEnv *env, jclass cls, jobject obj){
	return (*(uintptr_t*)obj);
}

static jlong JNICALL jni_ngx_http_clojure_mem_get_ptr_size(JNIEnv *env, jclass cls) {
	return sizeof(void *);
}

static void JNICALL jni_ngx_http_clojure_mem_copy_to_obj(JNIEnv *env, jclass cls, jlong src, jobject obj, jlong offset, jlong len) {
	char *dst = (char *)(*(uintptr_t*)obj);
	memcpy(dst+offset, (void *)src, len);
}

static void JNICALL jni_ngx_http_clojure_mem_copy_to_addr(JNIEnv *env, jclass cls, jobject src, jlong offset, jlong dest, jlong len) {
	void *srcptr = (char *)(*(uintptr_t*)src) + offset;
	memcpy((void*)dest, srcptr, len);
}

static int ngx_http_clojure_init_memory_util_flag = NGX_HTTP_CLOJURE_JVM_ERR;

int ngx_http_clojure_check_memory_util() {
	return ngx_http_clojure_init_memory_util_flag;
}

int ngx_http_clojure_init_memory_util() {
	ngx_http_clojure_get_jvm((void **)&jvm);

	if (ngx_http_clojure_init_memory_util_flag == NGX_HTTP_CLOJURE_JVM_OK) {
		return NGX_HTTP_CLOJURE_JVM_OK;
	}
	jlong MEM_INDEX[NGX_HTTP_CLOJURE_MEM_IDX_END];

	JNIEnv *env;
	(*jvm)->AttachCurrentThread(jvm, (void**)&env, NULL);
	mem_util_class = (*env)->FindClass(env, "nginx/clojure/MemoryUtil");
	exception_handle(mem_util_class == NULL, env, return NGX_HTTP_CLOJURE_JVM_ERR);


	JNINativeMethod nms[] = {
			{"ngx_palloc", "(JJ)J", jni_ngx_palloc},
			{"ngx_pcalloc", "(JJ)J", jni_ngx_pcalloc},
			{"ngx_create_temp_buf", "(JJ)J", jni_ngx_create_temp_buf},
			{"ngx_create_file_buf", "(JJJ)J", jni_ngx_create_file_buf},
			{"ngx_http_send_header", "(J)J", jni_ngx_http_send_header},
			{"ngx_http_output_filter", "(JJ)J", jni_ngx_http_output_filter},
			{"ngx_http_clojure_mem_init_ngx_buf", "(JLjava/lang/Object;JJI)J", jni_ngx_http_clojure_mem_init_ngx_buf}, //jlong buf, jlong obj, jlong offset, jlong len, jint last_buf
			{"ngx_http_clojure_mem_get_obj_attr", "(Ljava/lang/Object;)J", jni_ngx_http_clojure_mem_get_obj_attr},
			{"ngx_http_clojure_mem_copy_to_obj", "(JLjava/lang/Object;JJ)V", jni_ngx_http_clojure_mem_copy_to_obj},
			{"ngx_http_clojure_mem_copy_to_addr", "(Ljava/lang/Object;JJJ)V", jni_ngx_http_clojure_mem_copy_to_addr}
	};
	(*env)->RegisterNatives(env, mem_util_class, nms, sizeof(nms) / sizeof(JNINativeMethod));
	exception_handle(0 == 0, env, return NGX_HTTP_CLOJURE_JVM_ERR);
	mem_util_register_code_mid = (*env)->GetStaticMethodID(env, mem_util_class, "registerCode", "(JJ)I");
	mem_util_eval_mid = (*env)->GetStaticMethodID(env, mem_util_class, "eval", "(IJ)I");
	jmethodID mem_util_init_mid = (*env)->GetStaticMethodID(env, mem_util_class,"initMemIndex", "(J)V");
	exception_handle(mem_util_init_mid == NULL, env, return NGX_HTTP_CLOJURE_JVM_OK);


	MEM_INDEX[NGX_HTTP_CLOJURE_UINT_SIZE_IDX] = NGX_HTTP_CLOJURE_UINT_SIZE;
	MEM_INDEX[NGX_HTTP_CLOJURE_PTR_SIZE_IDX] = NGX_HTTP_CLOJURE_PTR_SIZE;
	MEM_INDEX[NGX_HTTP_CLOJURE_STR_SIZE_IDX] = 	NGX_HTTP_CLOJURE_STR_SIZE;
	MEM_INDEX[NGX_HTTP_CLOJURE_STR_LEN_IDX] =	NGX_HTTP_CLOJURE_STR_LEN_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_STR_DATA_IDX] = NGX_HTTP_CLOJURE_STR_DATA_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_SIZET_SIZE_IDX] = NGX_HTTP_CLOJURE_SIZET_SIZE;
	MEM_INDEX[NGX_HTTP_CLOJURE_OFFT_SIZE_IDX] = NGX_HTTP_CLOJURE_OFFT_SIZE;

	MEM_INDEX[NGX_HTTP_CLOJURE_TELT_SIZE_IDX] = NGX_HTTP_CLOJURE_TELT_SIZE;
	MEM_INDEX[NGX_HTTP_CLOJURE_TELT_HASH_IDX] = NGX_HTTP_CLOJURE_TELT_HASH_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_TELT_KEY_IDX] = NGX_HTTP_CLOJURE_TELT_KEY_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_TELT_VALUE_IDX] = NGX_HTTP_CLOJURE_TELT_VALUE_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_TELT_LOWCASE_KEY_IDX] = NGX_HTTP_CLOJURE_TELT_LOWCASE_KEY_OFFSET;

	MEM_INDEX[NGX_HTTP_CLOJURE_REQ_SIZE_IDX] = NGX_HTTP_CLOJURE_REQ_SIZE;
	MEM_INDEX[NGX_HTTP_CLOJURE_REQ_METHOD_IDX] = NGX_HTTP_CLOJURE_REQ_METHOD_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_REQ_URI_IDX] = NGX_HTTP_CLOJURE_REQ_URI_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_REQ_ARGS_IDX] = NGX_HTTP_CLOJURE_REQ_ARGS_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_REQ_HEADERS_IN_IDX] = NGX_HTTP_CLOJURE_REQ_HEADERS_IN_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_REQ_POOL_IDX] = NGX_HTTP_CLOJURE_REQ_POOL_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_REQ_HEADERS_OUT_IDX] = NGX_HTTP_CLOJURE_REQ_HEADERS_OUT_OFFSET;

	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERS_SIZE_IDX] = 	NGX_HTTP_CLOJURE_HEADERS_SIZE;
	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERS_HOST_IDX] = NGX_HTTP_CLOJURE_HEADERS_HOST_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERS_CONTENT_LENGTH_IDX] = NGX_HTTP_CLOJURE_HEADERS_CONTENT_LENGTH_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERS_CONTENT_TYPE_IDX] = NGX_HTTP_CLOJURE_HEADERS_CONTENT_TYPE_OFFSET;

	MEM_INDEX[NGX_HTTP_CLOJURE_CHAIN_SIZE_IDX] = NGX_HTTP_CLOJURE_CHAIN_SIZE;
	MEM_INDEX[NGX_HTTP_CLOJURE_CHAIN_BUF_IDX] = NGX_HTTP_CLOJURE_CHAIN_BUF_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_CHAIN_NEXT_IDX] = NGX_HTTP_CLOJURE_CHAIN_NEXT_OFFSET;

	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSO_SIZE_IDX] = NGX_HTTP_CLOJURE_HEADERSO_SIZE;
	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSO_STATUS_IDX] = NGX_HTTP_CLOJURE_HEADERSO_STATUS_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSO_CONTENT_LENGTH_N_IDX] = NGX_HTTP_CLOJURE_HEADERSO_CONTENT_LENGTH_N_OFFSET;
	MEM_INDEX[NGX_HTTP_CLOJURE_HEADERSO_CONTENT_TYPE_IDX] = NGX_HTTP_CLOJURE_HEADERSO_CONTENT_TYPE_OFFSET;



	(*env)->CallStaticVoidMethod(env, mem_util_class, mem_util_init_mid, MEM_INDEX);
	return ngx_http_clojure_init_memory_util_flag = NGX_HTTP_CLOJURE_JVM_OK;
}

int ngx_http_clojure_register_script(char *script, size_t len, ngx_int_t *cid) {
	JNIEnv *env;
	jint rc = (*jvm)->AttachCurrentThread(jvm, (void**)&env, NULL);
	if (rc < 0){
		return NGX_HTTP_CLOJURE_JVM_ERR;
	}
	*cid = (int)(*env)->CallStaticIntMethod(env, mem_util_class, mem_util_register_code_mid, (jlong)script, (jlong)len);
	if ((*env)->ExceptionOccurred(env)) {
		*cid = -1;
		(*env)->ExceptionDescribe(env);
		(*env)->ExceptionClear(env);
		return NGX_HTTP_CLOJURE_JVM_ERR;
	}
	return NGX_HTTP_CLOJURE_JVM_OK;
}

int ngx_http_clojure_eval(jint cid, void *r) {
	JNIEnv *env;
	(*jvm)->AttachCurrentThread(jvm, (void**)&env, NULL);
	return (*env)->CallStaticIntMethod(env, mem_util_class,  mem_util_eval_mid, cid, (uintptr_t)r);
}
