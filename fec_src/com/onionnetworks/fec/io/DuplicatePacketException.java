package com.onionnetworks.fec.io;

import java.io.IOException;

/**
 * This exception signifies that there was attempt to write a duplicate packet
 *
 * (c) Copyright 2001 Onion Networks
 * (c) Copyright 2000 OpenCola
 *
 * @author Justin F. Chapweske (justin@chapweske.com)
 */
public class DuplicatePacketException extends IOException {
    
    int blockNum, stripeNum, packetIndex;

    public DuplicatePacketException(int blockNum, int stripeNum, 
                                    int packetIndex) {
        super();

        this.blockNum = blockNum;
        this.stripeNum = stripeNum;
        this.packetIndex = packetIndex;
    }

    public DuplicatePacketException(String msg, int blockNum, int stripeNum,
                                    int packetIndex) {
        super(msg);

        this.blockNum = blockNum;
        this.stripeNum = stripeNum;
        this.packetIndex = packetIndex;
    }

    /**
     * @return the blockNum of the attempted packet
     */
    public int getBlockNum() {
        return blockNum;
    }

    /**
     * @return the stripeNum of the attempted packet
     */
    public int getStripeNum() {
        return stripeNum;
    }

    /**
     * @return the packetIndex of the existing copy of the packet.  -1 if the
     * packetIndex is unknown or there was an attempt to write a padding packet
     */
    public int getPacketIndex() {
        return packetIndex;
    }
}
