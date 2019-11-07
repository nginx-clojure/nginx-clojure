/*
 * Copyright (c) 2008-2013, Matthias Mann
 * Copyright (C) 2014 Zhang,Yuexiang (xfeep)
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 *     * Redistributions of source code must retain the above copyright notice,
 *       this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of Matthias Mann nor the names of its
 *       contributors may be used to endorse or promote products derived from
 *       this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package nginx.clojure.wave;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import nginx.clojure.SuspendExecution;
import nginx.clojure.asm.ClassReader;
import nginx.clojure.asm.Type;
import nginx.clojure.logger.LoggerService;
import nginx.clojure.logger.TinyLogService;
import nginx.clojure.logger.TinyLogService.MsgType;
import nginx.clojure.wave.SuspendMethodVerifier.VerifyVarInfo;

/**
 * <p>Collects information about classes and their suspendable methods.</p>
 * <p>Provides access to configuration parameters and to logging</p>
 * 
 * @author Matthias Mann
 * @author Zhang,Yuexiang (xfeep)
 */
public class MethodDatabase implements LoggerService {
	
	public static final String NGINX_CLOJURE_LOG_WAVE_LEVEL = "nginx.clojure.logger.wave.level";
	
	public static final String SUSPEND_BLOCKING_STR = "blocking";
//	public static final String SUSPEND_IGNORE_STR = "ignore";
	public static final String SUSPEND_NONE_STR = "none";
	public static final String SUSPEND_JUST_MARK_STR = "just_mark";
	public static final String SUSPEND_NORMAL_STR = "normal";
	public static final String SUSPEND_FAMILY_STR = "family";
	public static final String SUSPEND_SKIP_STR = "skip";
	
	public static final Integer SUSPEND_NONE = 0;
	public static final Integer SUSPEND_BLOCKING = 1;
	
	public static final Integer SUSPEND_FAMILY = 2;
	/**
	 * Those methods with suspend type SUSPEND_JUST_MARK will not or can not be waved, 
	 * but because they will call some SUSPEND_NORMAL methods, their caller should take care
	 * of this case generally will be waved.
	 */
	public static final Integer SUSPEND_JUST_MARK = 3;
	
	public static final Integer SUSPEND_NORMAL = 4;
	
	public static final Integer SUSPEND_SKIP = 5;
	
	public static final String[] SUSPEND_TYPE_STRS = new String[] {
		SUSPEND_NONE_STR,
		SUSPEND_BLOCKING_STR,
		SUSPEND_FAMILY_STR,
		SUSPEND_JUST_MARK_STR,
		SUSPEND_NORMAL_STR,
		SUSPEND_SKIP_STR
	};
	
	
	public static final String EXCEPTION_NAME = Type.getInternalName(SuspendExecution.class);
	public static final String EXCEPTION_DESC = Type.getDescriptor(SuspendExecution.class);
	
	public static final Charset UTF_8 = Charset.forName("utf-8");
    
    private final ClassLoader cl;
    private final ConcurrentHashMap<String, ClassEntry> classes;
    
    private final ConcurrentHashMap<String, LazyClassEntry> lazyClasses;
    
    private final ArrayList<FuzzyLazyClassEntry> fuzzlyLazyClasses;
    
    private final ArrayList<String> retransformedClasses = new ArrayList<String>();
    
    private final ArrayList<File> workList;
    private final ArrayList<String> filters;
    private ArrayList<String> userDefinedWaveConfigFiles = new ArrayList<String>();
    
    private Map<String, VerifyVarInfo[][]> verfiyMethodInfos;
    
    private static LoggerService log;
    private boolean verify;
    private boolean dump;
    private boolean allowMonitors;
    private boolean allowBlocking;
    private boolean allowOutofCoroutine = true;
    private boolean hookDumpWaveCfg = false;
    private boolean doNothing = false;
    private boolean runTool = false;
    private Pattern traceClassPattern = null;
    private Pattern traceClassMethodPattern = null;
    
    private String dumpDir;
    
    public MethodDatabase(ClassLoader classloader) {
        if(classloader == null) {
            throw new NullPointerException("classloader");
        }
        
        this.cl = classloader;
        
        classes = new ConcurrentHashMap<String, ClassEntry>();
        lazyClasses = new ConcurrentHashMap<String, LazyClassEntry>();
        fuzzlyLazyClasses = new ArrayList<MethodDatabase.FuzzyLazyClassEntry>();
        workList = new ArrayList<File>();
        filters = new ArrayList<String>();
        getLog();
    }
    
    public ArrayList<String> getRetransformedClasses() {
		return retransformedClasses;
	}

    public boolean isAllowMonitors() {
        return allowMonitors;
    }

    public void setAllowMonitors(boolean allowMonitors) {
        this.allowMonitors = allowMonitors;
    }

    public Pattern getTraceClassPattern() {
		return traceClassPattern;
	}

	public void setTraceClassPattern(Pattern traceClassPattern) {
		this.traceClassPattern = traceClassPattern;
	}
	
	public Pattern getTraceClassMethodPattern() {
		return traceClassMethodPattern;
	}
	
	public void setTraceClassMethodPattern(Pattern traceClassMethodPattern) {
		this.traceClassMethodPattern = traceClassMethodPattern;
	}

	public boolean isAllowBlocking() {
        return allowBlocking;
    }

    public void setAllowBlocking(boolean allowBlocking) {
        this.allowBlocking = allowBlocking;
    }
    
    public boolean isAllowOutofCoroutine() {
		return allowOutofCoroutine;
	}
    
    public void setAllowOutofCoroutine(boolean allowOutofCoroutine) {
		this.allowOutofCoroutine = allowOutofCoroutine;
	}

    public ConcurrentHashMap<String, ClassEntry> getClasses() {
		return classes;
	}
    
    public Map<String, VerifyVarInfo[][]> getVerfiyMethodInfos() {
		return verfiyMethodInfos;
	}
    
    public ConcurrentHashMap<String, LazyClassEntry> getLazyClasses() {
		return lazyClasses;
	}
    
    public ArrayList<FuzzyLazyClassEntry> getFuzzlyLazyClasses() {
		return fuzzlyLazyClasses;
	}
    
    public static LoggerService getLog() {
        if (log == null) {
        	log = new TinyLogService(TinyLogService.getSystemPropertyOrDefaultLevel(NGINX_CLOJURE_LOG_WAVE_LEVEL, MsgType.error), System.err, System.err);
        }
        return log;
    }

    public  static void setLog(LoggerService log) {
        MethodDatabase.log = log;
    }

    
    public void setRunTool(boolean runTool) {
		this.runTool = runTool;
	}
    
    public boolean isRunTool() {
		return runTool;
	}

    public void setDoNothing(boolean doNothing) {
		this.doNothing = doNothing;
	}
    
    public boolean isDoNothing() {
		return doNothing;
	}
    
    public boolean isDump() {
		return dump;
	}
    
    public void setDump(boolean dump) {
		this.dump = dump;
	}
    
    public String getDumpDir() {
		return dumpDir;
	}
    
    public void setDumpDir(String dumpDir) {
		this.dumpDir = dumpDir;
	}
    
    public boolean isHookDumpWaveCfg() {
		return hookDumpWaveCfg;
	}
    
    public void setHookDumpWaveCfg(boolean hookDumpWaveCfg) {
		this.hookDumpWaveCfg = hookDumpWaveCfg;
	}
    
    public void setVerify(boolean verify) {
		this.verify = verify;
		if (verify && verfiyMethodInfos == null) {
			verfiyMethodInfos = new ConcurrentHashMap<String, VerifyVarInfo[][]>();
		}
	}
    
    public boolean isVerify() {
		return verify;
	}
    
    public boolean isDebug() {
        return log.isDebugEnabled();
    }

    public void setDebug(boolean debug) {
        if (log instanceof TinyLogService) {
			TinyLogService tlog = (TinyLogService) log;
			tlog.setLevel(MsgType.debug);
		}
    }

    
    public void error(String msg, Exception ex) {
        if(log != null) {
            log.error(msg, ex);
        }
    }
    
    public void checkClass(File f) {
        try {
            FileInputStream fis = new FileInputStream(f);
            CheckInstrumentationVisitor civ = checkFileAndClose(fis, f.getPath());
            
            if(civ != null) {
                recordSuspendableMethods(civ.getName(), civ.getClassEntry());

                if(civ.needsInstrumentation()) {
                    if(civ.isAlreadyInstrumented()) {
                        info("Found instrumented class: %s", f.getPath());
                    } else {
                        info("Found class: %s", f.getPath());
                        workList.add(f);
                    }
                }
            }
        } catch (UnableToInstrumentException ex) {
            throw ex;
        } catch (Exception ex) {
            error(f.getPath(), ex);
        }
    }
    

    public ArrayList<String> getFilters() {
		return filters;
	}
    
    protected Integer checkMethodFamilySuspendType(ClassEntry ce, String fullname) {
    	Integer st = ce.check(fullname);
    	Integer fst = st;
    	if (st == null || st == SUSPEND_NONE) {
    		if (ce.getSuperName() != null) {
    			ClassEntry sce = classes.get(ce.getSuperName());
        		if (sce != null) {
        			st = checkMethodFamilySuspendType(sce, fullname);
        		}
        		if (st != null && (fst == null || fst == SUSPEND_NONE)) {
            		fst = st;
            	}
    		}
    		if ((st == null || st == SUSPEND_NONE) && ce.interfaces != null) {
    			for (String itf : ce.interfaces) {
    				ClassEntry sce = classes.get(itf);
    				if (sce != null) {
    					st = checkMethodFamilySuspendType(sce, fullname);
    					if (st != null && (fst == null || fst == SUSPEND_NONE)) {
    		        		fst = st;
    		        	}
    				}
    			}
    		}
    	}
        return st == null ? fst : st;
    }
    
    public final Integer checkMethodSuspendType(String className, String method, boolean searchSuperClass) {
    	return checkMethodSuspendType(className, method, searchSuperClass, true);
    }
    
    public final Integer checkMethodSuspendType(String className, String method, boolean searchSuperClass, boolean returnDefault) {
        if(method.charAt(0) == '<' && method.charAt(1) == 'c') {
            return SUSPEND_NONE;   // special methods are never suspendable
        }
        
        if (className == null) {
        	return SUSPEND_NORMAL;
        }
        
//        if(isJavaCore(className)) {
//            return SUSPEND_NONE;
//        }
//        String fullname = ClassEntry.key(methodName, methodDesc);
        ClassEntry ce = MethodDatabaseUtil.buildClassEntryFamily(this, className);
        if (ce == null) {
        	if (method.charAt(0) == '<') {
        		warn("contructor's class not found - assuming not suspendable: %s#%s", className, method);
        		return SUSPEND_NONE;
        	}
        	warn("not found class - assuming suspendable: %s#%s", className, method);
        	return SUSPEND_NORMAL;
        }
        Integer st = null;
        if (searchSuperClass) {
        	st =  checkMethodFamilySuspendType(ce, method);
        }else {
        	st = ce.check(method);
        }
       
        if (st == null && returnDefault) {
        	if(method.charAt(0) == '<') {
        		return SUSPEND_NONE;
        	}
        	 warn("Method not found in class - assuming suspendable: %s#%s", className, method);
             st = SUSPEND_NORMAL;
        }
        return st;
    }


    public void recordSuspendableMethods(String className, ClassEntry entry) {
        ClassEntry oldEntry;
        synchronized(this) {
            oldEntry = classes.put(className, entry);
        }
        if (log.isDebugEnabled()) {
            if(oldEntry != null) {
                if(!oldEntry.equals(entry)) {
                	warn("Duplicate class entries with different data for class: %s", className);
                }
            }
        }
    }
    
	private  boolean typeImplements(String type, ClassEntry ce, String itf) {
		while (!"java/lang/Object".equals(type)) {
			String[] itfs = ce.getInterfaces();
			for (int i = 0; i < itfs.length; ++i) {
				if (itfs[i].equals(itf)) {
					return true;
				}
			}
			for (int i = 0; i < itfs.length; ++i) {
				if (typeImplements(itfs[i], MethodDatabaseUtil.buildClassEntryFamily(this, itfs[i]), itf)) {
					return true;
				}
			}
			type = ce.getSuperName();
			ce = MethodDatabaseUtil.buildClassEntryFamily(this, type);
		}
		return false;
	}
    
    public String getCommonSuperClass(String classA, String classB) {
    	ClassEntry ace = MethodDatabaseUtil.buildClassEntryFamily(this, classA);
    	ClassEntry bce = MethodDatabaseUtil.buildClassEntryFamily(this, classB);
    	if (ace == null || bce == null) {
    		return "java/lang/Object"; 
    	}
		if (ace.isInterface && bce.isInterface) {
			if (typeImplements(classA, ace, classB)) {
				return classB;
			}
			if (typeImplements(classB, bce, classA)) {
				return classA;
			} 
			return "java/lang/Object";
		}
		
		if (ace.isInterface) {
			if (typeImplements(classB, bce, classA)) {
				return classA;
			} else {
				return "java/lang/Object";
			}
		}
		if (bce.isInterface) {
			if (typeImplements(classA, ace, classB)) {
				return classB;
			} else {
				return "java/lang/Object";
			}
		}
        ArrayList<String> listA = getSuperClasses(classA);
        ArrayList<String> listB = getSuperClasses(classB);
        if(listA == null || listB == null) {
            return null;
        }
        int idx = 0;
        int num = Math.min(listA.size(), listB.size());
        for(; idx<num ; idx++) {
            String superClassA = listA.get(idx);
            String superClassB = listB.get(idx);
            if(!superClassA.equals(superClassB)) {
                break;
            }
        }
        if(idx > 0) {
            return listA.get(idx-1);
        }
        return null;
    }

    public void debug(Object message) {
			log.debug(message);
	}

	public void debug(Object message, Throwable t) {
			log.debug(message, t);
	}

	public void debug(String format, Object... objects) {
			log.debug(format, objects);
	}

	public void error(Object message) {
		log.error(message);
	}

	public void error(Object message, Throwable t) {
		log.error(message, t);
	}

	public void error(String format, Object... objects) {
		log.error(format, objects);
	}

	public void fatal(Object message) {
		log.fatal(message);
	}

	public void fatal(Object message, Throwable t) {
		log.fatal(message, t);
	}

	public void fatal(String format, Object... objects) {
		log.fatal(format, objects);
	}

	public void info(Object message) {
		log.info(message);
	}

	public void info(Object message, Throwable t) {
		log.info(message, t);
	}

	public void info(String format, Object... objects) {
		log.info(format, objects);
	}

	public boolean isDebugEnabled() {
		return log.isDebugEnabled();
	}

	public boolean isErrorEnabled() {
		return log.isErrorEnabled();
	}

	public boolean isFatalEnabled() {
		return log.isFatalEnabled();
	}

	public boolean isInfoEnabled() {
		return log.isInfoEnabled();
	}

	public boolean isTraceEnabled() {
		return log.isTraceEnabled();
	}

	public boolean isWarnEnabled() {
		return log.isWarnEnabled();
	}

	public void trace(Object message) {
		log.trace(message);
	}

	public void trace(Object message, Throwable t) {
		log.trace(message, t);
	}

	public void warn(Object message) {
		log.warn(message);
	}

	public void warn(Object message, Throwable t) {
		log.warn(message, t);
	}

	public void trace(String format, Object... objects) {
		log.trace(format, objects);
	}

	@Override
	public void warn(String format, Object... objects) {
		log.warn(format, objects);
	}
	
	public boolean isException(String className) {
        for(;;) {
            if("java/lang/Throwable".equals(className)) {
                return true;
            }
            if("java/lang/Object".equals(className)) {
                return false;
            }

            String superClass = getDirectSuperClass(className);
            if(superClass == null) {
                warn("Can't determine super class of %s", className);
                return false;
            }
            className = superClass;
        }
    }

    public ArrayList<File> getWorkList() {
        return workList;
    }

    /**
     * <p>Overwrite this function if Coroutines is used in a transformation chain.</p>
     * <p>This method must create a new CheckInstrumentationVisitor and visit the
     * specified class with it.</p>
     * @param className the class the needs to be analysed
     * @return a new CheckInstrumentationVisitor that has visited the specified
     * class or null if the class was not found
     */
    public  CheckInstrumentationVisitor checkClass(String className) {
        InputStream is = cl.getResourceAsStream(className + ".class");
        if(is != null) {
            return checkFileAndClose(is, className);
        }
        return null;
    }
    
    public ClassLoader getClassLoader() {
    	return cl;
    }
    
    public  CheckInstrumentationVisitor checkClass(ClassReader r) {
		try {
			CheckInstrumentationVisitor civ = new CheckInstrumentationVisitor();
			r.accept(civ, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES
					| ClassReader.SKIP_CODE);
			return civ;
		} catch (UnableToInstrumentException ex) {
			throw ex;
		} catch (Exception ex) {
			error(r.getClassName(), ex);
		}
		return null;
    }
    
    public CheckInstrumentationVisitor checkFileAndClose(InputStream is, String name) {
        try {
            try {
                ClassReader r = new ClassReader(is);

                CheckInstrumentationVisitor civ = new CheckInstrumentationVisitor();
                r.accept(civ, ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES|ClassReader.SKIP_CODE);
                
                return civ;
            } finally {
                is.close();
            }
        } catch (UnableToInstrumentException ex) {
            throw ex;
        } catch (Exception ex) {
            error(name, ex);
        }
        return null;
    }

    
    public boolean isInterface(String className) {
    	ClassEntry ce = MethodDatabaseUtil.buildClassEntryFamily(this, className);
    	return ce != null && ce.isInterface;
    }

    private ArrayList<String> getSuperClasses(String className) {
        ArrayList<String> result = new ArrayList<String>();
        for(;;) {
            result.add(0, className);
            if("java/lang/Object".equals(className)) {
                return result;
            }

            String superClass = getDirectSuperClass(className);
            if(superClass == null) {
                return result;
            }
            className = superClass;
        }
    }

    protected String getDirectSuperClass(String className) {
    	ClassEntry ce = MethodDatabaseUtil.buildClassEntryFamily(this, className);
    	return ce == null ? "java/lang/Object" : ce.superName;
    }
    
    public boolean shouldIgnore(String className) {
    	if (className == null) {
    		return false;
    	}
    	
    	for (String f : filters) {
    		if (className.startsWith(f)) {
    			return true;
    		}
    	}
    	return false;
    }
    
    public boolean meetTraceTargetClass(String clz) {
    	return traceClassPattern != null && traceClassPattern.matcher(clz).find();
    }
    
    public boolean meetTraceTargetClassMethod(String clz, String method) {
    	return traceClassMethodPattern != null && traceClassMethodPattern.matcher(clz + "." + method).find();
    }
    
    public boolean meetTraceTargetClassMethod(String clzAndmethod) {
    	return traceClassMethodPattern != null && traceClassMethodPattern.matcher(clzAndmethod).find();
    }
    
    public static boolean isJavaCore(String className) {
        return className.startsWith("java/") || className.startsWith("javax/") ||
                className.startsWith("sun/") || className.startsWith("com/sun/");
    }
    
    public static String[] toStringArray(List<?> l) {
        if(l.isEmpty()) {
            return null;
        }
        return l.toArray(new String[l.size()]);
    }
    
    public ArrayList<String> getUserDefinedWaveConfigFiles() {
		return userDefinedWaveConfigFiles;
	}
    
//    private static final ClassEntry CLASS_NOT_FOUND = new ClassEntry("<class not found>", new String[0], false);

    
    public static final class LazyClassEntry {
    	private final LinkedHashMap<String, Integer> methods = new LinkedHashMap<String, Integer>();
    	private final String resource;
    	public LazyClassEntry(String resource) {
    		this.resource = resource;
		}
    	public final LinkedHashMap<String, Integer> getMethods() {
			return methods;
		}
    	public final String getResource() {
			return resource;
		}
    }
    
    public static final class FuzzyLazyClassEntry {
    	private final LinkedHashMap<String, Integer> methods = new LinkedHashMap<String, Integer>();
    	private final String resource;
    	private final Pattern pattern;
    	
    	public FuzzyLazyClassEntry(Pattern pattern, String resource) {
    		this.pattern = pattern;
    		this.resource = resource;
		}
    	public final LinkedHashMap<String, Integer> getMethods() {
			return methods;
		}
    	public final String getResource() {
			return resource;
		}
    	
    	public final Pattern getPattern() {
			return pattern;
		}
    }
    
    public static final class ClassEntry {
    	

    	
        private final ConcurrentHashMap<String, Integer> methods;
        private final boolean isInterface;
        private final String superName;
        private final String[] interfaces;
        private boolean alreadyInstrumented;
        private boolean suspenable;

        public ClassEntry(String superName, String[] interfaces, boolean isInterface) {
            this.superName = superName;
            this.interfaces = interfaces;
            this.isInterface = isInterface;
            this.methods = new ConcurrentHashMap<String, Integer>();
        }
        
        public boolean isInterface() {
			return isInterface;
		}
        
        public String[] getInterfaces() {
			return interfaces;
		}
        
		public boolean isSuspenable() {
			return suspenable;
		}
		
		public void setSuspenable(boolean suspenable) {
			this.suspenable = suspenable;
		}
        
        public ConcurrentHashMap<String, Integer> getMethods() {
			return methods;
		}
        
        public String getSuperName() {
			return superName;
		}
        
        public final void set(String name, String desc, Integer suspendable) {
            String nameAndDesc = key(name, desc);
            methods.put(nameAndDesc, suspendable);
        }
        
        public final void set(String nameAndDesc, Integer suspendable) {
            methods.put(nameAndDesc, suspendable);
        }
        
        public final Integer get(String nameAndDesc) {
            return methods.get(nameAndDesc);
        }
        
        public final Integer check(String name, String desc) {
            return methods.get(key(name, desc));
        }
        
        public final Integer check(String fullname) {
            return methods.get(fullname);
        }
        

        @Override
        public int hashCode() {
            return superName.hashCode() * 67 + methods.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if(!(obj instanceof ClassEntry)) {
                return false;
            }
            final ClassEntry other = (ClassEntry)obj;
            return (superName == other.superName || superName != null && superName.equals(other.superName)) && methods.equals(other.methods);
        }
        
        public static String key(String methodName, String methodDesc) {
            return methodName.concat(methodDesc);
        }
        
        public boolean isAlreadyInstrumented() {
			return alreadyInstrumented;
		}
        
        public void setAlreadyInstrumented(boolean alreadyInstrumented) {
			this.alreadyInstrumented = alreadyInstrumented;
		}
        
    }

}
