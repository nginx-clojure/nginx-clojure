/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package nginx.clojure;

import junit.framework.TestCase;

/**
 *
 * @author Matthias Mann
 */
public class ArrayTest extends TestCase  {

    private static final PatchLevel l1 = new PatchLevel();
    private static final PatchLevel[] l2 = new PatchLevel[] { l1 };
    private static final PatchLevel[][] l3 = new PatchLevel[][] { l2 };
    
    public void testArray() {
        Coroutine co = new Coroutine(new Runnable() {
			
			@Override
			public void run() throws SuspendExecution {
				coExecute();
			}
		});
        co.run();
        assertEquals(42, l1.i);
        assertTrue(co.getStack().allObjsAreNull());
    }
    
    public void coExecute() throws SuspendExecution {
        PatchLevel[][] local_patch_levels = l3;
        PatchLevel patch_level = local_patch_levels[0][0];
        patch_level.setLevel(42);
    }
    
    public static class PatchLevel {
        int i;
    
        public void setLevel(int value) throws SuspendExecution {
            i = value;
        }
    }
}
