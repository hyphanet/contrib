package com.onionnetworks.fec.io;

/**
 * This event signifies that the file has been completely decoded.
 *
 * (c) Copyright 2001 Onion Networks
 * (c) Copyright 2000 OpenCola
 *
 * @author Justin F. Chapweske (justin@chapweske.com)
 */
public class FileDecodedEvent extends FECIOEvent {

    public FileDecodedEvent(Object source) {
	super(source);
    }
}
	
