package nginx.clojure.net;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import nginx.clojure.java.ArrayMap;
import nginx.clojure.java.Constants;
import nginx.clojure.java.NginxJavaRingHandler;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

public class SimpleHandler4TestHttpClientGetMethod implements NginxJavaRingHandler {

	public SimpleHandler4TestHttpClientGetMethod() {
	}
	
	@Override
	public Object[] invoke(Map<String, Object> request) {
		CloseableHttpClient httpclient = HttpClients.createDefault();
//		HttpGet httpget = new HttpGet("http://cn.bing.com/");
		HttpGet httpget = new HttpGet("https://www.apache.org/dist/httpcomponents/httpclient/RELEASE_NOTES-4.3.x.txt");
		CloseableHttpResponse response = null;
		try {
			response = httpclient.execute(httpget);
			InputStream in = response.getEntity().getContent();
			byte[] buf = new byte[1024];
			int c = 0;
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			while ((c = in.read(buf)) > 0) {
				out.write(buf, 0, c);
			}
			in.close();
			Object[] resps = new Object[] {
					200,
					ArrayMap.create(Constants.CONTENT_TYPE, "text/html"),
					new ByteArrayInputStream(out.toByteArray())
			};
			return resps;
		} catch(IOException e) {
			throw new RuntimeException("ioexception", e);
		}finally {
			if (httpclient != null) {
				try {
					httpclient.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

}
