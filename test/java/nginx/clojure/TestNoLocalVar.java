package nginx.clojure;

import static org.junit.Assert.*;

import java.util.ArrayList;

import nginx.clojure.Coroutine;
import nginx.clojure.SuspendExecution;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TestNoLocalVar {

	
	ArrayList<Integer> ints = new ArrayList<Integer>();
	
	@Before
	public void setUp() throws Exception {
		ints = new ArrayList<Integer>();
	}

	@After
	public void tearDown() throws Exception {
	}
	
	public int get11() throws SuspendExecution {
		Coroutine.yield();
		ints.add(11);
		return 11;
	}
	
	public int get32() throws SuspendExecution {
		Coroutine.yield();
		ints.add(32);
		return 32;
	}
	
	public int add1132() throws SuspendExecution {
		int j = 0;
		j++;
		int rt = get11() + get32();
		System.out.println(j);
		return rt;
	}

	@Test
	public void testAddTowResults() {
		Coroutine co = new Coroutine(new Runnable() {
			
			@Override
			public void run() throws SuspendExecution {
				ints.add(add1132());
			}
		});
		co.resume();
		assertTrue(ints.isEmpty());
		co.resume();
		assertEquals(1, ints.size());
		assertEquals(11, (int)ints.get(0));
		co.resume();
		assertEquals(3, ints.size());
		assertEquals(11, (int)ints.get(0));
		assertEquals(32, (int)ints.get(1));
		assertEquals(11+32, (int)ints.get(2));
		assertTrue(co.getStack().allObjsAreNull());
	}

}
