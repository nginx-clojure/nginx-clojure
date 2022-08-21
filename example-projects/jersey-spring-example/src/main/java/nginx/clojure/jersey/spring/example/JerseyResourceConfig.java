package nginx.clojure.jersey.spring.example;

import javax.annotation.PostConstruct;
import javax.ws.rs.ApplicationPath;

import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.springframework.context.annotation.Configuration;

@Configuration
@ApplicationPath("/api")
public class JerseyResourceConfig extends ResourceConfig {

	public JerseyResourceConfig() {
	}

	@PostConstruct
	public void initialize() {
		packages("nginx.clojure.jersey.spring.example")
		.register(JacksonFeature.class);
	}

}
