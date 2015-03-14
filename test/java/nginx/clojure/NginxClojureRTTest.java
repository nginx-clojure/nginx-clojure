package nginx.clojure;

import java.util.Map;

import nginx.clojure.java.ArrayMap;

import org.junit.Test;

import junit.framework.TestCase;

public class NginxClojureRTTest extends TestCase {

	@Test
	public void testEvalSimpleExp() {
		Map<String, String> vars = ArrayMap.create("var1", "var1-values", "var2", "var2-values");
		assertEquals("var1="+vars.get("var1"), NginxClojureRT.evalSimpleExp("var1=#{var1}", vars));
		assertEquals(vars.get("var1"), NginxClojureRT.evalSimpleExp("#{var1}", vars));
		assertEquals(vars.get("var2"), NginxClojureRT.evalSimpleExp("#{var2}", vars));
		assertEquals("var2="+vars.get("var2"), NginxClojureRT.evalSimpleExp("var2=#{var2}", vars));
		assertEquals(vars.get("var1") + vars.get("var2"), NginxClojureRT.evalSimpleExp("#{var1}#{var2}", vars));
		assertEquals("var1=" + vars.get("var1") + ",var2=" + vars.get("var2"),
				NginxClojureRT.evalSimpleExp("var1=#{var1},var2=#{var2}", vars));
	}
}
