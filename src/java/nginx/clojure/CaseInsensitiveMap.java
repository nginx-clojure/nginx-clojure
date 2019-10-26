/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Only use this for small and stable map
 * 
 */
public class CaseInsensitiveMap<V> extends TreeMap<String, V> {

	public static final Comparator<String> IGNORE_CASE_COMPARATOR =  new Comparator<String>() {
		@Override
		public int compare(String s1, String s2) {
            int n1 = s1.length();
            int n2 = s2.length();
            int min = n1 > n2 ? n2 : n1;
            for (int i = 0; i < min; i++) {
                int c1 = s1.charAt(i);
                int c2 = s2.charAt(i);
                if (c1 != c2) {
                    c1 = c1 >= 'A' && c1 <= 'Z' ?  (c1 | 0x20) : c1;
                    c2 = c2 >= 'A' && c2 <= 'Z' ?  (c2 | 0x20) : c2;
                    if (c1 != c2) {
                        return c1 - c2;
                    }
                }
            }
            return n1 - n2;
		}
	};
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private HashMap<String, V> fastMap = new HashMap<String, V>();

	public CaseInsensitiveMap() {
		super(IGNORE_CASE_COMPARATOR);
	}
	
	public CaseInsensitiveMap(Map<String, V> map) {
		super(IGNORE_CASE_COMPARATOR);
		this.putAll(map);
	}
	
	@Override
	public V get(Object key) {
		V v = fastMap.get(key);
		return v == null ? super.get(key) : v;
	}
	
	@Override
	public V put(String key, V value) {
		fastMap.put(key, value);
		return super.put(key, value);
	}
	
	@Override
	public void putAll(Map<? extends String, ? extends V> map) {
		super.putAll(map);
		fastMap.putAll(map);
	}
	
}
