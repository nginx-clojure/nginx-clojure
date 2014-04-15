package nginx.clojure.wave;

import java.io.PrintWriter;

import nginx.clojure.asm.util.Printer;

public class TracableMethodNode extends InstrumentMethodNode {

	protected PrintWriter printWriter;
	protected Printer printer;
	
	public TracableMethodNode() {
	}

	public TracableMethodNode(int api) {
		super(api);
	}

	public TracableMethodNode(MethodDatabase db, int access, String name, String desc,
			String signature, String[] exceptions, Printer printer, PrintWriter printWriter) {
		super(db, access, name, desc, signature, exceptions);
		this.printWriter = printWriter;
		this.printer = printer;
	}
	
	@Override
	public void visitEnd() {
		super.visitEnd();
		printer.print(printWriter);
		printWriter.flush();
	}

}
