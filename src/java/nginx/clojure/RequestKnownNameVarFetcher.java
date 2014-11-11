/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure;

import static nginx.clojure.MiniConstants.CORE_VARS;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_REQ_POOL_OFFSET;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_UINT_SIZE;
import static nginx.clojure.NginxClojureRT.UNSAFE;
import static nginx.clojure.NginxClojureRT.fetchNGXInt;
import static nginx.clojure.NginxClojureRT.fetchString;
import static nginx.clojure.NginxClojureRT.ngx_http_clojure_mem_get_variable;
import static nginx.clojure.NginxClojureRT.ngx_palloc;

import java.io.InputStream;
import java.nio.charset.Charset;

public  class RequestKnownNameVarFetcher implements RequestVarFetcher {

	protected long nameNgxStrPtr;
	
	public RequestKnownNameVarFetcher() {
	}
	
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
		if (varLenPtr == 0) {
			throw new OutOfMemoryError("nginx OutOfMemoryError");
		}
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
		return fetchString(varValPtr + NGX_HTTP_CLOJURE_UINT_SIZE, len, encoding);
	}
	
	public InputStream fetchAsStream(long r) {
		long varLenPtr = ngx_palloc(UNSAFE.getAddress(r + NGX_HTTP_CLOJURE_REQ_POOL_OFFSET), NGX_HTTP_CLOJURE_UINT_SIZE);
		if (varLenPtr == 0) {
			throw new OutOfMemoryError("nginx OutOfMemoryError");
		}
		long varValPtr = ngx_http_clojure_mem_get_variable(r, nameNgxStrPtr, varLenPtr);
		if (varValPtr == 0) {
			return null;
		}
		int len = fetchNGXInt(varLenPtr);
		return new NativeInputStream(UNSAFE.getAddress(varValPtr + NGX_HTTP_CLOJURE_UINT_SIZE), len);
	}
	
}