package nginx.clojure.groovy;

import java.lang.reflect.Method;

import nginx.clojure.NginxClojureRT;
import nginx.clojure.NginxHandler;
import nginx.clojure.java.NginxJavaHandler;
import nginx.clojure.java.NginxJavaHandlerFactory;
import nginx.clojure.java.NginxJavaRingHandler;

public class NginxGroovyHandlerFactory extends NginxJavaHandlerFactory {

	protected ClassLoader groovyLoader;
	
	public NginxGroovyHandlerFactory() {
		super();
		ClassLoader parent = Thread.currentThread().getContextClassLoader();
		try {
			groovyLoader = (ClassLoader) parent.loadClass("groovy.lang.GroovyClassLoader").getConstructor(ClassLoader.class).newInstance(parent);
		} catch (Throwable e) {
			NginxClojureRT.UNSAFE.throwException(e);
		}
	}
	
	@Override
	public NginxHandler newInstance(int phase, String name, String code) {
		
		try {
			NginxJavaRingHandler ringHandler;
			if (name != null) {
				ringHandler = (NginxJavaRingHandler) groovyLoader.loadClass(name).newInstance();
			}else {
				Method m = groovyLoader.getClass().getMethod("parseClass", String.class);
				ringHandler = (NginxJavaRingHandler) ((Class)m.invoke(groovyLoader, code)).newInstance();
			}
			return new NginxJavaHandler(ringHandler);
		}catch(Throwable e) {
			NginxClojureRT.UNSAFE.throwException(e);
			return null; //never reach
		}
		
	}
}
