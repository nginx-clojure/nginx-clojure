/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure;

import java.nio.charset.Charset;
import static nginx.clojure.MiniConstants.CONTENT_TYPE_FETCHER;;

public class RequestCharacterEncodingFetcher implements RequestVarFetcher {
	
	public final static int CHARSET_OFFSET = " charset=".length() + 1;
	

	@Override
	public Object fetch(long r, Charset encoding) {
		String v = (String) CONTENT_TYPE_FETCHER.fetch(r, encoding);
		int sp = 0;
		if (v == null || (sp = v.indexOf("; charset=")) < 0) {
			return null;
		}
		sp += CHARSET_OFFSET;
		if (v.length() < sp) {
			return null;
		}
		return v.substring(sp);
	}

}
