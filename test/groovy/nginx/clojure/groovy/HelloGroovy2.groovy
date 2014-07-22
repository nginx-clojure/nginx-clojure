package nginx.clojure.groovy;

import nginx.clojure.java.NginxJavaRingHandler;
import java.util.Map;

public class HelloGroovy2 implements NginxJavaRingHandler {
    public Object[] invoke(Map<String, Object> request){
       return [200, ["Content-Type":"text/html"], "Hello, Groovy2 & Nginx!"];
    }
 }