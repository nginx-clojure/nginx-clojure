package nginx.clojure.java;

import static nginx.clojure.MiniConstants.*;

import java.util.HashMap;
import java.util.Map;

import org.codehaus.jackson.map.ObjectMapper;

public class GeneralSet4TestNginxJavaRingHandler implements NginxJavaRingHandler {

	public static class Hello implements NginxJavaRingHandler {

		@Override
		public Object[] invoke(Map<String, Object> request) {
			return new Object[] { 
					NGX_HTTP_OK, //http status 200
					ArrayMap.create(CONTENT_TYPE, "text/plain"), //headers map
					"Hello, Java & Nginx!"  //response body can be string, File or Array/Collection of string or File
					};
		}
	}
	
	public static class Headers implements NginxJavaRingHandler {

		@SuppressWarnings("rawtypes")
		@Override
		public Object[] invoke(Map<String, Object> request) {
			Map requestHeaders = (Map) request.get(HEADERS);
			
			Map headers = ArrayMap.create("content-type", "text/plain",
					                      "my-header", requestHeaders == null ? null : requestHeaders.get("my-header"),
					                      "etag","e29b7ffb8a5325de60aed2d46a9d150b",
					                      "cache-control", new String[]{"no-store", "no-cache"});
			try {
				return new Object[] {NGX_HTTP_OK, headers, new ObjectMapper().writeValueAsString(request)};
			} catch (Throwable e) {
				throw new RuntimeException(e);
			} 
		}
		
	}
	
	private Map<String, NginxJavaRingHandler> routing = new HashMap<String, NginxJavaRingHandler>();
	
	public GeneralSet4TestNginxJavaRingHandler() {
		routing.put("/hello", new Hello());
		routing.put("/headers", new Headers());
	}

	@Override
	public Object[] invoke(Map<String, Object> request) {
		String uri = (String) request.get("uri");
		String path = uri.substring(uri.lastIndexOf('/'));
		NginxJavaRingHandler handler = routing.get(path);
		if (handler == null) {
			return new Object[] {200, null, "OK"};
		}
		return handler.invoke(request);
	}
	
}
