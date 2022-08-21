/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

import nginx.clojure.asm.Type;
import nginx.clojure.wave.InstrumentConstructorMethod;

public class ReflectConstructorUtil {

	@SuppressWarnings("rawtypes")
	static Constructor findConstructor(Class clz, Class[] parameterTypes) {
		for (Constructor ctor : clz.getConstructors()) {
			boolean found = true;
			if ( ctor.getParameterCount() == parameterTypes.length ) {
				Class[] ctorParameterTypes = ctor.getParameterTypes();
				for (int i = 0; i < parameterTypes.length; i++) {
					if (parameterTypes[i] != ctorParameterTypes[i]) {
						found = false;
						break;
					}
				}
			} else {
				found = false;
			}
			if (found) {
				return ctor;
			}
		}
		return null;
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static Object __nc_new_instance(Constructor c, Object[] args) throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, SuspendExecution {
		Class[] classTypes = Arrays.copyOf(c.getParameterTypes(), c.getParameterCount() + 1);
		classTypes[c.getParameterCount()] = SuspendExecution.class;
		String desc = Type.getConstructorDescriptor(c);
		Constructor c2 = findConstructor(c.getDeclaringClass(), classTypes);
		
		if (c2 != null) {
			args = Arrays.copyOf(args, classTypes.length);
			Object obj = c2.newInstance(args);
			Method m = null;
			try {
				m = c2.getDeclaringClass().getDeclaredMethod(InstrumentConstructorMethod.buildInitHelpMethodName(desc));
			} catch (NoSuchMethodException e) {
				throw new IllegalArgumentException(e);
			}
			m.invoke(obj);
			return obj;	
		}
		
		return c.newInstance(args);
	}
}
