package nginx.clojure.net;

public interface NginxClojureSocketHandler {
	public void onConnect(NginxClojureAsynSocket s, long sc);
	public void onRead(NginxClojureAsynSocket s, long sc);
	public void onWrite(NginxClojureAsynSocket s, long sc);
	public void onRelease(NginxClojureAsynSocket s, long sc);
}
