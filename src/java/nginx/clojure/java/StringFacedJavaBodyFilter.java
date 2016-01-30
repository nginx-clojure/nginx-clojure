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
	
	public static final String CHAR_DECODER_BUF_REM_IN_REQUEST = "$char-decoder-buf-rem-in-request!";
	
	public StringFacedJavaBodyFilter() {
	}

	
	@Override
	public Object[] doFilter(Map<String, Object> request, InputStream bodyChunk, boolean isLast) throws IOException {
		ByteBuffer rem = (ByteBuffer) request.get(CHAR_DECODER_BUF_REM_IN_REQUEST);
		if (rem == null) {
			request.put(CHAR_DECODER_BUF_REM_IN_REQUEST, rem = ByteBuffer.allocate(3));
			rem.flip();
		}
		StringBuilder sb = decodeToString(rem, bodyChunk);
		return doFilter(request, sb.toString(), isLast);
	}


	public static StringBuilder decodeToString(ByteBuffer rem, InputStream bodyChunk) throws IOException {
		
		int c = 0;
		StringBuilder sb = new StringBuilder();
		ByteBuffer bb = NginxClojureRT.pickByteBuffer();
		CharBuffer cb = NginxClojureRT.pickCharBuffer();
		
		if (rem.hasRemaining()) {
			bb.put(rem);
		}
		
		while ( (c = bodyChunk.read(bb.array(), bb.position(), bb.remaining())) > 0) {
			bb.position(bb.position() + c);
			bb.flip();
			sb.append(HackUtils.decodeValid(bb, MiniConstants.DEFAULT_ENCODING, cb).toString());
			cb.clear();
			bb.compact();
		}
		
		bb.flip();
		if (bb.hasRemaining()) {
			rem.clear();
			rem.put(bb);
			rem.flip();
		}
		
		return sb;
	}
	
	protected abstract Object[] doFilter(Map<String, Object> request, String body, boolean isLast) throws IOException;

}
