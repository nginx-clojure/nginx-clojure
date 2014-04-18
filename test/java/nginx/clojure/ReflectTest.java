package nginx.clojure;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ReflectTest {

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	public static class A {
		public void echo(List<String> ma, String m) throws SuspendExecution {
			Coroutine.yield();
			ma.add(m);
		}
	}
	
	@Test
	public void testReflectInvoke() {
		final List<String> ma = new ArrayList<String>();
		Coroutine co = new Coroutine(new Runnable() {
			
			@Override
			public void run() throws SuspendExecution {
				A a = new A();
				try {
					Method m = a.getClass().getDeclaredMethod("echo", List.class, String.class);
//					a.echo(ma, "test");
					m.invoke(a, ma, "test");
//					a.echo(ma, "just");
					m.invoke(a, ma, "just");
				} catch (Throwable e) {
					// TODO Auto-generated catch block
//					if (e.getCause() == Stack.exception_instance_not_for_user_code) {
//						throw Stack.exception_instance_not_for_user_code;
//					}
					e.printStackTrace();
				}

			}
		});
		co.resume();
		assertEquals(0, ma.size());
		co.resume();
		assertEquals(1, ma.size());
		assertEquals("test", ma.get(0));
		co.resume();
		assertEquals(2, ma.size());
		assertEquals("just", ma.get(1));
		assertEquals(Coroutine.State.FINISHED, co.getState());
		assertTrue(co.getStack().allObjsAreNull());
	}

}
