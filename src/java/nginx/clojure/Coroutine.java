/*
 * Copyright (c) 2008, Matthias Mann
 * Copyright (C) 2014 Zhang,Yuexiang (xfeep)
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

import java.io.IOException;
import java.io.Serializable;


/**
 * <p>A Coroutine is used to run a CoroutineProto.</p>
 * <p>It also provides a function to suspend a running Coroutine.</p>
 * 
 * <p>A Coroutine can be serialized if it's not running and all involved
 * classes and data types are also {@link Serializable}.</p>
 * 
 * @author Matthias Mann
 */
public class Coroutine implements Runnable, Serializable {

    /**
     * Default stack size for the data stack.
     * @see #Coroutine(de.matthiasmann.continuations.CoroutineProto, int) 
     */
	public static final int DEFAULT_STACK_SIZE = System.getProperty("nginx.clojure.coroutine.defaultStackSize") == null ? 256
			: Integer.parseInt(System.getProperty("nginx.clojure.coroutine.defaultStackSize"));
    
    private static final long serialVersionUID = 2783452871536981L;
    
    public enum State {
        /** The Coroutine has not yet been executed */
        NEW,
        /** The Coroutine is currently executing */
        RUNNING,
        /** The Coroutine has suspended it's execution */
        SUSPENDED,
        /** The Coroutine has finished it's run method
         * @see CoroutineProto#coExecute()
         */
        FINISHED
    };
    
    public interface FinishAwaredRunnable extends Runnable {
    	void onFinished(Coroutine c);
    }
    
    private Runnable proto;
    private final Stack stack;
    private final SuspendableConstructorUtilStack cstack;
    private State state;
    private int resumeCounter = 0;
    
    private Object locals;
    private Object inheritableLocals;
    
    /**
     * Suspend the currently running Coroutine on the calling thread.
     * 
     * @throws de.matthiasmann.continuations.SuspendExecution This exception is used for control transfer - don't catch it !
     * @throws java.lang.IllegalStateException If not called from a Coroutine
     */
    public static void yield() throws SuspendExecution, IllegalStateException {
        throw new Error("Calling function not instrumented");
    }
    
    /**
     * DON'T call this, this method is used by wave tool for generate waving configuration file
     * @throws SuspendExecution
     * @throws IllegalStateException
     */
    public static void _yieldp() throws SuspendExecution, IllegalStateException {
    	
    }

    /**
     * Creates a new Coroutine from the given CoroutineProto. A CoroutineProto
     * can be used in several Coroutines at the same time - but then the normal
     * multi threading rules apply to the member state.
     * 
     * @param proto the CoroutineProto for the Coroutine.
     */
    public Coroutine(Runnable proto) {
        this(proto, DEFAULT_STACK_SIZE);
    }
    
    /**
     * Creates a new Coroutine from the given CoroutineProto. A CoroutineProto
     * can be used in several Coroutines at the same time - but then the normal
     * multi threading rules apply to the member state.
     * 
     * @param proto the CoroutineProto for the Coroutine.
     * @param stackSize the initial stack size for the data stack
     * @throws NullPointerException when proto is null
     * @throws IllegalArgumentException when stackSize is &lt;= 0
     */
    public Coroutine(Runnable proto, int stackSize) {
        this.proto = proto;
        this.stack = new Stack(this, stackSize);
        this.cstack = new SuspendableConstructorUtilStack(stackSize/8);
        this.state = State.NEW;
        Thread thread = Thread.currentThread();
        Object currentLocals = HackUtils.getThreadLocals(Thread.currentThread());
        this.locals = HackUtils.cloneThreadLocalMap(currentLocals);
        try {
            HackUtils.setThreadLocals(thread, this.locals);
            Stack.setStack(this.stack);
            SuspendableConstructorUtilStack.setStack(this.cstack);
        }finally {
        	HackUtils.setThreadLocals(thread, currentLocals);
        }
        
        Object inheritableLocals = HackUtils.getInheritableThreadLocals(Thread.currentThread());
        if (inheritableLocals != null) {
        	this.inheritableLocals = HackUtils.createInheritedMap(inheritableLocals);
        }
        
        if(proto == null) {
            throw new NullPointerException("proto");
        }
        assert isInstrumented(proto) : "Not instrumented";
    }
    
    public void reset() {
    	this.state = State.NEW;
    	Thread thread = Thread.currentThread();
        Object currentLocals = HackUtils.getThreadLocals(Thread.currentThread());
        this.locals = HackUtils.cloneThreadLocalMap(currentLocals);
        try {
            HackUtils.setThreadLocals(thread, this.locals);
            Stack.setStack(this.stack);
            SuspendableConstructorUtilStack.setStack(this.cstack);
        }finally {
        	HackUtils.setThreadLocals(thread, currentLocals);
        }
        
        Object inheritableLocals = HackUtils.getInheritableThreadLocals(Thread.currentThread());
        if (inheritableLocals != null) {
        	this.inheritableLocals = HackUtils.createInheritedMap(inheritableLocals);
        }
    }

    public void reset(Runnable proto) {
    	this.proto = proto;
    	reset();
    }
    /**
     * Returns the active Coroutine on this thread or NULL if no coroutine is running.
     * @return the active Coroutine on this thread or NULL if no coroutine is running.
     */
    public static Coroutine getActiveCoroutine() {
        Stack s = Stack.getStack();
        if(s != null) {
            return s.co;
        }
        return null;
    }
    
    /**
     * Returns the CoroutineProto that is used for this Coroutine
     * @return The CoroutineProto that is used for this Coroutine
     */
    public Runnable getProto() {
        return proto;
    }

    /**
     * <p>Returns the current state of this Coroutine. May be called by the Coroutine
     * itself but should not be called by another thread.</p>
     * 
     * <p>The Coroutine starts in the state NEW then changes to RUNNING. From
     * RUNNING it may change to FINISHED or SUSPENDED. SUSPENDED can only change
     * to RUNNING by calling run() again.</p>
     * 
     * @return The current state of this Coroutine
     * @see #run()
     */
    public State getState() {
        return state;
    }
    
    
    
    /**
     * Runs the Coroutine until it is finished or suspended. This method must only
     * be called when the Coroutine is in the states NEW or SUSPENDED. It is not
     * multi threading safe.
     * 
     * @throws java.lang.IllegalStateException if the Coroutine is currently running or already finished.
     */
    public void run() throws IllegalStateException {
        resume();
    }

    /**
     * Runs the Coroutine until it is finished or suspended. This method must only
     * be called when the Coroutine is in the states NEW or SUSPENDED. It is not
     * multi threading safe.
     * 
     * @throws java.lang.IllegalStateException if the Coroutine is currently running or already finished.
     */
	public void resume() {
		if(state != State.NEW && state != State.SUSPENDED) {
            throw new IllegalStateException("Not new or suspended");
        }
		resumeCounter++;
        State result = State.FINISHED;
//        Stack oldStack = Stack.getStack();
//        SuspendableConstructorUtilStack oldCStack = SuspendableConstructorUtilStack.getStack();
        Thread thread = Thread.currentThread();
        Object oldLocals = HackUtils.getThreadLocals(thread);
        Object oldInheritableLocals = HackUtils.getInheritableThreadLocals(thread);
        try {
            HackUtils.setThreadLocals(thread, this.locals);
            HackUtils.setInheritablehreadLocals(thread, this.inheritableLocals);
            state = State.RUNNING;
//            Stack.setStack(stack);
//            SuspendableConstructorUtilStack.setStack(cstack);
            
            try {
            		proto.run();
            } catch (SuspendExecution ex) {
                assert ex == SuspendExecution.instance;
                result = State.SUSPENDED;
                //stack.dump();
                stack.resumeStack();
            }
        } finally {
        	if (result == State.FINISHED) {
        		//for reduce memory leak probability
        		//cstack.release was called by waved code so skip it here
        		stack.release();
        		inheritableLocals = null;
        		locals = null;
        		resumeCounter = 0;
        		
        		if (proto instanceof FinishAwaredRunnable) {
					((FinishAwaredRunnable) proto).onFinished(this);;
				}
        	}
        	
            HackUtils.setThreadLocals(thread, oldLocals);
            HackUtils.setInheritablehreadLocals(thread, oldInheritableLocals);
            
//            Stack.setStack(oldStack);
//            SuspendableConstructorUtilStack.setStack(oldCStack);

            state = result;
        }
	}
	
	/**
	 * DON'T call this, this method is used by wave tool for generate waving configuration file
	 */
	public void _resumep(){
		if ( ++resumeCounter > 1) {
			return;
		}
		if(state != State.NEW && state != State.SUSPENDED) {
            throw new IllegalStateException("Not new or suspended");
        }
        State result = State.FINISHED;
        Stack oldStack = Stack.getStack();
        try {
            state = State.RUNNING;
            Stack.setStack(stack);
            try {
            		proto.run();
            } catch (SuspendExecution ex) {
                assert ex == SuspendExecution.instance;
                result = State.SUSPENDED;
                //stack.dump();
                stack.resumeStack();
            }
        } finally {
            Stack.setStack(oldStack);
            state = result;
        }
	}
    
    private void writeObject(java.io.ObjectOutputStream out) throws IOException {
        if(state == State.RUNNING) {
            throw new IllegalStateException("trying to serialize a running coroutine");
        }
        out.defaultWriteObject();
    }
    
    public int getResumeCounter() {
		return resumeCounter;
	}
    
    public Stack getStack() {
    	return stack; 
    }
    
    public SuspendableConstructorUtilStack getCStack() {
    	return cstack; 
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private boolean isInstrumented(Runnable proto) {
        try {
            Class clz = Class.forName("nginx.clojure.wave.AlreadyInstrumented");
            return proto.getClass().isAnnotationPresent(clz);
        } catch (ClassNotFoundException ex) {
            return true;   // can't check
        } catch (Throwable ex) {
            return true;    // it's just a check - make sure we don't fail if something goes wrong
        }
    }
}
