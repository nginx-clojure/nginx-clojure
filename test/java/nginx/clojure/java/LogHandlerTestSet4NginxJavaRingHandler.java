/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.java;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import nginx.clojure.Configurable;

public class LogHandlerTestSet4NginxJavaRingHandler {


	public LogHandlerTestSet4NginxJavaRingHandler() {
	}

	public static class SimpleLogHandler implements NginxJavaRingHandler, Configurable {
		
		Map<String, String> properties;
		
		public SimpleLogHandler() {
			properties = new HashMap<>();
		}

		/* (non-Javadoc)
		 * @see nginx.clojure.java.NginxJavaRingHandler#invoke(java.util.Map)
		 */
		@Override
		public Object[] invoke(Map<String, Object> request) throws IOException {
			File file = new File("logs/SimpleLogHandler.log");
			try (FileOutputStream out = new FileOutputStream(file, true)) {
				out.write((request.get(Constants.URI)+"\n").getBytes("utf8"));
			}
			return null;
		}

		/* (non-Javadoc)
		 * @see nginx.clojure.Configurable#config(java.util.Map)
		 */
		@Override
		public void config(Map<String, String> properties) {
			this.properties.putAll(properties);
		}
	}
}
