package nginx.clojure;


public abstract class NginxSimpleResponse implements NginxResponse {
	
	protected NginxRequest request;
	protected int type = TYPE_NORMAL;
	
	public NginxSimpleResponse() {
	}
	
	public NginxSimpleResponse(NginxRequest req) {
		this.request = req;
	}
	
	@Override
	public NginxRequest request() {
		return request;
	}
	
	@Override
	public int type() {
		return type;
	}
	
	@Override
	public boolean isLast() {
		return true;
	}
}
