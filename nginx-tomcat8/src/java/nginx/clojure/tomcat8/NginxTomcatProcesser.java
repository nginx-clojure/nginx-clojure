/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.tomcat8;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.servlet.ReadListener;
import javax.servlet.http.HttpUpgradeHandler;

import nginx.clojure.ChannelCloseAdapter;
import nginx.clojure.ChannelListener;
import nginx.clojure.MiniConstants;
import nginx.clojure.NginxClojureRT;
import nginx.clojure.NginxHttpServerChannel;
import nginx.clojure.java.Constants;
import nginx.clojure.java.NginxJavaRequest;

import org.apache.catalina.Globals;
import org.apache.catalina.connector.CoyoteAdapter;
import org.apache.coyote.ActionCode;
import org.apache.coyote.ActionHook;
import org.apache.coyote.Adapter;
import org.apache.coyote.AsyncContextCallback;
import org.apache.coyote.InputBuffer;
import org.apache.coyote.OutputBuffer;
import org.apache.coyote.Request;
import org.apache.coyote.Response;
import org.apache.coyote.http11.upgrade.NginxClojureWebConnectionImp;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.MimeHeaders;
import org.apache.tomcat.util.net.SocketStatus;

public	class NginxTomcatProcesser implements Runnable, ActionHook {
	
	protected static final org.apache.juli.logging.Log log =
	        org.apache.juli.logging.LogFactory.getLog( NginxTomcatBridgeImpl.class );
		
		NginxHttpServerChannel sc;
		AsyncContextCallback asyncContextCallback;
		Adapter adapter;

		Response res;
		Request req;
		boolean isAsync;
		boolean isStarted;
		boolean isCompleting;
		boolean isDispatching;
		boolean pauseNonContainerThread;
		Throwable error;
		boolean headerSent = false;
		boolean closed = false;
		boolean isUpgrade = false;
		
		HttpUpgradeHandler upgradeHandler;
		
		
		public NginxTomcatProcesser(Adapter adapter, NginxJavaRequest req) {
			this.adapter = adapter;
			this.sc = req.handler().hijack(req, false);
		}
		
		public void commit() {
			sendHeaders();
			res.setCommitted(true);
		}

		public void sendHeaders() {
			if (headerSent) {
				return;
			}
			int statusCode = res.getStatus();
	        long contentLength = res.getContentLengthLong();
	        MimeHeaders headers = res.getMimeHeaders();
	        if (contentLength > 0) {
	            headers.setValue("Content-Length").setLong(contentLength);
	        }
	        if (res.getContentType() != null) {
	        	MessageBytes ct = headers.setValue("Content-Type");
	        	ct.setString(res.getContentType());
	        }
	        Map<String, Object> oheaders = new HashMap<String, Object>();
	        int size = headers.size();
	        for (int i = 0; i < size; i++) {
	        	oheaders.put(headers.getName(i).toString(), headers.getValue(i).toString());
	        }
			sc.sendHeader(statusCode, oheaders.entrySet(), false, false);
			headerSent = true;
		}
		
		public void sendfile(String file) {
			sc.sendBody(new File(file), false);
		}
		
		public void close() {
			if (closed) {
				return;
			}
			if (!res.isCommitted()) {
				commit();
			}
			if (req.getAttribute(Globals.SENDFILE_FILENAME_ATTR) != null) {
				String file = (String) req.getAttribute(Globals.SENDFILE_FILENAME_ATTR);
				sendfile(file);
			}
			sc.close();
			closed = true;
		}
		
		@Override
		public void action(ActionCode actionCode, Object param) {
			try {
				NginxClojureRT.log.debug("actionCode: " + actionCode + ", param:" + param + "\n\n");
				
				switch (actionCode) {
				case COMMIT: {
		            // Commit current response
		            if (res.isCommitted()) {
		                return;
		            }
		            commit();
		            break;
				}
				case CLIENT_FLUSH: {
					sc.send((String)null, true, false);
					break;
				}
				case CLOSE: {
					if (!isUpgrade) {
						close();
					}
					break;
				}
				case IS_ERROR : {
					((AtomicBoolean) param).set(error != null);
					break;
				}
				case ASYNC_START : {
					isAsync = isStarted = true;
					asyncContextCallback = (AsyncContextCallback) param;
					sc.addListener(asyncContextCallback, new ChannelCloseAdapter<AsyncContextCallback>() {
						@Override
						public void onClose(AsyncContextCallback data) {
							asyncContextCallback.fireOnComplete();
						}
					});
					break;
				}
				case ASYNC_IS_ASYNC : {
					((AtomicBoolean) param).set(isAsync);
					break;
				}
				case ASYNC_IS_STARTED : {
					((AtomicBoolean) param).set(isAsync && isStarted);
					break;
				}
				case ASYNC_IS_DISPATCHING: {
					((AtomicBoolean) param).set(isAsync && isDispatching);
					break;
				}
				case ASYNC_IS_COMPLETING : {
					((AtomicBoolean) param).set(isAsync && isCompleting);
					break;
				}
				case ASYNC_DISPATCH : {
					isDispatching = true;
					try {
						if (!adapter.asyncDispatch(req, res, SocketStatus.OPEN_READ) ) {
							res.setStatus(500);
							commit();
						}
					}catch(Throwable e) {
						 error = e;
					}

					break;
				}
				case ASYNC_DISPATCHED : {
					isAsync = isStarted = isCompleting = isDispatching = pauseNonContainerThread = false;
					break;
				}
				case ASYNC_RUN : {
					((Runnable)param).run();
					break;
				}
				case ASYNC_COMPLETE : {
					isCompleting = true;
					org.apache.catalina.connector.Response cres = (org.apache.catalina.connector.Response) res.getNote(CoyoteAdapter.ADAPTER_NOTES); 
//					cres.finishResponse();
					cres.flushBuffer();
					isAsync = isStarted = isCompleting = isDispatching = pauseNonContainerThread = false;
					sc.close();
					break;
				}
				case AVAILABLE : {
					NginxJavaRequest nreq = (NginxJavaRequest)sc.request();
					InputStream body = (InputStream) nreq.get(Constants.BODY);
					req.setAvailable(body == null ? 0 : body.available());
					break;
				}
				case REQUEST_BODY_FULLY_READ : {
					NginxJavaRequest nreq = (NginxJavaRequest)sc.request();
					InputStream body = (InputStream) nreq.get(Constants.BODY);
					((AtomicBoolean) param).set(body == null || body.available() == 0);
					break;
				}
				case NB_WRITE_INTEREST : {
					if (!res.isCommitted()) {
						((AtomicBoolean) param).set(true);
					}
					break;
				}
				case DISPATCH_READ : {
					//TODO: dispatch event in another event thread
					ReadListener listener = req.getReadListener();
					if (listener != null) {
						listener.onDataAvailable();
					}
					break;
				}
				case ASYNC_SETTIMEOUT : {
					sc.setAsyncTimeout((Long)param);
					break;
				}
				case REQ_HOST_ADDR_ATTRIBUTE : {
					if (closed) {
		                req.remoteAddr().recycle();
		            } else {
		            	NginxJavaRequest nreq = (NginxJavaRequest)sc.request();
		                req.remoteAddr().setString((String)nreq.get(MiniConstants.REMOTE_ADDR));
		            }
		            break;
				}
				case UPGRADE : {
					upgradeHandler = (HttpUpgradeHandler)param;
//					upgradeHandler.init(new WebConnectionImp(sc));
					isUpgrade = true;
					log.debug("we are upgrading to websocket");
					break;
				}
				default: {
					NginxClojureRT.log.debug("not support actionCode:" + actionCode + ", on object:" + param);
				}
				}
			}catch(Throwable e) {
				error = e;
				log.error(e.getMessage(), e);
			}
		}
	

		
		@Override
		public void run() {

			req = new Request();
			NginxJavaRequest nreq = (NginxJavaRequest)sc.request();
			req.setAvailable(1);
			req.setCharacterEncoding((String)nreq.get(Constants.CHARACTER_ENCODING));
			Map<String, Object> headers = (Map<String, Object>)nreq.get(Constants.HEADERS);
			String contentLength = (String)headers.get("content-length");
			if (contentLength != null) {
//				req.setContentLength(Long.parseLong(contentLength));
			}
			
			req.setLocalPort(Integer.parseInt((String)nreq.get(Constants.SERVER_PORT)));
			req.setServerPort(Integer.parseInt((String)nreq.get(Constants.SERVER_PORT)));
			req.protocol().setString((String)nreq.get(nreq.getVariable("server_protocol")));
			//TODO: optimize
			req.setStartTime(System.currentTimeMillis());
			req.requestURI().setString((String)nreq.get(Constants.URI));
			req.decodedURI().setString((String)nreq.get(Constants.URI));
			req.queryString().setString((String)nreq.get(Constants.QUERY_STRING));
			req.remoteAddr().setString((String)nreq.get(Constants.REMOTE_ADDR));
			req.remoteHost().setString((String)nreq.get(Constants.REMOTE_ADDR));
			req.method().setString(nreq.get(Constants.REQUEST_METHOD).toString().toUpperCase());
			req.protocol().setString(nreq.getVariable("server_protocol"));
			for (Entry<String, Object> en : headers.entrySet()) {
				if (en.getKey().equalsIgnoreCase("Cookie")) {
					byte[] cbs = ((String)en.getValue()).getBytes(MiniConstants.DEFAULT_ENCODING);
					req.getMimeHeaders().addValue(en.getKey()).setBytes(cbs, 0, cbs.length);
					continue;
				}
				req.getMimeHeaders().addValue(en.getKey()).setString(en.getValue().toString());
			}
			
			final InputStream body = (InputStream) nreq.get(Constants.BODY);
			
			req.setInputBuffer(new InputBuffer() {
				byte[] buf = new byte[4096];
				@Override
				public int doRead(ByteChunk chunk, Request request) throws IOException {
					if (body == null) {
						return -1;
					}
					int len = body.read(buf);
					if (len == 0) {
						return -1;
					}
					chunk.setBytes(buf, 0, len);
					return len;
				}
			});
			res = new Response();
			req.setResponse(res);
			res.setHook(this);
			res.setOutputBuffer(new OutputBuffer() {
				int byteCount = 0;
				@Override
				public long getBytesWritten() {
					return byteCount;
				}
				
				@Override
				public int doWrite(ByteChunk chunk, Response response)
						throws IOException {
					if (!headerSent) {
						sendHeaders();
					}
					int length = chunk.getLength();
					sc.send(chunk.getBuffer(), chunk.getStart(), length, false, false);
					byteCount += chunk.getLength();
					return length;
				}
			});
			

			try {
				adapter.service(req, res);
				if (isUpgrade) {
					upgradeHandler.init(new NginxClojureWebConnectionImp(sc));
					log.info("upgrade to websocket");
				}
				
				if (!isAsync && !closed && !isUpgrade) {
					close();
				}
			} catch (Exception e) {
				//TODO: handle exception
				throw new RuntimeException("can not handle request", e);
			}
		
			
		}
	}