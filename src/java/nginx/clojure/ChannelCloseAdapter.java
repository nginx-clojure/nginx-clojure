package nginx.clojure;

import java.io.IOException;

public abstract class ChannelCloseAdapter<T> implements ChannelListener<T> {

	@Override
	public void onConnect(long status, T data) throws IOException {
	}
	
	@Override
	public void onRead(long status, T data) throws IOException {
	}
	
	@Override
	public void onWrite(long status, T data) throws IOException {
	}
}
