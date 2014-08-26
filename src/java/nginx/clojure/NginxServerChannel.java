package nginx.clojure;

import static nginx.clojure.MiniConstants.*;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import sun.nio.ch.DirectBuffer;

public class NginxServerChannel implements ChannelListener<NginxServerChannel> {
	
	protected NginxRequest request;
	protected boolean ignoreFilter;
	protected boolean closed;
	
	public NginxServerChannel(NginxRequest request, boolean ignoreFilter) {
		this.request = request;
		this.ignoreFilter = ignoreFilter;
	}
	
	public <T> void addListener(ChannelListener<T> listener, T data) {
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
			message.clear();
		}
		return rc;
	}
	
	/**
	 * If message is null when flush is true it will do flush, when last is true it will close channel.
	 */
	public void send(byte[] message, int off, int len, boolean flush, boolean last) {
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
		byte[] bs = message.getBytes(DEFAULT_ENCODING);
		int flag = computeFlag(flush, last);
		if (Thread.currentThread() != NginxClojureRT.NGINX_MAIN_THREAD) {
			NginxClojureRT.postHijackSendEvent(this, bs, 0, bs.length, flag);
		}else {
			send(bs, 0, bs.length, flag);
		}
	}
	
	public void send(ByteBuffer message, boolean flush, boolean last) {
		int flag = computeFlag(flush, last);
		if (Thread.currentThread() != NginxClojureRT.NGINX_MAIN_THREAD) {
			ByteBuffer cm = ByteBuffer.allocate(message.remaining());
			cm.put(message);
			NginxClojureRT.postHijackSendEvent(this, cm, 0, cm.remaining(), flag);
		}else {
			send(message, flag);
		}
	}
	
	public <K, V> void sendHeader(long status, Collection<Map.Entry<K, V>> headers, boolean flush, boolean last) {
		int flag = computeFlag(flush, last);
		request.handler().prepareHeaders(request, status, headers);
		if (Thread.currentThread() != NginxClojureRT.NGINX_MAIN_THREAD) {
			NginxClojureRT.postHijackSendHeaderEvent(this, flag);
			return;
		}else {
			NginxClojureRT.ngx_http_hijack_send_header(request.nativeRequest(), flag);
		}
	}
	
	public void sendResponse(Object resp) {
		NginxResponse response = request.handler().toNginxResponse(request, resp);
		if (Thread.currentThread() != NginxClojureRT.NGINX_MAIN_THREAD) {
			NginxClojureRT.postHijackSendResponseEvent(this, response, request.handler().buildOutputChain(response), computeFlag(false, true));
		}else {
			request.handler().prepareHeaders(request,response.fetchStatus(NGX_OK) , response.fetchHeaders());
			int rc = (int) NginxClojureRT.ngx_http_hijack_send_header(request.nativeRequest(), computeFlag(false, false));
			if (rc == NGX_OK || rc == NGX_AGAIN) {
				NginxClojureRT.ngx_http_hijack_send_chain(request.nativeRequest(), request.handler().buildOutputChain(response), computeFlag(false, true));
			}
		}
	}
	
	public void close() {
		if (!closed) {
			closed = true;
			send(null, 0, 0, false, true);
		}
	}
	
	public boolean isIgnoreFilter() {
		return ignoreFilter;
	}
	
	public NginxRequest request() {
		return request;
	}

	@Override
	public void onClose(NginxServerChannel data) {
		this.closed = true;
	}
	
}
