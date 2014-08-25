/*
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 */

#ifndef NGX_HTTP_CLOJURE_JVM_H_
#define NGX_HTTP_CLOJURE_JVM_H_

#include <jni.h>

#if defined(_WIN32) || defined(WIN32)

#pragma warning (disable : 4305)
#pragma warning (disable : 4244)
#pragma warning (disable : 4152)


typedef jint (WINAPI *jni_createvm_pt)(JavaVM **pvm, void **penv, void *args);

#else

typedef jint (*jni_createvm_pt)(JavaVM **pvm, void **penv, void *args);

#endif


#define NGX_HTTP_CLOJURE_JVM_OK 0
#define NGX_HTTP_CLOJURE_JVM_ERR 1
#define NGX_HTTP_CLOJURE_JVM_ERR_LOAD_LIB 2
#define NGX_HTTP_CLOJURE_JVM_ERR_INIT_PIPE 3
#define NGX_HTTP_CLOJURE_JVM_ERR_MALLOC 4
#define NGX_HTTP_CLOJURE_JVM_ERR_INIT_MEMIDX 5
#define NGX_HTTP_CLOJURE_JVM_ERR_INIT_SOCKETAPI 6

#define exception_handle(test, env, action) \
    do { \
        if (test) { \
			if ( (*env)->ExceptionOccurred(env) ) { \
				(*env)->ExceptionDescribe(env); \
				(*env)->ExceptionClear(env); \
				action; \
			} \
		} \
	} while (0)

#define ngx_http_clojure_abs_off_addr(jobj, off) \
		(jobj ? (char *)(*(uintptr_t*)jobj) + off : (char *)(uintptr_t)off)

#define ngx_http_clojure_abs_addr(jobj) \
		(jobj ? (char *)(*(uintptr_t*)jobj) : (char *)(uintptr_t)0;

int ngx_http_clojure_check_jvm();

/*
 * initialize jvm and return NGX_HTTP_CLOJURE_JVM_OK for success or NGX_HTTP_CLOJURE_JVM_ERR* for failing.
 * if jvm has been successfully initialized already it will do nothing and just return NGX_HTTP_CLOJURE_JVM_OK
 */
int ngx_http_clojure_init_jvm(char *jvm_path, char * *opts, size_t len);

int ngx_http_clojure_get_env(JNIEnv **penv);

int ngx_http_clojure_get_jvm(JavaVM  **pvm);

int ngx_http_clojure_close_jvm();

#endif /* NGX_HTTP_CLOJURE_JVM_H_ */
