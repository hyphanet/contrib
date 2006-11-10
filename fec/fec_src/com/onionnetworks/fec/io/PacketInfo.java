package com.onionnetworks.fec.io;

/**
 * This class provides a (blockNum,stripeNum) tuple that is comparable for
 * equality.
 *
 * (c) Copyright 2001 Onion Networks
 * (c) Copyright 2000 OpenCola
 *
 * @author Justin F. Chapweske (justin@chapweske.com)
 */
public class PacketInfo {

    int blockNum, stripeNum;

    public PacketInfo(int blockNum, int stripeNum) {
        this.blockNum = blockNum;
        this.stripeNum = stripeNum;
    }

    public int getBlockNum() {
        return blockNum;
    }

    public int getStripeNum() {
        return stripeNum;
    }

    public int hashCode() {
        return blockNum*stripeNum;
    }

    public boolean equals(Object obj) {
        if (obj instanceof PacketInfo && 
            ((PacketInfo) obj).blockNum == blockNum &&
            ((PacketInfo) obj).stripeNum == stripeNum) {
            return true;
        }
        return false;
    }
}
