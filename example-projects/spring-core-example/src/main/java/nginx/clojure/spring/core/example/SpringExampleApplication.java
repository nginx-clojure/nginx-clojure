package nginx.clojure.spring.core.example;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class SpringExampleApplication {

	public SpringExampleApplication() {
	}
	
	public static void main(String[] args) {
		ApplicationContext context = new AnnotationConfigApplicationContext("nginx.clojure.spring.core.example");
//		ProxyTargetComputeService proxyTargetComputeService = (ProxyTargetComputeService)context.getBean("proxyTargetComputeService");
//		System.out.println(proxyTargetComputeService.computeTarget("192.168.1.1", "5123"));
	}

}
