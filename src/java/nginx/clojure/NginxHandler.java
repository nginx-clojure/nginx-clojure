/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure;

import java.io.IOException;
import java.util.Collection;
import java.util.Map.Entry;



/**
 * Low level nginx handler which faces jni code
 * @author Zhang,Yuexiang (xfeep)
 *
 */
public interface NginxHandler {

	public int execute(long request, long chain);
	
	public NginxHeaderHolder fetchResponseHeaderPusher(String name);
	
	public NginxResponse toNginxResponse(NginxRequest req, Object resp);
	
	public void completeAsyncResponse(NginxRequest req, Object resp);
	
	/**
	 * Get a hijacked Server Channel used to send message later typically in another thread
	 * If ignoreFilter is true all data output to channel won't be filtered
	 * by any nginx HTTP header/body filters such as gzip filter, chucked filter, etc.
	 * @param req the request object
	 * @param ignoreFilter whether we need ignore nginx filter or not.
	 * @return hijacked channel used to send message later
	 */
	public NginxHttpServerChannel hijack(NginxRequest req, boolean ignoreFilter);

	public long buildOutputChain(NginxResponse response);

	public <K,V> long prepareHeaders(NginxRequest req, long status, Collection<Entry<K, V>> headers);

	public NginxResponse process(NginxRequest req) throws IOException;
	
}
