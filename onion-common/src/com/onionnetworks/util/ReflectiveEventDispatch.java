package com.onionnetworks.util;

import java.util.*;
import java.lang.reflect.Method;

/**
 * @author Justin Chapweske
 */
public class ReflectiveEventDispatch implements Runnable {

    public static final int DEFAULT_WARNING_TIME = 10;

    private final Thread thread;
    private HashMap methodCache = new HashMap();
    private HashMap listeners = new HashMap();
    private LinkedList eventQueue = new LinkedList();
    private ExceptionHandler handler;

    public ReflectiveEventDispatch() {
        thread = new Thread(this,"Reflective Dispatch#" + hashCode());
	thread.setDaemon(true);
        thread.start();
    }

    public void setPriority(int priority) {
        thread.setPriority(priority);
    }

    public void setExceptionHandler(ExceptionHandler h) {
	handler = h;
    }

    public synchronized void addListener(Object source, EventListener el, 
					 String methodName) {
        this.addListener(source,el,new String[]{methodName});
    }

    public synchronized void addListener(Object source, EventListener el, 
                                         String[] methodNames) {
	HashMap hm = (HashMap) listeners.get(source);
	if (hm == null) {
	    hm = new HashMap();
	    listeners.put(source, hm);
	}

        for (int i=0;i<methodNames.length;i++) {
            HashSet set = (HashSet) hm.get(methodNames[i]);
            if (set == null) {
                set = new HashSet();
                hm.put(methodNames[i],set);
            }
            set.add(el);
        }
    }

    public synchronized void removeListener(Object source, EventListener el, 
                                            String methodName) {
        this.removeListener(source,el,new String[]{methodName});
    }

    public synchronized void removeListener(Object source, EventListener el, 
                                            String[] methodNames) {
	HashMap hm = (HashMap) listeners.get(source);
	if (hm == null) {
	    throw new IllegalArgumentException("Listener not registered.");
	}
        for (int i=0;i<methodNames.length;i++) {
            HashSet set = (HashSet) hm.get(methodNames[i]);
            if (set == null || !set.contains(el)) {
                throw new IllegalArgumentException("Listener not registered.");
            }

            set.remove(el);
        }
    }

    public synchronized void fire(EventObject ev, String methodName) {
        eventQueue.add(new Tuple(ev, methodName));
        this.notifyAll();
    }

    public synchronized void close() {
	// Place this on the queue to signify that we are done.
	eventQueue.add(this);
        this.notifyAll();
    }

    public void run() {
	boolean done = false;

        while (!done) {
            EventObject ev = null;
            String methodName = null;
            HashSet set = null;
            synchronized (this) {
                if (eventQueue.isEmpty()) {
                    try {
                        this.wait();
                    } catch (InterruptedException e) {
                        done = true;
                    }
                    continue;
                }
		Object obj = eventQueue.removeFirst();
		if (obj == this) {
		    // If this is on the queue, it is time to close.
		    done = true;
		    continue;
		}
		Tuple t = (Tuple) obj;
                ev = (EventObject) t.getLeft();
                methodName = (String) t.getRight();
		HashMap hm = (HashMap) listeners.get(ev.getSource());
		if (hm == null) {
		    continue;
		}
		set = (HashSet) hm.get(methodName);
                if (set == null) {
                    continue;
                }
                // Make a copy incase its modified while we're doing the shit.
                set = (HashSet) set.clone();
            }

            for (Iterator it=set.iterator();it.hasNext();) {
                EventListener el = (EventListener) it.next();
                // Get the method and invoke it, passing the event.
                //long t1 = System.currentTimeMillis();
                try {
		    final Class elc = el.getClass();
		    final Class evc = ev.getClass();
		   
		    // Cache the method because getPublicMethod is very
		    // expensive to invoke.
		    Tuple cacheKey = new Tuple(elc,new Tuple(methodName,evc));
		    Method m = (Method) methodCache.get(cacheKey);
		    if (m == null) {
			final Class ca[] = new Class[] { evc };
			// This version of getMethod supports subclasses as
			// parameter types.
			m = Util.getPublicMethod(elc,methodName,ca);
			methodCache.put(cacheKey,m);
		    }
		    final Object oa[] = new Object[] { ev };
		    m.invoke(el,oa);
                } catch (Throwable t) {
		    if (handler != null) {
			handler.handleException(new ExceptionEvent(this,t));
		    } else {
			t.printStackTrace();
		    }
                }
                //long t2 = System.currentTimeMillis()-t1;
                //if (t2 > DEFAULT_WARNING_TIME) {
                //    System.out.println(el+"."+methodName+"("+ev+") took"+
                //                       " too long: "+t2+" millis");
                //}
            }
        }
    }
}
