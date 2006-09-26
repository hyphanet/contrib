package com.onionnetworks.fec.io;

/**
 * This event signifies that a complete block was decoded.
 *
 * (c) Copyright 2001 Onion Networks
 * (c) Copyright 2000 OpenCola
 *
 * @author Justin F. Chapweske (justin@chapweske.com)
 */
public class BlockDecodedEvent extends FECIOEvent {

    int blockNum;

    public BlockDecodedEvent(Object source, int blockNum) {
	super(source);
	this.blockNum = blockNum;
    }

    /**
     * @return the blockNum of the block that was decoded.
     */
    public int getBlockNum() {
	return blockNum;
    }
}
	
