/*
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 */
#include <ngx_config.h>
#include "ngx_http_clojure_jvm.h"

#if defined(_WIN32) || defined(WIN32)


#else

#include <dlfcn.h>

#endif

static JNIEnv *jvm_env = NULL;
static JavaVM *jvm = NULL;

int ngx_http_clojure_is_embeded_by_jse = 0;

int ngx_http_clojure_check_jvm() {
	return jvm == NULL ? NGX_HTTP_CLOJURE_JVM_ERR : NGX_HTTP_CLOJURE_JVM_OK;
}

int ngx_http_clojure_get_env(JNIEnv **penv) {
	*penv = jvm_env;
	return ngx_http_clojure_check_jvm();
}

int ngx_http_clojure_get_jvm(JavaVM **pvm) {
	*pvm = jvm;
	return ngx_http_clojure_check_jvm();
}



int ngx_http_clojure_init_jvm(char *jvm_path, char * *opts, size_t len) {

	void *libVM;
	void *env;
	JavaVMInitArgs vm_args;
	JavaVMOption *options;
	size_t i;
	jni_createvm_pt jvm_creator;

	if (jvm != NULL && jvm_env != NULL) {
		return NGX_HTTP_CLOJURE_JVM_OK;
	}

#if defined(_WIN32) || defined(WIN32)

	if ((libVM = LoadLibrary(jvm_path)) == NULL) {
		return NGX_HTTP_CLOJURE_JVM_ERR_LOAD_LIB;
	}
	jvm_creator = (jni_createvm_pt)GetProcAddress(libVM, "JNI_CreateJavaVM");

#else

    /*append RTLD_GLOBAL flag for Alpine Linux on which OpenJDK 7 libjvm.so 
     *can not correctly handle cross symbol links from libjava.so, libverify.so*/
	if ((libVM = dlopen(jvm_path, RTLD_LAZY | RTLD_GLOBAL)) == NULL) {
		printf("can not open shared lib :%s,\n %s\n", jvm_path, dlerror());
		return NGX_HTTP_CLOJURE_JVM_ERR_LOAD_LIB;
	}
	jvm_creator = dlsym(libVM, "JNI_CreateJavaVM");
	if (jvm_creator == NULL) {
		/*for macosx default jvm*/
		jvm_creator = dlsym(libVM, "JNI_CreateJavaVM_Impl");
	}

#endif

	if (jvm_creator == NULL){
		return NGX_HTTP_CLOJURE_JVM_ERR;
	}

	options = malloc(len * sizeof(JavaVMOption));
	if (!options) {
		return NGX_HTTP_CLOJURE_JVM_ERR_MALLOC;
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
		free(options);
		return NGX_HTTP_CLOJURE_JVM_ERR;
	}

	free(options);
	jvm_env = env;

	return NGX_HTTP_CLOJURE_JVM_OK;
}

int ngx_http_clojure_init_by_jse_app(JNIEnv *env) {
	ngx_http_clojure_is_embeded_by_jse = 1;
	jvm_env = env;
	return (*env)->GetJavaVM(env, &jvm);
}


int ngx_http_clojure_close_jvm() {
	jclass systemClass = NULL;
	jmethodID exitMethod = NULL;

	if (jvm == NULL ||  jvm_env == NULL) {
		return NGX_HTTP_CLOJURE_JVM_OK;
	}

	if (ngx_http_clojure_is_embeded_by_jse) {
		jvm = NULL;
		jvm_env = NULL;
		ngx_http_clojure_is_embeded_by_jse = 0;
		return NGX_HTTP_CLOJURE_JVM_OK;
	}

	systemClass = (*jvm_env)->FindClass(jvm_env, "java/lang/System");
	exitMethod = (*jvm_env)->GetStaticMethodID(jvm_env, systemClass, "exit", "(I)V");
	(*jvm_env)->CallStaticVoidMethod(jvm_env, systemClass, exitMethod, 0);
	(*jvm)->DestroyJavaVM(jvm);

	jvm_env = NULL;
	jvm = NULL;

	return NGX_HTTP_CLOJURE_JVM_OK;
}
