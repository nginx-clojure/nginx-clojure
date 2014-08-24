package nginx.clojure;

public interface ChannelListener<T> {
	
	public void onClose(T data);
	
}
