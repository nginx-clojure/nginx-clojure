package nginx.clojure;

import static nginx.clojure.MiniConstants.DEFAULT_ENCODING;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_REQ_POOL_OFFSET;
import static nginx.clojure.MiniConstants.NGX_HTTP_CLOJURE_STR_SIZE;
import static nginx.clojure.NginxClojureRT.UNSAFE;
import static nginx.clojure.NginxClojureRT.ngx_palloc;
import static nginx.clojure.NginxClojureRT.pushNGXLowcaseString;

import java.io.InputStream;
import java.nio.charset.Charset;


public class RequestUnknownNameVarFetcher extends RequestKnownNameVarFetcher {

	protected String name;
	
	public RequestUnknownNameVarFetcher(String name) {
		this.name = name;
	}

	@Override
	public Object fetch(long r, Charset encoding) {
		//TODO: use static temp byte[] to fake a c ngx_str pointer
		long pool = UNSAFE.getAddress(r + NGX_HTTP_CLOJURE_REQ_POOL_OFFSET);
		nameNgxStrPtr = ngx_palloc(pool, NGX_HTTP_CLOJURE_STR_SIZE);
		if (nameNgxStrPtr == 0) {
			throw new OutOfMemoryError("nginx OutOfMemoryError");
		}
		pushNGXLowcaseString(nameNgxStrPtr, name, DEFAULT_ENCODING, pool);
		return super.fetch(r, encoding);
	}
	
	@Override
	public InputStream fetchAsStream(long r) {
		if (nameNgxStrPtr == 0) {
			long pool = UNSAFE.getAddress(r + NGX_HTTP_CLOJURE_REQ_POOL_OFFSET);
			nameNgxStrPtr = ngx_palloc(pool, NGX_HTTP_CLOJURE_STR_SIZE);
			if (nameNgxStrPtr == 0) {
				throw new OutOfMemoryError("nginx OutOfMemoryError");
			}
			pushNGXLowcaseString(nameNgxStrPtr, name, DEFAULT_ENCODING, pool);
		}
		return super.fetchAsStream(r);
	}
}
