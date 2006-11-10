package com.onionnetworks.fec.io;

import java.io.IOException;

/**
 * This exception signals that there was an attempt to write a packet to a
 * file that has already been completely decoded. 
 *  
 * (c) Copyright 2001 Onion Networks
 * (c) Copyright 2000 OpenCola
 *
 * @author Justin F. Chapweske (justin@chapweske.com)
 */
public class FileAlreadyDecodedException extends IOException {
    
    public FileAlreadyDecodedException() {
        super();
    }

    public FileAlreadyDecodedException(String msg) {
        super(msg);
    }
}
