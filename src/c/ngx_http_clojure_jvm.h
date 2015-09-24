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
#define NGX_HTTP_CLOJURE_JVM_ERR_INIT_SHAREDMAP 7

#define NGX_HTTP_CLOJURE_SOCKET_SHUTDOWN_READ 0
#define NGX_HTTP_CLOJURE_SOCKET_SHUTDOWN_WRITE 1
#define NGX_HTTP_CLOJURE_SOCKET_SHUTDOWN_BOTH 2
#define NGX_HTTP_CLOJURE_SOCKET_SHUTDOWN_SOFT_FLAG  4
#define NGX_HTTP_CLOJURE_SOCKET_SHUTDOWN_SOFT_READ  (NGX_HTTP_CLOJURE_SOCKET_SHUTDOWN_READ | NGX_HTTP_CLOJURE_SOCKET_SHUTDOWN_SOFT_FLAG)
#define NGX_HTTP_CLOJURE_SOCKET_SHUTDOWN_SOFT_WRITE (NGX_HTTP_CLOJURE_SOCKET_SHUTDOWN_WRITE | NGX_HTTP_CLOJURE_SOCKET_SHUTDOWN_SOFT_FLAG)
#define NGX_HTTP_CLOJURE_SOCKET_SHUTDOWN_SOFT_BOTH  (NGX_HTTP_CLOJURE_SOCKET_SHUTDOWN_BOTH | NGX_HTTP_CLOJURE_SOCKET_SHUTDOWN_SOFT_FLAG)


#define NGX_HTTP_CLOJURE_SOCKET_OK 0
#define NGX_HTTP_CLOJURE_SOCKET_ERR -16
#define NGX_HTTP_CLOJURE_SOCKET_ERR_RESOLVE -17
#define NGX_HTTP_CLOJURE_SOCKET_ERR_CONNECT -18
#define NGX_HTTP_CLOJURE_SOCKET_ERR_CONNECT_TIMEOUT -19
/**
 * @deprecated
 * please use either of NGX_HTTP_CLOJURE_SOCKET_ERR_CONNECT_TIMEOUT, NGX_HTTP_CLOJURE_SOCKET_ERR_READ_TIMEOUT or NGX_HTTP_CLOJURE_SOCKET_ERR_WRITE_TIMEOUT
 */
#define NGX_HTTP_CLOJURE_SOCKET_ERR_TIMEOUT -20
#define NGX_HTTP_CLOJURE_SOCKET_ERR_READ -21
#define NGX_HTTP_CLOJURE_SOCKET_ERR_READ_TIMEOUT -22
#define NGX_HTTP_CLOJURE_SOCKET_ERR_WRITE -23
#define NGX_HTTP_CLOJURE_SOCKET_ERR_WRITE_TIMEOUT -24
#define NGX_HTTP_CLOJURE_SOCKET_ERR_RESET -25
#define NGX_HTTP_CLOJURE_SOCKET_ERR_OUTOFMEMORY -26
#define NGX_HTTP_CLOJURE_SOCKET_ERR_AGAIN -27
#define NGX_HTTP_CLOJURE_SOCKET_ERR_BIND   -28


#define NGX_HTTP_CLOJURE_CHANNEL_EVENT_CLOSE 0
#define NGX_HTTP_CLOJURE_CHANNEL_EVENT_CONNECT  1
#define NGX_HTTP_CLOJURE_CHANNEL_EVENT_READ  2
#define NGX_HTTP_CLOJURE_CHANNEL_EVENT_WRITE 4
#define NGX_HTTP_CLOJURE_CHANNEL_EVENT_MSGREMAIN 8
#define NGX_HTTP_CLOJURE_CHANNEL_EVENT_MSGTEXT  16
#define NGX_HTTP_CLOJURE_CHANNEL_EVENT_MSGBIN   32
#define NGX_HTTP_CLOJURE_CHANNEL_EVENT_MSGCLOSE 64
#define NGX_HTTP_CLOJURE_CHANNEL_EVENT_MSGFIRST 128
#define NGX_HTTP_CLOJURE_CHANNEL_EVENT_MSGPONG 256


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
		(jobj ? (void *)((char *)(*(uintptr_t*)jobj) + off) : (void *)(uintptr_t)off)

#define ngx_http_clojure_abs_addr(jobj) \
		(jobj ? (void *)((char *)(*(uintptr_t*)jobj)) : (void *)(uintptr_t)0;

extern int ngx_http_clojure_is_embeded_by_jse;

int ngx_http_clojure_check_jvm();

/*
 * initialize jvm and return NGX_HTTP_CLOJURE_JVM_OK for success or NGX_HTTP_CLOJURE_JVM_ERR* for failing.
 * if jvm has been successfully initialized already it will do nothing and just return NGX_HTTP_CLOJURE_JVM_OK
 */
int ngx_http_clojure_init_jvm(char *jvm_path, char * *opts, size_t len);

int ngx_http_clojure_init_by_jse_app(JNIEnv *env);

int ngx_http_clojure_get_env(JNIEnv **penv);

int ngx_http_clojure_get_jvm(JavaVM  **pvm);

int ngx_http_clojure_close_jvm();

#endif /* NGX_HTTP_CLOJURE_JVM_H_ */
