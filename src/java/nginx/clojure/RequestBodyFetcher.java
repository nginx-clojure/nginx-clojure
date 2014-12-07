/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;

import static nginx.clojure.NginxClojureRT.*;
import static nginx.clojure.HackUtils.*;

public class RequestBodyFetcher implements RequestVarFetcher {
	
	public final static RequestKnownNameVarFetcher BODY_VAR_FETCHER = new RequestKnownNameVarFetcher("request_body");
	
	public final static RequestKnownNameVarFetcher BODY_FILE_FETCHER = new RequestKnownNameVarFetcher("request_body_file");
	
	public RequestBodyFetcher() {
	}
	
	@Override
	public Object fetch(long r, Charset encoding) {
		ByteBuffer bb = pickByteBuffer();
		long len = ngx_http_clojure_mem_get_request_body(r,  bb,  BYTE_ARRAY_OFFSET, bb.capacity());
		if (len == 0) {
			return null;
		} else if (len < 0) {
			len = -len;
			bb.limit((int) len);
			String tmpfile = decode(bb, DEFAULT_ENCODING, pickCharBuffer());
			try {
				return new FileInputStream(tmpfile);
			} catch (FileNotFoundException e) {
				throw new RuntimeException("can not find tmp file", e);
			}
		}else {
			long addr = bb.order(ByteOrder.nativeOrder()).asLongBuffer().get();
			return new NativeInputStream(addr, len);
		}
	}
}
