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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

import nginx.clojure.NginxClojureRT;
import nginx.clojure.asm.ClassReader;
import nginx.clojure.asm.ClassVisitor;
import nginx.clojure.asm.ClassWriter;
import nginx.clojure.asm.MethodVisitor;
import nginx.clojure.asm.Opcodes;
import nginx.clojure.asm.util.CheckClassAdapter;
import nginx.clojure.asm.util.TraceClassVisitor;
import nginx.clojure.wave.MethodDatabase.ClassEntry;

/*
 * Created on Nov 21, 2010
 *
 * @author Riven
 * @author Matthias Mann
 */
public class JavaAgent {
    public static void premain(String agentArguments, Instrumentation instrumentation) {
        MethodDatabase db = new MethodDatabase(Thread.currentThread().getContextClassLoader());
        boolean checkArg = false;
        boolean runTool = false;
        boolean append = false;

        if(agentArguments != null) {
            for(char c : agentArguments.toCharArray()) {
                switch(c) {
                    case 'v':
                        db.setVerbose(true);
                        break;

                    case 'd':
                        db.setDebug(true);
                        break;

                    case 'm':
                        db.setAllowMonitors(true);
                        break;

                    case 'c':
                        checkArg = true;
                        break;

                    case 'b':
                        db.setAllowBlocking(true);
                        break;
                    case 't':
                    	runTool = true;
                    	break;
                    case 'a':
                    	append = true;
                    	break;
                    default:
                        throw new IllegalStateException("Usage: vdmc (verbose, debug, allow monitors, check class)");
                }
            }
        }

        if (NginxClojureRT.getLog() != null) {
        	db.setLog(NginxClojureRT.getLog());
        }
        
        if (runTool) {
        	SuspendMethodTracer.db = db;
        	SuspendMethodTracer.dumping.set(false);
        	instrumentation.addTransformer(new CoroutineConfigurationToolWaver(db, append));
        }else {
        	instrumentation.addTransformer(new CoroutineWaver(db, checkArg));
        }
        
    }

    static byte[] instrumentClass(MethodDatabase db, byte[] data, boolean check) {
        ClassReader r = new ClassReader(data);
        ClassWriter cw = new DBClassWriter(db, r);
        ClassVisitor cv = check ? new CheckClassAdapter(cw) : cw;
        ClassEntry ce = MethodDatabaseUtil.buildClassEntryFamily(db, r);
        InstrumentClass ic = new InstrumentClass(r.getClassName(), ce, cv, db, false);
        r.accept(ic, ClassReader.SKIP_FRAMES);
        return cw.toByteArray();
    }

    public static class CoroutineWaver implements ClassFileTransformer {
        private final MethodDatabase db;
        private final boolean check;

        public CoroutineWaver(MethodDatabase db, boolean check) {
            this.db = db;
            this.check = check;
            //load system configurations for method database
            try {
            	db.debug("load system coroutine wave file %s", "nginx/clojure/wave/coroutine-method-db.txt");
				MethodDatabaseUtil.load(db, "nginx/clojure/wave/coroutine-method-db.txt");
			} catch (IOException e) {
				db.error("can not load nginx/clojure/wave/coroutine-method-db.txt", e);
			}
            String udfs = System.getProperty("nginx.clojure.wave.udfs");
			if (udfs != null) {
				for (String udf : udfs.split(",")) {
					try {
						db.debug("load use defined coroutine wave file %s", udf);
						MethodDatabaseUtil.load(db, udf);
					} catch (IOException e) {
						db.warn("can not load " + udf, e);
					}
				}
			}
        }

        @Override
        public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
            if(db.shouldIgnore(className)) {
                return null;
            }

            db.debug("TRANSFORM: %s", className);

            try {
                return instrumentClass(db, classfileBuffer, check);
            } catch(Exception ex) {
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
					} catch (IOException e) {
						CoroutineConfigurationToolWaver.this.db.error("dump error!", e);
					}
    			}
    		});
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
			if (SuspendMethodTracer.dumping.get()) {
				return classfileBuffer;
			}
			try {
				
				ClassReader cr = new ClassReader(classfileBuffer);
				ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS) {
					@Override
					protected String getCommonSuperClass(String type1, String type2) {
						return db.getCommonSuperClass(type1, type2);
					}
				};
				ClassEntry ce = MethodDatabaseUtil.buildClassEntryFamily(db, cr);
				if (className.startsWith("sun/launcher/")
						|| className.startsWith("clojure/asm")
						|| className.startsWith("org/objectweb/asm/")
						|| className.startsWith("clojure/asm")
						|| className.startsWith("org/junit")
//						|| className.startsWith("clojure/main")
//						|| className.startsWith("clojure/lang")
						|| className.startsWith("sun/misc/ClassFileTransformer")
						|| className.startsWith("nginx/clojure/asm") 
						|| className.startsWith("nginx/clojure/wave") 
						|| className.startsWith("java/util/ArrayList") 
						|| className.startsWith("org/eclipse/jetty/server/")
						|| className.startsWith("clojure/lang/Compiler")
						|| className.startsWith("java/net/URLClassLoader")
						|| className.startsWith("java/lang")) {
					db.debug("skip class %s", className);
					return classfileBuffer;
				}
				db.debug("loading class %s", className);
				ClassVisitor cv = db.isVerbose() ?  new TraceClassVisitor(cw, new PrintWriter(System.out)) : cw;
				cv = new ClassVisitor(Opcodes.ASM4, cv == null ? cw : cv) {
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
						return new SuspendMethodTracerAdvice(className, mv, access, name, desc);
					}
				};

				cr.accept(cv, ClassReader.EXPAND_FRAMES);
				byte[] rt = cw.toByteArray();
				
				if (db.isDebug() && db.isVerbose()) {
					 File wavedFile = new File("waved", className+".class");
                     wavedFile.getParentFile().mkdirs();
                     FileOutputStream fo = new FileOutputStream(wavedFile);
                     fo.write(rt);
                     fo.close();
				}
				
				return rt;
				
			} catch(Exception ex) {
	                db.error("Unable to transform:" + className, ex);
	                return null;
	            }
		}
    	
    }
}
