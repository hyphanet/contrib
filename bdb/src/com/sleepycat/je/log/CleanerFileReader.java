/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: CleanerFileReader.java,v 1.34.2.2 2007/03/08 22:32:54 mark Exp $
 */

package com.sleepycat.je.log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.dbi.DatabaseId;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.log.entry.INLogEntry;
import com.sleepycat.je.log.entry.LNLogEntry;
import com.sleepycat.je.log.entry.LogEntry;
import com.sleepycat.je.tree.IN;
import com.sleepycat.je.tree.LN;
import com.sleepycat.je.utilint.DbLsn;

/**
 * CleanerFileReader scans log files for INs and LNs.
 */
public class CleanerFileReader extends FileReader {
    private static final byte IS_IN = 0;
    private static final byte IS_LN = 1;
    private static final byte IS_ROOT = 2;
    private static final byte IS_FILEHEADER = 3;

    private Map targetEntryMap;
    private LogEntry targetLogEntry;
    private byte targetCategory;

    /**
     * Create this reader to start at a given LSN.
     * @param env The relevant EnvironmentImpl.
     * @param readBufferSize buffer size in bytes for reading in log.
     * @param startLsn where to start in the log, or null for the beginning.
     * @param fileNum single file number.
     */
    public CleanerFileReader(EnvironmentImpl env,
                             int readBufferSize, 
                             long startLsn,
                             Long fileNum)
        throws IOException, DatabaseException {

        super(env,
              readBufferSize,
              true,                     // forward
              startLsn,
              fileNum,                  // single file number
              DbLsn.NULL_LSN,           // endOfFileLsn
              DbLsn.NULL_LSN);          // finishLsn

        targetEntryMap = new HashMap();

        addTargetType(IS_LN, LogEntryType.LOG_LN_TRANSACTIONAL);
        addTargetType(IS_LN, LogEntryType.LOG_LN);
        addTargetType(IS_LN, LogEntryType.LOG_NAMELN_TRANSACTIONAL);
        addTargetType(IS_LN, LogEntryType.LOG_NAMELN);
        addTargetType(IS_LN, LogEntryType.LOG_MAPLN_TRANSACTIONAL);
        addTargetType(IS_LN, LogEntryType.LOG_MAPLN);
        addTargetType(IS_LN, LogEntryType.LOG_DEL_DUPLN_TRANSACTIONAL);
        addTargetType(IS_LN, LogEntryType.LOG_DEL_DUPLN);
        addTargetType(IS_LN, LogEntryType.LOG_DUPCOUNTLN_TRANSACTIONAL);
        addTargetType(IS_LN, LogEntryType.LOG_DUPCOUNTLN);
        addTargetType(IS_LN, LogEntryType.LOG_FILESUMMARYLN);
        addTargetType(IS_IN, LogEntryType.LOG_IN);
        addTargetType(IS_IN, LogEntryType.LOG_BIN);
        addTargetType(IS_IN, LogEntryType.LOG_DIN);
        addTargetType(IS_IN, LogEntryType.LOG_DBIN);
        addTargetType(IS_ROOT, LogEntryType.LOG_ROOT);
        addTargetType(IS_FILEHEADER, LogEntryType.LOG_FILE_HEADER);
    }

    private void addTargetType(byte category, LogEntryType entryType)
        throws DatabaseException {

        targetEntryMap.put(entryType,
                           new EntryInfo(entryType.getNewLogEntry(),
					 category));
    }

    /**
     * Helper for determining the starting position and opening
     * up a file at the desired location.
     */
    protected void initStartingPosition(long endOfFileLsn,
                                        Long fileNum)
        throws IOException, DatabaseException {

        eof = false;

        /*
         * Start off at the startLsn. If that's null, start at the
         * beginning of the log. If there are no log files, set
         * eof.
         */
        readBufferFileNum = fileNum.longValue();
        readBufferFileEnd = 0;

        /* 
         * After we read the first entry, the currentEntry will
         * point here.
         */
        nextEntryOffset = readBufferFileEnd;
    }

    /** 
     * @return true if this is a type we're interested in.
     */
    protected boolean isTargetEntry(byte entryTypeNum,
                                    byte entryTypeVersion) {

        LogEntryType fromLogType = new LogEntryType(entryTypeNum,
						    entryTypeVersion);
                                                            
        /* Is it a target entry? */
        EntryInfo info = (EntryInfo) targetEntryMap.get(fromLogType);
        if (info == null) {
            return false;
        } else {
            targetCategory = info.targetCategory;
            targetLogEntry = info.targetLogEntry;
            return true;
        }
    }

    /**
     * This reader instantiates an LN and key for every LN entry.
     */
    protected boolean processEntry(ByteBuffer entryBuffer)
        throws DatabaseException {

        readEntry(targetLogEntry, entryBuffer, true); // readFullItem
        return true;
    }

    /**
     * @return true if the last entry was an IN.
     */
    public boolean isIN() {
        return (targetCategory == IS_IN);
    }

    /**
     * @return true if the last entry was a LN.
     */
    public boolean isLN() {
        return (targetCategory == IS_LN);
    }

    /**
     * @return true if the last entry was a root
     */
    public boolean isRoot() {
        return (targetCategory == IS_ROOT);
    }

    public boolean isFileHeader() {
        return (targetCategory == IS_FILEHEADER);
    }

    /**
     * Get the last LN seen by the reader.
     */
    public LN getLN() {
        return ((LNLogEntry) targetLogEntry).getLN();
    }

    /**
     * Get the last entry seen by the reader as an IN.
     */
    public IN getIN() 
        throws DatabaseException {

        return ((INLogEntry) targetLogEntry).getIN(envImpl);
    }

    public FileHeader getFileHeader() 
        throws DatabaseException {

        return (FileHeader) (targetLogEntry.getMainItem());
    }

    /**
     * Get the last databaseId seen by the reader.
     */
    public DatabaseId getDatabaseId() {
        if (targetCategory == IS_LN) {
            return ((LNLogEntry) targetLogEntry).getDbId(); 
        } else if (targetCategory == IS_IN) {
            return ((INLogEntry) targetLogEntry).getDbId(); 
        } else {
	    return null;
        }
    }

    /**
     * Get the last key seen by the reader.
     */
    public byte[] getKey() {
        return ((LNLogEntry) targetLogEntry).getKey();
    }

    /**
     * Get the last key seen by the reader.
     */
    public byte[] getDupTreeKey() {
        return ((LNLogEntry) targetLogEntry).getDupKey();
    }

    private static class EntryInfo {
        public LogEntry targetLogEntry;
        public byte     targetCategory;

        EntryInfo(LogEntry targetLogEntry, byte targetCategory) {
            this.targetLogEntry = targetLogEntry;
            this.targetCategory = targetCategory;
        }
    }
}
