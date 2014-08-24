package nginx.clojure;


public abstract class NginxSimpleResponse implements NginxResponse {
	
	protected NginxRequest request;
	
	public NginxSimpleResponse() {
	}
	
	public NginxSimpleResponse(NginxRequest req) {
		this.request = req;
	}
	
	@Override
	public NginxRequest request() {
		return request;
	}
	
}
