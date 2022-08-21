package nginx.clojure.jersey.spring.example;

import org.glassfish.jersey.server.ApplicationHandler;
import org.springframework.stereotype.Component;

import nginx.clojure.jersey.NginxJerseyContainer;

@Component
public class NginxJerseySpringContainer extends NginxJerseyContainer {

	public NginxJerseySpringContainer() {
	}
	
    public void setApplicationHandler(ApplicationHandler applicationHandler, String applicationPath) {
        super.appHandler = applicationHandler;
        super.appPath = applicationPath;
    }

}
