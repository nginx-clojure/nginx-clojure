package nginx.clojure;

import static org.junit.Assert.*;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class HackUtilsTest {

	Charset utf8 = Charset.forName("utf-8");
	
	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testDecode() {
		String ss = "abcdefg";
		String dss = HackUtils.decode(ByteBuffer.wrap(ss.getBytes(utf8)),  utf8,  CharBuffer.allocate(100));
		assertEquals(ss, dss);
		dss = HackUtils.decode(ByteBuffer.wrap(ss.getBytes(utf8)),  utf8,  CharBuffer.allocate(1));
		assertEquals(ss, dss);
	}
	
	@Test
	public void testEncode() {
		ByteBuffer bb = HackUtils.encodeLowcase("ABCDEFG", utf8,  ByteBuffer.allocate(100));
		assertEquals("abcdefg", new String(bb.array(), 0, bb.remaining()));
	}

}
