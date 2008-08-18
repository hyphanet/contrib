/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: FileHandleSource.java,v 1.16 2008/03/19 11:56:55 cwl Exp $
 */

package com.sleepycat.je.log;

import com.sleepycat.je.DatabaseException;

/**
 * FileHandleSource is a file source built on top of a cached file handle.
 */
class FileHandleSource extends FileSource {

    private FileHandle fileHandle;

    FileHandleSource(FileHandle fileHandle,
		     int readBufferSize,
                     FileManager fileManager) {
        super(fileHandle.getFile(), readBufferSize, fileManager,
              fileHandle.getFileNum());
        this.fileHandle = fileHandle;
    }

    /**
     * @see LogSource#release
     */
    public void release()
        throws DatabaseException {

        fileHandle.release();
    }
}
