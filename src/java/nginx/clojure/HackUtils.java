/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure;

import java.lang.ref.Reference;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.AccessControlContext;

import sun.misc.Unsafe;

public class HackUtils {

    private static final long threadLocalsOffset;
    private static final long inheritableThreadLocalsOffset;
    private static final long contextClassLoaderOffset;
    private static final long inheritedAccessControlContextOffset;
    private static final Method createInheritedMap;
    private static final Class threadLocalMapClass;
    
	private static final long  threadLocalMapTableFieldOffset;// = UNSAFE.objectFieldOffset(threadLocalMapTableField);
	private static final long  threadLocalMapSizeFieldOffset;// = UNSAFE.objectFieldOffset(threadLocalMapSizeField);
	private static final long  threadLocalMapThresholdFieldOffset;// = UNSAFE.objectFieldOffset(threadLocalMapThresholdField);
    
    private static final Class threadLocalMapEntryClass;
	private static final long  threadLocalMapEntryValueFieldOffset;
	private static final long  threadLocalMapEntryReferentFieldOffset;
	private static final long threadLocalMapEntryQueueFieldOffset;
	
	
	/*use it carefully!!*/
	public static Unsafe UNSAFE = null;
	

	public HackUtils() {
	}
	
	public static void initUnsafe() {
		if (UNSAFE != null) {
			return;
		}
	    try{
	        Field field = Unsafe.class.getDeclaredField("theUnsafe");
	        field.setAccessible(true);
	        UNSAFE = (Unsafe)field.get(null);
	    }
	    catch (Exception e){
	        throw new RuntimeException(e);
	    }
	}
	
	


    static {
    	initUnsafe();
        try {
        	
            threadLocalsOffset = UNSAFE.objectFieldOffset(Thread.class.getDeclaredField("threadLocals"));
            inheritableThreadLocalsOffset = UNSAFE.objectFieldOffset(Thread.class.getDeclaredField("inheritableThreadLocals"));
            contextClassLoaderOffset = UNSAFE.objectFieldOffset(Thread.class.getDeclaredField("contextClassLoader"));

            long _inheritedAccessControlContextOffset = -1;
            try {
                _inheritedAccessControlContextOffset = UNSAFE.objectFieldOffset(Thread.class.getDeclaredField("inheritedAccessControlContext"));
            } catch (NoSuchFieldException e) {
            }
            inheritedAccessControlContextOffset = _inheritedAccessControlContextOffset;

            threadLocalMapClass = Class.forName("java.lang.ThreadLocal$ThreadLocalMap");
            createInheritedMap = ThreadLocal.class.getDeclaredMethod("createInheritedMap", threadLocalMapClass);
            createInheritedMap.setAccessible(true);
            
            threadLocalMapTableFieldOffset = UNSAFE.objectFieldOffset(threadLocalMapClass.getDeclaredField("table"));
        	threadLocalMapSizeFieldOffset = UNSAFE.objectFieldOffset(threadLocalMapClass.getDeclaredField("size"));
        	threadLocalMapThresholdFieldOffset = UNSAFE.objectFieldOffset(threadLocalMapClass.getDeclaredField("threshold"));
        	
            threadLocalMapEntryClass = Class.forName("java.lang.ThreadLocal$ThreadLocalMap$Entry");
            threadLocalMapEntryValueFieldOffset = UNSAFE.objectFieldOffset(threadLocalMapEntryClass.getDeclaredField("value"));
            threadLocalMapEntryReferentFieldOffset = UNSAFE.objectFieldOffset(Reference.class.getDeclaredField("referent"));
            threadLocalMapEntryQueueFieldOffset = UNSAFE.objectFieldOffset(Reference.class.getDeclaredField("queue"));
            
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    public static Object getThreadLocals(Thread thread) {
        return UNSAFE.getObject(thread, threadLocalsOffset);
    }

    public static void setThreadLocals(Thread thread, Object threadLocals) {
        UNSAFE.putObject(thread, threadLocalsOffset, threadLocals);
    }

    public static Object getInheritableThreadLocals(Thread thread) {
        return UNSAFE.getObject(thread, inheritableThreadLocalsOffset);
    }

    public static void setInheritablehreadLocals(Thread thread, Object inheritableThreadLocals) {
        UNSAFE.putObject(thread, inheritableThreadLocalsOffset, inheritableThreadLocals);
    }

    public static Object createInheritedMap(Object inheritableThreadLocals) {
    	return cloneThreadLocalMap(inheritableThreadLocals);
    }

    public static Object cloneThreadLocalMap(Object o) {
        try {
            Object clone = UNSAFE.allocateInstance(threadLocalMapClass);
            Object origTable =  UNSAFE.getObject(o, threadLocalMapTableFieldOffset);
            
            int len = Array.getLength(origTable);
            Object tableClone = Array.newInstance(threadLocalMapEntryClass, len);
            for (int i = 0; i < len; i++) {
                Object entry = Array.get(origTable, i);
                if (entry != null)
                    Array.set(tableClone, i, cloneThreadLocalMapEntry(entry));
            }
            
            UNSAFE.putObject(clone, threadLocalMapTableFieldOffset, tableClone);
            UNSAFE.putInt(clone, threadLocalMapSizeFieldOffset, UNSAFE.getInt(o, threadLocalMapSizeFieldOffset));
            UNSAFE.putInt(clone, threadLocalMapThresholdFieldOffset, UNSAFE.getInt(o, threadLocalMapThresholdFieldOffset));
            
            return clone;
        } catch (Exception ex) {
            throw new AssertionError("can not cloneThreadLocalMap", ex);
        }
    }
    

    private static Object cloneThreadLocalMapEntry(Object entry) {
        try {
        	Object clone = UNSAFE.allocateInstance(threadLocalMapEntryClass);
        	UNSAFE.putObject(clone, threadLocalMapEntryReferentFieldOffset, UNSAFE.getObject(entry, threadLocalMapEntryReferentFieldOffset));
        	UNSAFE.putObject(clone, threadLocalMapEntryValueFieldOffset, UNSAFE.getObject(entry, threadLocalMapEntryValueFieldOffset));
        	UNSAFE.putObject(clone, threadLocalMapEntryQueueFieldOffset, UNSAFE.getObject(entry, threadLocalMapEntryQueueFieldOffset));
        	return clone;
        } catch (Exception e) {
            throw new AssertionError("can not cloneThreadLocalMapEntry", e);
        }
    }


    public static ClassLoader getContextClassLoader(Thread thread) {
        return (ClassLoader) UNSAFE.getObject(thread, contextClassLoaderOffset);
    }

    public static void setContextClassLoader(Thread thread, ClassLoader classLoader) {
        UNSAFE.putObject(thread, contextClassLoaderOffset, classLoader);
    }

    public static AccessControlContext getInheritedAccessControlContext(Thread thread) {
        if (inheritedAccessControlContextOffset < 0)
            return null;
        return (AccessControlContext) UNSAFE.getObject(thread, inheritedAccessControlContextOffset);
    }

    public static void setInheritedAccessControlContext(Thread thread, AccessControlContext accessControlContext) {
        if (inheritedAccessControlContextOffset >= 0)
            UNSAFE.putObject(thread, inheritedAccessControlContextOffset, accessControlContext);
    }

}
