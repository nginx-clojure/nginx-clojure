package example;

import java.io.IOException;
import java.util.Map;

import nginx.clojure.java.NginxJavaRingHandler;

public class JvmInitHandler implements NginxJavaRingHandler {

	public JvmInitHandler() {
		// TODO Auto-generated constructor stub
	}

	public Object[] invoke(Map<String, Object> request) throws IOException {
		System.err.println("JvmInitHandler ok!");
		return null;
	}

}
