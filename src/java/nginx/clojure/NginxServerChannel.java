package nginx.clojure;

import static nginx.clojure.MiniConstants.DEFAULT_ENCODING;

import java.util.Collection;
import java.util.Map;

public class NginxServerChannel {
	
	protected NginxRequest request;
	protected boolean ignoreFilter;
	
	public NginxServerChannel(NginxRequest request, boolean ignoreFilter) {
		this.request = request;
		this.ignoreFilter = ignoreFilter;
	}
	
	public <T> void addListener(ChannelListener<T> listener, T data) {
		NginxClojureRT.ngx_http_cleanup_add(request.nativeRequest(), listener, data);
	}
	
	protected int send(byte[] message, int off, int len, int flag) {
		if (message == null) {
			return (int)NginxClojureRT.ngx_http_hijack_send(request.nativeRequest(), null, 0, 0, flag);
		}
		return (int)NginxClojureRT.ngx_http_hijack_send(request.nativeRequest(), message, MiniConstants.BYTE_ARRAY_OFFSET + off, len, flag);
	}
	
	public void send(byte[] message, int off, int len, boolean flush, boolean last) {
		int flag = computeFlag(flush, last);
		if (Thread.currentThread() != NginxClojureRT.NGINX_MAIN_THREAD) {
			NginxClojureRT.postHijackSendEvent(this, message, off, len, flag);
			return;
		}
		send(message, off, len, flag);
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
		if (message == null) {
			send(null, 0, 0, flush, last);
		}else {
			byte[] bs = message.getBytes(DEFAULT_ENCODING);
			send(bs, 0, bs.length, flush, last);
		}
	}
	
	public <K, V> void sendHeader(int status, Collection<Map.Entry<K, V>> headers, boolean flush, boolean last) {
		int flag = computeFlag(flush, last);
		request.handler().prepareHeaders(request, status, headers);
		if (Thread.currentThread() != NginxClojureRT.NGINX_MAIN_THREAD) {
			NginxClojureRT.postHijackSendHeaderEvent(this, flag);
			return;
		}
		NginxClojureRT.ngx_http_hijack_send_header(request.nativeRequest(), flag);
	}
	
	public void sendResponse(Object resp) {
		NginxResponse response = request.handler().toNginxResponse(request, resp);
		NginxClojureRT.postResponseEvent(request, response);
	}
	
	public boolean isIgnoreFilter() {
		return ignoreFilter;
	}
	
	public NginxRequest request() {
		return request;
	}
	
}
