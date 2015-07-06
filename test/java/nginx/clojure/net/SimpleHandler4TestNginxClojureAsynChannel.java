package nginx.clojure.net;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetEncoder;
import java.util.Map;

import nginx.clojure.ChannelCloseAdapter;
import nginx.clojure.NginxClojureRT;
import nginx.clojure.NginxHttpServerChannel;
import nginx.clojure.NginxRequest;
import nginx.clojure.java.ArrayMap;
import nginx.clojure.java.NginxJavaRingHandler;
import nginx.clojure.logger.TinyLogService;
import nginx.clojure.logger.TinyLogService.MsgType;
import nginx.clojure.net.NginxClojureAsynChannel.CompletionListener;

public class SimpleHandler4TestNginxClojureAsynChannel implements NginxJavaRingHandler {

	TinyLogService log = new TinyLogService(MsgType.debug, System.err, System.err);
	
	private void handleError(long status, NginxClojureAsynChannel upstream, NginxHttpServerChannel downstream) throws IOException {
		upstream.close();
		if (downstream.getContext() == "sent") {
			downstream.send("\r\n************Error Happended************\r\n"+upstream.buildError(status), true, true);
			log.warn("error happened: %s", upstream.buildError(status));
		}else {
			downstream.sendResponse(new Object[] {500, ArrayMap.create("Content-Type", "text/html") , upstream.buildError(status)});
		}
	}
	
	private boolean checkDownStreamClosed(NginxClojureAsynChannel upstream, NginxHttpServerChannel downstream) {
		if (downstream.isClosed()) {
			log.info("downstream is closed!");
			upstream.close();
			return true;
		}
		return false;
	}
	
	@Override
	public Object[] invoke(Map<String, Object> request) {
		NginxRequest req = (NginxRequest) request;
		NginxHttpServerChannel downstream = req.hijack(true);
		downstream.addListener(downstream, new ChannelCloseAdapter<NginxHttpServerChannel>() {
			@Override
			public void onClose(NginxHttpServerChannel data) {
				log.info("***downstream closed!");
			}
		});
		final NginxClojureAsynChannel upstream = new NginxClojureAsynChannel();
		String url = "www.apache.org:80";
		upstream.setTimeout(5000, 20000, 20000);
		upstream.connect(url, downstream, new CompletionListener<NginxHttpServerChannel>() {
			@Override
			public void onError(long code, NginxHttpServerChannel downstream) throws IOException {
				log.info("connected error : " + code);
				handleError(code, upstream, downstream);
			}
			@Override
			public void onDone(long status, final NginxHttpServerChannel downstream) {
				log.info("connected successfully : " + status);
				if (checkDownStreamClosed(upstream, downstream)) {
					return;
				}
				CharsetEncoder encoder = NginxClojureRT.DEFAULT_ENCODING.newEncoder();
				ByteBuffer getCommand;
				try {
					getCommand = encoder.encode(CharBuffer
							.wrap("GET /dist/httpcomponents/httpclient/RELEASE_NOTES-4.3.x.txt HTTP/1.1\r\n"
									+ "User-Agent: nginx-clojure/0.2.5\r\n" 
									+ "Host: www.apache.org\r\nAccept: */*\r\n"
									+ "Connection: close\r\n\r\n"));
					upstream.write(getCommand, upstream, new CompletionListener<NginxClojureAsynChannel>() {
						public void onError(long code, NginxClojureAsynChannel attachment) throws IOException {
							attachment.close();
							handleError(code, upstream, downstream);
						};
						@Override
						public void onDone(long status, final NginxClojureAsynChannel upstream) {
							log.info("write onDone status : " + status);
							upstream.getAsynSocket().shutdown(NginxClojureAsynSocket.NGX_HTTP_CLOJURE_SOCKET_SHUTDOWN_SOFT_WRITE);
							if (checkDownStreamClosed(upstream, downstream)) {
								return;
							}
							ByteBuffer buffer = ByteBuffer.allocateDirect(1024*4);
							CompletionListener<ByteBuffer> upstreamListener = new CompletionListener<ByteBuffer>() {
								public void onError(long code, ByteBuffer attachment) throws IOException {
									handleError(code, upstream, downstream);
								};
								@Override
								public void onDone(long status, ByteBuffer buffer) throws IOException {
									log.info("read onDone status : " + status);
									if (checkDownStreamClosed(upstream, downstream)) {
										return;
									}
									boolean end = buffer.hasRemaining() || status == 0;
									buffer.flip();
									downstream.setContext("sent");//have sent something
									downstream.send(buffer, true, end);
									buffer.clear();
									if (!end) {
										upstream.read(buffer, buffer, this);
									}else {
										upstream.close();
									}
								}
							};
							upstream.read(buffer, buffer, upstreamListener);
						}

					});
				} catch (CharacterCodingException e) {// should not happend!
				}
					
			}
		});
		return null;
	}

}
