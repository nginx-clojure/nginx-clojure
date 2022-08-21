/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.BindException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.net.PortUnreachableException;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketImpl;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import nginx.clojure.Coroutine;
import nginx.clojure.Coroutine.State;
import nginx.clojure.NginxClojureRT;
import nginx.clojure.logger.LoggerService;
import nginx.clojure.logger.TinyLogService;
import nginx.clojure.logger.TinyLogService.MsgType;

public class NginxClojureSocketImpl extends SocketImpl implements NginxClojureSocketHandler {
	
	final static int YIELD_CONNECT = 1;
	final static int YIELD_READ = 2;
	final static int YIELD_WRITE = 3;
	
	final static long SOCKET_FIELD_OFFSET_OF_SOCKETIMPL;
	
	public static final String NGINX_CLOJURE_LOG_SOCKET_LEVEL = "nginx.clojure.logger.socket.level";
	
	static {
		Field socketField = null;
		try {
			socketField = SocketImpl.class.getDeclaredField("socket");
		} catch (Throwable e) {
		} 
		if (socketField != null) {
			SOCKET_FIELD_OFFSET_OF_SOCKETIMPL = NginxClojureRT.UNSAFE.objectFieldOffset(socketField);
		}else {
			SOCKET_FIELD_OFFSET_OF_SOCKETIMPL = 0;
		}
	}
	
	protected static LoggerService log;
	
	protected NginxClojureAsynSocket as;
	protected Coroutine coroutine;
	protected int yieldFlag = 0;
	protected long status = NginxClojureAsynSocket.NGX_HTTP_CLOJURE_SOCKET_OK;
	protected SocketInputStream inputStream;
	protected SocketOutputStream outputStream;

	public NginxClojureSocketImpl() {
		if (Thread.currentThread() != NginxClojureRT.NGINX_MAIN_THREAD) {
			throw new IllegalAccessError("coroutine based sockets can only be called in main thread");
		}
		if (log == null) {
			log = new TinyLogService(TinyLogService.getSystemPropertyOrDefaultLevel(NGINX_CLOJURE_LOG_SOCKET_LEVEL, MsgType.info), System.err, System.err);
		}
	}
	

	protected Socket fetchSocket()  {
		if (SOCKET_FIELD_OFFSET_OF_SOCKETIMPL != 0) {
			log.debug("we'll get socket field object from NginxClojureSocketImpl");
			return (Socket) NginxClojureRT.UNSAFE.getObject(this, SOCKET_FIELD_OFFSET_OF_SOCKETIMPL);
		}
		return null;
	}
	
	protected void attachCoroutine() {
		Coroutine ac = Coroutine.getActiveCoroutine();
		if (coroutine == null || coroutine.getState() == Coroutine.State.FINISHED) {
			coroutine = ac;
		}
	}
	
	public static void setOption(NginxClojureAsynSocket as, int optID, Object v)  {
		NginxClojureRT.getLog().debug("set socket options: %d, val: %s" , optID, v+"");
		switch (optID) {
		case SO_TIMEOUT:
			if (v == null || !(v instanceof Integer)) {
                throw new IllegalArgumentException("wrong argument for SO_TIMEOUT, it must be Integer! But it is " +  ((v == null) ? "null" : v.getClass()));
            }
            int tmp = ((Integer) v).intValue();
            if (tmp < 0) {
                throw new IllegalArgumentException("timeout < 0");
            }
            as.setReadTimeout(tmp);
            return;
		case SO_RCVBUF:
			if (as.isConnected()) {
				throw new IllegalArgumentException("SO_RCVBUF must set before connected");
			}
			if (v == null || !(v instanceof Integer)
					|| ((Integer) v).intValue() < 0) {
				NginxClojureRT.UNSAFE.throwException(new SocketException(
						"wrong argument for SO_RCVBUF, it must be Integer! But it is "
								+ ((v == null) ? "null" : v.getClass())));
			}
			as.setReceiveBufferSize(((Integer) v).intValue());
			return;
		case TCP_NODELAY:
			as.setTcpNoDelay(((Boolean)v).booleanValue() ? 1 : 0);
			return;
		case SO_KEEPALIVE:
			as.setSoKeepAlive(((Boolean)v).booleanValue() ? 1 : 0);
			return;
		case SO_REUSEADDR:
			break;
		case SO_LINGER:
		case IP_TOS:
		case SO_BINDADDR:
        case SO_SNDBUF:
        case SO_OOBINLINE:
        	NginxClojureRT.getLog().warn("not supported socket options: %d, val: %s just ignored" , optID, v+"");
        	break;
        default:
        	NginxClojureRT.UNSAFE.throwException( new SocketException("unknown TCP option: " + optID) );
        }
	}
	
	@Override
	public void setOption(int optID, Object v) throws SocketException {
		//now java.net.socket has done safe close check so we can ignore it
		//checkCreatedAndNotClosed();
		setOption(as, optID, v);
	}

	public boolean isClosed() {
		return as == null || as.isClosed();
	}

	public void checkCreatedAndNotClosed() throws SocketException {
		if (as == null) {
			throw new SocketException("Socket not created!");
		}
		if (as.isClosed()) {
			throw new SocketException("Socket Closed");
		}
	}

	public static Object getOption(NginxClojureAsynSocket as, int optID) {
		switch (optID) {
		case SO_TIMEOUT:
           return Integer.valueOf((int)as.getReadTimeout());
		case SO_RCVBUF:
			return Integer.valueOf((int)as.getReceiveBufferSize());
		case TCP_NODELAY:
			return Boolean.valueOf(as.getTcpNoDelay() == 1);
		case SO_KEEPALIVE:
			return Boolean.valueOf(as.getSoKeepAlive() == 1);
		}
		return null;
	}
	
	@Override
	public Object getOption(int optID) throws SocketException {
		//now java.net.socket has done safe close check so we can ignore it
		//checkCreatedAndNotClosed();
		return getOption(as, optID);
	}

	
	@Override
	protected void create(boolean stream) throws IOException {
		if (!stream) {
			throw new UnsupportedOperationException("stream = false not supported!");
		}
		as = new NginxClojureAsynSocket(this);
		/*
		 * Although the default SO_TIMEOUT in java socket is 0, when SO_TIMEOUT = 0 a blocked Java Socket will call socket API connect
		 * which will use a default timeout from system configuration. So to keep the same behavior with java build-in
		 * Socket implementation, we should give a similar connect timeout.
		 * e.g. On Linux the connection retries is in file /proc/sys/net/ipv4/tcp_syn_retries, on most case the value is 5 
		 * Linux tcp man page says it corresponds to approximately 180 seconds but my test result is about 127s
		 */
		as.setConnectTimeout(120000);
	}

	@Override
	protected void connect(String host, int port) throws IOException {
		// now java.net.socket has done safe close check so we can ignore it
		// checkCreatedAndNotClosed();
		if (log.isDebugEnabled()) {
			log.debug("socket#%d: connecting to %s:%d", as.s, host, port);
		}
		
		if (port > 0) {
			as.connect(new StringBuilder(host).append(':').append(port).toString());
		} else {
			as.connect(host);
		}
		
		if (!as.isConnected()) {
			yieldFlag = YIELD_CONNECT;
			if (log.isTraceEnabled()) {
				log.trace("show connect stack trace for debug", new Exception("DEBUG USAGE"));
			}
			if (status == NginxClojureAsynSocket.NGX_HTTP_CLOJURE_SOCKET_ERR_RESOLVE) {
				throw new NoRouteToHostException(as.buildError(status));
			} else if (status == NginxClojureAsynSocket.NGX_HTTP_CLOJURE_SOCKET_ERR_CONNECT) {
				throw new PortUnreachableException(as.buildError(status));
			} else if (status != NginxClojureAsynSocket.NGX_HTTP_CLOJURE_SOCKET_OK) {
				throw new ConnectException(as.buildError(status));
			}
			
			if (log.isDebugEnabled()) {
				log.debug("socket#%d: yield on connect", as.s);
			}
			attachCoroutine();
			Coroutine.yield();
			
			if (status == NginxClojureAsynSocket.NGX_HTTP_CLOJURE_SOCKET_ERR_RESOLVE) {
				throw new NoRouteToHostException(as.buildError(status));
			} else if (status == NginxClojureAsynSocket.NGX_HTTP_CLOJURE_SOCKET_ERR_CONNECT) {
				throw new PortUnreachableException(as.buildError(status));
			} else if (status != NginxClojureAsynSocket.NGX_HTTP_CLOJURE_SOCKET_OK) {
				throw new ConnectException(as.buildError(status));
			}
		}
	}

	@Override
	protected void connect(InetAddress address, int port) throws IOException {
		connect(address.getHostAddress(), port);
	}

	@Override
	protected void connect(SocketAddress address, int timeout) throws IOException {
		//now java.net.socket has done safe close check so we can ignore it
		//checkCreatedAndNotClosed();
		if (timeout > 0) { //see code comment in create method, we shouldn't overwrite the value.
			as.setConnectTimeout(timeout);
		}
		if (address == null || !(address instanceof InetSocketAddress))
			throw new IllegalArgumentException("unsupported address type");
		InetSocketAddress addr = (InetSocketAddress) address;
		if (addr.isUnresolved()) {
			if (addr.getHostName().startsWith("unix:")) {
				connect(addr.getHostName(), -1);
				return;
			}
			throw new UnknownHostException(addr.getHostName());
		}
		this.port = addr.getPort();
		this.address = addr.getAddress();
		connect(this.address, this.port);
	}

	@Override
	protected void bind(InetAddress host, int port) throws IOException {
		status = as.bind(new StringBuilder(host.getHostAddress()).append(':').append(port).toString());
		if (status != NginxClojureAsynSocket.NGX_HTTP_CLOJURE_SOCKET_OK) {
			throw new BindException(as.buildError(status));
		}
	}

	@Override
	protected void listen(int backlog) throws IOException {
		throw new UnsupportedOperationException("for client socket, listen not supported!");
	}

	@Override
	protected void accept(SocketImpl s) throws IOException {
		throw new UnsupportedOperationException("for client socket, accept not supported!");
	}

	@Override
	protected InputStream getInputStream() throws IOException {
		if (inputStream != null) {
			return inputStream;
		}
        //now java.net.socket has done safe close check so we can ignore it
//		if (as == null) {
//			throw new SocketException("Socket not created!");
//		}
//		as.checkConnected();
		return inputStream = new SocketInputStream(this);
	}

	@Override
	protected OutputStream getOutputStream() throws IOException {
		if (outputStream != null) {
			return outputStream;
		}
		//now java.net.socket has done safe close check so we can ignore it
//		if (as == null) {
//			throw new SocketException("Socket not created!");
//		}
//		as.checkConnected();
		return outputStream = new SocketOutputStream(this);
	}

	@Override
	protected int available() throws IOException {
		return as.available();
	}

	@Override
	protected void close() throws IOException {
//		checkCreatedAndNotClosed();
		if (!isClosed()) {
			if (inputStream != null) {
				inputStream.closed = true;
				inputStream = null;
			}
			if (outputStream != null) {
				outputStream.closed = true;
				outputStream = null;
			}
			if (Thread.currentThread() != NginxClojureRT.NGINX_MAIN_THREAD) {
				if (log.isDebugEnabled()) {
					log.debug(
							String.format(
									"socket#%d: close a coroutine based sockets not in main thread, so we post a event to main thread to do so",
									as.s), new Exception(
									"DEBUG USAGE--closeByPostEvent"));
				}
				NginxClojureRT.postCloseSocketEvent(this);
			}else {
				as.close();
				as = null;
			}
			yieldFlag = 0;
		}
	}
	
	public void closeByPostEvent() {
		if (as != null) {
			if (log.isDebugEnabled()) {
				log.debug("socket#%d: closed by post event", as.s);
			}
			as.close();
			as = null;
		}
	}

	@Override
	protected void shutdownInput() throws IOException {
		//make the same behavior with Java build-in socket implementation
		as.shutdown(NginxClojureAsynSocket.NGX_HTTP_CLOJURE_SOCKET_SHUTDOWN_READ);
	}
	
	@Override
	protected void shutdownOutput() throws IOException {
        //make the same behavior with Java build-in socket implementation
		as.shutdown(NginxClojureAsynSocket.NGX_HTTP_CLOJURE_SOCKET_SHUTDOWN_WRITE);
	}
	
	@Override
	protected void sendUrgentData(int data) throws IOException {
		throw new UnsupportedOperationException("sendUrgentData not supported!");
	}


	@Override
	public void onConnect(NginxClojureAsynSocket s, long sc) {
		if (log.isDebugEnabled()) {
			log.debug("socket#%d: on connect status=%d", as.s, sc);
		}
		status = sc;
		NginxClojureSocketImpl ns = (NginxClojureSocketImpl)s.getHandler();
		if (ns.yieldFlag == NginxClojureSocketImpl.YIELD_CONNECT){
			log.debug("socket#%d: find suspend on YIELD_CONNECT, we'll resume it", as.s);
			ns.yieldFlag = 0;
			ns.coroutine.resume();
		}
	}


	@Override
	public void onRead(NginxClojureAsynSocket s, long sc) {
		if (log.isDebugEnabled()) {
			log.debug("socket#%d: on read status=%d", as.s, sc);
		}
		status = sc;
		NginxClojureSocketImpl ns = (NginxClojureSocketImpl)s.getHandler();
		if (ns.yieldFlag == NginxClojureSocketImpl.YIELD_READ){
			log.debug("socket#%d: find suspend on YIELD_READ, we'll resume it", as.s);
			ns.yieldFlag = 0;
			ns.coroutine.resume();
		}		
	}


	@Override
	public void onWrite(NginxClojureAsynSocket s, long sc) {
		if (log.isDebugEnabled()) {
			log.debug("socket#%d: on write status=%d", as.s, sc);
		}
		status = sc;
		NginxClojureSocketImpl ns = (NginxClojureSocketImpl)s.getHandler();
		if (ns.yieldFlag == NginxClojureSocketImpl.YIELD_WRITE){
			log.debug("socket#%d: find suspend on YIELD_WRITE, we'll resume it", as.s);
			ns.yieldFlag = 0;
			ns.coroutine.resume();
		}
	}


	@Override
	public void onRelease(NginxClojureAsynSocket s, long sc) {
		if (log.isDebugEnabled()) {
			log.debug("socket#%d: on release status=%d", as.s, sc);
		}
		status = sc;
		NginxClojureSocketImpl ns = (NginxClojureSocketImpl)s.getHandler();
		if (ns.coroutine != null && ns.coroutine.getState() == State.SUSPENDED){
			if (log.isDebugEnabled()) {
				log.warn("socket#%d: onRelease : coroutine is not finished, but we receive release event!", as.s);
				log.debug(String.format("socket#%d: onRelease : coroutine is not finished, but we receive release event!", as.s), new Exception("DEBUG USAGE--onRelease Warning"));
			}
		}
	}
	

	protected static class SocketInputStream extends InputStream {

		NginxClojureSocketImpl s;
		//in coroutine thread safe is not necessary, so we just make it work
		byte[] oba = new byte[1];
		boolean closed;
		boolean eof;
		
		public SocketInputStream(NginxClojureSocketImpl s) {
			this.s = s;
		}
		
		@Override
		public int available() throws IOException {
			checkClosed();
			return s.available();
		}
		
		@Override
		public int read() throws IOException {
			if ( read(oba, 0, 1) == 1) {
				return oba[0];
			}
			return -1;
		}
		
		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			checkClosed();
			if (len == 0) {
				return 0;
			}
			if (eof) {
				return -1;
			}
			if (off + len > b.length) {
				throw new IndexOutOfBoundsException("buffer space is too small, off + len > b.length");
			}
			if (log.isDebugEnabled()) {
				log.debug("socket#%d: enter read offset %d len %d", s.as.s, off, len);
			}
			long rc = 0;
			long c = 0;
			do {
				rc = s.as.read(b, off + c, len - c);
				if (log.isDebugEnabled()) {
					log.debug("socket#%d: read offset %d len %d return %d, total %d", s.as.s, off+c, len-c, rc, rc > 0 ? rc + c : c);
				}
				
				if (rc == 0) {
					eof = true;
					return c == 0 ? -1 : (int)c;
				}else if (rc == NginxClojureAsynSocket.NGX_HTTP_CLOJURE_SOCKET_ERR_AGAIN) {
					if (c > 0) {
						return (int)c;
					}
					if (s.status == NginxClojureAsynSocket.NGX_HTTP_CLOJURE_SOCKET_ERR_READ_TIMEOUT) {
						throw new SocketTimeoutException(s.as.buildError(s.status));
					}
					s.yieldFlag = YIELD_READ;
					if (log.isDebugEnabled()) {
						if (log.isTraceEnabled()) {
							log.trace(String.format("socket#%d: yield read", s.as.s), new Exception("DEBUG USAGE--yield read"));
						}else {
							log.debug(String.format("socket#%d: yield read", s.as.s));
						}
					}
					s.attachCoroutine();
					Coroutine.yield();
					if (s.status != NginxClojureAsynSocket.NGX_HTTP_CLOJURE_SOCKET_OK) {
						throw new SocketException(s.as.buildError(s.status));
					}
				}else if (rc < 0) {
					if (c > 0) {
						log.warn("socket#%d: meet error %d, but we have read some data (len=%d), just return it", s.as.s, rc, c);
						break;
					}
					throw new SocketException(s.as.buildError(rc));
				}else {
					c += rc;
				}
				
			}while( (rc > 0 || rc == NginxClojureAsynSocket.NGX_HTTP_CLOJURE_SOCKET_ERR_AGAIN) && c < len);

			return (int)c;
		}
		
		void checkClosed() throws IOException {
			if (closed) {
				throw new IOException("SocketOutputStream closed already!");
			}
		}
		
		@Override
		public void close() throws IOException {
			if (log.isDebugEnabled()) {
				log.debug("socket#%d: close inputstream", s.as.s);
			}
//			checkClosed();
			if (closed) {
				return;
			}
			closed = true;
			Socket fs = s.fetchSocket();
			if (fs != null) {
				//although I think it's wrong  but it becomes de facto standard because of 
				//Java build-in implementation. so we have no choice :-(.
				fs.close();
			}else {
				s.close();
			}
			s = null;
		}
	}
	
	protected static class SocketOutputStream extends OutputStream {
		
		NginxClojureSocketImpl s;
		byte[] oba = new byte[1];
		boolean closed;
		
		public SocketOutputStream(NginxClojureSocketImpl s) {
			this.s = s;
		}
		
		@Override
		public void write(int b) throws IOException {
			oba[0] = (byte)(b & 0xff);
			write(oba, 0, 1);
		}
		
		@Override
		public void write(byte[] b, int off, int len) throws IOException {
			checkClosed();
			if (b == null) {
				throw new NullPointerException("byte[] can not be null");
			}
			if (off + len > b.length) {
				throw new IndexOutOfBoundsException("buffer space is too small, off + len > b.length");
			}
			if (log.isDebugEnabled()) {
				log.debug("socket#%d: enter write offset %d len %d", s.as.s, off, len);
			}
			long rc = 0;
			long c = 0;
			do {
				rc = s.as.write(b, off + c, len - c);
				if (log.isDebugEnabled()) {
					log.debug("socket#%d: write offset %d len %d return %d, total %d", s.as.s, off+c, len-c, rc, rc > 0 ? rc + c : c);
				}
				if (rc == 0) {
					return;
				}else if (rc == NginxClojureAsynSocket.NGX_HTTP_CLOJURE_SOCKET_ERR_AGAIN) {
					if (s.status == NginxClojureAsynSocket.NGX_HTTP_CLOJURE_SOCKET_ERR_WRITE_TIMEOUT) {
						throw new SocketTimeoutException(s.as.buildError(s.status));
					}
					s.yieldFlag = YIELD_WRITE;
					if (log.isDebugEnabled()) {
						if (log.isTraceEnabled()) {
							log.trace(String.format("socket#%d: yield write", s.as.s), new Exception("DEBUG USAGE--yield write"));
						}else {
							log.debug(String.format("socket#%d: yield write", s.as.s));
						}
						
					}
					s.attachCoroutine();
					Coroutine.yield();
					if (s.status != NginxClojureAsynSocket.NGX_HTTP_CLOJURE_SOCKET_OK) {
						throw new SocketException(s.as.buildError(s.status));
					}
				}else if (rc < 0) {
					throw new SocketException(s.as.buildError(rc));
				}else {
					c += rc;
				}
				
			}while((rc > 0 || rc == NginxClojureAsynSocket.NGX_HTTP_CLOJURE_SOCKET_ERR_AGAIN) && c < len);
		}
		
		final void checkClosed() throws IOException {
			if (closed) {
				throw new IOException("SocketOutputStream closed already!");
			}
		}
		
		@Override
		public void close() throws IOException {
			if (log.isDebugEnabled()) {
				log.debug("socket#%d: close outputstream", s.as.s);
			}
//			checkClosed();
			if (closed) {
				return;
			}
			
			closed = true;
			Socket fs = s.fetchSocket();
			if (fs != null) {
				//although I think it's wrong  but it becomes de facto standard because of 
				//Java build-in implementation. so we have no choice :-(.
				fs.close();
			}else {
				s.close();
			}
			s = null;
		}
	}
	
	public NginxClojureAsynSocket asynSocket() {
		return as;
	}
}
