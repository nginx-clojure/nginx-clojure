package nginx.clojure.jersey.spring.example;

import java.io.IOException;
import java.util.Map;

import org.glassfish.jersey.server.ApplicationHandler;
import org.glassfish.jersey.server.ResourceConfig;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import nginx.clojure.Configurable;
import nginx.clojure.java.NginxJavaRequest;
import nginx.clojure.java.NginxJavaRingHandler;

public class JerseySpringNginxHandler implements NginxJavaRingHandler, Configurable {

	NginxJerseySpringContainer nginxJerseySpringContainer;
	
	public JerseySpringNginxHandler() {
	}

	@Override
	public void config(Map<String, String> properties) {
		ApplicationContext context = new AnnotationConfigApplicationContext("nginx.clojure.jersey.spring.example");
		ResourceConfig resourceConfig = context.getBean(JerseyResourceConfig.class);
		resourceConfig.property("contextConfig", context);
		String jerseyContextPath = properties.get("jersey-context-path");
		nginxJerseySpringContainer = context.getBean(NginxJerseySpringContainer.class);
		nginxJerseySpringContainer.setApplicationHandler(new ApplicationHandler(resourceConfig), jerseyContextPath);
	}
	
	@Override
	public Object[] invoke(Map<String, Object> request) throws IOException {
		return nginxJerseySpringContainer.handle((NginxJavaRequest)request);
	}
}
