/*
 * Copyright (C) 2014 Zhang,Yuexiang (xfeep)
 * All rights reserved.
 */
package nginx.clojure.wave;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import nginx.clojure.SuspendableConstructorUtilStack;
import nginx.clojure.asm.Label;
import nginx.clojure.asm.MethodVisitor;
import nginx.clojure.asm.Opcodes;
import nginx.clojure.asm.Type;
import nginx.clojure.asm.tree.AbstractInsnNode;
import nginx.clojure.asm.tree.InsnList;
import nginx.clojure.asm.tree.LabelNode;
import nginx.clojure.asm.tree.LocalVariableNode;
import nginx.clojure.asm.tree.MethodInsnNode;
import nginx.clojure.asm.tree.MethodNode;
import nginx.clojure.asm.tree.TryCatchBlockNode;
import nginx.clojure.asm.tree.analysis.Analyzer;
import nginx.clojure.asm.tree.analysis.AnalyzerException;
import nginx.clojure.asm.tree.analysis.BasicValue;
import nginx.clojure.asm.tree.analysis.Frame;
import nginx.clojure.asm.tree.analysis.Value;
import nginx.clojure.wave.MethodDatabase.ClassEntry;

@SuppressWarnings({"rawtypes"})
public class InstrumentConstructorMethod {

    private static final String CSTACK_NAME = Type.getInternalName(SuspendableConstructorUtilStack.class);

	private static final String CSTACK_PUSH_DOUBLE_VALUE_DESC = "(DL"+CSTACK_NAME+";I)V";

	private static final String CSTACK_PUSH_LONG_VALUE_DESC = "(JL"+CSTACK_NAME+";I)V";

	private static final String CSTACK_PUSH_FLOAT_VALUE_DESC = "(FL"+CSTACK_NAME+";I)V";

	private static final String CSTACK_PUSH_INT_VALUE_DESC = "(IL"+CSTACK_NAME+";I)V";

	private static final String CSTACK_PUSH_OBJECT_VALUE_DESC = "(Ljava/lang/Object;L"+CSTACK_NAME+";I)V";
    
    private final MethodDatabase db;
    private final String className;
    private final MethodNode mn;
    private final Frame[] frames;
    private final int lvarCStack;
    private final int firstLocal;
    
    
    
    public InstrumentConstructorMethod(MethodDatabase db, String className, MethodNode mn) throws AnalyzerException {
        this.db = db;
        this.className = className;
        this.mn = mn;
        try {
            Analyzer a = MethodDatabaseUtil.buildAnalyzer(db);
            this.frames = a.analyze(className, mn);
            this.lvarCStack = mn.maxLocals+2;
            this.firstLocal = ((mn.access & Opcodes.ACC_STATIC) == Opcodes.ACC_STATIC) ? 0 : 1;
        } catch (UnsupportedOperationException ex) {
            throw new AnalyzerException(null, ex.getMessage(), ex);
        }
    }
    
    public static String getMD5(String desc) {
        try {
			byte[] dg = MessageDigest.getInstance("MD5").digest(desc.getBytes());
			return new BigInteger(1,dg).toString(16);
		} catch (NoSuchAlgorithmException ex) {
			throw new RuntimeException(ex.getMessage(), ex);
		}
    }
    

    
    public void trySplitIntoTwoNewMethods(InstrumentClass cv) throws AnalyzerException {
    	int numIns = mn.instructions.size();
    	int splitPos = -1;
    	MethodInsnNode invokedInitInsn = null;
    	for(int i = 0 ; i < numIns ; i++) {
    		AbstractInsnNode insn = mn.instructions.get(i);
    		if (db.meetTraceTargetClassMethod(className, mn.name)) {
    			db.debug(InstrumentClass.insnToString(insn));
    		}
//    		System.out.println(insnToString(insn));
    		if (insn instanceof MethodInsnNode) {
				MethodInsnNode minsn = (MethodInsnNode) insn;
				Frame mif = frames[i];
				if (minsn.name.equals("<init>") && mif.getLocal(0) == mif.getStack(mif.getStackSize()-1-TypeAnalyzer.getNumArguments(minsn.desc)) ) {
					splitPos = i+1;
					invokedInitInsn = minsn;
					break;
				}
			}
    	}
    	
    	if (splitPos == -1) {
    		splitPos = 0;
    	}
    	
        Frame f = frames[splitPos];
        FrameInfo fi = new FrameInfo(f, firstLocal, splitPos, mn.instructions, db);
        emitShrinkedInitMethod(cv, splitPos, f, fi, invokedInitInsn);
        emitInitHelpMethod(cv, numIns, splitPos, f, fi, invokedInitInsn);
    }

	public void emitInitHelpMethod(InstrumentClass cv, int numIns, int splitPos, Frame f, FrameInfo fi, MethodInsnNode invokedInitInsn) throws AnalyzerException {
		
		mn.instructions.resetLabels();
		
		List<String> exps = new ArrayList<String>(mn.exceptions);
		if (!exps.contains(CheckInstrumentationVisitor.EXCEPTION_NAME)) {
			exps.add(CheckInstrumentationVisitor.EXCEPTION_NAME);
		}
		String[] expss = MethodDatabase.toStringArray(exps);
		MethodNode mv = new MethodNode(Opcodes.ACC_PUBLIC, buildInitHelpMethodName(mn.desc), "()V", null, expss);
		
		Label invokedInitInsnStart = null;
		Label invokedInitInsnEnd = null;
		Label invokedInitInsnCatchAll = null;
		boolean needWaveInvokedInitInsn = invokedInitInsn != null 
				&& db.checkMethodSuspendType(invokedInitInsn.owner, ClassEntry.key(invokedInitInsn.name, invokedInitInsn.desc), false) == MethodDatabase.SUSPEND_NORMAL;
		if (needWaveInvokedInitInsn) {
			invokedInitInsnStart = new Label();
			invokedInitInsnEnd = new Label();
			invokedInitInsnCatchAll = new Label();
			mv.visitTryCatchBlock(invokedInitInsnStart, invokedInitInsnEnd, invokedInitInsnCatchAll, null);
		}
		
		for(TryCatchBlockNode tcb : mn.tryCatchBlocks) {
            tcb.accept(mv);
        }
		
		mv.visitMethodInsn(Opcodes.INVOKESTATIC, CSTACK_NAME, "getStack", "()L"+CSTACK_NAME+";", false);
		mv.visitVarInsn(Opcodes.ASTORE, lvarCStack);
		
		if (needWaveInvokedInitInsn) {
			mv.visitLabel(invokedInitInsnStart);
			mv.visitVarInsn(Opcodes.ALOAD, 0);
			mv.visitMethodInsn(invokedInitInsn.getOpcode(), invokedInitInsn.owner, "inch_" + getMD5(invokedInitInsn.desc), "()V", invokedInitInsn.itf);
			mv.visitLabel(invokedInitInsnEnd);
		}
		
		for(int i=firstLocal ; i<f.getLocals() ; i++) {
		    BasicValue v = (BasicValue) f.getLocal(i);
		    if(!isNullType(v)) {
		        int slotIdx = fi.localSlotIndices[i];
		        assert slotIdx >= 0 && slotIdx < fi.numSlots;
		        emitRestoreValue(mv, v, lvarCStack, slotIdx);
		        mv.visitVarInsn(v.getType().getOpcode(Opcodes.ISTORE), i);
		    } else if(v != BasicValue.UNINITIALIZED_VALUE) {
		        mv.visitInsn(Opcodes.ACONST_NULL);
		        mv.visitVarInsn(Opcodes.ASTORE, i);
		    }
		}
		
		for(int i=0 ; i<f.getStackSize() ; i++) {
		    BasicValue v = (BasicValue) f.getStack(i);
		    if(!isOmitted(v)) {
		        if(!isNullType(v)) {
		            int slotIdx = fi.stackSlotIndices[i];
		            assert slotIdx >= 0 && slotIdx < fi.numSlots;
		            emitRestoreValue(mv, v, lvarCStack, slotIdx);
		        } else {
		            mv.visitInsn(Opcodes.ACONST_NULL);
		        }
		    }
		}
		
		mv.visitVarInsn(Opcodes.ALOAD,lvarCStack);
		emitConst(mv, fi.numSlots);
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, CSTACK_NAME, "release", "(I)V", false);
		
		int maxStack = mn.maxStack;
		for (int i = splitPos; i < numIns; i++) {
			AbstractInsnNode insn = mn.instructions.get(i);
			if (insn instanceof MethodInsnNode) {
				MethodInsnNode misn = (MethodInsnNode) insn;
				String name = misn.name;
				if (name.charAt(0) == '<' && name.charAt(1) == 'i' && db != null && db.checkMethodSuspendType(misn.owner, ClassEntry.key(name, misn.desc), false, false) == MethodDatabase.SUSPEND_NORMAL) {
					mv.visitInsn(Opcodes.ACONST_NULL);
					mv.visitMethodInsn(misn.getOpcode(), misn.owner, name, InstrumentConstructorMethod.buildShrinkedInitMethodDesc(misn.desc), false);
					mv.visitInsn(Opcodes.DUP);
					mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, misn.owner, InstrumentConstructorMethod.buildInitHelpMethodName(misn.desc), "()V", false);
					maxStack = mn.maxStack + 1;
					continue;
				}
			}
			insn.accept(mv);
		}
		
		if (needWaveInvokedInitInsn) {
			mv.visitLabel(invokedInitInsnCatchAll);
			mv.visitVarInsn(Opcodes.ALOAD,lvarCStack);
			emitConst(mv, fi.numSlots);
			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, CSTACK_NAME, "release", "(I)V", false);
			mv.visitInsn(Opcodes.ATHROW);
		}

		if(mn.localVariables != null && !mn.localVariables.isEmpty()) {
			for (int i = 0; i < f.getLocals(); i++) {
				mn.localVariables.get(i).accept(mv);
			}
        }
		
		mv.visitMaxs(maxStack+3, mn.maxLocals+3);
		mv.visitEnd();
		
		InstrumentMethod im = new InstrumentMethod(db, className, mv);
		if(im.collectCodeBlocks()) {
			im.accept(cv.makeOutMV(mv));
		}else {
			db.warn("no suspendable method in constructor : " + mn.name + mn.desc);
//			throw new RuntimeException("no suspendable method in constructor : " + mn.name + mn.desc);
			mv.accept(cv.makeOutMV(mv.access, mv.name, mv.desc, mv.signature, expss));
		}
	}

	public void emitShrinkedInitMethod(InstrumentClass cv, int splitPos, Frame f, FrameInfo fi, MethodInsnNode invokedInitInsn)
			throws InternalError {
		String desc = buildShrinkedInitMethodDesc(mn.desc);
		String[] exps = MethodDatabase.toStringArray(mn.exceptions);
		MethodVisitor cmv = cv.makeOutMV(mn.access, "<init>", desc, null, exps);
		cmv.visitCode();
		
		for (int i = 0; i < splitPos -1; i++) {
			mn.instructions.get(i).accept(cmv);
		}
		
		cmv.visitMethodInsn(Opcodes.INVOKESTATIC, CSTACK_NAME, "getStack", "()L"+CSTACK_NAME+";", false);
		cmv.visitVarInsn(Opcodes.ASTORE, lvarCStack);
		cmv.visitVarInsn(Opcodes.ALOAD,lvarCStack);
		emitConst(cmv, fi.numSlots);
		cmv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, CSTACK_NAME, "incRefsAndReserveSpace", "(I)V", false);
		        
		for (int i = f.getStackSize(); i-->0 ;) {
		    BasicValue v = (BasicValue) f.getStack(i);
		    if(!isOmitted(v)) {
		        if(!isNullType(v)) {
		            int slotIdx = fi.stackSlotIndices[i];
		            assert slotIdx >= 0 && slotIdx < fi.numSlots;
		            emitStoreValue(cmv, v, lvarCStack, slotIdx);
		        } else {
		            db.trace("NULL stack entry: type=%s size=%d", v.getType(), v.getSize());
		            cmv.visitInsn(Opcodes.POP);
		        }
		    }
		}
		
		for(int i=firstLocal; i < f.getLocals() ; i++) {
		    BasicValue v = (BasicValue) f.getLocal(i);
		    if(!isNullType(v)) {
		        cmv.visitVarInsn(v.getType().getOpcode(Opcodes.ILOAD), i);
		        int slotIdx = fi.localSlotIndices[i];
		        assert slotIdx >= 0 && slotIdx < fi.numSlots;
		        emitStoreValue(cmv, v, lvarCStack, slotIdx);
		    }
		}
		
		if (invokedInitInsn != null) {
			if (db.checkMethodSuspendType(invokedInitInsn.owner, ClassEntry.key(invokedInitInsn.name, invokedInitInsn.desc), false, false) == MethodDatabase.SUSPEND_NORMAL) {
				Type[] tps = Type.getArgumentTypes(invokedInitInsn.desc);
				Type[] ntps = new Type[tps.length + 1];
				System.arraycopy(tps, 0, ntps, 0, tps.length);
				ntps[tps.length] = Type.getType(CheckInstrumentationVisitor.EXCEPTION_DESC);
				cmv.visitInsn(Opcodes.ACONST_NULL);
				cmv.visitMethodInsn(invokedInitInsn.getOpcode(), invokedInitInsn.owner,invokedInitInsn.name, Type.getMethodDescriptor(Type.VOID_TYPE, ntps), false);
			}else {
				invokedInitInsn.accept(cmv);
			}
		}
		
        if(mn.localVariables != null) {
            for(LocalVariableNode var : mn.localVariables) {
            	if (invokedInitInsn != null) {
            		if (mn.instructions.indexOf(var.start) <= splitPos) {
            			var.accept(cmv);
            		}
            	}else if (var.start.getPrevious() == null) {
            		var.accept(cmv);
            	}
            }
        }
		cmv.visitInsn(Opcodes.RETURN);
		cmv.visitMaxs(mn.maxStack+3, mn.maxLocals+3);
		cmv.visitEnd();
	}

	public static String buildShrinkedInitMethodDesc(String desc) {
		Type[] argsTypes = Type.getArgumentTypes(desc);
		Type[] fargsTypes = new Type[argsTypes.length + 1];
		System.arraycopy(argsTypes, 0, fargsTypes, 0, argsTypes.length);
		fargsTypes[argsTypes.length] = Type.getType(CheckInstrumentationVisitor.EXCEPTION_DESC);
		desc = Type.getMethodDescriptor(Type.VOID_TYPE, fargsTypes);
		return desc;
	}
    
	public static String buildInitHelpMethodName(String desc) {
		return "inch_" + getMD5(desc);
	}

    
    
    public void accept(InstrumentClass cv) throws AnalyzerException {
    	trySplitIntoTwoNewMethods(cv);
    }

	
  

    private static void emitConst(MethodVisitor mv, int value) {
        if(value >= -1 && value <= 5) {
            mv.visitInsn(Opcodes.ICONST_0 + value);
        } else if((byte)value == value) {
            mv.visitIntInsn(Opcodes.BIPUSH, value);
        } else if((short)value == value) {
            mv.visitIntInsn(Opcodes.SIPUSH, value);
        } else {
            mv.visitLdcInsn(value);
        }
    }

    private void emitStoreValue(MethodVisitor mv, BasicValue v, int lvarStack, int idx) throws InternalError, IndexOutOfBoundsException {
        String desc;

        switch(v.getType().getSort()) {
        case Type.OBJECT:
        case Type.ARRAY:
            desc = CSTACK_PUSH_OBJECT_VALUE_DESC;
            break;
        case Type.BOOLEAN:
        case Type.BYTE:
        case Type.SHORT:
        case Type.CHAR:
        case Type.INT:
            desc = CSTACK_PUSH_INT_VALUE_DESC;
            break;
        case Type.FLOAT:
            desc = CSTACK_PUSH_FLOAT_VALUE_DESC;
            break;
        case Type.LONG:
            desc = CSTACK_PUSH_LONG_VALUE_DESC;
            break;
        case Type.DOUBLE:
            desc = CSTACK_PUSH_DOUBLE_VALUE_DESC;
            break;
        default:
            throw new InternalError("Unexpected type: " + v.getType());
        }

        mv.visitVarInsn(Opcodes.ALOAD, lvarStack);
        emitConst(mv, idx);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, CSTACK_NAME, "push", desc, false);
    }

    private void emitRestoreValue(MethodVisitor mv, BasicValue v, int lvarStack, int idx) {
        mv.visitVarInsn(Opcodes.ALOAD, lvarStack);
        emitConst(mv, idx);
        
        switch(v.getType().getSort()) {
        case Type.OBJECT:
            String internalName = v.getType().getInternalName();
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, CSTACK_NAME, "getObject", "(I)Ljava/lang/Object;", false);
            if(!internalName.equals("java/lang/Object")) {  // don't cast to Object ;)
                mv.visitTypeInsn(Opcodes.CHECKCAST, internalName);
            }
            break;
        case Type.ARRAY:
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, CSTACK_NAME, "getObject", "(I)Ljava/lang/Object;", false);
            mv.visitTypeInsn(Opcodes.CHECKCAST, v.getType().getDescriptor());
            break;
        case Type.BYTE:
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, CSTACK_NAME, "getInt", "(I)I", false);
            mv.visitInsn(Opcodes.I2B);
            break;
        case Type.SHORT:
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, CSTACK_NAME, "getInt", "(I)I", false);
            mv.visitInsn(Opcodes.I2S);
            break;
        case Type.CHAR:
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, CSTACK_NAME, "getInt", "(I)I", false);
            mv.visitInsn(Opcodes.I2C);
            break;
        case Type.BOOLEAN:
        case Type.INT:
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, CSTACK_NAME, "getInt", "(I)I", false);
            break;
        case Type.FLOAT:
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, CSTACK_NAME, "getFloat", "(I)F", false);
            break;
        case Type.LONG:
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, CSTACK_NAME, "getLong", "(I)J", false);
            break;
        case Type.DOUBLE:
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, CSTACK_NAME, "getDouble", "(I)D", false);
            break;
        default:
            throw new InternalError("Unexpected type: " + v.getType());
        }
    }
    
    static boolean isNullType(BasicValue v) {
        return (v == BasicValue.UNINITIALIZED_VALUE) ||
                (v.isReference() && v.getType().getInternalName().equals("null"));
    }
    
    static boolean isOmitted(BasicValue v) {
        if(v instanceof NewValue) {
            return ((NewValue)v).omitted;
        }
        return false;
    }
    
    static boolean isNewValue(Value v, boolean dupped) {
        if(v instanceof NewValue) {
            return ((NewValue)v).isDupped == dupped;
        }
        return false;
    }
    
    static class BlockLabelNode extends LabelNode {
        final int idx;

        BlockLabelNode(int idx) {
            this.idx = idx;
        }
    }
    
    static class FrameInfo {
        static final FrameInfo FIRST = new FrameInfo(null, 0, 0, null, null);
        
        final int endInstruction;
        final int numSlots;
        final int numObjSlots;
        final int[] localSlotIndices;
        final int[] stackSlotIndices;
    
        BlockLabelNode lBefore;
        BlockLabelNode lAfter;
        
        FrameInfo(Frame f, int firstLocal, int endInstruction, InsnList insnList, MethodDatabase db) {
            this.endInstruction = endInstruction;
            
            int idxObj = 0;
            int idxPrim = 0;
            
            if(f != null) {
                stackSlotIndices = new int[f.getStackSize()];
                for(int i=0 ; i<f.getStackSize() ; i++) {
                    BasicValue v = (BasicValue)f.getStack(i);
                    if(v instanceof NewValue) {
                        NewValue newValue = (NewValue)v;
                        if(db.isDebug()) {
                            db.trace("Omit value from stack idx %d at instruction %d with type %s generated by %s",
                                    i, endInstruction, v, newValue.formatInsn());
                        }
                        if(!newValue.omitted) {
                            newValue.omitted = true;
                            if(db.isDebug()) {
                                // need to log index before replacing instruction
                                db.trace("Omitting instruction %d: %s", insnList.indexOf(newValue.insn), newValue.formatInsn());
                            }
                            insnList.set(newValue.insn, new OmittedInstruction(newValue.insn));
                        }
                        stackSlotIndices[i] = -666; // an invalid index ;)
                    } else if(!isNullType(v)) {
                        if(v.isReference()) {
                            stackSlotIndices[i] = idxObj++;
                        } else {
                            stackSlotIndices[i] = idxPrim++;
                        }
                    } else {
                        stackSlotIndices[i] = -666; // an invalid index ;)
                    }
                }

                localSlotIndices = new int[f.getLocals()];
                for(int i=firstLocal ; i<f.getLocals() ; i++) {
                    BasicValue v = (BasicValue)f.getLocal(i);
                    if(!isNullType(v)) {
                        if(v.isReference()) {
                            localSlotIndices[i] = idxObj++;
                        } else {
                            localSlotIndices[i] = idxPrim++;
                        }
                    } else {
                        localSlotIndices[i] = -666; // an invalid index ;)
                    }
                }
            } else {
                stackSlotIndices = null;
                localSlotIndices = null;
            }
            
            numSlots = Math.max(idxPrim, idxObj);
            numObjSlots = idxObj;
        }
        
        public LabelNode createBeforeLabel() {
            if(lBefore == null) {
                lBefore = new BlockLabelNode(endInstruction);
            }
            return lBefore;
        }
        
        public LabelNode createAfterLabel() {
            if(lAfter == null) {
                lAfter = new BlockLabelNode(endInstruction);
            }
            return lAfter;
        }
    }
}
