/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.charset.Charset;

public class RequestBodyFetcher implements RequestVarFetcher {
	
	public final static RequestKnownNameVarFetcher BODY_VAR_FETCHER = new RequestKnownNameVarFetcher("request_body");
	
	public final static RequestKnownNameVarFetcher BODY_FILE_FETCHER = new RequestKnownNameVarFetcher("request_body_file");
	
	public RequestBodyFetcher() {
	}
	
	@Override
	public Object fetch(long r, Charset encoding) {
		Object reqMethod = Constants.REQUEST_METHOD_FETCHER.fetch(r, encoding);
		if (Constants.GET == reqMethod) {
			return BODY_VAR_FETCHER.fetchAsStream(r);
		}
		String tmpfile = (String)BODY_FILE_FETCHER.fetch(r, encoding);
		if (tmpfile != null){
			System.out.println("tmp file:" + tmpfile);
			try {
				return new FileInputStream(tmpfile);
			} catch (FileNotFoundException e) {
				throw new RuntimeException("can not find tmp file", e);
			}
		}
		return BODY_VAR_FETCHER.fetchAsStream(r);
	}
}
