package nginx.clojure.java;

import static nginx.clojure.MiniConstants.DEFAULT_ENCODING;
import static nginx.clojure.MiniConstants.HEADERS;
import static nginx.clojure.java.Constants.PHASE_DONE;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.xml.bind.DatatypeConverter;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import nginx.clojure.Configurable;
import nginx.clojure.NginxHttpServerChannel;
import nginx.clojure.SuspendExecution;

public class AccessHandlerTestSet4NginxJavaRingHandler {

	
	public static class SimpleDeny  implements NginxJavaRingHandler {
		@Override
		public Object[] invoke(Map<String, Object> request) {
			return new Object[] {403, null, null};
		}
	}

	public static class ExceptionInAccessHandler implements NginxJavaRingHandler {

		@Override
		public Object[] invoke(Map<String, Object> request) {
			throw new RuntimeException("ExceptionInAccessHandler");
		}
		
	}
	
	/**
	 * This is an  example of HTTP basic Authentication.
	 * It will require visitor to input a user name (xfeep) and password (hello!) 
	 * otherwise it will return 401 Unauthorized or BAD USER & PASSWORD 
	 */
	public static class BasicAuthHandler implements NginxJavaRingHandler {

		@SuppressWarnings("rawtypes")
		@Override
		public Object[] invoke(Map<String, Object> request) {
			String auth = (String) ((Map)request.get(HEADERS)).get("authorization");
			if (auth == null) {
				return new Object[] { 401, ArrayMap.create("www-authenticate", "Basic realm=\"Secure Area\""),
						"<HTML><BODY><H1>401 Unauthorized.</H1></BODY></HTML>" };
			}
			String[] up = new String(DatatypeConverter.parseBase64Binary(auth.substring("Basic ".length())), DEFAULT_ENCODING).split(":");
			if (up[0].equals("xfeep") && up[1].equals("hello!")) {
				return PHASE_DONE;
			}
			return new Object[] { 401, ArrayMap.create("www-authenticate", "Basic realm=\"Secure Area\""),
			"<HTML><BODY><H1>401 Unauthorized BAD USER & PASSWORD.</H1></BODY></HTML>" };
		} 
	}
	
	public static class ConfigurableBasicAuthHandler implements NginxJavaRingHandler, Configurable {

		String user;
		String password;
		
		@SuppressWarnings("rawtypes")
		@Override
		public Object[] invoke(Map<String, Object> request) {
			String auth = (String) ((Map)request.get(HEADERS)).get("authorization");
			if (auth == null) {
				return new Object[] { 401, ArrayMap.create("www-authenticate", "Basic realm=\"Secure Area\""),
						"<HTML><BODY><H1>401 Unauthorized.</H1></BODY></HTML>" };
			}
			String[] up = new String(DatatypeConverter.parseBase64Binary(auth.substring("Basic ".length())), DEFAULT_ENCODING).split(":");
			if (up[0].equals(user) && up[1].equals(password)) {
				return PHASE_DONE;
			}
			return new Object[] { 401, ArrayMap.create("www-authenticate", "Basic realm=\"Secure Area\""),
			"<HTML><BODY><H1>401 Unauthorized BAD USER & PASSWORD.</H1></BODY></HTML>" };
		}

		@Override
		public void config(Map<String, String> properties) {
			user = properties.get("user");
			password = properties.get("password");
		} 
	}
	
	
	public static class BasicAuthWithRemoteFetchHandler implements NginxJavaRingHandler {
		@Override
		public Object[] invoke(Map<String, Object> request) throws SuspendExecution {

			CloseableHttpClient httpclient = HttpClients.createDefault();
			RequestConfig requestConfig = RequestConfig.custom().setConnectionRequestTimeout(10000).setConnectTimeout(10000)
					.setSocketTimeout(10000).build();
			HttpGet httpget = new HttpGet("https://www.apache.org/dist/httpcomponents/httpclient/RELEASE_NOTES-4.3.x.txt");
			httpget.setConfig(requestConfig);
			httpget.setHeader("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/37.0.2062.94 Safari/537.36");
			CloseableHttpResponse response = null;
			try {
				response = httpclient.execute(httpget);
				InputStream in = response.getEntity().getContent();
				byte[] buf = new byte[1024];
				int c = 0;
				int total = 0;
				while ((c = in.read(buf)) > 0) {
					total += c;
				}
				if (total != 77342) {
					throw new RuntimeException("bad total bytes!");
				}
				return Constants.PHASE_DONE;
			} catch(Throwable e) {
				throw new RuntimeException("BasicAuthWithRemoteFetchHandler ioexception", e);
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
	
	public static class HijackBasicAuthHandler implements NginxJavaRingHandler, Configurable {
		
		boolean ignoreFilter = false;
		ExecutorService executorService = Executors.newFixedThreadPool(16);
		
		@Override
		public void config(Map<String, String> properties) {
			ignoreFilter = Boolean.getBoolean(properties.get("ignoreFilter"));
		}

		@SuppressWarnings("rawtypes")
		@Override
		public Object[] invoke(Map<String, Object> request) throws IOException {
			NginxJavaRequest r = (NginxJavaRequest)request;
			final NginxHttpServerChannel channel = r.hijack(ignoreFilter);
			final String auth = (String) ((Map)request.get(HEADERS)).get("authorization");
			executorService.submit(new Runnable() {
				@Override
				public void run() {
					try {
						if (auth != null) {
							String[] up = new String(DatatypeConverter.parseBase64Binary(auth.substring("Basic ".length())), DEFAULT_ENCODING).split(":");
							if (up[0].equals("xfeep") && up[1].equals("hello!")) {
								channel.sendResponse(PHASE_DONE);
								return;
							}
						}
						channel.sendResponse(new Object[] { 401, ArrayMap.create("www-authenticate", "Basic realm=\"Secure Area\""),
			"<HTML><BODY><H1>401 Unauthorized BAD USER & PASSWORD.</H1></BODY></HTML>" });
					}catch(Throwable e) {
						try {
							channel.sendResponse(500);
						} catch (IOException e1) {
							// TODO Auto-generated catch block
							e1.printStackTrace();
						}
						e.printStackTrace();
					}
					
				}
			});
			return null;
		}
		
	}

}
