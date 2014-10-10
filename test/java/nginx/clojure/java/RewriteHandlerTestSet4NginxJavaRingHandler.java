package nginx.clojure.java;

import java.util.Map;

import nginx.clojure.NginxClojureRT;

public class RewriteHandlerTestSet4NginxJavaRingHandler {
	
	public static class SimpleRewriteHandler implements NginxJavaRingHandler {

		@Override
		public Object[] invoke(Map<String, Object> request) {
			NginxJavaRequest r = (NginxJavaRequest) request;
			NginxClojureRT.setNGXVariable(r.nativeRequest(), "myvar", "Hello");
			return Constants.PHRASE_DONE;
		}
		
	}
	
	public static class SimpleVarHandler   implements NginxJavaRingHandler {
		@Override
		public Object[] invoke(Map<String, Object> request) {
			NginxJavaRequest r = (NginxJavaRequest) request;
			long nr = r.nativeRequest();
			NginxClojureRT.setNGXVariable(nr, "myvar", NginxClojureRT.getNGXVariable(nr, "myvar") + ",Xfeep!");
			return new Object[] {Constants.NGX_HTTP_OK,  ArrayMap.create("content-type", "text/plain") , NginxClojureRT.getNGXVariable(nr, "myvar") };
		}
	}
	
}
