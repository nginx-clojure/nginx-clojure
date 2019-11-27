/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.net;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.apache.commons.io.FileUtils;

import nginx.clojure.groovy.Constants;
import nginx.clojure.java.ArrayMap;
import nginx.clojure.java.NginxJavaRingHandler;
import redis.clients.jedis.Jedis;


public class RedisCoroutineTestSet4JavaHandler {

	public static class SimpleRedisHandler implements NginxJavaRingHandler {

		/* (non-Javadoc)
		 * @see nginx.clojure.java.NginxJavaRingHandler#invoke(java.util.Map)
		 */
		@Override
		public Object[] invoke(Map<String, Object> request) throws IOException {
			try (Jedis jedis = new Jedis("localhost") ) {
				String value = FileUtils.readFileToString(new File("testfiles/large.txt"), "utf-8");
				jedis.set("myserver", value);
				String body = jedis.get("myserver");
				return new Object[] {Constants.NGX_HTTP_OK, ArrayMap.create("content-type", "text/html"), body};
			}
		}
	}
}
