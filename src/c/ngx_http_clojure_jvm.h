/*
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 */

#ifndef NGX_HTTP_CLOJURE_JVM_H_
#define NGX_HTTP_CLOJURE_JVM_H_

#include <jni.h>

#define NGX_HTTP_CLOJURE_JVM_MAX_OPTS 64

#define NGX_HTTP_CLOJURE_JVM_OK 0
#define NGX_HTTP_CLOJURE_JVM_ERR 1
#define NGX_HTTP_CLOJURE_JVM_ERR_LOAD_LIB 2

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

typedef jint (*jni_createvm_pt)(JavaVM **pvm, void **penv, void *args);

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
