package nginx.clojure;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface MessageListener<T> extends ChannelListener<T> {

	public void onOpen(T data) throws IOException;
	public void onError(T data, long status) throws IOException;
	public void onClose(T data, long status, String reason) throws IOException;
	public void onTextMessage(T data, String message, boolean remining) throws IOException;
	public void onBinaryMessage(T data, ByteBuffer message, boolean remining) throws IOException;
}
