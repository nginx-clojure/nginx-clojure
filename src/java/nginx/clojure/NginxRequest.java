package nginx.clojure;

import java.util.AbstractMap.SimpleEntry;
import java.util.List;

public interface NginxRequest {
	
	public long nativeRequest();
	
	public boolean isReleased();
	
	public void tagReleased();
	
	public void markReqeased();
	
	 //for safe access with another thread
	public void	prefetchAll();
	
	public void	prefetchAll(String[] headers, String[] variables, String[] outHeaders);
	
	public void applyDelayed();
	
	public NginxHandler handler();
	
	public NginxHttpServerChannel channel();
	
	public boolean isHijacked();
	
	public int phase();
	
	public List<SimpleEntry<Object, ChannelListener<Object>>>  listeners();
	
	public String uri();
	
	public <T> void addListener(T data, ChannelListener<T> listener);
	
	public boolean isWebSocket();
	
	public long nativeCount();
	
	public long nativeCount(long c);
	
	public int getAndIncEvalCount();
	
	public NginxHttpServerChannel hijack(boolean ignoreFilter);

	public long discardRequestBody();

	public String getVariable(String name);
	
	public String getVariable(String name, String defaultVal);

	public int setVariable(String name, String value);
	
}
