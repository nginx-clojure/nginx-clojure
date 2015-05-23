package nginx.clojure;

import static nginx.clojure.NginxClojureRT.pushNGXOfft;
import static nginx.clojure.NginxClojureRT.fetchNGXOfft;


public class OffsetHeaderHolder extends AbstractHeaderHolder {
	
	public OffsetHeaderHolder() {
	}
	
	public OffsetHeaderHolder(String name, long offset, long headersOffset) {
		super(name, offset,  headersOffset);
	}
	

	@Override
	public void push(long h, long pool, Object v) {
		long l;
		if (v instanceof Number) {
			l = ((Number)v).longValue();
		}else if (v instanceof String) {
			l = Long.valueOf((String)v);
		}else {
			l = Long.valueOf(pickString(v));
		}
		pushNGXOfft(h + offset, l);
	}

	public void push(long h, long v) {
		pushNGXOfft(h + offset, v);
	}
	
	@Override
	public void clear(long h) {
		pushNGXOfft(h + offset, -1);
	}

	@Override
	public Object fetch(long h) {
		return Long.toString(fetchNGXOfft(h + offset));
	}

	@Override
	public boolean exists(long h) {
		return h != 0 && fetchNGXOfft(h + offset) != -1;
	}

}
