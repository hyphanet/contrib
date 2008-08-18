/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: FileSource.java,v 1.37 2008/03/19 11:56:55 cwl Exp $
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
    private long fileNum;

    FileSource(RandomAccessFile file,
	       int readBufferSize,
	       FileManager fileManager,
               long fileNum) {
        this.file = file;
        this.readBufferSize = readBufferSize;
	this.fileManager = fileManager;
        this.fileNum = fileNum;
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
        throws DatabaseException, IOException {

        /* Fill up buffer from file. */
        ByteBuffer destBuf = ByteBuffer.allocate(readBufferSize);
        fileManager.readFromFile(file, destBuf, fileOffset, fileNum);

	assert EnvironmentImpl.maybeForceYield();

        destBuf.flip();
        return destBuf;
    }

    /**
     * @see LogSource#getBytes
     */
    public ByteBuffer getBytes(long fileOffset, int numBytes)
        throws DatabaseException, IOException {

        /* Fill up buffer from file. */
        ByteBuffer destBuf = ByteBuffer.allocate(numBytes);
        fileManager.readFromFile(file, destBuf, fileOffset, fileNum);

	assert EnvironmentImpl.maybeForceYield();

        destBuf.flip();

        assert destBuf.remaining() >= numBytes:
            "remaining=" + destBuf.remaining() +
            " numBytes=" + numBytes;
        return destBuf;
    }
}
