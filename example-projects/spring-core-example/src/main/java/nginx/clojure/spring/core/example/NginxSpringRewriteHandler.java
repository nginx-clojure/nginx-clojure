package nginx.clojure.spring.core.example;

import java.io.IOException;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import nginx.clojure.java.Constants;
import nginx.clojure.java.NginxJavaRequest;
import nginx.clojure.java.NginxJavaRingHandler;


@Service("myRewriteHandler")
public class NginxSpringRewriteHandler implements NginxJavaRingHandler {

	@Autowired
	private ProxyTargetComputeService proxyTargetComputeService;
	
	public NginxSpringRewriteHandler() {
		
	}

	@Override
	public Object[] invoke(Map<String, Object> r) throws IOException {		
		NginxJavaRequest req = (NginxJavaRequest)r;
		String target = proxyTargetComputeService.computeTarget(req.getVariable("remote_addr"), req.getVariable("remote_port"));
		req.setVariable("proxy_target", target);
		return Constants.PHASE_DONE;
	}

}
