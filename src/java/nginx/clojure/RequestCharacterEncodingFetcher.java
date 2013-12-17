package nginx.clojure;

import java.nio.charset.Charset;
import static nginx.clojure.Constants.CONTENT_TYPE_FETCHER;;

public class RequestCharacterEncodingFetcher implements RequestVarFetcher {

	@Override
	public Object fetch(long r, Charset encoding) {
		String v = (String) CONTENT_TYPE_FETCHER.fetch(r, encoding);
		int sp = 0;
		if (v == null || (sp = v.indexOf(";")) < 0) {
			return null;
		}
		return v.substring(sp + 1);
	}

}
