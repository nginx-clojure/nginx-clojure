package nginx.clojure;

import static org.junit.Assert.*;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ProxyTest {

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}
	
	public static class A {
		public void echo(List<String> ma, String m) throws SuspendExecution {
			Coroutine.yield();
			System.out.println("ok " + m);
			ma.add(m);
		}
	}

	@Test
	public void testProxyInvoke() {
		final List<String> ma = new ArrayList<String>();
		Runnable r = (Runnable) Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(), new Class<?>[]{Runnable.class}, new InvocationHandler() {
			@Override
			public Object invoke(Object proxy, Method method, Object[] args)
					throws Throwable, SuspendExecution {
				A a = new A();
				a.echo(ma, "test");
				a.echo(ma, "just");
				return null;
			}
		});
		Coroutine co = new Coroutine(r);
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

	public static interface ICal {
		public int add(int a, int b) throws SuspendExecution;
	}
	
	@Test
	public void testComplexProxyInvoke() {
		final List<String> ma = new ArrayList<String>();
		final ICal cal = (ICal) Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(), new Class<?>[]{ICal.class}, new InvocationHandler() {
			
			@Override
			public Object invoke(Object proxy, Method method, Object[] args)
					throws Throwable, SuspendExecution {
				A a = new A();
				a.echo(ma, "test");
				a.echo(ma, "just");
				Coroutine.yield();
				return (int)(Integer)args[0] + (int)(Integer)args[1];
			}
		});
		Coroutine co = new Coroutine(new Runnable() {
			
			@Override
			public void run() throws SuspendExecution {
				String a = "";
				a = "c";
				ma.add(a);
				a = "";
				ma.add(cal.add(1, 2)+a);
			}
		});
		
		co.resume();
		assertEquals(1, ma.size());
		co.resume();
		assertEquals(2, ma.size());
		assertEquals("test", ma.get(1));
		co.resume();
		assertEquals(3, ma.size());
		assertEquals("just", ma.get(2));
		
		co.resume();
		assertEquals(4, ma.size());
		assertEquals("3", ma.get(3));
		
		assertEquals(Coroutine.State.FINISHED, co.getState());
		assertTrue(co.getStack().allObjsAreNull());
		
	}
}
