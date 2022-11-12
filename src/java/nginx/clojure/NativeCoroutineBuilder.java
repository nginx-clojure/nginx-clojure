/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure;

/**
 * @author Zhang,Yuexiang (xfeep)
 *
 */
public interface NativeCoroutineBuilder {
	
	static interface NativeCoroutine {
		void resume();
	}
	
	public NativeCoroutine build(Runnable r);
	
	public boolean yield();
}
