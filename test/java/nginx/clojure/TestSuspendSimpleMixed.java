package nginx.clojure;

import static org.junit.Assert.*;

import java.util.ArrayList;

import nginx.clojure.Coroutine;
import nginx.clojure.SuspendExecution;

import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestSuspendSimpleMixed {

	ArrayList<Integer> ints = new ArrayList<Integer>();
	
	@Before
	public void setUp() throws Exception {
		ints = new ArrayList<Integer>();
	}

	@After
	public void tearDown() throws Exception {
	}

	public void sm1() throws SuspendExecution {
		long p = 0;
		for (int i = 0; i < 6; i++) {
			System.out.println("i=" + i);
			ints.add(i);
			p += i;
			Coroutine.yield();
		}
		System.out.println("p" + p);
	}
	
	public void nm1() {
		sm1();
	}
	
	public void nm2() {
		nm1();
	}
	
//	@Test
//	public void testNCallS1() {
//		Coroutine co = new Coroutine(new Runnable() {
//			@Override
//			public void run() throws SuspendExecution {
//				nm2();
//			}
//		});
//		for (int i = 0; i < 5; i++) {
//			co.resume();
//			assertEquals(i, (int)ints.get(i));
//		}
//	}
	
	public void nmCatch2() throws SuspendExecution {
		try{
			System.out.println("just test!");
			sm1();
		}catch(Throwable e) {
			e.printStackTrace();
		}
		
	}
	
	@Test
	public void testNCatchCallS() {
		Coroutine co = new Coroutine(new Runnable() {
			@Override
			public void run() throws SuspendExecution {
				nmCatch2();
			}
		});
		co.resume();
		for (int i = 0; i < 6; i++) {
			co.resume();
			assertEquals(i, (int)ints.get(i));
		}
		assertEquals(Coroutine.State.FINISHED, co.getState());
		assertTrue(co.getStack().allObjsAreNull());
	}

}
