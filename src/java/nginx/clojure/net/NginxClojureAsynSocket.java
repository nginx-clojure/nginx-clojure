/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.net;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;

import sun.nio.ch.DirectBuffer;
import nginx.clojure.HackUtils;
import nginx.clojure.MiniConstants;
import nginx.clojure.NginxClojureRT;


public class NginxClojureAsynSocket implements NginxClojureSocketRawHandler, Closeable {
	
	public static final long NGX_HTTP_CLOJURE_SOCKET_OK = 0;
	public static final long NGX_HTTP_CLOJURE_SOCKET_ERR = -16;
	public static final long NGX_HTTP_CLOJURE_SOCKET_ERR_RESOLVE = -17;
	public static final long NGX_HTTP_CLOJURE_SOCKET_ERR_CONNECT = -18;
	public static final long NGX_HTTP_CLOJURE_SOCKET_ERR_CONNECT_TIMEOUT = -19;
	/**
	 * @deprecated please use either of NGX_HTTP_CLOJURE_SOCKET_ERR_CONNECT_TIMEOUT, NGX_HTTP_CLOJURE_SOCKET_ERR_READ_TIMEOUT or NGX_HTTP_CLOJURE_SOCKET_ERR_WRITE_TIMEOUT
	 */
	public static final long NGX_HTTP_CLOJURE_SOCKET_ERR_TIMEOUT = -20;
	public static final long NGX_HTTP_CLOJURE_SOCKET_ERR_READ = -21;
	public static final long NGX_HTTP_CLOJURE_SOCKET_ERR_READ_TIMEOUT = -22;
	public static final long NGX_HTTP_CLOJURE_SOCKET_ERR_WRITE = -23;
	public static final long NGX_HTTP_CLOJURE_SOCKET_ERR_WRITE_TIMEOUT = -24;
	public static final long NGX_HTTP_CLOJURE_SOCKET_ERR_RESET = -25;
	public static final long NGX_HTTP_CLOJURE_SOCKET_ERR_OUTOFMEMORY = -26;
	public static final long NGX_HTTP_CLOJURE_SOCKET_ERR_AGAIN = -27;
	public static final long NGX_HTTP_CLOJURE_SOCKET_ERR_BIND = -28;


	public static final long NGX_HTTP_CLOJURE_SOCKET_SHUTDOWN_READ  = 0;
	public static final long NGX_HTTP_CLOJURE_SOCKET_SHUTDOWN_WRITE = 1;
	public static final long NGX_HTTP_CLOJURE_SOCKET_SHUTDOWN_BOTH = 2;
	public static final long NGX_HTTP_CLOJURE_SOCKET_SHUTDOWN_SOFT_FLAG = 4;
	public static final long NGX_HTTP_CLOJURE_SOCKET_SHUTDOWN_SOFT_READ = NGX_HTTP_CLOJURE_SOCKET_SHUTDOWN_READ | NGX_HTTP_CLOJURE_SOCKET_SHUTDOWN_SOFT_FLAG;
	public static final long NGX_HTTP_CLOJURE_SOCKET_SHUTDOWN_SOFT_WRITE = NGX_HTTP_CLOJURE_SOCKET_SHUTDOWN_WRITE | NGX_HTTP_CLOJURE_SOCKET_SHUTDOWN_SOFT_FLAG;
	public static final long NGX_HTTP_CLOJURE_SOCKET_SHUTDOWN_SOFT_BOTH = NGX_HTTP_CLOJURE_SOCKET_SHUTDOWN_BOTH | NGX_HTTP_CLOJURE_SOCKET_SHUTDOWN_SOFT_FLAG;

	public static final String[] NGX_HTTP_CLOJURE_SOCKET_ERROR_STRS = {
		"socket general error" , //NGX_HTTP_CLOJURE_SOCKET_ERR,
		"socket resolve error" ,//NGX_HTTP_CLOJURE_SOCKET_ERR_RESOLVE
		"socket connect error" , //NGX_HTTP_CLOJURE_SOCKET_ERR_CONNECT
		"socket connect timeout", //NGX_HTTP_CLOJURE_SOCKET_ERR_CONNECT_TIMEOUT
		"socket timeout", //NGX_HTTP_CLOJURE_SOCKET_ERR_TIMEOUT
		"socket read error", //NGX_HTTP_CLOJURE_SOCKET_ERR_READ
		"socket read timeout", //NGX_HTTP_CLOJURE_SOCKET_ERR_READ_TIMEOUT
		"socket write error" , //NGX_HTTP_CLOJURE_SOCKET_ERR_WRITE
		"socket write timeout" , //NGX_HTTP_CLOJURE_SOCKET_ERR_WRITE_TIMEOUT
		"socket reset", //NGX_HTTP_CLOJURE_SOCKET_ERR_RESET
		"socket out of memory" , //NGX_HTTP_CLOJURE_SOCKET_ERR_OUTOFMEMORY
		"socket try again"     , //NGX_HTTP_CLOJURE_SOCKET_ERR_AGAIN
		"socket bind error" ,  //NGX_HTTP_CLOJURE_SOCKET_ERR_BIND
	};
	
	protected long s;
	
	protected boolean connected;
	
	protected NginxClojureSocketHandler handler;
	
	protected Object context;
	
	protected String url;
	
	
	public NginxClojureAsynSocket() {
		if (Thread.currentThread() != NginxClojureRT.NGINX_MAIN_THREAD) {
			throw new IllegalAccessError("NginxClojureAsynSocket can only be operated in main thread");
		}
		s = create(this);
		if (s == 0) {
			throw new OutOfMemoryError("no memory for create a native nginx clojure socket");
		}
	}
	
	public NginxClojureAsynSocket(NginxClojureSocketHandler handler) {
		this();
		this.handler = handler;
	}
	
	
	public int available() {
		return (int)available(s);
	}
	
	public long setTcpNoDelay(long tcpNoDelay) {
		return setTcpNoDelay(s, tcpNoDelay);
	}
	
	public long getTcpNoDelay() {
		return getTcpNoDelay(s);
	}
	
	public long setSoKeepAlive(long soKeepAlive) {
		return setSoKeepAlive(s, soKeepAlive);
	}
	
	public long getSoKeepAlive() {
		return getSoKeepAlive(s);
	}
	
	public boolean isClosed() {
		return s <= 0;
	}
	
	public boolean isConnected() {
		return s > 0 && connected;
	}
	
	public final void checkConnected() {
		if (s <= 0 || !connected) {
			throw new RuntimeException("socket has been closed or not connected!");
		}
	}
	
	public final void checkNotClosed() {
		if (s <= 0) {
			throw new RuntimeException("socket has been closed!");
		}
	}
	
	public final static String errorCodeToString(long sc) {
		return NGX_HTTP_CLOJURE_SOCKET_ERROR_STRS[(int)(NGX_HTTP_CLOJURE_SOCKET_ERR - sc)];
	}
	
	public final String buildError(long sc) {
		StringBuilder err = new StringBuilder(errorCodeToString(sc));
		err.append(" On Server ").append(url);
		return err.toString();
	}
	
	/**
	 * if timeout is negative, it will be ignored. if timeout is 0, this means no timeout.
	 */
	public void setConnectTimeout(long timeout) {
		checkNotClosed();
		setTimeout(s, timeout, -1, -1);
	}
	
	/**
	 * if timeout is negative, it will be ignored. if timeout is 0, this means no timeout.
	 */
	public void setReadTimeout(long timeout) {
		checkNotClosed();
		setTimeout(s, -1, timeout, -1);
	}
	
	/**
	 * if timeout is negative, it will be ignored. if timeout is 0, this means no timeout.
	 */
	public void setWriteTimeout(long timeout) {
		checkNotClosed();
		setTimeout(s, -1, -1, timeout);
	}
	
	/**
	 * if timeout is negative, it will be ignored. if timeout is 0, this means no timeout.
	 */
	public void setTimeout(long connectTimeout, long readTimeout, long writeTimeout) {
		checkNotClosed();
		setTimeout(s, connectTimeout, readTimeout, writeTimeout);
	}
	
	public  long getReadTimeout() {
		checkNotClosed();
		return getReadTimeout(s);
	}
	
	public  long getWriteTimeout() {
		checkNotClosed();
		return getWriteTimeout(s);
	}
	
	public  long getConnectTimeout() {
		checkNotClosed();
		return getConnectTimeout(s);
	}
	
	public long  getReceiveBufferSize() {
		checkNotClosed();
		return getReceiveBufferSize(s);
	}
	
	public void  setReceiveBufferSize(long size) {
		checkNotClosed();
		setReceiveBufferSize(s, size);
	}
	
	/**
	 * 
	 * @param url
	 * e.g. "192.168.2.34:80" , "www.bing.com:80", or unix domain socket "unix:/var/mytest/server.sock"
	 * @return
	 */
	public long connect(String url) {
		this.url = url;
		ByteBuffer b = HackUtils.encode(url, MiniConstants.DEFAULT_ENCODING, NginxClojureRT.pickByteBuffer());
		return connect(s, b.array(), MiniConstants.BYTE_ARRAY_OFFSET, b.remaining());
	}
	
	public long bind(String addr) {
		ByteBuffer b = HackUtils.encode(addr, MiniConstants.DEFAULT_ENCODING, NginxClojureRT.pickByteBuffer());
		return bind(s, b.array(), MiniConstants.BYTE_ARRAY_OFFSET, b.remaining());
	}
	
	/**
	 * 
	 * @param buf buffer
	 * @param off offest of  buf,
	 * @param size the number of bytes returned at most
	 * @return 0 : EOF, 
	 *         NGX_HTTP_CLOJURE_SOCKET_ERR_AGAIN : try on next event, 
	 *         NGX_HTTP_CLOJURE_SOCKET_ERR_READ : read error
	 *         > 0 : the number of bytes read
	 */
	public long read(byte[] buf, long off, long size) {
		checkConnected();
		return read(s, buf, MiniConstants.BYTE_ARRAY_OFFSET + off, size);
	}
	
	
	/**
	 * @param buf buffer
	 * @return 0 : EOF, 
	 *         NGX_HTTP_CLOJURE_SOCKET_ERR_AGAIN : try on next event, 
	 *         NGX_HTTP_CLOJURE_SOCKET_ERR_READ : read error
	 *         > 0 : the number of bytes read
	 */
	public long read(ByteBuffer buf) {
		long rc;
		if (buf.isDirect()) {
			rc = read(s, null, ((DirectBuffer)buf).address()+buf.position(), buf.remaining());
		}else {
			rc = read(s, buf.array(), MiniConstants.BYTE_ARRAY_OFFSET + buf.arrayOffset()+buf.position(), buf.remaining());
		}
		if (rc > 0) {
			buf.position(buf.position() + (int)rc);
		}
		return rc;
	}
	
	/**
	 * 
	 * @param buf buffer
	 * @param off offest of  buf
	 * @param size the number of bytes sent at most
	 * @return 0 : EOF,
	 *         NGX_HTTP_CLOJURE_SOCKET_ERR_AGAIN : try on next event,
	 *         NGX_HTTP_CLOJURE_SOCKET_ERR_WRITE : write error
	 *         > 0 : the number of bytes sent
	 */
	public long write(byte[] buf, long off, long size) {
		checkConnected();
		return write(s, buf, MiniConstants.BYTE_ARRAY_OFFSET + off, size);
	}
	
	public long write(ByteBuffer buf) {
		long rc;
		if (buf.isDirect()) {
			rc = write(s, null, ((DirectBuffer)buf).address()+buf.position(), buf.remaining());
		}else {
			rc = write(s, buf.array(), MiniConstants.BYTE_ARRAY_OFFSET + buf.arrayOffset()+buf.position(), buf.remaining());
		}
		if (rc > 0) {
			buf.position(buf.position() + (int)rc);
		}
		return rc;
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
		if (s <= 0) {
			return;
		}
		s = -s;
		close(-s);
	}

	@SuppressWarnings({"unchecked"})
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
	

	@Override
	public void onConnect(long u, long sc) throws IOException {
		if (!connected && sc == NGX_HTTP_CLOJURE_SOCKET_OK) {
			connected = true;
		}
		handler.onConnect(this, sc);
	}

	@Override
	public void onRead(long u, long sc) throws IOException {
		handler.onRead(this, sc);
	}

	@Override
	public void onWrite(long u, long sc) throws IOException {
		handler.onWrite(this, sc);
	}

	@Override
	public void onRelease(long u, long sc) throws IOException {
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
	 * @return available bytes
	 */
	private static native long available(long s);
	
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
	
	private static native void setTimeout(long s, long ctimeout, long rtimeout, long wtimeout);
	
	private static native long  setTcpNoDelay(long s, long tcpNoDelay);
	
	private static native long  getTcpNoDelay(long s);
	
	private static native long  setSoKeepAlive(long s, long soKeepAlive);
	
	private static native long  getSoKeepAlive(long s);
	
	private static native long getReadTimeout(long s);
	
	private static native long getWriteTimeout(long s);
	
	private static native long getConnectTimeout(long s);
	
	private static native long  getReceiveBufferSize(long s);
	
	private static native long  setReceiveBufferSize(long s, long size);
	
	/**
	 * @param url byte[], eg. "192.168.2.12:8080".getBytes();
	 * @param off eg. BYTE_ARRAY_OFFSET
	 * @param len length of url bytes
	 */
	private static native long connect(long s, Object url, long off, long len);
	
	private static native long bind(long s, Object addr, long off, long len);
	
	private static native void close(long s);
	
	private static native long shutdown(long s, long how);
	
	private static native long cancelSoftShutdown(long s, long how);

}
