/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.java;

/**
 * @author who
 *
 */
public interface DefinedPrefetch {

	String[] ALL_HEADERS = new String[] { "*" };
	String[] NO_HEADERS = new String[0];
	String[] NO_VARS = new String[0];
	String[] CORE_VARS = new String[] { "$$CORE$$" };

	/**
	 * This method returns those headers which need prefetch when it will be used at non-main thread
	 * @return those headers which need prefetch when it will be used at non-main thread
	 */
	String[] headersNeedPrefetch();

	/**
	 * This method returns those Nginx variables which need prefetch when it will be used at non-main thread
	 * @return those Nginx variables which need prefetch when it will be used at non-main thread
	 */
	String[] variablesNeedPrefetch();

}