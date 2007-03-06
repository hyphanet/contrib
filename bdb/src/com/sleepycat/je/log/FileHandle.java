/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2006 Oracle.  All rights reserved.
 *
 * $Id: FileHandle.java,v 1.19 2006/10/30 21:14:20 bostic Exp $
 */

package com.sleepycat.je.log;

import java.io.IOException;
import java.io.RandomAccessFile;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.latch.Latch;
import com.sleepycat.je.latch.LatchSupport;

/**
 * A FileHandle embodies a File and its accompanying latch.
 */
class FileHandle {
    private RandomAccessFile file;
    private Latch fileLatch;
    private boolean oldHeaderVersion;

    FileHandle(RandomAccessFile file,
               String fileName,
               EnvironmentImpl env,
               boolean oldHeaderVersion) {
        this.file = file;
        this.oldHeaderVersion = oldHeaderVersion;
        fileLatch = LatchSupport.makeLatch(fileName + "_fileHandle", env);
    }

    RandomAccessFile getFile() {
        return file;
    }

    boolean isOldHeaderVersion() {
        return oldHeaderVersion;
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
