/*
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 */
#ifndef NGX_HTTP_CLOJURE_SHARED_MAP_TINYMAP_H_
#define NGX_HTTP_CLOJURE_SHARED_MAP_TINYMAP_H_

#include "ngx_http_clojure_shared_map.h"

/*
 * Compressed pointers version of ngx_http_clojure_hashmap_entry_t, typically on 64-bit OS
 * the size of ngx_http_clojure_hashmap_entry_t is 40B and the size of ngx_http_clojure_tinymap_entry_t
 * is 24B*/
typedef struct ngx_http_clojure_tinymap_entry_s {
	unsigned ktype : 4;
	unsigned vtype : 4;
	unsigned ksize : 24; /*key size*/
	uint32_t key; /*offset of key*/
	uint32_t hash;
	uint32_t val;
	uint32_t vsize; /*value size*/
	uint32_t next;
} NGX_CLOJURE_ATTR_MAY_ALIAS ngx_http_clojure_tinymap_entry_t;

typedef struct {
	ngx_atomic_uint_t size;
	uint32_t *table;
} ngx_http_clojure_tinymap_t;

typedef struct {
	uint32_t entry_table_size;
	uint64_t space_size;
	uint32_t hash_seed;
	ngx_http_clojure_tinymap_t *map;
	ngx_slab_pool_t *shpool;
} ngx_http_clojure_shared_map_tinymap_ctx_t;

ngx_int_t ngx_http_clojure_shared_map_tinymap_init(ngx_conf_t *cf, ngx_http_clojure_shared_map_ctx_t *ctx);

ngx_int_t ngx_http_clojure_shared_map_tinymap_get_entry(ngx_http_clojure_shared_map_ctx_t *ctx, uint8_t ktype,
		const u_char *key, size_t klen, ngx_http_clojure_shared_map_val_handler val_handler, void *handler_data);

ngx_int_t ngx_http_clojure_shared_map_tinymap_put_entry(ngx_http_clojure_shared_map_ctx_t *sctx, uint8_t ktype,
		const u_char *key, size_t klen, uint8_t vtype, const void *val, size_t vlen,
		ngx_http_clojure_shared_map_val_handler old_val_handler, void *handler_data);

ngx_int_t ngx_http_clojure_shared_map_tinymap_put_entry_if_absent(ngx_http_clojure_shared_map_ctx_t *sctx, uint8_t ktype,
		const u_char *key, size_t klen, uint8_t vtype, const void *val, size_t vlen,
		ngx_http_clojure_shared_map_val_handler old_val_handler, void *handler_data);

ngx_int_t ngx_http_clojure_shared_map_tinymap_remove_entry(ngx_http_clojure_shared_map_ctx_t *ctx, uint8_t ktype,
		const u_char *key, size_t len,ngx_http_clojure_shared_map_val_handler old_val_handler, void *handler_data);

ngx_int_t ngx_http_clojure_shared_map_tinymap_size(ngx_http_clojure_shared_map_ctx_t * sctx);

ngx_int_t ngx_http_clojure_shared_map_tinymap_clear(ngx_http_clojure_shared_map_ctx_t * sctx);

ngx_int_t ngx_http_clojure_shared_map_tinymap_visit(ngx_http_clojure_shared_map_ctx_t  *sctx,
    ngx_http_clojure_shared_map_visit_handler visit_handler, void * handler_data);

#endif /* NGX_HTTP_CLOJURE_SHARED_MAP_CHASHMAP_H_ */
