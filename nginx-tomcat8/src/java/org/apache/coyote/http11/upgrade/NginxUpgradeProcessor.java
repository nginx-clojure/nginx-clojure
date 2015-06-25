/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 *For reuse some classes from tomcat8 we have to use this package
 */
package org.apache.coyote.http11.upgrade;

import java.io.IOException;
import java.nio.ByteBuffer;

import javax.servlet.WriteListener;
import javax.servlet.http.HttpUpgradeHandler;

import nginx.clojure.MiniConstants;

import org.apache.coyote.http11.NginxChannel;
import org.apache.juli.logging.Log;
import org.apache.tomcat.util.net.SocketWrapper;

public class NginxUpgradeProcessor extends AbstractProcessor<NginxChannel>{

	
	protected NginxChannel channel;
	protected WebConnectionInputStream inputStream;
	protected WebConnectionOutputStream outputStream;
	
	protected static final org.apache.juli.logging.Log log =
	        org.apache.juli.logging.LogFactory.getLog( NginxUpgradeProcessor.class );
	
	
	public NginxUpgradeProcessor(SocketWrapper<NginxChannel> socket,
			ByteBuffer leftoverInput,
			HttpUpgradeHandler httpUpgradeProcessor) throws IOException {
		super(httpUpgradeProcessor, new WebConnectionInputStream(socket.getSocket()), 
				new WebConnectionOutputStream(socket));
		inputStream = (WebConnectionInputStream) getInputStream();
		outputStream = (WebConnectionOutputStream) getOutputStream();
		channel = socket.getSocket();
//		channel.setAsyncTimeout(-1);
		ByteBuffer readBuffer = socket.getSocket().getBufHandler().getReadBuffer();
        if (leftoverInput != null) {
            if (readBuffer.remaining() > 0) {
                readBuffer.flip();
            } else {
                readBuffer.clear();
            }
            readBuffer.put(leftoverInput);
            readBuffer.flip();
        }else {
        	readBuffer.flip();
        }
	}
	
	@Override
	protected Log getLog() {
		return log;
	}

	@Override
	public void close() throws Exception {
		channel.close();
	}
	
	protected static class WebConnectionInputStream extends AbstractServletInputStream {

		protected NginxChannel channel;
		protected boolean eof = false;
		
		public WebConnectionInputStream(NginxChannel channel) {
			this.channel = channel;
		}
		
		protected void onClose() {
			if (eof) {
				return;
			}
			eof = true;
		}

		@Override
		protected boolean doIsReady() throws IOException {

			ByteBuffer buffer = channel.getBufHandler().getReadBuffer();
			
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

			ByteBuffer buffer = channel.getBufHandler().getReadBuffer();
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
	        }  /*nRead == -1 will never happen*/

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

	
	public static class WebConnectionOutputStream extends AbstractServletOutputStream<NginxChannel> {
		
//		protected ByteBuffer buffer;
		protected NginxChannel channel;
		protected WriteListener listener;
		protected boolean eof = false;
		protected int maxWrite;

		public WebConnectionOutputStream(SocketWrapper<NginxChannel> socket) {
			super(socket, MiniConstants.NGINX_CLOJURE_CORE_CLIENT_HEADER_MAX_SIZE);
			this.channel = socket.getSocket();
			maxWrite = socket.getSocket().getBufHandler().getWriteBuffer().capacity();
		}

		/**
		 * before invoke it tomcat will get a write lock. so it need not be thread-safe.
		 */
		@Override
		protected int doWrite(boolean block, byte[] b, int off, int len)
				throws IOException {
	        int leftToWrite = len;
	        int count = 0;
	        int offset = off;

	        while (leftToWrite > 0) {
	            int writeThisLoop;
	            int writtenThisLoop;

	            if (leftToWrite > maxWrite) {
	                writeThisLoop = maxWrite;
	            } else {
	                writeThisLoop = leftToWrite;
	            }

	            writtenThisLoop = doWriteInternal(block, b, offset, writeThisLoop);
	            count += writtenThisLoop;
	            offset += writtenThisLoop;
	            leftToWrite -= writtenThisLoop;

	            if (writtenThisLoop == 0) {
	                break;
	            }
	        }

	        return count;
	    }
		
	    private int doWriteInternal (boolean block, byte[] b, int off, int len)
	            throws IOException {
	        channel.getBufHandler().getWriteBuffer().clear();
	        channel.getBufHandler().getWriteBuffer().put(b, off, len);
	        channel.getBufHandler().getWriteBuffer().flip();
	        return (int)channel.write(channel.getBufHandler().getWriteBuffer());
	    }


		@Override
		protected void doClose() throws IOException {
			eof = true;
			channel.close();
		}

		@Override
		protected void doFlush() throws IOException {
			/*nothing to do*/
//			channel.flush(true, null, -1);
		}
	}

}
