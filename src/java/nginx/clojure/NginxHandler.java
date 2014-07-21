/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure;

/**
 * Low level nginx handler which faces jni code
 * @author Zhang,Yuexiang (xfeep)
 *
 */
public interface NginxHandler {

	public int execute(long request);
	
}
