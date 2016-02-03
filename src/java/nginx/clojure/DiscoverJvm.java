/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure;

import java.io.File;
import java.lang.reflect.Method;



public class DiscoverJvm {

	public DiscoverJvm() {
	}

	public static File search(File dir) {
		if (!dir.exists()) {
			return null;
		}
		for (File f : dir.listFiles()) {
			if (f.isDirectory()) {
				File r = search(f);
				if (r != null) {
					return r;
				}
			}else {
				String fn = f.getName();
				if (fn.endsWith("jvm.so") || fn.endsWith("jvm.dll") || fn.endsWith("jvm.dylib")) {
					return f;
				}
			}
		}
		return null;
	}
	
	public static String getJvm() {
		//jrePath e.g. usr/lib/jvm/java-7-oracle/jre/lib/amd64
		String jrePath = System.getProperty("sun.boot.library.path");
		if (jrePath == null) {
			System.err.println("System property sun.boot.library.path not found!");
			return null;
		}
		//pick server jvm first
		File jvm = search(new File(jrePath, "server"));
		if (jvm == null) {
			jvm = search(new File(jrePath));
			if (jvm == null) {
				System.err.println("jvm not found!");
				return null;
			}
		}
		
		return (jvm.getAbsolutePath());
	}
	
	public static String getJniIncludes() {
		String home = System.getProperty("java.home");
		if (home.endsWith("jre")) {
			home = new File(home).getParent();
		}
		
		String incRoot = home + "/include";
		File incRootFile = new File(incRoot);
		if (!incRootFile.isDirectory()) {
			System.err.println("Failed to detect JNI header path, please set JNI_INCS manually, e.g :");
			System.err.println("  export JNI_INCS=\"-I /usr/lib/jvm/java-7-oracle/include -I /usr/lib/jvm/java-7-oracle/include/linux\"");
			return null;
		}
		
		StringBuilder sb = new StringBuilder("-I \"" + incRoot+"\"");
		for (File f : incRootFile.listFiles()) {
			if (f.isDirectory()) {
				sb.append(" -I \"").append(f.getAbsolutePath()+"\"");
			}
		}
		return sb.toString(); 
	}
	
	public static String detectOSArchExt() {
		String os = System.getProperty("os.name").toLowerCase().replaceAll("\\s+", "");
		String ext = ".so";
		if (os.startsWith("windows")) {
			os = "windows";
			ext = ".dll";
		}else if (os.equals("macosx")) {
			ext = ".dylib";
		}
		String arc = System.getProperty("os.arch").toLowerCase();
		if (arc.endsWith("amd64") || arc.endsWith("x86_64")) {
			arc = "x64";
		}
		return os +"-"+ arc + ext;
	}

	public static void main(String[] args) {
		if (args.length == 0) {
			System.out.println(getJvm());
			return;
		}
		try{
			String arg = args[0];
			Method m = DiscoverJvm.class.getMethod(arg);
			System.out.println(m.invoke(null));
		}catch(Throwable e) {
			e.printStackTrace();
		}

	}

}
