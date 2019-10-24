package nginx.clojure.embed;

import static org.junit.Assert.assertEquals;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

import org.junit.Test;

public class Http2Test {

	public Http2Test() {
	}
	
	public static String runShell(String[] cmd) {
		try {
			Process p = Runtime.getRuntime().exec(cmd);
			try (BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
				StringBuilder b = new StringBuilder();
				String line;
				while ((line = in.readLine()) != null) {
					b.append(line).append("\n");
				}
				return b.toString();
			}
		} catch (IOException e) {
			return null;
		}
	}

	@Test
	public void testSimpleHttp2() {
		NginxEmbedServer server = NginxEmbedServer.getServer();
		server.start("test/work-dir", "http2.conf");
		String result = runShell(new String[] {"/bin/bash", "-c",  "curl -k https://localhost:8443/clojure"});
		assertEquals(result, "Hello Clojure & Nginx!\n");
		result = runShell(new String[] {"/bin/bash", "-c",  "curl -k --head https://localhost:8443/clojure"});
		assertEquals(result.substring(0, result.indexOf('\n')).trim(), "HTTP/2 200");
		server.stop();
	}
	
	public static void main(String[] args) {
		NginxEmbedServer server = NginxEmbedServer.getServer();
//		server.start("/home/who/git/nginx-clojure/test/nginx-working-dir", 
//				"/home/who/git/nginx-clojure/test/nginx-working-dir/conf/nginx-http2-plain.conf");
		String dir = new File(".").getAbsolutePath();
		server.start(dir + "/test/work-dir", dir + "/test/work-dir/http2.conf");
		
	}
}
