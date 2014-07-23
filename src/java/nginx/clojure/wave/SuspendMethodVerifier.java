package nginx.clojure.wave;

import java.lang.reflect.Method;
import java.util.ArrayList;

import nginx.clojure.Stack;
import nginx.clojure.asm.Type;
import nginx.clojure.wave.MethodDatabase.ClassEntry;
import nginx.clojure.wave.SuspendMethodTracer.MethodInfo;

public class SuspendMethodVerifier {
	
	public static class VerifyVarInfo implements Cloneable {
		public int idx = -1;
		public int dataIdx = -1;
		public String name;
		public Object value;
		
		public VerifyVarInfo clone()  {
			try {
				return (VerifyVarInfo) super.clone();
			} catch (CloneNotSupportedException e) {
				e.printStackTrace();
			}
			return null;
		}
	}
	
	public static class VerifyMethodInfo extends MethodInfo {
		
		public int idx = -1;
		public VerifyVarInfo[] vars;
		public String classAndMethod;
		
		public VerifyMethodInfo(String owner, String method) {
			super(owner, method);
			classAndMethod = owner + "." + method;
		}
	}
	
	public static class VerifyInfo {
		public long vid;
		public boolean quite = false;
		public boolean exception = false;
		public ArrayList<VerifyMethodInfo> tracerStacks = new ArrayList<SuspendMethodVerifier.VerifyMethodInfo>();
		public VerifyMethodInfo[] methodIdxInfos = new VerifyMethodInfo[8];
//		private long id;
	}

	
	protected static MethodDatabase db;
	
	
	public SuspendMethodVerifier() {
	}
	
	public static void onYield() {

		VerifyInfo vi = Stack.getVerifyInfo();
		if (vi == null) {
			return;
		}
		
		vi.quite = true;
		try {
			ArrayList<VerifyMethodInfo> stack = vi.tracerStacks;
			MethodInfo cmi = stack.get(stack.size() -1);
			if (db.meetTraceTargetClassMethod(cmi.owner, cmi.method)) {
				db.info("#%d onYield %s.%s", vi.vid , cmi.owner, cmi.method);
			}
			
			for (int i = stack.size() - 1; i > -1;  i--) {
				MethodInfo mi = stack.get(i);
				if (mi.suspendType != -1 || ("nginx/clojure/Coroutine".equals(mi.owner) && "resume()V".equals(mi.method))) {
					break;
				}
				
				ClassEntry ce = db.getClasses().get(mi.owner);
				
				if (ce == null) {
					vi.exception = true;
					db.error(new RuntimeException(String.format("#%d onYield: can not found ClassEntry for %s", vi.vid, mi.owner)));
					return;
				}
				
				if (!ce.isAlreadyInstrumented()) {
					vi.exception = true;
					db.error(new RuntimeException(String.format("#%d onYield: %s is not AlreadyInstrumented!", vi.vid, mi.owner)));
					return;
				}
				
//				boolean meetTraced = db.meetTraceTargetClassMethod(mi.owner, mi.method);
				Integer knownType = db.checkMethodSuspendType(mi.owner, mi.method, false, true);
				
				
				if (knownType == null || knownType < MethodDatabase.SUSPEND_NORMAL) {
					mi.suspendType = knownType == null ? MethodDatabase.SUSPEND_NONE : knownType;
//					db.warn("meet type %s != SUSPEND_NORMAL from %s.%s", MethodDatabase.SUSPEND_TYPE_STRS[knownType], mi.owner, mi.method);
					vi.exception = true;
					db.error( new RuntimeException(String.format("#%d onYield: meet type %s != SUSPEND_NORMAL from %s.%s", vi.vid,  MethodDatabase.SUSPEND_TYPE_STRS[knownType], mi.owner, mi.method) ));
				}else if (knownType > MethodDatabase.SUSPEND_NORMAL) {
					mi.suspendType = knownType;
//					if (meetTraced) {
//						db.info("meet traced method %s.%s, known suspend type=%s", mi.owner, mi.method, MethodDatabase.SUSPEND_TYPE_STRS[knownType]);
//					}
					//we need not record those records which has been defined by predefined configuration files
					db.warn("#%d onYield: meet predefined type %s != SUSPEND_NORMAL from %s.%s", vi.vid, MethodDatabase.SUSPEND_TYPE_STRS[knownType], mi.owner, mi.method);
					continue;
				}else {
					mi.suspendType = MethodDatabase.SUSPEND_NORMAL;
//					if (meetTraced) {
//						db.info("meet traced method %s.%s, set unknown suspend type to =%s", mi.owner, mi.method, MethodDatabase.SUSPEND_TYPE_STRS[knownType]);
//					}
				}
			}
		}finally {
			vi.quite = false;
		}
		
		
	}
	
	public static void enter(String owner, String method) {
		
		VerifyInfo vi = Stack.getVerifyInfo();
		if (vi == null) {
			return;
		}
		if (vi.quite || vi.exception) {
			return;
		}
		vi.quite = true;
		ArrayList<VerifyMethodInfo> stack = vi.tracerStacks;
		try {
			if (db.meetTraceTargetClassMethod(owner, method)) {
				db.info("#%d enter %s.%s", vi.vid , owner, method);
			}
			stack.add(new VerifyMethodInfo(owner, method));
		}finally{
			vi.quite = false;
		}
	}
	
	public static void downProxyInvoke(Method m) {

		VerifyInfo vi = Stack.getVerifyInfo();
		if (vi == null) {
			return;
		}
		
		if (vi.quite || vi.exception) {
			return;
		}
		enter(Type.getInternalName(m.getDeclaringClass()), m.getName()+Type.getMethodDescriptor(m));
	}
	
	public static void upProxyInvoke(Method m) {
		VerifyInfo vi = Stack.getVerifyInfo();
		if (vi == null) {
			return;
		}
		
		if (vi.quite || vi.exception) {
			return;
		}
		leave(Type.getInternalName(m.getDeclaringClass()), m.getName()+Type.getMethodDescriptor(m));
	}
	
	public static void leave(String owner, String method) {
		VerifyInfo vi = Stack.getVerifyInfo();
		if (vi == null) {
			return;
		}
		if (vi.quite || vi.exception) {
			return;
		}
		vi.quite = true;
		
		ArrayList<VerifyMethodInfo> stack = vi.tracerStacks;
		try{
			if (db.meetTraceTargetClassMethod(owner, method)) {
				db.info("#%d leave %s.%s", vi.vid , owner, method);
			}
			MethodInfo mi = stack.get(stack.size() - 1);
			if (!mi.owner.equals(owner) || !mi.method.equals(method)) {
				vi.exception = true;
				db.error(new RuntimeException(String.format("#%d Thread #%d, leave != enter %s.%s != %s.%s", vi.vid, Thread
						.currentThread().getId(), owner, method, mi.owner,
						mi.method)));
				return;
			}else {
				stack.remove(stack.size() - 1);
			}
		}finally{
			vi.quite = false;
		}
	}

}
