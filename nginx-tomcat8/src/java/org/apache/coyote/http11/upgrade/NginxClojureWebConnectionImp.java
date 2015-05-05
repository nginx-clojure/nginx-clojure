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
import nginx.clojure.NginxHttpServerChannel;
import nginx.clojure.net.NginxClojureAsynSocket;

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
				buffer = ByteBuffer.allocate(4096);
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

	
	protected static class WebConnectionOutputStream extends AbstractServletOutputStream {
		
//		protected ByteBuffer buffer;
		protected NginxHttpServerChannel channel;
		protected WriteListener listener;
		protected boolean eof = false;
		protected final static int maxWrite = 4096;
		
		private static SocketWrapper<NginxHttpServerChannel> fackSocketWrapper = new SocketWrapper<NginxHttpServerChannel>(null) {
			@Override
			public void addDispatch(org.apache.tomcat.util.net.DispatchType dispatchType) {};
		};

		public WebConnectionOutputStream(NginxHttpServerChannel channel) {
			super(fackSocketWrapper, maxWrite);
			this.channel = channel;
//			buffer = ByteBuffer.allocate(maxWrite);
		}

		@Override
		protected int doWrite(boolean block, byte[] b, int off, int len)
				throws IOException {

	        int leftToWrite = len;
	        int count = 0;
	        int offset = off;

	        while (leftToWrite > 0) {
	            int writtenThisLoop;

	            writtenThisLoop = (int) channel.write(b, offset, leftToWrite);
	            count += writtenThisLoop;
	            offset += writtenThisLoop;
	            leftToWrite -= writtenThisLoop;

	            if (writtenThisLoop == 0) {
	                break;
	            }
	        }

	        return count;
	    
		}

		@Override
		protected void doFlush() throws IOException {
		}

		@Override
		protected void doClose() throws IOException {
			eof = true;
			channel.close();
		}
		
		
	}


	@Override
	public void onClose(NginxClojureWebConnectionImp data) {
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
	public void onConnect(long status, NginxClojureWebConnectionImp data) {
	}

	@Override
	public void onRead(long status, NginxClojureWebConnectionImp data) {
		if (status == NginxClojureAsynSocket.NGX_HTTP_CLOJURE_SOCKET_OK) {
			inputStream.onDataAvailable();
		} else {
			inputStream.onError(new IOException(NginxClojureAsynSocket
					.errorCodeToString(status)));
		}
	}

	@Override
	public void onWrite(long status, NginxClojureWebConnectionImp data) {

		if (status == NginxClojureAsynSocket.NGX_HTTP_CLOJURE_SOCKET_OK) {
			outputStream.onWritePossible();
		} else {
			outputStream.onError(new IOException(NginxClojureAsynSocket
					.errorCodeToString(status)));
		}

	}

}
