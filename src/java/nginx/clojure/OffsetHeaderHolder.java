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
		if (v instanceof String) {
			v = Long.valueOf((String)v);
		}
		pushNGXOfft(h + offset, ((Long)v).longValue());
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
