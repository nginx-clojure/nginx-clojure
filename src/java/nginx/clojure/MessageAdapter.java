package nginx.clojure;

import java.io.IOException;
import java.nio.ByteBuffer;


public  class MessageAdapter<T> implements MessageListener<T> {

	@Override
	public void onClose(T data) throws IOException {
	}
	
	@Override
	public void onClose(T data, long status, String reason) throws IOException{
	}

	@Override
	public void onConnect(long status, T data)throws IOException {
		if (status == 0) {
			this.onOpen(data);
		}else {
			this.onError(data, status);
		}
	}

	@Override
	public void onRead(long status, T data) throws IOException {
		if (status != 0) {
			this.onError(data, status);
		}
	}

	@Override
	public void onWrite(long status, T data) throws IOException {
		if (status != 0) {
			this.onError(data, status);
		}
	}

	@Override
	public void onTextMessage(T data, String message, boolean remaining) throws IOException {
	}

	@Override
	public void onBinaryMessage(T data, ByteBuffer message, boolean remaining) throws IOException {
	}

	@Override
	public void onOpen(T data) throws IOException {
	}

	@Override
	public void onError(T data, long status) throws IOException {
	}
	
}
