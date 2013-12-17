package nginx.clojure;

import static nginx.clojure.Constants.HTTP_METHODS;
import static nginx.clojure.Constants.NGX_HTTP_CLOJURE_REQ_METHOD_OFFSET;
import static nginx.clojure.MemoryUtil.fetchNGXInt;

import java.nio.charset.Charset;

public class RequestMethodFetcher implements RequestVarFetcher {

	
	@Override
	public Object fetch(long r, Charset encoding) {
		int methodIdx = 0;
		int methodCode = fetchNGXInt(r + NGX_HTTP_CLOJURE_REQ_METHOD_OFFSET);
		while (methodCode > 1) {
			methodCode = methodCode >> 1;
			methodIdx ++;
		}
		if (methodIdx >=  HTTP_METHODS.length){
			return HTTP_METHODS[0];
		}else {
			return HTTP_METHODS[methodIdx];
		}
	}

}
