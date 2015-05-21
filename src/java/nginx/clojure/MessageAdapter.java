package nginx.clojure;

import java.nio.ByteBuffer;


public  class MessageAdapter<T> implements MessageListener<T> {

	@Override
	public void onClose(T data) {
	}
	
	@Override
	public void onClose(T data, long status, String reason) {
	}

	@Override
	public void onConnect(long status, T data) {
		if (status == 0) {
			this.onOpen(data);
		}else {
			this.onError(data, status);
		}
	}

	@Override
	public void onRead(long status, T data) {
		if (status != 0) {
			this.onError(data, status);
		}
	}

	@Override
	public void onWrite(long status, T data) {
		if (status != 0) {
			this.onError(data, status);
		}
	}

	@Override
	public void onTextMessage(T data, String message, boolean remaining) {
	}

	@Override
	public void onBinaryMessage(T data, ByteBuffer message, boolean remaining) {
	}

	@Override
	public void onOpen(T data) {
	}

	@Override
	public void onError(T data, long status) {
	}
	
}
