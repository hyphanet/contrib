package com.onionnetworks.fec.io;

/**
 * This event signifies that a new packet entry has been added.
 *
 * (c) Copyright 2001 Onion Networks
 * (c) Copyright 2000 OpenCola
 *
 * @author Justin F. Chapweske (justin@chapweske.com)
 */
public class PacketEntryAddedEvent extends FECIOEvent {

    int packetIndex;

    public PacketEntryAddedEvent(Object source, int packetIndex) {
	super(source);
        this.packetIndex = packetIndex;
    }

    /**
     * @return the packetIndex of the packet just written
     */
    public int getPacketIndex() {
        return packetIndex;
    }
}
	
