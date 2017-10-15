/*
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 */

#include <ngx_config.h>
#include "ngx_http_clojure_mem.h"
#include "ngx_http_clojure_shared_map.h"
#include "ngx_http_clojure_jvm.h"
#include "ngx_http_clojure_shared_map_hashmap.h"
#include "ngx_http_clojure_shared_map_tinymap.h"

#define null_shared_map_impl {NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL}

static ngx_http_clojure_shared_map_impl_t ngx_http_clojure_shared_map_registered_impls[] = {
		{
			"hashmap",
			ngx_http_clojure_shared_map_hashmap_init,
			ngx_http_clojure_shared_map_hashmap_get_entry,
			ngx_http_clojure_shared_map_hashmap_put_entry,
			ngx_http_clojure_shared_map_hashmap_put_entry_if_absent,
			ngx_http_clojure_shared_map_hashmap_remove_entry,
			ngx_http_clojure_shared_map_hashmap_size,
			ngx_http_clojure_shared_map_hashmap_clear,
			ngx_http_clojure_shared_map_hashmap_visit
		},
		{
			"tinymap",
			ngx_http_clojure_shared_map_tinymap_init,
			ngx_http_clojure_shared_map_tinymap_get_entry,
			ngx_http_clojure_shared_map_tinymap_put_entry,
			ngx_http_clojure_shared_map_tinymap_put_entry_if_absent,
			ngx_http_clojure_shared_map_tinymap_remove_entry,
			ngx_http_clojure_shared_map_tinymap_size,
			ngx_http_clojure_shared_map_tinymap_clear,
			ngx_http_clojure_shared_map_tinymap_visit
		 },
		 null_shared_map_impl
};

static int ngx_http_clojure_init_shared_map_flag = NGX_HTTP_CLOJURE_JVM_ERR;
static ngx_array_t *ngx_http_clojure_shared_maps;
jmethodID nc_shm_native_2_jobject_mid;
jmethodID nc_shm_visit_mid;


char * ngx_http_clojure_shared_map(ngx_conf_t *cf, ngx_command_t *cmd, void *conf) {
	ngx_http_clojure_main_conf_t *mcf = conf;
	ngx_http_clojure_shared_map_ctx_t *ctx;
	ngx_int_t i;
	u_char *args;
	u_char *arg_start = NULL;
	u_char *arg_end = NULL;
	u_char *argv_start = NULL;
	u_char *argv_end = NULL;
	ngx_str_t tmpstr;
	ngx_str_t *value = cf->args->elts;

	if (!mcf->shared_maps) {
		ngx_http_clojure_shared_maps = mcf->shared_maps = ngx_array_create(cf->pool, 4, sizeof(ngx_http_clojure_shared_map_ctx_t));
		if (!mcf->shared_maps) {
			return NGX_CONF_ERROR;
		}
	}

	if ((ctx = ngx_array_push(mcf->shared_maps)) == NULL) {
		return NGX_CONF_ERROR;
	}

	if ((ctx->arguments = ngx_array_create(cf->pool, 4, sizeof(ngx_table_elt_t))) == NULL ) {
		return NGX_CONF_ERROR;
	}

	ctx->name = value[1];

	if (ctx->name.len > NGX_CLOJURE_SHARED_MAP_NAME_MAX_LEN) {
		ngx_conf_log_error(NGX_LOG_EMERG, cf, 0, "invalid shared map arguments, too long name \"%V\"", &ctx->name);
		return NGX_CONF_ERROR;
	}

	/* parse shared map arguments of implementation, e.g.
	 * hashmap?space=8M&entries=10k, redis?host=localhost&port=6379*/
	args = ngx_strstrn(value[2].data, "?", 1-1);
	if (!args || args + 3 > value[2].data + value[2].len) {
		ngx_conf_log_error(NGX_LOG_EMERG, cf, 0, "invalid shared map arguments \"%V\"", &value[2]);
		return NGX_CONF_ERROR;
	}

	/*lookup an implementation by type name*/
	for (i = 0; (ctx->impl = &ngx_http_clojure_shared_map_registered_impls[i])->name != NULL; i++) {
		if (!ngx_strncmp(value[2].data, ctx->impl->name, args - value[2].data)) {
			break;
		}
	}

	if (!ctx->impl->name) {
		tmpstr.data = value[2].data;
		tmpstr.len = args - value[2].data;
		ngx_conf_log_error(NGX_LOG_EMERG, cf, 0, "invalid shared map type, no implementation found \"%V\"", &tmpstr);
		return NGX_CONF_ERROR;
	}

	do {
		args++;
		if (argv_start) {
			if ( *args == '&' || args == value[2].data + value[2].len) {
				ngx_table_elt_t *argkv = ngx_array_push(ctx->arguments);
				argkv->key.data = arg_start;
				argkv->key.len = arg_end - arg_start;
				argv_end = args;
				argkv->value.data = argv_start;
				argkv->value.len = argv_end - argv_start;
				arg_start = argv_start = NULL;
			}
		}else {
			if (!arg_start) {
				arg_start = args;
			}
			if (*args == '=' || args == value[2].data + value[2].len) {
				arg_end = args;
				argv_start = args+1;
			}
		}
	}while(args < value[2].data + value[2].len);

	ctx->log = &cf->cycle->new_log;
	if ( ctx->impl->init(cf, ctx) != NGX_OK ) {
		ngx_conf_log_error(NGX_LOG_EMERG, cf, 0, "initialize shared map failed \"%V\"", &value[2]);
		return NGX_CONF_ERROR;
	}

	return NGX_CONF_OK;
}

ngx_http_clojure_shared_map_ctx_t* ngx_http_clojure_shared_map_get_map(u_char *name, size_t len) {
	ngx_uint_t i;
	ngx_http_clojure_shared_map_ctx_t *ctx;

	if (!ngx_http_clojure_shared_maps) {
		return NULL;
	}

	ctx = ngx_http_clojure_shared_maps->elts;
	for (i = 0; i < ngx_http_clojure_shared_maps->nelts; i++) {
		if (ctx->name.len == len && !ngx_strncmp(ctx->name.data, name, len)) {
			return ctx;
		}
		ctx++;
	}

	return NULL;
}


static jlong jni_ngx_http_clojure_shared_map_get_map(JNIEnv *env, jclass cls, jobject name, jlong offset, jlong len) {
	return (uintptr_t)ngx_http_clojure_shared_map_get_map(ngx_http_clojure_abs_off_addr(name, offset), (size_t)len);
}

static void nji_ngx_http_clojure_shared_map_val_to_jobj_handler(uint8_t type, const void *d, size_t l, void* ps) {
	void ** pp = (void **)ps;
	JNIEnv *env = pp[0];
	pp[1] = (*env)->CallStaticObjectMethod(env, pp[1], nc_shm_native_2_jobject_mid, (jint)type, (jlong)(uintptr_t)d, (jlong)l);
	exception_handle(pp[1] == NULL, env, return);
}

static void nji_ngx_http_clojure_shared_map_val_to_jpimary_handler(uint8_t type, const void *d, size_t l, void* ps) {
	uint64_t *pi = ps;
	if (type != pi[0]) {
		pi[0] = NGX_CLOJURE_SHARED_MAP_INVLAID_VALUE_TYPE;
	}else {
		pi[0] = NGX_CLOJURE_SHARED_MAP_OK;
	}
	pi[1] = (type == NGX_CLOJURE_SHARED_MAP_JINT ? *((uint32_t*)d) : *((uint64_t*)d));
}

static void nji_ngx_http_clojure_shared_map_num_val_add_handler(uint8_t type, const void *d, size_t l, void* ps) {
	int64_t *pi = ps;
	uint64_t old;
	if (type != pi[0]) {
		pi[0] = NGX_CLOJURE_SHARED_MAP_INVLAID_VALUE_TYPE;
	}else {
		pi[0] = NGX_CLOJURE_SHARED_MAP_OK;
	}
	if (type == NGX_CLOJURE_SHARED_MAP_JINT) {
		old =  *((uint32_t*)d);
		*((uint32_t*)d) += (int32_t)pi[1];
	}else {
		old = *((uint64_t*)d);
		*((uint64_t*)d) += (int64_t)pi[1];
	}
	pi[1] = old;

}

static ngx_int_t nji_ngx_http_clojure_shared_map_visit_handler(uint8_t ktype, const void *key, size_t ksize, uint8_t vtype, const void *val, size_t vsize,
    void *ps) {
  void ** pp = (void **) ps;
  JNIEnv *env = pp[0];
  ngx_int_t rc = (*env)->CallStaticIntMethod(env, pp[1], nc_shm_visit_mid, (jint)ktype, (jlong)(uintptr_t)key, (jint)ksize, (jint)vtype, (jlong)(uintptr_t)val, (jint)vsize, (jobject)pp[1]);
  exception_handle(1, env, return 1);
  return rc;
}

static jobject jni_ngx_http_clojure_shared_map_get(JNIEnv *env, jclass cls, jlong jctx,
		jint ktype, jobject key, jlong koff, jlong klen) {
	ngx_http_clojure_shared_map_ctx_t *ctx = (ngx_http_clojure_shared_map_ctx_t *)(uintptr_t)jctx;
	void *pp[2];
	ngx_int_t rc;

	pp[0] = (void *)env;
	pp[1] = cls;
	rc = ctx->impl->get(ctx, (uint8_t)ktype,
			ngx_http_clojure_abs_off_addr(key, koff), (size_t)klen,
			nji_ngx_http_clojure_shared_map_val_to_jobj_handler,
			(void *)pp);
	return !rc ? pp[1] : NULL;
}


static jobject jni_ngx_http_clojure_shared_map_put(JNIEnv *env, jclass cls, jlong jctx,
		jint ktype, jobject key, jlong koff, jlong klen, jint vtype, jobject val, jlong voff, jlong vlen) {
	void *pp[2];
	u_char err[1024]= {0};
	ngx_http_clojure_shared_map_ctx_t *ctx = (ngx_http_clojure_shared_map_ctx_t *)(uintptr_t)jctx;
	ngx_int_t rc;

	pp[0] = (void *)env;
	pp[1] = cls;
	rc = ctx->impl->put(ctx,
			(uint8_t)ktype, ngx_http_clojure_abs_off_addr(key, koff), (size_t)klen,
			(uint8_t)vtype, ngx_http_clojure_abs_off_addr(val, voff), (size_t)vlen,
			nji_ngx_http_clojure_shared_map_val_to_jobj_handler,
			pp
			);
	if (rc == NGX_CLOJURE_SHARED_MAP_OUT_OF_MEM) {
		jclass ec = (*env)->FindClass(env, "java/lang/OutOfMemoryError");
		if (ec != NULL) {
			ngx_snprintf(err, sizeof(err)-1, "shared map '%V' outofmemory (size=%ud)!", &ctx->name, ctx->impl->size(ctx));
			(*env)->ThrowNew(env, ec, (char*)err);
		}
		(*env)->DeleteLocalRef(env, ec);
	} else if (rc == NGX_CLOJURE_SHARED_MAP_INVLAID_VALUE_TYPE) {
		jclass ec = (*env)->FindClass(env, "java/lang/OutOfMemoryError");
		if (ec != NULL) {
			ngx_snprintf(err, sizeof(err) - 1, "shared map '%V' value type %d is not matched with existing type!", &ctx->name, vtype);
			(*env)->ThrowNew(env, ec, (char*)err);
		}
		(*env)->DeleteLocalRef(env, ec);
	}
	return !rc ? pp[1] : NULL;
}

static jobject jni_ngx_http_clojure_shared_map_put_if_absent(JNIEnv *env, jclass cls, jlong jctx,
		jint ktype, jobject key, jlong koff, jlong klen, jint vtype, jobject val, jlong voff, jlong vlen) {
	void *pp[2];
	ngx_int_t rc;
	u_char err[1024]= {0};
	ngx_http_clojure_shared_map_ctx_t *ctx = (ngx_http_clojure_shared_map_ctx_t *)(uintptr_t)jctx;

	pp[0] = (void *)env;
	pp[1] = cls;
	rc = ctx->impl->put_if_absent(ctx,
			(uint8_t)ktype, ngx_http_clojure_abs_off_addr(key, koff), (size_t)klen,
			(uint8_t)vtype, ngx_http_clojure_abs_off_addr(val, voff), (size_t)vlen,
			nji_ngx_http_clojure_shared_map_val_to_jobj_handler,
			pp
			);
	if (rc == NGX_CLOJURE_SHARED_MAP_OUT_OF_MEM) {
		jclass ec = (*env)->FindClass(env, "java/lang/OutOfMemoryError");
		if (ec != NULL) {
			ngx_snprintf(err, sizeof(err)-1, "shared map '%V' outofmemory (size=%ud)!", &ctx->name, ctx->impl->size(ctx));
			(*env)->ThrowNew(env, ec, (char*)err);
		}
		(*env)->DeleteLocalRef(env, ec);
	} else if (rc == NGX_CLOJURE_SHARED_MAP_INVLAID_VALUE_TYPE) {
		jclass ec = (*env)->FindClass(env, "java/lang/RuntimeException");
		if (ec != NULL) {
			ngx_snprintf(err, sizeof(err) - 1, "shared map '%V' value type %d is not matched with existing type!", &ctx->name, vtype);
			(*env)->ThrowNew(env, ec, (char*)err);
		}
		(*env)->DeleteLocalRef(env, ec);
	}
	return !rc ? pp[1] : NULL;
}

static jlong jni_ngx_http_clojure_shared_map_delete(JNIEnv *env, jclass cls, jlong jctx,
		jint ktype, jobject key, jlong koff, jlong klen) {
	ngx_http_clojure_shared_map_ctx_t *ctx = (ngx_http_clojure_shared_map_ctx_t *)(uintptr_t)jctx;
	ngx_int_t rc = ctx->impl->remove(ctx, (uint8_t)ktype,
			ngx_http_clojure_abs_off_addr(key, koff), (size_t) klen,
			NULL, NULL);
	return (jlong)rc;
}

static jobject jni_ngx_http_clojure_shared_map_remove(JNIEnv *env, jclass cls, jlong jctx,
		jint ktype, jobject key, jlong koff, jlong klen) {
	ngx_http_clojure_shared_map_ctx_t *ctx = (ngx_http_clojure_shared_map_ctx_t *)(uintptr_t)jctx;
	void *pp[2];
	ngx_int_t rc;

	pp[0] = (void *)env;
	pp[1] = cls;
	rc = ctx->impl->remove(ctx, (uint8_t)ktype,
			ngx_http_clojure_abs_off_addr(key, koff), (size_t)klen,
			nji_ngx_http_clojure_shared_map_val_to_jobj_handler,
			pp);
	return !rc ? pp[1] : NULL;
}

static jlong jni_ngx_http_clojure_shared_map_size(JNIEnv *env, jclass cls, jlong jctx) {
	return ((ngx_http_clojure_shared_map_ctx_t *) (uintptr_t) jctx)->impl->size(
			(ngx_http_clojure_shared_map_ctx_t *) (uintptr_t) jctx);
}


static jlong jni_ngx_http_clojure_shared_map_clear(JNIEnv *env, jclass cls, jlong jctx) {
	return ((ngx_http_clojure_shared_map_ctx_t *) (uintptr_t) jctx)->impl->clear(
			(ngx_http_clojure_shared_map_ctx_t *) (uintptr_t) jctx);
}

static jlong jni_ngx_http_clojure_shared_map_contains(JNIEnv *env, jclass cls, jlong jctx,
		jint ktype, jobject key, jlong koff, jlong klen) {
	ngx_http_clojure_shared_map_ctx_t *ctx = (ngx_http_clojure_shared_map_ctx_t *)(uintptr_t)jctx;
	ngx_int_t rc = ctx->impl->get(ctx, (uint8_t)ktype,
				ngx_http_clojure_abs_off_addr(key, koff), (size_t)klen,
				NULL, NULL);
	return (jlong)rc;
}

static jlong jni_ngx_http_clojure_shared_map_visit(JNIEnv *env, jclass cls, jlong jctx, jobject visitor)  {
  ngx_http_clojure_shared_map_ctx_t *ctx = (ngx_http_clojure_shared_map_ctx_t *)(uintptr_t)jctx;
  void *pp[2];// = {env, visitor};
  pp[0] = env;
  pp[1] = visitor;
  return ctx->impl->visit(ctx, nji_ngx_http_clojure_shared_map_visit_handler, pp);
}

/*********************************************************************
 * BEGIN optimized functions for those maps have int/long values.
 **********************************************************************/

static jlong jni_ngx_http_clojure_shared_map_get_number(JNIEnv *env, jclass cls, jlong jctx, jint ktype, jobject key, jlong koff,
		jlong klen, jint vtype) {
	ngx_http_clojure_shared_map_ctx_t *ctx = (ngx_http_clojure_shared_map_ctx_t *)(uintptr_t)jctx;
	jlong rt[2];
	u_char err[1024] = {0};
	ngx_int_t rc;

	rt[0] = vtype;
	rt[1] = 0;
	rc = ctx->impl->get(ctx, ktype,
			ngx_http_clojure_abs_off_addr(key, koff), (size_t)klen,
			nji_ngx_http_clojure_shared_map_val_to_jpimary_handler,
			rt);

	if (rc != NGX_CLOJURE_SHARED_MAP_OK) {
		ngx_snprintf(err, sizeof(err) - 1, "shared map '%V' key %d not found!", &ctx->name, key);
		goto THROWS_EXCEPTION;
	}else if (rt[0] != NGX_CLOJURE_SHARED_MAP_OK) {
		ngx_snprintf(err, sizeof(err) - 1, "shared map '%V' value type is not matched!", &ctx->name);
		goto THROWS_EXCEPTION;
	}
	return rt[1];

THROWS_EXCEPTION: {
	jclass ec = (*env)->FindClass(env, "java/lang/RuntimeException");
	if (ec != NULL) {
		(*env)->ThrowNew(env, ec, (char*)err);
	}
	(*env)->DeleteLocalRef(env, ec);
	return rc;
  }
}

static jlong jni_ngx_http_clojure_shared_map_put_number(JNIEnv *env, jclass cls, jlong jctx, jint ktype, jobject key, jlong koff,
		jlong klen, jint vtype, jlong val, jlong null_val) {
	ngx_http_clojure_shared_map_ctx_t *ctx = (ngx_http_clojure_shared_map_ctx_t *)(uintptr_t)jctx;
	jlong rt[2];
	u_char err[1024] = {0};
	ngx_int_t rc;

	rt[0] = vtype;
	rt[1] = 0;

	rc = ctx->impl->put(ctx,
					(uint8_t)ktype, ngx_http_clojure_abs_off_addr(key, koff), (size_t)klen,
					vtype, &val, vtype == NGX_CLOJURE_SHARED_MAP_JINT ? 4 : 8,
					nji_ngx_http_clojure_shared_map_val_to_jpimary_handler,
					rt);

	if (rc == NGX_CLOJURE_SHARED_MAP_OUT_OF_MEM) {
		ngx_snprintf(err, sizeof(err)-1, "shared map '%V' outofmemory (size=%ud)!", &ctx->name, ctx->impl->size(ctx));
		goto THROWS_EXCEPTION;
	}else if (rt[0] == NGX_CLOJURE_SHARED_MAP_NOT_FOUND) {
		return null_val;
	}
	return rt[1];

THROWS_EXCEPTION: {
	jclass ec = (*env)->FindClass(env, "java/lang/RuntimeException");
	if (ec != NULL) {
		(*env)->ThrowNew(env, ec, (char*)err);
	}
	(*env)->DeleteLocalRef(env, ec);
	return rc;
  }
}

static jlong jni_ngx_http_clojure_shared_map_put_number_if_absent(JNIEnv *env, jclass cls, jlong jctx, jint ktype, jobject key, jlong koff,
		jlong klen, jint vtype, jlong val, jlong null_val) {
	ngx_http_clojure_shared_map_ctx_t *ctx = (ngx_http_clojure_shared_map_ctx_t *)(uintptr_t)jctx;
	jlong rt[2];
	u_char err[1024] = {0};
	ngx_int_t rc;

	rt[0] = vtype;
	rt[1] = 0;
	rc = ctx->impl->put_if_absent(ctx,
					(uint8_t)ktype, ngx_http_clojure_abs_off_addr(key, koff), (size_t)klen,
					vtype, &val, vtype == NGX_CLOJURE_SHARED_MAP_JINT ? 4 : 8,
					nji_ngx_http_clojure_shared_map_val_to_jpimary_handler,
					rt);

	if (rc == NGX_CLOJURE_SHARED_MAP_OUT_OF_MEM) {
		ngx_snprintf(err, sizeof(err)-1, "shared map '%V' outofmemory (size=%ud)!", &ctx->name, ctx->impl->size(ctx));
		goto THROWS_EXCEPTION;
	}else if (rt[0] == NGX_CLOJURE_SHARED_MAP_NOT_FOUND) {
		return null_val;
	}
	return rt[1];

THROWS_EXCEPTION: {
	jclass ec = (*env)->FindClass(env, "java/lang/RuntimeException");
	if (ec != NULL) {
		(*env)->ThrowNew(env, ec, (char*)err);
	}
	(*env)->DeleteLocalRef(env, ec);
	return rc;
  }
}

static jlong jni_ngx_http_clojure_shared_map_atomic_add_number(JNIEnv *env, jclass cls, jlong jctx, jint ktype, jobject key,
		jlong koff, jlong klen, jint vtype, jlong delta) {

	ngx_http_clojure_shared_map_ctx_t *ctx = (ngx_http_clojure_shared_map_ctx_t *)(uintptr_t)jctx;
	jlong rt[2];
	u_char err[1024] = {0};
	ngx_int_t rc;

	rt[0] = vtype;
	rt[1] = delta;
	rc = ctx->impl->get(ctx, ktype,
			ngx_http_clojure_abs_off_addr(key, koff), (size_t)klen,
			nji_ngx_http_clojure_shared_map_num_val_add_handler,
			rt);

	if (rc != NGX_CLOJURE_SHARED_MAP_OK) {
		ngx_snprintf(err, sizeof(err) - 1, "shared map '%V' key %d not found!", &ctx->name, key);
		goto THROWS_EXCEPTION;
	}else if (rt[0] != NGX_CLOJURE_SHARED_MAP_OK) {
		ngx_snprintf(err, sizeof(err) - 1, "shared map '%V' value type is not matched!", &ctx->name);
		goto THROWS_EXCEPTION;
	}
	return rt[1];

THROWS_EXCEPTION: {
	jclass ec = (*env)->FindClass(env, "java/lang/RuntimeException");
	if (ec != NULL) {
		(*env)->ThrowNew(env, ec, (char*)err);
	}
	(*env)->DeleteLocalRef(env, ec);
	return rc;
  }

}

static jlong jni_ngx_http_clojure_shared_map_remove_number(JNIEnv *env, jclass cls, jlong jctx, jint ktype, jobject key,
		jlong koff, jlong klen, jint vtype, jlong null_val) {
	ngx_http_clojure_shared_map_ctx_t *ctx = (ngx_http_clojure_shared_map_ctx_t *)(uintptr_t)jctx;
	jlong rt[2];
	u_char err[1024] = {0};
	ngx_int_t rc;

	rt[0] = vtype;
	rt[1] = 0;
	rc = ctx->impl->remove(ctx,
					(uint8_t)ktype, ngx_http_clojure_abs_off_addr(key, koff), (size_t)klen,
					nji_ngx_http_clojure_shared_map_val_to_jpimary_handler,
					rt);

	if (rc != NGX_CLOJURE_SHARED_MAP_OK) {
		ngx_snprintf(err, sizeof(err) - 1, "shared map '%V' key %d not found!", &ctx->name, key);
		goto THROWS_EXCEPTION;
	}else if (rt[0] == NGX_CLOJURE_SHARED_MAP_NOT_FOUND) {
		return null_val;
	}
	return rt[1];

THROWS_EXCEPTION: {
	jclass ec = (*env)->FindClass(env, "java/lang/RuntimeException");
	if (ec != NULL) {
		ngx_snprintf(err, sizeof(err) - 1, "shared map '%V' key %d not found!", &ctx->name, key);
		(*env)->ThrowNew(env, ec, (char*)err);
	}
	(*env)->DeleteLocalRef(env, ec);
	return rc;
  }
}

/*********************************************************************
 * END optimized functions for those maps have int/long keys/values.
 *********************************************************************/

int ngx_http_clojure_init_shared_map_util() {
	JNIEnv *env;
	jclass nc_shm_class;
	JNINativeMethod nms[] = {
				{"ngetMap", "(Ljava/lang/Object;JJ)J", jni_ngx_http_clojure_shared_map_get_map},
				{"nput", "(JILjava/lang/Object;JJILjava/lang/Object;JJ)Ljava/lang/Object;", jni_ngx_http_clojure_shared_map_put},
				{"nputIfAbsent", "(JILjava/lang/Object;JJILjava/lang/Object;JJ)Ljava/lang/Object;", jni_ngx_http_clojure_shared_map_put_if_absent},
				{"nget", "(JILjava/lang/Object;JJ)Ljava/lang/Object;", jni_ngx_http_clojure_shared_map_get},
				{"ndelete", "(JILjava/lang/Object;JJ)J", jni_ngx_http_clojure_shared_map_delete},
				{"nremove", "(JILjava/lang/Object;JJ)Ljava/lang/Object;", jni_ngx_http_clojure_shared_map_remove},
				{"nsize", "(J)J", jni_ngx_http_clojure_shared_map_size},
				{"nclear", "(J)J", jni_ngx_http_clojure_shared_map_clear},
				{"ncontains", "(JILjava/lang/Object;JJ)J", jni_ngx_http_clojure_shared_map_contains},
				{"ngetNumber", "(JILjava/lang/Object;JJI)J", jni_ngx_http_clojure_shared_map_get_number},
				{"nputNumber", "(JILjava/lang/Object;JJIJJ)J", jni_ngx_http_clojure_shared_map_put_number},
				{"nputNumberIfAbsent", "(JILjava/lang/Object;JJIJJ)J", jni_ngx_http_clojure_shared_map_put_number_if_absent},
				{"nremoveNumber", "(JILjava/lang/Object;JJIJ)J", jni_ngx_http_clojure_shared_map_remove_number},
				{"natomicAddNumber","(JILjava/lang/Object;JJIJ)J", jni_ngx_http_clojure_shared_map_atomic_add_number},
				{"nvisit", "(JLnginx/clojure/util/NginxSharedHashMap$SharedMapSimpleVisitor;)J", jni_ngx_http_clojure_shared_map_visit}
	};

	if (ngx_http_clojure_init_shared_map_flag == NGX_HTTP_CLOJURE_JVM_OK) {
		return NGX_HTTP_CLOJURE_JVM_OK;
	}

	ngx_http_clojure_get_env(&env);
	nc_shm_class = (*env)->FindClass(env, "nginx/clojure/util/NginxSharedHashMap");
	exception_handle(nc_shm_class == NULL, env, return NGX_HTTP_CLOJURE_JVM_ERR);
	nc_shm_native_2_jobject_mid = (*env)->GetStaticMethodID(env, nc_shm_class,"native2JavaObject" ,"(IJJ)Ljava/lang/Object;");
	exception_handle(nc_shm_native_2_jobject_mid == NULL, env, return NGX_HTTP_CLOJURE_JVM_ERR);

	nc_shm_visit_mid = (*env)->GetStaticMethodID(env, nc_shm_class,"visit" ,"(IJJIJJLnginx/clojure/util/NginxSharedHashMap$SharedMapSimpleVisitor;)I");
	exception_handle(nc_shm_visit_mid == NULL, env, return NGX_HTTP_CLOJURE_JVM_ERR);

	(*env)->RegisterNatives(env, nc_shm_class, nms, sizeof(nms) / sizeof(JNINativeMethod));
	exception_handle(0 == 0, env, return NGX_HTTP_CLOJURE_JVM_ERR);

	ngx_http_clojure_init_shared_map_flag = NGX_HTTP_CLOJURE_JVM_OK;
	return NGX_HTTP_CLOJURE_JVM_OK;
}
