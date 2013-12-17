package nginx.clojure;

import java.nio.charset.Charset;

import static nginx.clojure.MemoryUtil.*;
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
		long varValPtr = ngx_http_clojure_mem_get_variable(r, nameNgxStrPtr);
		//TODO: handle big endian and larger variable 
		int len = UNSAFE.getByte(varValPtr);
		if (len < 0){
			len &= 0xffffffffL;
		}
		return fetchString(varValPtr + NGX_HTTP_CLOJURE_UINT_SIZE, len, DEFAULT_ENCODING);
	}
	
}