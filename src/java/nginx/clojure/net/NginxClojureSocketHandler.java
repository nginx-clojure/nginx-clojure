/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.net;

import java.io.IOException;

public interface NginxClojureSocketHandler {
	public void onConnect(NginxClojureAsynSocket s, long sc) throws IOException;
	public void onRead(NginxClojureAsynSocket s, long sc) throws IOException;
	public void onWrite(NginxClojureAsynSocket s, long sc) throws IOException;
	public void onRelease(NginxClojureAsynSocket s, long sc) throws IOException;
}
