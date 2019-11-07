package nginx.clojure.net;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Collections;
import java.util.Map;

import nginx.clojure.Configurable;
import nginx.clojure.NginxClojureRT;
import nginx.clojure.java.ArrayMap;
import nginx.clojure.java.Constants;
import nginx.clojure.java.NginxJavaRingHandler;
import nginx.clojure.logger.LoggerService;

public class SimpleHandler4TestNginxClojureSocket implements NginxJavaRingHandler, Configurable {

	static LoggerService log;
	String host = "www.apache.org";
	int port = 80;
	
	public SimpleHandler4TestNginxClojureSocket() {
		if (log == null) {
			log = NginxClojureRT.getLog();
		}
	}
	
	/* (non-Javadoc)
	 * @see nginx.clojure.Configurable#config(java.util.Map)
	 */
	@Override
	public void config(Map<String, String> properties) {
		if (properties.get("url") != null) {
			String url = properties.get("url");
			if (url.startsWith("unix:")) {
				host = url;
				port = 0;
			}else {
				String[] a = url.split(":");
				host = a[0];
				port = Integer.parseInt(a[1]);
			}
		}
	}

	
	@Override
	public Object[] invoke(Map<String, Object> request) {
		Socket socket = new Socket();
		try {
			//http://www.apache.org/apache/httpcomponents/httpclient/RELEASE_NOTES-4.3.x.txt
			InetSocketAddress inetSocketAddress = new InetSocketAddress(host, port);
			socket.setSoTimeout(50000);
			socket.setTcpNoDelay(true);
			socket.setKeepAlive(true);
//			socket.bind(new InetSocketAddress("192.168.10.56", 12345));
//			socket.bind(new InetSocketAddress("192.168.2.50", 0));
			socket.connect(inetSocketAddress);
			log.info("addr: %s, sotimeout %s, socket keepalive = %s, tcpnodelay = %s", inetSocketAddress, socket.getSoTimeout(),  socket.getKeepAlive()+"", socket.getTcpNoDelay()+"");
			log.info("fininsh connect");
			OutputStream out = socket.getOutputStream();
//			out.write("GET /ubuntu/dists/trusty/Release HTTP/1.1\r\nUser-Agent: nginx-clojure/0.2.0\r\nHost: mirrors.163.com\r\nAccept: */*\r\nConnection: close\r\n\r\n".getBytes());
			out.write("GET /dist/httpcomponents/httpclient/RELEASE_NOTES-4.3.x.txt HTTP/1.1\r\nUser-Agent: nginx-clojure/0.4.1\r\nHost: www.apache.org\r\nAccept: */*\r\nConnection: close\r\n\r\n".getBytes());
			out.flush();
			log.info("fininsh request");
			if (host.startsWith("unix:")) {
				socket.shutdownOutput();
			}
			InputStream in = socket.getInputStream();
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			byte[] buf = new byte[4096];
			int c = 0;
			int total = 0;
			while ( (c = in.read(buf)) > 0) {
				total += c;
				log.info("read:%d, total:%d", c, total);
				bos.write(buf, 0, c);
			}
			log.info("fininsh total read: %d", total);
			return new Object[] {200, ArrayMap.create(Constants.CONTENT_TYPE, "text/html"), new ByteArrayInputStream(bos.toByteArray())};
		} catch (IOException e) {
			throw new RuntimeException("error happend!", e);
		}
		finally {
			try {
				socket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	public static void main(String[] args) throws IOException {
		SimpleHandler4TestNginxClojureSocket ss = new SimpleHandler4TestNginxClojureSocket();
		@SuppressWarnings("unchecked")
		Object[] resp =  ss.invoke(Collections.EMPTY_MAP);
		ByteArrayInputStream bi = (ByteArrayInputStream)resp[2];
		byte[] ba = new byte[bi.available()];
		bi.read(ba);
		System.out.println(new String(ba));
	}


}
