package nginx.clojure.wave;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import nginx.clojure.asm.Type;
import nginx.clojure.wave.MethodDatabase.ClassEntry;


public class SuspendMethodTracer {
	
	public static class MethodInfo {
		public String owner;
		public String method;
		public Integer suspendType = -1; 
		
		public MethodInfo() {
		}

		public MethodInfo(String owner, String method) {
			super();
			this.owner = owner;
			this.method = method;
		}
		
		@Override
		public String toString() {
			return owner + "." + method;
		}
	}

	protected static ThreadLocal<ArrayList<MethodInfo> > tracerStacks = new ThreadLocal<ArrayList<MethodInfo>>();
	
	protected static ConcurrentHashMap<Long, ArrayList<MethodInfo>> threadTraceStacks = new ConcurrentHashMap<Long, ArrayList<MethodInfo>>();
	
	protected static ThreadLocal<Boolean> quiteFlags = new ThreadLocal<Boolean>();
	
	protected static MethodDatabase db;
	
	public static ArrayList<MethodInfo> fetchStack() {
		ArrayList<MethodInfo> stack = tracerStacks.get();
		if (stack == null) {
			tracerStacks.set(stack = new ArrayList<MethodInfo>());
			threadTraceStacks.put(Thread.currentThread().getId(), stack);
			quiteFlags.set(false);
		}
		assert stack == threadTraceStacks.get(Thread.currentThread().getId());
		return stack;
	}
	
	public SuspendMethodTracer() {
	}
	
	protected static Map<String, Set<String>> SUSPEND_CAUSE_SET = new HashMap<String, Set<String>>();
	
	protected static ConcurrentHashMap<String, ConcurrentHashMap<String, Object>> SUSPEND_INFO_RESULTS = new ConcurrentHashMap<String, ConcurrentHashMap<String, Object>>();
	
	protected static ConcurrentHashMap<String,  String> FUZZ_TO_ORG_MAP = new ConcurrentHashMap<String, String>();
	
	static {
//SocketInputStream
//		  public int read(byte[]) throws java.io.IOException;
//		    Signature: ([B)I
//
//		  public int read(byte[], int, int) throws java.io.IOException;
//		    Signature: ([BII)I
//
//		  int read(byte[], int, int, int) throws java.io.IOException;
//		    Signature: ([BIII)I
//
//		  public int read() throws java.io.IOException;
//		    Signature: ()I
//
//		  public long skip(long) throws java.io.IOException;
//		    Signature: (J)J
//
//		  public int available() throws java.io.IOException;
//		    Signature: ()I
//
//		  public void close() throws java.io.IOException;
//		    Signature: ()V
		{
		Set<String> socketInputMethods = new HashSet<String>();
		socketInputMethods.add("read([B)I");
		socketInputMethods.add("read([BII)I");
		socketInputMethods.add("read([BIII)I");
		socketInputMethods.add("read()I");
		socketInputMethods.add("skip(J)J");
		SUSPEND_CAUSE_SET.put("java/net/SocketInputStream", socketInputMethods);
		}
		
//SocketOutputStream
//		  public void write(int) throws java.io.IOException;
//		    Signature: (I)V
//
//		  public void write(byte[]) throws java.io.IOException;
//		    Signature: ([B)V
//
//		  public void write(byte[], int, int) throws java.io.IOException;
//		    Signature: ([BII)V
		{
		Set<String> socketOutputMethods = new HashSet<String>();
		socketOutputMethods.add("write(I)V");
		socketOutputMethods.add("write([B)V");
		socketOutputMethods.add("write([BII)V");
		SUSPEND_CAUSE_SET.put("java/net/SocketOutputStream", socketOutputMethods);
		}

//Socket
//		  public java.net.Socket(java.net.Proxy);
//		    Signature: (Ljava/net/Proxy;)V
//
//		  public java.net.Socket(java.lang.String, int) throws java.net.UnknownHostException, java.io.IOException;
//		    Signature: (Ljava/lang/String;I)V
//
//		  public java.net.Socket(java.net.InetAddress, int) throws java.io.IOException;
//		    Signature: (Ljava/net/InetAddress;I)V
//
//		  public java.net.Socket(java.lang.String, int, java.net.InetAddress, int) throws java.io.IOException;
//		    Signature: (Ljava/lang/String;ILjava/net/InetAddress;I)V
//
//		  public java.net.Socket(java.net.InetAddress, int, java.net.InetAddress, int) throws java.io.IOException;
//		    Signature: (Ljava/net/InetAddress;ILjava/net/InetAddress;I)V
//
//		  public java.net.Socket(java.lang.String, int, boolean) throws java.io.IOException;
//		    Signature: (Ljava/lang/String;IZ)V
//
//		  public java.net.Socket(java.net.InetAddress, int, boolean) throws java.io.IOException;
//		    Signature: (Ljava/net/InetAddress;IZ)V
//
//
//		  public void connect(java.net.SocketAddress) throws java.io.IOException;
//		    Signature: (Ljava/net/SocketAddress;)V
//
//		  public void connect(java.net.SocketAddress, int) throws java.io.IOException;
//		    Signature: (Ljava/net/SocketAddress;I)V
//
//		  public void bind(java.net.SocketAddress) throws java.io.IOException;
//		    Signature: (Ljava/net/SocketAddress;)V
//
//        private java.net.Socket(java.net.SocketAddress, java.net.SocketAddress, boolean) throws java.io.IOException;
//	        Signature: (Ljava/net/SocketAddress;Ljava/net/SocketAddress;Z)V
//

		{
		Set<String> socketMethods = new HashSet<String>();
//		socketMethods.add("<init>(Ljava/net/Proxy;)V");
		socketMethods.add("<init>(Ljava/lang/String;I)V");
		socketMethods.add("<init>(Ljava/net/InetAddress;I)V");
		socketMethods.add("<init>(Ljava/lang/String;ILjava/net/InetAddress;I)V");
		socketMethods.add("<init>(Ljava/net/InetAddress;ILjava/net/InetAddress;I)V");
		socketMethods.add("<init>(Ljava/lang/String;IZ)V");
		socketMethods.add("<init>(Ljava/net/SocketAddress;Ljava/net/SocketAddress;Z)V");
		socketMethods.add("<init>(Ljava/net/InetAddress;IZ)V");
		socketMethods.add("connect(Ljava/net/SocketAddress;)V");
		socketMethods.add("connect(Ljava/net/SocketAddress;I)V");

//		socketMethods.add("bind(Ljava/net/InetAddress;IZ)V");
		
		SUSPEND_CAUSE_SET.put("java/net/Socket", socketMethods);
		}
		
		{
			Set<String> coroutineMethods = new HashSet<String>();
			coroutineMethods.add("_yieldp()V");
			SUSPEND_CAUSE_SET.put("nginx/clojure/Coroutine", coroutineMethods);
		}
	}
	
	public static boolean isSuspend(String owner, String method) {
		Set<String> methodSet = SUSPEND_CAUSE_SET.get(owner);
		return methodSet != null && methodSet.contains(method);
	}
	
	public static void enter(String owner, String method) {
		ArrayList<MethodInfo> stack = fetchStack();
		if (quiteFlags.get()) {
			return;
		}
		quiteFlags.set(true);
		try {
			if (db.meetTraceTargetClassMethod(owner, method)) {
				db.info("enter %s.%s", owner, method);
			}
			if (isSuspend(owner, method)) {
				for (int i = stack.size() - 1; i > -1;  i--) {
					MethodInfo mi = stack.get(i);
					if (mi.suspendType == MethodDatabase.SUSPEND_NORMAL || mi.suspendType == MethodDatabase.SUSPEND_NONE) {
						break;
					}
					boolean meetTraced = db.meetTraceTargetClassMethod(mi.owner, mi.method);
					Integer knownType = db.checkMethodSuspendType(mi.owner, mi.method, false, false);
					if (knownType != null && knownType >= MethodDatabase.SUSPEND_NORMAL) {
						mi.suspendType = knownType;
						if (meetTraced) {
							db.info("meet traced method %s.%s, known suspend type=%s", mi.owner, mi.method, MethodDatabase.SUSPEND_TYPE_STRS[knownType]);
						}
						//we need not record those records which has been defined by predefined configuration files
						continue;
					}else {
						mi.suspendType = MethodDatabase.SUSPEND_NORMAL;
						if (meetTraced) {
							db.info("meet traced method %s.%s, set unknown suspend type to =%s", mi.owner, mi.method, MethodDatabase.SUSPEND_TYPE_STRS[knownType]);
						}
					}
					
					String key = mi.owner;
					String fowner = MethodDatabaseUtil.toFuzzyString(MethodDatabaseUtil.FUZZY_CLASS_PATTERN, mi.owner, MethodDatabaseUtil.FUZZY_CLASS_PATTERN.toString());
					if (fowner != null) {
						FUZZ_TO_ORG_MAP.put(fowner, key);
						key = fowner;
					}
					
					ConcurrentHashMap<String, Object> omis = SUSPEND_INFO_RESULTS.get(key);
					ConcurrentHashMap<String, Object> mis = omis;
					
					if (omis == null) {
						omis = SUSPEND_INFO_RESULTS.putIfAbsent(key, mis = new ConcurrentHashMap<String, Object>());
						if (omis != null) {
							mis = omis;
						}
					}
					if (db != null && db.isDebug()) {
						mis.put(mi.method, new Object[] {new Exception().getStackTrace(), new ArrayList<MethodInfo>(stack)});
					}else {
						mis.put(mi.method, "");
					}
				}
			}
			stack.add(new MethodInfo(owner, method));
		}finally{
			quiteFlags.set(false);
		}
	}
	
	public static void downProxyInvoke(Method m) {
		if (quiteFlags.get()) {
			return;
		}
		enter(Type.getInternalName(m.getDeclaringClass()), m.getName()+Type.getMethodDescriptor(m));
	}
	
	public static void upProxyInvoke(Method m) {
		if (quiteFlags.get()) {
			return;
		}
		leave(Type.getInternalName(m.getDeclaringClass()), m.getName()+Type.getMethodDescriptor(m));
	}
	
	public static void leave(String owner, String method) {
		if (quiteFlags.get()) {
			return;
		}
		quiteFlags.set(true);
		try{
			if (db.meetTraceTargetClassMethod(owner, method)) {
				db.info("leave %s.%s", owner, method);
			}
			ArrayList<MethodInfo> stack = fetchStack();
			MethodInfo mi = stack.get(stack.size() - 1);
			if ( !(mi.owner == owner || mi.owner.equals(owner) ) || !mi.method.equals(method)) {
				quiteFlags.set(true);
				db.error("Thread #%d, leave != enter %s.%s != %s.%s", Thread
						.currentThread().getId(), owner, method, mi.owner,
						mi.method);
				db.error("thread list: %s", threadTraceStacks.keySet().toString());
			}else {
				stack.remove(stack.size() - 1);
			}
		}finally{
			quiteFlags.set(false);
		}
	}
	
	public static void markSuper(String clz, Set<String> methods, Map<String, TreeMap<String, String>> upperMarks) {
		ClassEntry ce = db.getClasses().get(clz);
		if (ce == null) {
			db.warn("can not found class %s in db, maybe its' suspend info defined in orginal configuration file");
			return;
		}
		String[] itfs = ce.getInterfaces();
		if (itfs != null) {
			for (String itf : ce.getInterfaces()) {
				markSuper(itf, clz, methods, upperMarks);
			}
			String sclz = ce.getSuperName();
			if (sclz != null) {
				markSuper(sclz, clz, methods, upperMarks);
			}
		}
	}

	public static void markSuper(String parent, String child, Set<String> methods,
			Map<String, TreeMap<String, String>> upperMarks) {
		ClassEntry ice = db.getClasses().get(parent);
		if (ice != null) {
			Set<String> tmp = new HashSet<String>(methods);
			tmp.retainAll(ice.getMethods().keySet());
			if (!tmp.isEmpty()) {
				TreeMap<String, String> ms = upperMarks.get(parent);
				if (ms == null) {
					upperMarks.put(parent, ms = new TreeMap<String, String>());
				}
				for (String m : tmp) {
					Integer knownType = db.checkMethodSuspendType(child, m, false, false);
					if (knownType != null && knownType >= MethodDatabase.SUSPEND_JUST_MARK) {
						continue;
					}
					ms.put(m, child);
				}
			}
			markSuper(parent, methods, upperMarks);
		}
	}
	
	public static void load(InputStream in, ConcurrentHashMap<String, ConcurrentHashMap<String, Object>> result, Map<String, TreeMap<String, String>> upperMarks) throws IOException {

		BufferedReader r = null;
		try{
			r = new BufferedReader(new InputStreamReader(in, MethodDatabase.UTF_8));
			String line;
			String lastLine = null;
			String clz = null;
			int lc = 0;
			while ((line = r.readLine()) != null) {
				lc ++;
				line = line.trim();
				if (line.startsWith("#") || line.length() == 0) {
					lastLine = line;
					continue;
				}else if (line.startsWith("lazyclass:")) {
					clz = line.substring("lazyclass:".length());
				}else if (line.startsWith("fuzzylass:")) {
					clz = line.substring("fuzzylass:".length());
				}else if (line.startsWith("class:")) {
					clz = line.substring("class:".length());
				}else {
					String[] md = line.split(":");
					if (clz == null) {
						if (db != null) {
							quiteFlags.set(true);
							db.error("line:%d, method %s without class defined before", lc, md[0]);
							quiteFlags.set(false);
						}
						lastLine = line;
						continue;
					}
					if (MethodDatabase.SUSPEND_NORMAL_STR.equals(md[1])) {
						ConcurrentHashMap<String, Object> classMethods = result.get(clz);
						if (classMethods == null) {
							result.put(clz, classMethods = new ConcurrentHashMap<String, Object>());
						}
						classMethods.put(md[0], "");
					}else if (MethodDatabase.SUSPEND_JUST_MARK_STR.equals(md[1])) {
						TreeMap<String, String> upMethods = upperMarks.get(clz);
						if (upMethods == null){
							upperMarks.put(clz, upMethods = new TreeMap<String, String>());
						}
						if (lastLine != null && lastLine.startsWith("#mark from sub")){
							upMethods.put(md[0], lastLine);
						}else {
							upMethods.put(md[0], "unknown from merge orignal file");
						}
						
					}
				}
				lastLine = line;
			}
		}finally{
			if (r != null) {
				r.close();
			}
		}
	
	}
	
	public static void load(String path, ConcurrentHashMap<String, ConcurrentHashMap<String, Object>> result, Map<String, TreeMap<String, String>> upperMarks) throws IOException {
		load(new FileInputStream(path), result, upperMarks);
	}
	
	public static void dump() throws IOException {
		String file = System.getProperty("nginx.clojure.wave.CfgToolOutFile");
		if (file == null) {
			file = "nginx.clojure.wave.cfgtooloutfile";
		}
		dump(file, false);
	}
	
	@SuppressWarnings("unchecked")
	public static void dump(String path, boolean append) throws IOException {
		quiteFlags.set(true);
		Map<String, TreeMap<String, String>> upperMarks = new TreeMap<String, TreeMap<String, String>>();
		
		if (append) {
			load(path, SUSPEND_INFO_RESULTS, upperMarks);
		}
		db.info("dumping auto generated class waving configurations...........");
		PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(new File(path)), MethodDatabase.UTF_8));
		writer.printf("############Generated By Nginx-Clojure SuspendMethodTracer %1$tY-%1$tm-%1$td ##############\r\n", new Date());
		if (!db.getUserDefinedWaveConfigFiles().isEmpty()) {
			writer.printf("#######Notice: Ingored Waving information from current configuration file : %s\r\n", db.getUserDefinedWaveConfigFiles().toString());
		}
		
		
		try {
			for (Entry<String, ConcurrentHashMap<String,Object>> en : new TreeMap<String, ConcurrentHashMap<String,Object>>(SUSPEND_INFO_RESULTS).entrySet()) {
				String clz = en.getKey();
				boolean isfuzzy = false;
				if (clz.indexOf(MethodDatabaseUtil.FUZZY_CLASS_PATTERN.toString()) > -1) {
					isfuzzy = true;
					writer.printf("fuzzyclass:%s\r\n", clz);
				}else {
					writer.printf("lazyclass:%s\r\n", clz);
				}
				
				
				if (db.meetTraceTargetClass(clz)) {
					db.info("dumping meet traced class %s", clz);
				}
				
				if (!isfuzzy) {
					markSuper(clz, en.getValue().keySet(), upperMarks);
				}else {
					markSuper(FUZZ_TO_ORG_MAP.get(clz), en.getValue().keySet(), upperMarks);
				}
				
				for (Entry<String, Object> me : new TreeMap<String, Object>(en.getValue()).entrySet()) {
					String method = me.getKey();
					writer.printf("  %s:normal\r\n", method);
					if (db.meetTraceTargetClass(method)) {
						db.info("dumping meet traced method %s", method);
					}
					if (db != null && db.isDebug() && "" != me.getValue()) {
						writer.printf("#from trace:---------------------------------------\r\n");
						Object[] dinfo = (Object[])me.getValue();
						for (StackTraceElement se : (StackTraceElement[])dinfo[0]) {
							writer.printf("####%s.%s(%s:%s)\r\n", se.getClassName(), se.getMethodName(), se.getFileName(), se.getLineNumber());
						}
						for (MethodInfo mi : (List<MethodInfo>) dinfo[1]) {
							writer.printf("#--->%s.%s\r\n", mi.owner, mi.method);
						}
					}
				}
				writer.printf("\r\n");
			}
			
			for (Entry<String, TreeMap<String, String>> umen : upperMarks.entrySet()) {
				if (umen.getValue().isEmpty()) {
					continue;
				}
				writer.printf("lazyclass:%s\r\n", umen.getKey());
				for (Entry<String, String> me : umen.getValue().entrySet()) {
					writer.printf("#mark from sub %s\r\n", me.getValue());
					writer.printf("  %s:just_mark\r\n", me.getKey());
				}
				writer.printf("\r\n");
			}
		}finally{
			writer.close();
			quiteFlags.set(false);
			db.info("dumping done!");
		}
	}

}
