package example;

import static nginx.clojure.MiniConstants.*;
import static nginx.clojure.NginxClojureRT.*;


import java.io.IOException;
import java.util.Map;

import nginx.clojure.MiniConstants;
import nginx.clojure.java.NginxJavaRequest;
import nginx.clojure.java.NginxJavaRingHandler;

public class MyHandler implements NginxJavaRingHandler {

	public MyHandler() {
	}

	public Object[] invoke(Map<String, Object> request) throws IOException {
		NginxJavaRequest req = ((NginxJavaRequest)request);
		// we can finely control when & what to be sent to the client.
		req.hijack(true);
		long r = req.nativeRequest();
		
		long pool = UNSAFE.getAddress(r + MiniConstants.NGX_HTTP_CLOJURE_REQ_POOL_OFFSET);
		long nameNgxStrPtr = ngx_palloc(pool, NGX_HTTP_CLOJURE_STR_SIZE);
		if (nameNgxStrPtr == 0) {
			throw new OutOfMemoryError("nginx OutOfMemoryError");
		}
		pushNGXLowcaseString(nameNgxStrPtr, "my_array", DEFAULT_ENCODING, pool);
		long varLenPtr = ngx_palloc(UNSAFE.getAddress(r + NGX_HTTP_CLOJURE_REQ_POOL_OFFSET), NGX_HTTP_CLOJURE_UINT_SIZE);
		if (varLenPtr == 0) {
			throw new OutOfMemoryError("nginx OutOfMemoryError");
		}
		long varValPtr = ngx_http_clojure_mem_get_variable(r, nameNgxStrPtr, varLenPtr);
		if (varValPtr == 0) {
			return null;
		}
		int len = fetchNGXInt(varLenPtr);
		byte[] array = new byte[len];
		long varDataAddr = UNSAFE.getAddress(varValPtr+NGX_HTTP_CLOJURE_UINT_SIZE);
		
		ngx_http_clojure_mem_copy_to_obj(varDataAddr, array, MiniConstants.BYTE_ARRAY_OFFSET, len);
		
		//now we transform array, e.g. invoke the 3rd-party black box java library
		byte[] newArray = new String(array).toUpperCase().getBytes(DEFAULT_ENCODING);
		
		long varNewValPtr = ngx_palloc(pool, newArray.length);
		//set back the transformed value
		ngx_http_clojure_mem_copy_to_addr(newArray, BYTE_ARRAY_OFFSET, varNewValPtr, newArray.length);
		ngx_http_clojure_mem_set_variable(r, nameNgxStrPtr, varNewValPtr, newArray.length);
		
		//returning value will be ignored when request is hijacked.
		return null;
	}

}
