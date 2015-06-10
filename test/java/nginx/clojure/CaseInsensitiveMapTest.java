package nginx.clojure;

import static org.junit.Assert.assertEquals;

import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class CaseInsensitiveMapTest {

	Map<String, String> m;
	
	@Before
	public void setUp() throws Exception {
		m  = new CaseInsensitiveMap<String>();
		m.put("Good", "good");
		m.put("BAD", "bad");
		m.put("very", "very");
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testFind() {
		assertEquals("good", m.get("good"));
		assertEquals("good", m.get("Good"));
		assertEquals(null, m.get("good1"));
		assertEquals("bad", m.get("BAD"));
		assertEquals("bad", m.get("bAD"));
		assertEquals("bad", m.get("bad"));
		assertEquals("very", m.get("very"));
		assertEquals("very", m.get("VerY"));
	}

}
