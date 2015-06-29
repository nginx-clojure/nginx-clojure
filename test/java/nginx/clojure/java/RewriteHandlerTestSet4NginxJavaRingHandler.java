package nginx.clojure.java;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import nginx.clojure.Configurable;
import nginx.clojure.NginxClojureRT;
import nginx.clojure.NginxHttpServerChannel;

public class RewriteHandlerTestSet4NginxJavaRingHandler {
	
	public static class SimpleRewriteHandler implements NginxJavaRingHandler {

		@Override
		public Object[] invoke(Map<String, Object> request) {
			NginxJavaRequest r = (NginxJavaRequest) request;
			r.setVariable("myvar", "Hello");
			r.setVariable("myName", "Xfeep");
			System.out.println("SimpleRewriteHandler, myname" + r.getVariable("myName"));
			return Constants.PHASE_DONE;
		}
		
	}
	
	public static class SimpleConfigurableRewriteHandler implements NginxJavaRingHandler, Configurable {

		String myvar;
		String myName;
		
		@Override
		public void config(Map<String, String> properties) {
			myvar = properties.get("myvar");
			myName = properties.get("myName");
		}

		@Override
		public Object[] invoke(Map<String, Object> request) throws IOException {
			NginxJavaRequest r = (NginxJavaRequest) request;
			r.setVariable("myvar", myvar);
			r.setVariable("myName", myName);
			System.out.println("SimpleConfigurableRewriteHandler, myname" + r.getVariable("myName"));
			return Constants.PHASE_DONE;
		}
	}
	
	public static class SimpleVarHandler   implements NginxJavaRingHandler {
		@Override
		public Object[] invoke(Map<String, Object> request) {
			NginxJavaRequest r = (NginxJavaRequest) request;
			r.setVariable("myvar",  r.getVariable("myvar") + ","+ r.getVariable("myname") + "!" );
			return new Object[] {Constants.NGX_HTTP_OK,  ArrayMap.create("content-type", "text/plain") , r.getVariable( "myvar") };
		}
	}
	
	public static class Rw implements NginxJavaRingHandler {

		@Override
		public Object[] invoke(Map<String, Object> request) {
			NginxJavaRequest r = (NginxJavaRequest) request;
			r.setVariable("myvar", "Hello");
			r.setVariable("myName", r.getVariable("var"));
			return Constants.PHASE_DONE;
		}
		
	}
	
	public static class Sh   implements NginxJavaRingHandler {
		@Override
		public Object[] invoke(Map<String, Object> request) {
			NginxJavaRequest r = (NginxJavaRequest) request;
			r.setVariable("myvar",  r.getVariable("myvar") + ","+ r.getVariable("myName") + "!" );
			System.out.println(request.get(Constants.URI));
			return new Object[] {Constants.NGX_HTTP_OK,  ArrayMap.create("content-type", "text/plain") , r.getVariable( "myvar") };
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
			return Constants.PHASE_DONE;
		}
	}
	
	public static class SimpleHijackedRewriteHandler implements NginxJavaRingHandler,Configurable {

		boolean ignoreFilter = false;
		boolean continueToContentHandler = true;
		ExecutorService executorService = Executors.newFixedThreadPool(16);
		
		@Override
		public Object[] invoke(Map<String, Object> request) throws IOException {
			final NginxJavaRequest r = (NginxJavaRequest)request;
			final NginxHttpServerChannel channel = r.hijack(ignoreFilter);
			executorService.submit(new Runnable() {
				@Override
				public void run() {
					try {
						if (continueToContentHandler) {
							r.setVariable("myvar", "Hello");
							r.setVariable("myName", "Xfeep");
							channel.sendResponse(Constants.PHASE_DONE);
//							NginxClojureRT.postResponseEvent(r, r.handler().toNginxResponse(r, Constants.PHASE_DONE));
						}else {
							channel.sendResponse(new Object[] { 400, ArrayMap.create("Content-Type", "text/plain"),
									"hijacked rewrite handler no pass to content handler!" });
						}
					}catch(IOException e) {
						try {
							channel.close();
						} catch (IOException e1) {
							e1.printStackTrace();
						}
					}
					
				}
			});
			return null;
		}

		@Override
		public void config(Map<String, String> properties) {
			if (properties.containsKey("ignoreFilter")) {
				ignoreFilter = Boolean.parseBoolean(properties.get("ignoreFilter"));
			}
			
			if (properties.containsKey("continueToContentHandler")) {
				continueToContentHandler = Boolean.parseBoolean(properties.get("continueToContentHandler"));
			}
		}
		
	}
	
	
}
