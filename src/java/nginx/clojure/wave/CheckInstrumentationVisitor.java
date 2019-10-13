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

import nginx.clojure.SuspendExecution;
import nginx.clojure.wave.MethodDatabase.ClassEntry;
import nginx.clojure.asm.AnnotationVisitor;
import nginx.clojure.asm.ClassVisitor;
import nginx.clojure.asm.MethodVisitor;
import nginx.clojure.asm.Opcodes;
import nginx.clojure.asm.Type;

/**
 * Check if a class contains suspendable methods.
 * Basicly this class checks if a method is declared to throw {@link SuspendExecution}.
 * 
 * @author Matthias Mann
 */
public class CheckInstrumentationVisitor extends ClassVisitor {

    public static final String EXCEPTION_NAME = Type.getInternalName(SuspendExecution.class);
    public static final String EXCEPTION_DESC = Type.getDescriptor(SuspendExecution.class);

    private String className;
    private ClassEntry classEntry;
    private boolean hasSuspendable;
    private boolean alreadyInstrumented;

    public static class CheckMethodVisitor extends MethodVisitor {
    	private String name;
    	private String desc;
    	private ClassEntry classEntry;
		public CheckMethodVisitor(int api, ClassEntry classEntry, String name, String desc) {
			super(api);
			this.classEntry = classEntry;
			this.name = name;
			this.desc = desc;
		}
		
		@Override
		public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
			if (desc.equals(InstrumentClass.SUSPENDABLE_NAME)) {
				classEntry.set(name, this.desc, classEntry.isInterface() ? MethodDatabase.SUSPEND_JUST_MARK
						: MethodDatabase.SUSPEND_NORMAL);
			}
			return null;
		}
 }
    
    public CheckInstrumentationVisitor() {
        super(Opcodes.ASM7);
    }

    public boolean needsInstrumentation() {
        return hasSuspendable;
    }

    ClassEntry getClassEntry() {
        return classEntry;
    }
    
    public String getName() {
        return className;
    }

    public boolean isAlreadyInstrumented() {
        return alreadyInstrumented;
    }
    
    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        this.className = name;
        this.classEntry = new ClassEntry(superName, interfaces, (access & Opcodes.ACC_INTERFACE) != 0);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        if(desc.equals(InstrumentClass.ALREADY_INSTRUMENTED_NAME)) {
            alreadyInstrumented = true;
            classEntry.setAlreadyInstrumented(true);
        }else if (desc.equals(InstrumentClass.SUSPENDABLE_NAME)) {
        	hasSuspendable = true;
        	classEntry.setSuspenable(true);
        }
        return null;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        boolean suspendable = classEntry.isSuspenable() || checkExceptions(exceptions);
        MethodVisitor mv = null;
        if(suspendable) {
            hasSuspendable = true;
            // synchronized methods can't be made suspendable
            if((access & Opcodes.ACC_SYNCHRONIZED) == Opcodes.ACC_SYNCHRONIZED) {
                throw new UnableToInstrumentException("synchronized method", className, name, desc);
            }
        }else {
        	mv = new CheckMethodVisitor(super.api, classEntry, name, desc);
        }
        classEntry.set(name, desc, suspendable ? MethodDatabase.SUSPEND_NORMAL : MethodDatabase.SUSPEND_NONE);
        return mv;
    }

    public static boolean checkExceptions(String[] exceptions) {
        if(exceptions != null) {
            for(String ex : exceptions) {
                if(ex.equals(EXCEPTION_NAME)) {
                    return true;
                }
            }
        }
        return false;
    }
}
