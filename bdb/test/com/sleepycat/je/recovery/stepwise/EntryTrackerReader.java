/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: EntryTrackerReader.java,v 1.5.2.1 2007/02/01 14:50:18 cwl Exp $
 */

package com.sleepycat.je.recovery.stepwise;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import com.sleepycat.bind.tuple.IntegerBinding;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.log.FileReader;
import com.sleepycat.je.log.LogEntryType;
import com.sleepycat.je.log.entry.DeletedDupLNLogEntry;
import com.sleepycat.je.log.entry.LNLogEntry;
import com.sleepycat.je.log.entry.LogEntry;
import com.sleepycat.je.txn.TxnCommit;
import com.sleepycat.je.utilint.DbLsn;

/**
 * EntryTrackerReader collects a list of EntryInfo describing all log entries
 * in the truncated portion of a log.  It lets the test know where to do a log
 * truncation and remembers whether an inserted or deleted record was seen, in
 * order to update the test's set of expected records. 
 */
public class EntryTrackerReader extends FileReader {

    /* 
     * EntryInfo is a list that corresponds to each entry in the truncated
     * area of the log. 
     */
    private List entryInfo;
    private DatabaseEntry dbt = new DatabaseEntry();
    private LogEntry useLogEntry;
    private LogEntryType useLogEntryType;
    private boolean isCommit;

    /**
     * Create this reader to start at a given LSN.
     */
    public EntryTrackerReader(EnvironmentImpl env,
                              long startLsn,
                              List entryInfo) // EntryInfo
	throws IOException, DatabaseException {

        super(env, 2000, true, startLsn, null,
	      -1, DbLsn.NULL_LSN);

        this.entryInfo = entryInfo;
    }

    /** 
     * @return true if this is a targeted entry that should be processed.
     */
    protected boolean isTargetEntry(byte logEntryTypeNumber,
                                    byte logEntryTypeVersion) {
        isCommit = false;
        boolean targeted = true;

        useLogEntryType = null;
        
        if (LogEntryType.LOG_LN.equalsType(logEntryTypeNumber)) {
            useLogEntryType = LogEntryType.LOG_LN;
        } else if (LogEntryType.LOG_LN_TRANSACTIONAL.equalsType(
                                                        logEntryTypeNumber)) {
            useLogEntryType = LogEntryType.LOG_LN_TRANSACTIONAL;
        } else if (LogEntryType.LOG_DEL_DUPLN.equalsType(logEntryTypeNumber)) {
            useLogEntryType = LogEntryType.LOG_DEL_DUPLN;
        } else if (LogEntryType.LOG_DEL_DUPLN_TRANSACTIONAL.equalsType(
                                                     logEntryTypeNumber)) {
            useLogEntryType = LogEntryType.LOG_DEL_DUPLN_TRANSACTIONAL;
        } else if (LogEntryType.LOG_TXN_COMMIT.equalsType(logEntryTypeNumber)) {
            useLogEntryType = LogEntryType.LOG_TXN_COMMIT;
            isCommit = true;
        } else {
            /* 
             * Just make note, no need to process the entry, nothing to record
             * besides the LSN. Note that the offset has not been bumped by 
             * the FileReader, so use nextEntryOffset.
             */
            entryInfo.add(new LogEntryInfo(DbLsn.makeLsn(readBufferFileNum,
                                                         nextEntryOffset),
                                           0, 0));
            targeted = false;
        }

        if (useLogEntryType != null) {
            useLogEntry = useLogEntryType.getSharedLogEntry();
        }
        return targeted;
    }


    /**
     * This log entry has data which affects the expected set of records.
     * We need to save each lsn and determine whether the value of the
     * log entry should affect the expected set of records. For 
     * non-transactional entries, the expected set is affected right away.
     * For transactional entries, we defer updates of the expected set until
     * a commit is seen.
     */
    protected boolean processEntry(ByteBuffer entryBuffer)
        throws DatabaseException {

        /* 
         * Note that the offset has been bumped, so use currentEntryOffset 
         * for the LSN. 
         */
        long lsn = DbLsn.makeLsn(readBufferFileNum, currentEntryOffset);
        useLogEntry.readEntry(currentEntryHeader,
                              entryBuffer, 
                              true); // readFullItem

        boolean isTxnal = useLogEntryType.isTransactional();
        long txnId = useLogEntry.getTransactionId();

        if (isCommit) {
            
            /* 
             * The txn id in a single item log entry is embedded within
             * the item.
             */
            txnId = ((TxnCommit) useLogEntry.getMainItem()).getId();
            entryInfo.add(new CommitEntry(lsn, txnId));
        } else if (useLogEntry instanceof DeletedDupLNLogEntry) {

            /* This log entry is a deleted dup LN. */
            DeletedDupLNLogEntry delDupLogEntry =
                (DeletedDupLNLogEntry) useLogEntry;
            dbt.setData(delDupLogEntry.getKey());
            int keyValue = IntegerBinding.entryToInt(dbt);
            dbt.setData(delDupLogEntry.getDupKey());
            int dataValue = IntegerBinding.entryToInt(dbt);

            if (isTxnal) {
                entryInfo.add(new TxnalDeletedEntry(lsn, keyValue,
                                                    dataValue, txnId));
            } else {
                entryInfo.add(new NonTxnalDeletedEntry(lsn, keyValue,
                                                       dataValue));
            }
        } else {
            LNLogEntry lnLogEntry = (LNLogEntry) useLogEntry;
            byte [] keyArray = lnLogEntry.getKey();
            dbt.setData(keyArray);
            int keyValue = IntegerBinding.entryToInt(dbt);
            byte [] dataArray = lnLogEntry.getLN().getData();

            if (dataArray == null) {
                /* This log entry is a deleted, non-dup LN. */
                if (isTxnal) {
                    entryInfo.add(new TxnalDeletedEntry(lsn, keyValue, -1,
                                                        txnId));
                } else {
                    entryInfo.add(new NonTxnalDeletedEntry(lsn, keyValue, -1));
                }
            } else {
                /* This log entry is new LN. */
                dbt.setData(dataArray);
                int dataValue = IntegerBinding.entryToInt(dbt);
                if (isTxnal) {
                    entryInfo.add(new TxnalEntry(lsn, keyValue, dataValue,
                                                 txnId));
                } else {
                    entryInfo.add(new NonTxnalEntry(lsn, keyValue, dataValue));
                }
            }
        }

        return true;
    }
}
