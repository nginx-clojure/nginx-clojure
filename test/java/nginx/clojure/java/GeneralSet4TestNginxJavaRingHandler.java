package nginx.clojure.java;

import static nginx.clojure.MiniConstants.CONTENT_TYPE;
import static nginx.clojure.MiniConstants.HEADERS;
import static nginx.clojure.MiniConstants.NGX_HTTP_OK;
import static nginx.clojure.MiniConstants.QUERY_STRING;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

import nginx.clojure.ChannelCloseAdapter;
import nginx.clojure.Configurable;
import nginx.clojure.NginxClojureRT;
import nginx.clojure.NginxHttpServerChannel;
import nginx.clojure.util.NginxPubSubTopic;
import nginx.clojure.util.NginxPubSubTopic.PubSubListenerData;
import nginx.clojure.util.NginxPubSubListener;

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

		public Hello() {
			Runtime.getRuntime().addShutdownHook(new Thread(){
				public void run() {
					NginxClojureRT.log.error("JVM shutdown hook invoked!");
				}
			});
		}
		
		@Override
		public Object[] invoke(Map<String, Object> request) {
			return new Object[] { 
					NGX_HTTP_OK, //http status 200
					ArrayMap.create(CONTENT_TYPE, "text/plain"), //headers map
					"Hello, Java & Nginx!"  //response body can be string, File or Array/Collection of string or File
					};
		}
	}
	
	
	public static class MultipleChainHandler implements NginxJavaRingHandler {

		@SuppressWarnings("unused")
		private void sleep(int second) {
			try {
				Thread.sleep(second * 1000L);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		@Override
		public Object[] invoke(Map<String, Object> request) throws IOException {
			NginxJavaRequest r = (NginxJavaRequest)request;
//			NginxClojureRT.log.info("before hijack" + r.nativeCount());
			NginxHttpServerChannel channel = r.hijack(false);
//			NginxClojureRT.log.info("after hijack" + r.nativeCount());
			channel.sendHeader(200, null, true, false);
			channel.send("first part.\r\n", true, false);
			channel.send("second part.\r\n", true, false);
			channel.send("third part.\r\n", true, false);
			channel.send("last part.\r\n", true, true);
			NginxClojureRT.log.info("after send all" + r.nativeCount());
			return null;
		}
	}
	
	public static class Utf8MultipleChainHandler implements NginxJavaRingHandler {

		@Override
		public Object[] invoke(Map<String, Object> request) throws IOException {
			NginxJavaRequest r = (NginxJavaRequest)request;
//			System.out.println("before hijack" + r.nativeCount());
			NginxHttpServerChannel channel = r.hijack(false);
//			System.out.println("after hijack" + r.nativeCount());
			channel.sendHeader(200, ArrayMap.create("Content-Type", "text/plain").entrySet(), true, false);
			String s = "来1点中文，在utf8分隔下，中文字符会被截到不同的chain中";
			byte[] all = s.getBytes(Charset.forName("utf8"));
			int len = all.length;
			channel.send(all, 0, 1, true, false);
			len --;
			channel.send(all, 1, 10, true, false);
			len -= 10;
			channel.send(all, 11, len, true, true);
			return null;
		}
	}
	
	public static class Headers implements NginxJavaRingHandler {

		@SuppressWarnings({"rawtypes", "unchecked"})
		@Override
		public Object[] invoke(Map<String, Object> request) {
			Map requestHeaders = (Map) request.get(HEADERS);
			
			Map headers = ArrayMap.create("content-type", "text/plain",
					                      "my-header", requestHeaders == null ? null : requestHeaders.get("my-header"),
					                      "etag","e29b7ffb8a5325de60aed2d46a9d150b",
					                      "set-cookie", new String[] {"cookie1", "cookie2"},
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
	
	public static class Sub implements NginxJavaRingHandler {
		
		public static NginxPubSubTopic longPollPubSub = new NginxPubSubTopic("longpoll-topic");;
		
		public Sub() {
		}
		
		@Override
		public Object[] invoke(Map<String, Object> request) {
			NginxJavaRequest r = ((NginxJavaRequest)request);
			NginxHttpServerChannel channel = r.hijack(false);
			PubSubListenerData<NginxHttpServerChannel> pd = longPollPubSub.subscribe(channel, new NginxPubSubListener<NginxHttpServerChannel>() {
				@SuppressWarnings("rawtypes")
				@Override
				public void onMessage(String msg, NginxHttpServerChannel ch) throws IOException {
					ch.sendResponse(new Object[] { NGX_HTTP_OK,
							ArrayMap.create("content-type", "text/json"),
							msg});
					longPollPubSub.unsubscribe((PubSubListenerData)ch.getContext());
				}
			});
			channel.setContext(pd);
			return null;
		}
	}
	
	public static class Pub implements NginxJavaRingHandler {

		@Override
		public Object[] invoke(Map<String, Object> request) {
			Sub.longPollPubSub.publish(request.get(QUERY_STRING).toString());
			return new Object[] { NGX_HTTP_OK, null, "OK" };
		}
		
	}
	
	public static class SSESub implements NginxJavaRingHandler {
		
		public static NginxPubSubTopic ssePubSub = new NginxPubSubTopic("sse-topic");;
		
		@Override
		public Object[] invoke(Map<String, Object> request) throws IOException {
			NginxJavaRequest r = (NginxJavaRequest) request;
			NginxHttpServerChannel channel = r.hijack(true);
			PubSubListenerData<NginxHttpServerChannel> pd = ssePubSub.subscribe(channel, new NginxPubSubListener<NginxHttpServerChannel>() {
				@Override
				public void onMessage(String message, NginxHttpServerChannel ch) throws IOException {
					if ("shutdown!".equals(message)) {
						ch.send("data: "+message+"\r\n\r\n", true, true);
					}else if ("shutdownQuite!".equals(message)) {
						ch.close();
					}else {
						ch.send("data: "+message+"\r\n\r\n", true, false);
					}
				}
			});
			channel.setContext(pd);
			channel.addListener(channel, new ChannelCloseAdapter<NginxHttpServerChannel>() {
				@SuppressWarnings("rawtypes")
				@Override
				public void onClose(NginxHttpServerChannel ch) {
					ssePubSub.unsubscribe((PubSubListenerData) ch.getContext());
					NginxClojureRT.getLog().info("closing...." + ch.request().nativeRequest());
				}
			});
			
			channel.sendHeader(200, ArrayMap.create("Content-Type", "text/event-stream").entrySet(), true, false);
			channel.send("retry: 4500\r\n", true, false);
			return null;
		}
	}
	
	public static class SSEPub implements NginxJavaRingHandler {

		@Override
		public Object[] invoke(Map<String, Object> request) {
			SSESub.ssePubSub.publish(request.get(QUERY_STRING).toString());
			return new Object[] { NGX_HTTP_OK, null, "OK" };
		}
		
	}
	
	public static class HijackHello implements NginxJavaRingHandler {
		
		@Override
		public Object[] invoke(Map<String, Object> request) throws IOException {
			NginxHttpServerChannel channel = ((NginxJavaRequest)request).hijack(false);
			channel.sendResponse(new Object[] {NGX_HTTP_OK, null, "Hijack hello!"});
			return null;
		}
	}
	
	
	private Map<String, NginxJavaRingHandler> routing = new HashMap<String, NginxJavaRingHandler>();
	
	public GeneralSet4TestNginxJavaRingHandler() {
		routing.put("/hello", new Hello());
		routing.put("/hijackhello", new HijackHello());
		routing.put("/headers", new Headers());
		routing.put("/sub", new Sub());
		routing.put("/pub", new Pub());
		routing.put("/ssesub", new SSESub());
		routing.put("/ssepub", new SSEPub());
		routing.put("/file", new FileBytesHandler());
		routing.put("/upload", new UploadHandler());
		routing.put("/stream", new StreamingWriteHandler());
		routing.put("/mchain", new MultipleChainHandler());
		routing.put("/utf8mchain", new Utf8MultipleChainHandler());
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
