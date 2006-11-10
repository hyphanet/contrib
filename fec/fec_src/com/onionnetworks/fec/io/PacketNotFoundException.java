package com.onionnetworks.fec.io;

import java.io.IOException;

/**
 * This exception signals that there was an attempt to read a packet that
 * can't be found, or created from the data on disk.
 *  
 * (c) Copyright 2001 Onion Networks
 * (c) Copyright 2000 OpenCola
 *
 * @author Justin F. Chapweske (justin@chapweske.com)
 */
public class PacketNotFoundException extends IOException {
    
    int blockNum, stripeNum;

    public PacketNotFoundException(int blockNum, int stripeNum) {
        super();

        this.blockNum = blockNum;
        this.stripeNum = stripeNum;
    }

    public PacketNotFoundException(String msg, int blockNum, int stripeNum) {
        super(msg);

        this.blockNum = blockNum;
        this.stripeNum = stripeNum;
    }

    public int getBlockNum() {
        return blockNum;
    }

    public int getStripeNum() {
        return stripeNum;
    }
}
