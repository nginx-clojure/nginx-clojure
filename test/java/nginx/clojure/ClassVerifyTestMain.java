package nginx.clojure;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;

import nginx.clojure.wave.JavaAgent;

public class ClassVerifyTestMain {

	public ClassVerifyTestMain() {
		// TODO Auto-generated constructor stub
	}
	
	public static byte[] getClassBytes(String clz) throws IOException {
		InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(clz + ".class");
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buf = new byte[1024];
		int c = 0;
		while ((c = is.read(buf)) > 0) {
			out.write(buf, 0 ,c);
		}
		out.flush();
		is.close();
		return out.toByteArray();
	}

	public static void main(String[] args) {		
		ClassFileTransformer cft = JavaAgent.buildClassFileTransformer("mbdp");
		try {
			String clz = args[0];
			cft.transform(Thread.currentThread().getContextClassLoader(), clz, null, null, getClassBytes(clz));
		} catch (IllegalClassFormatException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
