/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.java;

import static nginx.clojure.MiniConstants.NGX_HTTP_BODY_FILTER_PHASE;
import static nginx.clojure.MiniConstants.NGX_HTTP_HEADER_FILTER_PHASE;
import static nginx.clojure.java.Constants.HEADER_FETCHER;
import nginx.clojure.NginxHandler;
import nginx.clojure.NginxHandlerFactory;

public class NginxJavaHandlerFactory extends NginxHandlerFactory {

	public NginxJavaHandlerFactory() {
		HEADER_FETCHER = new NginxJavaRequestHeadersFetcher();
	}
	
	@Override
	public NginxHandler newInstance(int phase, String name, String code) {
		try {
			name = name.trim();
			Object handler = Thread.currentThread().getContextClassLoader().loadClass(name).newInstance();
			switch (phase) {
			case NGX_HTTP_HEADER_FILTER_PHASE:
				return new NginxJavaHandler((NginxJavaHeaderFilter) handler);
			case NGX_HTTP_BODY_FILTER_PHASE:
				return new NginxJavaHandler((NginxJavaBodyFilter)handler);
			default:
				return new NginxJavaHandler((NginxJavaRingHandler) handler);
			}
		} catch (Throwable e) {
			throw new RuntimeException("can not create nginx handler for name : " + name, e);
		} 
	}

}
