package nginx.clojure;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import clojure.lang.IFn;
import clojure.lang.RT;
import clojure.lang.Symbol;

public class ClojureFnTest {

	ArrayList<String> messages = new ArrayList<String>();
	
	@Before
	public void setUp() throws Exception {
//		String classpath = System.getProperty("java.class.path");
//		String[] cps = classpath.split(File.pathSeparator);
//		for (String cp : cps) {
//			System.out.println(cp);
//		}
		messages = new ArrayList<String>();
	}

	@After
	public void tearDown() throws Exception {
		messages.clear();
	}

	@Test
	public void testSimpleFnOutofCoroutine() {
		RT.var("clojure.core", "require").invoke(Symbol.create("nginx.clojure.fns-for-test"));
//		System.out.println("rq cl :" + rq.getClass().getClassLoader());
		final IFn  fn = (IFn)RT.var("nginx.clojure.fns-for-test", "fn-out-of-coroutine").fn();
		fn.invoke(messages);
		assertEquals(4, messages.size());
		assertEquals("entering fn-out-of-coroutine", messages.get(0));
		for (int i = 0; i < 3; i++) {
			assertEquals("echo:" + i, messages.get(i+1));
		}
	}
	
	
	@Test
	public void testSimpleFn() {
		RT.var("clojure.core", "require").invoke(Symbol.create("nginx.clojure.fns-for-test"));
//		System.out.println("rq cl :" + rq.getClass().getClassLoader());
		final IFn  fn = (IFn)RT.var("nginx.clojure.fns-for-test", "simplefn").fn();
		Coroutine cr = new Coroutine(new Runnable() {
			@Override
			public void run() throws SuspendExecution {
				fn.invoke(messages);
			}
		});
		
		cr.resume();
		assertEquals(1, messages.size());
		assertEquals("entering simplefn", messages.get(0));
		
		cr.resume();
		assertEquals(2, messages.size());
		assertEquals("before yield:0", messages.get(1));
		
		cr.resume();
		assertEquals(4, messages.size());
		assertEquals("end yield:0", messages.get(2));
		assertEquals("before yield:1", messages.get(3));
		
		cr.resume();
		assertEquals(6, messages.size());
		assertEquals("end yield:1", messages.get(4));
		assertEquals("before yield:2", messages.get(5));
		
		cr.resume();
		assertEquals(8, messages.size());
		assertEquals("end yield:2", messages.get(6));
		
		long tid = Thread.currentThread().getId();
		//the same thread with caller: good!
		assertEquals("threadId:"+tid, messages.get(7));
		assertEquals(Coroutine.State.FINISHED, cr.getState());
		assertTrue(cr.getStack().allObjsAreNull());
	}
	

	@Test
	public void testReduce() {
		for (int k = 0; k < 10; k++) {
			RT.var("clojure.core", "require").invoke(Symbol.create("nginx.clojure.fns-for-test"));
//			System.out.println("rq cl :" + rq.getClass().getClassLoader());
			final IFn  fn = (IFn)RT.var("nginx.clojure.fns-for-test", "coreduce-test").fn();
			ArrayList<Long> ma = new ArrayList<Long>();
			Coroutine cr = (Coroutine)fn.invoke(ma);
			cr.resume();
			for (int i = 0; i < 4; i++) {
				cr.resume();
			}
			assertEquals(25L, (Number)ma.get(0));
			assertEquals(Coroutine.State.FINISHED, cr.getState());
			assertTrue(cr.getStack().allObjsAreNull());
		}
	}
	
	@Test
	public void testBinding() {
		RT.var("clojure.core", "require").invoke(Symbol.create("nginx.clojure.fns-for-test"));
		IFn  cafn = (IFn)RT.var("nginx.clojure.fns-for-test", "ca").fn();
		IFn  cbfn = (IFn)RT.var("nginx.clojure.fns-for-test", "cb").fn();
		ArrayList<String> ma = new ArrayList<String>();
		Coroutine ca = (Coroutine) cafn.invoke(ma);
		ArrayList<String> mb = new ArrayList<String>();
		Coroutine cb = (Coroutine) cbfn.invoke(mb);
		ca.resume();
		cb.resume();
		assertTrue(ma.isEmpty());
		assertTrue(mb.isEmpty());
		ca.resume();
		assertEquals("ca", ma.get(0));
		cb.resume();
		assertEquals("cb", mb.get(0));
	}
}
