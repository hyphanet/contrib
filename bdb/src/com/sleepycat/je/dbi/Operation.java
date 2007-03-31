/*
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: Operation.java,v 1.3.2.1 2007/02/01 14:49:44 cwl Exp $
 */

package com.sleepycat.je.dbi;

import java.nio.ByteBuffer;

/**  
 * An enumeration of different api call sources for replication, currently for
 * debugging. This is also intended to support the future possibility of
 * providing application level visibility into the replication operation
 * stream.
 */
public class Operation {

    public static final Operation PUT = new Operation((byte) 1);
    public static final Operation NO_OVERWRITE = new Operation((byte) 2);
    public static final Operation PLACEHOLDER = new Operation((byte) 3);

    private byte op; 

    private Operation(byte op) {
        this.op = op;
    }

    /**
     * Serialize this object into the buffer. 
     * @param buffer is the destination buffer
     */
    public void writeToBuffer(ByteBuffer buffer) {
        buffer.put(op);
    }
        
    public void readFromBuffer(ByteBuffer buffer) {
        op = buffer.get();
    }
}
