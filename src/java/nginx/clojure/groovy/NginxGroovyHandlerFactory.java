package nginx.clojure.groovy;

import static nginx.clojure.MiniConstants.NGX_HTTP_BODY_FILTER_PHASE;
import static nginx.clojure.MiniConstants.NGX_HTTP_HEADER_FILTER_PHASE;

import java.lang.reflect.Method;

import nginx.clojure.NginxClojureRT;
import nginx.clojure.NginxHandler;
import nginx.clojure.java.NginxJavaBodyFilter;
import nginx.clojure.java.NginxJavaHandler;
import nginx.clojure.java.NginxJavaHandlerFactory;
import nginx.clojure.java.NginxJavaHeaderFilter;
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
	
	@SuppressWarnings("rawtypes")
	@Override
	public NginxHandler newInstance(int phase, String name, String code) {
		
		try {
			Object handler;
			if (name != null) {
				handler = groovyLoader.loadClass(name).newInstance();
			} else {
				Method m = groovyLoader.getClass().getMethod("parseClass", String.class);
				handler = ((Class)m.invoke(groovyLoader, code)).newInstance();
			}
			switch (phase) {
			case NGX_HTTP_HEADER_FILTER_PHASE:
				return new NginxJavaHandler((NginxJavaHeaderFilter) handler);
			case NGX_HTTP_BODY_FILTER_PHASE:
				return new NginxJavaHandler((NginxJavaBodyFilter)handler);
			default:
				return new NginxJavaHandler((NginxJavaRingHandler) handler);
			}
		}catch(Throwable e) {
			NginxClojureRT.UNSAFE.throwException(e);
			return null; //never reach
		}
		
	}
}
