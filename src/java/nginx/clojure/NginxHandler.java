/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure;

import java.util.Collection;
import java.util.Map.Entry;



/**
 * Low level nginx handler which faces jni code
 * @author Zhang,Yuexiang (xfeep)
 *
 */
public interface NginxHandler {

	public int execute(long request);
	
	public ResponseHeaderPusher fetchResponseHeaderPusher(String name);
	
	public NginxResponse toNginxResponse(NginxRequest req, Object resp);
	
	public void completeAsyncResponse(NginxRequest req, Object resp);
	
	/**
	 * If ignoreFilter is true all data output to channel won't be filtered
   by any nginx HTTP header/body filters such as gzip filter, chucked filter, etc.
	 */
	public NginxServerChannel hijack(NginxRequest req, boolean ignoreFilter);

	public long buildOutputChain(NginxResponse response);

	public <K,V> long prepareHeaders(NginxRequest req, long status, Collection<Entry<K, V>> headers);

	public NginxResponse process(NginxRequest req);
	
}
