package nginx.clojure;

import java.util.AbstractMap.SimpleEntry;
import java.util.List;

public interface NginxRequest {
	
	public long nativeRequest();
	
	public boolean isReleased();
	
	public void tagReleased();
	
	 //for safe access with another thread
	public void	prefetchAll();
	
	public NginxHandler handler();
	
	public NginxHttpServerChannel channel();
	
	public boolean isHijacked();
	
	public int phase();
	
	public List<SimpleEntry<Object, ChannelListener<Object>>>  listeners();
	
	public String uri();
	
	public <T> void addListener(T data, ChannelListener<T> listener);
	
	public boolean isWebSocket();
	
	public long nativeCount();
	
	public int getAndIncEvalCount();
	
	public NginxHttpServerChannel hijack(boolean ignoreFilter);
	
}
