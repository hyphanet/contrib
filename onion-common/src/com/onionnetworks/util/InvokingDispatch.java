package com.onionnetworks.util;

import java.util.*;

public class InvokingDispatch extends ReflectiveEventDispatch implements
    EventListener{

    public static final String INVOKE = "invoke";

    public InvokingDispatch() {
	super();
	addListener(this,this,INVOKE);
    }
    
    // can't be inner class unless we create a public interface for invoke()
    public void invoke(InvokeEvent ev) {
	ev.getRunnable().run();
	synchronized (ev) {
	    ev.notifyAll();
	}
    }

    public void invokeLater(Runnable r) {
	fire(new InvokeEvent(this,r),INVOKE);
    }

    public void invokeAndWait(Runnable r) throws InterruptedException {
	InvokeEvent ev = new InvokeEvent(this,r);
	synchronized (ev) {
	    fire(ev,INVOKE);
	    ev.wait();
	}
    }

    public class InvokeEvent extends EventObject {
	Runnable r;
	public InvokeEvent(Object source, Runnable r) {
	    super(source);
	    this.r = r;
	}
	
	public Runnable getRunnable() {
	    return r;
	}
    }
}


