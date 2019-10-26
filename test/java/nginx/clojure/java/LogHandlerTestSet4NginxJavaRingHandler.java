/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.java;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;

import nginx.clojure.Configurable;

public class LogHandlerTestSet4NginxJavaRingHandler {


	public LogHandlerTestSet4NginxJavaRingHandler() {
	}

	public static class SimpleLogHandler implements NginxJavaRingHandler, Configurable {
		
		boolean logUserAgent;
		
		public SimpleLogHandler() {
			System.out.println("ok");
		}

		@Override
		public Object[] invoke(Map<String, Object> request) throws IOException {
			File file = new File("logs/SimpleLogHandler.log");
			NginxJavaRequest r = (NginxJavaRequest) request;
			try (FileOutputStream out = new FileOutputStream(file, true)) {
				String msg = String.format("%s - %s [%s] \"%s\" %s \"%s\" %s %s\n", r.getVariable("remote_addr"),
						r.getVariable("remote_user", "x"), r.getVariable("time_local"), r.getVariable("request"),
						r.getVariable("status"), r.getVariable("body_bytes_sent"), r.getVariable("http_referer", "x"),
						logUserAgent ? r.getVariable("http_user_agent") : "-");
				out.write(msg.getBytes("utf8"));
			}
			return null;
		}

		@Override
		public void config(Map<String, String> properties) {
			logUserAgent = "on".equalsIgnoreCase(properties.get("logUserAgent"));
		}
		

		@Override
		public String[] variablesNeedPrefetch() {
			return new String[] { "remote_addr", "remote_user", "time_local", "request", "status", "body_bytes_sent",
					"http_referer", "http_user_agent" };
		}
	}
}
