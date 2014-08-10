/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.clj;

import nginx.clojure.net.NginxClojureAsynSocket;
import nginx.clojure.net.NginxClojureSocketHandler;
import clojure.lang.IFn;


public  class ClojureFunctionSocketHandler implements NginxClojureSocketHandler {
	
	IFn f;
	
	public ClojureFunctionSocketHandler(IFn f) {
		this.f = f;
	}

	@Override
	public void onConnect(NginxClojureAsynSocket s, long sc) {
		f.invoke(s, "connect", sc);
	}

	@Override
	public void onRead(NginxClojureAsynSocket s, long sc) {
		f.invoke(s, "read", sc);
	}

	@Override
	public void onWrite(NginxClojureAsynSocket s, long sc) {
		f.invoke(s, "write", sc);
	}

	@Override
	public void onRelease(NginxClojureAsynSocket s, long sc) {
		f.invoke(s, "release", sc);
	}
	
}