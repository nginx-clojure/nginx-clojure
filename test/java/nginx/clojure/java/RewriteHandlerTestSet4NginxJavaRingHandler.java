package nginx.clojure.java;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
	
	
	public static class SimpleRewriteByBodyHandler   implements NginxJavaRingHandler {
		@Override
		public Object[] invoke(Map<String, Object> request) {
			NginxJavaRequest req = (NginxJavaRequest) request;
			InputStream in = (InputStream) req.get(Constants.BODY);
			if (in != null) {
				long nr = req.nativeRequest();
				BufferedReader reader;
				try {
					reader = new BufferedReader( new InputStreamReader(in, "utf-8"));
					//just for test no arguments check
					//read last line for test small or large body
					String up = null, l = null;
					while ( (l = reader.readLine()) != null) {
						up = l;
					}
					System.err.println("[SimpleRewriteByBodyHandler] up=" + up + ", body inputstream type = " + in.getClass());
					NginxClojureRT.setNGXVariable(nr, "myup", up);
				} catch (IOException e) {
					NginxClojureRT.UNSAFE.throwException(e);
				}
			}
			return Constants.PHRASE_DONE;
		}
	}
	
	
}
