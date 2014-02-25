package nginx.clojure.wave;

import static org.junit.Assert.*;

import java.io.IOException;

import nginx.clojure.asm.Type;
import nginx.clojure.wave.MethodDatabase;
import nginx.clojure.wave.MethodDatabase.ClassEntry;
import nginx.clojure.wave.MethodDatabaseUtil;

import org.junit.Test;

import clojure.lang.AFunction;

public class MethodDatabaseUtilTest {


	@Test
	public void testLoad() throws IOException {
		MethodDatabase db = new MethodDatabase(Thread.currentThread().getContextClassLoader());
		MethodDatabaseUtil.load(db, "nginx/clojure/wave/test-coroutine-method-db.txt");
		ClassEntry ce = db.getClasses().get("java/lang/Thread");
		assertEquals(MethodDatabase.SUSPEND_BLOCKING, ce.check("sleep", "(J)V"));
		assertEquals(MethodDatabase.SUSPEND_BLOCKING, ce.check("sleep", "(JI)V"));
		assertEquals(MethodDatabase.SUSPEND_BLOCKING, ce.check("join", "()V"));
		
		
		ce = db.getClasses().get("java/lang/Object");
		assertEquals(MethodDatabase.SUSPEND_BLOCKING, ce.check("wait", "(J)V"));
		assertEquals(MethodDatabase.SUSPEND_BLOCKING, ce.check("wait", "()V"));
		assertEquals(MethodDatabase.SUSPEND_BLOCKING, ce.check("wait", "(JI)V"));
		assertNull(ce.check("test-unknown-method", "()V"));
		
		ce = db.getClasses().get("clojure/lang/IFn");
		assertEquals(MethodDatabase.SUSPEND_NORMAL, ce.check("invoke", "()Ljava/lang/Object;"));
	}
	
	
	public static class MyAF extends AFunction {
		@Override
		public Object invoke() {
			return super.invoke();
		}
	}
	
	@Test
	public void testBuildClassEntryFamily() throws IOException {
		MethodDatabase db = new MethodDatabase(Thread.currentThread().getContextClassLoader());
		MethodDatabaseUtil.load(db, "nginx/clojure/wave/coroutine-method-db.txt");
		ClassEntry ce = MethodDatabaseUtil.buildClassEntryFamily(db, "nginx/clojure/CatchTest");
		assertEquals(MethodDatabase.SUSPEND_NONE, ce.check("testCatch", "()V"));
		assertEquals(MethodDatabase.SUSPEND_NORMAL, ce.check("run", "()V"));
		
		
		
		ClassEntry ifnce = MethodDatabaseUtil.buildClassEntryFamily(db, Type.getInternalName(MyAF.class));
		assertEquals(MethodDatabase.SUSPEND_FAMILY, db.checkMethodSuspendType(Type.getInternalName(MyAF.class),"invoke", "()Ljava/lang/Object;", true));
	}

}
