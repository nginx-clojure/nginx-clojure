/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.clj;

import static nginx.clojure.MiniConstants.NR_ASYNC_TAG;
import static nginx.clojure.MiniConstants.NR_PHRASE_DONE;
import static nginx.clojure.NginxClojureRT.initThreadPoolOnlyForTestingUsage;
import static nginx.clojure.NginxClojureRT.log;
import static nginx.clojure.NginxClojureRT.threadPoolOnlyForTestingUsage;
import static nginx.clojure.clj.Constants.ASYNC_TAG;
import static nginx.clojure.clj.Constants.PHRASE_DONE;

import java.util.Map;
import java.util.concurrent.Future;

import nginx.clojure.Coroutine;
import nginx.clojure.NginxClojureRT;
import nginx.clojure.NginxClojureRT.BatchCallRunner;
import nginx.clojure.NginxRequest;
import nginx.clojure.NginxResponse;
import nginx.clojure.NginxSimpleHandler;
import nginx.clojure.SuspendExecution;
import nginx.clojure.wave.JavaAgent;
import clojure.lang.IFn;
import clojure.lang.ISeq;
import clojure.lang.Keyword;
import clojure.lang.RT;

public class NginxClojureHandler extends NginxSimpleHandler {

	private IFn ringHandler;
	
	public NginxClojureHandler() {
	}
	
	public NginxClojureHandler(IFn ringHandler) {
		this.ringHandler = ringHandler;
	}
	
	
	/**
	 * Execute a batch of IFn instances, e.g. clojure functions, in different new coroutines so we can handle many sockets parallel 
	 * within one request-response cycle.
	 * When there 's no coroutine context, it will turn to use thread pool to make testing with lein-ring easy.
	 * @param fns Runnable instances
	 */
	public static ISeq coBatchCall(ISeq fns) throws SuspendExecution {
		int c = fns.count();
		int[] counter = new int[] {c};
		Object[] results = new Object[c];
		Coroutine parent = Coroutine.getActiveCoroutine();
		
		if (parent == null && (JavaAgent.db == null || !JavaAgent.db.isRunTool())) {
			log.warn("we are not in coroutine enabled context, so we turn to use thread for only testing usage!");
			Future[] futures = new Future[c];
			for (int i = 0; fns != null;  fns = fns.next(), i++) {
				IFn fn = (IFn) fns.first();
				BatchCallRunner bcr = new BatchCallRunner(parent, counter, fn, i, results);
				if (threadPoolOnlyForTestingUsage == null) {
					initThreadPoolOnlyForTestingUsage();
				}
				futures[i] = threadPoolOnlyForTestingUsage.submit(bcr);
			}
			for (Future f : futures) {
				try {
					f.get();
				} catch (Throwable e) {
					log.error("do future failed", e);
				} 
			}
		}else {
			boolean shouldYieldParent = false;
			for (int i = 0; fns != null;  fns = fns.next()) {
				IFn fn = (IFn) fns.first();
				Coroutine co  = new Coroutine(new BatchCallRunner(parent, counter, fn, i++, results));
				co.resume();
				if (co.getState() != Coroutine.State.FINISHED) {
					shouldYieldParent = true;
				}
			}
			
			if (parent != null && shouldYieldParent) {
				Coroutine.yield();
			}
		}

		return RT.seq(results);
	}
	
	public static  String normalizeHeaderName(Object nameObj) {
		String name;
		if (nameObj instanceof String) {
			name = (String)nameObj;
		}else if (nameObj instanceof Keyword) {
			name = ((Keyword)nameObj).getName();
		}else {
			name = nameObj.toString();
		}
		return name == null ? null : name.toLowerCase();
	}

	@Override
	public NginxRequest makeRequest(long r) {
		return new LazyRequestMap(ringHandler, r);
	}
	
	public static NginxResponse toNginxResponse(Map resp) {
		if (resp == ASYNC_TAG) {
			return NR_ASYNC_TAG;
		}
		if (resp == PHRASE_DONE) {
			return NR_PHRASE_DONE;
		}
		return new NginxClojureResponse(resp);
	}
	
	public static void completeAsyncResponse(long r, Map resp) {
		NginxClojureRT.completeAsyncResponse(r, toNginxResponse(resp));
	}

}
