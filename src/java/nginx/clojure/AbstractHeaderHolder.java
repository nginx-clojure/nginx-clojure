package nginx.clojure;

import java.lang.reflect.Array;
import java.util.List;

public  abstract class AbstractHeaderHolder  implements NginxHeaderHolder  {

	protected String name;
	protected long offset = -1;
	protected long headersOffset = -1;

	
	

	public AbstractHeaderHolder() {
		super();
	}

	public AbstractHeaderHolder(String name, long offset,  long headersOffset) {
		super();
		this.name = name;
		this.offset = offset;
		this.headersOffset = headersOffset;
	}



	@Override
	public long knownOffset() {
		return offset;
	}

	@Override
	public String name() {
		return name;
	}

	@Override
	public long headersOffset() {
		return headersOffset;
	}
	
	@SuppressWarnings("rawtypes")
	public final String pickString(Object v) {
		if (v == null) {
			return null;
		}
		if (v instanceof String) {
			return (String) v;
		}else if (v instanceof List) {
			return ((List) v).isEmpty() ? null : ((List) v).get(0).toString();
		}else if (v.getClass().isArray()){
			return Array.get(v, 0).toString();
		}
		return null;
	}
}