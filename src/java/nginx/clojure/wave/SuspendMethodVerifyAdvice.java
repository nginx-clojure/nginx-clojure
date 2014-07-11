package nginx.clojure.wave;

import nginx.clojure.asm.Label;
import nginx.clojure.asm.MethodVisitor;
import nginx.clojure.asm.commons.AdviceAdapter;

public class SuspendMethodVerifyAdvice extends AdviceAdapter {

	protected MethodDatabase db;
	protected String owner;
	protected String method;
	private final Label start = new Label();
	private final Label handler = new Label();

	
	public SuspendMethodVerifyAdvice(MethodDatabase db,String owner, MethodVisitor mv, int access,
			String name, String desc) {
		super(ASM4, mv, access, name, desc);
		this.db = db;
		this.owner = owner;
		this.method = name + desc;
	}

	public void visitCode() {
		super.visitCode();
		mv.visitLabel(start);
	};

	
	@Override
	protected void onMethodEnter() {
		mv.visitLdcInsn(owner);
		mv.visitLdcInsn(method);
		mv.visitMethodInsn(INVOKESTATIC, "nginx/clojure/wave/SuspendMethodVerifier", "enter", "(Ljava/lang/String;Ljava/lang/String;)V");
//		if (method.equals("invoke(Ljava/lang/Object;Ljava/lang/reflect/Method;[Ljava/lang/Object;)Ljava/lang/Object;")) {
//			mv.visitVarInsn(ALOAD, 2);
//			mv.visitMethodInsn(INVOKESTATIC, "nginx/clojure/wave/SuspendMethodVerifier", "downProxyInvoke", "(Ljava/lang/reflect/Method;)V");
//		}
	}
	
	private final void doExitCode() {
//		if (method.equals("invoke(Ljava/lang/Object;Ljava/lang/reflect/Method;[Ljava/lang/Object;)Ljava/lang/Object;")) {
//			mv.visitVarInsn(ALOAD, 2);
//			mv.visitMethodInsn(INVOKESTATIC, "nginx/clojure/wave/SuspendMethodVerifier", "upProxyInvoke", "(Ljava/lang/reflect/Method;)V");
//		}
		mv.visitLdcInsn(owner);
		mv.visitLdcInsn(method);
		mv.visitMethodInsn(INVOKESTATIC, "nginx/clojure/wave/SuspendMethodVerifier", "leave", "(Ljava/lang/String;Ljava/lang/String;)V");
	}
	
	@Override
	protected void onMethodExit(int opcode) {
		if (opcode != ATHROW) {
			doExitCode();
		}
	}
	
	@Override
	public void visitMaxs(int maxStack, int maxLocals) {
	    mv.visitTryCatchBlock(start, handler, handler, null);
	    mv.visitLabel(this.handler);
	    doExitCode();
	    mv.visitInsn(ATHROW);
	    mv.visitMaxs(Math.max(maxStack, 4), maxLocals);
	}
}
