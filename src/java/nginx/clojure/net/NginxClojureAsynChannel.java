/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.net;

import java.io.Closeable;
import java.io.IOException;
import java.net.SocketException;
import java.nio.ByteBuffer;

import nginx.clojure.ChannelListener;
import nginx.clojure.HackUtils;
import nginx.clojure.NginxClojureRT;
import nginx.clojure.logger.TinyLogService;
import nginx.clojure.logger.TinyLogService.MsgType;

public class NginxClojureAsynChannel implements NginxClojureSocketHandler, Closeable {
	
	protected ChannelListener<NginxClojureAsynChannel> listener;
	protected BufferChain connectFakeChain;
	protected BufferChain writeBusyChain;
	protected BufferChain freeChain;
	protected BufferChain readBusyChain;
	protected int pagesize = 1024 * 4;
	protected NginxClojureAsynSocket as;
	protected static TinyLogService log;
	
	public static class BufferChain {
		public ByteBuffer buffer;
		public BufferChain next;
		@SuppressWarnings({"rawtypes"})
		public CompletionListener listener;
		public Object attachement;
	}
	
	public static interface CompletionListener<T> {
		/**
		 * 
		 * @param status
		 *        read/written number of bytes , always > 0.
		 * @param attachment
		 *        the attachment object which was as the last read/write method input argument before CompletionListener argument.
		 *        e.g. <code>write(buf, off, size, listener, attachement)</code>
		 */
		public void onDone(long status, T attachment) throws IOException;
		
		/**
		 * 
		 * @param code
		 *        if code = 0, eof for read/write
		 *        if code < 0, code is error code which range from 
		 *        NginxClojureAsynSocket.NGX_HTTP_CLOJURE_SOCKET_ERR to NGX_HTTP_CLOJURE_SOCKET_ERR_OUTOFMEMORY
		 *        We can get the error string by invoking buildError(code)
		 * @param attachment
		 *        the attachment object which was as the last read/write method input argument before CompletionListener argument.
		 *        e.g. <code>write(buf, off, size, listener, attachement)</code>
		 */
		public void onError(long code, T attachment) throws IOException;
	}
	
	public NginxClojureAsynChannel() {
		as = new NginxClojureAsynSocket(this);
		if (log == null) {
			log = new TinyLogService(TinyLogService.getSystemPropertyOrDefaultLevel(NginxClojureSocketImpl.NGINX_CLOJURE_LOG_SOCKET_LEVEL, MsgType.info), System.err, System.err);
		}
	}
	
	public NginxClojureAsynSocket getAsynSocket() {
		return as;
	}
	
	public String buildError(long sc) {
		if (sc == 0) {
			return "end of stream or connection reset!";
		}
		return as.buildError(sc);
	}
	
	protected <T> BufferChain fetchFreeChainAndCopyBuf(ByteBuffer buf, T attachement, CompletionListener<T> listener) {
		BufferChain result;
		BufferChain cur;
		int size = buf.remaining();
		result = cur = freeChain;
		while (size > 0) {
			if (freeChain != null) {
				HackUtils.putBuffer(cur.buffer, buf);
				cur.buffer.flip();
				freeChain = freeChain.next;
				size -= freeChain.buffer.remaining();
				if (freeChain != null && size > 0) {
					cur = freeChain;
				}
			}else {
				ByteBuffer tmp = ByteBuffer.allocateDirect(pagesize);
				size -= pagesize;
				HackUtils.putBuffer(tmp, buf);
				tmp.flip();
				if (result == null) {
					result = cur = new BufferChain();
				}else {
					cur.next = new BufferChain();
					cur = cur.next;
				}
				cur.buffer = tmp;
			}
		}
		cur.attachement = attachement;
		cur.listener = listener;
		cur.next = null;
		return result;
	}
	
	protected void collectFreeChain() {
		
	}
	
	public void check()  {
		if (Thread.currentThread() != NginxClojureRT.NGINX_MAIN_THREAD) {
			throw new IllegalAccessError("NginxClojureAsynChannel can only be operated in main thread");
		}
		if (as == null) {
			NginxClojureRT.UNSAFE.throwException(new SocketException("Socket not created!"));
		}
		if (as.isClosed()) {
			NginxClojureRT.UNSAFE.throwException(new SocketException("Socket Closed"));
		}
	}
	
	public Object getOption(int optID)  {
		check();
		return NginxClojureSocketImpl.getOption(as, optID);
	}
	
	/**
	 * Set socket options, e.g. setOption(SO_TIMEOUT, 1000);
	 * @param optID @see java.net.SocketOptions
	 * @param v value of this option
	 */
	public void setOption(int optID, Object v)  {
		check();
		NginxClojureSocketImpl.setOption(as, optID, v);
	}
	
	/**
	 * if timeout is negative, it will be ignored. if timeout is 0, this means no timeout.
	 */
	public void setConnectTimeout(long timeout) {
		check();
		as.setTimeout(timeout, -1, -1);
	}
	
	/**
	 * if timeout is negative, it will be ignored. if timeout is 0, this means no timeout.
	 */
	public void setReadTimeout(long timeout) {
		check();
		as.setTimeout(-1, timeout, -1);
	}
	
	/**
	 * if timeout is negative, it will be ignored. if timeout is 0, this means no timeout.
	 */
	public void setWriteTimeout(long timeout) {
		check();
		as.setTimeout(-1, -1, timeout);
	}
	
	/**
	 * if timeout is negative, it will be ignored. if timeout is 0, this means no timeout.
	 */
	public void setTimeout(long connectTimeout, long readTimeout, long writeTimeout) {
		as.setTimeout(connectTimeout, readTimeout, writeTimeout);
	}
	
	
	/**
	 * connect to remote server
	 * @param url 
	 *        e.g. "192.168.2.34:80" , "www.bing.com:80", or unix domain socket "unix:/var/mytest/server.sock"
	 * @param listener 
	 *        completion listener
	 * @param attachement 
	 *        when action is done or meets error this attachment will be passed to the method of the completion listener
	 */
	public <T> void connect(String url, T attachement, CompletionListener<T> listener) {
		check();
		if (connectFakeChain == null && listener != null) {
			connectFakeChain = new BufferChain();
			connectFakeChain.attachement = attachement;
			connectFakeChain.listener = listener;
		}
		as.connect(url);
	}
	
	public <T> void write(byte[] buf, long off, long size, T attachement, CompletionListener<T> listener) {
		write(ByteBuffer.wrap(buf, (int)off, (int)size), attachement, listener);
	}
	
	public <T> void write(ByteBuffer buf, T attachement, CompletionListener<T> listener) {
		check();
		BufferChain chain = fetchFreeChainAndCopyBuf(buf, attachement, listener);
		if (writeBusyChain != null) {
			BufferChain tail = writeBusyChain;
			while (tail.next != null) {
				tail = tail.next;
			}
			tail.next = chain;
		}else {
			writeBusyChain = chain;
		}
		
		onWrite(as, 0);
	}
	
	public <T> void read(byte[] buf, long off, long size, T attachement, CompletionListener<T> listener) {
		read(ByteBuffer.wrap(buf, (int)off, (int)size), attachement, listener);
	}
	
    public <T> void read(ByteBuffer buf, T attachement, CompletionListener<T> listener) {
    	check();
		BufferChain chain = new BufferChain();
		chain.attachement = attachement;
		chain.buffer = buf;
		chain.listener = listener;
		chain.next = null;
		if (readBusyChain != null) {
			readBusyChain.next = chain;
		}else {
			readBusyChain = chain;
		}
		onRead(as, 0);
	}
	
	public void setListener(ChannelListener<NginxClojureAsynChannel> listener) {
		this.listener = listener;
	}
	
	public ChannelListener<NginxClojureAsynChannel> getListener() {
		return listener;
	}

	@Override
	public void onConnect(NginxClojureAsynSocket s, long sc) throws IOException {
		if (listener != null) {
			listener.onConnect(sc, this);
		}
		callOnEventNoThrows(connectFakeChain, sc);
		connectFakeChain = null;
	}
	
	@SuppressWarnings({"unchecked"})
	protected void callOnEventNoThrows(BufferChain chain, long status) {
		if (chain.listener == null) {
			return;
		}
		try {
			if (status >= 0) {
				chain.listener.onDone(status, chain.attachement);
			}else {
				chain.listener.onError(status, chain.attachement);
			}
		}catch(Throwable e) {
			log.warn("unhandled errors", e);
		}
	}
	
	protected void onIO(NginxClojureAsynSocket s, long sc, boolean isRead) {

		if (log.isDebugEnabled()) {
			log.debug("asyn-channel#%d: on %s status=%d", as.s, (isRead ? "read" : "write") , sc);
		}
		BufferChain chain = isRead ? readBusyChain : writeBusyChain;
		if (sc != NginxClojureAsynSocket.NGX_HTTP_CLOJURE_SOCKET_OK) {
			while (chain != null) {
				callOnEventNoThrows(chain, sc);
				if (!isRead) {
					BufferChain head = freeChain;
					freeChain = chain;
					freeChain.next = head;
					freeChain.buffer.clear();
					freeChain.attachement = null;
					freeChain.listener = null;
				}
				chain = chain.next;
			}
			readBusyChain = writeBusyChain = null;
			return;
		}
		
		while (chain != null) {
			ByteBuffer buffer = chain.buffer;
			long rc = isRead ? s.read(buffer) : s.write(buffer);
			int c = buffer.position();
			if (log.isDebugEnabled()) {
				if (rc > 0) {
					log.debug("asyn-channel#%d: %s offset %d len %d return %d, total %d", as.s, (isRead ? "read" : "write"),
							buffer.position() - rc, buffer.limit(), rc, buffer.position());
				}
				
			}
			if (rc > 0) {
				if (!buffer.hasRemaining()) {
					if (isRead) {
						readBusyChain = chain.next;
					}else {
						writeBusyChain = chain.next;
					}
					callOnEventNoThrows(chain, c);
					chain = isRead ? readBusyChain : writeBusyChain;
				}
			} else if (rc <= 0) {
				if (rc == NginxClojureAsynSocket.NGX_HTTP_CLOJURE_SOCKET_ERR_AGAIN) {
					return;
				}
				while (chain != null) {
					if (isRead) {
						readBusyChain = chain.next;
					}else {
						writeBusyChain = chain.next;
					}
					if (c > 0) {
						callOnEventNoThrows(chain, c);
						c = 0;
					}else {
						callOnEventNoThrows(chain, rc);
					}
					
					chain = isRead ? readBusyChain : writeBusyChain;
				}
				return;
			}
		}
	
	}

	@Override
	public void onRead(NginxClojureAsynSocket s, long sc) {
		onIO(s, sc, true);
	}

	@Override
	public void onWrite(NginxClojureAsynSocket s, long sc) {
		onIO(s, sc, false);
	}

	@Override
	public void onRelease(NginxClojureAsynSocket s, long sc) throws IOException{
		if (log.isDebugEnabled()) {
			log.debug("asyn-channel#%d: on release status=%d", as.s, sc);
		}
		if (listener != null) {
			listener.onClose(this);
		}
	}
	
	public boolean isClosed() {
		return as == null ? true : as.isClosed();
	}
	
	public void close() {
		this.as.close();
	}
	
	@SuppressWarnings({"unchecked"})
	public <T> T getContext() {
		return (T)as.getContext();
	}

	public <T> void setContext(T context) {
		as.setContext(context);
	}
}
