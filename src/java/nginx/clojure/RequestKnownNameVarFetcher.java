/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure;

import java.nio.charset.Charset;
import java.util.Map;

import static nginx.clojure.NginxClojureRT.*;
import static nginx.clojure.Constants.*;

public  class RequestKnownNameVarFetcher implements RequestVarFetcher {

	private long nameNgxStrPtr;
	
	public RequestKnownNameVarFetcher(String name) {
		Long l = CORE_VARS.get(name);
		if (l == null){
			throw new IllegalArgumentException(name + " is not a known variable");
		}else {
			nameNgxStrPtr = l.longValue();
		}
	}
	
	
	@Override
	public Object fetch(long r, Charset encoding) {
		long varLenPtr = ngx_palloc(UNSAFE.getAddress(r + NGX_HTTP_CLOJURE_REQ_POOL_OFFSET), NGX_HTTP_CLOJURE_UINT_SIZE);
		long varValPtr = ngx_http_clojure_mem_get_variable(r, nameNgxStrPtr, varLenPtr);
		if (varValPtr == 0) {
			return null;
		}
		int len = fetchNGXInt(varLenPtr);
//		for (Map.Entry<String, Long> entry : CORE_VARS.entrySet()) {
//			if (entry.getValue() == nameNgxStrPtr) {
//				System.out.println("name:" + entry.getKey() + ", len:" + len);
//			}
//		}
		return fetchString(varValPtr + NGX_HTTP_CLOJURE_UINT_SIZE, len, DEFAULT_ENCODING);
	}
	
}