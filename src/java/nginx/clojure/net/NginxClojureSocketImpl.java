/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketImpl;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import nginx.clojure.Coroutine;
import nginx.clojure.Coroutine.State;
import nginx.clojure.NginxClojureRT;

//TODO: check java.net.Socket and remove some safe check code which have been done in java.net.Socket
public class NginxClojureSocketImpl extends SocketImpl implements NginxClojureSocketHandler {
	
	final static int YIELD_CONNECT = 1;
	final static int YIELD_READ = 2;
	final static int YIELD_WRITE = 3;
	
	protected NginxClojureAsynSocket as;
	protected Coroutine coroutine;
	protected int yieldFlag = 0;
	protected SocketInputStream inputStream;
	protected SocketOutputStream outputStream;

	public NginxClojureSocketImpl() {
		coroutine = Coroutine.getActiveCoroutine();
	}
	

	@Override
	public void setOption(int optID, Object v) throws SocketException {
		checkCreatedAndNotClosed();
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
				throw new SocketException(
						"wrong argument for SO_RCVBUF, it must be Integer! But it is "
								+ ((v == null) ? "null" : v.getClass()));
			}
			as.setReceiveBufferSize(((Integer) v).intValue());
			return;
		case SO_LINGER:
		case IP_TOS:
		case SO_BINDADDR:
		case TCP_NODELAY:
        case SO_SNDBUF:
        case SO_KEEPALIVE:
        case SO_OOBINLINE:
        case SO_REUSEADDR:
        	NginxClojureRT.getLog().warn("not supported socket options: %d, just ignored" , optID);
        default:
            throw new SocketException("unknown TCP option: " + optID);
        }
	}


	public void checkCreatedAndNotClosed() throws SocketException {
		if (as == null) {
			throw new SocketException("Socket not created!");
		}
		if (as.isClosed()) {
			throw new SocketException("Socket Closed");
		}
	}

	@Override
	public Object getOption(int optID) throws SocketException {
		checkCreatedAndNotClosed();
		switch (optID) {
		case SO_TIMEOUT:
           return Integer.valueOf((int)as.getReadTimeout());
		case SO_RCVBUF:
			return Integer.valueOf((int)as.getReceiveBufferSize());
		}
		return null;
	}

	@Override
	protected void create(boolean stream) throws IOException {
		if (!stream) {
			throw new UnsupportedOperationException("stream = false not supported!");
		}
		as = new NginxClojureAsynSocket(this);
	}

	@Override
	protected void connect(String host, int port) throws IOException {
		checkCreatedAndNotClosed();
		as.connect(new StringBuilder(host).append(':').append(port).toString());
		if (!as.isConnected()) {
			yieldFlag = YIELD_CONNECT;
			Coroutine.yield();
		}
	}

	@Override
	protected void connect(InetAddress address, int port) throws IOException {
		connect(address.getHostAddress(), port);
	}

	@Override
	protected void connect(SocketAddress address, int timeout) throws IOException {
		checkCreatedAndNotClosed();
		as.setConnectTimeout(timeout);
		if (address == null || !(address instanceof InetSocketAddress))
			throw new IllegalArgumentException("unsupported address type");
		InetSocketAddress addr = (InetSocketAddress) address;
		if (addr.isUnresolved())
			throw new UnknownHostException(addr.getHostName());
		this.port = addr.getPort();
		this.address = addr.getAddress();
		connect(this.address, this.port);
	}

	@Override
	protected void bind(InetAddress host, int port) throws IOException {
		throw new UnsupportedOperationException("bind not supported!");
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
		if (as == null) {
			throw new SocketException("Socket not created!");
		}
		as.checkConnected();
		return inputStream = new SocketInputStream(this);
	}

	@Override
	protected OutputStream getOutputStream() throws IOException {
		if (outputStream != null) {
			return outputStream;
		}
		if (as == null) {
			throw new SocketException("Socket not created!");
		}
		as.checkConnected();
		return outputStream = new SocketOutputStream(this);
	}

	@Override
	protected int available() throws IOException {
		return 0;
	}

	@Override
	protected void close() throws IOException {
		checkCreatedAndNotClosed();
		as.close();
		yieldFlag = 0;
	}

	@Override
	protected void shutdownInput() throws IOException {
		inputStream.close();
	}
	
	@Override
	protected void shutdownOutput() throws IOException {
		outputStream.close();
	}
	
	@Override
	protected void sendUrgentData(int data) throws IOException {
		throw new UnsupportedOperationException("sendUrgentData not supported!");
	}


	@Override
	public void onConnect(NginxClojureAsynSocket s, long sc) {
		NginxClojureSocketImpl ns = (NginxClojureSocketImpl)s.getHandler();
		if (ns.yieldFlag == NginxClojureSocketImpl.YIELD_CONNECT){
			ns.yieldFlag = 0;
			ns.coroutine.resume();
		}
	}


	@Override
	public void onRead(NginxClojureAsynSocket s, long sc) {
		NginxClojureSocketImpl ns = (NginxClojureSocketImpl)s.getHandler();
		if (ns.yieldFlag == NginxClojureSocketImpl.YIELD_READ){
			ns.yieldFlag = 0;
			ns.coroutine.resume();
		}		
	}


	@Override
	public void onWrite(NginxClojureAsynSocket s, long sc) {
		NginxClojureSocketImpl ns = (NginxClojureSocketImpl)s.getHandler();
		if (ns.yieldFlag == NginxClojureSocketImpl.YIELD_WRITE){
			ns.yieldFlag = 0;
			ns.coroutine.resume();
		}
	}


	@Override
	public void onRelease(NginxClojureAsynSocket s, long sc) {
		NginxClojureSocketImpl ns = (NginxClojureSocketImpl)s.getHandler();
		if (ns.coroutine.getState() != State.SUSPENDED){
			NginxClojureRT.getLog().warn("onRelease : coroutine is not finished, but we receive release event!");
		}
	}
	

	protected static class SocketInputStream extends InputStream {

		NginxClojureSocketImpl s;
		byte[] oba = new byte[1];
		boolean closed;
		
		public SocketInputStream(NginxClojureSocketImpl s) {
			this.s = s;
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
			if (off + len > b.length) {
				throw new IndexOutOfBoundsException("buffer space is too small, off + len > b.length");
			}
			
			long rc = 0;
			long c = 0;
			do {
				rc = s.as.read(b, off + c, len - c);
				if (rc == 0) {
					return -1;
				}else if (rc == NginxClojureAsynSocket.NGX_HTTP_CLOJURE_SOCKET_ERR_AGAIN) {
					s.yieldFlag = YIELD_READ;
					Coroutine.yield();
				}else if (rc == NginxClojureAsynSocket.NGX_HTTP_CLOJURE_SOCKET_ERR_READ_TIMEOUT) {
					throw new SocketTimeoutException("socket read timeout :" + rc);
				}else if (rc < 0) {
					throw new SocketException("socket read error :" + rc);
				}else {
					c += rc;
				}
				
			}while(rc > 0 && c < len);

			return (int)c;
		}
		
		void checkClosed() throws IOException {
			if (closed) {
				throw new IOException("SocketOutputStream closed already!");
			}
		}
		
		@Override
		public void close() throws IOException {
			checkClosed();
			closed = true;
			s.as.shutdown(NginxClojureAsynSocket.NGX_HTTP_CLOJURE_SOCKET_SHUTDOWN_SOFT_READ);
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
			
			long rc = 0;
			long c = 0;
			do {
				rc = s.as.write(b, off + c, len - c);
				if (rc == 0) {
					return;
				}else if (rc == NginxClojureAsynSocket.NGX_HTTP_CLOJURE_SOCKET_ERR_AGAIN) {
					s.yieldFlag = YIELD_WRITE;
					Coroutine.yield();
				}else if (rc == NginxClojureAsynSocket.NGX_HTTP_CLOJURE_SOCKET_ERR_READ_TIMEOUT) {
					throw new SocketTimeoutException("socket read timeout :" + rc);
				}else if (rc < 0) {
					throw new SocketException("socket read error :" + rc);
				}else {
					c += rc;
				}
				
			}while(rc > 0 && c < len);
		}
		
		final void checkClosed() throws IOException {
			if (closed) {
				throw new IOException("SocketOutputStream closed already!");
			}
		}
		
		@Override
		public void close() throws IOException {
			checkClosed();
			closed = true;
			s.as.shutdown(NginxClojureAsynSocket.NGX_HTTP_CLOJURE_SOCKET_SHUTDOWN_SOFT_WRITE);
		}
	}
}
