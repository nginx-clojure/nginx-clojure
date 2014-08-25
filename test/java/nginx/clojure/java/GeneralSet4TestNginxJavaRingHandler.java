package nginx.clojure.java;

import static nginx.clojure.MiniConstants.CONTENT_TYPE;
import static nginx.clojure.MiniConstants.DEFAULT_ENCODING;
import static nginx.clojure.MiniConstants.HEADERS;
import static nginx.clojure.MiniConstants.NGX_HTTP_OK;
import static nginx.clojure.MiniConstants.POST_EVENT_TYPE_COMPLEX_EVENT_IDX_START;
import static nginx.clojure.MiniConstants.QUERY_STRING;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import nginx.clojure.ChannelListener;
import nginx.clojure.NginxClojureRT;
import nginx.clojure.NginxClojureRT.AppEventMessageHandler;
import nginx.clojure.NginxHandler;
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
		public static final int SEVER_SENT_EVENTS = LONGPOLL_EVENT + 1;
		public static Set<NginxServerChannel> longpollSubscribers;
		public static Set<NginxServerChannel> serverSentEventSubscribers;
		
		public Init() {
		}
		
		@Override
		public Object[] invoke(Map<String, Object> request) {
			longpollSubscribers = Collections.newSetFromMap(new ConcurrentHashMap<NginxServerChannel, Boolean>());
			serverSentEventSubscribers = Collections.newSetFromMap(new ConcurrentHashMap<NginxServerChannel, Boolean>());
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
				for (NginxServerChannel channel : longpollSubscribers) {
					channel.sendResponse(new Object[] { NGX_HTTP_OK,
							ArrayMap.create("content-type", "text/json"),
							message});
				}
				longpollSubscribers.clear();
			}else if (tag == SEVER_SENT_EVENTS) {
				for (NginxServerChannel channel : serverSentEventSubscribers) {
					if ("shutdown!".equals(message)) {
						channel.send("data: "+message+"\r\n\r\n", true, true);
					}else if ("shutdownQuite!".equals(message)) {
						channel.close();
					}else if (message.startsWith("DirectByteBufferTest")) {
						ByteBuffer buffer = ByteBuffer.allocateDirect(len + "data: ".length()+4);
						buffer.put("data: ".getBytes());
						buffer.put(buf, off, len);
						buffer.put("\r\n\r\n".getBytes());
						buffer.flip();
						channel.send(buffer, true, false);
					}else if (message.startsWith("HeapByteBufferTest")) {
						ByteBuffer buffer = ByteBuffer.allocate(len + "data: ".length()+4);
						buffer.put("data: ".getBytes());
						buffer.put(buf, off, len);
						buffer.put("\r\n\r\n".getBytes());
						buffer.flip();
						channel.send(buffer, true, false);
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
			NginxJavaRequest r = ((NginxJavaRequest)request);
			NginxServerChannel channel = r.handler().hijack(r, false);
			Init.longpollSubscribers.add(channel);
			//nginx-clojure will ignore this return because we have hijacked the request.
			return null;
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
			NginxServerChannel channel = handler.hijack(r, false);
			Init.serverSentEventSubscribers.add(channel);
			channel.addListener(new ChannelListener<NginxServerChannel>() {
				@Override
				public void onClose(NginxServerChannel data) {
					Init.serverSentEventSubscribers.remove(data);
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
			NginxClojureRT.broadcastEvent(Init.SEVER_SENT_EVENTS, request.get(QUERY_STRING).toString());
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
