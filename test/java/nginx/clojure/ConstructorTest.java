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
		
		public static ArrayList<Integer> sresult;
		
		public A(int n, ArrayList<Integer> result) throws SuspendExecution {
			for (int i = 0; i < n; i++) {
				if (Coroutine.getActiveCoroutine() != null) {
					Coroutine.yield();
				}
				result.add(i);
			}
		}
		
		public A(ArrayList<Integer> result) throws SuspendExecution {
			this(4, result);
			System.out.println("A(ArrayList<Integer> result) haha finish!");
		}
		
		public A() throws SuspendExecution {
			this(sresult);
			System.out.println("A() haha finish!");
		}
		
		public String haha(String msg) {
			System.out.println(msg);
			return msg + ":handled";
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
		assertTrue(SuspendableConstructorUtilStack.getStack().empty());
	}
	
	@Test
	public void testComplexConstructor() {
		
		final ArrayList<Integer> result = new ArrayList<Integer>();
		{
		Coroutine co = new Coroutine(new Runnable() {
			
			@Override
			public void run() throws SuspendExecution {
				A a = new A(result);
				a.haha("cpx good");
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
		co.resume();
		assertEquals(4, result.size());
		assertEquals((Integer)3, result.get(3));
		assertTrue(SuspendableConstructorUtilStack.getStack().empty());
		
		}
		
		result.clear();
		
		{
			A.sresult = result;
			Coroutine co = new Coroutine(new Runnable() {
				
				@Override
				public void run() throws SuspendExecution {
					A a = new A();
					a.haha("vcpx good");
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
			co.resume();
			assertEquals(4, result.size());
			assertEquals((Integer)3, result.get(3));
			assertTrue(SuspendableConstructorUtilStack.getStack().empty());
		}
	}
	
	@Test
	public void testNonCoroutine() {
		
		final ArrayList<Integer> result = new ArrayList<Integer>();
		A.sresult = result;
		
		Runnable r = new Runnable() {
			
			@Override
			public void run() throws SuspendExecution {
				A a = new A(result);
				a.haha("cpx good");
			}
		};
		r.run();
		assertEquals(4, result.size());
		assertEquals((Integer)3, result.get(3));
		assertTrue(SuspendableConstructorUtilStack.getStack().empty());
	}
	

} 
