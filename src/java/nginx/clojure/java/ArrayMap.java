/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.java;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import nginx.clojure.NginxSimpleHandler.SimpleEntry;
import nginx.clojure.NginxSimpleHandler.SimpleEntrySetter;
import nginx.clojure.java.PickerPoweredIterator.Picker;

public class ArrayMap<K, V> implements Map<K, V> {
	
	public static <K, V>  ArrayMap<K, V> create(Object ...objects) {
		return new ArrayMap<K, V>(objects);
	}

	protected Object[] array;
	
	public ArrayMap() {
		array = new Object[0];
	}
	
	public ArrayMap(Object[] a) {
		array = a;
	}
	
	
	protected int index(Object key) {
		for (int i = 0; i < array.length; i+=2){
			if (key.equals(array[i])) {
				return i >> 1;
			}
		}
		return -1;
	}
	
	
	@SuppressWarnings("unchecked")
	public K key(int i) {
		return (K) array[i << 1];
	}
	
	@SuppressWarnings("unchecked")
	public V val(int i) {
		return (V)array[(i << 1) + 1];
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public SimpleEntry<K, V> entry(final int i) {
		final SimpleEntry se = new SimpleEntry(key(i), val(i), null);
		se.setter = new SimpleEntrySetter() {
			public Object setValue(Object value) {
				Object old = array[(i << 1) + 1];
				se.value = array[(i << 1) + 1] = value;
				return old;
			}
		};
		return se;
	}
	

	@Override
	public boolean containsKey(Object key) {
		return index(key) != -1;
	}

	@Override
	public int size() {
		return array.length >> 1;
	}

	@Override
	public boolean isEmpty() {
		return array.length == 0;
	}

	@Override
	public boolean containsValue(Object value) {
		int size = size();
		for (int i = 0; i < size; i++) {
			if (value.equals(val(i))) {
				return true;
			}
		}
		return false;
	}

	@Override
	public V get(Object key) {
		int i = index(key);
		return i == -1 ? null : val(i);
	}

	@SuppressWarnings("unchecked")
	@Override
	public V put(K key, V val) {
		int i = index(key);
		if (i != -1) {
			i = (i << 1) + 1;
			V old = (V) array[i];
			array[i] = val;
			return old;
		}
		Object[] newArray = new Object[array.length + 2];
		System.arraycopy(array, 0, newArray, 0, array.length);
		newArray[array.length] = key;
		newArray[array.length+1] = val;
		this.array = newArray;
		return null;
	}

	@Override
	public V remove(Object key) {
		int i = index(key);
		if (i == -1) {
			return null;
		}else {
			V old = val(i);
			i <<= 1 ;
			Object[] newArray = new Object[array.length - 2];
			if (i > 0) {
				System.arraycopy(array, 0, newArray, 0, i);
			}
			System.arraycopy(array, i + 2, newArray, i, array.length - i - 2);
			this.array = newArray;
			return old;
		}
	
	}

	@Override
	public void putAll(Map<? extends K, ? extends V> m) {
		for (Entry<? extends K, ? extends V> entry : m.entrySet()) {
			put(entry.getKey(), entry.getValue());
		}
	}

	@Override
	public void clear() {
		this.array = new Object[0];
	}

	private class KeySet extends AbstractSet<K> {

		@Override
		public Iterator<K> iterator() {
			return new PickerPoweredIterator<K>(new Picker<K>() {
				@Override
				public K pick(int i) {
					return key(i);
				}
				@Override
				public int size() {
					return array.length >> 1;
				}
			});
		}

		@Override
		public int size() {
			return  array.length >> 1;
		}
		
	}
	
	private class ValueSet extends AbstractSet<V> {

		@Override
		public Iterator<V> iterator() {
			return new PickerPoweredIterator<V>(new Picker<V>() {
				@Override
				public V pick(int i) {
					return val(i);
				}
				@Override
				public int size() {
					return array.length >> 1;
				}
			});
		}

		@Override
		public int size() {
			return array.length >> 1;
		}
	}
		
	
	@Override
	public Set<K> keySet() {
		return new KeySet();
	}

	@Override
	public Collection<V> values() {
		return new ValueSet();
	}
	
	private class EntrySet extends AbstractSet<Entry<K, V>> {

		@Override
		public Iterator<Entry<K, V>> iterator() {
			return new PickerPoweredIterator<Entry<K, V>>(new Picker<Entry<K, V>>() {
				@Override
				public Entry<K, V> pick(int i) {
					return entry(i);
				}
				@Override
				public int size() {
					return array.length >> 1;
				}
			});
		}
		@Override
		public int size() {
			return array.length >> 1;
		}
		
	}

	@Override
	public Set<java.util.Map.Entry<K, V>> entrySet() {
		return new EntrySet();
	}


}
