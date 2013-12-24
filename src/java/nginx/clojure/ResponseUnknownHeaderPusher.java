package nginx.clojure;

import static nginx.clojure.Constants.DEFAULT_ENCODING;
import clojure.lang.ArraySeq;
import clojure.lang.ISeq;

public class ResponseUnknownHeaderPusher implements ResponseHeaderPusher {

	protected String name;
	
	public ResponseUnknownHeaderPusher(String name) {
		this.name = name;
	}
	
	@Override
	public String name() {
		return name;
	}
	
	@Override
	public long knownOffset() {
		return -1;
	}

	@Override
	public void push(long h, long pool, Object v) {
		
		ISeq seq = null;
		if (v instanceof String) {
			String val = (String) v;
			seq = ArraySeq.create(val);
		}else if (v instanceof ISeq) {
			seq = (ISeq) v;
		}
		
		int c = seq.count();
		if (c == 0) {
			return;
		}
		
		
		for (int i = 0; i < c; i++) {
			String val = (String) seq.first();
			seq = seq.next();
			if (val != null) {
				long p = NginxClojureRT.ngx_list_push(h + Constants.NGX_HTTP_CLOJURE_HEADERSO_HEADERS_OFFSET);
				if (p == 0) {
					throw new RuntimeException("can not push ngx list for headers");
				}
				NginxClojureRT.pushNGXInt(p + Constants.NGX_HTTP_CLOJURE_TEL_HASH_OFFSET, 1);
				NginxClojureRT.pushNGXString(p + Constants.NGX_HTTP_CLOJURE_TEL_KEY_OFFSET, name, DEFAULT_ENCODING, pool);
				NginxClojureRT.pushNGXString(p + Constants.NGX_HTTP_CLOJURE_TEL_VALUE_OFFSET, val, DEFAULT_ENCODING, pool);
			}
		}
	}

}
