package nginx.clojure.java;

import static nginx.clojure.MiniConstants.CONTENT_TYPE;
import static nginx.clojure.MiniConstants.DEFAULT_ENCODING;
import static nginx.clojure.MiniConstants.HEADERS;
import static nginx.clojure.MiniConstants.NGX_HTTP_OK;
import static nginx.clojure.MiniConstants.POST_EVENT_TYPE_COMPLEX_EVENT_IDX_START;
import static nginx.clojure.MiniConstants.QUERY_STRING;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import nginx.clojure.ChannelListener;
import nginx.clojure.NginxClojureRT;
import nginx.clojure.NginxClojureRT.AppEventMessageHandler;
import nginx.clojure.NginxHandler;
import nginx.clojure.NginxRequest;
import nginx.clojure.NginxServerChannel;

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
		public static final int LONGPOLL_EVENT = POST_EVENT_TYPE_COMPLEX_EVENT_IDX_START;
		public static final int SEVER_PUSH_EVENTS = LONGPOLL_EVENT + 1;
		public static Set<NginxRequest> longpollSubscribers;
		public static Set<NginxServerChannel> serverPushEventSubscribers;
		
		public Init() {
		}
		
		@Override
		public Object[] invoke(Map<String, Object> request) {
			//HashMap is enough without thread pool mode, 
			//otherwise we should use ConcurrentHashMap instead.
			longpollSubscribers = Collections.newSetFromMap(new HashMap<NginxRequest, Boolean>());
			serverPushEventSubscribers = Collections.newSetFromMap(new HashMap<NginxServerChannel, Boolean>());
			NginxClojureRT.setAppEventMessageHandler(this);
			return null;
		}

		@Override
		public void handleSimpleEvent(int tag, long data) {
		}

		@Override
		public void handleComplexEvent(int tag, byte[] buf, int off, int len) {
			String message = new String(buf, off, len, DEFAULT_ENCODING);
			if (tag == LONGPOLL_EVENT) {
				for (NginxRequest req : longpollSubscribers) {
					req.handler().completeAsyncResponse(
							req,
							new Object[] { NGX_HTTP_OK,
									ArrayMap.create("content-type", "text/json"),
									message});
					longpollSubscribers.clear();
				}
			}else if (tag == SEVER_PUSH_EVENTS) {
				for (NginxServerChannel channel : serverPushEventSubscribers) {
					if ("shutdown!".equals(message)) {
						channel.send("data: "+message+"\r\n\r\n", true, true);
					}else {
						channel.send("data: "+message+"\r\n\r\n", true, false);
					}
				}
			}
			
		}
	}
	
	public static class Sub implements NginxJavaRingHandler {
		
		@Override
		public Object[] invoke(Map<String, Object> request) {
			Init.longpollSubscribers.add(((NginxJavaRequest)request));
			//to tell nginx our work isn't done.
			//Init.handleComplexEvent will push the server event after we access uri http://localhost:8080/java/pube?message
			return Constants.ASYNC_TAG;
		}
	}
	
	public static class Pub implements NginxJavaRingHandler {

		@Override
		public Object[] invoke(Map<String, Object> request) {
			NginxClojureRT.broadcastEvent(Init.LONGPOLL_EVENT, request.get(QUERY_STRING).toString());
			return new Object[] { NGX_HTTP_OK, null, "OK" };
		}
		
	}
	
	public static class SubEvent implements NginxJavaRingHandler {

		@Override
		public Object[] invoke(Map<String, Object> request) {
			NginxJavaRequest r = (NginxJavaRequest) request;
			NginxHandler handler = r.handler();
			NginxServerChannel channel = handler.hijack(r, true);
			Init.serverPushEventSubscribers.add(channel);
			channel.addListener(new ChannelListener<NginxServerChannel>() {
				@Override
				public void onClose(NginxServerChannel data) {
					Init.serverPushEventSubscribers.remove(data);
					NginxClojureRT.getLog().info("closing...." + data.request().nativeRequest());
				}
			}, channel);
			channel.sendHeader(200, ArrayMap.create("Content-Type", "text/event-stream").entrySet(), true, false);
			channel.send("retry: 4500\r\n", true, false);
			return null;
		}
	}
	
	public static class PubEvent implements NginxJavaRingHandler {

		@Override
		public Object[] invoke(Map<String, Object> request) {
			NginxClojureRT.broadcastEvent(Init.SEVER_PUSH_EVENTS, request.get(QUERY_STRING).toString());
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
		routing.put("/sube", new SubEvent());
		routing.put("/pube", new PubEvent());
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
