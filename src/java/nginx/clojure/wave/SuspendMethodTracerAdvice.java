package nginx.clojure.wave;

import nginx.clojure.asm.Label;
import nginx.clojure.asm.MethodVisitor;
import nginx.clojure.asm.commons.AdviceAdapter;

public class SuspendMethodTracerAdvice extends AdviceAdapter {

	protected MethodDatabase db;
	protected String owner;
	protected String method;
	private final Label start = new Label();
	private final Label handler = new Label();

	
	public SuspendMethodTracerAdvice(MethodDatabase db,String owner, MethodVisitor mv, int access,
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
	public void visitMethodInsn(int opcode, String owner, String name,
			String desc) {
		if (owner.equals("nginx/clojure/Coroutine") && name.equals("yield")) {
			super.visitMethodInsn(opcode, owner, "yieldp", desc);
		}else {
			super.visitMethodInsn(opcode, owner, name, desc);
		}
		
	}
	
	@Override
	protected void onMethodEnter() {
		mv.visitLdcInsn(owner);
		mv.visitLdcInsn(method);
		mv.visitMethodInsn(INVOKESTATIC, "nginx/clojure/wave/SuspendMethodTracer", "enter", "(Ljava/lang/String;Ljava/lang/String;)V");
	}
	
	private final void doExitCode() {
		mv.visitLdcInsn(owner);
		mv.visitLdcInsn(method);
		mv.visitMethodInsn(INVOKESTATIC, "nginx/clojure/wave/SuspendMethodTracer", "leave", "(Ljava/lang/String;Ljava/lang/String;)V");
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
