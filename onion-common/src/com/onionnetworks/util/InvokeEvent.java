package com.onionnetworks.util;

//~--- JDK imports ------------------------------------------------------------

import java.util.EventObject;

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
