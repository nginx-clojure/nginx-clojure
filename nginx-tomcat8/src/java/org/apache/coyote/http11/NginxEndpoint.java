/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 *For reuse some classes from tomcat8 we have to use this package
 */
package org.apache.coyote.http11;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import nginx.clojure.ChannelListener;
import nginx.clojure.NginxClojureRT;
import nginx.clojure.NginxHttpServerChannel;
import nginx.clojure.java.ArrayMap;
import nginx.clojure.java.NginxJavaRequest;
import nginx.clojure.net.NginxClojureAsynSocket;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.collections.SynchronizedStack;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.SecureNioChannel.ApplicationBufferHandler;
import org.apache.tomcat.util.net.SocketStatus;
import org.apache.tomcat.util.net.SocketWrapper;

public class NginxEndpoint extends AbstractEndpoint<NginxChannel> implements ChannelListener<SocketWrapper<NginxChannel>>{
	
	
    public static class NginxBufferHandler implements ApplicationBufferHandler {
        private ByteBuffer readbuf = null;
        private ByteBuffer writebuf = null;

        public NginxBufferHandler(int readsize, int writesize, boolean direct) {
            if ( direct ) {
                readbuf = ByteBuffer.allocateDirect(readsize);
                writebuf = ByteBuffer.allocateDirect(writesize);
            }else {
                readbuf = ByteBuffer.allocate(readsize);
                writebuf = ByteBuffer.allocate(writesize);
            }
        }

        @Override
        public ByteBuffer expand(ByteBuffer buffer, int remaining) {return buffer;}
        @Override
        public ByteBuffer getReadBuffer() {return readbuf;}
        @Override
        public ByteBuffer getWriteBuffer() {return writebuf;}

    }
	
    public interface Handler extends AbstractEndpoint.Handler {
        public SocketState process(SocketWrapper<NginxChannel> channel,
                SocketStatus status);
        public void release(SocketWrapper<NginxChannel> channel);
        public void release(NginxHttpServerChannel channel);
    }
    
    protected class SocketProcessor implements Runnable {

        private KeyAttachment ka = null;
        private SocketStatus status = null;

        public SocketProcessor(KeyAttachment ka, SocketStatus status) {
            reset(ka, status);
        }

        public void reset(KeyAttachment ka, SocketStatus status) {
            this.ka = ka;
            this.status = status;
        }

        @Override
        public void run() {
            doRun(ka);
        }

        private void doRun(KeyAttachment ka) {
        	NginxChannel socket = ka.getSocket();

            try {
                SocketState state = SocketState.OPEN;
                // Process the request from this socket
                if (status == null) {
                    state = handler.process(ka, SocketStatus.OPEN_READ);
                } else {
                    state = handler.process(ka, status);
                }
                if (state == SocketState.CLOSED) {
                    // Close socket and pool
                    try {
                        ka.setComet(false);
                        if (cancelledKey(ka, SocketStatus.ERROR) != null) {
                            // SocketWrapper (attachment) was removed from the
                            // key - recycle both. This can only happen once
                            // per attempted closure so it is used to determine
                            // whether or not to return socket and ka to
                            // their respective caches. We do NOT want to do
                            // this more than once - see BZ 57340.
                            if (running && !paused) {
                            	nginxChannels.push(socket);
                            }
                            socket = null;
//                            if (running && !paused) {
//                                keyCache.push(ka);
//                            }
                        }
                        ka = null;
                    } catch (Exception x) {
                        log.error("",x);
                    }
                }
            
            }  catch (OutOfMemoryError oom) {
                try {
                    oomParachuteData = null;
                    log.error("", oom);
                    if (socket != null) {
                        cancelledKey(ka,SocketStatus.ERROR);
                    }
                    releaseCaches();
                } catch (Throwable oomt) {
                    try {
                        System.err.println(oomParachuteMsg);
                        oomt.printStackTrace();
                    } catch (Throwable letsHopeWeDontGetHere){
                        ExceptionUtils.handleThrowable(letsHopeWeDontGetHere);
                    }
                }
            } catch (VirtualMachineError vme) {
                ExceptionUtils.handleThrowable(vme);
            } catch (Throwable t) {
                log.error("", t);
                if (socket != null) {
                    cancelledKey(ka,SocketStatus.ERROR);
                }
            } finally {
                socket = null;
                status = null;
                //return to cache
                if (running && !paused) {
                    processorCache.push(this);
                }
            }
        }
    }

	private static final Log log = LogFactory.getLog(NginxEndpoint.class);
	
	protected SynchronizedStack<NginxChannel> nginxChannels;// = new SynchronizedStack<NginxChannel>(SynchronizedStack.DEFAULT_SIZE, -1);
	
    /**
     * Cache for SocketProcessor objects
     */
    private SynchronizedStack<SocketProcessor> processorCache;// = new SynchronizedStack<SocketProcessor>(SynchronizedStack.DEFAULT_SIZE, -1);
	
    /**
     * Cache for key attachment objects
     */
//    private SynchronizedStack<KeyAttachment> keyCache;// = new SynchronizedStack<KeyAttachment>(SynchronizedStack.DEFAULT_SIZE, -1);
    
    /**
     * The size of the OOM parachute.
     */
    private int oomParachute = 1024*1024;
    /**
     * The oom parachute, when an OOM error happens,
     * will release the data, giving the JVM instantly
     * a chunk of data to be able to recover with.
     */
    private byte[] oomParachuteData = null;

    /**
     * Make sure this string has already been allocated
     */
    private static final String oomParachuteMsg =
        "SEVERE:Memory usage is low, parachute is non existent, your system may start failing.";
    
    
    /**
     * Keep track of OOM warning messages.
     */
    private long lastParachuteCheck = System.currentTimeMillis();
    
    
    
	@Override
	public int getLocalPort() {
		
		return 0;
	}

	@Override
	protected boolean getDeferAccept() {
		return false;
	}

	@Override
	public void processSocket(
			final SocketWrapper<NginxChannel> socketWrapper,
			SocketStatus socketStatus, boolean dispatch) {
		final NginxChannel channel = socketWrapper.getSocket();
		if (channel.isOpen() && dispatch
				&& socketStatus == SocketStatus.OPEN_READ) {
			KeyAttachment ka = (KeyAttachment) socketWrapper;
			ka.setCometNotify(true);//TODO:remove this useless line, now we just keep trace with tomcat implementation
			if (!processSocket(ka, SocketStatus.OPEN_READ, true)) {
				processSocket(ka, SocketStatus.DISCONNECT, true);
			}
		} else {
            processSocket((KeyAttachment) socketWrapper, socketStatus, dispatch);
        }
	}
	
	// ----------------------------------------------------- Key Attachment Class
    public static class KeyAttachment extends SocketWrapper<NginxChannel> {

        public KeyAttachment(NginxChannel channel) {
            super(channel);
        }

        /*tomcat 0.2.1 remove reset method from SocketWrapper and do not reuse this object*/
//		public void reset(NginxChannel channel, long soTimeout) {
//			super.reset(channel, soTimeout);
//			cometNotify = false;
//			sendfileData = null;
//		}
//
//
//		public void reset() {
//			reset(null, -1);
//		}
        
        public void setCometNotify(boolean notify) { this.cometNotify = notify; }
        public boolean getCometNotify() { return cometNotify; }


        public void setSendfileData(SendfileData sf) { this.sendfileData = sf;}
        public SendfileData getSendfileData() { return this.sendfileData;}

        public void setDispatch(boolean dispatch) {this.dispatch = dispatch; }
        public boolean getDispatch() {return dispatch; }
        
        private boolean cometNotify = false;
        private boolean dispatch = false;
        private volatile SendfileData sendfileData = null;

    }
    
    /**
     * SendfileData class.
     */
    public static class SendfileData {
        // File
        public volatile String fileName;
        public volatile long pos;
        public volatile long length;
    }
	
    protected boolean processSocket(KeyAttachment attachment, SocketStatus status, boolean dispatch) {
        try {
            if (attachment == null) {
                return false;
            }
            attachment.setCometNotify(false); 
            SocketProcessor sc = processorCache.pop();
            if ( sc == null ) sc = new SocketProcessor(attachment, status);
            else sc.reset(attachment, status);
            Executor executor = getExecutor();
            if (dispatch && executor != null) {
                executor.execute(sc);
            } else {
                sc.run();
            }
        } catch (RejectedExecutionException ree) {
            log.warn(sm.getString("endpoint.executor.fail", attachment.getSocket()), ree);
            return false;
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            // This means we got an OOM or similar creating a thread, or that
            // the pool and its queue are full
            log.error(sm.getString("endpoint.process.fail"), t);
            return false;
        }
        return true;
    }

	@Override
	public void bind() throws Exception {
		log.info("skip bind because nginx has already done it.");
	}

	@Override
	public void unbind() throws Exception {
		log.info("skip unbind because nginx will do it.");
	}

	@Override
	public void startInternal() throws Exception {


        if (!running) {
            running = true;
            paused = false;

        	nginxChannels = new SynchronizedStack<NginxChannel>(SynchronizedStack.DEFAULT_SIZE, -1);
        	processorCache = new SynchronizedStack<SocketProcessor>(SynchronizedStack.DEFAULT_SIZE, -1);
//        	keyCache = new SynchronizedStack<KeyAttachment>(SynchronizedStack.DEFAULT_SIZE, -1);
        	acceptors = new Acceptor[0];

            // Create worker collection
            if ( getExecutor() == null ) {
                createExecutor();
            }

            //nginx control max number connections so we skip it
            //initializeConnectionLatch();
        }
    
	}

	@Override
	public void stopInternal() throws Exception {

        if (!paused) {
            pause();
        }
        if (running) {
            running = false;
//            keyCache.clear();
            nginxChannels.clear();
            processorCache.clear();
        }
	}

	@Override
	protected org.apache.tomcat.util.net.AbstractEndpoint.Acceptor createAcceptor() {
		throw new UnsupportedOperationException("createAcceptor");
	}

	@Override
	protected Log getLog() {
		return log;
	}

	@Override
	public boolean getUseSendfile() {
		return true;
	}
	
    /**
     * Handling of accepted sockets.
     */
    private Handler handler = null;
    public void setHandler(Handler handler ) { this.handler = handler; }
    public Handler getHandler() { return handler; }

	@Override
	public boolean getUseComet() {
		return false;
	}

	@Override
	public boolean getUseCometTimeout() {
		return false;
	}

	@Override
	public boolean getUsePolling() {
		return true;
	}

	@Override
	public String[] getCiphersUsed() {
		return null;
	}
	
    protected void checkParachute() {
        boolean para = reclaimParachute(false);
        if (!para && (System.currentTimeMillis()-lastParachuteCheck)>10000) {
            try {
                log.fatal(oomParachuteMsg);
            }catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                System.err.println(oomParachuteMsg);
            }
            lastParachuteCheck = System.currentTimeMillis();
        }
    }

    protected boolean reclaimParachute(boolean force) {
        if ( oomParachuteData != null ) return true;
        if ( oomParachute > 0 && ( force || (Runtime.getRuntime().freeMemory() > (oomParachute*2))) )
            oomParachuteData = new byte[oomParachute];
        return oomParachuteData != null;
    }

    protected void releaseCaches() {
//        this.keyCache.clear();
        this.nginxChannels.clear();
        this.processorCache.clear();
        if ( handler != null ) handler.recycle();

    }
    
    public void register(final NginxChannel socket, boolean dispatch) {
//        KeyAttachment key = keyCache.pop();
        final KeyAttachment ka = new KeyAttachment(socket);
        ka.setDispatch(dispatch);
        ka.setTimeout(getSocketProperties().getSoTimeout());
        ka.setKeepAliveLeft(NginxEndpoint.this.getMaxKeepAliveRequests());
        ka.setSecure(isSSLEnabled());
        processSocket(ka, SocketStatus.OPEN_READ, dispatch);
    }
    
    public KeyAttachment cancelledKey(KeyAttachment ka, SocketStatus status) {
    	try {
            if (ka != null && ka.isComet() && status != null) {
                ka.setComet(false);//to avoid a loop
                if (status == SocketStatus.TIMEOUT ) {
                    if (processSocket(ka, status, true)) {
                        return null; // don't close on comet timeout
                    }
                } else {
                    // Don't dispatch if the lines below are cancelling the key
                    processSocket(ka, status, false);
                }
            }
            if (ka!=null) handler.release(ka);
            if (ka.getSocket().isOpen()) {
                try {
                	ka.getSocket().close();
                } catch (Exception e) {
                    if (log.isDebugEnabled()) {
                        log.debug(sm.getString(
                                "endpoint.debug.channelCloseFail"), e);
                    }
                }
            }
            try {
                if (ka!=null) {
                    ka.getSocket().close(true);
                }
            } catch (Exception e){
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString(
                            "endpoint.debug.socketCloseFail"), e);
                }
            }
            if (ka!=null) {
//                ka.reset();
                countDownConnection();
            }
        } catch (Throwable e) {
            ExceptionUtils.handleThrowable(e);
            if (log.isDebugEnabled()) log.error("",e);
        }
        return ka;
    }
    
    public void accept(NginxJavaRequest req, boolean ignoreNginxFilter, boolean dispatch) throws IOException {
    	final NginxHttpServerChannel ioChannel = req.hijack(ignoreNginxFilter);
    	NginxChannel channel = nginxChannels.pop();
		if (channel == null) {
			channel = new NginxChannel(ioChannel, new NginxBufferHandler(
					socketProperties.getAppReadBufSize(),
					socketProperties.getAppWriteBufSize(),
					false), ignoreNginxFilter);
		}else {
			channel.setIOChannel(ioChannel);
			channel.reset();
		}
		
		register(channel, dispatch);
    }

	@Override
	public void onClose(SocketWrapper<NginxChannel> data) throws IOException {
		if (data.getSocket() == null) {
			return;
		}
		processSocket(data, SocketStatus.CLOSE_NOW, ((KeyAttachment)data).dispatch);
	}

	@Override
	public void onConnect(long status, SocketWrapper<NginxChannel> data)
			throws IOException {
	}

	@Override
	public void onRead(long status, SocketWrapper<NginxChannel> data)
			throws IOException {
		if (data.getSocket() == null) {
			return;
		}
		if (status == NginxClojureAsynSocket.NGX_HTTP_CLOJURE_SOCKET_OK) {
			processSocket(data, SocketStatus.OPEN_READ, ((KeyAttachment)data).dispatch);
		}else {
			processSocket(data, SocketStatus.ASYNC_READ_ERROR, ((KeyAttachment)data).dispatch);
		}
	}

	@Override
	public void onWrite(long status, SocketWrapper<NginxChannel> data)
			throws IOException {
		if (data.getSocket() == null) {
			return;
		}
		if (status == NginxClojureAsynSocket.NGX_HTTP_CLOJURE_SOCKET_OK) {
			processSocket(data, SocketStatus.OPEN_WRITE, ((KeyAttachment)data).dispatch);
		}else {
			processSocket(data, SocketStatus.ASYNC_WRITE_ERROR, ((KeyAttachment)data).dispatch);
		}
	}

}
