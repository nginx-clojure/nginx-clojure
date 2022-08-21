package nginx.clojure.spring.core.example;

import org.springframework.stereotype.Service;

@Service("proxyTargetComputeService")
public class ProxyTargetComputeService {
 	
	public String computeTarget(String ip, String port) {
		int m = (ip + ":" + port).hashCode() % 2;
		return m == 0 ? "127.0.0.1:8081" : "127.0.0.1:8082"; 
	}
}