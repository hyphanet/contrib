/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: FileSource.java,v 1.32.2.1 2007/02/01 14:49:47 cwl Exp $
 */

package com.sleepycat.je.log;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.dbi.EnvironmentImpl;

/**
 * FileSource is used as a channel to a log file when faulting in objects
 * from the log.
 */
class FileSource implements LogSource {

    private RandomAccessFile file;
    private int readBufferSize;
    private FileManager fileManager;

    FileSource(RandomAccessFile file,
	       int readBufferSize,
	       FileManager fileManager) {
        this.file = file;
        this.readBufferSize = readBufferSize;
	this.fileManager = fileManager;
    }

    /**
     * @see LogSource#release
     */
    public void release() 
        throws DatabaseException {
    }

    /**
     * @see LogSource#getBytes
     */
    public ByteBuffer getBytes(long fileOffset)
        throws IOException {
        
        /* Fill up buffer from file. */
        ByteBuffer destBuf = ByteBuffer.allocate(readBufferSize);
        fileManager.readFromFile(file, destBuf, fileOffset);

	assert EnvironmentImpl.maybeForceYield();

        destBuf.flip();
        return destBuf;
    }

    /**
     * @see LogSource#getBytes
     */
    public ByteBuffer getBytes(long fileOffset, int numBytes)
        throws IOException {

        /* Fill up buffer from file. */
        ByteBuffer destBuf = ByteBuffer.allocate(numBytes);
        fileManager.readFromFile(file, destBuf, fileOffset);

	assert EnvironmentImpl.maybeForceYield();

        destBuf.flip();
        
        assert destBuf.remaining() >= numBytes:
            "remaining=" + destBuf.remaining() +
            " numBytes=" + numBytes;
        return destBuf;
    }
}
