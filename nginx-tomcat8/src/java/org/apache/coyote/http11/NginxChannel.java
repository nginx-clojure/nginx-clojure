/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 *For reuse some classes from tomcat8 we have to use this package
 */
package org.apache.coyote.http11;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.Selector;

import nginx.clojure.NginxHttpServerChannel;

import org.apache.coyote.http11.NginxEndpoint.KeyAttachment;
import org.apache.tomcat.util.net.SecureNioChannel.ApplicationBufferHandler;
import org.apache.tomcat.util.res.StringManager;

public class NginxChannel implements ByteChannel {



    protected static final StringManager sm =
            StringManager.getManager("org.apache.tomcat.util.net.res");

    protected static ByteBuffer emptyBuf = ByteBuffer.allocate(0);

    protected NginxHttpServerChannel sc = null;
    
    protected KeyAttachment attachment;

    protected ApplicationBufferHandler bufHandler;
    
    protected NginxEndpoint.SendfileData sendfileData = null;

    protected boolean sendFile = false;
    
    protected boolean ignoreNginxFilter = false;

    public NginxChannel(NginxHttpServerChannel channel, ApplicationBufferHandler bufHandler, boolean ignoreNginxFilter) {
        this.sc = channel;
        this.bufHandler = bufHandler;
        this.ignoreNginxFilter = ignoreNginxFilter;
    }

    /**
     * Reset the channel
     *
     * @throws IOException If a problem was encountered resetting the channel
     */
    public void reset() throws IOException {
        bufHandler.getReadBuffer().clear();
        bufHandler.getWriteBuffer().clear();
        this.sendFile = false;
        this.sendfileData = null;
        closed = false;
    }

    public int getBufferSize() {
        if ( bufHandler == null ) return 0;
        int size = 0;
        size += bufHandler.getReadBuffer()!=null?bufHandler.getReadBuffer().capacity():0;
        size += bufHandler.getWriteBuffer()!=null?bufHandler.getWriteBuffer().capacity():0;
        return size;
    }

    /**
     * Returns true if the network buffer has been flushed out and is empty.
     *
     * @param block     Unused. May be used when overridden
     * @param s         Unused. May be used when overridden
     * @param timeout   Unused. May be used when overridden
     * @return Always returns <code>true</code> since there is no network buffer
     *         in the regular channel
     * @throws IOException
     */
    public boolean flush(boolean block, Selector s, long timeout)
            throws IOException {
        return true;
    }


    boolean closed = false;
    
    /**
     * Closes this channel.
     *
     * @throws IOException If an I/O error occurs
     */
    @Override
    public  void close() throws IOException {
    	synchronized (this) {
    		if (closed) {
    			return;
    		}
    		closed = true;
		}
        getIOChannel().close();
    }

    public void close(boolean force) throws IOException {
        if (isOpen() || force ) close();
    }
    /**
     * Tells whether or not this channel is open.
     *
     * @return <tt>true</tt> if, and only if, this channel is open
     */
    @Override
    public boolean isOpen() {
        return !sc.isClosed();
    }

    /**
     * Writes a sequence of bytes to this channel from the given buffer.
     *
     * @param src The buffer from which bytes are to be retrieved
     * @return The number of bytes written, possibly zero
     * @throws IOException If some other I/O error occurs
     */
    @Override
    public int write(ByteBuffer src) throws IOException {
        checkInterruptStatus();
        return (int)sc.write(src);
    }

    /**
     * Reads a sequence of bytes from this channel into the given buffer.
     *
     * @param dst The buffer into which bytes are to be transferred
     * @return The number of bytes read, possibly zero, or <tt>-1</tt> if the
     *         channel has reached end-of-stream
     * @throws IOException If some other I/O error occurs
     */
    @Override
    public int read(ByteBuffer dst) throws IOException {
        return (int) sc.read(dst);
    }

    public Object getAttachment() {
        return attachment;
    }

    public ApplicationBufferHandler getBufHandler() {
        return bufHandler;
    }

    public NginxHttpServerChannel getIOChannel() {
        return sc;
    }

    public boolean isClosing() {
        return false;
    }

    public boolean isHandshakeComplete() {
        return true;
    }

    /**
     * Performs SSL handshake hence is a no-op for the non-secure
     * implementation.
     *
     * @param read  Unused in non-secure implementation
     * @param write Unused in non-secure implementation
     * @return Always returns zero
     * @throws IOException
     */
    public int handshake(boolean read, boolean write) throws IOException {
        return 0;
    }


    public void setIOChannel(NginxHttpServerChannel IOChannel) {
        this.sc = IOChannel;
    }

    @Override
    public String toString() {
        return super.toString()+":"+this.sc.toString();
    }

    public int getOutboundRemaining() {
        return 0;
    }

    /**
     * Return true if the buffer wrote data
     * @throws IOException
     */
    public boolean flushOutbound() throws IOException {
        return false;
    }

    public boolean isSendFile() {
        return sendFile;
    }

    public void setSendFile(boolean s) {
        this.sendFile = s;
    }


    /**
     * This method should be used to check the interrupt status before
     * attempting a write.
     *
     * If a thread has been interrupted and the interrupt has not been cleared
     * then an attempt to write to the socket will fail. When this happens the
     * socket is removed from the poller without the socket being selected. This
     * results in a connection limit leak for NIO as the endpoint expects the
     * socket to be selected even in error conditions.
     */
    protected void checkInterruptStatus() throws IOException {
        if (Thread.interrupted()) {
            throw new IOException(sm.getString("channel.nio.interrupted"));
        }
    }


}
