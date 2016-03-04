package nginx.clojure.java;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.UUID;

import nginx.clojure.ChannelCloseAdapter;
import nginx.clojure.NginxClojureRT;
import nginx.clojure.NginxHttpServerChannel;
import nginx.clojure.NginxRequest;
import nginx.clojure.net.NginxClojureAsynSocket;

public class MyBodyReadEventHandler implements NginxJavaRingHandler {

 private static class MyContext {
        private ByteBuffer readBuffer = ByteBuffer.allocate(100*1024);
        private ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(5*1024);

    }

    @Override
    public Object[] invoke(Map<String, Object> request) throws IOException {
        NginxRequest req = (NginxRequest) request;
        NginxHttpServerChannel downstream = req.hijack(true);
        downstream.turnOnEventHandler(true, false, true);
        UUID uuid = UUID.randomUUID();
        String guid = uuid.toString();
        MyContext context = new MyContext();
        downstream.setContext(context);
        downstream.addListener(downstream, new ChannelCloseAdapter<NginxHttpServerChannel>() {
            @Override
            public void onClose(NginxHttpServerChannel data) throws IOException {
                NginxClojureRT.log.info("StreamingWriteHandler closed now!");
            }

            @Override
            public void onRead(long status, NginxHttpServerChannel data) throws IOException {
                NginxClojureRT.log.info("Read event called ");
                if(status <0) {
                    NginxClojureRT.log.info("Read status is " + status);
                } else {
                    doRead(data);
                }
            }

            @Override
            public void onWrite(long status, NginxHttpServerChannel ch) throws IOException {
                if (status < 0) {
                    NginxClojureRT.log.error("onWrite error %s", NginxClojureAsynSocket.errorCodeToString(status));
                    ch.close();
                }else {
                    //doWrite(ch);
                }
            }
        });
        doRead(downstream);
        return null;
    }

    protected void doRead(NginxHttpServerChannel ch) throws IOException {
        NginxClojureRT.log.info("inside doRead method");
        MyContext context = (MyContext)ch.getContext();
        do {
            long c = ch.read(context.readBuffer);
            
            if (c < 0) {
            	String s = String.format("inside doRead: should have read the whole data , rc=%s, total=%d", c, context.byteArrayOutputStream.size());
                NginxClojureRT.log.info(s);
                ch.sendResponse(new Object[] {200, null, s});
                break;
            }if (c == 0) {
            	break;
            } else {
            	
                context.readBuffer.flip();
                System.out.println(context.readBuffer.remaining());
                NginxClojureRT.log.info("inside doRead: draining the buffer to outpustream");
                while (context.readBuffer.hasRemaining()) {
                    context.byteArrayOutputStream.write(context.readBuffer.get());
                }
                context.readBuffer.clear();
            }

        }while(true);

    }

}