package com.onionnetworks.util;

import java.util.EventObject;

/**
 * This class encapsulates an Exception and a Source to be dispatched
 * through the ExceptionHandler class.
 *
 * @author Justin Chapweske
 */
public class ExceptionEvent extends EventObject {

    Throwable t;

    /**
     * @param source The source of the event.  This should probably be
     * the object that caught the exception.
     * @param t The Throwable that is being fired.
     */
    public ExceptionEvent(Object source, Throwable t) {
	super(source);
	this.t = t;
    }

    /**
     * @return The Throwable being dispatched.
     */
    public Throwable getException() {
	return t;
    }
}
