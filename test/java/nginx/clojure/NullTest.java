/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package nginx.clojure;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 *
 * @author Matthias Mann
 */
public class NullTest  implements Runnable {

    Object result = "b";
    
    @Test
    public void testNull() {
        int count = 0;
        Coroutine co = new Coroutine(this);
        while(co.getState() != Coroutine.State.FINISHED) {
            ++count;
            co.run();
        }
        assertEquals(2, count);
        assertEquals("a", result);
    }
    
    public void run() throws SuspendExecution {
        result = getProperty();
    }
    
    private Object getProperty() throws SuspendExecution {
        Object x = null;
        
        Object y = getProtery("a");
        if(y != null) {
            x = y;
        }
        
        return x;
    }

    private Object getProtery(String string) throws SuspendExecution {
        Coroutine.yield();
        return string;
    }
    
    public void testfinal() {
    	try {
    		System.out.println("bad");
    	} catch (Throwable e) {
			// TODO: handle exception
		}finally {
    		System.out.println("good");
    	}
    }
    
    public static class NC extends NullTest {
    	public void testfinal() {
    		super.testfinal();
    	}
    }

}
