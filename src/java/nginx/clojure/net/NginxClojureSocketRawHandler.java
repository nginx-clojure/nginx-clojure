/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.net;

import java.io.IOException;

public interface NginxClojureSocketRawHandler {
	public void onConnect(long u, long sc) throws IOException;
	public void onRead(long u, long sc) throws IOException;
	public void onWrite(long u, long sc) throws IOException;
	public void onRelease(long u, long sc) throws IOException;
}
