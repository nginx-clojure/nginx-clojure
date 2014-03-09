package nginx.clojure.net;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

import nginx.clojure.Constants;
import nginx.clojure.NginxClojureRT;
import nginx.clojure.logger.LoggerService;
import clojure.lang.AFn;
import clojure.lang.PersistentArrayMap;

public class SimpleHandler4TestNginxClojureSocket extends AFn {

	static LoggerService log;
	
	public SimpleHandler4TestNginxClojureSocket() {
		if (log == null) {
			log = NginxClojureRT.getLog();
		}
	}

	public Object invoke(Object r) {
		Socket socket = new Socket();
		try {
			InetSocketAddress inetSocketAddress = new InetSocketAddress("cn.bing.com", 80);
			socket.connect(inetSocketAddress);
			log.info("fininsh connect");
			OutputStream out = socket.getOutputStream();
//			out.write("GET / HTTP/1.1\r\nUser-Agent: nginx-clojure/0.2.0\r\nHost: cn.bing.com\r\nAccept: */*\r\nConnection: close\r\n\r\n".getBytes());
			out.write("GET / HTTP/1.1\r\nUser-Agent: nginx-clojure/0.2.0\r\nHost: cn.bing.com\r\nAccept: */*\r\n\r\n".getBytes());
			out.flush();
			log.info("fininsh request");
			socket.shutdownOutput();
			InputStream in = socket.getInputStream();
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			byte[] buf = new byte[1024];
			int c = 0;
			int total = 0;
			while ( (c = in.read(buf)) > 0) {
				total += c;
				log.info("read:%d, total:%d", c, total);
				bos.write(buf, 0, c);
			}
			log.info("fininsh total read: %d", total);
			Object[] resps = new Object[] {
					Constants.STATUS,
					200,
					Constants.HEADERS,
					new PersistentArrayMap(new Object[] {
							Constants.CONTENT_TYPE.getName(),
							"text/html" }),
			Constants.BODY, new ByteArrayInputStream(bos.toByteArray()) };
			return new PersistentArrayMap(resps);
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
	

}
