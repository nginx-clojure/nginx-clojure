/*
 * Copyright (C) 2014 Zhang,Yuexiang (xfeep)
 * All rights reserved.
 */
package nginx.clojure;

import java.io.Serializable;

import nginx.clojure.wave.MethodDatabase;


public final class SuspendableConstructorUtilStack implements Serializable {

    private static final long serialVersionUID = 1396934991052L;
    
    private static final ThreadLocal<SuspendableConstructorUtilStack>  cstacks = new ThreadLocal<SuspendableConstructorUtilStack>();
    
    private static  MethodDatabase db;
    
    private long[] dataLong;
    private Object[] dataObject;
    private int[] methodSlotNumbers;
    
    private int sp = -1;
    private int top = 0;
    private int refs = 0;
    
    SuspendableConstructorUtilStack(int stackSize) {
        if(stackSize <= 0) {
        	stackSize = 8;
        }
        this.dataLong = new long[stackSize];
        this.dataObject = new Object[stackSize];
        this.methodSlotNumbers = new int[2];
    }
    
    public static SuspendableConstructorUtilStack getStack() {
    	SuspendableConstructorUtilStack cs = cstacks.get();
    	if (cs == null) {
    		cstacks.set(cs = new SuspendableConstructorUtilStack(3));
    	}
    	return cs;
    }
    
    public static void setStack(SuspendableConstructorUtilStack cs) {
    	cstacks.set(cs);
    }
    
    public final boolean empty() {
    	return sp == -1;
    }
        
    public final void incRefsAndReserveSpace(int numSlots) {
        refs++;
        int dataTOS = top + numSlots;
        
        sp = top;
        top = dataTOS;
        
        if (db != null && db.isDebug()) {
          db.debug("th#%d: reserveSpace   numSlots=%d, top=%d,  nr(tos)=%d, %s", Thread.currentThread().getId(),numSlots, sp, Thread.currentThread().getStackTrace()[2]);
        }
        
        if(dataTOS > dataObject.length) {
            growDataStack(dataTOS);
        }
        
        if (refs > methodSlotNumbers.length) {
        	methodSlotNumbers = Util.copyOf(methodSlotNumbers, methodSlotNumbers.length + 2);
        }
        
        methodSlotNumbers[refs -1] = numSlots;
    }
    
	public static void push(int value, SuspendableConstructorUtilStack s, int idx) {
		s.dataLong[s.sp + idx] = value;
	}

	public static void push(float value, SuspendableConstructorUtilStack s, int idx) {
		s.dataLong[s.sp + idx] = Float.floatToRawIntBits(value);
	}

	public static void push(long value, SuspendableConstructorUtilStack s, int idx) {
		s.dataLong[s.sp + idx] = value;
	}

	public static void push(double value, SuspendableConstructorUtilStack s, int idx) {
		s.dataLong[s.sp + idx] = Double.doubleToRawLongBits(value);
	}

	public static void push(Object value, SuspendableConstructorUtilStack s, int idx) {
		s.dataObject[s.sp + idx] = value;
	}

	public final int getInt(int idx) {
		return (int) dataLong[sp + idx];
	}

	public final float getFloat(int idx) {
		return Float.intBitsToFloat((int) dataLong[sp + idx]);
	}

	public final long getLong(int idx) {
		return dataLong[sp + idx];
	}

	public final double getDouble(int idx) {
		return Double.longBitsToDouble(dataLong[sp + idx]);
	}

	public final Object getObject(int idx) {
		return dataObject[sp + idx];
	}
    
    public final void release(int c) {
    	if (--refs == 0) {
    		Stack.fillNull(dataObject, 0, top);
    		sp = -1;
    		top = 0;
    	} else {
    		sp -= methodSlotNumbers[refs - 1];
    		top -= c;
    		Stack.fillNull(dataObject, top, c);
    	}
    }

    private void growDataStack(int required) {
        int newSize = dataObject.length;
		do {
			newSize *= 2;
		} while (newSize < required);
        
        dataLong = Util.copyOf(dataLong, newSize);
        dataObject = Util.copyOf(dataObject, newSize);
    }
    
    public static void setDb(MethodDatabase db) {
		SuspendableConstructorUtilStack.db = db;
	}
}
