/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.clj;

import nginx.clojure.util.NginxSharedHashMap;
import clojure.lang.ArityException;
import clojure.lang.IFn;
import clojure.lang.IPersistentMap;
import clojure.lang.ISeq;
import clojure.lang.ITransientAssociative;
import clojure.lang.ITransientCollection;
import clojure.lang.ITransientMap;
import clojure.lang.MapEntry;
import clojure.lang.RT;
import clojure.lang.Util;

@SuppressWarnings("rawtypes")
public class ClojureSharedHashMap extends NginxSharedHashMap implements ITransientAssociative, ITransientMap, IFn {

	public ClojureSharedHashMap(String name) {
		super(name);
	}

	@Override
	public ITransientCollection conj(Object val) {
		MapEntry me = (MapEntry)val;
		assoc(me.key(), me.val());
		return this;
	}

	@Override
	public IPersistentMap persistent() {
		throw new UnsupportedOperationException("persistent");
	}

	@Override
	public Object valAt(Object key) {
		return super.get(key);
	}

	@Override
	public Object valAt(Object key, Object notFound) {
		Object o = super.get(key);
		return o == null ? notFound : o;
	}

	@Override
	public int count() {
		return super.size();
	}

	@Override
	public ITransientMap without(Object key) {
		super.delete(key);
		return this;
	}

	@SuppressWarnings("unchecked")
	@Override
	public ClojureSharedHashMap assoc(Object key, Object val) {
		super.put(key, val);
		return this;
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
}
