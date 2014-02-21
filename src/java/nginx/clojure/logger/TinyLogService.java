package nginx.clojure.logger;

import java.io.PrintStream;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * This tiny log service is mainly for debug use
 */
public class TinyLogService implements LoggerService {
	
	public static final String NGINX_CLOJURE_LOG_LEVEL = "NGINX_CLOJURE_LOG_LEVEL";

	public enum MsgType{ trace, debug,info, warn, error, fatal };
	
	protected MsgType level = MsgType.info;
	
	protected PrintStream out;
	protected PrintStream err;
	
	
	public static MsgType getSystemPropertyOrDefaultLevel() {
		String l = System.getProperty(NGINX_CLOJURE_LOG_LEVEL);
		if (l != null){
			try{
				return MsgType.valueOf(l);
			}catch (Exception e) {
				e.printStackTrace();
			}	
		}
		return MsgType.info;
	}
	
	public TinyLogService(){
		String l = System.getProperty(NGINX_CLOJURE_LOG_LEVEL);
		if (l != null){
			try{
				level = MsgType.valueOf(l);
			}catch (Exception e) {
				e.printStackTrace();
			}	
		}
		out = System.out;
		err = System.err;
	}
	
	public TinyLogService(MsgType level, PrintStream out, PrintStream err) {
		this.level = level;
		this.out = out;
		this.err = err;
	}

	public void debug(Object message) {
		message(message, MsgType.debug);
	}


	public void debug(Object message, Throwable t) {
		if (isDebugEnabled()){
			t.printStackTrace(message(message, MsgType.debug));
		}
	}
	
	public  void debug(String format, Object ... objects){
		message(format, MsgType.debug, objects);
	}


	public void error(Object message) {
		message(message, MsgType.error); 
	}


	public void error(Object message, Throwable t) {
		t.printStackTrace(message(message, MsgType.error));
	}
	
	public  void error(String format, Object ... objects){
		message(format, MsgType.error, objects);
	}



	public void fatal(Object message) {
		message(message, MsgType.fatal);
	}


	public void fatal(Object message, Throwable t) {
		t.printStackTrace(message(message, MsgType.fatal));
	}
	
	public  void fatal(String format, Object ... objects){
		message(format, MsgType.fatal, objects);
	}
	
	public PrintStream message(String format, MsgType type, Object ...objects){
		if (type.compareTo(level) < 0){
			return System.out;
		}
		return message(MessageFormat.format(format, objects), type);
	}

	public PrintStream message(Object message, MsgType type){
		if (type.compareTo(level) < 0){
			return System.out;
		}
		SimpleDateFormat sf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		StringBuffer s = new StringBuffer();
		StackTraceElement se = null;
		boolean meetCurrentMethod = false;
		for (StackTraceElement si : Thread.currentThread().getStackTrace()){
			if (si.getClassName().equals(TinyLogService.class.getName())){
				meetCurrentMethod = true;
				continue;
			}
			if (meetCurrentMethod){
				se = si;
				break;
			}
		}
		s.append(sf.format(new Date())).append("[").append(type).append("]:")
		.append("[").append(se.getClassName()).append(".").append(se.getMethodName()).append("]:")
		.append(message);
		PrintStream out = System.out;
		if (type != MsgType.debug && type != MsgType.info){
			out = System.err;
		}
		out.println(s);
		return out;
	}
	

	public void info(Object message) {
		message(message, MsgType.info);
	}


	public void info(Object message, Throwable t) {
		message(message, MsgType.info);
		t.printStackTrace(System.out);
	}

	public  void info(String format, Object ... objects){
		message(format, MsgType.info, objects);
	}
	
	

	public boolean isDebugEnabled() {
		return level.compareTo(MsgType.debug) <= 0;
	}


	public boolean isErrorEnabled() {
		return level.compareTo(MsgType.error) <= 0;
	}


	public boolean isFatalEnabled() {
		return level.compareTo(MsgType.fatal) <= 0;
	}


	public boolean isInfoEnabled() {
		return level.compareTo(MsgType.info) <= 0;
	}


	public boolean isTraceEnabled() {
		return level.compareTo(MsgType.trace) <= 0;
	}


	public boolean isWarnEnabled() {
		return level.compareTo(MsgType.warn) <= 0;
	}


	public void trace(Object message) {
		message(message, MsgType.trace);
	}


	public void trace(Object message, Throwable t) {
		t.printStackTrace(message(message, MsgType.trace));
	}
	
	public  void trace(String format, Object ... objects){
		message(format, MsgType.trace, objects);
	}


	public void warn(Object message) {
		message(message, MsgType.warn);
	}


	public void warn(Object message, Throwable t) {
		t.printStackTrace(message(message, MsgType.warn));
	}

	public  void warn(String format, Object ... objects){
		message(format, MsgType.warn, objects);
	}


	public MsgType getLevel() {
		return level;
	}


	public void setLevel(MsgType level) {
		this.level = level;
	}


	public PrintStream getOut() {
		return out;
	}


	public void setOut(PrintStream out) {
		this.out = out;
	}


	public PrintStream getErr() {
		return err;
	}


	public void setErr(PrintStream err) {
		this.err = err;
	}
	
	

}