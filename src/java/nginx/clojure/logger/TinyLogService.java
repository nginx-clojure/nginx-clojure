package nginx.clojure.logger;

import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import nginx.clojure.NginxClojureRT;

/**
 * This tiny log service is mainly for debug use
 */
public class TinyLogService implements LoggerService {
	
	public static final String NGINX_CLOJURE_LOG_LEVEL = "nginx.clojure.logger.level";

	public enum MsgType{ trace, debug,info, warn, error, fatal };
	
	public static final Set<String> LOG_METHODS = new HashSet<String>();
	
	static {
		for (MsgType t : MsgType.values()) {
			LOG_METHODS.add(t.name());
		}
	}
	
	protected MsgType level = MsgType.info;
	
	protected PrintStream out;
	protected PrintStream err;
	protected boolean showMethod = false;
	
	
	public static TinyLogService createDefaultTinyLogService() {
		return new TinyLogService(TinyLogService.getSystemPropertyOrDefaultLevel(), System.err, System.err);
	}
	
	public static MsgType getSystemPropertyOrDefaultLevel(String p, MsgType t) {
		String l = System.getProperty(p);
		if (l != null){
			try{
				return MsgType.valueOf(l);
			}catch (Exception e) {
				e.printStackTrace();
			}	
		}
		return t == null ? MsgType.info : t;
	}
	
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
		if (message instanceof Throwable) {
			((Throwable)message).printStackTrace(message(((Throwable)message).getMessage(), MsgType.debug));
		}else {
			message(message, MsgType.debug); 
		}
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
		if (message instanceof Throwable) {
			((Throwable)message).printStackTrace(message(((Throwable)message).getMessage(), MsgType.error));
		}else {
			message(message, MsgType.error); 
		}
	}


	public void error(Object message, Throwable t) {
		t.printStackTrace(message(message, MsgType.error));
	}
	
	public  void error(String format, Object ... objects){
		message(format, MsgType.error, objects);
	}



	public void fatal(Object message) {
		if (message instanceof Throwable) {
			((Throwable)message).printStackTrace(message(((Throwable)message).getMessage(), MsgType.fatal));
		}else {
			message(message, MsgType.fatal); 
		}
	}


	public void fatal(Object message, Throwable t) {
		t.printStackTrace(message(message, MsgType.fatal));
	}
	
	public  void fatal(String format, Object ... objects){
		message(format, MsgType.fatal, objects);
	}
	
	public PrintStream message(String format, MsgType type, Object ...objects){
		if (type.compareTo(level) < 0){
			return out;
		}
		return message(String.format(format, objects), type);
	}

	public PrintStream message(Object message, MsgType type){
		if (type.compareTo(level) < 0){
			return this.out;
		}
		SimpleDateFormat sf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		StringBuffer s = new StringBuffer();
		s.append(sf.format(new Date())).append("[").append(type).append("]");
		s.append("[").append(NginxClojureRT.processId).append("]");
		if (type.compareTo(MsgType.debug) >= 0) {
			s.append("[").append(Thread.currentThread().getName()).append("]");
		}
		if (showMethod) {
			StackTraceElement se = null;
			boolean meetCurrentMethod = false;
			for (StackTraceElement si : Thread.currentThread().getStackTrace()){
				if (si.getClassName().equals(TinyLogService.class.getName()) || LOG_METHODS.contains(si.getMethodName())){
					meetCurrentMethod = true;
					continue;
				}
				if (meetCurrentMethod){
					se = si;
					break;
				}
			}
			s.append("[").append(se.getClassName()).append(".").append(se.getMethodName()).append("]:");
		}
		s.append(message);
		PrintStream out = this.out;
		if (type != MsgType.debug && type != MsgType.info){
			out = this.err;
		}
		out.println(s);
		return out;
	}
	

	public void info(Object message) {
		if (message instanceof Throwable) {
			((Throwable)message).printStackTrace(message(((Throwable)message).getMessage(), MsgType.info));
		}else {
			message(message, MsgType.info); 
		}
	}


	public void info(Object message, Throwable t) {
		message(message, MsgType.info);
		t.printStackTrace(out);
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
		if (message instanceof Throwable) {
			((Throwable)message).printStackTrace(message(((Throwable)message).getMessage(), MsgType.warn));
		}else {
			message(message, MsgType.warn); 
		}
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
	
	public void setShowMethod(boolean showMethod) {
		this.showMethod = showMethod;
	}
	
	public boolean isShowMethod() {
		return showMethod;
	}

}