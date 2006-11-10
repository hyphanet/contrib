package com.onionnetworks.fec.io;

import java.io.IOException;

/**
 * This exception signals that there was an attempt to write a packet to a
 * block that has already been decoded. 
 *  
 * (c) Copyright 2001 Onion Networks
 * (c) Copyright 2000 OpenCola
 *
 * @author Justin F. Chapweske (justin@chapweske.com)
 */
public class BlockAlreadyDecodedException extends IOException {
    
    int blockNum, stripeNum;

    /**
     * Constructs a new Exception
     *
     * @param blockNum The blockNum of the attempted packet
     * @param stripeNum The stripeNum of the attempted packet
     */
    public BlockAlreadyDecodedException(int blockNum, int stripeNum) {
        super();

        this.blockNum = blockNum;
        this.stripeNum = stripeNum;
    }

    /**
     * Constructs a new Exception
     *
     * @param blockNum The blockNum of the attempted packet
     * @param stripeNum The stripeNum of the attempted packet
     * @param msg An informational message to include
     */
    public BlockAlreadyDecodedException(String msg, int blockNum, 
                                        int stripeNum) {
        super(msg);

        this.blockNum = blockNum;
        this.stripeNum = stripeNum;
    }

    /**
     * @return The blockNum of the attempted packet.
     */
    public int getBlockNum() {
        return blockNum;
    }

    /** 
     * @return the stripeNum of the attempted packet.
     */
    public int getStripeNum() {
        return stripeNum;
    }
}
