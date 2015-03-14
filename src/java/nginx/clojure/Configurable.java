package nginx.clojure;

import java.util.Map;

public interface Configurable {
	public void config(Map<String,String> properties);
}
