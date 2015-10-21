/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.embed;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;

import nginx.clojure.DiscoverJvm;
import nginx.clojure.MiniConstants;
import nginx.clojure.NginxClojureRT;
import nginx.clojure.java.ArrayMap;
import nginx.clojure.java.NginxJavaRequest;
import nginx.clojure.java.NginxJavaRingHandler;

public class NginxEmbedServer {

	protected final static int EMBED_OK = 0;
	protected final static int EMBED_ERR  = -1;
	protected final static int EMBED_PASS_CONF = 1;
	protected final static int EMBED_PASS_ALL = 2;
	
	protected final static NginxEmbedServer instance;
	protected boolean started = false;
	protected String workDir;
	protected CountDownLatch stopCountDownLatch;
	static BlockingQueue<EmbedStartEvent> embedStarteEventQueue = new LinkedBlockingQueue<EmbedStartEvent>();
	static File tmpRoot;
	static {
		loadDynamicLibrary();
		if (register() != 0) {
			throw new RuntimeException("NginxEmbedServer can not register native methods!");
		}
		instance = new NginxEmbedServer();
	}
	
	private NginxEmbedServer() {
		try {
			workDir = new File(System.getProperty("user.dir")).getCanonicalPath();
		} catch (IOException e) {
			throw new RuntimeException("can not getCanonicalPath() for setting workDir of nginx embed server", e);
		}
	}
	
	private  static void loadDynamicLibrary() {
		tmpRoot = new File(System.getProperty("java.io.tmpdir") + "/nginx-clojure-embed-" + NginxClojureRT.processId);
		tmpRoot.mkdirs();
		tmpRoot.deleteOnExit();
		if (System.getProperty("nginx.clojure.embed.sopath") != null) {
			System.load(System.getProperty("nginx.clojure.embed.sopath"));
			return;
		}
		String soname = "nginx-clojure-embed-" + DiscoverJvm.detectOSArchExt();
		URL url = NginxEmbedServer.class.getClassLoader().getResource("slib/" + soname);
		
		if (url == null) {
			String error = "can not get slib/" + soname +" at class path";
			NginxClojureRT.getLog().error(error);
			throw new RuntimeException(error);
		}
		
		InputStream in = null;
		
		try {
			URLConnection conn = url.openConnection();
			in = conn.getInputStream();
			File soRoot = new File(tmpRoot.getParentFile(), "nginx-clojure-embed-so-" + MiniConstants.NGINX_CLOJURE_RT_VER);
			soRoot.mkdir();
			File sofile = new File(soRoot, soname);
			if (!sofile.exists() || conn.getLastModified() > sofile.lastModified()) {
				FileOutputStream out = new FileOutputStream(sofile);
				byte[] buffer = new byte[4096];
				int c = 0;
				while ( (c = in.read(buffer)) != -1) {
					out.write(buffer, 0, c);
				}
				out.close();
				if (DiscoverJvm.detectOSArchExt().startsWith("linux")) {
					/**
					 * fix SELinux Enabled issue ,e.g.
					 * Exception in thread "main" java.lang.UnsatisfiedLinkError: 
					 * nginx-clojure-embed-linux-xxx.so: cannot restore segment prot after reloc: Permission denied 
					 */
					Process p = Runtime.getRuntime().exec(new String[] {"/bin/sh", "-c", "chcon -u system_u -r object_r -t textrel_shlib_t " + sofile.getAbsolutePath()});
					try {
						if (p.waitFor() != 0) {
							NginxClojureRT.getLog().debug("fail to change security of nginx clojure embed shared library, just ignore it.");
						}
					} catch (InterruptedException e) {
						NginxClojureRT.getLog().error("can not write so file " + tmpRoot.getAbsoluteFile() + "/" + soname , e);
					}
				}
			}
			
			System.load(sofile.getAbsolutePath());
		} catch (IOException e) {
			NginxClojureRT.getLog().error("can not write so file " + tmpRoot.getAbsoluteFile() + "/" + soname , e);
		}finally{
			try {
				if (in != null) {
					in.close();
				}
			} catch (IOException e) {
				NginxClojureRT.getLog().error(e);
			}
		}
		
	}
	
	public static NginxEmbedServer getServer() {
		return instance;
	}
	
	private native static long register();
	
	public static class EmbedStartEvent {
		int type;
		String message;
		public EmbedStartEvent() {
		}
		public EmbedStartEvent(int type, String message) {
			this.type = type;
			this.message = message;
		}
	}
	
	private static void notifyFromNative(int type, String message) {
		if (type == EMBED_PASS_ALL) {
			NginxJavaRequest.fixDefaultRequestArray();
			try{
				nginx.clojure.clj.LazyRequestMap.fixDefaultRequestArray();
			}catch(Throwable e) {};
		}
		embedStarteEventQueue.add(new EmbedStartEvent(type, message));
	}
	
	private native static long innerStart(String cmd);
	
	private native static void innerStop();
	
	public void setWorkDir(String workDir) {
		this.workDir = workDir;
	}
	
	public static int pickFreePort() {
		Socket s = new Socket();
		try {
			s.bind(null);
			int port = s.getLocalPort();
			s.setReuseAddress(true);
			s.close();
			return port;
		} catch (IOException e) {
			throw new RuntimeException("can not pickFreePort", e);
		}
	}
	
	/**
	 * @param handler class name of a instance of NginxJavaRingHandler
	 * @param options default options are 
	 * <pre>
	 *          "error-log", "logs/error.log",
	 *          "max-connections", "1024",
	 *          "access-log", "off",
	 *          "keepalive-timeout", "65",
	 *          "max-threads", "8",
	 *          "host", "0.0.0.0",
	 *          "port", "8080",
	 *          "jvm-init-handler-type", "java" 
	 *          "jvm-init-handler-name", "nginx.clojure.embed.NginxEmbedServer$DefaultJvmInitHandler"
	 *          //user defined zone
	 *          "global-user-defined", "",
	 *          "http-user-defined", "",
	 *          "types-user-defined", "",
	 *          "server-user-defined", "",
	 *          "location-user-defined", "" 
	 * </pre>
	 * @return the listening port
	 */
	public int start(String handler, final Map<String, String> options) {
		
		Map<String, String> moptions = ArrayMap.create(
				"error-log", "logs/error.log",
				"max-connections", "1024",
				"access-log", "off",
				"keepalive-timeout", "65",
				"max-threads", "8",
				"host", "0.0.0.0",
				"port", "8080",
				"jvm_handler_type", "java" ,
				"jvm-init-handler-name", "nginx.clojure.embed.NginxEmbedServer$DefaultJvmInitHandler",
				"content-handler-type", "java",
				"content-handler-name", handler,
				//user defined zone
				"global-user-defined", "",
				"http-user-defined", "",
				"types-user-defined", "",
				"server-user-defined", "",
				"location-user-defined", ""
				);
		for (Map.Entry<String, String> entry : options.entrySet()) {
			if (entry.getKey().equals("port") && entry.getValue().equals("0")) {
				entry.setValue(pickFreePort()+"");
			}
			if (moptions.containsKey(entry.getKey())) {
				moptions.put(entry.getKey(), entry.getValue());
			}else {
				NginxClojureRT.getLog().warn("[nginx-clojure embed] skipping unsupported option: %s", entry.getKey());
			}
		}
		BufferedReader r = new BufferedReader(new InputStreamReader(this.getClass().getResourceAsStream("cftmpl"), MiniConstants.DEFAULT_ENCODING));
		BufferedWriter w;
		
		File cfg = new File(tmpRoot, "embed.conf");
		tmpRoot.deleteOnExit();
		try {
			w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(cfg), MiniConstants.DEFAULT_ENCODING));
		} catch (FileNotFoundException e) {
			throw new RuntimeException("can not create tmp conf file for nginx-clojure embed server!", e);
		}
		int c;
		int state = 0; // 1 meet #, 2 meet #{, 0 normal
		StringBuilder var = new StringBuilder();
		try{
			while ( (c = r.read()) != -1) {
				switch (c) {
				case '#':
					if (state == 1) {
						w.write('#');
						state = 0;
					}
					state = 1;
					break;
				case '{' :
					if (state == 1) {
						state = 2;
					}else {
						w.write('{');
					}
					break;
				case '}' :
					if (state == 2) {
						state = 0;
						String val = moptions.get(var.toString());
						if (val != null) {
							w.write(val);
						}
						var.delete(0, var.length());
					}else {
						w.write('}');
					}
					break;
				default:
					if (state == 2) {
						var.append((char)c);
					}else if (state == 1) {
						w.write('#');
						w.write((char)c);
						state = 0;
					}else {
						w.write((char)c);
					}
					break;
				}
			}
		}catch(IOException e) {
			throw new RuntimeException("can not create tmp conf file for nginx-clojure embed server!", e);
		}finally {
			try {
				r.close();
				w.close();
			} catch (IOException e) {
				throw new RuntimeException("can not close tmp conf file for nginx-clojure embed server!", e);
			}
		}
		cfg.deleteOnExit();
		try {
			start(cfg.getCanonicalPath());
			NginxClojureRT.getLog().info("[nginx-clojure embed] server listening at port %s.", moptions.get("port"));
		} catch (IOException e) {
			throw new RuntimeException("can not getCanonicalPath of conf file for nginx-clojure embed server!", e);
		}
		return Integer.parseInt(moptions.get("port"));
	}
	
	public void start(final String workDir, final String cfg) {
		this.workDir = workDir;
		start(cfg);
	}
	
	public synchronized void start(final String cfg) {
		if (started) {
			throw new IllegalStateException("server has already started!");
		}
		new File(workDir, "logs").mkdir();
		new File(workDir, "temp").mkdir();
		NginxClojureRT.getLog().info("[nginx-clojure embed] starting......");
		stopCountDownLatch = new CountDownLatch(1);
		Thread t = new Thread(new Runnable() {
			@Override
			public void run() {
				try{
					started = true;
					StringBuilder cmdsb = new StringBuilder();
					cmdsb.append("java\n").append("-p\n").append(workDir).append("\n")
					.append("-c\n").append(cfg).append("\n")
					.append("-g\n").append("working_directory ").append(workDir).append(";");
					innerStart(cmdsb.toString());
				}finally{
					started = false;
					stopCountDownLatch.countDown();
				}
			}
		});
		t.setName("nginx-clojure-embed");
		t.start();
		do {
			try {
				EmbedStartEvent event = embedStarteEventQueue.take();
				switch (event.type) {
				case EMBED_ERR :
					started = false;
					throw new RuntimeException("start error:"+ event.message);
				case EMBED_PASS_CONF:
					NginxClojureRT.getLog().info("[nginx-clojure embed] finish configuration check");
					break;
				case EMBED_PASS_ALL:
					NginxClojureRT.getLog().info("[nginx-clojure embed] server started!");
					return;
				}
			} catch (InterruptedException e) {
				NginxClojureRT.getLog().warn("Interrupted!", e);
				break;
			}
		}while(true);
	}
	
	public synchronized void stop()  {
		if (!started) {
			throw new IllegalStateException("server not started!");
		}
		NginxClojureRT.getLog().info("[nginx-clojure embed] server stopping.....");
		innerStop();
		try {
			stopCountDownLatch.await();
			NginxClojureRT.getLog().info("[nginx-clojure embed] server stopped");
		} catch (InterruptedException e) {
			NginxClojureRT.getLog().warn("stop Interrupted!", e);
		}
	}
	
	public static class DefaultJvmInitHandler implements NginxJavaRingHandler {
		@Override
		public Object[] invoke(Map<String, Object> request) throws IOException {
			return null;
		}
	}
}
