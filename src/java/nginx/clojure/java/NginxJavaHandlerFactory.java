/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.java;

import static nginx.clojure.java.Constants.HEADER_FETCHER;
import nginx.clojure.NginxHandler;
import nginx.clojure.NginxHandlerFactory;

public class NginxJavaHandlerFactory extends NginxHandlerFactory {

	public NginxJavaHandlerFactory() {
		HEADER_FETCHER = new NginxJavaRequestHeadersFetcher();
	}
	
	@Override
	public NginxHandler newInstance(String name, String code) {
		try {
			NginxJavaRingHandler ringHandler = (NginxJavaRingHandler) Thread.currentThread().getContextClassLoader().loadClass(name).newInstance();
			return new NginxJavaHandler(ringHandler);
		} catch (Throwable e) {
			throw new RuntimeException("can not create nginx handler for name : " + name, e);
		} 
	}

}
