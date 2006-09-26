package com.onionnetworks.fec.io;

import java.util.*;

/**
 * This event signifies that a new packet was recieved and written to disk.
 *
 * (c) Copyright 2001 Onion Networks
 * (c) Copyright 2000 OpenCola
 *
 * @author Justin F. Chapweske (justin@chapweske.com)
 */
public class PacketWrittenEvent extends FECIOEvent {

    int blockNum,stripeNum,blockPacketCount;

    public PacketWrittenEvent(Object source, int blockNum, int stripeNum,
                              int blockPacketCount) {
	super(source);
        this.blockNum = blockNum;
        this.stripeNum = stripeNum;
        this.blockPacketCount = blockPacketCount;
    }

    /**
     * @return the blockNum of the packet just written.
     */
    public int getBlockNum() {
        return blockNum;
    }

    /**
     * @return the stripeNum of the packet just written.
     */
    public int getStripeNum() {
        return stripeNum;
    }

    /**
     * @return the blockPacketCount for this write.  This value is used to
     * tell how many packets have been written to disk at this time.  Remember
     * that more packets can be written to disk than need be for repair.
     */
    public int getBlockPacketCount() {
        return blockPacketCount;
    }
}
	
