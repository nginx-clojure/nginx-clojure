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
	
	public static class B {
		public A a;
		public B(int n, ArrayList<Integer> result) throws SuspendExecution {
			System.out.println("b begin");
			try {
				a = new A(n, result);
				a.haha("in b (1)");
				a = null;
				a.haha("npe!");
			}catch(Exception e) {
				System.out.println("b exception");
			}
			
			a = new A(n, result);
			a.haha("in b (2)");

			System.out.println("b end");
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
		assertTrue(co.getCStack().empty());
		assertTrue(co.getStack().allObjsAreNull());
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
		assertTrue(co.getCStack().empty());
		assertTrue(co.getStack().allObjsAreNull());
		
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
			assertTrue(co.getStack().allObjsAreNull());
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
	
	
	@Test
	public void testException() {
		final ArrayList<Integer> result = new ArrayList<Integer>();


		Coroutine co = new Coroutine(new Runnable() {
			
			@Override
			public void run() throws SuspendExecution {
				B b = new B(4, result);
				b.a.haha("run end");
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
		
		result.clear();
		
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
		
		assertTrue(co.getStack().allObjsAreNull());
	}
	
	public static class D {
		public ArrayList<Integer> cal(int n) throws SuspendExecution {
			return realCal(n);
		}

		public ArrayList<Integer> realCal(int n) throws SuspendExecution {
			ArrayList<Integer> result = new ArrayList<Integer>();
			for (int i = 0; i < n; i++) {
				if (Coroutine.getActiveCoroutine() != null) {
					Coroutine.yield();
				}
				result.add(i);
			}
			return result;
		}
	}
	
	public static class C {
		private ArrayList<Integer> result;
		
		public C() {
		}
		
		public void doCal(int n)  throws SuspendExecution {
			D d = new D();
			this.result = d.cal(n);
		}
		
		public ArrayList<Integer> getResult() {
			return result;
		}
	}
	
	@Test
	public void testFiledDirectAssignFromSuspendableMethod() {
		
		final C c = new C();
		
		Coroutine co = new Coroutine(new Runnable() {
			
			@Override
			public void run() throws SuspendExecution {
				c.doCal(4);
				System.out.println(c.getResult());
			}
		});
		co.resume();
		assertEquals(null, c.result);
		co.resume();
//		assertEquals(1, c.result.size());
//		assertEquals((Integer)0, c.result.get(0));
		co.resume();
//		assertEquals(2, c.result.size());
//		assertEquals((Integer)1, c.result.get(1));
		co.resume();
//		assertEquals(3, c.result.size());
//		assertEquals((Integer)2, c.result.get(2));
//		assertTrue(SuspendableConstructorUtilStack.getStack().empty());
		co.resume();
		assertEquals(4, c.result.size());
		assertEquals((Integer)3, c.result.get(3));
		assertTrue(co.getCStack().empty());
		assertTrue(co.getStack().allObjsAreNull());
	}

} 
