package nginx.clojure;

import static nginx.clojure.Constants.CHARACTER_ENCODING;
import static nginx.clojure.Constants.CHARACTER_ENCODING_FETCHER;
import static nginx.clojure.Constants.CONTENT_TYPE;
import static nginx.clojure.Constants.CONTENT_TYPE_FETCHER;
import static nginx.clojure.Constants.DEFAULT_ENCODING;
import static nginx.clojure.Constants.HEADERS;
import static nginx.clojure.Constants.HEADER_FETCHER;
import static nginx.clojure.Constants.QUERY_STRING;
import static nginx.clojure.Constants.QUERY_STRING_FETCHER;
import static nginx.clojure.Constants.REMOTE_ADDR;
import static nginx.clojure.Constants.REMOTE_ADDR_FETCHER;
import static nginx.clojure.Constants.REQUEST_METHOD;
import static nginx.clojure.Constants.REQUEST_METHOD_FETCHER;
import static nginx.clojure.Constants.SCHEME;
import static nginx.clojure.Constants.SCHEME_FETCHER;
import static nginx.clojure.Constants.SERVER_NAME;
import static nginx.clojure.Constants.SERVER_NAME_FETCHER;
import static nginx.clojure.Constants.SERVER_PORT;
import static nginx.clojure.Constants.SERVER_PORT_FETCHER;
import static nginx.clojure.Constants.URI;
import static nginx.clojure.Constants.URI_FETCHER;

import java.util.HashMap;

public   class LazyRequestMap extends HashMap {
	
	long r;
	
	public LazyRequestMap() {
	}
	
	@SuppressWarnings("unchecked")
	public LazyRequestMap(long r) {
		this.r = r;
//		TODO: SSL_CLIENT_CERT,BODY
		put(SERVER_PORT,SERVER_PORT_FETCHER);
		put(SERVER_NAME, SERVER_NAME_FETCHER);
		put(REMOTE_ADDR, REMOTE_ADDR_FETCHER);
		put(URI, URI_FETCHER);
		put(QUERY_STRING, QUERY_STRING_FETCHER);
		put(SCHEME, SCHEME_FETCHER);
		put(REQUEST_METHOD, REQUEST_METHOD_FETCHER);
		put(CONTENT_TYPE, CONTENT_TYPE_FETCHER);
		put(CHARACTER_ENCODING, CHARACTER_ENCODING_FETCHER);
		put(HEADERS, HEADER_FETCHER);
	}
	
	@Override
	public Object get(Object key) {
//		System.out.println("LazyRequestMap, key:" + key);
		Object o = super.get(key);
		if (o instanceof RequestVarFetcher) {
			RequestVarFetcher rf = (RequestVarFetcher) o;
			Object rt = rf.fetch(r, DEFAULT_ENCODING);
			put(key, rt);
//			System.out.println("LazyRequestMap, key=" + rt);
			return rt;
		}
//		System.out.println("LazyRequestMap, key=" + o);
		return o;
	}
}