package nginx.clojure.spring.core.example;

import java.io.IOException;
import java.util.Map;

import nginx.clojure.Configurable;
import nginx.clojure.java.NginxJavaRingHandler;

public class NginxSpringHandlerWrapper implements NginxJavaRingHandler, Configurable {

	private NginxJavaRingHandler realHandler;
	
	private String[] prefetchedVars;
	
	
	public NginxSpringHandlerWrapper() {
		
	}

	@Override
	public void config(Map<String, String> properties) {
		String name = properties.get("spring.realHandler");
		realHandler = (NginxJavaRingHandler)SpringApplicationContextAware.getApplicationContext().getBean(name);
		prefetchedVars = properties.get("spring.prefetched.vars").split(",");
	}

	@Override
	public Object[] invoke(Map<String, Object> request) throws IOException {
		return realHandler.invoke(request);
	}
	
	@Override
	public String[] variablesNeedPrefetch() {
		return prefetchedVars;
	}


}
