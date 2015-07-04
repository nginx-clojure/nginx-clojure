package nginx.clojure;

public interface RawMessageListener<T> extends ChannelListener<T> {
	public void onTextMessage(T data, long message, boolean remining, boolean first);
	public void onBinaryMessage(T data, long message, boolean remining, boolean first);
	
	/**
	 * Only when protocol defined client initiated close event happens this method will be invoked. 
	 * Protocol error caused close event and low level channel close event won't cause it invoked.
	 * @param data listener attached data
	 * @param message close reason message
	 */
	public void onClose(T data, long message);
}
