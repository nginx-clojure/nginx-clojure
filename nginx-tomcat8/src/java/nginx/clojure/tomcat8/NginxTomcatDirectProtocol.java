package nginx.clojure.tomcat8;

import java.util.concurrent.Executor;

import nginx.clojure.NginxClojureRT;

import org.apache.coyote.Adapter;
import org.apache.coyote.ProtocolHandler;

public class NginxTomcatDirectProtocol  implements ProtocolHandler {

	protected Adapter adapter;
	protected Executor executor;
	
	@Override
	public void setAdapter(Adapter adapter) {
		this.adapter = adapter;
	}

	@Override
	public Adapter getAdapter() {
		return adapter;
	}

	@Override
	public Executor getExecutor() {
		return executor;
	}
	
	public void setExecutor(Executor executor) {
		this.executor = executor;
	}

	@Override
	public void init() throws Exception {
	}

	@Override
	public void start() throws Exception {
	}

	@Override
	public void pause() throws Exception {
	}

	@Override
	public void resume() throws Exception {
	}

	@Override
	public void stop() throws Exception {
	}

	@Override
	public void destroy() throws Exception {
	}

	@Override
	public boolean isAprRequired() {
		return false;
	}

	@Override
	public boolean isCometSupported() {
		return false;
	}

	@Override
	public boolean isCometTimeoutSupported() {
		return false;
	}

	@Override
	public boolean isSendfileSupported() {
		return true;
	}

}
