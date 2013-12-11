/*
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 */

#include <stdlib.h>
#include <string.h>
#include "ngx_http_clojure_jvm.h"
#include <dlfcn.h>

static JNIEnv *env = NULL;
static JavaVM *jvm = NULL;
static jclass Class_java_lang_String = NULL;
static jmethodID MID_String_init = NULL;
static jmethodID MID_String_getBytes = NULL;

int ngx_http_clojure_check_jvm() {
	return jvm == NULL ? NGX_HTTP_CLOJURE_JVM_ERR : NGX_HTTP_CLOJURE_JVM_OK;
}

int ngx_http_clojure_get_env(JNIEnv **penv) {
	*penv = env;
	return ngx_http_clojure_check_jvm();
}

int ngx_http_clojure_get_jvm(JavaVM **pvm) {
	*pvm = jvm;
	return ngx_http_clojure_check_jvm();
}

int ngx_http_clojure_init_jvm(char *jvm_path, char * *opts, size_t len) {

	void *libVM;
	JavaVMInitArgs vm_args;
	JavaVMOption options[NGX_HTTP_CLOJURE_JVM_MAX_OPTS];
	size_t i;

	if (jvm != NULL && env != NULL) {
		return NGX_HTTP_CLOJURE_JVM_OK;
	}

	if ((libVM = dlopen(jvm_path, RTLD_LAZY)) == NULL) {
		return NGX_HTTP_CLOJURE_JVM_ERR_LOAD_LIB;
	}

	jni_createvm_pt jvm_creator = dlsym(libVM, "JNI_CreateJavaVM");
	if (jvm_creator == NULL){
		return NGX_HTTP_CLOJURE_JVM_ERR;
	}

	for (i = 0; i < len; i++){
		options[i].extraInfo = NULL;
		options[i].optionString = opts[i];
	}

	vm_args.version = JNI_VERSION_1_6;
	vm_args.ignoreUnrecognized = JNI_TRUE;
	vm_args.options = options;
	vm_args.nOptions = len;

	if ((*jvm_creator)(&jvm, (void **)&env, (void *)&vm_args) < 0){
		return NGX_HTTP_CLOJURE_JVM_ERR;
	}

	Class_java_lang_String = (*env)->FindClass(env, "java/lang/String");
	MID_String_getBytes = (*env)->GetMethodID(env, Class_java_lang_String, "getBytes", "()[B");
	MID_String_init = (*env)->GetMethodID(env, Class_java_lang_String, "<init>", "([B)V");


	return NGX_HTTP_CLOJURE_JVM_OK;

}

/*
 *This function code is from "The Java  Native Interface -- Programmer’s Guide and Specification"
 */
//static jstring JNU_NewStringNative(JNIEnv *env, const char *str) {
//	jstring result;
//	jbyteArray bytes = 0;
//	int len;
//	if ((*env)->EnsureLocalCapacity(env, 2) < 0) {
//		return NULL; /* out of memory error */
//	}
//	len = strlen(str);
//	bytes = (*env)->NewByteArray(env, len);
//	if (bytes != NULL) {
//		(*env)->SetByteArrayRegion(env, bytes, 0, len, (jbyte *) str);
//		result = (*env)->NewObject(env, Class_java_lang_String, MID_String_init,
//				bytes);
//		(*env)->DeleteLocalRef(env, bytes);
//		return result;
//	} /* else fall through */
//	return NULL;
//}

/*
 *This function code is from "The Java Native Interface -- Programmer’s Guide and Specification"
 */
//static int JNU_GetStringNativeChars(JNIEnv *env, jstring jstr, char result[], int *plen) {
//	jbyteArray bytes = 0;
//	jthrowable exc;
//	if ((*env)->EnsureLocalCapacity(env, 2) < 0) {
//		return NGX_HTTP_CLOJURE_JVM_ERR; /* out of memory error */
//	}
//	bytes = (*env)->CallObjectMethod(env, jstr, MID_String_getBytes);
//	exc = (*env)->ExceptionOccurred(env);
//	if (!exc) {
//		jint len = (*env)->GetArrayLength(env, bytes);
//		if (len < *plen){
//			*plen = len;
//		}
//		(*env)->GetByteArrayRegion(env, bytes, 0, *plen, (jbyte *) result);
//		result[*plen] = 0; /* NULL-terminate */
//	} else {
//		(*env)->DeleteLocalRef(env, exc);
//	}
//	(*env)->DeleteLocalRef(env, bytes);
//	return !exc ? NGX_HTTP_CLOJURE_JVM_OK : NGX_HTTP_CLOJURE_JVM_ERR;
//}


int ngx_http_clojure_close_jvm() {
	JNIEnv * lenv;
	jclass systemClass = NULL;
	jmethodID exitMethod = NULL;
	(*jvm)->AttachCurrentThread(jvm, (void**)&lenv, NULL);
	systemClass = (*lenv)->FindClass(lenv, "java/lang/System");
	exitMethod = (*lenv)->GetStaticMethodID(lenv, systemClass, "exit", "(I)V");
	(*lenv)->CallStaticVoidMethod(lenv, systemClass, exitMethod, 0);
	(*jvm)->DestroyJavaVM(jvm);

	env = NULL;
	jvm = NULL;

	return NGX_HTTP_CLOJURE_JVM_OK;
}
