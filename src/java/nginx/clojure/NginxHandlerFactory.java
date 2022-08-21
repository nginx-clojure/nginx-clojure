/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure;

import java.util.HashMap;
import java.util.Map;

public abstract class  NginxHandlerFactory {
	
	public static final String NGINX_CLOJURE_HANDLER_FACTORY_SYSTEM_PROPERTY_PREFIX = "nginx.clojure.handler.factory.";
	
	private static Map<String, NginxHandlerFactory> handlerFactoryMap = new HashMap<String, NginxHandlerFactory>();
	
	public abstract NginxHandler newInstance(int phase, String name, String code);
	
	
	public static synchronized void register(String type,  NginxHandlerFactory factory) {
		handlerFactoryMap.put(type, factory);
	}
	
	public static synchronized NginxHandlerFactory fetchFactory(String type) {
		NginxHandlerFactory factory = handlerFactoryMap.get(type);
		if (factory == null) {
			String factoryName = System.getProperty(NGINX_CLOJURE_HANDLER_FACTORY_SYSTEM_PROPERTY_PREFIX + type);
			if (factoryName == null) {
				return null;
			}
			try {
				@SuppressWarnings("rawtypes")
				Class clz = Thread.currentThread().getContextClassLoader().loadClass(factoryName);
				factory = (NginxHandlerFactory) clz.newInstance();
				handlerFactoryMap.put(type, factory);
			} catch (Throwable e) {
				throw new RuntimeException("can not load factory:" + factoryName, e);
			}
		}
		return factory;
	}
	
	public static NginxHandler fetchHandler(int phase, String type, String name, String code) {
		NginxHandlerFactory factory = fetchFactory(type);
		if (factory == null) {
			throw new RuntimeException("can not find subclass of NginxHandlerFactory for type : " + type);
		}
		return factory.newInstance(phase, name, code);
	}
	
}
