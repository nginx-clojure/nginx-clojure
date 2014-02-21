/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.logger;

public interface LoggerService {

	public abstract void debug(Object message);

	public abstract void debug(Object message, Throwable t);

	public abstract void debug(String format, Object... objects);

	public abstract void error(Object message);

	public abstract void error(Object message, Throwable t);

	public abstract void error(String format, Object... objects);

	public abstract void fatal(Object message);

	public abstract void fatal(Object message, Throwable t);

	public abstract void fatal(String format, Object... objects);

	public abstract void info(Object message);

	public abstract void info(Object message, Throwable t);

	public abstract void info(String format, Object... objects);

	public abstract boolean isDebugEnabled();

	public abstract boolean isErrorEnabled();

	public abstract boolean isFatalEnabled();

	public abstract boolean isInfoEnabled();

	public abstract boolean isTraceEnabled();

	public abstract boolean isWarnEnabled();

	public abstract void trace(Object message);

	public abstract void trace(Object message, Throwable t);
	
	public abstract void trace(String format, Object... objects);

	public abstract void warn(Object message);

	public abstract void warn(Object message, Throwable t);
	
	public abstract void warn(String format, Object... objects);

}