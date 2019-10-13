package nginx.clojure.wave;

import java.io.PrintWriter;

import nginx.clojure.asm.MethodVisitor;
import nginx.clojure.asm.Opcodes;
import nginx.clojure.asm.util.Printer;

public class TracableMethodVisitor extends MethodVisitor {

	protected PrintWriter printWriter;
	protected Printer printer;
	protected String title;
	

	
	public TracableMethodVisitor(String title, MethodVisitor mv, int access, String name, String desc,
			String signature, String[] exceptions, Printer printer, PrintWriter printWriter) {
		super(Opcodes.ASM7, mv);
		this.title = title;
		this.printWriter = printWriter;
		this.printer = printer;
		this.title = title;
	}

	@Override
	public void visitCode() {
		printWriter.println("*************start of " + title +  "**************");
		super.visitCode();
	}
	
	@Override
	public void visitEnd() {
		super.visitEnd();
		printer.print(printWriter);
		printWriter.println("*************end of " + title + "**************");
		printWriter.flush();
	}
}
