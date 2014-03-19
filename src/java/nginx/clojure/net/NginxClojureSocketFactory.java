/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.net;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.SocketImpl;
import java.net.SocketImplFactory;

import nginx.clojure.Coroutine;
import nginx.clojure.logger.LoggerService;
import nginx.clojure.logger.TinyLogService;

public class NginxClojureSocketFactory implements SocketImplFactory {
	
	protected static LoggerService log;

	public NginxClojureSocketFactory() {
		if (log == null) {
			log = TinyLogService.createDefaultTinyLogService();
		}
	}

	@Override
	public SocketImpl createSocketImpl() {
		if (Coroutine.getActiveCoroutine() == null) {
			log.warn("we are not in coroutine so we turn to java build-in socket implement!");
			try {
				Class<?> socketImpClz = Thread.currentThread().getContextClassLoader().loadClass("java.net.SocksSocketImpl");
				@SuppressWarnings("unchecked")
				Constructor<SocketImpl> socketConstructor = (Constructor<SocketImpl>) socketImpClz.getDeclaredConstructor();
				socketConstructor.setAccessible(true);
				return socketConstructor.newInstance();
			} catch (InvocationTargetException e) {
				throw new RuntimeException(e.getCause());
			} catch (Throwable e) {
				throw new RuntimeException(e);
			} 
		}
		return new NginxClojureSocketImpl();
	}

}
