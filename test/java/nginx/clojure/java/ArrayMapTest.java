package nginx.clojure.java;

import static org.junit.Assert.*;

import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ArrayMapTest {

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testAll() {
		Map<String, String> m = ArrayMap.create("java", "ok", "clojure", "good");
		assertEquals(2, m.size());
		assertEquals("ok", m.get("java"));
		assertEquals("good", m.get("clojure"));
		assertNull(m.get("c"));
		
		m.put("java", "?");
		assertEquals("?", m.get("java"));
		m.put("clojure", "??");
		assertEquals("??", m.get("clojure"));
		m.put("c", "fine");
		assertEquals("fine", m.get("c"));
		assertEquals(3, m.size());
		
		String jv = m.remove("java");
		assertEquals("?", jv);
		assertEquals(2, m.size());
		
		jv = m.remove("java");
		assertNull(jv);
		assertEquals(2, m.size());
		
		
		String cv = m.remove("c");
		assertEquals("fine", cv);
		assertEquals(1, m.size());
		
		cv = m.remove("c");
		assertEquals(null, cv);
		assertEquals(1, m.size());
	}

}
