/*
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 */

#ifndef NGX_HTTP_CLOJURE_SOCKET_H_
#define NGX_HTTP_CLOJURE_SOCKET_H_


#include <nginx.h>
#include <ngx_http.h>


extern ngx_cycle_t *ngx_http_clojure_global_cycle;

/*move constants such as NGX_HTTP_CLOJURE_JVM_OK to ngx_http_clojure_jvm.h for sharing them*/

typedef struct ngx_http_clojure_socket_upstream_s ngx_http_clojure_socket_upstream_t;

typedef void (*ngx_http_clojure_socket_upstream_handler_pt)(ngx_http_clojure_socket_upstream_t *u, ngx_int_t sc/*status code*/);


struct ngx_http_clojure_socket_upstream_s {

	/*socket options*/
	ngx_msec_t connect_timeout;
	ngx_msec_t write_timeout;
	ngx_msec_t read_timeout;

	unsigned tcp_nodelay : 1;
	unsigned so_keepalive : 1;

	unsigned connect_event_sent  : 1;
	unsigned hijacked_from_http : 1;

	/*TCP SO_SNDLOWAT option*/
	size_t send_lowat;

	/*current http request which lunching current upstream request
	 * this http request may be null if the socket is running on the background where no http request binding
	 * this http request will be changed frequently when this socket upstream created from a pool (eg. jdbc connection pool)
	 * TODO: check broken connection of http request so that it can stop working and return to the pool earlier*/
	ngx_http_request_t *r;

	/*buffer size for receive (it is equal to SO_RCVBUF)*/
	size_t buffer_size;

	/*total number of buffers*/
	/*size_t buffer_num;*/

	/*connection to upstream server*/
	ngx_peer_connection_t peer;

	ngx_pool_t *pool;

	ngx_http_upstream_resolved_t    *resolved;

	/*user defined context, eg. JNI java object handle*/
	void *context;

	/*So far we need not do complex buffer management because java byte[] object
	 *can be direct written or read by native code and is enough to implement fast java client socket.
	 *But someday if we consider upstream proxy
	 *we maybe should do refactor together with output chain functions from NginxClojureRT .*/

	/*buffers for read*/
	/*ngx_chain_t *read_bufs;*/

	/*buffers for write*/
	/*ngx_chain_t *write_bufs;*/

	/*free buffers*/
	/*ngx_chain_t *free_bufs;*/

	/*event handler*/
	ngx_http_clojure_socket_upstream_handler_pt read_event_handler;
	ngx_http_clojure_socket_upstream_handler_pt write_event_handler;
	/*for receive connect finished event or error event*/
	ngx_http_clojure_socket_upstream_handler_pt connect_event_handler;


	ngx_http_clojure_socket_upstream_handler_pt socket_upstream_finalize;


};


/*
 *Define macros to hint which field can be set as public field
 *All macros should be in a {} block, other kinds of usages maybe are wrong,
 *eg.  "if (xx) macros(x, y);" is wrong! It should be "if (xx) { macros(x, y); }"
 **/
#define ngx_http_clojure_socket_upstream_set_connect_timeout(u, t)  u->connect_timeout = t

#define ngx_http_clojure_socket_upstream_set_send_timeout(u, t)  u->send_timeout = t

#define ngx_http_clojure_socket_upstream_set_read_timeout(u, t)  u->read_timeout = t

#define ngx_http_clojure_socket_upstream_set_write_timeout(u, t)  u->write_timeout = t

#define ngx_http_clojure_socket_upstream_set_send_lowat(u, n) u->send_lowat = n

#define ngx_http_clojure_socket_upstream_set_buffer_size(u, n) u->buffer_size = n

#define ngx_http_clojure_socket_upstream_set_context(u, ctx) u->context = ctx

#define ngx_http_clojure_socket_upstream_set_context(u, ctx) u->context = ctx

#define ngx_http_clojure_socket_upstream_set_event_handler(u, r, w, c, f) u->read_event_handler = r; \
	u->write_event_handler=w;\
	u->connect_event_handler=c;\
	u->socket_upstream_finalize = f

/*#define ngx_http_clojure_socket_upstream_set_buffer_num(u, n) u->buffer_num = n*/

#define set_if_unset(u, f, v) !u->f ? u->f = v : 0


/*ngx_chain_t * ngx_http_clojure_socket_upstream_init_free_bufs(ngx_http_clojure_socket_upstream_t *u);*/

/*
#define ngx_http_clojure_socket_upstream_make_unset_to_default(u) (set_if_unset(u,buffer_size, ngx_pagesize), \
	set_if_unset(u,buffer_num, 2), \
	ngx_http_clojure_socket_upstream_init_free_bufs(u))
*/


ngx_http_clojure_socket_upstream_t *ngx_http_clojure_socket_upstream_create(size_t pool_size, ngx_log_t *log);

int ngx_http_clojure_socket_upstream_available(ngx_http_clojure_socket_upstream_t *u);

int ngx_http_clojure_socket_upstream_set_tcp_nodelay(ngx_http_clojure_socket_upstream_t *u, int tcp_nodelay);

int ngx_http_clojure_socket_upstream_set_so_keepalive(ngx_http_clojure_socket_upstream_t *u, int so_keepalive);

void ngx_http_clojure_socket_upstream_connect(ngx_http_clojure_socket_upstream_t *u, struct sockaddr *addr, socklen_t len);

void ngx_http_clojure_socket_upstream_connect_by_url(ngx_http_clojure_socket_upstream_t *u, ngx_url_t *url);

int ngx_http_clojure_socket_upstream_read(ngx_http_clojure_socket_upstream_t *u, void *buf, size_t size);

int ngx_http_clojure_socket_upstream_write(ngx_http_clojure_socket_upstream_t *u, void *buf, size_t size);

void ngx_http_clojure_socket_upstream_close(ngx_http_clojure_socket_upstream_t *u);

/*how can be either:
 * NGX_HTTP_CLOJURE_SOCKET_SHUTDOWN_READ,
 * NGX_HTTP_CLOJURE_SOCKET_SHUTDOWN_WRITE,
 * NGX_HTTP_CLOJURE_SOCKET_SHUTDOWN_BOTH
 * NGX_HTTP_CLOJURE_SOCKET_SHUTDOWN_SOFT_READ
 * NGX_HTTP_CLOJURE_SOCKET_SHUTDOWN_SOFT_WRITE
 * NGX_HTTP_CLOJURE_SOCKET_SHUTDOWN_SOFT_BOTH
 * */
int ngx_http_clojure_socket_upstream_shutdown(ngx_http_clojure_socket_upstream_t *u, int how);

/*for jni init*/
int ngx_http_clojure_init_socket_util();

void ngx_http_clojure_destory_socket_util();

#endif /* NGX_HTTP_CLOJURE_SOCKET_H_ */
