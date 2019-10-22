/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.java;

import java.io.IOException;
import java.util.Map;

import nginx.clojure.NginxHttpServerChannel;

public class Http2Handlers {


	public Http2Handlers() {
		
	}
	
	
	public static class HijackSendHandler implements NginxJavaRingHandler {

		/* (non-Javadoc)
		 * @see nginx.clojure.java.NginxJavaRingHandler#invoke(java.util.Map)
		 */
		@Override
		public Object[] invoke(Map<String, Object> request) throws IOException {
			NginxJavaRequest nginxJavaRequest = (NginxJavaRequest)request;
			NginxHttpServerChannel ch = nginxJavaRequest.hijack(false);
			ch.sendResponse(new Object[] {200, ArrayMap.create("content-type", "html"), "Hello1"});
//			ch.sendResponse(new Object[] {200, ArrayMap.create("content-type", "html"), "Hello2"});
			return null;
		}
		
	}

}
