/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 *For reuse some classes from tomcat8 we have to use this package
 */
package org.apache.coyote.http11;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import nginx.clojure.MiniConstants;
import nginx.clojure.NginxClojureRT;
import nginx.clojure.NginxHttpServerChannel;
import nginx.clojure.java.Constants;
import nginx.clojure.java.NginxJavaRequest;

import org.apache.coyote.InputBuffer;
import org.apache.coyote.Request;
import org.apache.coyote.http11.AbstractNioInputBuffer;
import org.apache.coyote.http11.InternalNioInputBuffer.SocketInputBuffer;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.SocketWrapper;

public  class InternalNginxInputBuffer extends AbstractNioInputBuffer<NginxChannel> {
	
	private static final Log log =
            LogFactory.getLog(InternalNginxInputBuffer.class);
	
	protected InputStream body;
	protected NginxChannel socket;
	
	public InternalNginxInputBuffer(Request request, int headerBufferSize) {
		super(request, headerBufferSize);
		inputStreamInputBuffer = new SocketInputBuffer();
	}

	@Override
	protected boolean fill(boolean block) throws IOException {
		if (!parsingHeader) {
			lastValid = pos = end;
		}
		int nRead = 0;
		ByteBuffer readBuffer = socket.getBufHandler().getReadBuffer();
		readBuffer.clear();
		nRead = body == null ? -1 : body.read(readBuffer.array(), 0, readBuffer.limit());
        if (nRead > 0) {
            readBuffer.flip();
            readBuffer.limit(nRead);
            expand(nRead + pos);
            readBuffer.get(buf, pos, nRead);
            lastValid = pos + nRead;
        } else if (nRead == -1) {
            //return false;
            throw new EOFException(sm.getString("iib.eof.error"));
        }
        return nRead > 0;
	}
	
	@Override
	public boolean isFinished() {
        if (lastValid > pos) {
            // Data to read in the buffer so not finished
            return false;
        }
        
        try {
			return body == null || body.available() == 0;
		} catch (IOException e) {
			return true;
		}
	}
	
	protected void init(NginxChannel sc) throws IOException {
		this.socket = sc;
		NginxJavaRequest jreq = (NginxJavaRequest) sc.getIOChannel().request();
		//fill header buffer
		if (buf == null) {
			buf = new byte[headerBufferSize*2];
		}
		body = (InputStream) jreq.get(Constants.BODY);
		lastValid = (int)NginxClojureRT.ngx_http_clojure_mem_copy_header_buf(jreq.nativeRequest(), buf, MiniConstants.BYTE_ARRAY_OFFSET, headerBufferSize);
	}

	@Override
	protected void init(
			SocketWrapper<NginxChannel> socketWrapper,
			AbstractEndpoint<NginxChannel> endpoint)
			throws IOException {
		init(socketWrapper.getSocket());
	}

	@Override
	protected Log getLog() {
		return log;
	}
	
	public int doRead(ByteChunk chunk, Request request) throws IOException {
		if (body == null) {
			return -1;
		}
		
		lastValid = pos = end;
		int len = body.read(buf, lastValid, buf.length - lastValid);
		if (len == 0) {
			return -1;
		}
		chunk.setBytes(buf, lastValid, len);
		return len;
	}
	
	@Override
	public void recycle() {
		super.recycle();
		body = null;
	}
	
    protected class SocketInputBuffer
    implements InputBuffer {


    /**
     * Read bytes into the specified chunk.
     */
    @Override
    public int doRead(ByteChunk chunk, Request req )
        throws IOException {

        if (pos >= lastValid) {
            if (!fill(true)) //read body, must be blocking, as the thread is inside the app
                return -1;
        }

        int length = lastValid - pos;
        chunk.setBytes(buf, pos, length);
        pos = lastValid;

        return (length);
    }
}
}