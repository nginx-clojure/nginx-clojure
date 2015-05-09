package nginx.clojure;

public interface RawMessageListener<T> extends ChannelListener<T> {
	public void onTextMessage(T data, long message, boolean remining);
	public void onBinaryMessage(T data, long message, boolean remining);
}
