/*
 * Copyright (c) 2008, Matthias Mann
 * Copyright (C) 2014, Zhang,Yuexiang (xfeep)
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 *     * Redistributions of source code must retain the above copyright notice,
 *       this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of Matthias Mann nor the names of its
 *       contributors may be used to endorse or promote products derived from
 *       this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package nginx.clojure;

import java.io.Serializable;
import java.lang.reflect.Proxy;

import nginx.clojure.wave.MethodDatabase;
import nginx.clojure.wave.SuspendMethodVerifier.VerifyInfo;
import nginx.clojure.wave.SuspendMethodVerifier.VerifyMethodInfo;
import nginx.clojure.wave.SuspendMethodVerifier.VerifyVarInfo;

/**
 * Internal Class - DO NOT USE !
 * 
 * Needs to be public so that instrumented code can access it.
 * ANY CHANGE IN THIS CLASS NEEDS TO BE SYNCHRONIZED WITH {@link de.matthiasmann.continuations.instrument.InstrumentMethod}
 * 
 * @author Matthias Mann
 */
public final class Stack implements Serializable {

    private static final long serialVersionUID = 12786283751253L;
    
    private static final ThreadLocal<Stack> tls = new ThreadLocal<Stack>();
    
    private static volatile long vidCounter = 0;
    
    /** sadly this need to be here */
    public static SuspendExecution exception_instance_not_for_user_code = SuspendExecution.instance;
    
    private static final Object[] nullValue1kArray = new Object[1024];
    
    final Coroutine co;
    
    private static  MethodDatabase db;
    
    private int methodTOS = -1;
    private int[] method;
    
    private long[] dataLong;
    private Object[] dataObject;
    
    private VerifyInfo verifyInfo;
    
    transient int curMethodSP;
    
    
    Stack(Coroutine co, int stackSize) {
        if(stackSize <= 0) {
            throw new IllegalArgumentException("stackSize");
        }
        this.co = co;
        this.method = new int[8];
        this.dataLong = new long[stackSize];
        this.dataObject = new Object[stackSize];
        
        if (db == null) {
        	throw new IllegalArgumentException("corutine model was configured wrongly!");
        }
        
        if (db.isVerify()) {
        	verifyInfo = new VerifyInfo();
        	verifyInfo.vid = vidCounter++;
        }
    }
    
    public static VerifyInfo getVerifyInfo() {
    	Stack stack = tls.get();
    	if (stack == null) {
    		return null;
    	}
    	return stack.verifyInfo;
    }
    
    public static Stack getStack() {
        return tls.get();
    }
    
    /**
     * For inner usage, Don't call it.
     */
    public static void setStack(Stack s) {
        tls.set(s);
    }
    
    
    /**
     * Called before a method is called.
     * @param entry the entry point in the method for resume
     * @param numSlots the number of required stack slots for storing the state
     */
    public final void pushMethodAndReserveSpace(int entry, int numSlots) {
    	
    	
        final int methodIdx = methodTOS;
        
        
        if(method.length - methodIdx < 2) {
            growMethodStack();
        }
        
        int oldDataTos = method[methodIdx+1];
        curMethodSP = method[methodIdx-1];
        int dataTOS = curMethodSP + numSlots;
        
        method[methodIdx] = entry;
        method[methodIdx+1] = dataTOS;
        
        //maybe in the same method the previous suspendable invoke finished
        if (oldDataTos > dataTOS) {
        	for (int i = dataTOS; i < oldDataTos; i++) {
        		dataObject[i] = null;
        	}
        }
        
        if(dataTOS > dataObject.length) {
            growDataStack(dataTOS);
        }
    }
    
    public final void pushMethodAndReserveSpaceV(int entry, int numSlots, String classAndMethod) {
    	int idx = methodTOS >> 1;
    	pushMethodAndReserveSpace(entry, numSlots);
    	if (db.meetTraceTargetClassMethod(classAndMethod)) {
    		db.info("#%d pushMethodAndReserveSpaceV %s, slots=%d tos=%d midx=%d sp=%d", verifyInfo.vid, classAndMethod, numSlots, methodTOS, idx, curMethodSP);
    	}
        if (idx >= verifyInfo.methodIdxInfos.length -1) {
        	VerifyMethodInfo[] nvmis = new VerifyMethodInfo[verifyInfo.methodIdxInfos.length << 1];
            System.arraycopy(verifyInfo.methodIdxInfos, 0, nvmis, 0, verifyInfo.methodIdxInfos.length);
            verifyInfo.methodIdxInfos = nvmis;
        }
        checkClassAndMethod(idx, "pushMethodAndReserveSpaceV", classAndMethod);
        VerifyMethodInfo vmi = verifyInfo.methodIdxInfos[idx];
        VerifyVarInfo[] mvvis = db.getVerfiyMethodInfos().get(classAndMethod)[entry-1];
        VerifyVarInfo[] vvis = new VerifyVarInfo[mvvis.length];
        for (int i = 0; i < mvvis.length; i++) {
        	if (mvvis[i] != null) {
        		vvis[i] = mvvis[i].clone();
        	}
        }
        vmi.vars = vvis;
    }
    
    /**
     * Called at the end of a method.
     * Undoes the effects of nextMethodEntry() and clears the dataObject[] array
     * to allow the values to be GCed.
     */
    public final void popMethod() {
    	
        int idx = methodTOS;
        
        int oldSP = curMethodSP;
        int newSP = method[idx-1];
        curMethodSP = newSP;
        methodTOS = idx-2;

        if (newSP == oldSP && idx < method.length -1) {
        	oldSP = method[idx + 1];
        }
        
        for (int i = newSP; i < oldSP; i++) {
			dataObject[i] = null;
		}
        
        method[idx] = 0;
        
        if (idx < method.length - 1) { /*newSP == oldSP*/
        	method[idx+1] = 0;
        }
    }
    
    private boolean checkClassAndMethod(int idx, String phrase,  String classAndMethod) {
    	VerifyMethodInfo vmi = verifyInfo.methodIdxInfos[idx];
    	if ( !classAndMethod.equals(vmi.classAndMethod) ) {
    		RuntimeException re = new RuntimeException(buildMessage(this, "#%d %s tos=%d midx=%d sp=%d %s != %s", verifyInfo.vid, phrase, methodTOS, idx, curMethodSP, classAndMethod, vmi.classAndMethod));
    		db.error(re);
    		return false;
    	}
    	return true;
    }
    
    public final void popMethodV(String classAndMethod) {
    	int idx = methodTOS >> 1;
    	popMethod();
    	if (db.meetTraceTargetClassMethod(classAndMethod)) {
    		db.info("#%d popMethodV %s tos=%d midx=%d sp=%d", verifyInfo.vid, classAndMethod, methodTOS, idx, curMethodSP);
    	}
    	checkClassAndMethod(idx, "popMethodV", classAndMethod);
    	verifyInfo.methodIdxInfos[idx] = null;
    }
    
    public final int nextMethodEntryV(String classAndMethod) {
    	int entry = nextMethodEntry();
    	int idx = methodTOS >> 1;
    	if (db.meetTraceTargetClassMethod(classAndMethod)) {
    		db.info("#%d nextMethodEntryV %s, entry=%d tos=%d midx=%d sp=%d", verifyInfo.vid, classAndMethod, entry, methodTOS, idx, curMethodSP);
    	}
    	if (entry < 0) {
			db.warn("#%d nextMethodEntry %s,tos=%d midx=%d sp=%d return -1, we meet a broken suspend methods path because of unwaved methods mingled in the path",
					verifyInfo.vid, classAndMethod, methodTOS, idx, curMethodSP);
		} else {
			if (entry == 0) {
				VerifyMethodInfo vmi = verifyInfo.methodIdxInfos[idx] = verifyInfo.tracerStacks.get(verifyInfo.tracerStacks.size() - 1);
				vmi.idx = methodTOS;
			}
			if (!checkClassAndMethod(idx, "nextMethodEntryV", classAndMethod)){
				db.error("#%d nextMethodEntryV  entry=%d tos=%d midx=%d sp=%d classAndMethod:%s", verifyInfo.vid, entry, methodTOS, idx, curMethodSP, classAndMethod);
			}
		}
    	return entry;
    }
    
    /**
     * called at the begin of a method
     * @return the entry point of this method
     */
    public final int nextMethodEntry() {
    	if (methodTOS > 0 && (methodTOS + 1 == method.length || method[methodTOS + 1] == 0)) {
    		return -1;
    	}
        int idx = methodTOS;
        curMethodSP = method[++idx];
        methodTOS = ++idx;
        return method[idx];
    }
    
    public static void push(int value, Stack s, int idx) {
        s.dataLong[s.curMethodSP + idx] = value;
    }
    public static void push(float value, Stack s, int idx) {
        s.dataLong[s.curMethodSP + idx] = Float.floatToRawIntBits(value);
    }
    public static void push(long value, Stack s, int idx) {
        s.dataLong[s.curMethodSP + idx] = value;
    }
    public static void push(double value, Stack s, int idx) {
        s.dataLong[s.curMethodSP + idx] = Double.doubleToRawLongBits(value);
    }
    public static void push(Object value, Stack s, int idx) {
        s.dataObject[s.curMethodSP + idx] = value;
    }
    
    public static void pushV(int value, Stack s, int idx, String classAndMethod) {
    	int midx = s.methodTOS >> 1;
    	if (db.meetTraceTargetClassMethod(classAndMethod)) {
    		db.info("#%d pushVInt %s, tos=%d midx=%d sp=%d idx=%d v=%d", s.verifyInfo.vid, classAndMethod, s.methodTOS, midx, s.curMethodSP, idx, value);
    	}
        s.dataLong[s.curMethodSP + idx] = value;
	    s.checkClassAndMethod(midx, "pushV", classAndMethod);
        VerifyVarInfo[] vars = s.verifyInfo.methodIdxInfos[midx].vars;
        vars[(vars.length >> 1) + idx].value = value;
    }
    
    public static void pushV(float value, Stack s, int idx, String classAndMethod) {
    	int midx = s.methodTOS >> 1;
    	if (db.meetTraceTargetClassMethod(classAndMethod)) {
    		db.info("#%d pushVFloat %s, tos=%d midx=%d sp=%d idx=%d v=%f", s.verifyInfo.vid, classAndMethod, s.methodTOS, midx, s.curMethodSP, idx, value);
    	}
	    s.checkClassAndMethod(midx, "pushV", classAndMethod);
        s.dataLong[s.curMethodSP + idx] = Float.floatToRawIntBits(value);
        VerifyVarInfo[] vars = s.verifyInfo.methodIdxInfos[s.methodTOS >> 1].vars;
        vars[(vars.length >> 1) + idx].value = value;
    }
    
    public static void pushV(long value, Stack s, int idx, String classAndMethod) {
    	int midx = s.methodTOS >> 1;
    	if (db.meetTraceTargetClassMethod(classAndMethod)) {
    		db.info("#%d pushVLong %s, tos=%d midx=%d sp=%d idx=%d v=%d", s.verifyInfo.vid, classAndMethod, s.methodTOS, midx, s.curMethodSP, idx, value);
    	}
	    s.checkClassAndMethod(midx, "pushV", classAndMethod);
        s.dataLong[s.curMethodSP + idx] = value;
        VerifyVarInfo[] vars = s.verifyInfo.methodIdxInfos[s.methodTOS >> 1].vars;
        vars[(vars.length >> 1) + idx].value = value;
    }
    
    public static void pushV(double value, Stack s, int idx, String classAndMethod) {
    	int midx = s.methodTOS >> 1;
    	if (db.meetTraceTargetClassMethod(classAndMethod)) {
    		db.info("#%d pushVDouble %s, tos=%d midx=%d sp=%d idx=%d v=%f", s.verifyInfo.vid, classAndMethod, s.methodTOS, midx, s.curMethodSP, idx, value);
    	}
	    s.checkClassAndMethod(midx, "pushV", classAndMethod);
        s.dataLong[s.curMethodSP + idx] = Double.doubleToRawLongBits(value);
        VerifyVarInfo[] vars = s.verifyInfo.methodIdxInfos[s.methodTOS >> 1].vars;
        vars[(vars.length >> 1) + idx].value = value;
    }
    
    private static Object takeValueWithoutRealizeIt(Object v) {
    	if (v == null) {
    		return null;
    	}
    	Object fv = v;
    	//TODO:!((IPending) fv).isRealized()
		if (fv.getClass().getName().equals("clojure.lang.IPending")) {
			fv = fv.getClass().getName() + Long.toHexString(nginx.clojure.NginxClojureRT.ngx_http_clojure_mem_get_obj_addr(fv));
		}else if (Proxy.isProxyClass(fv.getClass())) {
			Object handler = Proxy.getInvocationHandler(fv);
			fv = "proxy@" + handler.getClass().getName() + Long.toHexString(nginx.clojure.NginxClojureRT.ngx_http_clojure_mem_get_obj_addr(handler));
		}else if (fv != null) {
			fv = fv.toString();
			if (((String)fv).length() > 100) {
				fv = ((String)fv).substring(0, 100);
			}
		}
		
		return fv;
    }
    
    public static void pushV(Object value, Stack s, int idx, String classAndMethod) {
    	int midx = s.methodTOS >> 1;
    	if (db.meetTraceTargetClassMethod(classAndMethod)) {
    		db.info(buildMessage(s, "#%d pushVObject %s, tos=%d midx=%d sp=%d idx=%d v=%s", s.verifyInfo.vid, classAndMethod, s.methodTOS, midx, s.curMethodSP, idx, takeValueWithoutRealizeIt(value)));
    	}
	    s.checkClassAndMethod(midx, "pushV", classAndMethod);
        s.dataObject[s.curMethodSP + idx] = value;
        VerifyVarInfo[] vars = s.verifyInfo.methodIdxInfos[s.methodTOS >> 1].vars;
        vars[idx].value = value;
    }
    

    public final int getInt(int idx) {
        return (int)dataLong[curMethodSP + idx];
    }
    public final float getFloat(int idx) {
        return Float.intBitsToFloat((int)dataLong[curMethodSP + idx]);
    }
    public final long getLong(int idx) {
        return dataLong[curMethodSP + idx];
    }
    public final double getDouble(int idx) {
        return Double.longBitsToDouble(dataLong[curMethodSP + idx]);
    }
    public final Object getObject(int idx) {
        return dataObject[curMethodSP + idx];
    }
    
    private static String buildMessage(Stack s, String format, Object... args) {
    	setStack(null);
    	try {
    		return String.format(format, args);
    	}finally {
    		setStack(s);
    	}
    }
    
    private static void printErrorMessage(MethodDatabase db, Stack s, String format, Object... args) {
    	setStack(null);
    	try {
    		RuntimeException re = new RuntimeException(String.format(format, args));
    		db.error(re);
    	}finally {
    		setStack(s);
    	}
    }
    
    public final int getIntV(int idx, String classAndMethod) {
    	int midx = methodTOS >> 1;
	    checkClassAndMethod(midx, "getIntV", classAndMethod);
        int rt = (int)dataLong[curMethodSP + idx];
    	if (db.meetTraceTargetClassMethod(classAndMethod)) {
    		db.info("#%d getIntV %s, tos=%d mid=%d, sp=%d idx=%d v=%s", verifyInfo.vid, classAndMethod, methodTOS, midx, curMethodSP, idx, rt);
    	}
    	VerifyVarInfo[] vis = verifyInfo.methodIdxInfos[midx].vars;
    	Object ort = vis[(vis.length >> 1) + idx].value;
    	int prt = 0;
    	
    	if (ort instanceof Boolean) {
    		prt = (Boolean)ort ? 1 : 0;
    	}else if (ort instanceof Number) {
    		prt = ((Number)ort).intValue();
    	}else if (ort instanceof Character) {
    		prt = ((Character)ort).charValue();
    	}else {
    		printErrorMessage(db, this,"#%d getIntV %s tos=%d midx=%d, sp=%d idx=%d  %s != %s",  verifyInfo.vid, classAndMethod, methodTOS, midx, curMethodSP, idx, rt, ort);
    		return rt;
    	}
        
        if (rt != prt) {
        	printErrorMessage(db, this ,"#%d getIntV %s tos=%d midx=%d, sp=%d idx=%d  %s != %s", verifyInfo.vid, classAndMethod, methodTOS, midx, curMethodSP, idx, rt, prt);
        }
    	return rt;
    }
    public final float getFloatV(int idx, String classAndMethod) {
    	int midx = methodTOS >> 1;
	    checkClassAndMethod(midx, "getFloatV", classAndMethod);
        float rt = Float.intBitsToFloat((int)dataLong[curMethodSP + idx]);
        if (db.meetTraceTargetClassMethod(classAndMethod)) {
    		db.info("#%d getFloatV %s, tos=%d mid=%d, sp=%d idx=%d v=%s", verifyInfo.vid, classAndMethod, methodTOS, midx, curMethodSP, idx, rt);
    	}
    	VerifyVarInfo[] vis = verifyInfo.methodIdxInfos[midx].vars;
    	Object ort = vis[(vis.length >> 1) + idx].value;
    	Float prt;
    	if (ort instanceof Float) {
    		prt = (Float)ort;
    	}else {
    		printErrorMessage(db, this,"#%d getFloatV %s tos=%d midx=%d, sp=%d idx=%d  %s != %s",  verifyInfo.vid, classAndMethod, methodTOS, midx,curMethodSP, idx, rt, ort);
    		return rt;
    	}
        if (rt != prt) {
        	printErrorMessage(db, this ,"#%d getFloatV %s tos=%d midx=%d, sp=%d idx=%d  %s != %s", verifyInfo.vid, classAndMethod, methodTOS, midx, curMethodSP, idx,rt, prt);
        }
    	return rt;
    }
    public final long getLongV(int idx, String classAndMethod) {
    	int midx = methodTOS >> 1;
	    checkClassAndMethod(midx, "getLongV", classAndMethod);
        long rt = dataLong[curMethodSP + idx];
        if (db.meetTraceTargetClassMethod(classAndMethod)) {
    		db.info("#%d getLongV %s, tos=%d midx=%d, sp=%d idx=%d v=%s", verifyInfo.vid, classAndMethod, methodTOS, midx, curMethodSP, idx, rt);
    	}
    	VerifyVarInfo[] vis = verifyInfo.methodIdxInfos[midx].vars;
    	Object ort = vis[(vis.length >> 1) + idx].value;
    	Long prt;
    	if (ort instanceof Long) {
    		prt = (Long)ort;
    	}else {
    		printErrorMessage(db, this ,"#%d getLongV %s tos=%d midx=%d, sp=%d idx=%d  %s != %s",  verifyInfo.vid, classAndMethod, methodTOS, midx, curMethodSP, idx, rt, ort);
    		return rt;
    	}
        if (rt != prt) {
        	printErrorMessage(db, this ,"#%d getLongV %s tos=%d midx=%d, sp=%d idx=%d  %s != %s", verifyInfo.vid, classAndMethod, methodTOS, midx,  curMethodSP, idx,rt, prt);
        }
    	return rt;
    }
    public final double getDoubleV(int idx, String classAndMethod) {
    	int midx = methodTOS >> 1;
	    checkClassAndMethod(midx, "getDoubleV", classAndMethod);
        double rt = Double.longBitsToDouble(dataLong[curMethodSP + idx]);
        if (db.meetTraceTargetClassMethod(classAndMethod)) {
    		db.info("#%d getDoubleV %s, tos=%d midx=%d, sp=%d idx=%d v=%s", verifyInfo.vid, classAndMethod, midx, curMethodSP, idx, rt);
    	}
    	VerifyVarInfo[] vis = verifyInfo.methodIdxInfos[midx].vars;
    	Object ort = vis[(vis.length >> 1) + idx].value;
    	Double prt;
    	if (ort instanceof Double) {
    		prt = (Double)ort;
    	}else {
    		printErrorMessage(db, this ,"#%d getDoubleV %s tos=%d midx=%d, sp=%d idx=%d  %s != %s",  verifyInfo.vid, classAndMethod, methodTOS,midx, curMethodSP, idx, rt, ort);
    		return rt;
    	}
        if (rt != prt) {
        	printErrorMessage(db, this ,"#%d getDoubleV %s tos=%d midx=%d, sp=%d idx=%d  %s != %s", verifyInfo.vid, classAndMethod, methodTOS, midx, curMethodSP, idx,rt, prt);
        }
    	return rt;
    }
    public final Object getObjectV(int idx, String classAndMethod) {
    	int midx = methodTOS >> 1;
	    checkClassAndMethod(midx, "getObjectV", classAndMethod);
        Object rt = dataObject[curMethodSP + idx];
        if (db.meetTraceTargetClassMethod(classAndMethod)) {
    		db.info(buildMessage(this ,"#%d getObjectV %s, tos=%d midx=%d, sp=%d idx=%d v=%s", verifyInfo.vid, classAndMethod, methodTOS, midx, curMethodSP, idx, takeValueWithoutRealizeIt(rt)));
    	}
        Object prt = verifyInfo.methodIdxInfos[midx].vars[idx].value;
        if (rt != prt) {
        	printErrorMessage(db, this ,"#%d getObjectV %s tos=%d midx=%d, sp=%d idx=%d  %s != %s" , verifyInfo.vid, classAndMethod, methodTOS, midx,  curMethodSP, idx, takeValueWithoutRealizeIt(rt), takeValueWithoutRealizeIt(prt));
        }
    	return rt;
    }
    
    
    /** called when resuming a stack */
    final void resumeStack() {
        methodTOS = -1;
    }
    
    /* DEBUGGING CODE
    public void dump() {
        int sp = 0;
        for(int i=0 ; i<=methodTOS ; i++) {
            System.out.println("i="+i+" entry="+methodEntry[i]+" sp="+methodSP[i]);
            for(; sp < methodSP[i+1] ; sp++) {
                System.out.println("sp="+sp+" long="+dataLong[sp]+" obj="+dataObject[sp]);
            }
        }
    }
    */

    private void growDataStack(int required) {
        int newSize = dataObject.length;
        do {
            newSize *= 2;
        } while(newSize < required);
        
        dataLong = Util.copyOf(dataLong, newSize);
        dataObject = Util.copyOf(dataObject, newSize);
    }

    private void growMethodStack() {
        int newSize = method.length * 2;
        
        method = Util.copyOf(method, newSize);
    }
    
    public static void setDb(MethodDatabase db) {
		Stack.db = db;
	}
    
    public static MethodDatabase getDb() {
		return db;
	}
    
    /**
     * for junit test to check all objects are null now.
     */
    public boolean allObjsAreNull() {
    	if (dataObject != null) {
        	for (Object o : dataObject) {
        		if (o != null) {
        			return false;
        		}
        	}
    	}
    	return true;
    }
    
    protected void release() {
    	methodTOS = -1;
    	if (verifyInfo != null) {
    		verifyInfo.tracerStacks.clear();
    		fillNull(verifyInfo.methodIdxInfos, 0, verifyInfo.methodIdxInfos.length);
    	}
    	fillNull(dataObject, 0, dataObject.length);
    }
    
    public static void fillNull(Object[] array, int s, int len) {
    	if (len > 0) {
    		if (len > 1024) {
    			System.arraycopy(nullValue1kArray, 0, array, s, 1024);
    			for (int i = 1024; i < len; i += i) {
    				System.arraycopy(array, 0, array, i+s, ((len - i) < i) ? (len - i) : i);
    			}
    		}else {
    			System.arraycopy(nullValue1kArray, 0, array, s, len);
    		}
    		
    	}
    }
}
