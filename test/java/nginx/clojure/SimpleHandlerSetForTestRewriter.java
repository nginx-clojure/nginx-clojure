package nginx.clojure;

import nginx.clojure.clj.Constants;
import clojure.lang.AFn;

public class SimpleHandlerSetForTestRewriter {

	public SimpleHandlerSetForTestRewriter() {
	}
	
	public static class MyRewriteProxyPassHandler extends AFn {
		@Override
		public Object invoke(Object arg) {
			NginxRequest req = (NginxRequest)arg;
			String myhost = computeMyHost(req);
			NginxClojureRT.setNGXVariable(req.nativeRequest(), "myhost", myhost);
			return Constants.PHRASE_DONE;
		}

		private String computeMyHost(NginxRequest req) {
			//compute a upstream name or host name;
			return null;
		}
	}
}
