/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 *For reuse some classes from tomcat8 we have to use this package
 */
package org.apache.coyote.http11;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;

import nginx.clojure.NginxHttpServerChannel;

import org.apache.coyote.OutputBuffer;
import org.apache.coyote.Response;
import org.apache.coyote.http11.AbstractOutputBuffer;
import org.apache.coyote.http11.Constants;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.NioEndpoint;
import org.apache.tomcat.util.net.SocketWrapper;

public class InternalNginxOutputBuffer extends AbstractOutputBuffer<NginxChannel> {

	protected NginxChannel socket;
	
	protected volatile boolean flipped = false;
	
	protected InternalNginxOutputBuffer(Response response, int headerBufferSize) {
		super(response, headerBufferSize);
		outputStreamOutputBuffer = new HttpChannelOutputBuffer();
	}

	@Override
	public void init(SocketWrapper<NginxChannel> socketWrapper,
			AbstractEndpoint<NginxChannel> endpoint)
			throws IOException {
		socket = socketWrapper.getSocket();
	}

	public void addActiveFilter(OutputFilter filter) {
		//by default socket.ignoreNginxFilter is true we will use nginx filter instead of tomcat filter
		if (socket.ignoreNginxFilter) {
			super.addActiveFilter(filter);
		}
	}
	
	@Override
	public void sendAck() throws IOException {
        if (!committed) {
            socket.getBufHandler().getWriteBuffer().put(
                    Constants.ACK_BYTES, 0, Constants.ACK_BYTES.length);
            int result = writeToSocket(socket.getBufHandler().getWriteBuffer(), true, true);
            if (result < 0) {
                throw new IOException(sm.getString("iob.failedwrite.ack"));
            }
        }
	}
	
	protected synchronized int writeToSocket(byte[] buf, int pos, int len) throws IOException {
		try {
			socket.getIOChannel().send(buf, pos, len, true, false);
		}catch(RuntimeException e) {
			throw new IOException(e.getMessage(), e);
		}
	  return len;
	}
	
    protected synchronized int writeToSocket(ByteBuffer bytebuffer, boolean block, boolean flip) throws IOException {
        if ( flip ) {
            bytebuffer.flip();
            flipped = true;
        }

        int written = bytebuffer.remaining();
        NginxEndpoint.KeyAttachment att = (NginxEndpoint.KeyAttachment)socket.getAttachment();
        if ( att == null ) throw new IOException("Key must be cancelled");

        socket.getIOChannel().send(bytebuffer, true, false);

        if ( block || bytebuffer.remaining()==0) {
            //blocking writes must empty the buffer
            //and if remaining==0 then we did empty it
            bytebuffer.clear();
            flipped = false;
        }
        // If there is data left in the buffer the socket will be registered for
        // write further up the stack. This is to ensure the socket is only
        // registered for write once as both container and user code can trigger
        // write registration.
        return written;
    }

	@Override
	protected void commit() throws IOException {
        // The response is now committed
        committed = true;
        response.setCommitted(true);

        if (pos > 0 && (!socket.isSendFile() || socket.getIOChannel().isIgnoreFilter())) {
        	socket.getIOChannel().sendHeader(headerBuffer, 0, pos, true, false);
        }
	}

	@Override
	public void endRequest() throws IOException {
		if (finished) {
			return;
		}
		super.endRequest();
		if (!socket.isSendFile() || socket.getIOChannel().isIgnoreFilter()) {
			socket.close();
		}
	}
	
	@Override
	protected boolean hasMoreDataToFlush() {
		return false;
	}

	@Override
	protected void registerWriteInterest() throws IOException {
	}

	@Override
	protected boolean flushBuffer(boolean block) throws IOException {
		try {
			socket.getIOChannel().flush();
		}catch(RuntimeException e) {
			throw new IOException(e.getMessage(), e);
		}
		return false;
	}
	
	protected  class HttpChannelOutputBuffer implements OutputBuffer {
		
		public HttpChannelOutputBuffer() {
		}
		
		
		@Override
		public long getBytesWritten() {
			return byteCount;
		}
		
		@Override
		public int doWrite(ByteChunk chunk, Response response)
				throws IOException {
			int c = writeToSocket(chunk.getBuffer(), chunk.getStart(), chunk.getLength());
			byteCount += c;
			return c;
		}
	}

}
