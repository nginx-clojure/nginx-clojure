/*
 * Copyright (C) 2014 Zhang,Yuexiang (xfeep)
 * All rights reserved.
 */
package nginx.clojure.wave;

import nginx.clojure.asm.Opcodes;
import nginx.clojure.asm.tree.MethodNode;
import nginx.clojure.wave.MethodDatabase.ClassEntry;

public class InstrumentMethodNode extends MethodNode {

	MethodDatabase db;
	

	public InstrumentMethodNode() {
	}

	public InstrumentMethodNode(int api) {
		super(api);
	}

	public InstrumentMethodNode(MethodDatabase db, int access, String name, String desc,
			String signature, String[] exceptions) {
		super(Opcodes.ASM4, access, name, desc, signature, exceptions);
		this.db = db;
	}

	@Override
	public void visitMethodInsn(int opcode, String owner, String name, String desc) {
		if (name.charAt(0) == '<' && name.charAt(1) == 'i' && db != null && db.checkMethodSuspendType(owner, ClassEntry.key(name, desc), false, false) == MethodDatabase.SUSPEND_NORMAL) {
			super.visitInsn(Opcodes.ACONST_NULL);
			super.visitMethodInsn(opcode, owner, name, InstrumentConstructorMethod.buildShrinkedInitMethodDesc(desc));
			super.visitInsn(Opcodes.DUP);
			super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner, InstrumentConstructorMethod.buildInitHelpMethodName(desc), "()V");
		}else {
			super.visitMethodInsn(opcode, owner, name, desc);
		}
	}
	
	@Override
	public void visitMaxs(int maxStack, int maxLocals) {
		super.visitMaxs(maxStack + 1, maxLocals + 1);
	}
	
	public MethodDatabase getDb() {
		return db;
	}

	public void setDb(MethodDatabase db) {
		this.db = db;
	}
}
