/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.java;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
//import java.nio.charset.CharsetDecoder;
import java.util.Map;

import nginx.clojure.HackUtils;
import nginx.clojure.MiniConstants;
import nginx.clojure.NginxClojureRT;


public abstract class StringFacedJavaBodyFilter implements NginxJavaBodyFilter {

	public static final class DecorderTool {
//		protected CharsetDecoder decoder;
		protected ByteBuffer rem;
		
		public DecorderTool() {
//			decoder = MiniConstants.DEFAULT_ENCODING.newDecoder();
			rem = ByteBuffer.allocate(3);
			rem.flip();
		}
	}
	
	public static final String CHAR_DECODER_TOOL_IN_REQUEST = "$char-decoder-tool-in-request!";
	
	public StringFacedJavaBodyFilter() {
	}

	
	@Override
	public Object[] doFilter(Map<String, Object> request, InputStream bodyChunk, boolean isLast) throws IOException {
		DecorderTool detool = (DecorderTool) request.get(CHAR_DECODER_TOOL_IN_REQUEST);
		if (detool == null) {
			request.put(CHAR_DECODER_TOOL_IN_REQUEST, detool = new DecorderTool());
		}
		
		int c = 0;
		StringBuilder sb = new StringBuilder();
		ByteBuffer bb = NginxClojureRT.pickByteBuffer();
		CharBuffer cb = NginxClojureRT.pickCharBuffer();
		
		if (detool.rem.hasRemaining()) {
			bb.put(detool.rem);
		}
		
		detool.rem.clear();
		
		while ( (c = bodyChunk.read(bb.array(), bb.position(), bb.remaining())) > 0) {
			bb.position(bb.position() + c);
			bb.flip();
//			detool.decoder.decode(bb, cb, true);
			sb.append(HackUtils.decodeValid(bb, MiniConstants.DEFAULT_ENCODING, cb).toString());
			cb.clear();
			bb.compact();
		}
		
//		detool.decoder.flush(cb);
//		sb.append(cb.toString());
		bb.flip();
		if (bb.hasRemaining()) {
			detool.rem.put(bb);
			detool.rem.flip();
		}
		
		return doFilter(request, sb.toString(), isLast);
	}
	
	protected abstract Object[] doFilter(Map<String, Object> request, String body, boolean isLast) throws IOException;

}
