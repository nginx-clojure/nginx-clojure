/*
 * Copyright (c) 2008-2013, Matthias Mann
 *
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
package nginx.clojure.wave;

import nginx.clojure.asm.Opcodes;
import nginx.clojure.asm.Type;
import nginx.clojure.asm.tree.AbstractInsnNode;
import nginx.clojure.asm.tree.MethodInsnNode;
import nginx.clojure.asm.tree.analysis.Analyzer;
import nginx.clojure.asm.tree.analysis.AnalyzerException;
import nginx.clojure.asm.tree.analysis.BasicValue;
import nginx.clojure.asm.tree.analysis.Frame;
import nginx.clojure.asm.tree.analysis.Interpreter;
import nginx.clojure.asm.tree.analysis.Value;

/**
 *
 * @author matthias
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class TypeAnalyzer extends Analyzer {

    public TypeAnalyzer(Interpreter interpreter) {
        super(interpreter);
    }

    @Override
    protected Frame newFrame(int nLocals, int nStack) {
        return new TypeFrame(nLocals, nStack);
    }

    @Override
    protected Frame newFrame(Frame src) {
        return new TypeFrame(src);
    }
    
    /**
     * Computes the number of arguments
     * Returns the same result as {@code Type.getArgumentTypes(desc).length }
     * just with no memory allocations
     */
    public static int getNumArguments(String methodDesc) {
        int off = 1;
        int size = 0;
        for(;;) {
            char car = methodDesc.charAt(off++);
            if (car == ')') {
                return size;
            }
            if (car != '[') {
                ++size;
                if (car == 'L') {
                    off = methodDesc.indexOf(';', off) + 1;
                }
            }
        }
    }
    
    static class TypeFrame extends Frame {
        TypeFrame(int nLocals, int nStack) {
            super(nLocals, nStack);
        }

        TypeFrame(Frame src) {
            super(src);
        }

        @Override
        public void execute(AbstractInsnNode insn, Interpreter interpreter) throws AnalyzerException {
            switch (insn.getOpcode()) {
                case Opcodes.INVOKEVIRTUAL:
                case Opcodes.INVOKESPECIAL:
                case Opcodes.INVOKESTATIC:
                case Opcodes.INVOKEINTERFACE: {
                    String desc = ((MethodInsnNode)insn).desc;
                    for(int i=getNumArguments(desc) ; i>0 ; --i) {
                        pop();
                    }
                    if (insn.getOpcode() != Opcodes.INVOKESTATIC) {
                        pop();
                        if(insn.getOpcode() == Opcodes.INVOKESPECIAL && getStackSize() > 0) {
                            if("<init>".equals(((MethodInsnNode)insn).name)) {
                                Value value = pop();
                                if(value instanceof NewValue) {
                                    value = new BasicValue(((NewValue)value).getType());
                                }
                                push(value);
                            }
                        }
                    }
                    Type returnType = Type.getReturnType(desc);
                    if (returnType != Type.VOID_TYPE) {
                        push(interpreter.newValue(returnType));
                    }
                    break;
                }
                default:
                    super.execute(insn, interpreter);
            }
        }
    }
}
