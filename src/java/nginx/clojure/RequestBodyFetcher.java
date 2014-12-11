/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.SequenceInputStream;
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
		long len = ngx_http_clojure_mem_get_request_body(r,  bb.array(),  BYTE_ARRAY_OFFSET, bb.capacity());
		if (len == 0) {
			return null;
		} else if (len < 0) {
			len = -len;
			bb.limit((int) len);
			String tmpfile = decode(bb, DEFAULT_ENCODING, pickCharBuffer());
			try {
				if (log.isDebugEnabled()) {
					log.debug("#%d:get tmp file :%s", r, tmpfile);
				}
				return new FileInputStream(tmpfile);
			} catch (FileNotFoundException e) {
				throw new RuntimeException("can not find tmp file", e);
			}
		}else {
			long li = bb.order(ByteOrder.nativeOrder()).getLong();
			if (li == len) {
				return new NativeInputStream(bb.getLong(), len);
			}else {
				InputStream rt = new NativeInputStream(bb.getLong(), li);
				li = bb.getLong();
				while (li > 0) {
					rt = new SequenceInputStream(rt, new NativeInputStream(bb.getLong(), li));
					li = bb.getLong();
				}
				return rt;
			}
		}
	}
}
