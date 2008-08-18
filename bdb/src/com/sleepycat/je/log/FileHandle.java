/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: FileHandle.java,v 1.27 2008/05/07 17:12:25 mark Exp $
 */

package com.sleepycat.je.log;

import java.io.IOException;
import java.io.RandomAccessFile;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.latch.Latch;

/**
 * A FileHandle embodies a File and its accompanying latch.
 */
class FileHandle {
    private RandomAccessFile file;
    private Latch fileLatch;
    private int logVersion;
    private long fileNum;

    /**
     * Creates a new handle but does not initialize it.  The init method must
     * be called before using the handle to access the file.
     */
    FileHandle(long fileNum, String label) {
        fileLatch = new Latch("file_" + label + "_fileHandle");
        this.fileNum = fileNum;
    }

    /**
     * Initializes the handle after opening the file and reading the header.
     */
    void init(RandomAccessFile file, int logVersion) {
        this.file = file;
        this.logVersion = logVersion;
    }

    RandomAccessFile getFile() {
        return file;
    }

    long getFileNum() {
        return fileNum;
    }

    int getLogVersion() {
        return logVersion;
    }

    boolean isOldHeaderVersion() {
        return logVersion < LogEntryType.LOG_VERSION;
    }

    void latch()
        throws DatabaseException {

        fileLatch.acquire();
    }

    boolean latchNoWait()
        throws DatabaseException {

        return fileLatch.acquireNoWait();
    }

    void release()
        throws DatabaseException {

        fileLatch.release();
    }

    void close()
	throws IOException {

	if (file != null) {
	    file.close();
	    file = null;
	}
    }
}
