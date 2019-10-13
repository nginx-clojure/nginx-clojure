package nginx.clojure.wave;

import nginx.clojure.asm.Label;
import nginx.clojure.asm.MethodVisitor;
import nginx.clojure.asm.commons.AdviceAdapter;

public class SuspendMethodTracerAdvice extends AdviceAdapter {

	protected MethodDatabase db;
	protected String owner;
	protected String method;
	private  Label start = new Label();
	private final Label handler = new Label();

	
	public SuspendMethodTracerAdvice(MethodDatabase db,String owner, MethodVisitor mv, int access,
			String name, String desc) {
		super(ASM7, mv, access, name, desc);
		this.db = db;
		this.owner = owner;
		this.method = name + desc;
	}

	public void visitCode() {
		super.visitCode();
		mv.visitLabel(start);
	};
	
	@Override
	public void visitMethodInsn(int opcode, String owner, String name,
			String desc, boolean isInterface) {
		
		if (owner == null) {
			super.visitMethodInsn(opcode, owner, name, desc, isInterface);
			return;
		}
		
		if (owner.equals("nginx/clojure/Coroutine") && name.equals("yield")) {
			super.visitMethodInsn(opcode, owner, "_yieldp", desc, false);
		}else if (owner.equals("nginx/clojure/Coroutine") && name.equals("resume")) {
			super.visitMethodInsn(opcode, owner, "_resumep", desc, false);
		}else {
			super.visitMethodInsn(opcode, owner, name, desc, isInterface);
		}
		
		if (method != null && method.startsWith("<init>") && opcode == INVOKESPECIAL) {
			start = new Label();
			mv.visitLabel(start);
		}
		
	}
	
	@Override
	protected void onMethodEnter() {
		if (owner != null) {
			mv.visitLdcInsn(owner);	
		} else {
			mv.visitInsn(ACONST_NULL);
		}
		
		mv.visitLdcInsn(method);
		mv.visitMethodInsn(INVOKESTATIC, "nginx/clojure/wave/SuspendMethodTracer", "enter", "(Ljava/lang/String;Ljava/lang/String;)V", false);
		if (method.equals("invoke(Ljava/lang/Object;Ljava/lang/reflect/Method;[Ljava/lang/Object;)Ljava/lang/Object;")) {
			mv.visitVarInsn(ALOAD, 2);
			mv.visitMethodInsn(INVOKESTATIC, "nginx/clojure/wave/SuspendMethodTracer", "downProxyInvoke", "(Ljava/lang/reflect/Method;)V", false);
		}
	}
	
	private final void doExitCode() {
		if (method.equals("invoke(Ljava/lang/Object;Ljava/lang/reflect/Method;[Ljava/lang/Object;)Ljava/lang/Object;")) {
			mv.visitVarInsn(ALOAD, 2);
			mv.visitMethodInsn(INVOKESTATIC, "nginx/clojure/wave/SuspendMethodTracer", "upProxyInvoke", "(Ljava/lang/reflect/Method;)V", false);
		}
		
		if (owner != null) {
			mv.visitLdcInsn(owner);
		} else {
			mv.visitInsn(ACONST_NULL);
		}
		mv.visitLdcInsn(method);
		mv.visitMethodInsn(INVOKESTATIC, "nginx/clojure/wave/SuspendMethodTracer", "leave", "(Ljava/lang/String;Ljava/lang/String;)V", false);
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
	    mv.visitMaxs(0, 0);
	}
}
