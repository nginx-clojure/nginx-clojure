package nginx.clojure.spring.core.example;

import java.io.IOException;
import java.util.Map;

import nginx.clojure.java.NginxJavaRingHandler;

public class NginxJvmInitHandler implements NginxJavaRingHandler {

	public NginxJvmInitHandler() {
		SpringExampleApplication.main(new String[0]);
	}

	@Override
	public Object[] invoke(Map<String, Object> request) throws IOException {
		return null;
	}

}
