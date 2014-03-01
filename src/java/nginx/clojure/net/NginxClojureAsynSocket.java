package nginx.clojure.net;

import clojure.lang.IFn;
import nginx.clojure.Constants;


public class NginxClojureAsynSocket implements NginxClojureSocketRawHandler {
	
	public static final long NGX_HTTP_CLOJURE_SOCKET_OK = 0;
	public static final long NGX_HTTP_CLOJURE_SOCKET_ERR = -16;
	public static final long NGX_HTTP_CLOJURE_SOCKET_ERR_RESOLVE = -17;
	public static final long NGX_HTTP_CLOJURE_SOCKET_ERR_CONNECT = -18;
	public static final long NGX_HTTP_CLOJURE_SOCKET_ERR_CONNECT_TIMEOUT = -19;
	public static final long NGX_HTTP_CLOJURE_SOCKET_ERR_TIMEOUT = -20;
	public static final long NGX_HTTP_CLOJURE_SOCKET_ERR_READ = -21;
	public static final long NGX_HTTP_CLOJURE_SOCKET_ERR_READ_TIMEOUT = -22;
	public static final long NGX_HTTP_CLOJURE_SOCKET_ERR_WRITE = -23;
	public static final long NGX_HTTP_CLOJURE_SOCKET_ERR_WRITE_TIMEOUT = -24;
	public static final long NGX_HTTP_CLOJURE_SOCKET_ERR_RESET = -25;
	public static final long NGX_HTTP_CLOJURE_SOCKET_ERR_OUTOFMEMORY = -26;
	public static final long NGX_HTTP_CLOJURE_SOCKET_ERR_AGAIN = -27;


	public static final long NGX_HTTP_CLOJURE_SOCKET_SHUTDOWN_READ  = 0;
	public static final long NGX_HTTP_CLOJURE_SOCKET_SHUTDOWN_WRITE = 1;
	public static final long NGX_HTTP_CLOJURE_SOCKET_SHUTDOWN_BOTH = 2;
	public static final long NGX_HTTP_CLOJURE_SOCKET_SHUTDOWN_SOFT_FLAG = 4;
	public static final long NGX_HTTP_CLOJURE_SOCKET_SHUTDOWN_SOFT_READ = NGX_HTTP_CLOJURE_SOCKET_SHUTDOWN_READ | NGX_HTTP_CLOJURE_SOCKET_SHUTDOWN_SOFT_FLAG;
	public static final long NGX_HTTP_CLOJURE_SOCKET_SHUTDOWN_SOFT_WRITE = NGX_HTTP_CLOJURE_SOCKET_SHUTDOWN_WRITE | NGX_HTTP_CLOJURE_SOCKET_SHUTDOWN_SOFT_FLAG;
	public static final long NGX_HTTP_CLOJURE_SOCKET_SHUTDOWN_SOFT_BOTH = NGX_HTTP_CLOJURE_SOCKET_SHUTDOWN_BOTH | NGX_HTTP_CLOJURE_SOCKET_SHUTDOWN_SOFT_FLAG;

	
	protected long s;
	
	protected boolean connected;
	
	protected NginxClojureSocketHandler handler;
	
	protected Object context;
	
	
	public NginxClojureAsynSocket() {
	}
	
	public NginxClojureAsynSocket(NginxClojureSocketHandler handler) {
		this.handler = handler;
	}
	
	public NginxClojureAsynSocket(IFn f) {
		this(new ClojureFunctionHandler(f));
	}
	
	public boolean isClosed() {
		return s == 0;
	}
	
	public boolean isConnected() {
		return connected;
	}
	
	private final void checkConnected() {
		if (s == 0 || !connected) {
			throw new RuntimeException("socket has been closed or not connected");
		}
	}
	
	public long connect(String url) {
		byte[] urlba = url.getBytes(Constants.DEFAULT_ENCODING);
		s = create(this);
		if (s == 0) {
			return NGX_HTTP_CLOJURE_SOCKET_ERR_OUTOFMEMORY;
		}
		return connect(s, urlba, Constants.BYTE_ARRAY_OFFSET, urlba.length);
	}
	
	public long read(byte[] buf, long off, long size) {
		checkConnected();
		return read(s, buf, Constants.BYTE_ARRAY_OFFSET + off, size);
	}
	
	public long write(byte[] buf, long off, long size) {
		checkConnected();
		return write(s, buf, Constants.BYTE_ARRAY_OFFSET + off, size);
	}

	public long shutdown(long how) {
		checkConnected();
		return shutdown(s, how);
	}
	
	public long cancelSoftShutdown(long how) {
		checkConnected();
		return cancelSoftShutdown(s, how);
	}
	
	public void close() {
		if (s == 0) {
			return;
		}
		long os = s;
		s = 0;
		close(os);
	}

	public <T> T getContext() {
		return (T)context;
	}

	public <T> void setContext(T context) {
		this.context = context;
	}
	
	public NginxClojureSocketHandler getHandler() {
		return handler;
	}

	public void setHandler(NginxClojureSocketHandler handler) {
		this.handler = handler;
	}
	
	public void setHandler(IFn f) {
		this.handler = new ClojureFunctionHandler(f);
	}

	@Override
	public void onConnect(long u, long sc) {
		if (!connected && sc == NGX_HTTP_CLOJURE_SOCKET_OK) {
			connected = true;
		}
		handler.onConnect(this, sc);
	}

	@Override
	public void onRead(long u, long sc) {
		handler.onRead(this, sc);
	}

	@Override
	public void onWrite(long u, long sc) {
		handler.onWrite(this, sc);
	}

	@Override
	public void onRelease(long u, long sc) {
		handler.onRelease(this, sc);
	}
	
	/**
	 * 
	 * @param handler socket event handler
	 * @return native socket handle created
	 */
	private static native long create(NginxClojureSocketRawHandler handler);
	
	/**
	 * 
	 * @param s native socket handle
	 * @param buf if buf is null, off is a native buffer address
	 * @param off offest of  buf, if buf is not null, offset must include Java Object Base Offset
	 * @param size the number of bytes returned at most
	 * @return 0 : EOF, 
	 *         NGX_HTTP_CLOJURE_SOCKET_ERR_AGAIN : try on next event, 
	 *         NGX_HTTP_CLOJURE_SOCKET_ERR_READ : read error
	 */
	private static native long read(long s, Object buf, long off, long size);
	
	/**
	 * 
	 * @param s native socket handle
	 * @param buf if buf is null, off is a native buffer address
	 * @param off offest of  buf, if buf is not null, offset must include Java Object Base Offset
	 * @param size the number of bytes sent at most
	 * @return 0 : EOF,
	 *         NGX_HTTP_CLOJURE_SOCKET_ERR_AGAIN : try on next event,
	 *         NGX_HTTP_CLOJURE_SOCKET_ERR_WRITE : write error
	 */
	private static native long write(long s, Object buf, long off, long size);
	
	/**
	 * @param url byte[], eg. "192.168.2.12:8080".getBytes();
	 * @param off eg. BYTE_ARRAY_OFFSET
	 * @param len length of url bytes
	 */
	private static native long connect(long s, Object url, long off, long len);
	
	private static native void close(long s);
	
	private static native long shutdown(long s, long how);
	
	private static native long cancelSoftShutdown(long s, long how);
	
	
	public final static class ClojureFunctionHandler implements NginxClojureSocketHandler {
		
		IFn f;
		
		public ClojureFunctionHandler(IFn f) {
			this.f = f;
		}

		@Override
		public void onConnect(NginxClojureAsynSocket s, long sc) {
			f.invoke(s, "connect", sc);
		}

		@Override
		public void onRead(NginxClojureAsynSocket s, long sc) {
			f.invoke(s, "read", sc);
		}

		@Override
		public void onWrite(NginxClojureAsynSocket s, long sc) {
			f.invoke(s, "write", sc);
		}

		@Override
		public void onRelease(NginxClojureAsynSocket s, long sc) {
			f.invoke(s, "release", sc);
		}
		
	}
}
