/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.net;

import java.net.SocketImpl;
import java.net.SocketImplFactory;

public class NginxClojureSocketFactory implements SocketImplFactory {

	public NginxClojureSocketFactory() {
	}

	@Override
	public SocketImpl createSocketImpl() {
		return new NginxClojureSocketImpl();
	}

}
