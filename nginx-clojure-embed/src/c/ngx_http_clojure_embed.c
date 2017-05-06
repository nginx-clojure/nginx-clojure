/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
#include <ngx_config.h>
#include <ngx_http_clojure_jvm.h>
#include <ngx_http_clojure_mem.h>
#include <ngx_core.h>
#include <ngx_event.h>
#include <nginx.h>

#if !(NGX_WIN32)
#define PUBLIC __attribute__ ((visibility ("default")))
#else
#define PUBLIC
#endif

#define EMBED_OK 0
#define EMBED_ERR -1
#define EMBED_PASS_CONF 1
#define EMBED_PASS_ALL 2

static char *nc_embed_err;

typedef struct {
	void *obj;
	void *data;
	void (*destroy) (void *, void *);
} ngx_http_clojure_embed_cleaner_t;

#define ngx_http_clojure_embed_return_error(m) \
		nc_embed_err = m; \
		return EMBED_ERR;

static void ngx_http_clojure_embed_clean_uncleaned(ngx_http_clojure_embed_cleaner_t *cleaners, ngx_http_clojure_embed_cleaner_t *pcleaner) {
		while (pcleaner != cleaners - 1) {
			pcleaner->destroy(pcleaner->obj, pcleaner->data);
			pcleaner--;
		}
}

#define ngx_http_clojure_embed_clean_and_return_error(m, cleaners, pcleaner) \
		nc_embed_err = m; \
		ngx_http_clojure_embed_clean_uncleaned(cleaners, pcleaner); \
		return EMBED_ERR;

static JavaVM *nc_embed_jvm;
static jclass nc_embed_class;
static jmethodID nc_embed_notify_mid;
static ngx_cycle_t      ngx_exit_cycle;
static ngx_log_t        ngx_exit_log;
static ngx_open_file_t  ngx_exit_log_file;

extern ngx_int_t ngx_save_argv(ngx_cycle_t *cycle, int argc, char *const *argv);
extern ngx_int_t ngx_get_options(int argc, char *const *argv);
extern ngx_int_t ngx_process_options(ngx_cycle_t *cycle);

static void nji_ngx_http_clojure_notify(ngx_int_t type, const char *message) {
	JNIEnv *env;
	(*nc_embed_jvm)->GetEnv(nc_embed_jvm, (void **)&env, JNI_VERSION_1_6);
	(*env)->CallStaticVoidMethod(env, nc_embed_class, nc_embed_notify_mid,
			(jint) type, (*env)->NewStringUTF(env, message));
}

static void ngx_http_clojure_embed_free_cloned_fake_argv(void * obj, void *data) {
	ngx_int_t c = (ngx_int_t)(intptr_t)data;
	char **argv = obj;
	for (; c > -1; c--) {
		free(argv[c]);
	}
	free(argv);
}

/*static void ngx_http_clojure_embed_free(void *obj, void *data) {
	free(obj);
}*/

static void ngx_http_clojure_embed_free_cycle_uncleaned(void * obj, void *data) {
	ngx_cycle_t *cycle = obj;
	if (cycle->connections)
		ngx_free(cycle->connections);
	if (cycle->read_events)
		ngx_free(cycle->read_events);
	if (cycle->write_events)
		ngx_free(cycle->write_events);
	if (cycle->files)
		ngx_free(cycle->files);
}

static void ngx_http_clojure_close_all_connections(ngx_cycle_t *cycle) {
	ngx_queue_t *q;
	ngx_connection_t *c;

	while (1) {
		if (ngx_queue_empty(&cycle->reusable_connections_queue)) {
			break;
		}

		q = ngx_queue_last(&cycle->reusable_connections_queue);
		c = ngx_queue_data(q, ngx_connection_t, queue);

		c->close = 1;
		c->read->handler(c->read);
	}
}

static void ngx_http_clojure_single_process_cycle_stop(ngx_cycle_t *cycle, ngx_http_clojure_embed_cleaner_t *cleaners, ngx_http_clojure_embed_cleaner_t *pcleaner) {
	ngx_uint_t i;
	ngx_log_t *log;

	ngx_delete_pidfile(cycle);

	ngx_log_error(NGX_LOG_NOTICE, cycle->log, 0, "exit");

	for (i = 0; ngx_modules[i]; i++) {
		if (ngx_modules[i]->exit_master) {
			ngx_modules[i]->exit_master(cycle);
		}
	}

	ngx_close_listening_sockets(cycle);
	ngx_http_clojure_close_all_connections(cycle);
	ngx_done_events(cycle);

	ngx_exit_log = *ngx_log_get_file_log(ngx_cycle->log);

	ngx_exit_log_file.fd = ngx_exit_log.file->fd;
	ngx_exit_log.file = &ngx_exit_log_file;
	ngx_exit_log.next = NULL;
	ngx_exit_log.writer = NULL;

	log = ngx_exit_cycle.log = &ngx_exit_log;
	ngx_exit_cycle.files = ngx_cycle->files;
	ngx_exit_cycle.files_n = ngx_cycle->files_n;
	ngx_cycle = &ngx_exit_cycle;

	for (log = ngx_cycle->log; log; log = log->next) {
		if (log->file != NULL && log->file->fd != ngx_stderr) {
			ngx_close_file(log->file->fd);
		}
	}

    ngx_http_clojure_embed_clean_uncleaned(cleaners, pcleaner);

	ngx_destroy_pool(cycle->pool);
}

static ngx_int_t ngx_http_clojure_single_process_cycle(ngx_cycle_t *cycle, ngx_http_clojure_embed_cleaner_t *cleaners, ngx_http_clojure_embed_cleaner_t **ppcleaner) {
	ngx_uint_t i;

/*	if (ngx_set_environment(cycle, NULL) == NULL) {
		ngx_http_clojure_embed_return_error("ngx_set_environment");
	}*/

	for (i = 0; ngx_modules[i]; i++) {
		if (ngx_modules[i]->init_process) {
			if (ngx_modules[i]->init_process(cycle) == NGX_ERROR) {
				nc_embed_err = "init_process error";
				ngx_quit = 1;
				goto QUIT;
			}else if (ngx_modules[i] == &ngx_event_core_module) {
				(*ppcleaner)++;
				(*ppcleaner)->obj = cycle;
				(*ppcleaner)->destroy = ngx_http_clojure_embed_free_cycle_uncleaned;
			}
		}
	}

	nji_ngx_http_clojure_notify(EMBED_PASS_ALL, "Initiliazed all modules");

	for (;;) {
		ngx_log_debug0(NGX_LOG_DEBUG_EVENT, cycle->log, 0, "worker cycle");

		ngx_process_events_and_timers(cycle);

QUIT:
        if (ngx_terminate || ngx_quit) {

			for (i = 0; ngx_modules[i]; i++) {
				if (ngx_modules[i]->exit_process) {
					ngx_modules[i]->exit_process(cycle);
				}
			}

			/*reset some global variables so that we can restart server in one JVM process*/
			ngx_pagesize_shift = 0;

			/*We need not exit the process and JVM will do it.*/
			ngx_http_clojure_single_process_cycle_stop(cycle, cleaners, *ppcleaner);
			if (nc_embed_err) {
				return EMBED_ERR;
			}
			return 0;
		}
	}
}


#if (NGX_SETPROCTITLE_USES_ENV)
	extern char **environ;
#endif

#if (NGX_DARWIN)
	char **environ;
    #include <crt_externs.h>
#endif

static ngx_int_t ngx_http_clojure_embed_start(int argc, char *const *argv){
	ngx_log_t *log;
	ngx_cycle_t *cycle, init_cycle;
	ngx_core_conf_t  *ccf;
#if nginx_version < 1009011
	ngx_int_t i;
#endif
	ngx_int_t rc;
	ngx_http_clojure_embed_cleaner_t cleaners[64];
	ngx_http_clojure_embed_cleaner_t *pcleaner = cleaners -1;

#if (NGX_DARWIN)
	environ = *_NSGetEnviron();
#endif

	ngx_memzero(cleaners, sizeof(cleaners));
	nc_embed_err = NULL;

    if (ngx_strerror_init() != NGX_OK) {
    	ngx_http_clojure_embed_return_error("ngx_strerror_init");
    }

	if (ngx_get_options(argc, argv) != NGX_OK) {
		ngx_http_clojure_embed_return_error("ngx_get_options");
	}

	ngx_max_sockets = -1;
    ngx_time_init();

    ngx_pid = ngx_getpid();

    log = ngx_log_init(NULL);
    if (log == NULL) {
    	ngx_http_clojure_embed_return_error("ngx_log_init");
    }


#if (NGX_OPENSSL)
    ngx_ssl_init(log);
#endif

    ngx_memzero(&init_cycle, sizeof(ngx_cycle_t));
	init_cycle.log = log;
	ngx_cycle = &init_cycle;

	init_cycle.pool = ngx_create_pool(1024, log);
	if (init_cycle.pool == NULL) {
		ngx_http_clojure_embed_return_error("ngx_create_pool(1024, log)");
	}

	ngx_save_argv(&init_cycle, argc, argv);

	if (ngx_process_options(&init_cycle) != NGX_OK) {
		ngx_http_clojure_embed_return_error("invalid option found at ngx_process_options(&init_cycle)");
	}

	if (ngx_argv != argv) {
		pcleaner++;
		pcleaner->obj = ngx_argv;
		pcleaner->data = (void *)(intptr_t)1;
		pcleaner->destroy = ngx_http_clojure_embed_free_cloned_fake_argv;
	}

    if (ngx_os_init(log) != NGX_OK) {
    	ngx_http_clojure_embed_clean_and_return_error("ngx_os_init", cleaners, pcleaner);
    }


    if (ngx_crc32_table_init() != NGX_OK) {
    	ngx_http_clojure_embed_clean_and_return_error("ngx_crc32_table_init", cleaners, pcleaner);
    }

#if nginx_version >= 1009011
	if (ngx_preinit_modules() != NGX_OK) {
		ngx_http_clojure_embed_clean_and_return_error("ngx_preinit_modules", cleaners, pcleaner);
	}
#else
    ngx_max_module = 0;
    for (i = 0; ngx_modules[i]; i++) {
        ngx_modules[i]->index = ngx_max_module++;
    }
#endif
/*  init_cycle.conf_file.data = conf_file->data;
    init_cycle.conf_file.len = conf_file->len;*/

    cycle = ngx_init_cycle(&init_cycle);

    if (cycle == NULL) {
    	ngx_http_clojure_embed_clean_and_return_error("ngx_init_cycle", cleaners, pcleaner);
    }

    nji_ngx_http_clojure_notify(EMBED_PASS_CONF, "");

    ngx_cycle = cycle;
    ccf = (ngx_core_conf_t *) ngx_get_conf(cycle->conf_ctx, ngx_core_module);

#if !(NGX_WIN32)
    ngx_daemonized = 0;
#endif

    if (ngx_create_pidfile(&ccf->pid, cycle->log) != NGX_OK) {
    	ngx_http_clojure_embed_clean_and_return_error("ngx_create_pidfile", cleaners, pcleaner);
    }

    ngx_use_stderr = 1;
    ngx_process = NGX_PROCESS_SINGLE;

    rc = ngx_http_clojure_single_process_cycle(cycle, cleaners, &pcleaner);
    return rc;
}

static jlong jni_ngx_http_clojure_embed_start(JNIEnv *env, jclass clz, jstring cmdline) {
#if !(NGX_WIN32)
	char path[NGX_MAX_PATH];
#else
	char path[4096];
#endif

	size_t len = (*env)->GetStringUTFLength(env, cmdline);
	int rc = 0;
	int argc = 0;
	char* argv[10];
	char *pos = path;

	if (len > sizeof(path) - 2) {
		nji_ngx_http_clojure_notify(EMBED_ERR, "too long nginx args!");
		return 1;
	}

	//set jvm for NginxClojureRT jni
	rc = ngx_http_clojure_init_by_jse_app(env);
	exception_handle(rc != 0, env, return rc);

	(*env)->GetStringUTFRegion(env, cmdline, 0, len, path);
	path[len] = 0;
	argv[argc++] = pos;

	while (*pos) {
		if (*pos == '\n') {
			if (argc == 8) {
				nji_ngx_http_clojure_notify(EMBED_ERR, "too many nginx args!");
				return 1;
			}
			*pos = 0;
			argv[argc++] = ++pos;
		}else {
			pos++;
		}
	}

	argv[argc] = 0;
	ngx_quit = 0;
	rc = ngx_http_clojure_embed_start(argc, argv);
	if (rc != 0) {
		nji_ngx_http_clojure_notify(rc, nc_embed_err);
	}
	return 0;
}

static void jni_ngx_http_clojure_embed_stop(JNIEnv *env, jclass clz) {
	ngx_quit = 1;
	ngx_http_clojure_mem_wakeup_event_loop();
}


PUBLIC JNIEXPORT jlong JNICALL Java_nginx_clojure_embed_NginxEmbedServer_register(
		JNIEnv *env, jclass clz) {
	JNINativeMethod nms[] = {
			{ "innerStart", "(Ljava/lang/String;)J", jni_ngx_http_clojure_embed_start },
			{ "innerStop", "()V", jni_ngx_http_clojure_embed_stop }
	};

	(*env)->RegisterNatives(env, clz, nms,
			sizeof(nms) / sizeof(JNINativeMethod));
	exception_handle(0 == 0, env, return NGX_HTTP_CLOJURE_JVM_ERR);

	(*env)->GetJavaVM(env, &nc_embed_jvm);
	nc_embed_class = (*env)->NewGlobalRef(env, clz);
	nc_embed_notify_mid = (*env)->GetStaticMethodID(env, clz, "notifyFromNative", "(ILjava/lang/String;)V");
	return 0;
}
