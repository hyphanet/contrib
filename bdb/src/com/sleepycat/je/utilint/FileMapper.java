/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: FileMapper.java,v 1.9 2008/06/27 18:30:33 linda Exp $
 */

package com.sleepycat.je.utilint;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.sleepycat.bind.tuple.LongBinding;
import com.sleepycat.bind.tuple.TupleBinding;
import com.sleepycat.bind.tuple.TupleInput;
import com.sleepycat.bind.tuple.TupleOutput;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.log.LogEntryType;

/**
 * A FileMapper instance represents the VLSN->LSN mappings for a single log
 * file. There are persistent FileMappers that are stored in the log, and
 * temporary instances that are used for collecting mappings found in the
 * log during recovery.
 *
 * Note that we only need to store the file offset portion of the lsn
 * persistently on disk, because the file number is self evident. We still need
 * to use longs in memory to represent the offset, since the file offset is an
 * unsigned int.
 */
public class FileMapper {

    /* File number for target file. */
    private long fileNumber;

    /* 
     * The last VLSN in this file that can be a sync matchpoint. Used at
     * recovery time, to transfer information from the recovery scan of the log
     * to initialize the VLSNIndex.
     */
    private VLSN lastSyncVLSN;

    /*
     * The last VLSN in this file which is a replicated commit record. Akin
     * to lastSyncVLSN, but used specifically to determine if a syncup is
     * rolling back past a committed txn, and therefore whether the syncup
     * needs to be a hard recovery, or can just be a soft partial rollback.
     */
    private VLSN lastCommitVLSN;

    /*
     * The file offset is really an unsigned int on disk, but must be
     * represented as a long in Java.
     */
    private Map<Long,Long> vlsnToFileOffsetMap;

    /*
     * True if there are changes to vlsnToFileOffsetMap that are not on
     * disk.
     */
    private boolean dirty;

    public FileMapper(long fileNumber) {
        this.fileNumber = fileNumber;
        this.vlsnToFileOffsetMap = new HashMap<Long,Long>();
        lastSyncVLSN = VLSN.NULL_VLSN;
        lastCommitVLSN = VLSN.NULL_VLSN;
    }

    /* For reading from disk */
    private FileMapper() {
    }

    public void setFileNumber(long fileNumber) {
        this.fileNumber = fileNumber;
    }

    public long getFileNumber() {
        return fileNumber;
    }

    public VLSN getLastSyncVLSN() {
        return lastSyncVLSN;
    }

    public VLSN getLastCommitVLSN() {
        return lastCommitVLSN;
    }

    public void writeToDatabase(Database fileMapperDb)
        throws DatabaseException {

        if (dirty) {
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry data = new DatabaseEntry();

            LongBinding.longToEntry(fileNumber, key);
            FileMapperBinding mapperBinding =
                new FileMapperBinding();
            mapperBinding.objectToEntry(this, data);
            OperationStatus status = fileMapperDb.put(null, key, data);
            if (status != OperationStatus.SUCCESS) {
                throw new DatabaseException
                    ("Unable to write VLSN mapping "+
                     " for file " + fileNumber +
                     " status=" + status);
            }
            dirty = false;
        }
    }

    /*
     * Initialize this from the database. Assumes that there are no
     * mappings currently stored.
     */
    public static FileMapper readFromDatabase(DatabaseEntry data) {
        FileMapperBinding mapperBinding = new FileMapperBinding();
        FileMapper mapper = (FileMapper)mapperBinding.entryToObject(data);
        return mapper;
    }

    /** Record the LSN location for this VLSN. */
    public void putLSN(long vlsn, 
                       long lsn, 
                       LogEntryType entryType) {

        assert DbLsn.getFileNumber(lsn) == fileNumber:
            "unexpected lsn file num=" +  DbLsn.getFileNumber(lsn) +
            " while file mapper file number=" + fileNumber;

        vlsnToFileOffsetMap.put(vlsn, DbLsn.getFileOffset(lsn));
        if (entryType.isSyncPoint()) {
            VLSN thisVLSN = new VLSN(vlsn);
            if (lastSyncVLSN.compareTo(thisVLSN) < 0) {
                lastSyncVLSN = thisVLSN;
            }
        }

        if (LogEntryType.LOG_TXN_COMMIT.equals(entryType)) {
            VLSN thisVLSN = new VLSN(vlsn);
            if (lastCommitVLSN.compareTo(thisVLSN) < 0) {
                lastCommitVLSN = thisVLSN;
            }
        }

        dirty = true;
    }

    /**
     * Put all the VLSN->LSN mappings in the file mapper parameter into this
     * one.
     */
    public void putAll(FileMapper other) {
        assert other.fileNumber == fileNumber : "bad file number = " +
            other.fileNumber;
        vlsnToFileOffsetMap.putAll(other.vlsnToFileOffsetMap);

        if (lastSyncVLSN.compareTo(other.lastSyncVLSN) < 0) {
            lastSyncVLSN = other.lastSyncVLSN;
        }

        if (lastCommitVLSN.compareTo(other.lastCommitVLSN) < 0) {
            lastCommitVLSN = other.lastCommitVLSN;
        }

        dirty = true;
    }

    /* Retrieve the LSN location for this VLSN. */
    public long getLSN(long vlsn) {
        return DbLsn.makeLsn(fileNumber, vlsnToFileOffsetMap.get(vlsn));
    }

    /**
     * Individual mappings are removed if this VLSN is written more than
     * once to the log, as might happen on some kind of replay.
     */
    public void removeLSN(long vlsn) {
        vlsnToFileOffsetMap.remove(vlsn);
        dirty = true;
    }

    /**
     * Return the set of VLSNs in this mapper.
     */
    public Set<Long> getVLSNs() {
        return vlsnToFileOffsetMap.keySet();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("<FileMapper fileNumber=");
        sb.append(fileNumber).append(" ");
        sb.append(" lastSync=").append(lastSyncVLSN).append(" ");
        sb.append(" lastCommit=").append(lastCommitVLSN).append(" ");
        sb.append(vlsnToFileOffsetMap);
        sb.append("/>");
        return sb.toString();
    }

    /**
     * Marshals a FileMapper to a byte buffer to store in the database.
     * Doesn't persist the file number, because that's the key of the database.
     * TODO: use packed numbers for the map in HA release.
     */
    private static class FileMapperBinding extends TupleBinding<FileMapper> {

        public FileMapper entryToObject(TupleInput ti) {
            FileMapper mapper = new FileMapper();
            mapper.lastSyncVLSN = new VLSN(ti.readPackedLong());
            mapper.lastCommitVLSN = new VLSN(ti.readPackedLong());

            mapper.vlsnToFileOffsetMap = new HashMap<Long,Long>();
            int nEntries = ti.readInt();
            for (int i = 0; i < nEntries; i++) {
                long vlsnSeq = ti.readLong();
                long fileOffset = ti.readUnsignedInt();
                mapper.vlsnToFileOffsetMap.put(vlsnSeq, fileOffset);
            }
            return mapper;
        }

        public void objectToEntry(FileMapper mapper, TupleOutput to) {
            to.writePackedLong(mapper.lastSyncVLSN.getSequence());
            to.writePackedLong(mapper.lastCommitVLSN.getSequence());

            int nEntries = mapper.vlsnToFileOffsetMap.size();
            to.writeInt(nEntries);
            for (Map.Entry<Long,Long> entry : 
                     mapper.vlsnToFileOffsetMap.entrySet()) {
                to.writeLong(entry.getKey());
                to.writeUnsignedInt(entry.getValue());
            }
        }
    }
}

