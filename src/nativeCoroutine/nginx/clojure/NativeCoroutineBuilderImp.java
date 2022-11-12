/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure;

import nginx.clojure.Coroutine.State;
import jdk.internal.vm.ContinuationScope;
import jdk.internal.vm.Continuation;

/**
 * @author Zhang,Yuexiang (xfeep)
 *
 */
public class NativeCoroutineBuilderImp implements NativeCoroutineBuilder {

	static final ContinuationScope scope = new ContinuationScope("nginx-clojure");
	
	public NativeCoroutine build(Runnable r) {
		return new NativeCoroutine() {
			
			final Continuation continuation = new Continuation(scope, r);
			
			public void resume() {
				continuation.run();
			}
		};
				
	}

	public boolean yield() {
		Coroutine.getActiveCoroutine().setState(State.SUSPENDED);
		return Continuation.yield(scope);
	}
	
}
