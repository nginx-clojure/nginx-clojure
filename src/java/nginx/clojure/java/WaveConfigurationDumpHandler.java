/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.java;

import java.io.IOException;
import java.util.Map;

import nginx.clojure.NginxClojureRT;
import nginx.clojure.wave.SuspendMethodTracer;
import static nginx.clojure.MiniConstants.*;

public class WaveConfigurationDumpHandler implements NginxJavaRingHandler {

	@Override
	public Object[] invoke(Map<String, Object> request) {
		try {
			SuspendMethodTracer.dump();
		} catch (IOException e) {
			NginxClojureRT.UNSAFE.throwException(e);
		}
		return new Object[]{NGX_HTTP_OK, ArrayMap.create(CONTENT_TYPE, "text/plain"), "ok"};
	}

}
