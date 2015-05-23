package nginx.clojure.java;

import static nginx.clojure.MiniConstants.CONTENT_TYPE;
import static nginx.clojure.MiniConstants.DEFAULT_ENCODING;
import static nginx.clojure.MiniConstants.HEADERS;
import static nginx.clojure.MiniConstants.NGX_HTTP_OK;
import static nginx.clojure.MiniConstants.POST_EVENT_TYPE_COMPLEX_EVENT_IDX_START;
import static nginx.clojure.MiniConstants.QUERY_STRING;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import nginx.clojure.AppEventListenerManager.Listener;
import nginx.clojure.AppEventListenerManager.PostedEvent;
import nginx.clojure.ChannelCloseAdapter;
import nginx.clojure.NginxClojureRT;
import nginx.clojure.NginxHandler;
import nginx.clojure.NginxHttpServerChannel;

import org.codehaus.jackson.map.ObjectMapper;

public class GeneralSet4TestNginxJavaRingHandler implements NginxJavaRingHandler {
	
	public static class JVMInitHandler implements NginxJavaRingHandler {
		@Override
		public Object[] invoke(Map<String, Object> params) {
			NginxClojureRT.log.info("JVMInitHandler invoked!");
			return null;
		}
	}
	
	public static class JVMExitHandler implements NginxJavaRingHandler {
		@Override
		public Object[] invoke(Map<String, Object> params) {
			NginxClojureRT.log.info("JVMExitHandler invoked!");
			return null;
		}
	}

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
			Map bmap = new HashMap(request);
			bmap.putAll(requestHeaders);
			try {
				String body = new ObjectMapper().writeValueAsString(bmap);
				return new Object[] {NGX_HTTP_OK, headers, body};
			} catch (Throwable e) {
				throw new RuntimeException(e);
			} 
		}
		
	}
	
	public static class Init implements NginxJavaRingHandler, Listener {
		public static final int LONGPOLL_EVENT = POST_EVENT_TYPE_COMPLEX_EVENT_IDX_START;
		public static final int SEVER_SENT_EVENTS = LONGPOLL_EVENT + 1;
		public static Set<NginxHttpServerChannel> longpollSubscribers;
		public static Set<NginxHttpServerChannel> serverSentEventSubscribers;
		
		public Init() {
		}
		
		@Override
		public Object[] invoke(Map<String, Object> request) {
			longpollSubscribers = Collections.newSetFromMap(new ConcurrentHashMap<NginxHttpServerChannel, Boolean>());
			serverSentEventSubscribers = Collections.newSetFromMap(new ConcurrentHashMap<NginxHttpServerChannel, Boolean>());
			NginxClojureRT.getAppEventListenerManager().addListener(this);
			return null;
		}
		
		@Override
		public void onEvent(PostedEvent event) {
			if (event.tag != LONGPOLL_EVENT && event.tag != SEVER_SENT_EVENTS) {
				return;
			}
			String message = new String((byte[])event.data, event.offset, event.length, DEFAULT_ENCODING);
			if (event.tag == LONGPOLL_EVENT) {
				for (NginxHttpServerChannel channel : longpollSubscribers) {
					channel.sendResponse(new Object[] { NGX_HTTP_OK,
							ArrayMap.create("content-type", "text/json"),
							message});
				}
				longpollSubscribers.clear();
			}else if (event.tag == SEVER_SENT_EVENTS) {
				for (NginxHttpServerChannel channel : serverSentEventSubscribers) {
					if ("shutdown!".equals(message)) {
						channel.send("data: "+message+"\r\n\r\n", true, true);
					}else if ("shutdownQuite!".equals(message)) {
						channel.close();
					}else if (message.startsWith("DirectByteBufferTest")) {
						ByteBuffer buffer = ByteBuffer.allocateDirect(event.length + "data: ".length()+4);
						buffer.put("data: ".getBytes());
						buffer.put((byte[])event.data, event.offset, event.length);
						buffer.put("\r\n\r\n".getBytes());
						buffer.flip();
						channel.send(buffer, true, false);
					}else if (message.startsWith("HeapByteBufferTest")) {
						ByteBuffer buffer = ByteBuffer.allocate(event.length + "data: ".length()+4);
						buffer.put("data: ".getBytes());
						buffer.put((byte[])event.data, event.offset, event.length);
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
			NginxHttpServerChannel channel = r.handler().hijack(r, false);
			Init.longpollSubscribers.add(channel);
			//nginx-clojure will ignore this return because we have hijacked the request.
			return null;
		}
	}
	
	public static class Pub implements NginxJavaRingHandler {

		@Override
		public Object[] invoke(Map<String, Object> request) {
			/*
			 * Or use NginxClojureRT.broadcastEvent(Init.LONGPOLL_EVENT,request.get(QUERY_STRING).toString());
			 */
			PostedEvent event = new PostedEvent(Init.LONGPOLL_EVENT, request.get(QUERY_STRING).toString());
			NginxClojureRT.getAppEventListenerManager().broadcast(event);
			return new Object[] { NGX_HTTP_OK, null, "OK" };
		}
		
	}
	
	public static class SSESub implements NginxJavaRingHandler {

		@Override
		public Object[] invoke(Map<String, Object> request) {
			NginxJavaRequest r = (NginxJavaRequest) request;
			NginxHandler handler = r.handler();
			NginxHttpServerChannel channel = handler.hijack(r, true);
			channel.addListener(channel, new ChannelCloseAdapter<NginxHttpServerChannel>() {
				@Override
				public void onClose(NginxHttpServerChannel data) {
					Init.serverSentEventSubscribers.remove(data);
					NginxClojureRT.getLog().info("closing...." + data.request().nativeRequest());
				}
			});
			Init.serverSentEventSubscribers.add(channel);
			channel.sendHeader(200, ArrayMap.create("Content-Type", "text/event-stream").entrySet(), true, false);
			channel.send("retry: 4500\r\n", true, false);
			return null;
		}
	}
	
	public static class SSEPub implements NginxJavaRingHandler {

		@Override
		public Object[] invoke(Map<String, Object> request) {
			/*
			 * Or use NginxClojureRT.broadcastEvent(Init.SEVER_SENT_EVENTS, request.get(QUERY_STRING).toString());
			 */
			PostedEvent event = new PostedEvent(Init.SEVER_SENT_EVENTS, request.get(QUERY_STRING).toString());
			NginxClojureRT.getAppEventListenerManager().broadcast(event);
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
		routing.put("/ssesub", new SSESub());
		routing.put("/ssepub", new SSEPub());
	}

	@Override
	public Object[] invoke(Map<String, Object> request) throws IOException {
		String uri = (String) request.get("uri");
		String path = uri.substring(uri.lastIndexOf('/'));
		NginxJavaRingHandler handler = routing.get(path);
		if (handler == null) {
			return new Object[] {200, null, "OK"};
		}
		return handler.invoke(request);
	}
	
}
