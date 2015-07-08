package nginx.clojure.java;

import static nginx.clojure.MiniConstants.CONTENT_TYPE;
import static nginx.clojure.MiniConstants.DEFAULT_ENCODING;
import static nginx.clojure.MiniConstants.HEADERS;
import static nginx.clojure.MiniConstants.NGX_HTTP_OK;
import static nginx.clojure.MiniConstants.POST_EVENT_TYPE_COMPLEX_EVENT_IDX_START;
import static nginx.clojure.MiniConstants.QUERY_STRING;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import nginx.clojure.AppEventListenerManager.Listener;
import nginx.clojure.AppEventListenerManager.PostedEvent;
import nginx.clojure.ChannelCloseAdapter;
import nginx.clojure.Configurable;
import nginx.clojure.NginxClojureRT;
import nginx.clojure.NginxHttpServerChannel;

import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.FileUpload;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.RequestContext;
import org.apache.commons.fileupload.util.Streams;
import org.codehaus.jackson.map.ObjectMapper;

public class GeneralSet4TestNginxJavaRingHandler implements NginxJavaRingHandler, Configurable {
	
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
	
	public static class UploadHandler implements NginxJavaRingHandler {

		@Override
		public Object[] invoke(final Map<String, Object> request) throws IOException {
			final InputStream in = (InputStream)request.get("body");
			FileUpload fileUpload = new FileUpload();
			try {
				FileItemIterator fi = fileUpload.getItemIterator(new RequestContext() {
					
					@Override
					public InputStream getInputStream() throws IOException {
						return in;
					}
					
					@Override
					public String getContentType() {
						return (String)request.get("content-type");
					}
					
					@Override
					public int getContentLength() {
						String l = (String)request.get("content-length");
						return l == null ? -1 : Integer.parseInt(l);
					}
					
					@Override
					public String getCharacterEncoding() {
						return "utf-8";
					}
				});
				StringBuilder sb = new StringBuilder();
				while (fi.hasNext()) {
					FileItemStream fis = fi.next();
					sb.append(fis.getFieldName()).append(":{");
					if (fis.isFormField()) { //just a form field
						sb.append("formFiled=true,").append("value=").append(Streams.asString(fis.openStream(), "utf-8")).append("}<br>");
					}else { //file data
						sb.append("file=true,").append("filename=").append(fis.getName()).append(",content-type=").append(fis.getContentType())
						.append("file content=").append(Streams.asString(fis.openStream(), "utf-8")).append("}<br>");
					}
				}
				
				return new Object[] {200, ArrayMap.create("content-type", "text/html"), sb.toString()};
				
			} catch (FileUploadException e) {
				NginxClojureRT.log.error("can not parse file upload", e);
				return new Object[] {500, null, null};
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
		public void onEvent(PostedEvent event) throws IOException {
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
			NginxHttpServerChannel channel = r.hijack(false);
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
		public Object[] invoke(Map<String, Object> request) throws IOException {
			NginxJavaRequest r = (NginxJavaRequest) request;
			NginxHttpServerChannel channel = r.hijack(true);
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
		routing.put("/file", new FileBytesHandler());
		routing.put("/upload", new UploadHandler());
		routing.put("/stream", new StreamingWriteHandler());
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

	@Override
	public void config(Map<String, String> properties) {
		for (NginxJavaRingHandler h : routing.values()) {
			if (h instanceof Configurable) {
				((Configurable)h).config(properties);
			}
		}
	}
	
}
