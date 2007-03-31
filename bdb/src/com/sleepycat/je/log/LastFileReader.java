/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: LastFileReader.java,v 1.48.2.1 2007/02/01 14:49:47 cwl Exp $
 */

package com.sleepycat.je.log;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.utilint.DbLsn;
import com.sleepycat.je.utilint.Tracer;

/**
 * LastFileReader traverses the last log file, doing checksums and looking for
 * the end of the log. Different log types can be registered with it and it
 * will remember the last occurrence of targetted entry types.
 */
public class LastFileReader extends FileReader {

    /* Log entry types to track. */
    private Set trackableEntries;

    private long nextUnprovenOffset;
    private long lastValidOffset;
    private LogEntryType entryType;

    /* 
     * Last lsn seen for tracked types. Key = LogEntryType, data is the offset
     * (Long).
     */
    private Map lastOffsetSeen;

    /**
     * This file reader is always positioned at the last file.
     */
    public LastFileReader(EnvironmentImpl env,
                          int readBufferSize)
        throws IOException, DatabaseException {

        super(env, readBufferSize, true,  DbLsn.NULL_LSN, new Long(-1),
	      DbLsn.NULL_LSN, DbLsn.NULL_LSN);

        trackableEntries = new HashSet();
        lastOffsetSeen = new HashMap();

        lastValidOffset = 0;
	anticipateChecksumErrors = true;
        nextUnprovenOffset = nextEntryOffset;
    }

    /**
     * Ctor which allows passing in the file number we want to read to the end
     * of.  This is used by the ScavengerFileReader when it encounters a bad
     * log record in the middle of a file.
     */
    public LastFileReader(EnvironmentImpl env,
                          int readBufferSize,
			  Long specificFileNumber)
        throws IOException, DatabaseException {

        super(env, readBufferSize, true,  DbLsn.NULL_LSN,
              specificFileNumber, DbLsn.NULL_LSN, DbLsn.NULL_LSN);

        trackableEntries = new HashSet();
        lastOffsetSeen = new HashMap();

        lastValidOffset = 0;
	anticipateChecksumErrors = true;
        nextUnprovenOffset = nextEntryOffset;
    }

    /**
     * Override so that we always start at the last file.
     */
    protected void initStartingPosition(long endOfFileLsn,
					Long singleFileNum)
        throws IOException, DatabaseException {

        eof = false;

        /*
         * Start at what seems like the last file. If it doesn't exist, we're
         * done.
         */
        Long lastNum = ((singleFileNum != null) &&
			(singleFileNum.longValue() >= 0)) ?
	    singleFileNum :
	    fileManager.getLastFileNum();
        FileHandle fileHandle = null;
        readBufferFileEnd = 0;

        long fileLen = 0;
        while ((fileHandle == null) && !eof) {
            if (lastNum == null) {
                eof = true;
            } else {
                try {
                    readBufferFileNum = lastNum.longValue();
                    fileHandle = fileManager.getFileHandle(readBufferFileNum);

                    /*
                     * Check the size of this file. If it opened successfully
                     * but only held a header or is 0 length, backup to the
                     * next "last" file unless this is the only file in the
                     * log. Note that an incomplete header will end up throwing
                     * a checksum exception, but a 0 length file will open
                     * successfully in read only mode.
                     */
                    fileLen = fileHandle.getFile().length();
                    if (fileLen <= FileManager.firstLogEntryOffset()) {
                        lastNum = fileManager.getFollowingFileNum
			    (lastNum.longValue(), false);
                        if (lastNum != null) {
                            fileHandle.release();
                            fileHandle = null;
                        }
                    }
                } catch (DatabaseException e) {
                    lastNum = attemptToMoveBadFile(e);
                    fileHandle = null;
                } finally {
                    if (fileHandle != null) {
                        fileHandle.release();
                    }
                }
            }
        } 

        nextEntryOffset = 0;
    }

    /**
     * Something is wrong with this file. If there is no data in this file (the
     * header is <= the file header size) then move this last file aside and
     * search the next "last" file. If the last file does have data in it,
     * throw an exception back to the application, since we're not sure what to
     * do now.
     */
    private Long attemptToMoveBadFile(DatabaseException origException)
        throws DatabaseException, IOException {

        String fileName = fileManager.getFullFileNames(readBufferFileNum)[0];
        File problemFile = new File(fileName);
        Long lastNum = null;

        if (problemFile.length() <= FileManager.firstLogEntryOffset()) {
            fileManager.clear(); // close all existing files
            /* Move this file aside. */
            lastNum = fileManager.getFollowingFileNum(readBufferFileNum,
                                                      false);
            fileManager.renameFile(readBufferFileNum, 
                                   FileManager.BAD_SUFFIX);

        } else {
            /* There's data in this file, throw up to the app. */
            throw origException;
        }
        return lastNum;
    }

    public void setEndOfFile() 
        throws IOException, DatabaseException  {

        fileManager.truncateLog(readBufferFileNum, nextUnprovenOffset);
    }

    /**
     * @return The LSN to be used for the next log entry.
     */
    public long getEndOfLog() {
        return DbLsn.makeLsn(readBufferFileNum, nextUnprovenOffset);
    }

    public long getLastValidLsn() {
        return DbLsn.makeLsn(readBufferFileNum, lastValidOffset);
    }

    public long getPrevOffset() {
        return lastValidOffset;
    }

    public  LogEntryType getEntryType() {
        return entryType;
    }

    /**
     * Tell the reader that we are interested in these kind of entries.
     */
    public void setTargetType(LogEntryType type) {
        trackableEntries.add(type);
    }

    /**
     * @return The last LSN seen in the log for this kind of entry, or null.
     */
    public long getLastSeen(LogEntryType type) {
        Long typeNumber =(Long) lastOffsetSeen.get(type);
        if (typeNumber != null) {
            return DbLsn.makeLsn(readBufferFileNum, typeNumber.longValue());
        } else {
            return DbLsn.NULL_LSN;
        }
    }

    /**
     * Validate the checksum on each entry, see if we should remember the LSN
     * of this entry.
     */
    protected boolean processEntry(ByteBuffer entryBuffer) {

        /* Skip over the data, we're not doing anything with it. */
        entryBuffer.position(entryBuffer.position() + currentEntryHeader.getItemSize());

        /* If we're supposed to remember this lsn, record it. */
        entryType = new LogEntryType(currentEntryHeader.getType(),
                                     currentEntryHeader.getVersion());
        if (trackableEntries.contains(entryType)) {
            lastOffsetSeen.put(entryType, new Long(currentEntryOffset));
        }

        return true;
    }

    /**
     * readNextEntry will stop at a bad entry.
     * @return true if an element has been read.
     */
    public boolean readNextEntry()
        throws DatabaseException, IOException {

        boolean foundEntry = false;

        try {

            /*
             * At this point, 
             *  currentEntryOffset is the entry we just read.
             *  nextEntryOffset is the entry we're about to read.
             *  currentEntryPrevOffset is 2 entries ago.
             * Note that readNextEntry() moves all the offset pointers up.
             */

            foundEntry = super.readNextEntry();


            /*
             * Note that initStartingPosition() makes sure that the file header
             * entry is valid.  So by the time we get to this method, we know
             * we're at a file with a valid file header entry.
             */
            lastValidOffset = currentEntryOffset;
            nextUnprovenOffset = nextEntryOffset;
        } catch (DbChecksumException e) {
            Tracer.trace(Level.INFO,
                         envImpl, "Found checksum exception while searching " +
                         " for end of log. Last valid entry is at " +
                         DbLsn.toString
			 (DbLsn.makeLsn(readBufferFileNum, lastValidOffset)) +
                         " Bad entry is at " +
                         DbLsn.makeLsn(readBufferFileNum, nextUnprovenOffset));
        }
        return foundEntry;
    }
}
