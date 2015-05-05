package nginx.clojure;

public abstract class ChannelCloseAdapter<T> implements ChannelListener<T> {

	@Override
	public void onConnect(long status, T data) {
	}
	
	@Override
	public void onRead(long status, T data) {
	}
	
	@Override
	public void onWrite(long status, T data) {
	}
}
