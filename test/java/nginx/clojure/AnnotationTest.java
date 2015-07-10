/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import nginx.clojure.anno.Suspendable;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class AnnotationTest {

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}
	
	@Suspendable
	public static class A {
		public void echo(List<String> ma, String m) {
			Coroutine.yield();
			System.out.println("ok " + m);
			ma.add(m);
		}
	}

	public static class B {
		@Suspendable
		public void echo(List<String> ma, String m) {
			Coroutine.yield();
			System.out.println("ok " + m);
			ma.add(m);
		}
	}
	
	@Test
	public void testClassAnnotation() {
		final List<String> ma = new ArrayList<String>();
		
		Runnable r = new Runnable() {
			@Override
			public void run() throws SuspendExecution {
				A a = new A();
				a.echo(ma, "test");
				a.echo(ma, "just");
			}
		};
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
	
	@Test
	public void testMethodAnnotation() {

		final List<String> ma = new ArrayList<String>();
		
		Runnable r = new Runnable() {
			@Override
			public void run() throws SuspendExecution {
				B a = new B();
				a.echo(ma, "test");
				a.echo(ma, "just");
			}
		};
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

}
