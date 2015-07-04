package nginx.clojure;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface MessageListener<T> extends ChannelListener<T> {

	public void onOpen(T data) throws IOException;
	
	/**
	 * Only read or write I/O error will cause it invoked.
	 * Protocol error and low level channel error won't cause it invoked.
	 */
	public void onError(T data, long status) throws IOException;
	/**
	 * Only when protocol defined client initiated close event happens this method will be invoked. 
	 * Protocol error caused close event and low level channel close event won't cause it invoked.
	 */
	public void onClose(T data, long status, String reason) throws IOException;
	public void onTextMessage(T data, String message, boolean remining) throws IOException;
	public void onBinaryMessage(T data, ByteBuffer message, boolean remining) throws IOException;
}
