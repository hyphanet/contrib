/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2006
 *      Oracle Corporation.  All rights reserved.
 *
 * $Id: FileReader.java,v 1.95 2006/09/12 19:16:51 cwl Exp $
 */

package com.sleepycat.je.log;

import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.DbConfigManager;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.utilint.DbLsn;
import com.sleepycat.je.utilint.Tracer;

/**
 * A FileReader is an abstract class that traverses the log files, reading in
 * chunks of the file at a time. Concrete subclasses perform a particular
 * action to each entry.
 */
public abstract class FileReader {

    protected EnvironmentImpl env;
    protected FileManager fileManager;

    /* Buffering reads */
    private ByteBuffer readBuffer;   // buffer for reading from the file
    private ByteBuffer saveBuffer;   // for piecing together data
    private int maxReadBufferSize;   // read buffer can't grow larger than this

    /* Managing the buffer reads */
    private boolean singleFile;      // if true, do not read across files
    protected boolean eof;           // true if at end of the log.
                                     // XXX, use exception instead of status?
    private boolean forward;         // if true, we're reading forward

    /* 
     * ReadBufferFileNum, readBufferFileStart and readBufferFileEnd indicate
     * how the read buffer maps to the file. For example, if the read buffer
     * size is 100 and the read buffer was filled from file 9, starting at byte
     * 100, then
     *          readBufferFileNum = 9
     *          readBufferFileStart = 100
     *          readBufferFileEnd = 200
     */
    protected long readBufferFileNum;  // file number we're pointing to
    protected long readBufferFileStart;// file position that maps to buf start
    protected long readBufferFileEnd;  // file position that maps to buf end

    /* stats */
    private int nRead;           // num entries we've seen

    /* 
     * The number of times we've tried to read in a log entry that was too 
     * large for the read buffer.
     */
    private long nRepeatIteratorReads; 
                                 
    /* Info about the last entry seen. */
    protected byte currentEntryTypeNum;
    protected byte currentEntryTypeVersion;
    protected long currentEntryPrevOffset;
    protected int  currentEntrySize;
    protected long currentEntryChecksum;

    /*
     * nextEntryOffset is used to set the currentEntryOffset after we've read
     * an entry.
     */
    protected long currentEntryOffset;
    protected long nextEntryOffset; 
    protected long startLsn;  // We start reading from this LSN.
    private long finishLsn; // If going backwards, read up to this LSN.

    /* For checking checksum on the read. */
    protected ChecksumValidator cksumValidator;
    private boolean doValidateChecksum;     // Validate checksums
    private boolean alwaysValidateChecksum; // Validate for all entry types

    /* True if this is the scavenger and we are expecting checksum issues. */
    protected boolean anticipateChecksumErrors;

    /**
     * A FileReader just needs to know what size chunks to read in.
     * @param endOfFileLsn indicates the end of the log file
     */
    public FileReader(EnvironmentImpl env,
                      int readBufferSize,
                      boolean forward,
                      long startLsn,
                      Long singleFileNumber,
                      long endOfFileLsn,
                      long finishLsn)
        throws IOException, DatabaseException {

        this.env = env;
        this.fileManager = env.getFileManager();
        this.doValidateChecksum = env.getLogManager().getChecksumOnRead();

        /* Allocate a read buffer. */
        this.singleFile = (singleFileNumber != null);
        this.forward = forward;

        readBuffer = ByteBuffer.allocate(readBufferSize);
        threadSafeBufferFlip(readBuffer);
        saveBuffer = ByteBuffer.allocate(readBufferSize);

        DbConfigManager configManager = env.getConfigManager();
        maxReadBufferSize =
	    configManager.getInt(EnvironmentParams. LOG_ITERATOR_MAX_SIZE);

        /* Determine the starting position. */
        this.startLsn = startLsn;
        this.finishLsn = finishLsn;
        initStartingPosition(endOfFileLsn, singleFileNumber);

        /* stats */
        nRead = 0;
        if (doValidateChecksum) {
            cksumValidator = new ChecksumValidator();
        }
	anticipateChecksumErrors = false;
    }

    /**
     * Helper for determining the starting position and opening up a file at
     * the desired location.
     */
    protected void initStartingPosition(long endOfFileLsn,
					Long ignoreSingleFileNumber)
        throws IOException, DatabaseException {

        eof = false;
        if (forward) {

            /*
             * Start off at the startLsn. If that's null, start at the
             * beginning of the log. If there are no log files, set eof.
             */
            if (startLsn != DbLsn.NULL_LSN) {
                readBufferFileNum = DbLsn.getFileNumber(startLsn);
                readBufferFileEnd = DbLsn.getFileOffset(startLsn);
            } else {
                Long firstNum = fileManager.getFirstFileNum();
                if (firstNum == null) {
                    eof = true;
                } else {
                    readBufferFileNum = firstNum.longValue();
                    readBufferFileEnd = 0;
                }
            }

            /* 
             * After we read the first entry, the currentEntry will point here.
             */
            nextEntryOffset = readBufferFileEnd;
        } else {

            /*
             * Make the read buffer look like it's positioned off the end of
             * the file. Initialize the first LSN we want to read. When
             * traversing the log backwards, we always start at the very end.
             */
            assert startLsn != DbLsn.NULL_LSN;
            readBufferFileNum = DbLsn.getFileNumber(endOfFileLsn);
            readBufferFileStart = DbLsn.getFileOffset(endOfFileLsn);
            readBufferFileEnd = readBufferFileStart;

            /*
             * currentEntryPrevOffset points to the entry we want to start out
             * reading when going backwards. If it's 0, the entry we want to
             * read is in a different file.
             */
            if (DbLsn.getFileNumber(startLsn) ==
		DbLsn.getFileNumber(endOfFileLsn)) {
                currentEntryPrevOffset = DbLsn.getFileOffset(startLsn);
            } else {
                currentEntryPrevOffset = 0;
            }
            currentEntryOffset = DbLsn.getFileOffset(endOfFileLsn);
        }
    }

    /**
     * Whether to always validate the checksum, even for non-target entries.
     */
    public void setAlwaysValidateChecksum(boolean validate) {
        alwaysValidateChecksum = validate;
    }

    /**
     * @return the number of entries processed by this reader.
     */
    public int getNumRead() {
        return nRead;
    }

    public long getNRepeatIteratorReads() {
        return nRepeatIteratorReads;
    }

    /**
     * Get LSN of the last entry read.
     */
    public long getLastLsn() {
        return DbLsn.makeLsn(readBufferFileNum, currentEntryOffset);
    }

    /**
     * readNextEntry scans the log files until either it's reached the end of
     * the log or has hit an invalid portion. It then returns false.
     * 
     * @return true if an element has been read
     */
    public boolean readNextEntry()
        throws DatabaseException, IOException {

        boolean foundEntry = false;
        try {
            while ((!eof) && (!foundEntry)) {

                /* Read the next header. */
                getLogEntryInReadBuffer();
                ByteBuffer dataBuffer =
                    readData(LogManager.HEADER_BYTES, true);

                readHeader(dataBuffer);

                boolean isTargetEntry = isTargetEntry(currentEntryTypeNum,
                                                      currentEntryTypeVersion);
                boolean doValidate = doValidateChecksum &&
                    (isTargetEntry || alwaysValidateChecksum);
                boolean collectData = doValidate || isTargetEntry;

                /* Initialize the checksum with the header. */
                if (doValidate) {
                    startChecksum(dataBuffer);
                }

                /*
                 * Read in the body of the next entry. Note that even if this
                 * isn't a targetted entry, we have to move the buffer position
                 * along.
                 */
                dataBuffer = readData(currentEntrySize, collectData);

                /*
                 * We've read an entry. Move up our offsets if we're moving
                 * forward. If we're moving backwards, we set our offset before
                 * we read the header, because we knew where the entry started.
                 */
                if (forward) {
                    currentEntryOffset = nextEntryOffset;
                    nextEntryOffset +=
                        LogManager.HEADER_BYTES + currentEntrySize;
                }

                /* Validate the log entry checksum. */
                if (doValidate) {
                    validateChecksum(dataBuffer);
                }

                if (isTargetEntry) {

                    /*
                     * For a target entry, call the subclass reader's
                     * processEntry method to do whatever we need with the
                     * entry.  It returns true if this entry is one that should
                     * be returned.  Note that some entries, although targetted
                     * and read, are not returned.
                     */
                    if (processEntry(dataBuffer)) {
                        foundEntry = true;
                        nRead++;
                    }
                } else if (collectData) {

                    /*
                     * For a non-target entry that was validated, the buffer is
                     * positioned at the start of the entry; skip over it.
                     */
                    threadSafeBufferPosition
                        (dataBuffer,
                         threadSafeBufferPosition(dataBuffer) +
                         currentEntrySize);
                }
            }
        } catch (EOFException e) {
            eof = true;
        } catch (DatabaseException e) {
            eof = true;
            /* Report on error. */
            LogEntryType problemType =
                LogEntryType.findType(currentEntryTypeNum,
				      currentEntryTypeVersion);
            Tracer.trace(env, "FileReader", "readNextEntry",
			 "Halted log file reading at file 0x" +
                         Long.toHexString(readBufferFileNum) +
                         " offset 0x" +
                         Long.toHexString(nextEntryOffset) +
                         " offset(decimal)=" + nextEntryOffset +
                         ":\nentry="+ problemType +
                         "(typeNum=" + currentEntryTypeNum +
                         ",version=" + currentEntryTypeVersion +
                         ")\nprev=0x" +
                         Long.toHexString(currentEntryPrevOffset) +
                         "\nsize=" + currentEntrySize +
                         "\nNext entry should be at 0x" +
                         Long.toHexString((nextEntryOffset +
                                           LogManager.HEADER_BYTES +
                                           currentEntrySize)) +
                         "\n:", e);
            throw e;
        }
        return foundEntry;
    }

    protected boolean resyncReader(long nextGoodRecordPostCorruption,
				   boolean dumpCorruptedBounds)
	throws DatabaseException, IOException {

	/* Resync not allowed for straight FileReader runs. */
	return false;
    }

    /**
     * Make sure that the start of the target log entry is in the header. This
     * is a no-op if we're reading forwards
     */
    private void getLogEntryInReadBuffer()
        throws IOException, DatabaseException, EOFException {

        /*
         * If we're going forward, because we read every byte sequentially,
         * we're always sure the read buffer is positioned at the right spot.
         * If we go backwards, we need to jump the buffer position.
         */
        if (!forward) {

            /* 
             * currentEntryPrevOffset is the entry before the current entry.
             * currentEntryOffset is the entry we just read (or the end of the
             * file if we're starting out.
             */
            if ((currentEntryPrevOffset != 0) &&
                (currentEntryPrevOffset >= readBufferFileStart)) {

                /* The next log entry has passed the start LSN. */
                long nextLsn = DbLsn.makeLsn(readBufferFileNum,
					     currentEntryPrevOffset);
                if (finishLsn != DbLsn.NULL_LSN) {
                    if (DbLsn.compareTo(nextLsn, finishLsn) == -1) {
                        throw new EOFException();
                    }
                }

                /* This log entry starts in this buffer, just reposition. */
		threadSafeBufferPosition(readBuffer,
					 (int) (currentEntryPrevOffset -
						readBufferFileStart));
            } else {

		/* 
		 * If the start of the log entry is not in this read buffer,
		 * fill the buffer again. If the target log entry is in a
		 * different file from the current read buffer file, just start
		 * the read from the target LSN. If the target log entry is the
		 * same file but the log entry is larger than the read chunk
		 * size, also start the next read buffer from the target
		 * LSN. Otherwise, try to position the next buffer chunk so the
		 * target entry is held within the buffer, all the way at the
		 * end.
		 */
                if (currentEntryPrevOffset == 0) {
                    /* Go to another file. */
                    currentEntryPrevOffset =
                        fileManager.getFileHeaderPrevOffset(readBufferFileNum);
                    Long prevFileNum = 
                        fileManager.getFollowingFileNum(readBufferFileNum,
                                                        false);
                    if (prevFileNum == null) {
                        throw new EOFException();
                    }
                    if (readBufferFileNum - prevFileNum.longValue() != 1) {

			if (!resyncReader(DbLsn.makeLsn
					  (prevFileNum.longValue(),
                       DbLsn.MAX_FILE_OFFSET),
					  false)) {

			    throw new DatabaseException
				("Cannot read backward over cleaned file" +
				 " from " + readBufferFileNum +
				 " to " + prevFileNum);
			}
		    }
                    readBufferFileNum = prevFileNum.longValue();
                    readBufferFileStart = currentEntryPrevOffset;
                } else if ((currentEntryOffset - currentEntryPrevOffset) >
                           readBuffer.capacity()) {

                    /* 
		     * The entry is in the same file, but is bigger than one
		     * buffer.
		     */
                    readBufferFileStart = currentEntryPrevOffset;
                } else {

                    /* In same file, but not in this buffer. */
                    long newPosition = currentEntryOffset -
                        readBuffer.capacity();
                    readBufferFileStart = (newPosition < 0) ? 0 : newPosition;
                }

                /* The next log entry has passed the start LSN. */
                long nextLsn = DbLsn.makeLsn(readBufferFileNum,
					     currentEntryPrevOffset);
                if (finishLsn != DbLsn.NULL_LSN) {
                    if (DbLsn.compareTo(nextLsn, finishLsn) == -1) {
                        throw new EOFException();
                    }
                }

                /*
                 * Now that we've set readBufferFileNum and
                 * readBufferFileStart, do the read.
                 */
                FileHandle fileHandle =
                    fileManager.getFileHandle(readBufferFileNum);
                try {
                    readBuffer.clear();
                    fileManager.readFromFile(fileHandle.getFile(), readBuffer,
                                             readBufferFileStart);

		    assert EnvironmentImpl.maybeForceYield();
                } finally {
                    fileHandle.release();
                }
                readBufferFileEnd = readBufferFileStart +
                    threadSafeBufferPosition(readBuffer);
                threadSafeBufferFlip(readBuffer);
		threadSafeBufferPosition(readBuffer,
					 (int) (currentEntryPrevOffset -
						readBufferFileStart));
            }
            
            /* The current entry will start at this offset. */
            currentEntryOffset = currentEntryPrevOffset;
        } else {

	    /*
	     * Going forward, and an end point has been specified.  Check if
	     * we've gone past.
	     */
	    if (finishLsn != DbLsn.NULL_LSN) {
		/* The next log entry has passed the end LSN. */
		long nextLsn = DbLsn.makeLsn(readBufferFileNum,
					     nextEntryOffset);
		if (DbLsn.compareTo(nextLsn, finishLsn) >= 0) {
		    throw new EOFException();
		}
	    }
	}
    }

    /**
     * Read the log entry header, leaving the buffer mark at the beginning of
     * the checksummed header data.
     */
    private void readHeader(ByteBuffer dataBuffer) 
        throws DatabaseException  {

        /* Get the checksum for this log entry. */
        currentEntryChecksum = LogUtils.getUnsignedInt(dataBuffer);
        dataBuffer.mark();

        /* Read the log entry header. */
        currentEntryTypeNum = dataBuffer.get();

        /*
         * Always validate the entry type, since this check is cheap.  Throw a
         * DbChecksumException so that LastFileReader and others will recognize
         * this as data corruption.
         */
        if (!LogEntryType.isValidType(currentEntryTypeNum))
            throw new DbChecksumException
		((anticipateChecksumErrors ? null : env),
                 "FileReader read invalid log entry type: " +
                 currentEntryTypeNum);

        currentEntryTypeVersion = dataBuffer.get();
        currentEntryPrevOffset = LogUtils.getUnsignedInt(dataBuffer);
        currentEntrySize = LogUtils.readInt(dataBuffer);
    }

    /**
     * Reset the checksum and add the header bytes.  This method must be called
     * with the entry header data at the buffer mark.
     */
    private void startChecksum(ByteBuffer dataBuffer)
        throws DatabaseException {

        /* Move back up to the beginning of the cksum covered header. */
        cksumValidator.reset();
        int entryStart = threadSafeBufferPosition(dataBuffer);
        dataBuffer.reset();
        cksumValidator.update(env, dataBuffer,
                              LogManager.HEADER_CONTENT_BYTES,
                              anticipateChecksumErrors);
        
        /* Move the data buffer back to where the log entry starts. */
        threadSafeBufferPosition(dataBuffer, entryStart);
    }

    /**
     * Add the entry bytes to the checksum and check the value.  This method
     * must be called with the buffer positioned at the start of the entry.
     */
    private void validateChecksum(ByteBuffer entryBuffer)
        throws DatabaseException {

        cksumValidator.update(env, entryBuffer, currentEntrySize,
			      anticipateChecksumErrors);
        cksumValidator.validate(env, currentEntryChecksum,
				readBufferFileNum, currentEntryOffset,
				anticipateChecksumErrors);
    }

    /**
     * Try to read a specified number of bytes. 
     * @param amountToRead is the number of bytes we need
     * @param collectData is true if we need to actually look at the data. 
     *  If false, we know we're skipping this entry, and all we need to
     *  do is to count until we get to the right spot.
     * @return a byte buffer positioned at the head of the desired portion, 
     * or null if we reached eof.
     */
    private ByteBuffer readData(int amountToRead, boolean collectData)
        throws IOException, DatabaseException, EOFException {

        int alreadyRead = 0;
        ByteBuffer completeBuffer = null;
        saveBuffer.clear();

        while ((alreadyRead < amountToRead) && !eof) {
            
            int bytesNeeded = amountToRead - alreadyRead;
            if (readBuffer.hasRemaining()) {

                /* There's data in the read buffer, process it. */
                if (collectData) {
                    /*
                     * Save data in a buffer for processing.
                     */
                    if ((alreadyRead > 0) ||
                        (readBuffer.remaining() < bytesNeeded)) {

                        /* We need to piece an entry together. */

                        copyToSaveBuffer(bytesNeeded);
                        alreadyRead = threadSafeBufferPosition(saveBuffer);
                        completeBuffer = saveBuffer;
                    } else {

                        /* A complete entry is available in this buffer. */

                        completeBuffer = readBuffer;
                        alreadyRead = amountToRead;
                    }
                } else {
                    /*
                     * No need to save data, just move buffer positions.
                     */
                    int positionIncrement = 
                        (readBuffer.remaining() > bytesNeeded) ?
                        bytesNeeded : readBuffer.remaining();

                    alreadyRead += positionIncrement;
		    threadSafeBufferPosition
			(readBuffer,
			 threadSafeBufferPosition(readBuffer) +
			 positionIncrement);
                    completeBuffer = readBuffer;
                }
            } else {
                /*
                 * Look for more data.
                 */
                fillReadBuffer(bytesNeeded);
            }
        }

        /* Flip the save buffer just in case we've been accumulating in it. */
        threadSafeBufferFlip(saveBuffer);

        return completeBuffer;
    }

    /**
     * Change the read buffer size if we start hitting large log
     * entries so we don't get into an expensive cycle of multiple reads
     * and piecing together of log entries.
     */
    private void adjustReadBufferSize(int amountToRead) {
        int readBufferSize = readBuffer.capacity();
        /* We need to read something larger than the current buffer size. */
        if (amountToRead > readBufferSize) {
            /* We're not at the max yet. */
            if (readBufferSize < maxReadBufferSize) {

                /* 
                 * Make the buffer the minimum of amountToRead or a
                 * maxReadBufferSize.
                 */
                if (amountToRead < maxReadBufferSize) {
                    readBufferSize = amountToRead;
                    /* Make it a modulo of 1K */
                    int remainder = readBufferSize % 1024;
                    readBufferSize += 1024 - remainder;
                    readBufferSize = Math.min(readBufferSize, 
                    		          maxReadBufferSize);
                } else {
                    readBufferSize = maxReadBufferSize;
                }
                readBuffer = ByteBuffer.allocate(readBufferSize);
            }
            
            if (amountToRead > readBuffer.capacity()) {
                nRepeatIteratorReads++;
            }
        }
    }

    /**
     * Copy the required number of bytes into the save buffer.
     */
    private void copyToSaveBuffer(int bytesNeeded) {
        /* How much can we get from this current read buffer? */
        int bytesFromThisBuffer;

        if (bytesNeeded <= readBuffer.remaining()) {
            bytesFromThisBuffer = bytesNeeded;
        } else {
            bytesFromThisBuffer = readBuffer.remaining();
        }
                
        /* Gather it all into this save buffer. */
        ByteBuffer temp;

        /* Make sure the save buffer is big enough. */
        if (saveBuffer.capacity() - threadSafeBufferPosition(saveBuffer) <
            bytesFromThisBuffer) {
            /* Grow the save buffer. */
            temp = ByteBuffer.allocate(saveBuffer.capacity() +
                                          bytesFromThisBuffer);
            threadSafeBufferFlip(saveBuffer);
            temp.put(saveBuffer);
            saveBuffer = temp;
        }

        /*
         * Bulk copy only the required section from the read buffer into the
         * save buffer. We need from readBuffer.position() to
         * readBuffer.position() + bytesFromThisBuffer
         */
        temp = readBuffer.slice();
        temp.limit(bytesFromThisBuffer);
        saveBuffer.put(temp);
	threadSafeBufferPosition(readBuffer,
				 threadSafeBufferPosition(readBuffer) +
				 bytesFromThisBuffer);
    }

    /**
     * Fill up the read buffer with more data.
     */
    private void fillReadBuffer(int bytesNeeded)
	throws DatabaseException, EOFException {

        FileHandle fileHandle = null;
        try {
            adjustReadBufferSize(bytesNeeded);

            /* Get a file handle to read in more log. */
            fileHandle = fileManager.getFileHandle(readBufferFileNum); 
            boolean fileOk = false;

            /* 
             * Check to see if we've come to the end of the file.  If so, get
             * the next file.
             */
            if (readBufferFileEnd < fileHandle.getFile().length()) {
                fileOk = true;
            } else {
                /* This file is done -- can we read in the next file? */
                if (!singleFile) {
                    Long nextFile =
                        fileManager.getFollowingFileNum(readBufferFileNum,
                                                        forward);
                    if (nextFile != null) {
                        readBufferFileNum = nextFile.longValue();
                        fileHandle.release();
                        fileHandle =
                            fileManager.getFileHandle(readBufferFileNum); 
                        fileOk = true;
                        readBufferFileEnd = 0;
                        nextEntryOffset = 0;
                    }
                } 
            }

            if (fileOk) {
                readBuffer.clear();
		fileManager.readFromFile(fileHandle.getFile(), readBuffer,
                                         readBufferFileEnd);

		assert EnvironmentImpl.maybeForceYield();

                readBufferFileStart = readBufferFileEnd;
                readBufferFileEnd =
		    readBufferFileStart + threadSafeBufferPosition(readBuffer);
                threadSafeBufferFlip(readBuffer);
            } else {
                throw new EOFException();
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new DatabaseException
		("Problem in fillReadBuffer, readBufferFileNum = " + 
		 readBufferFileNum + ": " + e.getMessage());

        } finally {
            if (fileHandle != null) {
                fileHandle.release();
            }
        }
    }

    /** 
     * @return true if this reader should process this entry, or just
     * skip over it.
     */
    protected boolean isTargetEntry(byte logEntryTypeNumber,
                                    byte logEntryTypeVersion)
        throws DatabaseException {

        return true;
    }

    /**
     * Each file reader implements this method to process the entry data.
     * @param enteryBuffer contains the entry data and is positioned at the
     * data
     * @return true if this entry should be returned
     */
    protected abstract boolean processEntry(ByteBuffer entryBuffer)
        throws DatabaseException;

    private static class EOFException extends Exception {
    }

    /**
     * Note that we catch Exception here because it is possible that another
     * thread is modifying the state of buffer simultaneously.  Specifically,
     * this can happen if another thread is writing this log buffer out and it
     * does (e.g.) a flip operation on it.  The actual mark/pos of the buffer
     * may be caught in an unpredictable state.  We could add another latch to
     * protect this buffer, but that's heavier weight than we need.  So the
     * easiest thing to do is to just retry the duplicate operation.  See
     * [#9822].
     */
    private Buffer threadSafeBufferFlip(ByteBuffer buffer) {
	while (true) {
	    try {
		return buffer.flip();
	    } catch (IllegalArgumentException IAE) {
		continue;
	    }
	}
    }

    private int threadSafeBufferPosition(ByteBuffer buffer) {
	while (true) {
	    try {
		return buffer.position();
	    } catch (IllegalArgumentException IAE) {
		continue;
	    }
	}
    }

    private Buffer threadSafeBufferPosition(ByteBuffer buffer,
					    int newPosition) {
	while (true) {
	    try {
		return buffer.position(newPosition);
	    } catch (IllegalArgumentException IAE) {
		continue;
	    }
	}
    }
}
