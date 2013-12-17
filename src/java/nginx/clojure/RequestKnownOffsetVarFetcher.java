package nginx.clojure;

import java.nio.charset.Charset;

import static nginx.clojure.MemoryUtil.*;

public  class RequestKnownOffsetVarFetcher implements RequestVarFetcher {

	private long offset;
	
	public RequestKnownOffsetVarFetcher(long offset) {
		this.offset = offset;
	}
	
	@Override
	public Object fetch(long r, Charset encoding) {
		return fetchNGXString(r + offset, encoding);
	}
	
}