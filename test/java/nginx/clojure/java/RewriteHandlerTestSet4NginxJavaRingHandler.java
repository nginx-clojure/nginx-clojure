package nginx.clojure.java;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import nginx.clojure.Configurable;
import nginx.clojure.NginxClojureRT;
import nginx.clojure.NginxHttpServerChannel;
import nginx.clojure.SuspendExecution;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

public class RewriteHandlerTestSet4NginxJavaRingHandler {
	
	public static class SimpleRewriteHandler implements NginxJavaRingHandler {

		@Override
		public Object[] invoke(Map<String, Object> request) {
			NginxJavaRequest r = (NginxJavaRequest) request;
			r.setVariable("myvar", "Hello");
			r.setVariable("myName", "Xfeep");
			System.out.println("SimpleRewriteHandler, myname" + r.getVariable("myName"));
			System.out.println("request_id" +  r.getVariable("request_id"));
			return Constants.PHASE_DONE;
		}
		
	}
	
	public static class ExceptionInRewriteHandler implements NginxJavaRingHandler {

		@Override
		public Object[] invoke(Map<String, Object> request) throws IOException {
			throw new RuntimeException("ExceptionInRewriteHandler");
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
	
	public static class FetchRemoteTextRewriteHandler implements NginxJavaRingHandler {

		@Override
		public Object[] invoke(Map<String, Object> request) throws IOException, SuspendExecution {

			CloseableHttpClient httpclient = HttpClients.createDefault();
//			HttpGet httpget = new HttpGet("http://cn.bing.com/");
			HttpGet httpget = new HttpGet("https://www.apache.org/dist/httpcomponents/httpclient/RELEASE_NOTES-4.3.x.txt");
			httpget.setConfig(RequestConfig.custom().setConnectTimeout(10000).setSocketTimeout(10000).build());
			CloseableHttpResponse response = null;
			try {
				response = httpclient.execute(httpget);
				InputStream in = response.getEntity().getContent();
				byte[] buf = new byte[1024];
				int c = 0;
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				while ((c = in.read(buf)) > 0) {
					out.write(buf, 0, c);
				}
				in.close();
				((NginxJavaRequest)request).setVariable("myvar", new String(out.toByteArray(), "utf-8"));
				((NginxJavaRequest)request).setVariable("myname", "Xfeep");
				return Constants.PHASE_DONE;
			}finally {
				if (httpclient != null) {
					try {
						httpclient.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		
		}
		
	}
	
	
}
