/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.java;

import nginx.clojure.NginxRequest;
import nginx.clojure.NginxSimpleHandler;

public class NginxJavaHandler extends NginxSimpleHandler {

	protected NginxJavaRingHandler ringHandler;
	
	public NginxJavaHandler() {
	}
	
	
	public NginxJavaHandler(NginxJavaRingHandler ringHandler) {
		super();
		this.ringHandler = ringHandler;
	}


	@Override
	public NginxRequest makeRequest(long r) {
		return new NginxJavaRequest(ringHandler, r);
	}

}
