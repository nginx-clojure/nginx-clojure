/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.clj;

import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_GET_HEADER_FLAG_HEADERS_OUT;

import java.util.Iterator;

import nginx.clojure.NginxSimpleHandler.SimpleEntry;
import nginx.clojure.java.JavaLazyHeaderMap;
import clojure.lang.ArityException;
import clojure.lang.IFn;
import clojure.lang.IMapEntry;
import clojure.lang.IPersistentCollection;
import clojure.lang.IPersistentMap;
import clojure.lang.ISeq;
import clojure.lang.ITransientAssociative;
import clojure.lang.ITransientCollection;
import clojure.lang.ITransientMap;
import clojure.lang.IteratorSeq;
import clojure.lang.MapEntry;
import clojure.lang.PersistentArrayMap;
import clojure.lang.RT;
import clojure.lang.Util;

@SuppressWarnings("unchecked")
public class LazyHeaderMap extends JavaLazyHeaderMap implements IPersistentMap, IFn, ITransientAssociative, ITransientMap  {
	
	
	public LazyHeaderMap(long r, boolean headersOut) {
		super(r, headersOut);
	}
	
	@SuppressWarnings("rawtypes")
	@Override
	public Iterator iterator() {
		
		if (safeCache != null) {
			Iterator<Entry<String, Object>> ci = safeCache.entrySet().iterator();
			return new Iterator<MapEntry>() {
				@Override
				public boolean hasNext() {
					return ci.hasNext();
				}

				@Override
				public MapEntry next() {
					Entry<String, Object> se = ci.next();
					return new MapEntry(se.getKey(), se.getValue());
				}

				@Override
				public void remove() {
					throw new UnsupportedOperationException("remove not supported now!");
				}
			};
		}
		
		return new Iterator<MapEntry>() {
			int i = 0;
			@Override
			public boolean hasNext() {
				return i < size -1;
			}

			@Override
			public MapEntry next() {
				return element(i++);
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException("remove not supported now!");
			}
			
		};
	}
	
	private MapEntry element(int i) {
		@SuppressWarnings("rawtypes")
		SimpleEntry se = entry(i);
		if (se.value != null && se.value.getClass().isArray()) {
			se.value = RT.seq(se.value);
		}
		return new MapEntry(se.key, se.value);
	}

	@Override
	public boolean containsKey(Object keyObj) {
		String key = NginxClojureHandler.normalizeHeaderNameHelper(keyObj);
		return super.containsKey(key);
	}

	@Override
	public IMapEntry entryAt(Object key) {
		Object val = valAt(key);
		return val == null ? null : new MapEntry(key, val);
	}

	@Override
	public int count() {
		return size;
	}

	@Override
	public IPersistentCollection cons(Object o) {
		throw new UnsupportedOperationException("cons not supported now!");
	}

	@Override
	public IPersistentCollection empty() {
		//TODO: empty by jni
		return PersistentArrayMap.EMPTY;
	}

	@Override
	public boolean equiv(Object o) {
		return o == this;
	}
	
	@Override
	public ISeq seq() {
		return IteratorSeq.create(iterator());
	}

	@Override
	public Object valAt(Object keyObj) {
		String key = NginxClojureHandler.normalizeHeaderNameHelper(keyObj);
		Object v = super.get(key);
		if (v != null && v.getClass().isArray()) {
			return RT.seq(v);
		}
		return v;
	}

	@Override
	public Object valAt(Object key, Object notFound) {
		Object val = valAt(key);
		return val == null ? notFound : val;
	}

	@Override
	public LazyHeaderMap assoc(Object key, Object val) {
		if ( (flag & NGX_HTTP_CLOJURE_GET_HEADER_FLAG_HEADERS_OUT) ==  0 ) {
			throw new UnsupportedOperationException("assoc not supported for read-only request map!");
		}else {
			put(NginxClojureHandler.normalizeHeaderNameHelper(key), val);
			return this;
		}
	}

	@Override
	public IPersistentMap assocEx(Object key, Object val) {
		throw new UnsupportedOperationException("assocEx not supported now!");
	}

	@Override
	public LazyHeaderMap without(Object key) {
		if ( (flag & NGX_HTTP_CLOJURE_GET_HEADER_FLAG_HEADERS_OUT) ==  0 ) {
			throw new UnsupportedOperationException("without not supported for read-only request map!");
		}else {
			remove(NginxClojureHandler.normalizeHeaderNameHelper(key));
			return this;
		}
	}
	

	public Object call() {
		return invoke();
	}

	public void run(){
		try
			{
			invoke();
			}
		catch(Exception e)
			{
			throw Util.sneakyThrow(e);
			}
	}



	public Object invoke() {
		return throwArity(0);
	}

	public Object invoke(Object keyObj) {
		return valAt(keyObj);
	}

	public Object invoke(Object keyObj, Object notFound) {
		return valAt(keyObj, notFound);
	}

	public Object invoke(Object arg1, Object arg2, Object arg3) {
		return throwArity(3);
	}

	public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4) {
		return throwArity(4);
	}

	public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5) {
		return throwArity(5);
	}

	public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6) {
		return throwArity(6);
	}

	public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7)
			{
		return throwArity(7);
	}

	public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
	                     Object arg8) {
		return throwArity(8);
	}

	public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
	                     Object arg8, Object arg9) {
		return throwArity(9);
	}

	public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
	                     Object arg8, Object arg9, Object arg10) {
		return throwArity(10);
	}

	public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
	                     Object arg8, Object arg9, Object arg10, Object arg11) {
		return throwArity(11);
	}

	public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
	                     Object arg8, Object arg9, Object arg10, Object arg11, Object arg12) {
		return throwArity(12);
	}

	public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
	                     Object arg8, Object arg9, Object arg10, Object arg11, Object arg12, Object arg13)
			{
		return throwArity(13);
	}

	public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
	                     Object arg8, Object arg9, Object arg10, Object arg11, Object arg12, Object arg13, Object arg14)
			{
		return throwArity(14);
	}

	public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
	                     Object arg8, Object arg9, Object arg10, Object arg11, Object arg12, Object arg13, Object arg14,
	                     Object arg15) {
		return throwArity(15);
	}

	public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
	                     Object arg8, Object arg9, Object arg10, Object arg11, Object arg12, Object arg13, Object arg14,
	                     Object arg15, Object arg16) {
		return throwArity(16);
	}

	public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
	                     Object arg8, Object arg9, Object arg10, Object arg11, Object arg12, Object arg13, Object arg14,
	                     Object arg15, Object arg16, Object arg17) {
		return throwArity(17);
	}

	public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
	                     Object arg8, Object arg9, Object arg10, Object arg11, Object arg12, Object arg13, Object arg14,
	                     Object arg15, Object arg16, Object arg17, Object arg18) {
		return throwArity(18);
	}

	public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
	                     Object arg8, Object arg9, Object arg10, Object arg11, Object arg12, Object arg13, Object arg14,
	                     Object arg15, Object arg16, Object arg17, Object arg18, Object arg19) {
		return throwArity(19);
	}

	public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
	                     Object arg8, Object arg9, Object arg10, Object arg11, Object arg12, Object arg13, Object arg14,
	                     Object arg15, Object arg16, Object arg17, Object arg18, Object arg19, Object arg20)
			{
		return throwArity(20);
	}


	public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
	                     Object arg8, Object arg9, Object arg10, Object arg11, Object arg12, Object arg13, Object arg14,
	                     Object arg15, Object arg16, Object arg17, Object arg18, Object arg19, Object arg20,
	                     Object... args)
			{
		return throwArity(21);
	}

	public Object applyTo(ISeq arglist) {
		return applyToHelper(this, Util.ret1(arglist,arglist = null));
	}

	static public Object applyToHelper(IFn ifn, ISeq arglist) {
		switch(RT.boundedLength(arglist, 20))
			{
			case 0:
				arglist = null;
				return ifn.invoke();
			case 1:
				return ifn.invoke(Util.ret1(arglist.first(),arglist = null));
			case 2:
				return ifn.invoke(arglist.first()
						, Util.ret1((arglist = arglist.next()).first(),arglist = null)
				);
			default: throw new RuntimeException("can not take more than 2 args");
			}
	}

	public Object throwArity(int n){
		String name = getClass().getSimpleName();
		int suffix = name.lastIndexOf("__");
		throw new ArityException(n, (suffix == -1 ? name : name.substring(0, suffix)).replace('_', '-'));
	}

	@Override
	public ITransientCollection conj(Object val) {
		MapEntry me = (MapEntry)val;
		assoc(me.key(), me.val());
		return this;
	}

	@Override
	public LazyHeaderMap persistent() {
		return this;
	}
	
}
