/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure;

import static nginx.clojure.MiniConstants.DEFAULT_ENCODING;
import static nginx.clojure.MiniConstants.NGX_OK;
import static nginx.clojure.NginxClojureRT.log;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

import nginx.clojure.java.NginxJavaResponse;
import nginx.clojure.net.NginxClojureAsynSocket;
import sun.nio.ch.DirectBuffer;

public class NginxHttpServerChannel implements Closeable {

	protected NginxRequest request;
	protected boolean ignoreFilter;
	protected volatile boolean closed;
	protected Object context;
	protected long asyncTimeout;
	protected final Object closeLock = new Object[0];

	private static final ChannelListener<NginxHttpServerChannel> closeListener = new ChannelCloseAdapter<NginxHttpServerChannel>() {
		@Override
		public void onClose(NginxHttpServerChannel sc) {
			synchronized (sc.closeLock) {
				if (!sc.closed) {
					sc.request.uri();//cache uri for logging usage otherwise we can not get uri from a released request
					sc.closed = true;
				}
			}
		}
	};

	public NginxHttpServerChannel(NginxRequest request, boolean ignoreFilter) {
		this.request = request;
		this.ignoreFilter = ignoreFilter;
		request.addListener(this, closeListener);
	}

	public <T> void addListener(T data, ChannelListener<T> listener) {
		this.request.addListener(data, listener);
	}

	/**
	 * turn on event handler
	 * @throws IOException
	 */
	public void turnOnEventHandler(boolean read, boolean write, boolean nokeepalive) throws IOException {
		checkValid();
		int flag = 0;
		if (read) {
			flag |= MiniConstants.NGX_HTTP_CLOJURE_EVENT_HANDLER_FLAG_READ;
		}
		if (write) {
			flag |= MiniConstants.NGX_HTTP_CLOJURE_EVENT_HANDLER_FLAG_WRITE;
		}
		if (nokeepalive) {
			flag |= MiniConstants.NGX_HTTP_CLOJURE_EVENT_HANDLER_FLAG_NOKEEPALIVE;
		}
		if (Thread.currentThread() != NginxClojureRT.NGINX_MAIN_THREAD) {
			final int fflag = flag;
			NginxClojureRT.postPollTaskEvent(() -> NginxClojureRT.ngx_http_hijack_turn_on_event_handler(request.nativeRequest(), fflag));
		}else {
			NginxClojureRT.ngx_http_hijack_turn_on_event_handler(request.nativeRequest(), flag);
		}
	}


	protected int send(byte[] message, long off, int len, int flag) {
		if (message == null) {
			return (int)NginxClojureRT.ngx_http_hijack_send(request.nativeRequest(), null, 0, 0, flag);
		}
		return (int)NginxClojureRT.ngx_http_hijack_send(request.nativeRequest(), message, MiniConstants.BYTE_ARRAY_OFFSET + off, len, flag);
	}

	protected int send(ByteBuffer message, int flag) {
		if (message == null) {
			return (int) NginxClojureRT.ngx_http_hijack_send(request.nativeRequest(), null, 0, 0, flag);
		}
		int rc = (int) (message.isDirect() ?
				NginxClojureRT.ngx_http_hijack_send(request.nativeRequest(), null,
						((DirectBuffer) message).address() + message.position(), message.remaining(), flag) :
				NginxClojureRT.ngx_http_hijack_send(request.nativeRequest(), message.array(),
						MiniConstants.BYTE_ARRAY_OFFSET + message.arrayOffset()+message.position(), message.remaining(), flag));

		if (rc == MiniConstants.NGX_OK) {
			message.position(message.limit());
		}
		return rc;
	}

	private final void checkValid() throws IOException {
		if (closed) {
			throw new IOException("Op on a closed NginxHttpServerChannel with request :" + request);
		}
	}

	/**
	 * If message is null when flush is true it will do flush, when last is true it will close channel.
	 */
	public void send(byte[] message, int off, int len, boolean flush, boolean last) throws IOException {
		checkValid();
		if (last) {
			closed = true;
		}
		int flag = computeFlag(flush, last);
		if (Thread.currentThread() != NginxClojureRT.NGINX_MAIN_THREAD) {
			NginxClojureRT.postHijackSendEvent(this, message == null ? null : Arrays.copyOfRange(message, off, off + len), 0,
					len, flag);
		}else {
			send(message, off, len, flag);
		}
	}

	public int computeFlag(boolean flush, boolean last) {
		int flag = 0;
		if (flush) {
			flag |= MiniConstants.NGX_CLOJURE_BUF_FLUSH_FLAG;
		}
		if (last) {
			flag |= MiniConstants.NGX_CLOJURE_BUF_LAST_FLAG;
		}
		if (ignoreFilter) {
			flag |= MiniConstants.NGX_CLOJURE_BUF_IGNORE_FILTER_FLAG;
		}
		return flag;
	}

	public void flush() throws IOException {
		checkValid();
		int flag = computeFlag(true, false);
		if (Thread.currentThread() != NginxClojureRT.NGINX_MAIN_THREAD) {
			NginxClojureRT.postHijackSendEvent(this, null, 0, 0, flag);
		}else {
			send(null, 0, 0, flag);
		}
	}

	public void send(String message, boolean flush, boolean last) throws IOException {
		checkValid();
		if (last) {
			closed = true;
		}
		if (log.isDebugEnabled()) {
			log.debug("#%s: send message : '%s', len=%s, flush=%s, last=%s, lns=%s", request.nativeRequest(),
					HackUtils.truncateToDotAppendString(message, 10), message == null ? "<NULL>" : message.length(), flush, last,
					request.listeners() == null ? 0 : request.listeners().size());
		}
		byte[] bs = message == null ? null : message.getBytes(DEFAULT_ENCODING);
		int flag = computeFlag(flush, last) | MiniConstants.NGX_CLOJURE_BUF_APP_MSGTXT;
		if (Thread.currentThread() != NginxClojureRT.NGINX_MAIN_THREAD) {
			NginxClojureRT.postHijackSendEvent(this, bs, 0, bs == null ? 0 : bs.length, flag);
		}else {
			send(bs, 0, bs == null ? 0 : bs.length, flag);
		}
	}

	public void send(ByteBuffer message, boolean flush, boolean last) throws IOException {
		checkValid();
		if (last) {
			closed = true;
		}
		if (log.isDebugEnabled()) {
			log.debug("#%s: send message : '%s', flush=%s, last=%s, lns=%s", request.nativeRequest(), message, flush, last, request.listeners() == null ? 0 : request.listeners().size());
		}
		int flag = computeFlag(flush, last);
		if (Thread.currentThread() != NginxClojureRT.NGINX_MAIN_THREAD) {
			if (message != null) {
				ByteBuffer cm = ByteBuffer.allocate(message.remaining());
				cm.put(message);
				cm.flip();
				NginxClojureRT.postHijackSendEvent(this, cm, 0, cm.remaining(), flag);
			}else {
				NginxClojureRT.postHijackSendEvent(this, null, 0, 0, flag);
			}
		}else {
			send(message, flag);
		}
	}

	public long read(ByteBuffer buf) throws IOException {
		long rc = 0;

		if (Thread.currentThread() != NginxClojureRT.NGINX_MAIN_THREAD) {
			synchronized (closeLock) {
				if (closed) {
					throw new IOException("Op on a closed NginxHttpServerChannel with request :" + request);
				}
				if (buf.isDirect()) {
					rc = NginxClojureRT.ngx_http_hijack_read(request.nativeRequest(), null,
							((DirectBuffer) buf).address() + buf.position(), buf.remaining());
				} else {
					rc = NginxClojureRT.ngx_http_hijack_read(request.nativeRequest(), buf.array(), MiniConstants.BYTE_ARRAY_OFFSET
							+ buf.arrayOffset() + buf.position(), buf.remaining());
				}
			}
		}else {
			if (closed) {
				throw new IOException("Op on a closed NginxHttpServerChannel with request :" + request);
			}
			if (buf.isDirect()) {
				rc = NginxClojureRT.ngx_http_hijack_read(request.nativeRequest(), null,
						((DirectBuffer) buf).address() + buf.position(), buf.remaining());
			} else {
				rc = NginxClojureRT.ngx_http_hijack_read(request.nativeRequest(), buf.array(), MiniConstants.BYTE_ARRAY_OFFSET
						+ buf.arrayOffset() + buf.position(), buf.remaining());
			}
		}

		if (NginxClojureRT.log.isDebugEnabled()) {
			NginxClojureRT.log.debug("NginxHttpServerChannel read rc=%d", rc);
		}

		if (rc == NginxClojureAsynSocket.NGX_HTTP_CLOJURE_SOCKET_ERR_AGAIN) {
			return 0;
		}else if (rc == 0) {
			return -1;
		}else if (rc < 0) {
			throw new IOException(NginxClojureAsynSocket.errorCodeToString(rc));
		}else {
			buf.position(buf.position() + (int)rc);
		}

		return rc;
	}

	public long read(byte[] buf, long off, long size) throws IOException {
		long rc;

		if (Thread.currentThread() != NginxClojureRT.NGINX_MAIN_THREAD) {
			synchronized (closeLock) {
				if (closed) {
					throw new IOException("Op on a closed NginxHttpServerChannel with request :" + request);
				}
				rc = NginxClojureRT.ngx_http_hijack_read(request.nativeRequest(), buf, MiniConstants.BYTE_ARRAY_OFFSET + off, size);
			}
		}else {
			if (closed) {
				throw new IOException("Op on a closed NginxHttpServerChannel with request :" + request);
			}
			rc = NginxClojureRT.ngx_http_hijack_read(request.nativeRequest(), buf, MiniConstants.BYTE_ARRAY_OFFSET + off, size);
		}

		if (NginxClojureRT.log.isDebugEnabled()) {
			NginxClojureRT.log.debug("NginxHttpServerChannel read rc=%d", rc);
		}
		if (rc == NginxClojureAsynSocket.NGX_HTTP_CLOJURE_SOCKET_ERR_AGAIN) {
			return 0;
		}else if (rc == 0) {
			return -1;
		}else if (rc < 0) {
			throw new IOException(NginxClojureAsynSocket.errorCodeToString(rc));
		}
		return rc;
	}

	protected long unsafeWrite(byte[] buf, long off, long size) {
		return NginxClojureRT.ngx_http_hijack_write(request.nativeRequest(), buf, MiniConstants.BYTE_ARRAY_OFFSET + off, size);
	}

	protected long unsafeWrite(ByteBuffer buf) {
		long rc;
		if (buf.isDirect()) {
			rc = NginxClojureRT.ngx_http_hijack_write(request.nativeRequest(), null,
					((DirectBuffer) buf).address() + buf.position(), buf.remaining());
		}else {
			rc = NginxClojureRT.ngx_http_hijack_write(request.nativeRequest(), buf.array(),
					MiniConstants.BYTE_ARRAY_OFFSET + buf.arrayOffset() + buf.position(), buf.remaining());
		}

		if (rc > 0) {
			buf.position(buf.position() + (int)rc);
		}
		return rc;
	}

	public long write(byte[] buf, long off, int size) throws IOException {
		checkValid();
		long rc;

		if (Thread.currentThread() != NginxClojureRT.NGINX_MAIN_THREAD) {
			rc = NginxClojureRT.postHijackWriteEvent(this, buf, off, size);
		}else {
			rc = unsafeWrite(buf, off, size);
		}

		if (NginxClojureRT.log.isDebugEnabled()) {
			NginxClojureRT.log.debug("NginxHttpServerChannel write rc=%d", rc);
		}

		if (rc == NginxClojureAsynSocket.NGX_HTTP_CLOJURE_SOCKET_ERR_AGAIN) {
			return 0;
		}else if (rc == 0) {
			return -1;
		}else if (rc < 0) {
			throw new IOException(NginxClojureAsynSocket.errorCodeToString(rc));
		}

		return (int)rc;
	}

	public long write(ByteBuffer buf) throws IOException {
		checkValid();
		long rc;

		if (Thread.currentThread() != NginxClojureRT.NGINX_MAIN_THREAD) {
			rc = NginxClojureRT.postHijackWriteEvent(this, buf, 0, buf.remaining());
		}else {
			rc = unsafeWrite(buf);
		}

		if (NginxClojureRT.log.isDebugEnabled()) {
			NginxClojureRT.log.debug("NginxHttpServerChannel write rc=%d", rc);
		}

		if (rc == NginxClojureAsynSocket.NGX_HTTP_CLOJURE_SOCKET_ERR_AGAIN) {
			return 0;
		}else if (rc == 0) {
			return -1;
		}else if (rc < 0) {
			throw new IOException(NginxClojureAsynSocket.errorCodeToString(rc));
		}
		return rc;
	}

	protected void sendHeader(int flag) {
		NginxClojureRT.ngx_http_hijack_send_header(request.nativeRequest(), flag);
	}

	protected int sendHeader(byte[] message, long off, int len, int flag) {
		int rc = (int)NginxClojureRT.ngx_http_hijack_send_header(request.nativeRequest(), message, MiniConstants.BYTE_ARRAY_OFFSET + off, len, flag);
		if (rc < 0) {
			NginxClojureRT.log.error("bad header from server : %s", new String(message));
		}
		return rc;
	}

	protected int sendHeader(ByteBuffer message, int flag) {
		int rc = 0;
		if (message.isDirect()) {
			rc = (int) NginxClojureRT.ngx_http_hijack_send_header(request.nativeRequest(), null,
					((DirectBuffer) message).address() + message.position(), message.remaining(), flag);
		} else {
			rc = (int) NginxClojureRT.ngx_http_hijack_send_header(request.nativeRequest(), message.array(),
					MiniConstants.BYTE_ARRAY_OFFSET + message.arrayOffset()+message.position(), message.remaining(), flag);
		}
		if (rc == MiniConstants.NGX_OK) {
			message.position(message.limit());
		}else if (rc < 0) {
			NginxClojureRT.log.error("bad header from server : %s", HackUtils.decode(message, DEFAULT_ENCODING, NginxClojureRT.pickCharBuffer()));
		}
		return rc;
	}

	public <K, V> void sendHeader(long status, Collection<Map.Entry<K, V>> headers, boolean flush, boolean last) throws IOException {
		checkValid();
		if (last) {
			closed = true;
		}
		int flag = computeFlag(flush, last);
		request.handler().prepareHeaders(request, status, headers);
		if (Thread.currentThread() != NginxClojureRT.NGINX_MAIN_THREAD) {
			NginxClojureRT.postHijackSendHeaderEvent(this, flag);
		} else {
			sendHeader(flag);
		}
	}

	public void sendHeader(byte[] buf, int pos, int len, boolean flush, boolean last) throws IOException {
		checkValid();
		if (last) {
			closed = true;
		}
		int flag = computeFlag(flush, last);
		if (Thread.currentThread() != NginxClojureRT.NGINX_MAIN_THREAD) {
			NginxClojureRT.postHijackSendHeaderEvent(this, buf, pos, len, flag);
		} else {
			sendHeader(buf, pos, len, flag);
		}
	}


	protected long sendResponseHelp(NginxResponse resp, long chain) {

		NginxRequest req = resp.request();
		if (req.isReleased()) {
			if (resp.type()  >  0) {
				log.error("#%d: request is release! and we also meet an unhandled exception! %s",  req.nativeRequest(), resp.fetchBody());
			}else {
				log.error("#%d: request is release! ", req.nativeRequest());
			}
			return MiniConstants.NGX_HTTP_INTERNAL_SERVER_ERROR;
		}

		req.applyDelayed();
		
		long rc = NGX_OK;
		long r = req.nativeRequest();

		if (resp.type() == NginxResponse.TYPE_FAKE_PHASE_DONE) {
			if (req.phase() == MiniConstants.NGX_HTTP_HEADER_FILTER_PHASE) {
				rc = NginxClojureRT.ngx_http_filter_continue_next(r, -1, 0);
				NginxClojureRT.ngx_http_finalize_request(r, rc);
				return NGX_OK;
			}
//			if (req.isHijacked()) {
//				//decrease r->count
//				NginxClojureRT.ngx_http_finalize_request(r, rc);
//			}
			NginxClojureRT.ngx_http_clojure_mem_continue_current_phase(r, MiniConstants.NGX_DECLINED);
			return NGX_OK;
		}

		int phase = req.phase();
		long nr = req.nativeRequest();
		if (chain < 0) {
			req.handler().prepareHeaders(req, -(int)chain, resp.fetchHeaders());
			rc = -chain;
		}else if (chain == 0) {
			rc = MiniConstants.NGX_HTTP_INTERNAL_SERVER_ERROR;
		} else {
			int status = resp.fetchStatus(MiniConstants.NGX_HTTP_OK);
			if (phase == MiniConstants.NGX_HTTP_HEADER_FILTER_PHASE) {
				NginxClojureRT.ngx_http_clear_header_and_reset_ctx_phase(nr, ~phase);
			}
			req.handler().prepareHeaders(req, status, resp.fetchHeaders());
			rc = NginxClojureRT.ngx_http_hijack_send_header(r, computeFlag(false, false));
			if (rc != MiniConstants.NGX_ERROR && rc <= NGX_OK) {
				//close will be done by handleReturnCodeFromHandler, so we do not need pass close flag
				rc = NginxClojureRT.ngx_http_hijack_send_chain(r, chain, computeFlag(true, false));
				if (rc == NGX_OK && phase != -1) {
					NginxClojureRT.ngx_http_ignore_next_response(nr);
				}
				if (phase != -1) {
					if (phase == MiniConstants.NGX_HTTP_ACCESS_PHASE || phase == MiniConstants.NGX_HTTP_REWRITE_PHASE ) {
						rc = NginxClojureRT.handleReturnCodeFromHandler(nr, phase, rc, status);
					}else {
						NginxClojureRT.handleReturnCodeFromHandler(nr, phase, rc, status);
					}
				}
			}
		}

		if (phase == -1 || phase == MiniConstants.NGX_HTTP_HEADER_FILTER_PHASE) {
			NginxClojureRT.ngx_http_finalize_request(r, rc);
		}else if (rc != MiniConstants.NGX_DONE){
			NginxClojureRT.ngx_http_clojure_mem_continue_current_phase(r,  rc);
		}
		return NGX_OK;
	}

	public void sendResponse(Object resp) throws IOException {
		checkValid();
		NginxResponse response = request.handler().toNginxResponse(request, resp);
		if (Thread.currentThread() != NginxClojureRT.NGINX_MAIN_THREAD) {
			NginxClojureRT.postHijackSendResponseEvent(this, response, request.handler().buildOutputChain(response));
		}else {
			sendResponseHelp(response, request.handler().buildOutputChain(response));
		}
	}

	public void sendBody(final Object body, boolean last) throws IOException {
		checkValid();

		if (last) {
			closed = true;
		}
		NginxResponse tmpResp = new NginxSimpleResponse(request) {
			@Override
			public Object fetchBody() {
				return body;
			}

			@Override
			public <K, V> Collection<Entry<K, V>> fetchHeaders() {
				return Collections.emptyList();
			}

			@Override
			public int fetchStatus(int defaultStatus) {
				return 200;
			}
		};
		long chain = ((NginxSimpleHandler)request.handler()).buildResponseItemBuf(request.nativeRequest(), body, 0);
		if (Thread.currentThread() != NginxClojureRT.NGINX_MAIN_THREAD) {
			NginxClojureRT.postHijackSendResponseEvent(this, tmpResp, chain);
		}else {
			NginxClojureRT.ngx_http_hijack_send_chain(request.nativeRequest(), chain, computeFlag(false, last));
		}
	}

	public void sendResponse(int status) throws IOException {
		checkValid();
		if (Thread.currentThread() != NginxClojureRT.NGINX_MAIN_THREAD) {
			NginxResponse response = new NginxJavaResponse(request, new Object[]{status, null, null});
			NginxClojureRT.postHijackSendResponseEvent(this, response, request.handler().buildOutputChain(response));
		}else {
			closed = true;
			NginxClojureRT.ngx_http_finalize_request(request.nativeRequest(), status);
		}
	}

	public void close() throws IOException {
		int flag = computeFlag(false, true);
		if (Thread.currentThread() != NginxClojureRT.NGINX_MAIN_THREAD) {
			synchronized (closeLock) {
				if (closed) {
					return;
				}
				closed = true;
			}
			NginxClojureRT.postHijackSendEvent(this, null, 0,
					0, flag);
		}else {
			if (closed) {
				return;
			}
			closed = true;
			send(null, 0, 0, flag);
		}
	}

	public void tagClose() {
		closed = true;
	}

	public boolean isIgnoreFilter() {
		return ignoreFilter;
	}

	public void setIgnoreFilter(boolean ignoreFilter) {
		this.ignoreFilter = ignoreFilter;
	}

	public NginxRequest request() {
		return request;
	}

	public boolean isClosed() {
		return closed;
	}

	public Object getContext() {
		return context;
	}

	public void setContext(Object context) {
		this.context = context;
	}

	public long getAsyncTimeout() {
		return asyncTimeout;
	}

	public void setAsyncTimeout(final long asyncTimeout) throws IOException {
		checkValid();
		this.asyncTimeout = asyncTimeout;

		if (Thread.currentThread() != NginxClojureRT.NGINX_MAIN_THREAD) {
			NginxClojureRT.postPollTaskEvent(() -> NginxClojureRT.ngx_http_hijack_set_async_timeout(request.nativeRequest(), asyncTimeout));
		}else {
			NginxClojureRT.ngx_http_hijack_set_async_timeout(request.nativeRequest(), asyncTimeout);
		}
	}

	public boolean webSocketUpgrade(final boolean sendErrorForNonWebSocket) {
		if (Thread.currentThread() != NginxClojureRT.NGINX_MAIN_THREAD) {
			FutureTask<Boolean> task = new FutureTask<>(() -> NginxClojureRT.ngx_http_clojure_websocket_upgrade(request.nativeRequest(), sendErrorForNonWebSocket ? 1 : 0) == 0);
			NginxClojureRT.postPollTaskEvent(task);
			try {
				return task.get();
			} catch (InterruptedException e) {
				throw new RuntimeException("webSocketUpgrade  Interrupted", e);
			} catch (ExecutionException e) {
				throw new RuntimeException("webSocketUpgrade  Execution error", e.getCause());
			}
		}else {
			return NginxClojureRT.ngx_http_clojure_websocket_upgrade(request.nativeRequest(), sendErrorForNonWebSocket ? 1 : 0) == 0;
		}
	}
}
