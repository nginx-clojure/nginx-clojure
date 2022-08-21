/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.java;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
//import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
//import java.util.zip.ZipEntry;
//import java.util.zip.ZipOutputStream;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import nginx.clojure.MiniConstants;

/**
 * @author who
 *
 */
public class StringFacedJavaBodyFilterTest {

	/**
	 * @throws java.lang.Exception
	 */
	@Before
	public void setUp() throws Exception {
	}

	/**
	 * @throws java.lang.Exception
	 */
	@After
	public void tearDown() throws Exception {
	}

	/**
	 * Test method for {@link nginx.clojure.java.StringFacedJavaBodyFilter#decodeToString(java.nio.ByteBuffer, java.io.InputStream)}.
	 * @throws IOException 
	 */
	@Test
	public void testDecodeToString() throws IOException {
		ByteBuffer rem = ByteBuffer.allocate(3);
		rem.flip();
		String s = "權補縮短補發情報機關";
//		ByteArrayOutputStream bo = new ByteArrayOutputStream();
//		ZipOutputStream zo = new ZipOutputStream(bo);
//		zo.putNextEntry(new ZipEntry("good"));
		byte[] all = s.getBytes(MiniConstants.DEFAULT_ENCODING);
		
//		zo.write(all);
//		zo.flush();
//		zo.close();
		
//		all = bo.toByteArray();
		
		StringFacedJavaBodyFilter.decodeToString(rem, new ByteArrayInputStream(all));
		
		ByteArrayInputStream b1 = new ByteArrayInputStream(all, 0, 3);
		ByteArrayInputStream b2 = new ByteArrayInputStream(all, 3, all.length - 3);
		
		StringBuilder p1 = StringFacedJavaBodyFilter.decodeToString(rem, b1);
		assertEquals(p1.toString(), "權");
		StringBuilder p2 = StringFacedJavaBodyFilter.decodeToString(rem, b2);
		assertEquals(p2.toString(), "補縮短補發情報機關");
	}

}
