package nginx.clojure.wave;

import nginx.clojure.asm.MethodVisitor;
import nginx.clojure.asm.commons.AdviceAdapter;

public class SuspendMethodTracerAdvice extends AdviceAdapter {

	protected String owner;
	protected String method;
	
	public SuspendMethodTracerAdvice(String owner, MethodVisitor mv, int access,
			String name, String desc) {
		super(ASM4, mv, access, name, desc);
		this.owner = owner;
		this.method = name + desc;
	}

	@Override
	protected void onMethodEnter() {
		mv.visitLdcInsn(owner);
		mv.visitLdcInsn(method);
		mv.visitMethodInsn(INVOKESTATIC, "nginx/clojure/wave/SuspendMethodTracer", "enter", "(Ljava/lang/String;Ljava/lang/String;)V");
	}
	
	@Override
	protected void onMethodExit(int opcode) {
		mv.visitMethodInsn(INVOKESTATIC, "nginx/clojure/wave/SuspendMethodTracer", "leave", "()V");
	}
	
	@Override
	public void visitMaxs(int maxStack, int maxLocals) {
		super.visitMaxs(maxStack, maxLocals);
	}
}
