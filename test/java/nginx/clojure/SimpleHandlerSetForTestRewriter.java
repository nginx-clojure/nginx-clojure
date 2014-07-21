package nginx.clojure;

import nginx.clojure.clj.Constants;
import nginx.clojure.clj.LazyRequestMap;
import clojure.lang.AFn;

public class SimpleHandlerSetForTestRewriter {

	public SimpleHandlerSetForTestRewriter() {
	}
	
	public static class MyRewriteProxyPassHandler extends AFn {
		@Override
		public Object invoke(Object arg) {
			LazyRequestMap req = (LazyRequestMap)arg;
			String myhost = computeMyHost(req);
			NginxClojureRT.setNGXVariable(req.nativeRequest(), "myhost", myhost);
			return Constants.PHRASE_DONE;
		}

		private String computeMyHost(LazyRequestMap req) {
			//compute a upstream name or host name;
			return null;
		}
	}
}
