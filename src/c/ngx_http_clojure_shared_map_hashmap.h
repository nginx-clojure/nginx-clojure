/*
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 */
#ifndef NGX_HTTP_CLOJURE_SHARED_MAP_HASHMAP_H_
#define NGX_HTTP_CLOJURE_SHARED_MAP_HASHMAP_H_

#include "ngx_http_clojure_shared_map.h"


typedef struct ngx_http_clojure_hashmap_entry_s {
	char *key;
	uint32_t ksize; /*key size*/
	unsigned ktype : 4;
	unsigned vtype : 4;
	char *val;
	uint32_t vsize; /*value size*/
	uint32_t hash;
	struct ngx_http_clojure_hashmap_entry_s * next;
} NGX_CLOJURE_ATTR_MAY_ALIAS ngx_http_clojure_hashmap_entry_t;


typedef struct {
	ngx_atomic_uint_t size;
	ngx_http_clojure_hashmap_entry_t **table;
} ngx_http_clojure_hashmap_t;

typedef struct {
	uint32_t entry_table_size;
	uint64_t space_size;
	uint32_t hash_seed;
	ngx_http_clojure_hashmap_t *map;
	ngx_slab_pool_t *shpool;
} ngx_http_clojure_shared_map_hashmap_ctx_t;


#define rotate_left(u32, l) ((u32 << l) | (u32 >> (32-l)))

#define round_up_to_power_of_2(a) \
	a--; \
	a |= a >> 1; \
	a |= a >> 2; \
	a |= a >> 4; \
	a |= a >> 8; \
	a |= a >> 16; \
	a++

/*table_len MUST be non-zero power of 2*/
#define index_for(hash, table_len) ((hash) & (table_len - 1))


uint32_t murmur3_32(uint32_t seed, const u_char *data, uint32_t offset, uint32_t len);

#define argstr_equals(ngxstr, s2) (ngxstr.len == sizeof(s2) - 1 \
		&& !ngx_strncmp(ngxstr.data, s2, sizeof(s2)-1))

#define rehash_with_seed(h, seed) \
	h ^= seed; \
	h ^= (h >> 20) ^ (h >> 12); \
	h ^= (h >> 7) ^ (h >> 4);


#define compute_hash(ctx, ktype, key, klen, /*out*/hash) \
		switch (ktype) {\
		case NGX_CLOJURE_SHARED_MAP_JINT:\
			hash = *((uint32_t*)key);\
			rehash_with_seed(hash, ctx->hash_seed);\
			break;\
		case NGX_CLOJURE_SHARED_MAP_JLONG:\
			hash = (uint32_t)((*((uint64_t*)key) ^ (*((uint64_t*)key) >> 32)));\
			rehash_with_seed(hash, ctx->hash_seed);\
			break;\
		case NGX_CLOJURE_SHARED_MAP_JSTRING:\
		case NGX_CLOJURE_SHARED_MAP_JBYTEA:\
			hash = murmur3_32(ctx->hash_seed, key, 0, klen);\
			break;\
		default:\
			return NGX_CLOJURE_SHARED_MAP_INVLAID_KEY_TYPE;\
		}

ngx_int_t ngx_http_clojure_shared_map_hashmap_init(ngx_conf_t *cf, ngx_http_clojure_shared_map_ctx_t *ctx);

ngx_int_t ngx_http_clojure_shared_map_hashmap_get_entry(ngx_http_clojure_shared_map_ctx_t *ctx, uint8_t ktype,
		const u_char *key, size_t klen, ngx_http_clojure_shared_map_val_handler val_handler, void *handler_data);

ngx_int_t ngx_http_clojure_shared_map_hashmap_put_entry(ngx_http_clojure_shared_map_ctx_t *ctx, uint8_t ktype,
		const u_char *key, size_t klen, uint8_t vtype, const void *val, size_t vlen,
		ngx_http_clojure_shared_map_val_handler val_handler, void *handler_data);

ngx_int_t ngx_http_clojure_shared_map_hashmap_put_entry_if_absent(ngx_http_clojure_shared_map_ctx_t *ctx, uint8_t ktype,
		const u_char *key, size_t klen, uint8_t vtype, const void *val, size_t vlen,
		ngx_http_clojure_shared_map_val_handler val_handler, void *handler_data);

ngx_int_t ngx_http_clojure_shared_map_hashmap_remove_entry(ngx_http_clojure_shared_map_ctx_t *ctx, uint8_t ktype,
		const u_char *key, size_t len, ngx_http_clojure_shared_map_val_handler val_handler, void *handler_data);

ngx_int_t ngx_http_clojure_shared_map_hashmap_size(ngx_http_clojure_shared_map_ctx_t  *sctx);

ngx_int_t ngx_http_clojure_shared_map_hashmap_clear(ngx_http_clojure_shared_map_ctx_t  *sctx);

ngx_int_t ngx_http_clojure_shared_map_hashmap_visit(ngx_http_clojure_shared_map_ctx_t  *sctx,
    ngx_http_clojure_shared_map_visit_handler visit_handler, void * handler_data);

#endif /* NGX_HTTP_CLOJURE_SHARED_MAP_HASHMAP_H_ */
