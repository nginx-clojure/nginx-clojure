/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.net;

public interface NginxClojureSocketRawHandler {
	public void onConnect(long u, long sc);
	public void onRead(long u, long sc);
	public void onWrite(long u, long sc);
	public void onRelease(long u, long sc);
}
