/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.util;

import java.io.IOException;

/**
 * @author who
 *
 */
public interface NginxPubSubListener<T> {
	
	public void onMessage(String msg, T data) throws IOException;
	
}
