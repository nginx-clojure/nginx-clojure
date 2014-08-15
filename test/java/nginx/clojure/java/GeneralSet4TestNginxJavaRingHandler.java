package nginx.clojure.java;

import static nginx.clojure.MiniConstants.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import nginx.clojure.NginxClojureRT;
import nginx.clojure.NginxClojureRT.AppEventMessageHandler;

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
	
	public static class Init implements NginxJavaRingHandler, AppEventMessageHandler {
		
		public static Set<Long> subscribers;
		
		@Override
		public Object[] invoke(Map<String, Object> request) {
			//HashMap is enough without thread pool mode, 
			//otherwise we should use ConcurrentHashMap instead.
			subscribers = Collections.newSetFromMap(new HashMap<Long, Boolean>());
			NginxClojureRT.setAppEventMessageHandler(this);
			return null;
		}

		@Override
		public void handleSimpleEvent(int tag, long data) {
		}

		@Override
		public void handleComplexEvent(int tag, byte[] buf, int off, int len) {
			for (Long l : subscribers) {
				NginxJavaHandler.completeAsyncResponse(
						l,
						new Object[] { NGX_HTTP_OK,
								ArrayMap.create("content-type", "text/json"),
								new String(buf, off, len, DEFAULT_ENCODING) });
			}
			subscribers.clear();
		}
	}
	
	public static class Sub implements NginxJavaRingHandler {
		
		@Override
		public Object[] invoke(Map<String, Object> request) {
			Init.subscribers.add(((NginxJavaRequest)request).nativeRequest());
			//to tell nginx our work isn't done.
			return Constants.ASYNC_TAG;
		}
	}
	
	public static class Pub implements NginxJavaRingHandler {

		@Override
		public Object[] invoke(Map<String, Object> request) {
			NginxClojureRT.broadcastEvent(request.get(QUERY_STRING).toString());
			return new Object[] { NGX_HTTP_OK, null, "OK" };
		}
		
	}
	
	
	private Map<String, NginxJavaRingHandler> routing = new HashMap<String, NginxJavaRingHandler>();
	
	public GeneralSet4TestNginxJavaRingHandler() {
		Init init = new Init();
		init.invoke(null);
		routing.put("/hello", new Hello());
		routing.put("/headers", new Headers());
		routing.put("/sub", new Sub());
		routing.put("/pub", new Pub());
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
