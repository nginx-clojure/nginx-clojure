/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 *For reuse some classes from tomcat8 we have to use this package
 */
package org.apache.coyote.http11.upgrade;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;

import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.WebConnection;

import nginx.clojure.ChannelListener;
import nginx.clojure.HackUtils;
import nginx.clojure.MiniConstants;
import nginx.clojure.NginxClojureRT;
import nginx.clojure.NginxHttpServerChannel;
import nginx.clojure.net.NginxClojureAsynSocket;

import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.net.SocketWrapper;

public class NginxClojureWebConnectionImp implements WebConnection, ChannelListener<NginxClojureWebConnectionImp> {
	
	protected NginxHttpServerChannel channel;
	protected WebConnectionInputStream inputStream;
	protected WebConnectionOutputStream outputStream;
	
	protected static final org.apache.juli.logging.Log log =
	        org.apache.juli.logging.LogFactory.getLog( NginxClojureWebConnectionImp.class );
	
	
	/**
	 * We only operate buffers in main thread so thread-unsafe LinkedList is enough.
	 */
	protected static final LinkedList<ByteBuffer> buffers = new LinkedList<ByteBuffer>();

	@Override
	public void close() throws Exception {
		channel.close();
	}

	public NginxClojureWebConnectionImp(NginxHttpServerChannel channel) {
		this.channel = channel;
		channel.addListener(this, this);
	}
	
	@Override
	public ServletInputStream getInputStream() throws IOException {
		return inputStream == null ? inputStream = new WebConnectionInputStream(channel) : inputStream;
	}

	@Override
	public ServletOutputStream getOutputStream() throws IOException {
		return outputStream == null ? outputStream = new WebConnectionOutputStream(channel) : outputStream;
	}
	
	protected static class WebConnectionInputStream extends AbstractServletInputStream {

		protected ByteBuffer buffer;
		protected NginxHttpServerChannel channel;
		protected boolean eof = false;
		
		public WebConnectionInputStream(NginxHttpServerChannel channel) {
			this.channel = channel;
			buffer = buffers.poll();
			if (buffer == null) {
				buffer = ByteBuffer.allocate(MiniConstants.NGINX_CLOJURE_CORE_CLIENT_HEADER_MAX_SIZE);
				buffer.position(0);
				buffer.limit(0);
			}
		}
		
		protected void onClose() {
			if (eof) {
				return;
			}
			eof = true;
			buffer.position(0);
			buffer.limit(0);
			buffers.offer(buffer);
			buffer = null;
		}

		@Override
		protected boolean doIsReady() throws IOException {

			if (buffer.remaining() > 0) {
	            return true;
	        }

	        buffer.clear();
	        channel.read(buffer);
	        boolean isReady = buffer.position() > 0;
	        buffer.flip();
	        return isReady;
		}
		
		@Override
		protected int doRead(boolean block, byte[] b, int off, int len)
				throws IOException {

	        int remaining = buffer.remaining();

	        // Is there enough data in the read buffer to satisfy this request?
	        if (remaining >= len) {
	        	buffer.get(b, off, len);
	            return len;
	        }

	        // Copy what data there is in the read buffer to the byte array
	        int leftToWrite = len;
	        int newOffset = off;
	        if (remaining > 0) {
	        	buffer.get(b, off, remaining);
	            leftToWrite -= remaining;
	            newOffset += remaining;
	        }

	        // Fill the read buffer as best we can
	        buffer.clear();
	        int nRead = (int) channel.read(buffer);
	        // Full as much of the remaining byte array as possible with the data
	        // that was just read
	        if (nRead > 0) {
	        	buffer.flip();
	            if (nRead > leftToWrite) {
	            	buffer.get(b, newOffset, leftToWrite);
	                leftToWrite = 0;
	            } else {
	            	buffer.get(b, newOffset, nRead);
	                leftToWrite -= nRead;
	            }
	        } else if (nRead == 0) {
	        	buffer.flip();
	        } else if (nRead == -1) {
	            // TODO i18n
	            throw new EOFException();
	        }

	        return len - leftToWrite;
		}

		@Override
		protected void doClose() throws IOException {
			eof = true;
			channel.close();
		}
	}
	
	public static class SimpleBufferChain {
		public ByteBuffer buffer;
		public SimpleBufferChain next;
	}

	
	public static class WebConnectionOutputStream extends AbstractServletOutputStream {
		
//		protected ByteBuffer buffer;
		protected NginxHttpServerChannel channel;
		protected WriteListener listener;
		protected boolean eof = false;
		protected final static int maxWrite = MiniConstants.NGINX_CLOJURE_CORE_CLIENT_HEADER_MAX_SIZE;
		protected SimpleBufferChain freeChain;
		protected SimpleBufferChain busyChain;
		
		private static SocketWrapper<NginxHttpServerChannel> fackSocketWrapper = new SocketWrapper<NginxHttpServerChannel>(null) {
			@Override
			public void addDispatch(org.apache.tomcat.util.net.DispatchType dispatchType) {};
		};

		public WebConnectionOutputStream(NginxHttpServerChannel channel) {
			super(fackSocketWrapper, maxWrite);
			this.channel = channel;
//			buffer = ByteBuffer.allocate(maxWrite);
		}
		
		protected  SimpleBufferChain fetchFreeChainAndCopyBuf(ByteBuffer buf) {
			SimpleBufferChain result;
			SimpleBufferChain cur;
			int size = buf.remaining();
			result = cur = freeChain;
			
			while (size > 0) {
				if (freeChain != null) {
					size -= HackUtils.putBuffer(cur.buffer, buf);
					cur.buffer.flip();
					freeChain = freeChain.next;
					if (freeChain != null && size > 0) {
						cur = freeChain;
					}
				}else {
					ByteBuffer tmp = ByteBuffer.allocate(maxWrite);
					size -= maxWrite;
					HackUtils.putBuffer(tmp, buf);
					tmp.flip();
					if (result == null) {
						result = cur = new SimpleBufferChain();
					}else {
						cur.next = new SimpleBufferChain();
						cur = cur.next;
					}
					cur.buffer = tmp;
				}
			}

			cur.next = null;
			return result;
		}

		@Override
		protected int doWrite(boolean block, byte[] b, int off, int len)
				throws IOException {

			if (Thread.currentThread() != NginxClojureRT.NGINX_MAIN_THREAD) {
				throw new IOException("doWrite can must be invoked at main thread");
			}
			int olen = len;
			if (busyChain == null) {
				while (len > 0) {
					int n = (int)channel.write(b, off, len);
					if (n == 0) {
						break;
					}
					off += n;
					len -= n;
				}
			}
			
			if (len > 0) {
				if (busyChain == null) {
					busyChain = fetchFreeChainAndCopyBuf(ByteBuffer.wrap(b, off, len));
				}else {
					SimpleBufferChain chain = busyChain;
					while (chain.next != null) {
						chain = chain.next;
					}
					ByteBuffer rem = ByteBuffer.wrap(b, off, len);
					ByteBuffer cbuf = chain.buffer;
					if (cbuf.limit() != cbuf.capacity()) {
						cbuf.mark();
						cbuf.position(cbuf.limit());
						cbuf.limit(cbuf.capacity());
						HackUtils.putBuffer(cbuf, rem);
						cbuf.limit(cbuf.position());
						cbuf.reset();
					}
					
					if (rem.hasRemaining()) {
						chain.next = fetchFreeChainAndCopyBuf(rem);
					}
				}
				onInnerWrite();
			}

	        return olen;
	    
		}
		
		protected void onInnerWrite() throws IOException {
			
			while (busyChain != null) {
				int rc = (int) channel.write(busyChain.buffer);
				if (rc == 0) {
					return;
				}
				if (!busyChain.buffer.hasRemaining()) {
					busyChain.buffer.clear();
					if (freeChain == null) {
						freeChain = busyChain;
						busyChain = busyChain.next;
						freeChain.next = null;
					}else {
						SimpleBufferChain ftail = freeChain;
						while (ftail.next != null) {
							ftail = ftail.next;
						}
						ftail.next = busyChain;
						busyChain = busyChain.next;
						ftail.next.next = null;
					}
				}
			}
		}

		@Override
		protected void doClose() throws IOException {
			eof = true;
			channel.close();
		}

		@Override
		protected void doFlush() throws IOException {
			onInnerWrite();
		}
	}


	@Override
	public void onClose(NginxClojureWebConnectionImp data) throws IOException {
		try {
			inputStream.doClose();
		}catch(Throwable e) {
			log.error("on close error", e);
		}
		
		try {
			outputStream.doClose();
		}catch(Throwable e) {
			log.error("on close error", e);
		}
	}

	@Override
	public void onConnect(long status, NginxClojureWebConnectionImp data) throws IOException {
		try{
			outputStream.onWritePossible();
		}catch(Throwable e) {
			ExceptionUtils.handleThrowable(e);
			outputStream.onError(e);
		}
	}

	@Override
	public void onRead(final long status, NginxClojureWebConnectionImp data) throws IOException {

		if (NginxClojureRT.log.isDebugEnabled()) {
			NginxClojureRT.log.debug(
					"NginxClojureWebConnectionImp onRead status=%d", status);
		}

		if (status == NginxClojureAsynSocket.NGX_HTTP_CLOJURE_SOCKET_OK) {
			try {
				inputStream.onDataAvailable();
			}catch(Throwable e) {
				ExceptionUtils.handleThrowable(e);
				inputStream.onError(e);
			}
		} else {
			inputStream.onError(new IOException(NginxClojureAsynSocket
					.errorCodeToString(status)));
		}

	}

	@Override
	public void onWrite(final long status, NginxClojureWebConnectionImp data) throws IOException {

		if (NginxClojureRT.log.isDebugEnabled()) {
			NginxClojureRT.log.debug("NginxClojureWebConnectionImp onWrite status=%d", status);
		}		
		
		if (status == NginxClojureAsynSocket.NGX_HTTP_CLOJURE_SOCKET_OK) {
			try {
				outputStream.onInnerWrite();
				if (outputStream.busyChain == null) {
					outputStream.onWritePossible();
				}
			}catch(Throwable e) {
				ExceptionUtils.handleThrowable(e);
				outputStream.onError(e);
			}
		} else {
			outputStream.busyChain = null;
			outputStream.onError(new IOException(NginxClojureAsynSocket
					.errorCodeToString(status)));
		}
	}

}
