/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure;

import static nginx.clojure.MiniConstants.STRING_CHAR_ARRAY_OFFSET;
import static nginx.clojure.MiniConstants.STRING_OFFSET_OFFSET;

import java.io.FileDescriptor;
import java.io.RandomAccessFile;
import java.lang.ref.Reference;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.security.AccessControlContext;

import sun.misc.Unsafe;
import sun.nio.cs.ThreadLocalCoders;

public class HackUtils {

    private static final long threadLocalsOffset;
    private static final long inheritableThreadLocalsOffset;
    private static final long contextClassLoaderOffset;
    private static final long inheritedAccessControlContextOffset;
    private static final Method createInheritedMap;
    @SuppressWarnings("rawtypes")
	private static final Class threadLocalMapClass;
    
	private static final long  threadLocalMapTableFieldOffset;// = UNSAFE.objectFieldOffset(threadLocalMapTableField);
	private static final long  threadLocalMapSizeFieldOffset;// = UNSAFE.objectFieldOffset(threadLocalMapSizeField);
	private static final long  threadLocalMapThresholdFieldOffset;// = UNSAFE.objectFieldOffset(threadLocalMapThresholdField);
    
    @SuppressWarnings("rawtypes")
	private static final Class threadLocalMapEntryClass;
	private static final long  threadLocalMapEntryValueFieldOffset;
	private static final long  threadLocalMapEntryReferentFieldOffset;
	private static final long threadLocalMapEntryQueueFieldOffset;
	
	@SuppressWarnings("rawtypes")
	private static final Class randomAccessFileClass;
	private static final long randomAccessFileFdFieldOffset;
	@SuppressWarnings("rawtypes")
	private static final Class fileDescriptorClass;
	private static final long  fileDescriptorClassFdFieldOffset;
	
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
	        STRING_CHAR_ARRAY_OFFSET = UNSAFE.objectFieldOffset(String.class.getDeclaredField("value"));
	    }catch (Exception e){
	        throw new RuntimeException(e);
	    }
	    
        try {
			STRING_OFFSET_OFFSET = UNSAFE.objectFieldOffset(String.class.getDeclaredField("offset"));
		}  catch (NoSuchFieldException e) {
			STRING_OFFSET_OFFSET = -1;
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
            
            randomAccessFileClass = Class.forName("java.io.RandomAccessFile");
            randomAccessFileFdFieldOffset = UNSAFE.objectFieldOffset(randomAccessFileClass.getDeclaredField("fd"));
            fileDescriptorClass = Class.forName("java.io.FileDescriptor");
            fileDescriptorClassFdFieldOffset  = UNSAFE.objectFieldOffset(fileDescriptorClass.getDeclaredField("fd"));
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }
    
    public static RandomAccessFile buildShadowRandomAccessFile(int fd) {
    	RandomAccessFile rf = null;
    	try {
			rf = (RandomAccessFile) UNSAFE.allocateInstance(randomAccessFileClass);
		} catch (InstantiationException e) {
			throw new RuntimeException(e);
		}
    	FileDescriptor fileDescriptor = new FileDescriptor();
    	UNSAFE.putInt(fileDescriptor, fileDescriptorClassFdFieldOffset, fd);
    	UNSAFE.putObject(rf, randomAccessFileFdFieldOffset, fileDescriptor);
    	return rf;
    	
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
            throw new AssertionError(ex);
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
            throw new AssertionError(e);
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
    
    public static int putBuffer(ByteBuffer dst, ByteBuffer src) {
    	int c = 0;
    	while (dst.hasRemaining() && src.hasRemaining()) {
    		dst.put(src.get());
    		c ++;
    	}
    	return c;
    }
    
    
    public static ByteBuffer encode(String s, Charset cs, ByteBuffer bb)  {
    	//for safe
    	if (s == null) {
    		throw new NullPointerException("string should not be null");
    	}
		CharsetEncoder ce =  ThreadLocalCoders.encoderFor(cs)
				.onMalformedInput(CodingErrorAction.REPLACE)
				.onUnmappableCharacter(CodingErrorAction.REPLACE);
		CharBuffer cb = CharBuffer.wrap(s);
		ce.reset();
		CoderResult rt = ce.encode(cb, bb, true);
		if (rt == CoderResult.OVERFLOW) {
			bb.flip();
			ByteBuffer lbb = ByteBuffer.allocate((int)(s.length() * (double)ce.maxBytesPerChar()));
			lbb.put(bb);
			bb = lbb;
			rt = ce.encode(cb, bb, true);
		}
		if (rt != CoderResult.UNDERFLOW) {
			throw new RuntimeException(rt.toString());
		}
		rt = ce.flush(bb);
		if (rt != CoderResult.UNDERFLOW) {
			throw new RuntimeException(rt.toString());
		}
		bb.flip();
		if (bb.remaining() < bb.capacity()) {
			bb.array()[bb.arrayOffset()+bb.remaining()] = 0; // for char* c language is ended with '\0'
		}
		return bb;
    }
    
	public static ByteBuffer encodeLowcase(String s, Charset cs, ByteBuffer bb) {
		if (bb.isDirect()) {
			return encode(s.toLowerCase(), cs, bb);
		}
		encode(s, cs, bb);
		int len = bb.remaining();
		byte[] array = bb.array();
		for (int i = 0; i < len; i++) {
			byte b = array[i];
			array[i] = b >= 'A' && b <= 'Z' ? (byte) (b | 0x20) : b;
		}
		return bb;
	}
    
    public static String decode(ByteBuffer bb, Charset cs, CharBuffer cb)  {
    	CharsetDecoder de = ThreadLocalCoders.decoderFor(cs)
    			.onMalformedInput(CodingErrorAction.REPLACE)
				.onUnmappableCharacter(CodingErrorAction.REPLACE);
    	de.reset();
    	int len = bb.remaining();
    	CoderResult rt = de.decode(bb, cb, true);
    	if (rt == CoderResult.OVERFLOW) {
    		cb.flip();
    		CharBuffer lcb = CharBuffer.allocate((int)(len * (double)de.maxCharsPerByte()));
    		lcb.put(cb);
    		cb = lcb;
    		rt = de.decode(bb, cb, true);
    	}
    	
		if (rt != CoderResult.UNDERFLOW) {
			throw new RuntimeException(rt.toString());
		}
		rt = de.flush(cb);
		if (rt != CoderResult.UNDERFLOW) {
			throw new RuntimeException(rt.toString());
		}
		cb.flip();
		return cb.toString();
    }
    
    public static CharBuffer decodeValid(ByteBuffer bb, Charset cs, CharBuffer cb)  {
    	CharsetDecoder de = ThreadLocalCoders.decoderFor(cs)
    			.onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT);
    	de.reset();
    	int len = bb.remaining();
    	CoderResult rt = de.decode(bb, cb, true);
    	if (rt == CoderResult.OVERFLOW) {
    		cb.flip();
    		CharBuffer lcb = CharBuffer.allocate((int)(len * (double)de.maxCharsPerByte()));
    		lcb.put(cb);
    		cb = lcb;
    		rt = de.decode(bb, cb, true);
    	}
    	
		rt = de.flush(cb);
		cb.flip();
		return cb;
    }
    
    public static String truncateToDotAppendString(String s, int max) {
    	StringBuilder sb = new StringBuilder();
		if (s == null) {
			sb.append("<NULL>");
		}else if (s.length() == 0) {
			sb.append("<EMPTY>");
		}else {
			int alen = Math.min(max, s.length());
			sb.append(s.substring(0, alen));
			if (alen < s.length()) {
				sb.append("...");
			}
		}
		return sb.toString();
    }
    
}
