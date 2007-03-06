/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2006 Oracle.  All rights reserved.
 *
 * $Id: LogWritable.java,v 1.15 2006/10/30 21:14:20 bostic Exp $
 */

package com.sleepycat.je.log;

import java.nio.ByteBuffer;

/**
 * A class that implements LogWritable knows how to write itself into the JE
 * log.
 */
public interface LogWritable {

    /**
     * @return number of bytes used to store this object.
     */
    public int getLogSize();

    /**
     * Serialize this object into the buffer. 
     * @param logBuffer is the destination buffer
     */
    public void writeToLog(ByteBuffer logBuffer);
}
