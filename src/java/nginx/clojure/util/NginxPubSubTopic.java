/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.util;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import nginx.clojure.AppEventListenerManager.Listener;
import nginx.clojure.AppEventListenerManager.PostedEvent;
import nginx.clojure.MiniConstants;
import nginx.clojure.NginxClojureRT;

public class NginxPubSubTopic {

	protected String topic;
	protected long topicId;
	
	public static class PubSubListenerData<T> {
		public NginxPubSubListener<T> listener;
		public T data;

		public PubSubListenerData() {
		}

		public PubSubListenerData(NginxPubSubListener<T> listener, T data) {
			super();
			this.listener = listener;
			this.data = data;
		}
	}
	
	protected static ConcurrentHashMap<Long, Set<PubSubListenerData>> topicSubs = new ConcurrentHashMap<Long, Set<PubSubListenerData>>();
	
	public static final String PUBSUB_SHARED_MAP_NAME = "PubSubTopic";
	
	public static final String PUBSUB_TOPIC_ID_COUNTER = "PUBSUB_TOPIC_ID_COUNTER";
	public static final long PUBSUB_EVENT_ID_COUNTER = 0;
	private static final long PUBSUB_MAX_TOPIC_ID = Long.MAX_VALUE >> 10;
	
	protected static NginxSharedHashMap<Object, Object> sharedBox;
	
	
	private static void initSharedBox() {
		do {
			sharedBox = NginxSharedHashMap.build(PUBSUB_SHARED_MAP_NAME);
			if (sharedBox == null) {
				NginxClojureRT.getLog().error("can not find shared map '"+PUBSUB_SHARED_MAP_NAME+"' without which NginxPubSubTopic can't work!");
				break;
			}
			sharedBox.putIntIfAbsent(PUBSUB_EVENT_ID_COUNTER, 1);
			NginxClojureRT.getAppEventListenerManager().addListener(new Listener() {
				@Override
				public void onEvent(PostedEvent event) throws IOException {
					if (event.tag == MiniConstants.POST_EVENT_TYPE_PUB) {
						long id = (Long) event.data;
						long rid = id | 0x0100000000L;
						String message = (String) sharedBox.get(id);
						long topicId = sharedBox.getLong(rid) >> 10;
						
						if ((sharedBox.atomicAddLong(rid, -1) & 0x3ff) == 1) {
							// the message has been post to all nginx worker processes
							// so we can remove it from shared map now.
							sharedBox.delete(rid);
							sharedBox.delete(id);
						}
						Set<PubSubListenerData> pds = topicSubs.get(topicId);
						if (pds == null) {
							NginxClojureRT.getLog().debug("no sub found about topic %d", topicId);
						}else {
							for (PubSubListenerData pd : pds) {
								try {
									pd.listener.onMessage(message, pd.data);
								}catch(Throwable e) {
									NginxClojureRT.getLog().warn("error on pubsub event, message=%s", e);
								}
							}
						}
					}
				}
			});
		}while(false);
	}
	
	public NginxPubSubTopic(String topic) {
		
		if (sharedBox == null) {
			synchronized (topicSubs) {
				initSharedBox();
			}
		}
		this.topic = topic;
		if (sharedBox == null) {
			throw new RuntimeException("can not find shared map '"+PUBSUB_SHARED_MAP_NAME+"' without which NginxPubSubTopic can't work!");
		}
		Object topicIdObj = sharedBox.get(topic);
		if (topicIdObj == null) {
			if (sharedBox.putIfAbsent(PUBSUB_TOPIC_ID_COUNTER, 2L) == null) {
				topicId = 2L;
			}else {
				topicId = sharedBox.atomicAddLong(PUBSUB_TOPIC_ID_COUNTER, 1);
				if (topicId > PUBSUB_MAX_TOPIC_ID) {
					throw new RuntimeException("too many topics! nginx-clojure can not support > " + PUBSUB_MAX_TOPIC_ID + " topics");
				}
			}
			topicIdObj = sharedBox.putIfAbsent(topic, topicId);
			if (topicIdObj != null) {
				topicId = (Long) topicIdObj;
			}
		}else {
			topicId = (Long) topicIdObj;
		}
	}
	
	public void pubish(String message) {
		long id = sharedBox.atomicAddInt(PUBSUB_EVENT_ID_COUNTER, 1) & 0xffffffffL;
		//message related keys are always long
		sharedBox.put(id, message);
		sharedBox.putLong(id | 0x0100000000L, MiniConstants.NGX_WORKER_PROCESSORS_NUM | topicId << 10);
		NginxClojureRT.broadcastEvent(MiniConstants.POST_EVENT_TYPE_PUB, id);
	}
	
	public <T> PubSubListenerData<T> subscribe(T data, NginxPubSubListener<T> listener) {
		Set<PubSubListenerData> pds = topicSubs.get(topicId);
		if (pds == null) {
			Set<PubSubListenerData> old = topicSubs.putIfAbsent(topicId,
					pds = Collections.newSetFromMap(new ConcurrentHashMap<PubSubListenerData, Boolean>()));
			if (old != null) {
				pds = old;
			}
		}
		PubSubListenerData<T> pd = new PubSubListenerData(listener, data);
		pds.add(pd);
		return pd;
	}
	
	public void unsubscribe(PubSubListenerData pd) {
		Set<PubSubListenerData> pds = topicSubs.get(topicId);
		if (pds != null) {
			pds.remove(pd);
		}
	}
	
	public void destory() {
		sharedBox.delete(topic);
		topicSubs.remove(topicId);
	}

}
