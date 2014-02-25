package nginx.clojure;

import static org.junit.Assert.*;

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
		
	}
	

}
