/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure;

import static nginx.clojure.MiniConstants.DEFAULT_ENCODING;
import static nginx.clojure.MiniConstants.POST_EVENT_TYPE_APPICATION_EVENT_IDX_START;
import static nginx.clojure.NginxClojureRT.broadcastEvent;

import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

public class AppEventListenerManager  {
	
	private CopyOnWriteArrayList<Decoder> decorders = new CopyOnWriteArrayList<AppEventListenerManager.Decoder>();
	private CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<AppEventListenerManager.Listener>();
	private ConcurrentLinkedQueue<PostedEvent> pooledEvents = new ConcurrentLinkedQueue<AppEventListenerManager.PostedEvent>();

	
	public static class PostedEvent {
		public int tag;
		public Object data;
		public int offset;
		public int length;
		
		public PostedEvent() {
		}
		
		public PostedEvent(int tag, long event) {
			this.tag = (int)tag;
			this.data = event;
		}
		
		public PostedEvent(String message) {
			this(POST_EVENT_TYPE_APPICATION_EVENT_IDX_START, message);
		}
		
		public PostedEvent(int tag, String message) {
			this.tag = tag;
			data = message.getBytes(DEFAULT_ENCODING);
			offset = 0;
			length = ((byte[])data).length;
		}
		
		public PostedEvent(byte[] message) {
			tag = POST_EVENT_TYPE_APPICATION_EVENT_IDX_START;
			data = message;
			offset = 0;
			length = ((byte[])data).length;
		}
		
		public PostedEvent(int tag, byte[] message) {
			this(tag, message, 0, message.length);
		}
		
		public PostedEvent(int tag, Object message, int offset, int len) {
			this.tag = tag;
			this.data = message;
			this.offset = offset;
			this.length = len;
		}
		
		public void accept(int tag, Object data) {
			if (data instanceof String){
				accept(tag, (String)data);
			}else if (data instanceof byte[]){
				accept(tag, (byte[])data);
			}else {
				this.tag = tag;
				this.data = data;
			}
		}
		
		public void accept(int tag, String message) {
			this.tag = tag;
			data = message.getBytes(DEFAULT_ENCODING);
			offset = 0;
			length = ((byte[])data).length;
		}
		
		public void accept(int tag, byte[] message) {
			this.tag = tag;
			data = message;
			offset = 0;
			length = message.length;
		}
		
		public void accept(int tag, Object message, int offset, int len) {
			this.tag = tag;
			this.data = message;
			this.offset = offset;
			this.length = len;
		}
	}
	
	public static interface Decoder {
		public boolean shouldDecode(PostedEvent event);
		public PostedEvent decode(PostedEvent event);
	}
	
	public static interface Listener {
		/**
		 * Because event.data will be reused by next event so this listener must handle it carefully and
		 * do use it out of this invoking scope.
		 * */
		public void onEvent(PostedEvent event) throws IOException;
	}
	
	public void addDecoder(Decoder d) {
		decorders.add(d);
	}
	
	public boolean removeDecoder(Decoder d) {
		return decorders.remove(d);
	}
	
	public void addListener(Listener listener) {
		listeners.add(listener);
	}
	
	public boolean removeListener(Listener listener) {
		return listeners.remove(listener);
	}
	
	public void onBroadcastedEvent(PostedEvent ev) {
		for (Decoder d : decorders) {
			if (d.shouldDecode(ev)) {
				ev = d.decode(ev);
			}
		}
		for (Listener l : listeners) {
			try{
				l.onEvent(ev);
			}catch(Throwable e) {
				NginxClojureRT.log.error("onBroadcastedEvent error", e);
			}
		}
	}
	
	public void onBroadcastedEvent(int tag, long data) {
		PostedEvent e = buildPostedEvent(tag, (Long)data);
		onBroadcastedEvent(e);
	}
	
	public void onBroadcastedEvent(int tag, byte[] buf, int offset, int len) {
		PostedEvent e = new PostedEvent(tag, buf, offset, len);
		onBroadcastedEvent(e);
	}
	
	public void broadcast(PostedEvent e) {
		Object data = e.data;
		if (data instanceof Long) {
			long id = (Long) data;
			broadcastEvent(e.tag, id);
		}else if (data instanceof byte[]) {
			broadcastEvent(e.tag, (byte[])e.data, e.offset, e.length);
		}
	}
	
	/**
	 * this method is used by clojure to build posted event easier.
	 */
	public PostedEvent buildPostedEvent(Object otag, Object data) {
		int tag = otag == null ? POST_EVENT_TYPE_APPICATION_EVENT_IDX_START : (Integer)otag;
		PostedEvent e = pooledEvents.poll();
		if (e == null) {
			e = new PostedEvent();
		}
		e.accept(tag, data);
		return e;
	}
	
	public PostedEvent buildPostedEvent(int tag, Object data) {
		PostedEvent e = pooledEvents.poll();
		if (e == null) {
			e = new PostedEvent();
		}
		e.accept(tag, data);
		return e;
	}
	
	public void returnEvent(PostedEvent e) {
		e.data = null;
		e.length = e.offset = e.tag = 0;
		pooledEvents.add(e);
	}
}
