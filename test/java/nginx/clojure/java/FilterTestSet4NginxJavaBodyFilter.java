/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.java;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import nginx.clojure.Coroutine;
import nginx.clojure.MiniConstants;
import nginx.clojure.NginxChainWrappedInputStream;
import nginx.clojure.NginxClojureRT;
import nginx.clojure.NginxRequest;
import nginx.clojure.anno.Suspendable;

public class FilterTestSet4NginxJavaBodyFilter {

	public FilterTestSet4NginxJavaBodyFilter() {
	}
	
	public static class ReadOnlyBodyFilter implements NginxJavaBodyFilter {

		@Override
		public Object[] doFilter(Map<String, Object> request, InputStream bodyChunk, boolean isLast) throws IOException {
			
			//read first non-empty line of every response body chunk and print it
			BufferedReader reader = new BufferedReader(new InputStreamReader(bodyChunk, MiniConstants.DEFAULT_ENCODING));
			String line = null;
			while ( (line = reader.readLine()) != null && line.trim().length() == 0)
				;
			NginxClojureRT.getLog().info("first line of response chunk: %s",  line);
			
			((NginxChainWrappedInputStream)bodyChunk).rewind();
			if (isLast) {
				return new Object[] {200, null, bodyChunk};
			}else {
				return new Object[] {null, null, bodyChunk};
			}
		}
		
	}
	
	public static class StringFacedUppercaseBodyFilter extends StringFacedJavaBodyFilter {
		@Override
		protected Object[] doFilter(Map<String, Object> request, String body, boolean isLast) throws IOException {
			if (isLast) {
				return new Object[] {200, null, body.toUpperCase()};
			}else {
				return new Object[] {null, null, body.toUpperCase()};
			}
		}
	}
	
	public static class WholeBodyValiator implements NginxJavaBodyFilter {

		@Override
		public Object[] doFilter(Map<String, Object> request, InputStream bodyChunk, boolean isLast) throws IOException {
			@SuppressWarnings("unchecked")
			List<byte[]> chunks = (List<byte[]>) request.get("chunks");
			if (chunks == null) {
				request.put("chunks", chunks = new ArrayList<>());
			}
			
			byte[] chunk = new byte[bodyChunk.available()];
			bodyChunk.read(chunk);
			chunks.add(chunk);
			
			NginxClojureRT.log.info("do body filter size : %d, isLast %s", chunk.length, isLast);
			String s = new String(chunk);
			
			if (chunk.length > 0) {
				NginxClojureRT.log.info("s length is %d, tail is %s", s.length(), s.substring(s.length() - 10));
			} else {
				NginxClojureRT.log.info("s length is 0");
			}
			
			if (!isLast) {
				return null;
			} 
			
			return new Object[] {200, null, chunks};
		}
		
	}
	
	public static class StreamFacedUppercaseBodyFilter implements NginxJavaBodyFilter {

		public StreamFacedUppercaseBodyFilter() {
			NginxClojureRT.getLog().info("UppercaseBodyFilter created!");
		}
		
		@Override
		public Object[] doFilter(Map<String, Object> request, InputStream bodyChunk, boolean isLast) throws IOException {
			Object[] objs = (Object[]) request.get("body-filter-decorder-objs");
			CharsetDecoder der;
			ByteBuffer buf;
			CharBuffer cb;
			
			if (objs == null) {
				der = Charset.forName("utf8").newDecoder();
				buf = ByteBuffer.allocate(1024);
				cb = CharBuffer.allocate(1024);
				request.put("body-filter-decorder-objs", objs = new Object[]{der, buf, cb});
			}else {
				der = (CharsetDecoder)objs[0];
				buf = (ByteBuffer)objs[1];
				cb = (CharBuffer)objs[2];
			}
			
			int c = 0;
			StringBuilder sb = new StringBuilder();
			
			while ( (c = bodyChunk.read(buf.array(), buf.position(), buf.remaining())) > 0) {
				buf.position(buf.position() + c);
				buf.flip();
				der.decode(buf, cb, isLast);
				cb.flip();
				sb.append(cb.toString().toUpperCase());
				cb.clear();
				buf.compact();
			}
			
			String rt = sb.toString();
			
			NginxClojureRT.getLog().info("[%d] UppercaseBodyFilter.doFilter returns: %s, isLast=%s", ((NginxRequest)request).nativeRequest(), rt , isLast);
			
			if (isLast) {
				return new Object[] {200, null, rt};
			}else {
				return new Object[] {null, null, rt};
			}
			
		}
	}
	
	static List<Coroutine> testBodyFilters = new CopyOnWriteArrayList<Coroutine>();
	
	public static class CoroutineResumeHandler implements NginxJavaRingHandler {
		@Override
		public Object[] invoke(Map<String, Object> request) throws IOException {
			
			for (Coroutine co : testBodyFilters) {
				co.resume();
				break;
			}
			
			return new Object[] {200, null, "OK"};
		}
	}
	
	public static class CoroutineTestBodyFilter extends StreamFacedUppercaseBodyFilter {

		public CoroutineTestBodyFilter() {
		}
		
		@Override
		@Suspendable
		public Object[] doFilter(Map<String, Object> request, InputStream bodyChunk, boolean isLast) throws IOException {
			Coroutine co = Coroutine.getActiveCoroutine();
			testBodyFilters.add(co);
			NginxClojureRT.getLog().info("before yield");
			Coroutine.yield();
			testBodyFilters.remove(co);
			NginxClojureRT.getLog().info("after yield");
			return super.doFilter(request, bodyChunk, isLast);
		}
		
	}
	
	
}
