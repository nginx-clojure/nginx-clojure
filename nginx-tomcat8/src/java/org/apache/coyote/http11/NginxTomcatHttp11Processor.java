/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 *For reuse some classes from tomcat8 we have to use this package
 */
package org.apache.coyote.http11;

import static nginx.clojure.MiniConstants.NGX_HTTP_OK;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Set;

import nginx.clojure.MiniConstants;
import nginx.clojure.java.ArrayMap;
import nginx.clojure.java.NginxJavaRequest;

import org.apache.coyote.ActionCode;
import org.apache.coyote.ErrorState;
import org.apache.coyote.RequestInfo;
import org.apache.juli.logging.Log;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.NioEndpoint;
import org.apache.tomcat.util.net.SSLSupport;
import org.apache.tomcat.util.net.SocketStatus;
import org.apache.tomcat.util.net.SocketWrapper;

public class NginxTomcatHttp11Processor extends AbstractHttp11Processor<NginxChannel> {

	protected static final org.apache.juli.logging.Log log =
	        org.apache.juli.logging.LogFactory.getLog( NginxTomcatHttp11Processor.class );
	
	protected NginxEndpoint.SendfileData sendfileData = null;
	
	public NginxTomcatHttp11Processor(int maxHttpHeaderSize, NginxEndpoint endpoint,
            int maxTrailerSize, Set<String> allowedTrailerHeaders, int maxExtensionSize, int maxSwallowSize) {
		super(endpoint);
		inputBuffer = new InternalNginxInputBuffer(request, maxHttpHeaderSize);
        request.setInputBuffer(inputBuffer);

        outputBuffer = new InternalNginxOutputBuffer(response, maxHttpHeaderSize);
        response.setOutputBuffer(outputBuffer);

        initializeFilters(maxTrailerSize, allowedTrailerHeaders, maxExtensionSize, maxSwallowSize);
	}

	@Override
	public void setSslSupport(SSLSupport sslSupport) {
	}

	@Override
	protected AbstractInputBuffer<NginxChannel> getInputBuffer() {
		return inputBuffer;
	}

	@Override
	protected AbstractOutputBuffer<NginxChannel> getOutputBuffer() {
		return outputBuffer;
	}

	@Override
	void actionInternal(ActionCode actionCode, Object param) {

        switch (actionCode) {
        case REQ_HOST_ADDR_ATTRIBUTE: {
            if (socketWrapper == null) {
                request.remoteAddr().recycle();
            } else {
                if (socketWrapper.getRemoteAddr() == null) {
                	NginxJavaRequest nreq = (NginxJavaRequest) socketWrapper.getSocket().getIOChannel().request();
                    socketWrapper.setRemoteAddr((String)nreq.get(MiniConstants.REMOTE_ADDR));
                }
                request.remoteAddr().setString(socketWrapper.getRemoteAddr());
            }
            break;
        }
        case REQ_LOCAL_NAME_ATTRIBUTE: {
            if (socketWrapper == null) {
                request.localName().recycle();
            } else {
                if (socketWrapper.getLocalName() == null) {
                	NginxJavaRequest nreq = (NginxJavaRequest) socketWrapper.getSocket().getIOChannel().request();
                	String serverAddr = nreq.getVariable("server_addr");
                    InetAddress inetAddr = null;
					try {
						inetAddr = InetAddress.getByName(serverAddr);
					} catch (UnknownHostException e) {
						log.error("can not get local server name", e);
					}
                    if (inetAddr != null) {
                        socketWrapper.setLocalName(inetAddr.getHostName());
                    }
                }
                request.localName().setString(socketWrapper.getLocalName());
            }
            break;
        }
        case REQ_HOST_ATTRIBUTE: {
            if (socketWrapper == null) {
                request.remoteHost().recycle();
            } else {
                if (socketWrapper.getRemoteHost() == null) {
                    if (socketWrapper.getRemoteHost() == null) {
                        if (socketWrapper.getRemoteAddr() == null) {
                        	NginxJavaRequest nreq = (NginxJavaRequest) socketWrapper.getSocket().getIOChannel().request();
                            socketWrapper.setRemoteAddr((String)nreq.get(MiniConstants.REMOTE_ADDR));
                        }
                        if (socketWrapper.getRemoteAddr() != null) {
                            socketWrapper.setRemoteHost(socketWrapper.getRemoteAddr());
                        }
                    }
                }
                request.remoteHost().setString(socketWrapper.getRemoteHost());
            }
            break;
        }
        case REQ_LOCAL_ADDR_ATTRIBUTE: {
            if (socketWrapper == null) {
                request.localAddr().recycle();
            } else {
                if (socketWrapper.getLocalAddr() == null) {
                	NginxJavaRequest nreq = (NginxJavaRequest) socketWrapper.getSocket().getIOChannel().request();
                	String serverAddr = nreq.getVariable("server_addr");
                    socketWrapper.setLocalAddr(serverAddr);
                }
                request.localAddr().setString(socketWrapper.getLocalAddr());
            }
            break;
        }
        case REQ_REMOTEPORT_ATTRIBUTE: {
            if (socketWrapper == null) {
                request.setRemotePort(0);
            } else {
                if (socketWrapper.getRemotePort() == -1) {
                	NginxJavaRequest nreq = (NginxJavaRequest) socketWrapper.getSocket().getIOChannel().request();
                	String remotePort = nreq.getVariable("remote_port");
                    socketWrapper.setRemotePort(Integer.parseInt(remotePort));
                }
                request.setRemotePort(socketWrapper.getRemotePort());
            }
            break;
        }
        case REQ_LOCALPORT_ATTRIBUTE: {
            if (socketWrapper == null) {
                request.setLocalPort(0);
            } else {
                if (socketWrapper.getLocalPort() == -1) {
                	NginxJavaRequest nreq = (NginxJavaRequest) socketWrapper.getSocket().getIOChannel().request();
                	String localPort = nreq.getVariable("server_port");
                    socketWrapper.setLocalPort(Integer.parseInt(localPort));
                }
                request.setLocalPort(socketWrapper.getLocalPort());
            }
            break;
        }
        case REQ_SSL_ATTRIBUTE: {
            break;
        }
        case REQ_SSL_CERTIFICATE: {
            break;
        }
        case COMET_BEGIN: {
            comet = true;
            break;
        }
        case COMET_END: {
            comet = false;
            break;
        }
        case COMET_CLOSE: {
            if (socketWrapper==null || socketWrapper.getSocket().getAttachment()==null) {
                return;
            }
            RequestInfo rp = request.getRequestProcessor();
            if (rp.getStage() != org.apache.coyote.Constants.STAGE_SERVICE) {
                // Close event for this processor triggered by request
                // processing in another processor, a non-Tomcat thread (i.e.
                // an application controlled thread) or similar.
//                socketWrapper.getSocket().getPoller().add(socketWrapper.getSocket());
            	throw new UnsupportedOperationException("not support COMET_CLOSE yet!");
            }
            break;
        }
        case COMET_SETTIMEOUT: {
            if (param==null) {
                return;
            }
            if (socketWrapper==null || socketWrapper.getSocket().getAttachment()==null) {
                return;
            }
            NioEndpoint.KeyAttachment attach = (NioEndpoint.KeyAttachment)socketWrapper.getSocket().getAttachment();
            long timeout = ((Long)param).longValue();
            //if we are not piggy backing on a worker thread, set the timeout
            RequestInfo rp = request.getRequestProcessor();
            if ( rp.getStage() != org.apache.coyote.Constants.STAGE_SERVICE ) {
                attach.setTimeout(timeout);
            }
            break;
        }
        }
    
	}

	@Override
	protected boolean disableKeepAlive() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	protected void setRequestLineReadTimeout() throws IOException {
	}

	@Override
	protected boolean handleIncompleteRequestLineRead() {
		throw new IllegalStateException("nginx tomat won't have incomplete request line to read");
	}

	@Override
	protected void setSocketTimeout(int timeout) throws IOException {
		socketWrapper.getSocket().getIOChannel().setAsyncTimeout(timeout);
	}

	@Override
	protected boolean breakKeepAliveLoop(
			SocketWrapper<NginxChannel> socketWrapper) {
		if (sendfileData != null && !getErrorState().isError()) {
			try {
				socketWrapper
						.getSocket()
						.getIOChannel()
						.sendResponse(
								new Object[] { NGX_HTTP_OK, null,
										new File(sendfileData.fileName) });
				sendfileInProgress = true;
			} catch (IOException e) {
				setErrorState(ErrorState.CLOSE_NOW, e);
			}
		}
		return true;
	}
	
	@Override
	protected void prepareRequestInternal() {
		// TODO Auto-generated method stub
		
	}

	@Override
	boolean prepareSendfile(OutputFilter[] outputFilters) {
		String fileName = (String) request.getAttribute(
                org.apache.coyote.Constants.SENDFILE_FILENAME_ATTR);
        if (fileName != null) {
            // No entity body sent here
            outputBuffer.addActiveFilter(outputFilters[Constants.VOID_FILTER]);
            contentDelimitation = true;
            sendfileData = new NginxEndpoint.SendfileData();
            sendfileData.fileName = fileName;
            sendfileData.pos = ((Long) request.getAttribute(
                    org.apache.coyote.Constants.SENDFILE_FILE_START_ATTR)).longValue();
            sendfileData.length = ((Long) request.getAttribute(
                    org.apache.coyote.Constants.SENDFILE_FILE_END_ATTR)).longValue() - sendfileData.pos;
            socketWrapper.getSocket().setSendFile(true);
            return true;
        }
        return false;
	}

	@Override
	protected void resetTimeouts() {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void setCometTimeouts(
			SocketWrapper<NginxChannel> socketWrapper) {
		// TODO Auto-generated method stub
		
	}


	@Override
	protected void recycleInternal() {
        socketWrapper = null;
        sendfileData = null;
	}

	@Override
	public SocketState event(SocketStatus status) throws IOException {

        long soTimeout = endpoint.getSoTimeout();

        RequestInfo rp = request.getRequestProcessor();
        final NginxEndpoint.KeyAttachment attach = (NginxEndpoint.KeyAttachment)socketWrapper.getSocket().getAttachment();
        try {
            rp.setStage(org.apache.coyote.Constants.STAGE_SERVICE);
            if (!getAdapter().event(request, response, status)) {
                setErrorState(ErrorState.CLOSE_NOW, null);
            }
            if (!getErrorState().isError()) {
                if (attach != null) {
                    attach.setComet(comet);
                    if (comet) {
                        Integer comettimeout = (Integer) request.getAttribute(
                                org.apache.coyote.Constants.COMET_TIMEOUT_ATTR);
                        if (comettimeout != null) {
                            attach.setTimeout(comettimeout.longValue());
                        }
                    } else {
                        //reset the timeout
                        if (keepAlive) {
                            attach.setTimeout(keepAliveTimeout);
                        } else {
                            attach.setTimeout(soTimeout);
                        }
                    }

                }
            }
        } catch (InterruptedIOException e) {
            setErrorState(ErrorState.CLOSE_NOW, e);
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            // 500 - Internal Server Error
            response.setStatus(500);
            setErrorState(ErrorState.CLOSE_NOW, t);
            log.error(sm.getString("http11processor.request.process"), t);
            getAdapter().log(request, response, 0);
        }

        rp.setStage(org.apache.coyote.Constants.STAGE_ENDED);

        if (getErrorState().isError() || status==SocketStatus.STOP) {
            return SocketState.CLOSED;
        } else if (!comet) {
            if (keepAlive) {
                inputBuffer.nextRequest();
                outputBuffer.nextRequest();
                return SocketState.OPEN;
            } else {
                return SocketState.CLOSED;
            }
        } else {
            return SocketState.LONG;
        }
    
	}

	@Override
	protected void registerForEvent(boolean read, boolean write) {
	}

	@Override
	protected Log getLog() {
		return log;
	}
}
