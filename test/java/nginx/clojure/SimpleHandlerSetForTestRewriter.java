package nginx.clojure;

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
			return NginxClojureRT.PHRASE_DONE;
		}

		private String computeMyHost(LazyRequestMap req) {
			//compute a upstream name or host name;
			return null;
		}
	}
}
