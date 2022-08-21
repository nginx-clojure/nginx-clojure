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

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import nginx.clojure.Stack;
import nginx.clojure.SuspendExecution;
import nginx.clojure.asm.Label;
import nginx.clojure.asm.MethodVisitor;
import nginx.clojure.asm.Opcodes;
import nginx.clojure.asm.Type;
import nginx.clojure.asm.tree.AbstractInsnNode;
import nginx.clojure.asm.tree.AnnotationNode;
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
import nginx.clojure.wave.SuspendMethodVerifier.VerifyVarInfo;

/**
 * Instrument a method to allow suspension
 * 
 * @author Matthias Mann
 * @author Zhang,Yuexiang (xfeep)
 */
@SuppressWarnings({"rawtypes"})
public class InstrumentMethod {

	private static final String STACK_NAME = Type.getInternalName(Stack.class);

	private static final String STACK_PUSH_OBJECT_VALUE_DESC = "(Ljava/lang/Object;L" + STACK_NAME + ";I)V";

	private static final String STACK_PUSHV_OBJECT_VALUE_DESC = "(Ljava/lang/Object;L" + STACK_NAME + ";ILjava/lang/String;)V";

	private static final String STACK_PUSH_DOUBLE_VALUE_DESC = "(DL" + STACK_NAME + ";I)V";

	private static final String STACK_PUSHV_DOUBLE_VALUE_DESC = "(DL" + STACK_NAME + ";ILjava/lang/String;)V";

	private static final String STACK_PUSH_LONG_VALUE_DESC = "(JL" + STACK_NAME + ";I)V";

	private static final String STACK_PUSHV_LONG_VALUE_DESC = "(JL" + STACK_NAME + ";ILjava/lang/String;)V";

	private static final String STACK_PUSH_FLOAT_VALUE_DESC = "(FL" + STACK_NAME + ";I)V";

	private static final String STACK_PUSHV_FLOAT_VALUE_DESC = "(FL" + STACK_NAME + ";ILjava/lang/String;)V";

	private static final String STACK_PUSH_INT_VALUE_DESC = "(IL" + STACK_NAME + ";I)V";

	private static final String STACK_PUSHV_INT_VALUE_DESC = "(IL" + STACK_NAME + ";ILjava/lang/String;)V";

	private final MethodDatabase db;
	private final String className;
	private final String classAndMethod;
	private final MethodNode mn;
	private final Frame[] frames;
	private final int lvarStack;
	private final int firstLocal;

	private FrameInfo[] codeBlocks = new FrameInfo[32];
	private int numCodeBlocks;
	private int additionalLocals;

	@SuppressWarnings("unused")
	private boolean warnedAboutMonitors;
	@SuppressWarnings("unused")
	private boolean warnedAboutBlocking;

	private boolean hasReflectInvoke;

	private Set<LabelNode> reflectExceptionHandlers;

	private final static Set<String> REFLECT_EXCEPTION_SET = new HashSet<String>(
			Arrays.asList("java/lang/reflect/InvocationTargetException", "java/lang/reflect/ReflectiveOperationException",
					"java/lang/Exception", "java/lang/Throwable"));

	private VerifyVarInfo[][] verifyVarInfoss;

	public InstrumentMethod(MethodDatabase db, String className, MethodNode mn) throws AnalyzerException {
		this.db = db;
		this.className = className;
		this.mn = mn;
		this.classAndMethod = className + "." + mn.name + mn.desc;
		try {
			Analyzer a = MethodDatabaseUtil.buildAnalyzer(db);
			this.frames = a.analyze(className, mn);
			this.lvarStack = mn.maxLocals;
			this.firstLocal = ((mn.access & Opcodes.ACC_STATIC) == Opcodes.ACC_STATIC) ? 0 : 1;
		} catch (UnsupportedOperationException ex) {
			throw new AnalyzerException(null, ex.getMessage(), ex);
		}
	}

	public boolean collectCodeBlocks() {
		int numIns = mn.instructions.size();

		codeBlocks[0] = FrameInfo.FIRST;
		for (int i = 0; i < numIns; i++) {
			Frame f = frames[i];
			if (f != null) { // reachable ?
				AbstractInsnNode in = mn.instructions.get(i);
				if (in.getType() == AbstractInsnNode.METHOD_INSN) {
					MethodInsnNode min = (MethodInsnNode) in;
					int opcode = min.getOpcode();
					if (min.owner.equals("java/lang/reflect/Method") && min.name.equals("invoke")) {
						hasReflectInvoke = true;
					}
					Integer st = db.checkMethodSuspendType(min.owner, ClassEntry.key(min.name, min.desc),
							opcode == Opcodes.INVOKEVIRTUAL || opcode == Opcodes.INVOKESTATIC
									|| opcode == Opcodes.INVOKEINTERFACE);
					if (st == MethodDatabase.SUSPEND_NORMAL || st == MethodDatabase.SUSPEND_FAMILY
							|| st == MethodDatabase.SUSPEND_JUST_MARK) {
						db.trace("Method call at instruction %d to %s#%s%s is suspendable", i, min.owner, min.name, min.desc);
						FrameInfo fi = addCodeBlock(f, i);
						splitTryCatch(fi);
					} else {
						if (st == MethodDatabase.SUSPEND_BLOCKING) {
							if (!db.isAllowBlocking()) {
								throw new UnableToInstrumentException("blocking call to " + min.owner + "#" + min.name + min.desc,
										className, mn.name, mn.desc);
							} else {
								warnedAboutBlocking = true;
								db.warn("Method %s#%s%s contains potentially blocking call to " + min.owner + "#" + min.name
										+ min.desc, className, mn.name, mn.desc);
							}
						}
					}
				}
			}
		}
		addCodeBlock(null, numIns);

		return numCodeBlocks > 1;
	}

	public void accept(MethodVisitor mv) {
		db.trace("Instrumenting method %s.%s%s", className, mn.name, mn.desc);
		if (db.isDebug() && db.meetTraceTargetClassMethod(classAndMethod)) {
			db.info("Instrumenting meet traced method %s.%s%s", className, mn.name, mn.desc);
		}

		if (db.isVerify()) {
			verifyVarInfoss = new VerifyVarInfo[numCodeBlocks - 1][];
		}

		mv.visitCode();

		Label lMethodStart = new Label();
		Label lMethodEnd = new Label();
		Label lCatchSEE = new Label();
		Label lCatchAll = new Label();
		Label[] lMethodCalls = new Label[numCodeBlocks - 1];

		for (int i = 1; i < numCodeBlocks; i++) {
			lMethodCalls[i - 1] = new Label();
		}

		mv.visitTryCatchBlock(lMethodStart, lMethodEnd, lCatchSEE, CheckInstrumentationVisitor.EXCEPTION_NAME);

		for (TryCatchBlockNode tcb : mn.tryCatchBlocks) {
			if (hasReflectInvoke && REFLECT_EXCEPTION_SET.contains(tcb.type)) {
				if (reflectExceptionHandlers == null) {
					reflectExceptionHandlers = new HashSet<LabelNode>();
				}
				reflectExceptionHandlers.add(tcb.handler);
			}
			if (CheckInstrumentationVisitor.EXCEPTION_NAME.equals(tcb.type)) {
				throw new UnableToInstrumentException("catch for " + SuspendExecution.class.getSimpleName(), className, mn.name,
						mn.desc);
			}
			tcb.accept(mv);
		}

		if (mn.visibleParameterAnnotations != null) {
			dumpParameterAnnotations(mv, mn.visibleParameterAnnotations, true);
		}

		if (mn.invisibleParameterAnnotations != null) {
			dumpParameterAnnotations(mv, mn.invisibleParameterAnnotations, false);
		}

		if (mn.visibleAnnotations != null) {
			for (Object o : mn.visibleAnnotations) {
				AnnotationNode an = (AnnotationNode) o;
				an.accept(mv.visitAnnotation(an.desc, true));
			}
		}

		mv.visitTryCatchBlock(lMethodStart, lMethodEnd, lCatchAll, null);

		mv.visitMethodInsn(Opcodes.INVOKESTATIC, STACK_NAME, "getStack", "()L" + STACK_NAME + ";", false);
		mv.visitInsn(Opcodes.DUP);
		mv.visitVarInsn(Opcodes.ASTORE, lvarStack);

		if (db.isAllowOutofCoroutine()) {
			mv.visitJumpInsn(Opcodes.IFNULL, lMethodStart);
			mv.visitVarInsn(Opcodes.ALOAD, lvarStack);
		}

		if (verifyVarInfoss != null) {
			mv.visitLdcInsn(classAndMethod);
			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STACK_NAME, "nextMethodEntryV", "(Ljava/lang/String;)I", false);
		} else {
			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STACK_NAME, "nextMethodEntry", "()I", false);
		}

		mv.visitInsn(Opcodes.DUP);
		Label tableSwitchLabel = new Label();
		mv.visitJumpInsn(Opcodes.IFGE, tableSwitchLabel);
		mv.visitInsn(Opcodes.POP);
		mv.visitInsn(Opcodes.ACONST_NULL);
		mv.visitVarInsn(Opcodes.ASTORE, lvarStack);
		mv.visitJumpInsn(Opcodes.GOTO, lMethodStart);

		mv.visitLabel(tableSwitchLabel);
		mv.visitTableSwitchInsn(1, numCodeBlocks - 1, lMethodStart, lMethodCalls);

		mv.visitLabel(lMethodStart);
		dumpCodeBlock(mv, 0, 0);

		for (int i = 1; i < numCodeBlocks; i++) {
			FrameInfo fi = codeBlocks[i];

			MethodInsnNode min = (MethodInsnNode) (mn.instructions.get(fi.endInstruction));
			if (InstrumentClass.COROUTINE_NAME.equals(min.owner) && "yield".equals(min.name)) {
				// special case - call to yield() - resume AFTER the call
				if (min.getOpcode() != Opcodes.INVOKESTATIC) {
					throw new UnableToInstrumentException("invalid call to yield()", className, mn.name, mn.desc);
				}

				if (verifyVarInfoss != null) {
					mv.visitMethodInsn(Opcodes.INVOKESTATIC, "nginx/clojure/wave/SuspendMethodVerifier", "onYield", "()V", false);
				}

				emitStoreState(mv, i, fi);
				mv.visitFieldInsn(Opcodes.GETSTATIC, STACK_NAME, "exception_instance_not_for_user_code",
						CheckInstrumentationVisitor.EXCEPTION_DESC);
				mv.visitInsn(Opcodes.ATHROW);
				min.accept(mv); // only the call
				mv.visitLabel(lMethodCalls[i - 1]);
				emitRestoreState(mv, i, fi);
				dumpCodeBlock(mv, i, 1); // skip the call
			} else {

				final Label ocl = new Label();
				if (db.isAllowOutofCoroutine()) {
					mv.visitVarInsn(Opcodes.ALOAD, lvarStack);
					mv.visitJumpInsn(Opcodes.IFNULL, ocl);
				}

				// normal case - call to a suspendable method - resume before
				// the call
				emitStoreState(mv, i, fi);
				mv.visitLabel(lMethodCalls[i - 1]);
				emitRestoreState(mv, i, fi);

				if (db.isAllowOutofCoroutine()) {
					mv.visitLabel(ocl);
				}

				dumpCodeBlock(mv, i, 0);
			}
		}

		mv.visitLabel(lMethodEnd);

		mv.visitLabel(lCatchAll);
		if (hasReflectInvoke) {
			emitRelectExceptionHandleCode(mv, true);
		}
		emitPopMethod(mv);
		mv.visitLabel(lCatchSEE);
		mv.visitInsn(Opcodes.ATHROW); // rethrow shared between catchAll and
										// catchSSE

		if (mn.localVariables != null) {
			for (Object o : mn.localVariables) {
				((LocalVariableNode) o).accept(mv);
			}
		}
		if (verifyVarInfoss != null) {
			mv.visitMaxs(mn.maxStack + 4, mn.maxLocals + 1 + additionalLocals);
			db.getVerfiyMethodInfos().put(classAndMethod, verifyVarInfoss);
		} else {
			mv.visitMaxs(mn.maxStack + 3, mn.maxLocals + 1 + additionalLocals);
		}
		mv.visitEnd();
	}

	private void emitRelectExceptionHandleCode(MethodVisitor mv, boolean doThrow) {
		Label lNoReflectException = new Label();
		mv.visitInsn(Opcodes.DUP);
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Throwable", "getCause", "()Ljava/lang/Throwable;", false);
		mv.visitFieldInsn(Opcodes.GETSTATIC, "nginx/clojure/Stack", "exception_instance_not_for_user_code",
				"Lnginx/clojure/SuspendExecution;");
		mv.visitJumpInsn(Opcodes.IF_ACMPNE, lNoReflectException);
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Throwable", "getCause", "()Ljava/lang/Throwable;", false);
		if (doThrow) {
			mv.visitInsn(Opcodes.ATHROW);
		}
		mv.visitLabel(lNoReflectException);
	}

	private FrameInfo addCodeBlock(Frame f, int end) {
		if (++numCodeBlocks == codeBlocks.length) {
			FrameInfo[] newArray = new FrameInfo[numCodeBlocks * 2];
			System.arraycopy(codeBlocks, 0, newArray, 0, codeBlocks.length);
			codeBlocks = newArray;
		}
		FrameInfo fi = new FrameInfo(f, firstLocal, end, mn.instructions, db);
		codeBlocks[numCodeBlocks] = fi;
		return fi;
	}

	private int getLabelIdx(LabelNode l) {
		int idx;
		if (l instanceof BlockLabelNode) {
			idx = ((BlockLabelNode) l).idx;
		} else {
			idx = mn.instructions.indexOf(l);
		}

		if (idx == mn.instructions.size() - 1) {
			return idx;
		}
		// search for the "real" instruction
		for (;;) {
			int type = mn.instructions.get(idx).getType();
			if (type != AbstractInsnNode.LABEL && type != AbstractInsnNode.LINE) {
				return idx;
			}
			idx++;
		}
	}

	private void splitTryCatch(FrameInfo fi) {
		for (int i = 0; i < mn.tryCatchBlocks.size(); i++) {
			TryCatchBlockNode tcb = (TryCatchBlockNode) mn.tryCatchBlocks.get(i);

			int start = getLabelIdx(tcb.start);
			int end = getLabelIdx(tcb.end);

			if (start <= fi.endInstruction && end >= fi.endInstruction) {
				// System.out.println("i="+i+" start="+start+" end="+end+"
				// split="+splitIdx+
				// " start="+mn.instructions.get(start)+"
				// end="+mn.instructions.get(end));

				// need to split try/catch around the suspendable call
				if (start == fi.endInstruction) {
					tcb.start = fi.createAfterLabel();
				} else {
					if (end > fi.endInstruction) {
						TryCatchBlockNode tcb2 = new TryCatchBlockNode(fi.createAfterLabel(), tcb.end, tcb.handler, tcb.type);
						mn.tryCatchBlocks.add(i + 1, tcb2);
					}

					tcb.end = fi.createBeforeLabel();
				}
			}
		}
	}

	@SuppressWarnings("unused")
	private void dumpCodeBlock(MethodVisitor mv, int idx, int skip) {
		int start = codeBlocks[idx].endInstruction;
		int end = codeBlocks[idx + 1].endInstruction;

		for (int i = start + skip; i < end; i++) {
			AbstractInsnNode ins = mn.instructions.get(i);

			if (ins instanceof LabelNode) {
				ins.accept(mv);
				LabelNode ln = (LabelNode) ins;
				if (hasReflectInvoke && reflectExceptionHandlers != null && reflectExceptionHandlers.contains(ln)) {
					emitRelectExceptionHandleCode(mv, true);
				}
				continue;
			}

			switch (ins.getOpcode()) {
			case Opcodes.RETURN:
			case Opcodes.ARETURN:
			case Opcodes.IRETURN:
			case Opcodes.LRETURN:
			case Opcodes.FRETURN:
			case Opcodes.DRETURN:
				emitPopMethod(mv);
				break;

			case Opcodes.MONITORENTER:
			case Opcodes.MONITOREXIT:
				if (!db.isAllowMonitors()) {
					throw new UnableToInstrumentException("synchronisation", className, mn.name, mn.desc);
				} else {
					warnedAboutMonitors = true;
					if (true) {
						db.warn("Method %s#%s%s contains synchronisation, we'll clear it", className, mn.name, mn.desc);
						mv.visitInsn(Opcodes.POP);
						continue;
					}
				}
				break;

			case Opcodes.INVOKESPECIAL:
				MethodInsnNode min = (MethodInsnNode) ins;
				if ("<init>".equals(min.name)) {
					int argSize = TypeAnalyzer.getNumArguments(min.desc);
					Frame frame = frames[i];
					if (frame != null) {
						int stackIndex = frame.getStackSize() - argSize - 1;
						Value thisValue = frame.getStack(stackIndex);
						if (stackIndex >= 1 && isNewValue(thisValue, true) && isNewValue(frame.getStack(stackIndex - 1), false)) {
							NewValue newValue = (NewValue) thisValue;
							if (newValue.omitted) {
								emitNewAndDup(mv, frame, stackIndex, min);
							}
						} else {
							db.warn("Expected to find a NewValue on stack index %d: %s", stackIndex, frame);
						}
					} else {
						db.error("frame is null!!");
					}
				}
				break;
				
			case Opcodes.INVOKEVIRTUAL:
				min = (MethodInsnNode) ins;
				if ("sun/security/ssl/SSLSocketImpl".equals(className) && "java/lang/Object".equals(min.owner)
						&& ("notifyAll".equals(min.name) || "notify".equals(min.name) || "wait".equals(min.name))) {
					if (db.isAllowMonitors()) {
						db.warn("Method %s#%s%s contains wait/notify/notifyAll, we'll clear it", className, mn.name, mn.desc);
						mv.visitInsn(Opcodes.POP);
						continue;
					}
				}
				break;
			}
			ins.accept(mv);
		}
	}

	private static void dumpParameterAnnotations(MethodVisitor mv, List[] parameterAnnotations, boolean visible) {
		for (int i = 0; i < parameterAnnotations.length; i++) {
			if (parameterAnnotations[i] != null) {
				for (Object o : parameterAnnotations[i]) {
					AnnotationNode an = (AnnotationNode) o;
					an.accept(mv.visitParameterAnnotation(i, an.desc, visible));
				}
			}
		}
	}

	private static void emitConst(MethodVisitor mv, int value) {
		if (value >= -1 && value <= 5) {
			mv.visitInsn(Opcodes.ICONST_0 + value);
		} else if ((byte) value == value) {
			mv.visitIntInsn(Opcodes.BIPUSH, value);
		} else if ((short) value == value) {
			mv.visitIntInsn(Opcodes.SIPUSH, value);
		} else {
			mv.visitLdcInsn(value);
		}
	}

	private void emitNewAndDup(MethodVisitor mv, Frame frame, int stackIndex, MethodInsnNode min) {
		int arguments = frame.getStackSize() - stackIndex - 1;
		int neededLocals = 0;
		for (int i = arguments; i >= 1; i--) {
			BasicValue v = (BasicValue) frame.getStack(stackIndex + i);
			mv.visitVarInsn(v.getType().getOpcode(Opcodes.ISTORE), lvarStack + 1 + neededLocals);
			neededLocals += v.getSize();
		}
		db.trace("Inserting NEW & DUP for constructor call %s%s with %d arguments (%d locals)", min.owner, min.desc, arguments,
				neededLocals);
		if (additionalLocals < neededLocals) {
			additionalLocals = neededLocals;
		}
		((NewValue) frame.getStack(stackIndex - 1)).insn.accept(mv);
		((NewValue) frame.getStack(stackIndex)).insn.accept(mv);
		for (int i = 1; i <= arguments; i++) {
			BasicValue v = (BasicValue) frame.getStack(stackIndex + i);
			neededLocals -= v.getSize();
			mv.visitVarInsn(v.getType().getOpcode(Opcodes.ILOAD), lvarStack + 1 + neededLocals);
		}
	}

	private void emitPopMethod(MethodVisitor mv) {

		final Label ocl = new Label();
		if (db.isAllowOutofCoroutine()) {
			mv.visitVarInsn(Opcodes.ALOAD, lvarStack);
			mv.visitJumpInsn(Opcodes.IFNULL, ocl);
		}

		mv.visitVarInsn(Opcodes.ALOAD, lvarStack);

		if (verifyVarInfoss != null) {
			mv.visitLdcInsn(classAndMethod);
			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STACK_NAME, "popMethodV", "(Ljava/lang/String;)V", false);
		} else {
			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STACK_NAME, "popMethod", "()V", false);
		}

		if (db.isAllowOutofCoroutine()) {
			mv.visitLabel(ocl);
		}
	}

	private LocalVariableNode findVarNode(int i) {
		for (LocalVariableNode lvn : mn.localVariables) {
			if (lvn.index == i) {
				return lvn;
			}
		}
		return null;
	}

	private void emitStoreState(MethodVisitor mv, int idx, FrameInfo fi) {
		Frame f = frames[fi.endInstruction];

		if (verifyVarInfoss != null) {
			VerifyVarInfo[] vis = verifyVarInfoss[idx - 1] = new VerifyVarInfo[fi.numSlots * 2];
			for (int i = f.getStackSize(); i-- > 0;) {
				BasicValue v = (BasicValue) f.getStack(i);
				if (!isOmitted(v) && !isNullType(v)) {
					VerifyVarInfo vi = new VerifyVarInfo();
					int slotIdx = fi.stackSlotIndices[i];
					vi.idx = i;
					vi.name = "_NGX_STACK_VAL_";
					vi.dataIdx = slotIdx;
					vi.value = v;
					if (v.isReference()) {
						vis[slotIdx] = vi;
					} else {
						vis[fi.numSlots + slotIdx] = vi;
					}
				}
			}

			for (int i = firstLocal; i < f.getLocals(); i++) {
				BasicValue v = (BasicValue) f.getLocal(i);
				if (!isNullType(v)) {
					VerifyVarInfo vi = new VerifyVarInfo();
					int slotIdx = fi.localSlotIndices[i];
					LocalVariableNode lvn = findVarNode(i);
					if (lvn != null) {
						vi.name = lvn.name;
						vi.idx = i;
					}
					vi.dataIdx = slotIdx;
					vi.value = v;

					if (v.isReference()) {
						vis[slotIdx] = vi;
					} else {
						vis[fi.numSlots + slotIdx] = vi;
					}
				}
			}
		}

		if (fi.lBefore != null) {
			fi.lBefore.accept(mv);
		}

		mv.visitVarInsn(Opcodes.ALOAD, lvarStack);
		emitConst(mv, idx);
		emitConst(mv, fi.numSlots);
		if (verifyVarInfoss != null) {
			mv.visitLdcInsn(classAndMethod);
			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STACK_NAME, "pushMethodAndReserveSpaceV", "(IILjava/lang/String;)V", false);
		} else {
			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STACK_NAME, "pushMethodAndReserveSpace", "(II)V", false);
		}

		for (int i = f.getStackSize(); i-- > 0;) {
			BasicValue v = (BasicValue) f.getStack(i);
			if (!isOmitted(v)) {
				if (!isNullType(v)) {
					int slotIdx = fi.stackSlotIndices[i];
					assert slotIdx >= 0 && slotIdx < fi.numSlots;
					emitStoreValue(mv, v, lvarStack, slotIdx);
				} else {
					db.trace("NULL stack entry: type=%s size=%d", v.getType(), v.getSize());
					mv.visitInsn(Opcodes.POP);
				}
			}
		}

		for (int i = firstLocal; i < f.getLocals(); i++) {
			BasicValue v = (BasicValue) f.getLocal(i);
			if (!isNullType(v)) {
				mv.visitVarInsn(v.getType().getOpcode(Opcodes.ILOAD), i);
				int slotIdx = fi.localSlotIndices[i];
				assert slotIdx >= 0 && slotIdx < fi.numSlots;
				emitStoreValue(mv, v, lvarStack, slotIdx);
			}
		}
	}

	private void emitRestoreState(MethodVisitor mv, int idx, FrameInfo fi) {
		Frame f = frames[fi.endInstruction];

		for (int i = firstLocal; i < f.getLocals(); i++) {
			BasicValue v = (BasicValue) f.getLocal(i);
			if (!isNullType(v)) {
				int slotIdx = fi.localSlotIndices[i];
				assert slotIdx >= 0 && slotIdx < fi.numSlots;
				emitRestoreValue(mv, v, lvarStack, slotIdx);
				mv.visitVarInsn(v.getType().getOpcode(Opcodes.ISTORE), i);
			} else if (v != BasicValue.UNINITIALIZED_VALUE) {
				mv.visitInsn(Opcodes.ACONST_NULL);
				mv.visitVarInsn(Opcodes.ASTORE, i);
			}
		}

		for (int i = 0; i < f.getStackSize(); i++) {
			BasicValue v = (BasicValue) f.getStack(i);
			if (!isOmitted(v)) {
				if (!isNullType(v)) {
					int slotIdx = fi.stackSlotIndices[i];
					assert slotIdx >= 0 && slotIdx < fi.numSlots;
					emitRestoreValue(mv, v, lvarStack, slotIdx);
				} else {
					mv.visitInsn(Opcodes.ACONST_NULL);
				}
			}
		}

		if (fi.lAfter != null) {
			fi.lAfter.accept(mv);
		}
	}

	private void emitStoreValue(MethodVisitor mv, BasicValue v, int lvarStack, int idx)
			throws InternalError, IndexOutOfBoundsException {
		String desc;

		switch (v.getType().getSort()) {
		case Type.OBJECT:
		case Type.ARRAY:
			if (verifyVarInfoss != null) {
				desc = STACK_PUSHV_OBJECT_VALUE_DESC;
			} else {
				desc = STACK_PUSH_OBJECT_VALUE_DESC;
			}
			break;
		case Type.BOOLEAN:
		case Type.BYTE:
		case Type.SHORT:
		case Type.CHAR:
		case Type.INT:
			if (verifyVarInfoss != null) {
				desc = STACK_PUSHV_INT_VALUE_DESC;
			} else {
				desc = STACK_PUSH_INT_VALUE_DESC;
			}
			break;
		case Type.FLOAT:
			if (verifyVarInfoss != null) {
				desc = STACK_PUSHV_FLOAT_VALUE_DESC;
			} else {
				desc = STACK_PUSH_FLOAT_VALUE_DESC;
			}
			break;
		case Type.LONG:
			if (verifyVarInfoss != null) {
				desc = STACK_PUSHV_LONG_VALUE_DESC;
			} else {
				desc = STACK_PUSH_LONG_VALUE_DESC;
			}
			break;
		case Type.DOUBLE:
			if (verifyVarInfoss != null) {
				desc = STACK_PUSHV_DOUBLE_VALUE_DESC;
			} else {
				desc = STACK_PUSH_DOUBLE_VALUE_DESC;
			}
			break;
		default:
			throw new InternalError("Unexpected type: " + v.getType());
		}

		mv.visitVarInsn(Opcodes.ALOAD, lvarStack);
		emitConst(mv, idx);
		if (verifyVarInfoss != null) {
			mv.visitLdcInsn(classAndMethod);
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, STACK_NAME, "pushV", desc, false);
		} else {
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, STACK_NAME, "push", desc, false);
		}
	}

	private void emitRestoreValue(MethodVisitor mv, BasicValue v, int lvarStack, int idx) {
		mv.visitVarInsn(Opcodes.ALOAD, lvarStack);
		emitConst(mv, idx);
		if (verifyVarInfoss != null) {
			mv.visitLdcInsn(classAndMethod);
		}
		switch (v.getType().getSort()) {
		case Type.OBJECT:
			String internalName = v.getType().getInternalName();
			if (verifyVarInfoss != null) {
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STACK_NAME, "getObjectV", "(ILjava/lang/String;)Ljava/lang/Object;", false);
			} else {
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STACK_NAME, "getObject", "(I)Ljava/lang/Object;", false);
			}
			if (!internalName.equals("java/lang/Object")) { // don't cast to
															// Object ;)
				mv.visitTypeInsn(Opcodes.CHECKCAST, internalName);
			}
			break;
		case Type.ARRAY:
			if (verifyVarInfoss != null) {
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STACK_NAME, "getObjectV", "(ILjava/lang/String;)Ljava/lang/Object;", false);
			} else {
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STACK_NAME, "getObject", "(I)Ljava/lang/Object;", false);
			}
			mv.visitTypeInsn(Opcodes.CHECKCAST, v.getType().getDescriptor());
			break;
		case Type.BYTE:
			if (verifyVarInfoss != null) {
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STACK_NAME, "getIntV", "(ILjava/lang/String;)I", false);
			} else {
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STACK_NAME, "getInt", "(I)I", false);
			}
			mv.visitInsn(Opcodes.I2B);
			break;
		case Type.SHORT:
			if (verifyVarInfoss != null) {
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STACK_NAME, "getIntV", "(ILjava/lang/String;)I", false);
			} else {
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STACK_NAME, "getInt", "(I)I", false);
			}
			mv.visitInsn(Opcodes.I2S);
			break;
		case Type.CHAR:
			if (verifyVarInfoss != null) {
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STACK_NAME, "getIntV", "(ILjava/lang/String;)I", false);
			} else {
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STACK_NAME, "getInt", "(I)I", false);
			}
			mv.visitInsn(Opcodes.I2C);
			break;
		case Type.BOOLEAN:
		case Type.INT:
			if (verifyVarInfoss != null) {
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STACK_NAME, "getIntV", "(ILjava/lang/String;)I", false);
			} else {
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STACK_NAME, "getInt", "(I)I", false);
			}
			break;
		case Type.FLOAT:
			if (verifyVarInfoss != null) {
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STACK_NAME, "getFloatV", "(ILjava/lang/String;)F", false);
			} else {
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STACK_NAME, "getFloat", "(I)F", false);
			}
			break;
		case Type.LONG:
			if (verifyVarInfoss != null) {
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STACK_NAME, "getLongV", "(ILjava/lang/String;)J", false);
			} else {
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STACK_NAME, "getLong", "(I)J", false);
			}
			break;
		case Type.DOUBLE:
			if (verifyVarInfoss != null) {
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STACK_NAME, "getLongV", "(ILjava/lang/String;)D", false);
			} else {
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STACK_NAME, "getDouble", "(I)D", false);
			}
			break;
		default:
			throw new InternalError("Unexpected type: " + v.getType());
		}
	}

	static boolean isNullType(BasicValue v) {
		return (v == BasicValue.UNINITIALIZED_VALUE) || (v.isReference() && v.getType().getInternalName().equals("null"));
	}

	static boolean isOmitted(BasicValue v) {
		if (v instanceof NewValue) {
			return ((NewValue) v).omitted;
		}
		return false;
	}

	static boolean isNewValue(Value v, boolean dupped) {
		if (v instanceof NewValue) {
			return ((NewValue) v).isDupped == dupped;
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

			if (f != null) {
				stackSlotIndices = new int[f.getStackSize()];
				for (int i = 0; i < f.getStackSize(); i++) {
					BasicValue v = (BasicValue) f.getStack(i);
					if (v instanceof NewValue) {
						NewValue newValue = (NewValue) v;
						if (db.isDebug()) {
							db.trace("Omit value from stack idx %d at instruction %d with type %s generated by %s", i,
									endInstruction, v, newValue.formatInsn());
						}
						if (!newValue.omitted) {
							newValue.omitted = true;
							if (db.isDebug()) {
								// need to log index before replacing
								// instruction
								db.trace("Omitting instruction %d: %s", insnList.indexOf(newValue.insn), newValue.formatInsn());
							}
							insnList.set(newValue.insn, new OmittedInstruction(newValue.insn));
						}
						stackSlotIndices[i] = -666; // an invalid index ;)
					} else if (!isNullType(v)) {
						if (v.isReference()) {
							stackSlotIndices[i] = idxObj++;
						} else {
							stackSlotIndices[i] = idxPrim++;
						}
					} else {
						stackSlotIndices[i] = -666; // an invalid index ;)
					}
				}

				localSlotIndices = new int[f.getLocals()];
				for (int i = firstLocal; i < f.getLocals(); i++) {
					BasicValue v = (BasicValue) f.getLocal(i);
					if (!isNullType(v)) {
						if (v.isReference()) {
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
			if (lBefore == null) {
				lBefore = new BlockLabelNode(endInstruction);
			}
			return lBefore;
		}

		public LabelNode createAfterLabel() {
			if (lAfter == null) {
				lAfter = new BlockLabelNode(endInstruction);
			}
			return lAfter;
		}
	}
}
