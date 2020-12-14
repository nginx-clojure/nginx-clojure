/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.java;

import static nginx.clojure.MiniConstants.HEADERS;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;

import nginx.clojure.NginxClojureRT;

public class Loadstress {
	
	
	public static class HeaderEchoHanlder implements NginxJavaRingHandler {

		/* (non-Javadoc)
		 * @see nginx.clojure.java.NginxJavaRingHandler#invoke(java.util.Map)
		 */
		@Override
		public Object[] invoke(Map<String, Object> request) throws IOException {
			String processId = NginxClojureRT.processId;
			Map requestHeaders = (Map) request.get(HEADERS);
			Map<String, String> response = new HashMap();
			response.putAll(requestHeaders);
			response.put("pid", processId);
//			try {
//				Thread.sleep(10000);
//			} catch (InterruptedException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
			return new Object[] {200,ArrayMap.create(Constants.CONTENT_TYPE, "text/plain"), 
					new ObjectMapper().writeValueAsString(response)};
		}
		
	}
	

	/**
	 * 
	 */
	public Loadstress() {
		// TODO Auto-generated constructor stub
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		byte[] prefix = new byte[1024];
		Random random = new Random();
		
		for (int i = 0; i < prefix.length; i++) {
			prefix[i] = (byte)((Math.abs(random.nextInt()) % 26) + 'a');
		}
		
		String prefixStr = new String(prefix);
		ConcurrentHashMap<String, String> pids = new ConcurrentHashMap<String, String>();
		Thread[] threads = new Thread[50];
		int max = 100;
		String url = "http://172.16.111.1:8080/java/loadheader";
		System.out.println(prefixStr);
		System.out.println((prefixStr + UUID.randomUUID().toString()).length());
		
//		if (1 == 1) {
//			return;
//		}
		
		for (int i = 0; i < threads.length; i++) {
			threads[i] = new Thread(()->{
				for (int c = 0; c < max; c++) {
					String cookie = prefixStr + UUID.randomUUID().toString();
					ObjectMapper om = new ObjectMapper();
					try(CloseableHttpClient httpClient = HttpClients.custom()
					        .build()) {
						HttpGet httpGet = new HttpGet(url);
						httpGet.addHeader("Cookie", "sid="+cookie);
						HttpResponse response = httpClient.execute(httpGet);
						String body = EntityUtils.toString(response.getEntity());
//						System.out.println(body);
						Map<String,String> rh = om.readValue(body, new TypeReference<Map<String,String>>() {
				        });
						if (!("sid="+cookie).equals(rh.get("Cookie"))) {
							System.err.println("wrong cookie:" + rh.get("Cookie") + ", r:" + cookie);
						}
						pids.put(rh.get("pid"), "g");
					} catch (Throwable e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

				}
			});
			threads[i].start();
		}
		
		for (int i = 0; i < threads.length; i++) {
			try {
				threads[i].join();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		System.out.println("pids " + pids.size());
		System.out.println(pids);
	}

}
