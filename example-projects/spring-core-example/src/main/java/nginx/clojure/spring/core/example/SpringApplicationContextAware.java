package nginx.clojure.spring.core.example;

import java.util.concurrent.CountDownLatch;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component
public class SpringApplicationContextAware implements ApplicationContextAware {

	private static ApplicationContext applicationContext;
	
	private static CountDownLatch countDownLatch = new CountDownLatch(1);
	
	public static ApplicationContext getApplicationContext() {
		try {
			countDownLatch.await();
		} catch (InterruptedException e) {
			throw new RuntimeException("SpringApplicationContextAware countDownLatch interrupted error", e);
		}
		return applicationContext;
	}
	
	public SpringApplicationContextAware() {
	}

	@Override
	public void setApplicationContext(ApplicationContext ctx) throws BeansException {
		applicationContext = ctx;
		countDownLatch.countDown();
	}

}
