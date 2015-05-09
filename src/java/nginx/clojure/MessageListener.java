package nginx.clojure;

public interface MessageListener<T> extends ChannelListener<T> {

	public void onTextMessage(T data, String message, boolean remining);
	public void onBinaryMessage(T data, byte[] message, boolean remining);
}
