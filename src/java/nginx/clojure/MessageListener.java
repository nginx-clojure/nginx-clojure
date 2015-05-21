package nginx.clojure;

import java.nio.ByteBuffer;

public interface MessageListener<T> extends ChannelListener<T> {

	public void onOpen(T data);
	public void onError(T data, long status);
	public void onClose(T data, long status, String reason);
	public void onTextMessage(T data, String message, boolean remining);
	public void onBinaryMessage(T data, ByteBuffer message, boolean remining);
}
