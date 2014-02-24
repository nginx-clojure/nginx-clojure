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
public class DoubleTest  implements Runnable {

    double result;

    @Test
    public void testDouble() {
        Coroutine co = new Coroutine(this);
        co.run();
        assertEquals(0, result, 1e-8);
        co.run();
        assertEquals(1, result, 1e-8);
        assertEquals(Coroutine.State.FINISHED, co.getState());
    }

    @Override
    public void run() throws SuspendExecution {
        double temp = Math.cos(0);
        Coroutine.yield();
        this.result = temp;
    }

}
