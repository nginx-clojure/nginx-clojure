/*
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import nginx.clojure.HackUtils;
import nginx.clojure.MiniConstants;
import nginx.clojure.NginxClojureRT;

public class NginxSharedHashMap<K, V> implements ConcurrentMap<K, V>{

	public final static int NGX_CLOJURE_SHARED_MAP_OK = 0;
	public final static int NGX_CLOJURE_SHARED_MAP_OUT_OF_MEM = 1;
	public final static int NGX_CLOJURE_SHARED_MAP_NOT_FOUND = 2;
	public final static int NGX_CLOJURE_SHARED_MAP_INVLAID_KEY_TYPE = 3;
	public final static int NGX_CLOJURE_SHARED_MAP_INVLAID_VALUE_TYPE = 4;


	public final static int NGX_CLOJURE_SHARED_MAP_JINT = 0;
	public final static int NGX_CLOJURE_SHARED_MAP_JLONG = 1;
	public final static int NGX_CLOJURE_SHARED_MAP_JSTRING = 2;
	public final static int NGX_CLOJURE_SHARED_MAP_JBYTEA = 3;
	public final static int NGX_CLOJURE_SHARED_MAP_JOBJECT = 4;
	
	private long ctx;
	private String name;
	private long nullVal = 0;
	
	//shared hashmap 
	private native static long ngetMap(Object nameBuf, long offset, long len);
	
	private native static Object nget(long ctx, int ktype, Object keyBuf, long offset, long len);
	
	private native static Object nput(long ctx, int ktype, Object keyBuf, long keyOffset, long keyLen, int vtype, Object valBuf, long valueOffset, long valLen);
	
	private native static Object nputIfAbsent(long ctx, int ktype, Object keyBuf, long keyOffset, long keyLen, int vtype, Object valBuf, long valueOffset, long valLen);
	
	private native static Object nremove(long ctx, int ktype, Object keyBuf, long offset, long len);
	
	private native static long ndelete(long ctx, int ktype, Object keyBuf, long offset, long len);
	
	private native static long nsize(long ctx);
	
	private native static long nclear(long ctx);
	
	private native static long ncontains(long ctx, int ktype, Object keyBuf, long offset, long len);
	
	private native static long ngetNumber(long ctx, int ktype, Object keyBuf, long offset, long len, int vtype);
	
	private native static long nputNumber(long ctx, int ktype, Object keyBuf, long keyOffset, long keyLen, int vtype, long val, long nullVal);
	
	private native static long nputNumberIfAbsent(long ctx, int ktype, Object keyBuf, long keyOffset, long keyLen, int vtype, long val, long nullVal);

	private native static long nremoveNumber(long ctx, int ktype, Object keyBuf, long offset, long len, int vtype, long nullVal);

	private native static long natomicAddNumber(long ctx, int ktype, Object keyBuf, long offset, long len, int vtype, long delta);
	
	private native static long nvisit(long ctx, @SuppressWarnings("rawtypes") SharedMapSimpleVisitor visitor);

	private NginxSharedHashMap() {
	}
	
	public NginxSharedHashMap(String name) {
		ByteBuffer bb = HackUtils.encode(name, MiniConstants.DEFAULT_ENCODING, NginxClojureRT.pickByteBuffer());
		long ctx = ngetMap(bb.array(), MiniConstants.BYTE_ARRAY_OFFSET, bb.remaining());
		if (ctx == 0) {
			throw new RuntimeException("can not find shared map whose name is " + name);
		}
		this.name = name;
		this.ctx = ctx;
	}

	
	private final static Object native2JavaObject(int type, long addr, long size) {
		switch (type) {
		case NGX_CLOJURE_SHARED_MAP_JINT:
			return HackUtils.UNSAFE.getInt(addr);
		case NGX_CLOJURE_SHARED_MAP_JLONG:
			return HackUtils.UNSAFE.getLong(addr);
		case NGX_CLOJURE_SHARED_MAP_JSTRING:
			return NginxClojureRT.fetchDString(addr, (int)size);
		case NGX_CLOJURE_SHARED_MAP_JBYTEA:
			byte[] ba = new byte[(int)size];
			NginxClojureRT.ngx_http_clojure_mem_copy_to_obj(addr, ba, MiniConstants.BYTE_ARRAY_OFFSET, size);
			return ba;
		default:
			//TODO: POJO deserialization
			throw new RuntimeException("unsupported type:" + type);
		}
	}
	
	public interface SharedMapSimpleVisitor<K, V> {
		int visit(K key, V val);
	}
	
	private final static int visit(int ktype, long kaddr, long ksize, int vtype, long vaddr, long vsize, SharedMapSimpleVisitor<Object, Object> visitor) {
		return visitor.visit(native2JavaObject(ktype, kaddr, ksize), native2JavaObject(vtype, vaddr, vsize));
	}
	
	public static <KT, VT> NginxSharedHashMap<KT, VT> build(String name) {
		return new NginxSharedHashMap<>(name);
	}
	
	public void setNullVal(long nullVal) {
		this.nullVal = nullVal;
	}

	@Override
	public int size() {
		return (int) nsize(ctx);
	}


	@Override
	public boolean isEmpty() {
		return size() == 0;
	}


	@Override
	public boolean containsKey(Object key) {
		int ktype = buildType(key);
		ByteBuffer kb = buildKeyBuffer(ktype, key);
		return ncontains(ctx, ktype, kb.array(), MiniConstants.BYTE_ARRAY_OFFSET, kb.remaining())
				== NGX_CLOJURE_SHARED_MAP_OK;
	}


	@Override
	public boolean containsValue(Object value) {
		throw new UnsupportedOperationException("containsValue");
	}
	
	private int buildType(Object o) {
		if (o == null) {
			throw new NullPointerException("null object not supported!");
		}
		if (o instanceof Integer) {
			return NGX_CLOJURE_SHARED_MAP_JINT;
		} else if (o instanceof Long) {
			return NGX_CLOJURE_SHARED_MAP_JLONG;
		} else if (o instanceof String) {
			return NGX_CLOJURE_SHARED_MAP_JSTRING;
		} else if (o instanceof byte[]) {
			return NGX_CLOJURE_SHARED_MAP_JBYTEA;
		} else {
			throw new UnsupportedOperationException("type " + o.getClass() + " not supported!");
		}
	}

	private ByteBuffer buildKeyBuffer(int ktype, Object key) {
		ByteBuffer kb = NginxClojureRT.pickByteBuffer();
		switch(ktype) {
		case NGX_CLOJURE_SHARED_MAP_JINT:
			Integer ik = (Integer) key;
			kb.order(ByteOrder.nativeOrder());
			kb.putInt(ik);
			kb.flip();
			break;
		case NGX_CLOJURE_SHARED_MAP_JLONG:
			Long lk = (Long)key;
			kb.order(ByteOrder.nativeOrder());
			kb.putLong(lk);
			kb.flip();
			break;
		case NGX_CLOJURE_SHARED_MAP_JSTRING:
			kb = HackUtils.encode((String)key, MiniConstants.DEFAULT_ENCODING, kb);
			break;
		case NGX_CLOJURE_SHARED_MAP_JBYTEA:
			kb = ByteBuffer.wrap((byte[])key);
			break;
		default:
			//TODO: POJO serialization
			throw new UnsupportedOperationException("key type " + ktype + " not supported!");
		}
		return kb;
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public V get(Object key) {
		int ktype = buildType(key);
		ByteBuffer kb = buildKeyBuffer(ktype, key);
		return (V)nget(ctx, ktype, kb.array(), MiniConstants.BYTE_ARRAY_OFFSET, kb.remaining());
	}

	@SuppressWarnings("unchecked")
	@Override
	public V put(K key, V val) {
		int ktype = buildType(key);
		int vtype;
		ByteBuffer kb = buildKeyBuffer(ktype, key);
		
		ByteBuffer vb;
		if (val instanceof Integer) {
			vtype = NGX_CLOJURE_SHARED_MAP_JINT;
			long rt = nputNumber(ctx, ktype, kb.array(), MiniConstants.BYTE_ARRAY_OFFSET, kb.remaining(), vtype,
					((Integer) val).longValue(), nullVal);
			if (rt == nullVal) {
				return null;
			}
			return (V) (Integer)(int)(rt);
		} else if (val instanceof Long) {
			vtype = NGX_CLOJURE_SHARED_MAP_JLONG;
			long rt =  nputNumber(ctx, ktype, kb.array(), MiniConstants.BYTE_ARRAY_OFFSET, kb.remaining(), vtype,
					(Long) val, nullVal);
			if (rt == nullVal) {
				return null;
			}
			return (V)(Long)rt;
		} else if (val instanceof String) {
			String value = (String)val;
			vtype = NGX_CLOJURE_SHARED_MAP_JSTRING;
			if (kb.capacity() - kb.remaining() >= value.length()) {
				kb.position(kb.remaining());
				kb.limit(kb.capacity());
				vb = kb.slice();
				kb.flip();
			}else {
				vb = ByteBuffer.allocate(value.length());
			}
			vb = HackUtils.encode(value, MiniConstants.DEFAULT_ENCODING, vb);
			return (V)nput(ctx, ktype, kb.array(), MiniConstants.BYTE_ARRAY_OFFSET,
					kb.remaining(), vtype, vb.array(), MiniConstants.BYTE_ARRAY_OFFSET + vb.arrayOffset() + vb.position(), vb.remaining());
		} else if (val instanceof byte[]) {
			vtype = NGX_CLOJURE_SHARED_MAP_JBYTEA;
			return (V)nput(ctx, ktype, kb.array(), MiniConstants.BYTE_ARRAY_OFFSET,
					kb.remaining(), vtype, val, MiniConstants.BYTE_ARRAY_OFFSET, ((byte[])val).length);
		} else {
			throw new UnsupportedOperationException("val type : " + key.getClass() + ", not supported!");
		}

	}


	@SuppressWarnings("unchecked")
	@Override
	public V remove(Object key) {
		int ktype = buildType(key);
		ByteBuffer kb = buildKeyBuffer(ktype, key);
		return (V)nremove(ctx, ktype, kb.array(), MiniConstants.BYTE_ARRAY_OFFSET, kb.remaining());	
	}
	
	public boolean delete(Object key) {
		int ktype = buildType(key);
		ByteBuffer kb = buildKeyBuffer(ktype, key);
		return ndelete(ctx, ktype, kb.array(), MiniConstants.BYTE_ARRAY_OFFSET, kb.remaining()) == NGX_CLOJURE_SHARED_MAP_OK;	
	}

	public int getInt(Object key) {
		int ktype = buildType(key);
		ByteBuffer kb = buildKeyBuffer(ktype, key);
		return (int)ngetNumber(ctx, ktype, kb.array(), MiniConstants.BYTE_ARRAY_OFFSET, kb.remaining(), NGX_CLOJURE_SHARED_MAP_JINT);
	}
	
	public int putInt(K key, int val) {
		int ktype = buildType(key);
		ByteBuffer kb = buildKeyBuffer(ktype, key);
		return (int)nputNumber(ctx, ktype, kb.array(), MiniConstants.BYTE_ARRAY_OFFSET, kb.remaining(), NGX_CLOJURE_SHARED_MAP_JINT, val, nullVal);
	}
	
	public int putIntIfAbsent(K key, int val) {
		int ktype = buildType(key);
		ByteBuffer kb = buildKeyBuffer(ktype, key);
		return (int)nputNumberIfAbsent(ctx, ktype, kb.array(), MiniConstants.BYTE_ARRAY_OFFSET, kb.remaining(), NGX_CLOJURE_SHARED_MAP_JINT, val, nullVal);
	}
	
	/**
	 * Atomic update value to `old_value + delta` and returns the old value.
	 * If the key is not found or the value type is not int RuntimeException will be thrown.
	 * @return old int value
	 */
	public int atomicAddInt(K key, int delta) {
		int ktype = buildType(key);
		ByteBuffer kb = buildKeyBuffer(ktype, key);
		return (int)natomicAddNumber(ctx, ktype, kb.array(), MiniConstants.BYTE_ARRAY_OFFSET, kb.remaining(), NGX_CLOJURE_SHARED_MAP_JINT, delta);
	}
	
	/**
	 * Atomic update value to `old_value + delta` and returns the old value.
	 * If the key is not found or the value type is not long RuntimeException will be thrown.
	 * @return old long value
	 */
	public long atomicAddLong(K key, long delta) {
		int ktype = buildType(key);
		ByteBuffer kb = buildKeyBuffer(ktype, key);
		return (int)natomicAddNumber(ctx, ktype, kb.array(), MiniConstants.BYTE_ARRAY_OFFSET, kb.remaining(), NGX_CLOJURE_SHARED_MAP_JLONG, delta);
	}
	
	public long getLong(Object key) {
		int ktype = buildType(key);
		ByteBuffer kb = buildKeyBuffer(ktype, key);
		return ngetNumber(ctx, ktype, kb.array(), MiniConstants.BYTE_ARRAY_OFFSET, kb.remaining(), NGX_CLOJURE_SHARED_MAP_JLONG);
	}
	
	public long putLong(K key, long val) {
		int ktype = buildType(key);
		ByteBuffer kb = buildKeyBuffer(ktype, key);
		return nputNumber(ctx, ktype, kb.array(), MiniConstants.BYTE_ARRAY_OFFSET, kb.remaining(), NGX_CLOJURE_SHARED_MAP_JLONG, val, nullVal);
	}
	
	public long putLongIfAbsent(K key, long val) {
		int ktype = buildType(key);
		ByteBuffer kb = buildKeyBuffer(ktype, key);
		return nputNumberIfAbsent(ctx, ktype, kb.array(), MiniConstants.BYTE_ARRAY_OFFSET, kb.remaining(), NGX_CLOJURE_SHARED_MAP_JLONG, val, nullVal);
	}

	@Override
	public void clear() {
		long rc = nclear(ctx);
		if (rc != 0) {
			throw new RuntimeException("unexcepted error, rc=" + rc);
		}
	}


	@Override
	public Set<K> keySet() {
		NginxClojureRT.getLog().warn("NginxSharedHashMap.keySet is quite expensive operation DO NOT use it at non-debug case!!!");
		final Set<K> sets = new HashSet<>();
		nvisit(ctx, (SharedMapSimpleVisitor<K, V>) (key, val) -> {
			sets.add(key);
			return 0;
		});
		return sets;
	}


	@Override
	public Collection<V> values() {
		NginxClojureRT.getLog().warn("NginxSharedHashMap.values is quite expensive operation DO NOT use it at non-debug case!!!");
		final List<V> vals = new ArrayList<>();
		nvisit(ctx, (SharedMapSimpleVisitor<K, V>) (key, val) -> {
			vals.add(val);
			return 0;
		});
		return vals;		
	}


	@Override
	public Set<java.util.Map.Entry<K, V>> entrySet() {
		NginxClojureRT.getLog().warn("NginxSharedHashMap.entrySet is quite expensive operation DO NOT use it at non-debug case!!!");
		final Set<java.util.Map.Entry<K, V>> sets = new HashSet<>();
		nvisit(ctx, (SharedMapSimpleVisitor<K, V>) (key, val) -> {
			SimpleEntry<K, V> en = new SimpleEntry<>(key, val);
			sets.add(en);
			return 0;
		});
		return sets;
	}

	/* (non-Javadoc)
	 * @see java.util.Map#putAll(java.util.Map)
	 */
	@Override
	public void putAll(Map<? extends K, ? extends V> m) {
		for (Entry<? extends K, ? extends V> en : m.entrySet()) {
			put(en.getKey(), en.getValue());
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public V putIfAbsent(K key, V val) {
		int ktype = buildType(key);
		int vtype;
		ByteBuffer kb = buildKeyBuffer(ktype, key);
		
		ByteBuffer vb;
		if (val instanceof Integer) {
			vtype = NGX_CLOJURE_SHARED_MAP_JINT;
			long rt = nputNumberIfAbsent(ctx, ktype, kb.array(), MiniConstants.BYTE_ARRAY_OFFSET, kb.remaining(), vtype,
					((Integer) val).longValue(), nullVal);
			if (rt == nullVal) {
				return null;
			}
			return (V) (Integer)(int)(rt);
		} else if (val instanceof Long) {
			vtype = NGX_CLOJURE_SHARED_MAP_JLONG;
			long rt =  nputNumberIfAbsent(ctx, ktype, kb.array(), MiniConstants.BYTE_ARRAY_OFFSET, kb.remaining(), vtype,
					(Long) val, nullVal);
			if (rt == nullVal) {
				return null;
			}
			return (V)(Long)rt;
		} else if (val instanceof String) {
			String value = (String)val;
			vtype = NGX_CLOJURE_SHARED_MAP_JSTRING;
			if (kb.capacity() - kb.remaining() >= value.length()) {
				kb.position(kb.remaining());
				kb.limit(kb.capacity());
				vb = kb.slice();
				kb.flip();
			}else {
				vb = ByteBuffer.allocate(value.length());
			}
			vb = HackUtils.encode(value, MiniConstants.DEFAULT_ENCODING, vb);
			return (V)nputIfAbsent(ctx, ktype, kb.array(), MiniConstants.BYTE_ARRAY_OFFSET,
					kb.remaining(), vtype, vb.array(), MiniConstants.BYTE_ARRAY_OFFSET + vb.arrayOffset() + vb.position(), vb.remaining());
		} else if (val instanceof byte[]) {
			vtype = NGX_CLOJURE_SHARED_MAP_JBYTEA;
			return (V)nputIfAbsent(ctx, ktype, kb.array(), MiniConstants.BYTE_ARRAY_OFFSET,
					kb.remaining(), vtype, val, MiniConstants.BYTE_ARRAY_OFFSET, ((byte[])val).length);
		} else {
			throw new UnsupportedOperationException("val type : " + key.getClass() + ", not supported!");
		}
	}

	@Override
	public boolean remove(Object key, Object value) {
		throw new UnsupportedOperationException("boolean remove(Object key, Object value)");
	}

	@Override
	public boolean replace(K key, V oldValue, V newValue) {
		throw new UnsupportedOperationException("boolean replace(K key, V oldValue, V newValue)");
	}

	@Override
	public V replace(K key, V value) {
		throw new UnsupportedOperationException("V replace(K key, V value");
	}
	
	public String getName() {
		return name;
	}
}
