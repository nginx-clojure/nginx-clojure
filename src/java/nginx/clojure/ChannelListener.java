/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure;

import java.io.IOException;

public interface ChannelListener<T> {
	
	public void onClose(T data) throws IOException;
	public void onConnect(long status, T data) throws IOException;
	public void onRead(long status, T data) throws IOException;
	public void onWrite(long status, T data) throws IOException;
	
}