package nginx.clojure.net;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import nginx.clojure.Constants;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import clojure.lang.AFn;
import clojure.lang.PersistentArrayMap;

public class SimpleHandler4TestHttpClientGetMethod extends AFn {

	public SimpleHandler4TestHttpClientGetMethod() {
	}
	
	@Override
	public Object invoke(Object r) {
		CloseableHttpClient httpclient = HttpClients.createDefault();
//		HttpGet httpget = new HttpGet("http://cn.bing.com/");
		HttpGet httpget = new HttpGet("http://192.168.2.12/ctest/medium.html");
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
			Object[] resps = new Object[] {
					Constants.STATUS,
					200,
					Constants.HEADERS,
					new PersistentArrayMap(new Object[] {
							Constants.CONTENT_TYPE.getName(),
							"text/html" }),
			Constants.BODY, new ByteArrayInputStream(out.toByteArray()) };
			return new PersistentArrayMap(resps);
		} catch(IOException e) {
			throw new RuntimeException("ioexception", e);
		}finally {
			if (response != null) {
				try {
					response.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

}
