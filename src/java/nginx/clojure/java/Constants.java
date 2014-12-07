/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.java;

import nginx.clojure.MiniConstants;
import nginx.clojure.RequestVarFetcher;

public class Constants extends MiniConstants {

	public static RequestVarFetcher HEADER_FETCHER;
	
	public static final Object[] ASYNC_TAG = new Object[3];
	
	public static final Object[] PHRASE_DONE = new Object[3];
	
	public static final Object[] PHASE_DONE = PHRASE_DONE;

	
}
