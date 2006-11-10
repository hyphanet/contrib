package com.onionnetworks.util;

import java.util.EventListener;

/**
 * An interface for an Exception handler.
 *
 * @author Justin F. Chapweske
 */
public interface ExceptionHandler extends EventListener {

    public static final String HANDLE_EXCEPTION = "handleException";
    
    public static final String[] EVENTS = new String[] { HANDLE_EXCEPTION };

    public void handleException(ExceptionEvent ev);
}
