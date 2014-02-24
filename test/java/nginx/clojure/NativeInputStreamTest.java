package nginx.clojure;

import static nginx.clojure.NginxClojureRT.UNSAFE;
import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class NativeInputStreamTest {

	NativeInputStream nativeInputStream;
	long addr;
	int c = 256;
	
	
	@BeforeClass
	public static void beforeClass() {
		NginxClojureRT.initUnsafe();
	}
	
	@Before
	public void setUp() throws Exception {
		
		addr = UNSAFE.allocateMemory(c);
		nativeInputStream = new NativeInputStream(addr, c);
		for (int i = 0; i < c; i++) {
			UNSAFE.putByte(addr + i, (byte)i);
		}
	}

	@After
	public void tearDown() throws Exception {
		UNSAFE.freeMemory(addr);
	}

	@Test
	public void testRead() throws IOException {
		for (int i = 0; i < c; i++) {
			int r = nativeInputStream.read();
			assertEquals(i, r);
		}
	}

    /*static jni function can not called*/	
//	@Test
//	public void testReadBytes() throws IOException {
//		byte[] bs = new byte[c];
//		nativeInputStream.read(bs);
//		for (int i = 0; i < c; i++) {
//			assertEquals(i, bs[i]&0xff);
//		}
//	}
}
