/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.java;

import java.io.IOException;
import java.util.Map;

/**
 * An interface for Nginx Java content handler or access handler or rewrite handler.
 * @author Zhang,Yuexiang (xfeep)
 *
 */
public interface NginxJavaRingHandler extends DefinedPrefetch {

	/**
	 *  When an object implements this interface it will be called by nginx-clojure at a certain phase of nginx.
	 *  The argument request is a  request map defined by the ring SPEC at https://github.com/ring-clojure/ring/blob/master/SPEC .
	 *  It contains serveral parts:
	 *  
	 *  <li>server-port (Required, Integer)   The port on which the request is being handled.</li>
	 *  <li>server-name  (Required, String)  The resolved server name, or the server IP address.</li>
	 *  <li>remote-addr (Required, String) The IP address of the client or the last proxy that sent the request.</li>
	 *  <li>uri  (Required, String)   The request URI, excluding the query string and the "?" separator.   Must start with "/".</li>
 	 *  <li>query-string  (Optional, String) The query string, if present.</li>
	 *  <li>scheme (Required, String) The transport protocol, must be one of http or https.</li>
	 *  <li>request-method (Required, String) The HTTP request method, must be a lowercase keyword corresponding to a HTTP  request method, such as :get or :post.</li>
	 *  <li>content-type [DEPRECATED] (Optional, String)The MIME type of the request body, if known.</li>
	 *  <li>content-length [DEPRECATED] (Optional, Integer) The number of bytes in the request body, if known.</li>
	 *  <li>character-encoding [DEPRECATED]  (Optional, String) The name of the character encoding used in the request body, if known.</li>
	 *  <li>sl-client-cert  (Optional, X509Certificate)  The SSL client certificate, if supplied. This is not supported yet.</li>
	 *  <li>headers (Required, Map) A  map of  header name Strings to corresponding header value Strings.</li>
	 *  <li>body (Optional, InputStream)  An InputStream for the request body, if present.</li>
	 *  <p></p>
	 *  The return response  is an array of object,  e.g
	 *  <pre>
	 *  [200, //http status 200
	 *   ArrayMap.create("Content-Type", "text/html", "", "" ), //headers map
     *  "Hello, Java & Nginx!"  //response body can be string, File or Array/Collection of string or File
     *      ]; 
     *       </pre>
     *  Note that  If the rewrite/access handler returns phase-done (Clojure) or Constants.PHASE_DONE (Groovy/Java), nginx will continue to next phases (e.g.  invoke proxy_pass
     *   or content ring handler).  If the rewrite handler returns a general response, nginx will send this response to the client and stop to continue to 
     *   next phases.                  
     *   
	 * @param request  a request map defined by the ring SPEC at https://github.com/ring-clojure/ring/blob/master/SPEC .
	 * @return a object array  which has a different meaning for different handler type.
	 */
	public Object[] invoke(Map<String, Object> request) throws IOException;
	
	/* (non-Javadoc)
	 * @see nginx.clojure.java.DefinedPrefetch#headersNeedPrefetch()
	 */
	@Override
	default String[] headersNeedPrefetch() {
		return ALL_HEADERS;
	}
	
	/* (non-Javadoc)
	 * @see nginx.clojure.java.DefinedPrefetch#variablesNeedPrefetch()
	 */	
	@Override
	default String[] variablesNeedPrefetch() {
		return NO_VARS;
	}
	
	/* (non-Javadoc)
	 * @see nginx.clojure.java.DefinedPrefetch#responseHeadersNeedPrefetch()
	 */
	@Override
	default String[] responseHeadersNeedPrefetch() {
		return NO_HEADERS;
	}
	
	
}
