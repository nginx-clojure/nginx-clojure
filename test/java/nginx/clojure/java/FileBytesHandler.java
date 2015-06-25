package nginx.clojure.java;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;

import nginx.clojure.Configurable;

/**
 * DO NOT use this for file services, it is only used for testing content handler properties
 *
 */
public class FileBytesHandler implements NginxJavaRingHandler, Configurable {

	Object[] results = new Object[] {
			200,
			ArrayMap.create("content-type", "text/html"),
			null
	};
	
	@Override
	public Object[] invoke(Map<String, Object> request) throws IOException {
		return results;
	}
	
	@Override
	public void config(Map<String, String> properties) {
		File f = new File(properties.get("file"));
		if (f.canRead()) {
			byte[] bs = new byte[(int)f.length()];
			try{
				FileInputStream in = new FileInputStream(f);
				in.read(bs);
				in.close();
			}catch(Throwable e) {
				e.printStackTrace();
				results[0] = 500;
				return;
			}

			results[2] = bs;
		}else {
			results[0] = 404;
		}
	}

}
