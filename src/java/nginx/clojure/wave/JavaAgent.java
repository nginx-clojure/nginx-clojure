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
 *     * Neither the name of Matthias Mann nor the names of its contributors may
 *       be used to endorse or promote products derived from this software
 *       without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/*
 * Copyright (c) 2012, Enhanced Four
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * * Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in the
 *   documentation and/or other materials provided with the distribution.
 *
 * * Neither the name of 'Enhanced Four' nor the names of its contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package nginx.clojure.wave;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.regex.Pattern;

import nginx.clojure.Stack;
import nginx.clojure.asm.ClassReader;
import nginx.clojure.asm.ClassVisitor;
import nginx.clojure.asm.ClassWriter;
import nginx.clojure.asm.MethodVisitor;
import nginx.clojure.asm.Opcodes;
import nginx.clojure.asm.commons.JSRInlinerAdapter;
import nginx.clojure.asm.util.CheckClassAdapter;
import nginx.clojure.logger.TinyLogService;
import nginx.clojure.wave.MethodDatabase.ClassEntry;

/*
 * Created on Nov 21, 2010
 *
 * @author Riven
 * @author Matthias Mann
 * @author Zhang,Yuexing (xfeep)
 */
public class JavaAgent {
	
	public static final String NGINX_CLOJURE_WAVE_UDFS = "nginx.clojure.wave.udfs";
	public static final String NGINX_CLOJURE_WAVE_TRACE_CLASSMETHODPATTERN = "nginx.clojure.wave.trace.classmethodpattern";
	public static final String NGINX_CLOJURE_WAVE_TRACE_CLASSPATTERN = "nginx.clojure.wave.trace.classpattern";
	public static final String NGINX_CLOJURE_WAVE_DUMPDIR = "nginx.clojure.wave.dumpdir";
	public static MethodDatabase db;
	
    public static void premain(String agentArguments, Instrumentation instrumentation) {
    	ClassFileTransformer cft = buildClassFileTransformer(agentArguments);
    	if (cft != null) {
    		instrumentation.addTransformer(cft, true);
    		for (String c : db.getRetransformedClasses()) {
    			try {
					instrumentation.retransformClasses(db.getClassLoader().loadClass(c));
				} catch (Throwable e) {
					db.warn("retransformClasses error:" + c, e);
				} 
    		}
    	}
    }
    
    public static ClassFileTransformer buildClassFileTransformer(String agentArguments) {

        MethodDatabase db = JavaAgent.db = new MethodDatabase(Thread.currentThread().getContextClassLoader());
        boolean checkArg = false;
//        boolean runTool = false;
        boolean append = false;

        if(agentArguments != null) {
            for(char c : agentArguments.toCharArray()) {
                switch(c) {
                    case 'v':
                        db.setVerify(true);
                        break;

                    case 'm':
                        db.setAllowMonitors(true);
                        break;
                    case 'd':
                    	
                    	break;
                    case 'c':
                        checkArg = true;
                        break;

                    case 'b':
                        db.setAllowBlocking(true);
                        break;
                    case 't':
                    	db.setRunTool(true);
                    	break;
                    case 'a':
                    	append = true;
                    	break;
                    case 'h':
                    	db.setHookDumpWaveCfg(true);
                    	break;
                    case 'p' :
                    	db.setDump(true);
                    	break;
                    case 'n':
                    	TinyLogService.createDefaultTinyLogService().info("nginx clojure will do nothing about class waving!");
                    	//do nothing!!
                    	db.setDoNothing(true);
                    	return null;
                    default:
                        throw new IllegalStateException("Usage: nvdmcbtap (do Nothing, Verbose, Debug, allow Monitors, Check class, allow Blocking, run configuration generation Tool,  Append result, dumP waved class)");
                }
            }
        }

        MethodDatabase.getLog();
        
		if (System.getProperty(NGINX_CLOJURE_WAVE_DUMPDIR) != null) {
			db.setDumpDir(System.getProperty(NGINX_CLOJURE_WAVE_DUMPDIR));
		} else {
			db.setDumpDir(System.getProperty("java.io.tmpdir") + "/nginx-clojure-wave-dump");
		}
        
        if (System.getProperty(NGINX_CLOJURE_WAVE_TRACE_CLASSPATTERN) != null) {
        	db.setTraceClassPattern(Pattern.compile(System.getProperty(NGINX_CLOJURE_WAVE_TRACE_CLASSPATTERN)));
        }
        
        if (System.getProperty(NGINX_CLOJURE_WAVE_TRACE_CLASSMETHODPATTERN) != null) {
        	db.setTraceClassMethodPattern(Pattern.compile(System.getProperty(NGINX_CLOJURE_WAVE_TRACE_CLASSMETHODPATTERN)));
        }
        
        //load system configurations for method database
        try {
        	db.info("load system coroutine wave file %s", "nginx/clojure/wave/coroutine-method-db.txt");
			MethodDatabaseUtil.load(db, "nginx/clojure/wave/coroutine-method-db.txt");
		} catch (IOException e) {
			db.error("can not load nginx/clojure/wave/coroutine-method-db.txt", e);
		}
        String udfs = System.getProperty(NGINX_CLOJURE_WAVE_UDFS);
		if (udfs != null) {
			for (String udf : udfs.split(",|;")) {
				try {
					db.info("load use defined coroutine wave file %s", udf);
					MethodDatabaseUtil.load(db, udf);
				} catch (IOException e) {
					db.warn("can not load " + udf, e);
				}
			}
		}
        
        Stack.setDb(db);
        
        MethodDatabaseUtil.buildClassEntryFamily(db, "nginx/clojure/wave/MethodDatabaseUtil");
        SuspendMethodVerifier.db = db;
        
        if (db.isRunTool()) {
        	SuspendMethodTracer.db = db;
        	SuspendMethodTracer.quiteFlags.set(false);
        	return new CoroutineConfigurationToolWaver(db, append);
        }else {
        	return new CoroutineWaver(db, checkArg);
        }
        
    
    }

    static byte[] instrumentClass(MethodDatabase db, byte[] data, boolean check) {
        ClassReader r = new ClassReader(data);
        ClassWriter cw = new DBClassWriter(db, r);
        ClassVisitor cv = check ? new CheckClassAdapter(cw) : cw;
        ClassEntry ce = MethodDatabaseUtil.buildClassEntryFamily(db, r);
        if(db.shouldIgnore(r.getClassName()) || ce == null) {
            return null;
        }
        db.trace("TRANSFORM: %s", r.getClassName());
        InstrumentClass ic = new InstrumentClass(r.getClassName(), ce, cv, db, false);
        r.accept(ic, ClassReader.SKIP_FRAMES);
        return cw.toByteArray();
    }
    
	public static void dumpClass(byte[] classfileBuffer, File df, MethodDatabase db)  {
		try{
			RandomAccessFile raf = new RandomAccessFile(df, "rw");
			raf.setLength(0);
			raf.write(classfileBuffer, 0, classfileBuffer.length);
			raf.close();
		}catch(IOException e) {
			e.printStackTrace();
			db.error("dump file : " + df.getName(), e);
		}
	}

    public static class CoroutineWaver implements ClassFileTransformer {
        private final MethodDatabase db;
        private final boolean check;

        public CoroutineWaver(MethodDatabase db, boolean check) {
            this.db = db;
            this.check = check;
        }

        @Override
        public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {

        	if (className.startsWith("java/util/LinkedHashMap")) {
        		return null;
        	}
        	if (db.meetTraceTargetClass(className)) {
        		db.info("meet traced class %s", className);
        	}
			
            try {
                byte[] bs = instrumentClass(db, classfileBuffer, check);
                if (db.isDump() && bs != null && bs.length != classfileBuffer.length) {
                	File wavedFile = new File(new File(db.getDumpDir() + "/waved"), className + ".class");
                	wavedFile.getParentFile().mkdirs();
                	dumpClass(bs, wavedFile, db);
                }
            	if (db.meetTraceTargetClass(className)) {
            		File orgFile = new File(new File(db.getDumpDir() + "/org"), className + ".class");
            		orgFile.getParentFile().mkdirs();
            		dumpClass(classfileBuffer, orgFile, db);
            	}
                return bs;
            } catch(Throwable ex) {
            	if (db.isDump()){
            		File errDumpFile = new File(new File(db.getDumpDir() + "/failed"), className + ".class");
            		errDumpFile.getParentFile().mkdirs();
            		dumpClass(classfileBuffer, errDumpFile, db);
            	}
                db.error("Unable to instrument:" + className, ex);
                return null;
            }
            
        }

    }
    
    public static class CoroutineConfigurationToolWaver implements ClassFileTransformer {

    	private final MethodDatabase db;
    	private final boolean append;
    	
    	public CoroutineConfigurationToolWaver(MethodDatabase db, boolean append) {
    		this.db = db;
    		this.append = append;
    		if (db.isHookDumpWaveCfg()) {
        		Runtime.getRuntime().addShutdownHook(new Thread() {
        			@Override
        			public void run() {
        				String file = System.getProperty("nginx.clojure.wave.CfgToolOutFile");
        				if (file == null) {
        					file = "nginx.clojure.wave.cfgtooloutfile";
        					CoroutineConfigurationToolWaver.this.db.warn("system property 'nginx.clojure.wave.CfgToolOutFile' not found, use '%s' as file path", file);
        				}
        				try {
    						SuspendMethodTracer.dump(file, CoroutineConfigurationToolWaver.this.append);
    					} catch (Throwable e) {
    						CoroutineConfigurationToolWaver.this.db.error("dump error!", e);
    					}
        			}
        		});
    		}
		}
//      filter:org/objectweb/asm/    	
//    	filter:nginx/clojure/asm/
//    	filter:java/
//    	filter:sun/
//    	filter:com/sun/
//    	filter:clojure/asm
//    	filter:clojure/lang
//    	filter:clojure/core
//    	filter:org/junit
		@Override
		public byte[] transform(ClassLoader loader, final String className, Class<?> classBeingRedefined,
				ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
			
        	if (db.meetTraceTargetClass(className)) {
        		db.info("meet traced class %s", className);
        	}
			
			if (SuspendMethodTracer.quiteFlags.get()) {
				return classfileBuffer;
			}
			try {
				
				ClassReader cr = new ClassReader(classfileBuffer);
				ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES) {
					@Override
					protected String getCommonSuperClass(String type1, String type2) {
						return db.getCommonSuperClass(type1, type2);
					}
				};
				
				@SuppressWarnings("unused")
				ClassEntry ce = MethodDatabaseUtil.buildClassEntryFamily(db, cr);
//				if (className.startsWith("sun/launcher/")
//						|| className.startsWith("clojure/asm")
//						|| className.startsWith("org/objectweb/asm/")
//						|| className.startsWith("clojure/asm")
//						|| className.startsWith("org/junit")
////						|| className.startsWith("clojure/main")
////						|| className.startsWith("clojure/lang")
//						|| className.startsWith("sun/misc/ClassFileTransformer")
//						|| className.startsWith("nginx/clojure/asm") 
//						|| className.startsWith("nginx/clojure/wave") 
//						|| className.startsWith("java/util/ArrayList") 
//						|| className.startsWith("org/eclipse/jetty/server/")
//						|| className.startsWith("clojure/lang/Compiler")
//						|| className.startsWith("java/net/URLClassLoader")
//						|| className.startsWith("java/io/PrintStream")
//						|| className.startsWith("java/util/concurrent/ConcurrentHashMap")
//						|| className.startsWith("java/util/concurrent/atomic/AtomicBoolean")
//						|| className.startsWith("java/")
//						|| className.startsWith("sun/")
//						|| className.startsWith("com/sun/")
//						|| className.startsWith("org/eclipse/jdt/")
//						|| className.endsWith("ClassLoader")
//						|| className.startsWith("clojure/lang/PersistentHashMap")
//						|| className.startsWith("clojure/lang/Keyword")
//						|| className.startsWith("clojure/lang/Symbol")
//						|| className.startsWith("clojure/lang/Namespace")
//						|| className.startsWith("clojure/lang/Persistent")
//						|| className.startsWith("java/lang")) 
				if (db.shouldIgnore(className)){
					db.debug("skip class %s", className);
					return classfileBuffer;
				}
				db.debug("loading class %s", className);
//				ClassVisitor cv = db.isVerbose() ?  new TraceClassVisitor(cw, new PrintWriter(System.out)) : cw;
				ClassVisitor cv = new ClassVisitor(Opcodes.ASM7, cw) {
					@Override
					public MethodVisitor visitMethod(int access, String name,
							String desc, String signature, String[] exceptions) {
						MethodVisitor mv = super.visitMethod(access, name,
								desc, signature, exceptions);
						if ( (Opcodes.ACC_NATIVE & access) != 0
								||  (Opcodes.ACC_ABSTRACT & access) != 0 
//								|| name.startsWith("<cinit>")
								) {
							db.debug("skip native or abstract method: %s.%s%s", className, name, desc);
							return mv;
						}
						return new JSRInlinerAdapter(new SuspendMethodTracerAdvice(db, className, mv, access, name, desc), access, name, desc, signature, exceptions);
					}
				};

				cr.accept(cv, ClassReader.EXPAND_FRAMES);
				byte[] rt = cw.toByteArray();
				
				if (db.isDump()) {
					File wavedFile = new File(new File(db.getDumpDir() + "/waved-by-tool"), className + ".class");
                    wavedFile.getParentFile().mkdirs();
                    dumpClass(rt, wavedFile, db);
				}
				
				return rt;
				
			} catch(Throwable ex) {
	                db.error("Unable to transform:" + className, ex);
	                return null;
	        }
		}
    	
    }
}
