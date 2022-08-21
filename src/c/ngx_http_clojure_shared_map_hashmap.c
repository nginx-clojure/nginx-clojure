/*
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 */

#include <ngx_config.h>
#include "ngx_http_clojure_mem.h"
#include "ngx_http_clojure_shared_map_hashmap.h"

static ngx_int_t ngx_http_clojure_shared_map_hashmap_init_zone(ngx_shm_zone_t *shm_zone, void *data);

uint32_t murmur3_32(uint32_t seed, const u_char *data, uint32_t offset, uint32_t len) {
	uint32_t h1 = seed;
	uint32_t nblocks = len / 4;
	const uint32_t *blocks = (const uint32_t *) data;
	uint32_t i;
	uint32_t k1;

	/* body */
	for (i = 0; i < nblocks; i++) {
		k1 = blocks[i];

		k1 *= 0xcc9e2d51;
		k1 = rotate_left(k1, 15);
		k1 *= 0x1b873593;

		h1 ^= k1;
		h1 = rotate_left(h1, 13);
		h1 = h1 * 5 + 0xe6546b64;
	}

	k1 = 0;
	i = len;
	/* tail */
	switch(len & 3) {
		case 3:
			k1 ^= data[--i] << 16;
			/* no break */
			/*fallthrough*/
		case 2:
			k1 ^= data[--i] << 8;
			/* no break */
			/*fallthrough*/
		case 1:
			k1 ^= data[--i];
			k1 *= 0xcc9e2d51;
			k1 = rotate_left(k1, 15);
			k1 *= 0x1b873593;
			h1 ^= k1;
			/* no break */
			/*fallthrough*/
	}

	h1 ^= len;

	/* finalization mix force all bits of a hash block to avalanche */
	h1 ^= h1 >> 16;
	h1 *= 0x85ebca6b;
	h1 ^= h1 >> 13;
	h1 *= 0xc2b2ae35;
	h1 ^= h1 >> 16;

	return h1;
}

ngx_int_t ngx_http_clojure_shared_map_hashmap_init(ngx_conf_t *cf, ngx_http_clojure_shared_map_ctx_t *ctx) {
	ngx_table_elt_t* arg = ctx->arguments->elts;
	ngx_uint_t i;
	ssize_t size;
	ngx_http_clojure_shared_map_hashmap_ctx_t *hmctx;
	ngx_shm_zone_t *shm_zone;

	ctx->impl_ctx = hmctx = ngx_pcalloc(cf->pool, sizeof(ngx_http_clojure_shared_map_hashmap_ctx_t));

	if (hmctx == NULL) {
		return NGX_ERROR;
	}

	for (i = 0; i < ctx->arguments->nelts; i++) {
		if (argstr_equals(arg->key, "space")) {
			size = ngx_parse_size(&arg->value);
			if (size == NGX_ERROR) {
				ngx_conf_log_error(NGX_LOG_EMERG, cf, 0, "invalid shared map argument: space \"%V\"", &arg->value);
				return NGX_ERROR ;
			} else if (size < (ssize_t) (8 * ngx_pagesize)) {
				size = (ssize_t) (8 * ngx_pagesize);
				ngx_conf_log_error(NGX_LOG_WARN, cf, 0, "space is too small, adjust to %ud, old is \"%V\"", size, &arg->value);
			}
			hmctx->space_size = (uint64_t) size;
		} else if (argstr_equals(arg->key, "entries")) {
			size = ngx_parse_size(&arg->value);
			if (size == NGX_ERROR) {
				ngx_conf_log_error(NGX_LOG_EMERG, cf, 0, "invalid shared map argument: entries \"%V\"", &arg->value);
				return NGX_ERROR ;
			} else if ((uint64_t)size > (uint64_t)0x80000000LL) { /*so far we have not supported > 2G entries*/
				ngx_conf_log_error(NGX_LOG_EMERG, cf, 0, "invalid shared map argument: entries is too large (at most %ll) \"%V\"",
						0x80000000LL, &arg->value);
				return NGX_ERROR ;
			}
			hmctx->entry_table_size = (uint32_t) size;
		} else {
			ngx_log_error(NGX_LOG_EMERG, ctx->log, 0, "invalid shared map argument : \"%V\"", &arg->key);
			return NGX_ERROR ;
		}
		arg++;
	}

	shm_zone = ngx_shared_memory_add(cf, &ctx->name, hmctx->space_size, &ngx_http_clojure_module);

	if (shm_zone == NULL) {
		return NGX_ERROR;
	}

	if (shm_zone->data) {
		ngx_conf_log_error(NGX_LOG_EMERG, cf, 0,
				"\"%V\" is already bound to key \"%V\"", &ctx->name, &((ngx_http_clojure_shared_map_ctx_t *)shm_zone->data)->name);
		return NGX_ERROR;
	}

	shm_zone->init = ngx_http_clojure_shared_map_hashmap_init_zone;
	shm_zone->data = hmctx;
	return NGX_OK;
}

static ngx_int_t ngx_http_clojure_shared_map_hashmap_init_zone(ngx_shm_zone_t *shm_zone, void *data) {
	ngx_http_clojure_shared_map_hashmap_ctx_t *octx = data;
	ngx_http_clojure_shared_map_hashmap_ctx_t *ctx = shm_zone->data;
	size_t len;

	if (octx) {
		ctx->map = octx->map;
		ctx->shpool = octx->shpool;
		ctx->hash_seed = octx->hash_seed;
		ctx->entry_table_size = octx->entry_table_size;
		return NGX_OK;
	}

	ctx->shpool = (ngx_slab_pool_t *)shm_zone->shm.addr;

	if (shm_zone->shm.exists) {
		ctx->map = ctx->shpool->data;
		return NGX_OK;
	}

	ctx->entry_table_size = len =  ctx->entry_table_size * 100 / 75;
	if (len < 8) {
		len = 8;
	}else {
		round_up_to_power_of_2(/*modified*/len);
		if (ctx->entry_table_size - (len >> 1)  < len - ctx->entry_table_size) {
			len >>= 1;
		}
	}
	ctx->entry_table_size = len;
	len = sizeof(" in hashmap_zone \"\"") + shm_zone->shm.name.len;

	ctx->map = ngx_slab_alloc(ctx->shpool,
			sizeof(ngx_http_clojure_hashmap_t)
			+ sizeof(ngx_http_clojure_hashmap_entry_t *) * ctx->entry_table_size
			+ len);
	if (ctx->map == NULL) {
		return NGX_ERROR;
	}

	ctx->shpool->data = ctx->map;
	ctx->map->size = 0;
/*	ctx->hash_seed = (uint32_t)ngx_pid | (uintptr_t)ctx->shpool;*/
	ctx->hash_seed = 1;

	ctx->map->table = (void*)((uintptr_t)ctx->map + sizeof(ngx_http_clojure_hashmap_t));
	ngx_memzero(ctx->map->table, sizeof(ngx_http_clojure_hashmap_entry_t *) * ctx->entry_table_size);

	ctx->shpool->log_ctx = (void*)((uintptr_t)ctx->map->table + sizeof(ngx_http_clojure_hashmap_entry_t *) * ctx->entry_table_size);

	ngx_sprintf(ctx->shpool->log_ctx, " in hashmap_zone \"%V\"%Z",
			&shm_zone->shm.name);
	return NGX_OK;
}



static void ngx_http_clojure_shared_map_hashmap_invoke_value_handler_helper(ngx_http_clojure_hashmap_entry_t *entry,
		ngx_http_clojure_shared_map_val_handler val_handler, void *handler_data) {
	switch (entry->vtype) {
	case NGX_CLOJURE_SHARED_MAP_JINT:
		val_handler(NGX_CLOJURE_SHARED_MAP_JINT, &entry->val, 4, handler_data);
		return;
	case NGX_CLOJURE_SHARED_MAP_JLONG:
		val_handler(NGX_CLOJURE_SHARED_MAP_JLONG, &entry->val, 8, handler_data);
		return;
	case NGX_CLOJURE_SHARED_MAP_JSTRING:
	case NGX_CLOJURE_SHARED_MAP_JBYTEA:
		val_handler(NGX_CLOJURE_SHARED_MAP_JSTRING, entry->val, entry->vsize, handler_data);
		return;
	}
}

static ngx_int_t ngx_http_clojure_shared_map_hashmap_invoke_visit_handler_helper(ngx_http_clojure_hashmap_entry_t *entry,
    ngx_http_clojure_shared_map_visit_handler visit_handler, void *handler_data) {
  const void *key;
  size_t ksize;
  const void *val;
  size_t vsize;

  switch (entry->ktype) {
  case NGX_CLOJURE_SHARED_MAP_JINT:
    key =  &entry->key;
    ksize = 4;
    break;
  case NGX_CLOJURE_SHARED_MAP_JLONG:
    key = &entry->key;
    ksize = 8;
    break;
  case NGX_CLOJURE_SHARED_MAP_JSTRING:
  case NGX_CLOJURE_SHARED_MAP_JBYTEA:
    key = entry->key;
    ksize = entry->ksize;
    break;
  default:
    return NGX_CLOJURE_SHARED_MAP_INVLAID_KEY_TYPE;
  }

  switch (entry->vtype) {
  case NGX_CLOJURE_SHARED_MAP_JINT:
    val =  &entry->val;
    vsize = 4;
    break;
  case NGX_CLOJURE_SHARED_MAP_JLONG:
    val = &entry->val;
    vsize = 8;
    break;
  case NGX_CLOJURE_SHARED_MAP_JSTRING:
  case NGX_CLOJURE_SHARED_MAP_JBYTEA:
    val = entry->val;
    vsize = entry->vsize;
    break;
  default:
    return NGX_CLOJURE_SHARED_MAP_INVLAID_VALUE_TYPE;
  }

  return visit_handler(entry->ktype, key, ksize, entry->vtype, val, vsize, handler_data);
}

static ngx_int_t ngx_http_clojure_shared_map_hashmap_set_key_helper(ngx_slab_pool_t *shpool, ngx_http_clojure_hashmap_entry_t *entry,
		const void *key, size_t klen) {

	void *ek = &entry->key; /* *((uint64_t *)(void*)&entry->key) will cause gcc 4.4 warning*/
	switch (entry->ktype) {
	case NGX_CLOJURE_SHARED_MAP_JINT:
		*((uint32_t *)ek) = *((uint32_t *)key);
		return NGX_CLOJURE_SHARED_MAP_OK;
	case NGX_CLOJURE_SHARED_MAP_JLONG:
		*((uint64_t *)ek) = *((uint64_t *) key);
		return NGX_CLOJURE_SHARED_MAP_OK;
	case NGX_CLOJURE_SHARED_MAP_JSTRING:
	case NGX_CLOJURE_SHARED_MAP_JBYTEA:
		entry->key = ngx_slab_alloc_locked(shpool, klen);
		if (entry->key == NULL) {
			return NGX_CLOJURE_SHARED_MAP_OUT_OF_MEM;
		}
		ngx_memcpy(entry->key, key, klen);
		entry->ksize = klen;
		return NGX_CLOJURE_SHARED_MAP_OK;
	default:
		return NGX_CLOJURE_SHARED_MAP_INVLAID_VALUE_TYPE;
	}
}

static ngx_int_t ngx_http_clojure_shared_map_hashmap_set_value_helper(ngx_slab_pool_t *shpool, ngx_http_clojure_hashmap_entry_t *entry,
		uint8_t vtype, const void *val, size_t vlen, ngx_http_clojure_shared_map_val_handler old_handler, void *handler_data) {
	void* oldv = NULL;
	size_t oldv_size = 0;
	void *ev = &entry->val; /* *((uint64_t *)(void*)&entry->val) will cause gcc 4.4 warning*/

	switch (entry->vtype) {
	case NGX_CLOJURE_SHARED_MAP_JINT:
		if (old_handler) {
			old_handler(NGX_CLOJURE_SHARED_MAP_JINT, &entry->val, 4, handler_data);
		}
		break;
	case NGX_CLOJURE_SHARED_MAP_JLONG:
		if (old_handler) {
			old_handler(NGX_CLOJURE_SHARED_MAP_JLONG, &entry->val, 8, handler_data);
		}
		break;
	case NGX_CLOJURE_SHARED_MAP_JSTRING:
	case NGX_CLOJURE_SHARED_MAP_JBYTEA:
		if (old_handler) {
			oldv = entry->val;
			oldv_size = entry->vsize;
		}else if (entry->val){
			ngx_slab_free_locked(shpool, entry->val);
		}
		break;
	default:
		return NGX_CLOJURE_SHARED_MAP_INVLAID_VALUE_TYPE;
	}

	switch (vtype) {
	case NGX_CLOJURE_SHARED_MAP_JINT:
		*((uint32_t *)ev) = *((uint32_t *)val);
		goto HANDLE_CPX_OLDV;
	case NGX_CLOJURE_SHARED_MAP_JLONG:
		*((uint64_t *)ev) = *((uint64_t *) val);
		goto HANDLE_CPX_OLDV;
	case NGX_CLOJURE_SHARED_MAP_JSTRING:
	case NGX_CLOJURE_SHARED_MAP_JBYTEA:
		entry->val = ngx_slab_alloc_locked(shpool, vlen);
		if (entry->val == NULL) {
			return NGX_CLOJURE_SHARED_MAP_OUT_OF_MEM;
		}
		ngx_memcpy(entry->val, val, vlen);
		entry->vsize = vlen;
		goto HANDLE_CPX_OLDV;
	default:
		return NGX_CLOJURE_SHARED_MAP_INVLAID_VALUE_TYPE;
	}

HANDLE_CPX_OLDV:
	if (old_handler && oldv) {
		old_handler(entry->vtype, oldv, oldv_size, handler_data);
		ngx_slab_free_locked(shpool, oldv);
	}
	entry->vtype = vtype;
	return NGX_CLOJURE_SHARED_MAP_OK;
}



static ngx_int_t ngx_http_clojure_shared_map_hashmap_match_key(uint8_t ktype,
		const u_char *key, size_t klen, uint32_t hash,
		ngx_http_clojure_hashmap_entry_t *entry) {
	void *ek = &entry->key; /* *((uint64_t *)(void*)&entry->key) will cause gcc 4.4 warning*/
	if (ktype != entry->ktype) {
		return NGX_CLOJURE_SHARED_MAP_NOT_FOUND;
	}
	switch (ktype) {
	case NGX_CLOJURE_SHARED_MAP_JINT:
		if (*((uint32_t *)ek) == *((uint32_t*) key)) {
			return NGX_CLOJURE_SHARED_MAP_OK;
		}
		break;
	case NGX_CLOJURE_SHARED_MAP_JLONG:
		if (*((uint64_t*)ek) == *((uint64_t*) key)) {
			return NGX_CLOJURE_SHARED_MAP_OK;
		}
		break;
	case NGX_CLOJURE_SHARED_MAP_JSTRING:
	case NGX_CLOJURE_SHARED_MAP_JBYTEA:
		if (hash == entry->hash && klen == entry->ksize && !ngx_strncmp(key, entry->key, klen)) {
			return NGX_CLOJURE_SHARED_MAP_OK;
		}
		break;
	default:
		return NGX_CLOJURE_SHARED_MAP_INVLAID_VALUE_TYPE;
	}
	return NGX_CLOJURE_SHARED_MAP_NOT_FOUND;
}

/**
 * returns NGX_CLOJURE_SHARED_MAP_OK if key is found otherwise returns NGX_CLOJURE_SHARED_MAP_NOT_FOUND
 */
ngx_int_t ngx_http_clojure_shared_map_hashmap_get_entry(ngx_http_clojure_shared_map_ctx_t *sctx, uint8_t ktype,
		const u_char *key, size_t klen, ngx_http_clojure_shared_map_val_handler val_handler, void *handler_data) {
	ngx_http_clojure_shared_map_hashmap_ctx_t *ctx = sctx->impl_ctx;
	uint32_t hash;
	ngx_http_clojure_hashmap_entry_t *entry;
	ngx_int_t rc = NGX_CLOJURE_SHARED_MAP_NOT_FOUND;

	compute_hash(ctx, ktype, key, klen, hash);

	ngx_shmtx_lock(&ctx->shpool->mutex);

	for (entry =  ctx->map->table[index_for(hash, ctx->entry_table_size)];
			entry != NULL;
			entry = entry->next) {
		if ((rc = ngx_http_clojure_shared_map_hashmap_match_key(ktype, key, klen, hash, entry)) == NGX_CLOJURE_SHARED_MAP_OK) {
			if (val_handler) {
			    	ngx_http_clojure_shared_map_hashmap_invoke_value_handler_helper(entry, val_handler,
			    			handler_data);
			}
			break;
		}
	}

	ngx_shmtx_unlock(&ctx->shpool->mutex);
	return rc;
}


ngx_int_t ngx_http_clojure_shared_map_hashmap_put_entry(ngx_http_clojure_shared_map_ctx_t *sctx, uint8_t ktype,
		const u_char *key, size_t klen, uint8_t vtype, const void *val, size_t vlen,
		ngx_http_clojure_shared_map_val_handler old_val_handler, void *handler_data) {
	ngx_http_clojure_shared_map_hashmap_ctx_t *ctx = sctx->impl_ctx;
	ngx_http_clojure_hashmap_entry_t **pentry;
	ngx_http_clojure_hashmap_entry_t *entry;
	ngx_int_t rc = NGX_CLOJURE_SHARED_MAP_NOT_FOUND;
	uint32_t hash;

	compute_hash(ctx, ktype, key, klen, hash);

	ngx_shmtx_lock(&ctx->shpool->mutex);
	for (pentry = &ctx->map->table[index_for(hash, ctx->entry_table_size)];
			(entry = *pentry) != NULL;
			pentry = (void*)&entry->next) {
		if (NGX_CLOJURE_SHARED_MAP_OK == (rc = ngx_http_clojure_shared_map_hashmap_match_key(ktype, key, klen, hash, entry))) {
			rc = ngx_http_clojure_shared_map_hashmap_set_value_helper(ctx->shpool, entry, vtype, val, vlen, old_val_handler, handler_data);
			ngx_shmtx_unlock(&ctx->shpool->mutex);
			return rc;
		}
	}

	entry = ngx_slab_alloc_locked(ctx->shpool, sizeof(ngx_http_clojure_hashmap_entry_t));
	if (entry == NULL) {
		ngx_shmtx_unlock(&ctx->shpool->mutex);
		return NGX_CLOJURE_SHARED_MAP_OUT_OF_MEM;
	}
	entry->next = NULL;
	entry->hash = hash;
	entry->vtype = vtype;
	entry->ktype = ktype;
	entry->val = NULL;

	rc = ngx_http_clojure_shared_map_hashmap_set_key_helper(ctx->shpool, entry, key, klen);
	if (rc != NGX_CLOJURE_SHARED_MAP_OK) {
		ngx_slab_free_locked(ctx->shpool, entry);
		ngx_shmtx_unlock(&ctx->shpool->mutex);
		return rc;
	}

	rc = ngx_http_clojure_shared_map_hashmap_set_value_helper(ctx->shpool, entry, vtype, val, vlen, NULL, NULL);

	if (rc != NGX_CLOJURE_SHARED_MAP_OK) {
		ngx_slab_free_locked(ctx->shpool, entry->key);
		ngx_slab_free_locked(ctx->shpool, entry);
		ngx_shmtx_unlock(&ctx->shpool->mutex);
		return rc;
	}

	*pentry = entry;
	(void)ngx_atomic_fetch_add(&ctx->map->size, 1);
	ngx_shmtx_unlock(&ctx->shpool->mutex);

	return NGX_CLOJURE_SHARED_MAP_NOT_FOUND;
}

ngx_int_t ngx_http_clojure_shared_map_hashmap_put_entry_if_absent(ngx_http_clojure_shared_map_ctx_t *sctx, uint8_t ktype,
		const u_char *key, size_t klen, uint8_t vtype, const void *val, size_t vlen,
		ngx_http_clojure_shared_map_val_handler old_val_handler, void *handler_data) {
	ngx_http_clojure_shared_map_hashmap_ctx_t *ctx = sctx->impl_ctx;
	ngx_http_clojure_hashmap_entry_t **pentry;
	ngx_http_clojure_hashmap_entry_t *entry;
	ngx_int_t rc = NGX_CLOJURE_SHARED_MAP_NOT_FOUND;
	uint32_t hash;

	compute_hash(ctx, ktype, key, klen, hash);

	ngx_shmtx_lock(&ctx->shpool->mutex);
	for (pentry = &ctx->map->table[index_for(hash, ctx->entry_table_size)];
			(entry = *pentry) != NULL;
			pentry = (void*)&entry->next) {
		if (NGX_CLOJURE_SHARED_MAP_OK == (rc = ngx_http_clojure_shared_map_hashmap_match_key(ktype, key, klen, hash, entry))) {
			if (old_val_handler) {
				ngx_http_clojure_shared_map_hashmap_invoke_value_handler_helper(entry, old_val_handler, handler_data);
			}
			ngx_shmtx_unlock(&ctx->shpool->mutex);
			return rc;
		}
	}

	entry = ngx_slab_alloc_locked(ctx->shpool, sizeof(ngx_http_clojure_hashmap_entry_t));
	if (entry == NULL) {
		ngx_shmtx_unlock(&ctx->shpool->mutex);
		return NGX_CLOJURE_SHARED_MAP_OUT_OF_MEM;
	}
	entry->next = NULL;
	entry->hash = hash;
	entry->vtype = vtype;
	entry->ktype = ktype;
	entry->val = NULL;

	rc = ngx_http_clojure_shared_map_hashmap_set_key_helper(ctx->shpool, entry, key, klen);
	if (rc != NGX_CLOJURE_SHARED_MAP_OK) {
		ngx_slab_free_locked(ctx->shpool, entry);
		ngx_shmtx_unlock(&ctx->shpool->mutex);
		return rc;
	}

	rc = ngx_http_clojure_shared_map_hashmap_set_value_helper(ctx->shpool, entry, vtype, val, vlen, NULL, NULL);

	if (rc != NGX_CLOJURE_SHARED_MAP_OK) {
		ngx_slab_free_locked(ctx->shpool, entry->key);
		ngx_slab_free_locked(ctx->shpool, entry);
		ngx_shmtx_unlock(&ctx->shpool->mutex);
		return rc;
	}

	*pentry = entry;
	(void)ngx_atomic_fetch_add(&ctx->map->size, 1);
	ngx_shmtx_unlock(&ctx->shpool->mutex);

	return NGX_CLOJURE_SHARED_MAP_NOT_FOUND;
}

/**
 * returns NGX_CLOJURE_SHARED_MAP_OK if key is found otherwise returns NGX_CLOJURE_SHARED_MAP_NOT_FOUND
 */
ngx_int_t ngx_http_clojure_shared_map_hashmap_remove_entry(ngx_http_clojure_shared_map_ctx_t *sctx, uint8_t ktype,
		const u_char *key, size_t klen, ngx_http_clojure_shared_map_val_handler val_handler, void *handler_data) {
	ngx_http_clojure_shared_map_hashmap_ctx_t *ctx = sctx->impl_ctx;
	ngx_http_clojure_hashmap_entry_t **pentry;
	ngx_http_clojure_hashmap_entry_t *entry;
	uint32_t hash;
	ngx_int_t rc = NGX_CLOJURE_SHARED_MAP_NOT_FOUND;

	compute_hash(ctx, ktype, key, klen, hash);

	ngx_shmtx_lock(&ctx->shpool->mutex);
	for (pentry =  &ctx->map->table[index_for(hash, ctx->entry_table_size)];
			(entry = *pentry) != NULL;
			pentry = (void*)&entry->next) {
		if (NGX_CLOJURE_SHARED_MAP_OK == (rc = ngx_http_clojure_shared_map_hashmap_match_key(ktype, key, klen, hash, entry))) {
			if (val_handler) {
				ngx_http_clojure_shared_map_hashmap_invoke_value_handler_helper(entry, val_handler, handler_data);
			}
			*pentry = entry->next;
			(void)ngx_atomic_fetch_add(&ctx->map->size, -1);

			if (entry->ktype >= NGX_CLOJURE_SHARED_MAP_JSTRING) {
				ngx_slab_free_locked(ctx->shpool, entry->key);
			}

			if (entry->vtype >= NGX_CLOJURE_SHARED_MAP_JSTRING) {
				ngx_slab_free_locked(ctx->shpool, entry->val);
			}

			ngx_slab_free_locked(ctx->shpool, entry);
			break;
		}
	}

	ngx_shmtx_unlock(&ctx->shpool->mutex);
	return rc;
}


ngx_int_t ngx_http_clojure_shared_map_hashmap_size(ngx_http_clojure_shared_map_ctx_t * sctx) {
	 return ((ngx_http_clojure_shared_map_hashmap_ctx_t *)sctx->impl_ctx)->map->size;
}

ngx_int_t ngx_http_clojure_shared_map_hashmap_clear(ngx_http_clojure_shared_map_ctx_t * sctx) {
	ngx_http_clojure_shared_map_hashmap_ctx_t *ctx = sctx->impl_ctx;
	u_char tmp_name[NGX_CLOJURE_SHARED_MAP_NAME_MAX_LEN+1];
	ngx_str_t log_ctx_name;


	ngx_shmtx_lock(&ctx->shpool->mutex);

	if (ctx->map->size == 0) {
		ngx_shmtx_unlock(&ctx->shpool->mutex);
		return NGX_OK;
	}

	log_ctx_name.len = ngx_strlen(ctx->shpool->log_ctx);
	log_ctx_name.data = tmp_name;

	ngx_memcpy(log_ctx_name.data, ctx->shpool->log_ctx, log_ctx_name.len);
#if nginx_version >= 1005013
	ctx->shpool->log_nomem = 0;
#endif
	ctx->shpool->min_size = 0;
	ctx->shpool->start = ctx->shpool->data = NULL;
#if nginx_version >= 1007002
	ctx->shpool->last = NULL;
#endif
	ctx->shpool->pages = NULL;
	ngx_memzero(&ctx->shpool->free, sizeof(ngx_slab_page_t));
	ngx_slab_init(ctx->shpool);

	ctx->map = ngx_slab_alloc_locked(ctx->shpool,
			sizeof(ngx_http_clojure_hashmap_t)
			+ sizeof(ngx_http_clojure_hashmap_entry_t *) * ctx->entry_table_size
			+ log_ctx_name.len + 1);
	if (ctx->map == NULL) {
		ngx_shmtx_unlock(&ctx->shpool->mutex);
		return NGX_ERROR;
	}

	ctx->shpool->data = ctx->map;
	ctx->map->size = 0;
/*	ctx->hash_seed = (uint32_t)ngx_pid | (uintptr_t)ctx->shpool;*/
	ctx->hash_seed = 1;

	ctx->map->table = (void*)((uintptr_t)ctx->map + sizeof(ngx_http_clojure_hashmap_t));
	ngx_memzero(ctx->map->table, sizeof(ngx_http_clojure_hashmap_entry_t *) * ctx->entry_table_size);
	ctx->shpool->log_ctx = (void*)((uintptr_t)ctx->map->table + sizeof(ngx_http_clojure_hashmap_entry_t *) * ctx->entry_table_size);

	ngx_sprintf(ctx->shpool->log_ctx, " in hashmap_zone \"%V\"%Z",
			&log_ctx_name);

	ngx_shmtx_unlock(&ctx->shpool->mutex);
	return NGX_OK;
}

ngx_int_t ngx_http_clojure_shared_map_hashmap_visit(ngx_http_clojure_shared_map_ctx_t  *sctx,
    ngx_http_clojure_shared_map_visit_handler visit_handler, void * handler_data) {
  ngx_http_clojure_shared_map_hashmap_ctx_t *ctx = sctx->impl_ctx;
  ngx_http_clojure_hashmap_entry_t *entry;
  uint32_t i;
  ngx_shmtx_lock(&ctx->shpool->mutex);
  for (i = 0; i < ctx->entry_table_size; i++) {
    entry = ctx->map->table[i];
    while (entry) {
      if (ngx_http_clojure_shared_map_hashmap_invoke_visit_handler_helper(entry, visit_handler, handler_data)) {
        goto DONE;
      }
      entry = entry->next;
    }
  }
DONE:
  ngx_shmtx_unlock(&ctx->shpool->mutex);
  return NGX_OK;
}
