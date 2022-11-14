/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.wave;

import nginx.clojure.asm.ClassVisitor;
import nginx.clojure.asm.MethodVisitor;
import nginx.clojure.asm.Opcodes;

/**
 * @author Zhang,Yuexiang (xfeep)
 *
 */
public class RemoveMonitorVisitor extends ClassVisitor {

	/**
	 * @param api
	 * @param classVisitor
	 */
	protected RemoveMonitorVisitor(int api, ClassVisitor classVisitor) {
		super(api, classVisitor);
	}
	
	/* (non-Javadoc)
	 * @see nginx.clojure.asm.ClassVisitor#visitMethod(int, java.lang.String, java.lang.String, java.lang.String, java.lang.String[])
	 */
	@Override
	public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
		if ((access & Opcodes.ACC_SYNCHRONIZED) == Opcodes.ACC_SYNCHRONIZED) {
			access &= ~Opcodes.ACC_SYNCHRONIZED;
		}
		return new RemoveMonitorMethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, descriptor, signature, exceptions));
	}
	
}

class RemoveMonitorMethodVisitor extends MethodVisitor {
	/**
	 * @param api
	 * @param methodVisitor
	 */
	protected RemoveMonitorMethodVisitor(int api, MethodVisitor methodVisitor) {
		super(api, methodVisitor);
	}
	
	/* (non-Javadoc)
	 * @see nginx.clojure.asm.MethodVisitor#visitInsn(int)
	 */
	@Override
	public void visitInsn(int opcode) {
		switch (opcode) {
		case Opcodes.MONITORENTER:
		case Opcodes.MONITOREXIT:
			mv.visitInsn(Opcodes.POP);
			break;
		default:
			super.visitInsn(opcode);
		}
	}
}
