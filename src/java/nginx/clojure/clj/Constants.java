/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.clj;

import java.util.HashMap;
import java.util.Map;

import nginx.clojure.CaseInsensitiveMap;
import nginx.clojure.MiniConstants;
import nginx.clojure.NginxHeaderHolder;
import nginx.clojure.RequestVarFetcher;
import clojure.lang.Keyword;
import clojure.lang.RT;

/**
 * Constants needed by Nginx-Clojure Clojure Platform
 * @author Zhang,Yuexiang (xfeep)
 *
 */
public class Constants extends MiniConstants {

	/**
	 * Ring Spec (1.1) Keywords : https://github.com/ring-clojure/ring/blob/master/SPEC
	 */
	public static final Keyword SERVER_PORT = RT.keyword(null, "server-port");
	public static final Keyword SERVER_NAME = RT.keyword(null, "server-name");
	public static final Keyword REMOTE_ADDR = RT.keyword(null, "remote-addr");
	public static final Keyword URI = RT.keyword(null, "uri");
	public static final Keyword QUERY_STRING = RT.keyword(null, "query-string");
	public static final Keyword SCHEME = RT.keyword(null, "scheme");
	public static final Keyword REQUEST_METHOD = RT.keyword(null, "request-method");
	public static final Keyword CONTENT_TYPE = RT.keyword(null, "content-type");
	public static final Keyword CHARACTER_ENCODING = RT.keyword(null, "character-encoding");
	public static final Keyword SSL_CLIENT_CERT = RT.keyword(null, "ssl-client-cert");
	public static final Keyword HEADERS = RT.keyword(null, "headers");
	public static final Keyword BODY = RT.keyword(null, "body");
	
	public static final Keyword WEBSOCKET = RT.keyword(null, "websocket?");
	
	public static final Keyword UNKNOWN = RT.keyword(null, "UNKNOWN");
	public static final Keyword GET = RT.keyword(null, "get");
	public static final Keyword HEAD = RT.keyword(null, "head");
	public static final Keyword POST = RT.keyword(null, "post");
	public static final Keyword PUT = RT.keyword(null, "put");
	public static final Keyword DELETE = RT.keyword(null, "delete");
	public static final Keyword MKCOL = RT.keyword(null, "mkcol");
	public static final Keyword COPY = RT.keyword(null, "copy");
	public static final Keyword MOVE = RT.keyword(null, "move");
	public static final Keyword OPTIONS = RT.keyword(null, "options");
	public static final Keyword PROPFIND = RT.keyword(null, "propfind");
	public static final Keyword PROPPATCH = RT.keyword(null, "proppatch");
	public static final Keyword LOCK = RT.keyword(null, "lock");
	public static final Keyword UNLOCK = RT.keyword(null, "unlock");
	public static final Keyword PATCH = RT.keyword(null, "patch");
	public static final Keyword TRACE = RT.keyword(null, "trace");
	
	
	public static final Keyword[] HTTP_METHODS = { UNKNOWN, GET, HEAD,
			POST, PUT, DELETE, MKCOL, COPY, MOVE, OPTIONS, PROPFIND,
			PROPPATCH, LOCK, UNLOCK, PATCH, TRACE };
	
	
	public static final Keyword STATUS = RT.keyword(null, "status");
	
	public static RequestVarFetcher REQUEST_METHOD_FETCHER;

	@SuppressWarnings("rawtypes")
	public static final Map ASYNC_TAG = new HashMap(0);
	
	@SuppressWarnings("rawtypes")
	public static final Map PHRASE_DONE = new HashMap(0);
	
	@SuppressWarnings("rawtypes")
	public static final Map PHASE_DONE = PHRASE_DONE;
	
	public static RequestVarFetcher HEADER_FETCHER;
	
	public final static Map<String, NginxHeaderHolder> KNOWN_RESP_HEADERS = new CaseInsensitiveMap<NginxHeaderHolder>();
	
}
