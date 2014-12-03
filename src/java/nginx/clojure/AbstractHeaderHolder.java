package nginx.clojure;

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
}