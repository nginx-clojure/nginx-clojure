package nginx.clojure;

import static org.junit.Assert.*;

import java.util.ArrayList;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ConstructorTest {

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}
	
	public static class A {
		
		public A(int n, ArrayList<Integer> result) throws SuspendExecution {
			for (int i = 0; i < n; i++) {
				Coroutine.yield();
				result.add(i);
			}
		}
		
	}

	@Test
	public void testSimpleConstructor() {
		
		final ArrayList<Integer> result = new ArrayList<Integer>();
		Coroutine co = new Coroutine(new Runnable() {
			
			@Override
			public void run() throws SuspendExecution {
				A a = new A(3, result);
			}
		});
		co.resume();
		assertEquals(0, result.size());
		co.resume();
		assertEquals(1, result.size());
		assertEquals((Integer)0, result.get(0));
		co.resume();
		assertEquals(2, result.size());
		assertEquals((Integer)1, result.get(1));
		co.resume();
		assertEquals(3, result.size());
		assertEquals((Integer)2, result.get(2));
		
	}

} 
