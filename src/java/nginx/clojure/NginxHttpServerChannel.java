/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure;

import static nginx.clojure.MiniConstants.DEFAULT_ENCODING;
import static nginx.clojure.MiniConstants.NGX_AGAIN;
import static nginx.clojure.MiniConstants.NGX_OK;
import static nginx.clojure.NginxClojureRT.log;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;

import nginx.clojure.java.NginxJavaResponse;
import sun.nio.ch.DirectBuffer;

public class NginxHttpServerChannel implements ChannelListener<NginxHttpServerChannel> {
	
	protected NginxRequest request;
	protected boolean ignoreFilter;
	protected boolean closed;
	protected Object context;
	
	public NginxHttpServerChannel(NginxRequest request, boolean ignoreFilter) {
		this.request = request;
		this.ignoreFilter = ignoreFilter;
	}
	
	public <T> void addListener(T data, ChannelListener<T> listener) {
		NginxClojureRT.ngx_http_cleanup_add(request.nativeRequest(), listener, data);
	}
	
	protected int send(byte[] message, long off, int len, int flag) {
		if (len == 0) {
			return (int)NginxClojureRT.ngx_http_hijack_send(request.nativeRequest(), null, 0, 0, flag);
		}
		return (int)NginxClojureRT.ngx_http_hijack_send(request.nativeRequest(), message, MiniConstants.BYTE_ARRAY_OFFSET + off, len, flag);
	}
	
	protected int send(ByteBuffer message, int flag) {
		if (message == null) {
			return (int) NginxClojureRT.ngx_http_hijack_send(request.nativeRequest(), null, 0, 0, flag);
		}
		int rc = 0;
		if (message.isDirect()) {
			rc = (int) NginxClojureRT.ngx_http_hijack_send(request.nativeRequest(), null, 
					((DirectBuffer) message).address() + message.position(), message.remaining(), flag);
		} else {
			rc = (int) NginxClojureRT.ngx_http_hijack_send(request.nativeRequest(), message.array(), 
					MiniConstants.BYTE_ARRAY_OFFSET + message.arrayOffset()+message.position(), message.remaining(), flag);
		}
		if (rc == MiniConstants.NGX_OK) {
			message.position(message.limit());
		}
		return rc;
	}
	
	private void checkValid() {
		if (closed) {
			throw new IllegalStateException("Op on a closed NginxHttpServerChannel with request :" + request);
		}
	}
	
	/**
	 * If message is null when flush is true it will do flush, when last is true it will close channel.
	 */
	public void send(byte[] message, int off, int len, boolean flush, boolean last) {
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
	
	public void send(String message, boolean flush, boolean last) {
		checkValid();
		if (last) {
			closed = true;
		}
		if (log.isDebugEnabled()) {
			log.debug("#%s: send message : '%s', flush=%s, last=%s", NginxClojureRT.processId, message, flush, last);
		}
		byte[] bs = message == null ? null : message.getBytes(DEFAULT_ENCODING);
		int flag = computeFlag(flush, last);
		if (Thread.currentThread() != NginxClojureRT.NGINX_MAIN_THREAD) {
			NginxClojureRT.postHijackSendEvent(this, bs, 0, bs == null ? 0 : bs.length, flag);
		}else {
			send(bs, 0, bs == null ? 0 : bs.length, flag);
		}
	}
	
	public void send(ByteBuffer message, boolean flush, boolean last) {
		checkValid();
		if (last) {
			closed = true;
		}
		int flag = computeFlag(flush, last);
		if (Thread.currentThread() != NginxClojureRT.NGINX_MAIN_THREAD) {
			if (message != null) {
				ByteBuffer cm = ByteBuffer.allocate(message.remaining());
				cm.put(message);
				NginxClojureRT.postHijackSendEvent(this, cm, 0, cm.remaining(), flag);
			}else {
				NginxClojureRT.postHijackSendEvent(this, null, 0, 0, flag);
			}
		}else {
			send(message, flag);
		}
	}
	
	public <K, V> void sendHeader(long status, Collection<Map.Entry<K, V>> headers, boolean flush, boolean last) {
		checkValid();
		if (last) {
			closed = true;
		}
		int flag = computeFlag(flush, last);
		request.handler().prepareHeaders(request, status, headers);
		if (Thread.currentThread() != NginxClojureRT.NGINX_MAIN_THREAD) {
			NginxClojureRT.postHijackSendHeaderEvent(this, flag);
			return;
		}else {
			NginxClojureRT.ngx_http_hijack_send_header(request.nativeRequest(), flag);
		}
	}
	
	protected void sendResponseHelp(NginxResponse response, long chain) {
		closed = true;
		if (chain < 0) {
			int status = (int)-chain;
			request.handler().prepareHeaders(request, status, response.fetchHeaders());
			NginxClojureRT.ngx_http_finalize_request(request.nativeRequest(), status);
			return;
		}
		request.handler().prepareHeaders(request, response.fetchStatus(NGX_OK) , response.fetchHeaders());
		int rc = (int) NginxClojureRT.ngx_http_hijack_send_header(request.nativeRequest(), computeFlag(false, false));
		if (rc == NGX_OK || rc == NGX_AGAIN) {
			NginxClojureRT.ngx_http_hijack_send_chain(request.nativeRequest(), chain, computeFlag(false, true));
		}
	}
	
	public void sendResponse(Object resp) {
		checkValid();
		NginxResponse response = request.handler().toNginxResponse(request, resp);
		if (Thread.currentThread() != NginxClojureRT.NGINX_MAIN_THREAD) {
			NginxClojureRT.postHijackSendResponseEvent(this, response, request.handler().buildOutputChain(response));
		}else {
			sendResponseHelp(response, request.handler().buildOutputChain(response));
		}
	}
	
	public void sendBody(final Object body, boolean last) {
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
				return Collections.EMPTY_LIST;
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
	
	public void sendResponse(int status) {
		checkValid();
		if (Thread.currentThread() != NginxClojureRT.NGINX_MAIN_THREAD) {
			NginxResponse response = new NginxJavaResponse(request, new Object[]{status, null, null});
			NginxClojureRT.postHijackSendResponseEvent(this, response, request.handler().buildOutputChain(response));
		}else {
			NginxClojureRT.ngx_http_finalize_request(request.nativeRequest(), status);
		}
	}
	
	public void close() {
		if (!closed) {
			closed = true;
			if (Thread.currentThread() != NginxClojureRT.NGINX_MAIN_THREAD) {
				NginxClojureRT.postHijackSendEvent(this, null, 0,
						0, MiniConstants.NGX_CLOJURE_BUF_LAST_FLAG);
			}else {
				send(null, 0, 0, MiniConstants.NGX_CLOJURE_BUF_LAST_FLAG);
			}
		}
	}
	
	public boolean isIgnoreFilter() {
		return ignoreFilter;
	}
	
	public NginxRequest request() {
		return request;
	}

	@Override
	public void onClose(NginxHttpServerChannel data) {
		this.closed = true;
	}

	@Override
	public void onConnect(long status, NginxHttpServerChannel data) {
		//this method will never be called.
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
	
	
}
