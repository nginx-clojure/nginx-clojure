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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;

import nginx.clojure.anno.Suspendable;
import nginx.clojure.asm.AnnotationVisitor;
import nginx.clojure.asm.ClassVisitor;
import nginx.clojure.asm.MethodVisitor;
import nginx.clojure.asm.Opcodes;
import nginx.clojure.asm.Type;
import nginx.clojure.asm.commons.JSRInlinerAdapter;
import nginx.clojure.asm.tree.AbstractInsnNode;
import nginx.clojure.asm.tree.MethodNode;
import nginx.clojure.asm.tree.analysis.AnalyzerException;
import nginx.clojure.asm.util.Printer;
import nginx.clojure.asm.util.Textifier;
import nginx.clojure.asm.util.TraceMethodVisitor;
import nginx.clojure.wave.MethodDatabase.ClassEntry;

/**
 * Instrument a class by instrumenting all suspendable methods and copying the others.
 * 
 * @author Matthias Mann
 */
public class InstrumentClass extends ClassVisitor {

    static final String COROUTINE_NAME = "nginx/clojure/Coroutine";
    static final String ALREADY_INSTRUMENTED_NAME = Type.getDescriptor(AlreadyInstrumented.class);
    static final String SUSPENDABLE_NAME = Type.getDescriptor(Suspendable.class);
    
    private final MethodDatabase db;
    private final boolean forceInstrumentation;
    private String className;
    private ClassEntry classEntry;
    private boolean alreadyInstrumented;
    private ArrayList<MethodNode> methods;
    
    public InstrumentClass(String className, ClassEntry classEntry, ClassVisitor cv, MethodDatabase db, boolean forceInstrumentation) {
        super(Opcodes.ASM7, cv);
        this.className = className;
        this.classEntry = classEntry;
        this.db = db;
        this.forceInstrumentation = forceInstrumentation;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        // need atleast 1.5 for annotations to work
        if(version < Opcodes.V1_5) {
            version = Opcodes.V1_5;
        }
        
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        if(desc.equals(InstrumentClass.ALREADY_INSTRUMENTED_NAME)) {
            alreadyInstrumented = true;
        }
        return super.visitAnnotation(desc, visible);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
    	String method = ClassEntry.key(name, desc);
    	Integer suspendType = db.checkMethodSuspendType(className, method, true);
        if (db.meetTraceTargetClassMethod(className, method)) {
        	db.info("meet traced method %s.%s, suspend type = %s", className, method, MethodDatabase.SUSPEND_TYPE_STRS[suspendType]);
        }
        if((suspendType == MethodDatabase.SUSPEND_NORMAL 
//Now for less boot cost we don't wave those class just mark  SUSPEND_FAMILY        		
//        		|| suspendType == MethodDatabase.SUSPEND_FAMILY
        		) 
        		&& checkAccess(access) && !(className.equals(COROUTINE_NAME) && name.equals("yield"))) {
            if(db.isDebug()) {
                db.trace("Instrumenting method %s#%s", className, name);
            }
            
            if(methods == null) {
                methods = new ArrayList<MethodNode>();
            }
            
            MethodNode mn = null;
            MethodVisitor mv = null;
            if (name.charAt(0) == '<') {
            	mv = mn = new MethodNode(access, name, desc, signature, exceptions);
            }else {
            	if (db.meetTraceTargetClassMethod(className, method)) {
            		Printer tp = new Textifier();
            		mn = new TracableMethodNode("Orginal: " + className + "." + method,  db, access, name, desc, signature, exceptions, tp, new PrintWriter(System.out));
            		mv = new TraceMethodVisitor(mn, tp);
            	}else {
            		mv = mn = new InstrumentMethodNode(db, access, name, desc, signature, exceptions);
            	}
            }
            
            methods.add(mn);
            return new JSRInlinerAdapter(mv, access, name, desc, signature, exceptions);
        }
        
        if (db.isVerify()) {
			return new JSRInlinerAdapter(new SuspendMethodVerifyAdvice(db,
					className, super.visitMethod(access, name, desc, signature,
							exceptions), access, name, desc), access, name,
					desc, signature, exceptions);
        }else {
        	return super.visitMethod(access, name, desc, signature, exceptions);
        }
    }
    
    @Override
    public void visitEnd() {
        db.recordSuspendableMethods(className, classEntry);
        
        if(methods != null) {
            if(alreadyInstrumented && !forceInstrumentation) {
                for(MethodNode mn : methods) {
                    mn.accept(makeOutMV(mn));
                }
            } else {
                if(!alreadyInstrumented) {
                    super.visitAnnotation(ALREADY_INSTRUMENTED_NAME, true);
                }
                
                for(MethodNode mn : methods) {
                    MethodVisitor outMV = makeOutMV(mn);
                    
                    try {
                    	if (db.meetTraceTargetClassMethod(className, ClassEntry.key(mn.name, mn.desc))) {
                        	db.info("On waving: meet traced method %s.%s%s", className, mn.name, mn.desc);
                        }
                    	
                    	if (mn.name.charAt(0) == '<' && mn.name.charAt(1) == 'i') {
                    		mn.accept(outMV);
                        	InstrumentConstructorMethod icm = new InstrumentConstructorMethod(db, className, mn);
                        	icm.accept(this);
                        }else {
                        	InstrumentMethod im = new InstrumentMethod(db, className, mn);
                            if(im.collectCodeBlocks()) {
                                if(mn.name.charAt(0) == '<') {
                                		throw new UnableToInstrumentException("special method", className, mn.name, mn.desc);
                                }else {
                                	im.accept(outMV);
                                }
                            } else {
                                mn.accept(outMV);
                            }
                        }
                    } catch(AnalyzerException ex) {
                        ex.printStackTrace();
                        throw new InternalError(ex.getMessage());
                    }
                }
            }
        }
        super.visitEnd();
        classEntry.setAlreadyInstrumented(true);
    }

    protected MethodVisitor makeOutMV(MethodNode mn) {
    	String[] exps = MethodDatabase.toStringArray(mn.exceptions);
    	String mk = ClassEntry.key(mn.name, mn.desc);
    	MethodVisitor mv = super.visitMethod(mn.access, mn.name, mn.desc, mn.signature, exps);
    	if (db.meetTraceTargetClassMethod(className, mk)) {
    		Printer tp = new Textifier();
    		TracableMethodVisitor tmv = new TracableMethodVisitor("Waved: " + className + "." + mk,  mv, mn.access, mn.name, mn.desc, mn.signature, exps, tp, new PrintWriter(System.out));
    		mv = new TraceMethodVisitor(tmv, tp);
    	}
    	if (db.isVerify()) {
    		return new JSRInlinerAdapter(new SuspendMethodVerifyAdvice(db, className, mv, mn.access, mn.name, mn.desc), mn.access, mn.name, mn.desc, mn.signature, exps);
    	}
    	return new JSRInlinerAdapter(mv, mn.access, mn.name, mn.desc, mn.signature, exps);
    }

    protected MethodVisitor makeOutMV(int access, String name, String desc, String signature, String[] exceptions) {
    	return super.visitMethod(access, name, desc, signature, exceptions);
    }
    
    private static boolean checkAccess(int access) {
        return (access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0;
    }
    
    //for debug usage
    public static String insnToString(AbstractInsnNode insn){
        Printer printer = new Textifier();
        TraceMethodVisitor mp = new TraceMethodVisitor(printer);
        insn.accept(mp);
        StringWriter sw = new StringWriter();
        printer.print(new PrintWriter(sw));
        printer.getText().clear();
        return sw.toString();
    }
    
    public static void methodToString(MethodNode mn) {
    	Printer printer = new Textifier();
        TraceMethodVisitor mp = new TraceMethodVisitor(printer);
        mn.accept(mp);
        PrintWriter pw = new PrintWriter(System.out);
        printer.print(pw);
        pw.flush();
    }
}
