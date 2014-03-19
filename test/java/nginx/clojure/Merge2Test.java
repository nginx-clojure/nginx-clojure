/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package nginx.clojure;

import static org.junit.Assert.assertTrue;

/**
 *
 * @author mam
 */
public class Merge2Test  implements Runnable {

    public interface Interface {
        public void method();
    }

    public static Interface getInterface() {
        return null;
    }

    public static void suspendable() throws SuspendExecution {
    }

    public void run() throws SuspendExecution {
        try {
            Interface iface = getInterface();
            iface.method();
        } catch(IllegalStateException ise) {
            suspendable();
        }
    }

    @org.junit.Test
    public void testMerge2() {
        try {
            Coroutine c = new Coroutine(new Merge2Test());
            c.run();
            assertTrue("Should not reach here", false);
        } catch (NullPointerException ex) {
            // NPE expected
        }
    }
}
