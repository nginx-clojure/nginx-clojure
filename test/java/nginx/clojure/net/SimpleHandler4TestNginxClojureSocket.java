package nginx.clojure.net;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Collections;

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
			//http://mirror.bit.edu.cn/apache/httpcomponents/httpclient/RELEASE_NOTES-4.3.x.txt
			InetSocketAddress inetSocketAddress = new InetSocketAddress("mirror.bit.edu.cn", 80);
			socket.setSoTimeout(50000);
			socket.setTcpNoDelay(true);
			socket.setKeepAlive(true);
			socket.connect(inetSocketAddress);
			log.info("addr: %s, sotimeout %s, socket keepalive = %s, tcpnodelay = %s", inetSocketAddress, socket.getSoTimeout(),  socket.getKeepAlive()+"", socket.getTcpNoDelay()+"");
			log.info("fininsh connect");
			OutputStream out = socket.getOutputStream();
//			out.write("GET /ubuntu/dists/trusty/Release HTTP/1.1\r\nUser-Agent: nginx-clojure/0.2.0\r\nHost: mirrors.163.com\r\nAccept: */*\r\nConnection: close\r\n\r\n".getBytes());
			out.write("GET /apache/httpcomponents/httpclient/RELEASE_NOTES-4.3.x.txt HTTP/1.1\r\nUser-Agent: curl/7.32.0\r\nHost: mirror.bit.edu.cn\r\nAccept: */*\r\nConnection: close\r\n\r\n".getBytes());
			out.flush();
			log.info("fininsh request");
//			socket.shutdownOutput();
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
	
	public static void main(String[] args) throws IOException {
		SimpleHandler4TestNginxClojureSocket ss = new SimpleHandler4TestNginxClojureSocket();
		PersistentArrayMap resp = (PersistentArrayMap) ss.invoke(Collections.EMPTY_MAP);
		ByteArrayInputStream bi = (ByteArrayInputStream)resp.get(Constants.BODY);
		byte[] ba = new byte[bi.available()];
		bi.read(ba);
		System.out.println(new String(ba));
	}

}
