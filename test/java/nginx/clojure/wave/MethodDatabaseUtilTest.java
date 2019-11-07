package nginx.clojure.wave;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

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
		assertEquals(MethodDatabase.SUSPEND_FAMILY, ce.check("invoke", "()Ljava/lang/Object;"));
		
		@SuppressWarnings("unused")
		ClassEntry mce = MethodDatabaseUtil.buildClassEntryFamily(db, Type.getInternalName(MyAF.class));
		assertEquals(MethodDatabase.SUSPEND_FAMILY, db.checkMethodSuspendType(Type.getInternalName(MyAF.class), "invoke()Ljava/lang/Object;", true));
		
	}
	
	
	public void testLazyAndFuzzyClass() throws IOException { 
		MethodDatabase db = new MethodDatabase(Thread.currentThread().getContextClassLoader());
		MethodDatabaseUtil.load(db, "nginx/clojure/wave/test-coroutine-and-compojure-db.txt");
		ClassEntry sce = MethodDatabaseUtil.buildClassEntryFamily(db, "nginx/clojure/net/SimpleHandler4TestNginxClojureSocket");
		assertEquals(MethodDatabase.SUSPEND_NORMAL, sce.check("invoke(Ljava/lang/Object;)Ljava/lang/Object;"));
	}
	
	@SuppressWarnings("serial")
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
		
		
		
		@SuppressWarnings("unused")
		ClassEntry ifnce = MethodDatabaseUtil.buildClassEntryFamily(db, Type.getInternalName(MyAF.class));
		assertEquals(MethodDatabase.SUSPEND_FAMILY, db.checkMethodSuspendType(Type.getInternalName(MyAF.class),"invoke()Ljava/lang/Object;", true));
	}
	
	@Test
	public void testMergeExistsDBText() throws Exception {
		ConcurrentHashMap<String, ConcurrentHashMap<String, Object>> result = new ConcurrentHashMap<String, ConcurrentHashMap<String, Object>>();
		Map<String, TreeMap<String, String>> upperMarks = new TreeMap<String, TreeMap<String, String>>();
		SuspendMethodTracer.load(this.getClass().getResourceAsStream("test-merge-exists-db.txt"), result, upperMarks);
		assertEquals(2, result.size());
		
		ConcurrentHashMap<String, Object> mynotfound = result.get("my/notfound/class");
		assertEquals(2, mynotfound.size());
		assertEquals("", mynotfound.get("test()V"));
		assertEquals("", mynotfound.get("test2()V"));
		
		ConcurrentHashMap<String, Object> socketInputStream = result.get("java/net/SocketInputStream");
		assertEquals(2, socketInputStream.size());
		assertEquals("", socketInputStream.get("read([B)I"));
		assertEquals("", socketInputStream.get("read([BII)I"));
		
		assertEquals(1, upperMarks.size());
		TreeMap<String, String> sessionInputBuffer = upperMarks.get("org/apache/http/io/SessionInputBuffer");
		assertEquals(2, sessionInputBuffer.size());
		assertEquals("#mark from sub org/apache/http/impl/io/AbstractSessionInputBuffer", sessionInputBuffer.get("read([BII)I"));
		assertEquals("#mark from sub org/apache/http/impl/io/AbstractSessionInputBuffer", sessionInputBuffer.get("readLine(Lorg/apache/http/util/CharArrayBuffer;)I"));
	}
	
	@Test
	public void testFuzzingClass() throws Exception {
		String f1 = MethodDatabaseUtil.toFuzzyString(MethodDatabaseUtil.FUZZY_CLASS_PATTERN, "com/sun/proxy/$Proxy34", MethodDatabaseUtil.FUZZY_CLASS_PATTERN.toString());
		assertEquals("com/sun/proxy/\\$Proxy(\\d+)", f1);
		String f2 = MethodDatabaseUtil.toFuzzyString(MethodDatabaseUtil.FUZZY_CLASS_PATTERN, "nginx/clojure/java/FilterTestSet4NginxJavaHeaderFilter$AccessRemoteHeaderFilter", MethodDatabaseUtil.FUZZY_CLASS_PATTERN.toString());
		assertEquals("nginx/clojure/java/FilterTestSet(\\d+)NginxJavaHeaderFilter\\$AccessRemoteHeaderFilter", f2);
	}

}
