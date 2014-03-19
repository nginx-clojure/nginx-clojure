/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package nginx.clojure;

import java.io.FileNotFoundException;
import java.io.IOException;

import org.junit.Test;

/**
 *
 * @author mam
 */
public class MergeTest implements Runnable {

    public static void throwsIO() throws IOException {
    }

    public void run() throws SuspendExecution {
        try {
            throwsIO();
        } catch(FileNotFoundException e) {
            e.printStackTrace();
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testMerge() {
        Coroutine c = new Coroutine(new MergeTest());
        c.run();
    }
}
