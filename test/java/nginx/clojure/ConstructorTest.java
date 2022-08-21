package nginx.clojure;

import static org.junit.Assert.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import nginx.clojure.anno.Suspendable;

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
	
	public static class SmRunnable implements Runnable {
		ArrayList<Integer> result;
		@Override
		public void run() throws SuspendExecution {
			A a = new A(3, result);
			System.out.println(a);
		}
	}

	@Test
	public void testSimpleConstructor() {
		
		final ArrayList<Integer> result = new ArrayList<Integer>();
		SmRunnable smr = new SmRunnable();
		smr.result = result;
		Coroutine co = new Coroutine(smr);
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
	
	public static class SmReflectRunnable implements Runnable {
		ArrayList<Integer> result;
		@Override
		public void run() throws SuspendExecution {
			try {
				Constructor<A> ctor = A.class.getConstructor(Integer.TYPE, ArrayList.class);
				ctor.newInstance(new Object[] {3, result});
			} catch (NoSuchMethodException e) {
				e.printStackTrace();
				fail(e.getMessage());
			} catch (SecurityException e) {
				e.printStackTrace();
				fail(e.getMessage());
			} catch (InstantiationException e) {
				e.printStackTrace();
				fail(e.getMessage());
			} catch (IllegalAccessException e) {
				e.printStackTrace();
				fail(e.getMessage());
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
				fail(e.getMessage());
			} catch (InvocationTargetException e) {
				e.printStackTrace();
				fail(e.getMessage());
			}
		}
	}
	
	@Test
	public void testReflectConstructorInvoke() {
		final ArrayList<Integer> result = new ArrayList<Integer>();
		SmReflectRunnable smr = new SmReflectRunnable();
		smr.result = result;
		Coroutine co = new Coroutine(smr);
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

	public static class CA extends A {
		String name;
		public CA(int n, String name, ArrayList<Integer> result) throws SuspendExecution {
			super(n, result);
			this.name = name;
		}
	}
	
	public static class SmSuperReflectRunnable implements Runnable {
		ArrayList<Integer> result;
		@Override
		public void run() throws SuspendExecution {
			try {
				Constructor<CA> ctor = CA.class.getConstructor(Integer.TYPE, String.class, ArrayList.class);
				ctor.newInstance(new Object[] {3, "CA001", result});
			} catch (NoSuchMethodException e) {
				e.printStackTrace();
				fail(e.getMessage());
			} catch (SecurityException e) {
				e.printStackTrace();
				fail(e.getMessage());
			} catch (InstantiationException e) {
				e.printStackTrace();
				fail(e.getMessage());
			} catch (IllegalAccessException e) {
				e.printStackTrace();
				fail(e.getMessage());
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
				fail(e.getMessage());
			} catch (InvocationTargetException e) {
				e.printStackTrace();
				fail(e.getMessage());
			}
		}
	}
	
	@Test
	public void testReflectSuperConstructorInvoke() {
		final ArrayList<Integer> result = new ArrayList<Integer>();
		SmSuperReflectRunnable smr = new SmSuperReflectRunnable();
		smr.result = result;
		Coroutine co = new Coroutine(smr);
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
	
	public static class Cb1 {
		public int k;
		@Suspendable
		public Cb1(int k, AtomicInteger ac) {
			this.k = k;
			System.out.println(k);
			Coroutine.yield();
			ac.incrementAndGet();
		}
	}
	
	public static class C1 {
		String name;
		
		public Cb1 cb1;
		
		@Suspendable
		public C1(AtomicInteger ac, String name) {
			this.name = name;
			Cb1 cb1 = new Cb1(20, ac);
			this.cb1 = cb1;
			System.out.println("name is " + name);
			Coroutine.yield();
			ac.addAndGet(3);
		}
	}
	
	public static class C2 extends C1 {
		static String strField;
		@Suspendable
		public C2(AtomicInteger ac) {
			super(ac, strField = "C2");
			System.out.println("result is " + ac.intValue());
		}
	}
	
	@Test
	public void testReflectSuperComplexInvoke() {
		final AtomicInteger ac = new AtomicInteger(5);
		final C2[] C2Ref = new C2[1];
		Coroutine co = new Coroutine(new Runnable() {
			
			@Override
			@Suspendable
			public void run() {
				C2 c2 = new C2(ac);
				C2Ref[0] = c2;
				System.out.println(c2.name + ", " + ac.intValue());
			}
		});
		
		co.resume();
		assertEquals(5, ac.intValue());
		co.resume();
		assertEquals(6, ac.intValue());
		co.resume();
		assertEquals(9, ac.intValue());
		assertEquals(20, C2Ref[0].cb1.k);
	}
} 
