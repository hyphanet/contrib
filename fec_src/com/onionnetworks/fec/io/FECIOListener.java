package com.onionnetworks.fec.io;

import java.util.*;

/**
 * This interface is implemented to listen for FECIOEvents
 *
 * (c) Copyright 2001 Onion Networks
 * (c) Copyright 2000 OpenCola
 *
 * @author Justin F. Chapweske (justin@chapweske.com)
 */
public interface FECIOListener extends EventListener {

    public static final String NOTIFY = "notify";
    
    public static final String[] EVENTS = new String[] { NOTIFY };

    public void notify(FECIOEvent ev);
}

