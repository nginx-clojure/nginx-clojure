/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 *For reuse some classes from tomcat8 we have to use this package
 */
package org.apache.coyote.http11;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Iterator;

import javax.servlet.http.HttpUpgradeHandler;

import nginx.clojure.NginxClojureRT;
import nginx.clojure.NginxHttpServerChannel;

import org.apache.coyote.AbstractProtocol;
import org.apache.coyote.Processor;
import org.apache.coyote.http11.NginxEndpoint.Handler;
import org.apache.coyote.http11.upgrade.NginxUpgradeProcessor;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.net.SocketStatus;
import org.apache.tomcat.util.net.SocketWrapper;

public class NginxDirectProtocol extends AbstractHttp11JsseProtocol<NginxChannel> {

	private static final Log log = LogFactory.getLog(NginxDirectProtocol.class);
	
    private final Http11ConnectionHandler cHandler;

    public NginxDirectProtocol() {
    	endpoint = new NginxEndpoint();
    	endpoint.setMaxConnections(-1);
    	cHandler = new Http11ConnectionHandler(this);
    	((NginxEndpoint)endpoint).setHandler(cHandler);
    	setSoLinger(Constants.DEFAULT_CONNECTION_LINGER);
        setSoTimeout(Constants.DEFAULT_CONNECTION_TIMEOUT);
        setTcpNoDelay(Constants.DEFAULT_TCP_NO_DELAY);
    }
    
    public NginxEndpoint getEndpoint() {
        return ((NginxEndpoint)endpoint);
    }
    
	@Override
	protected Log getLog() {
		return log;
	}

	@Override
	protected String getNamePrefix() {
		return "http-nginx";
	}

	@Override
	protected Handler getHandler() {
		return cHandler;
	}

	protected static class Http11ConnectionHandler extends
			AbstractConnectionHandler<NginxChannel, NginxTomcatHttp11Processor> implements
			Handler {

		protected NginxDirectProtocol proto;

		Http11ConnectionHandler(NginxDirectProtocol proto) {
			this.proto = proto;
		}

		@Override
		protected AbstractProtocol<NginxChannel> getProtocol() {
			return proto;
		}

		@Override
		protected Log getLog() {
			return log;
		}

		/**
		 * Expected to be used by the Poller to release resources on socket
		 * close, errors etc.
		 */
		@Override
		public void release(NginxHttpServerChannel socket) {
			if (log.isDebugEnabled())
				log.debug("Iterating through our connections to release a socket channel:"
						+ socket);
			boolean released = false;
			Iterator<java.util.Map.Entry<NginxChannel, Processor<NginxChannel>>> it = connections
					.entrySet().iterator();
			while (it.hasNext()) {
				java.util.Map.Entry<NginxChannel, Processor<NginxChannel>> entry = it
						.next();
				if (entry.getKey().getIOChannel() == socket) {
					it.remove();
					Processor<NginxChannel> result = entry.getValue();
					result.recycle(true);
					unregister(result);
					released = true;
					break;
				}
			}
			if (log.isDebugEnabled())
				log.debug("Done iterating through our connections to release a socket channel:"
						+ socket + " released:" + released);
		}

		/**
		 * Expected to be used by the Poller to release resources on socket
		 * close, errors etc.
		 */
		@Override
		public void release(SocketWrapper<NginxChannel> socket) {
			Processor<NginxChannel> processor = connections.remove(socket
					.getSocket());
			if (processor != null) {
				processor.recycle(true);
				recycledProcessors.push(processor);
			}
		}

		@Override
		public SocketState process(SocketWrapper<NginxChannel> socket,
				SocketStatus status) {
			//tomcat 8.0.23 removed proto.npnHandler and nginx will do SPDY things so we do not need it  
//			if (proto.npnHandler != null) {
//				SocketState ss = proto.npnHandler.process(socket, status);
//				if (ss != SocketState.OPEN) {
//					return ss;
//				}
//			}
			return super.process(socket, status);
		}

		/**
		 * Expected to be used by the handler once the processor is no longer
		 * required.
		 * 
		 * @param socket
		 * @param processor
		 * @param isSocketClosing
		 *            Not used in HTTP
		 * @param addToPoller
		 */
		@Override
		public void release(SocketWrapper<NginxChannel> socket,
				Processor<NginxChannel> processor, boolean isSocketClosing,
				boolean addToPoller) {
			processor.recycle(isSocketClosing);
			recycledProcessors.push(processor);
//			if (addToPoller) {
////				socket.getSocket().getPoller().add(socket.getSocket());
//				throw new UnsupportedOperationException("addToPoller = true is not supported at NginxDirectProtocol.release");
//			}
		}

		@Override
		protected void initSsl(SocketWrapper<NginxChannel> socket,
				Processor<NginxChannel> processor) {
		}

		@Override
		protected void longPoll(SocketWrapper<NginxChannel> socket,
				Processor<NginxChannel> processor) {

			if (processor.isAsync()) {
				socket.setAsync(true);
			} else {
				// Either:
				// - this is comet request
				// - this is an upgraded connection
				// - the request line/headers have not been completely
				// read
//				socket.getSocket().getPoller().add(socket.getSocket());
			}
		}

		@Override
		public NginxTomcatHttp11Processor createProcessor() {
			NginxTomcatHttp11Processor processor = new NginxTomcatHttp11Processor(
					proto.getMaxHttpHeaderSize(), (NginxEndpoint) proto.endpoint,
					proto.getMaxTrailerSize(), proto.getAllowedTrailerHeadersAsSet(),
					proto.getMaxExtensionSize(),proto.getMaxSwallowSize());
			proto.configureProcessor(processor);
			register(processor);
			return processor;
		}

		@Override
		protected Processor<NginxChannel> createUpgradeProcessor(
				SocketWrapper<NginxChannel> socket, ByteBuffer leftoverInput,
				HttpUpgradeHandler httpUpgradeProcessor) throws IOException {
			NginxUpgradeProcessor processor = new NginxUpgradeProcessor(socket, leftoverInput, httpUpgradeProcessor);
			if (socket.getSocket().ignoreNginxFilter) {
				socket.getSocket().getIOChannel().turnOnEventHandler(true, true, true);
			}
			socket.getSocket().getIOChannel().addListener(socket, (NginxEndpoint)proto.endpoint);
			return processor;
		}

	}
}
