/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: LogEntryType.java,v 1.76.2.1 2007/02/01 14:49:47 cwl Exp $
 */

package com.sleepycat.je.log;

import java.util.HashSet;
import java.util.Set;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.log.entry.BINDeltaLogEntry;
import com.sleepycat.je.log.entry.DeletedDupLNLogEntry;
import com.sleepycat.je.log.entry.INLogEntry;
import com.sleepycat.je.log.entry.LNLogEntry;
import com.sleepycat.je.log.entry.LogEntry;
import com.sleepycat.je.log.entry.SingleItemEntry;

/**
 * LogEntryType is an  enumeration of all log entry types. 
 *
 * <p>When adding a new version of a log entry type, make sure the
 * corresponding LogEntry instance is capable of reading in older versions from
 * the log. The LogEntry instance must be sure that older versions are
 * converted in memory into a correct instance of the newest version, so when
 * that LogEntry object is written again as the result of migration, eviction,
 * the resulting new log entry conforms to the requirements of the new version.
 * If context objects are required for data conversion, the conversion can be
 * done in the Node.postFetchInit method.</p>
 */
public class LogEntryType {

    /* w
     * Collection of log entry type classes, used to read the log.  Note that
     * this must be declared before any instances of LogEntryType, since the
     * constructor uses this map. Each statically defined LogEntryType should
     * register itself with this collection.
     */ 
    private static final int MAX_TYPE_NUM = 27;

    private static LogEntryType[] LOG_TYPES = new LogEntryType[MAX_TYPE_NUM];

    /*
     * Enumeration of log entry types. The log entry type represents the 2
     * byte field that starts every log entry. The top byte is the log type,
     * the bottom byte holds the version value, provisional bit, and 
     * replicated bit.
     *
     *  Log type (8 bits)
     * (Provisional (1 bit) Replicated (1 bit) Version (6 bits)
     *
     * The top byte (log type) identifies the type and can be used to
     * lookup the LogEntryType object, while the bottom byte has
     * information about the entry (instance) of this type.  The bottom
     * byte is effectively entry header information that is common to
     * all types and is managed by static methods in this class. 
     *
     * The provisional bit can be set for any log type in the log. It's an
     * indication to recovery that the entry shouldn't be processed when
     * rebuilding the tree. It's used to ensure the atomic logging of multiple
     * entries.
     *
     * The replicated bit should only be set for log types where 
     * isTypeReplicated is true. This means that this particular log entry
     * did get broadcast to the replication group, and bears a VLSN tag.
     */

    /*  Node types */
    public static final LogEntryType LOG_LN_TRANSACTIONAL =
        new LogEntryType((byte) 1, (byte) 0, "LN_TX",
                         new LNLogEntry(com.sleepycat.je.tree.LN.class),
                         true,  // isTransactional
                         true,  // marshallOutsideLatch
                         true); // isTypeReplicated

    public static final LogEntryType LOG_LN =
        new LogEntryType((byte) 2, (byte) 0, "LN",
                         new LNLogEntry(com.sleepycat.je.tree.LN.class),
                         false, // isTransactional
                         true,  // marshallOutsideLatch
                         false);// isTypeReplicated

    public static final LogEntryType LOG_MAPLN_TRANSACTIONAL =
        new LogEntryType((byte) 3, (byte) 2, "MapLN_TX",
                         new LNLogEntry(com.sleepycat.je.tree.MapLN.class),
                         true,  // isTransactional
                         true,  // marshallOutsideLatch
                         true); // isTypeReplicated

    public static final LogEntryType LOG_MAPLN =
        new LogEntryType((byte) 4, (byte) 2, "MapLN",
                         new LNLogEntry(com.sleepycat.je.tree.MapLN.class),
                         false, // isTransactional
                         true,  // marshallOutsideLatch
                         false);// isTypeReplicated

    public static final LogEntryType LOG_NAMELN_TRANSACTIONAL =
        new LogEntryType((byte) 5, (byte) 0, "NameLN_TX",
                         new LNLogEntry(com.sleepycat.je.tree.NameLN.class),
                         true,  // isTransactional
                         true,  // marshallOutsideLatch
                         true); // isTypeReplicated

    public static final LogEntryType LOG_NAMELN =
        new LogEntryType((byte) 6, (byte) 0, "NameLN",
                         new LNLogEntry(com.sleepycat.je.tree.NameLN.class),
                         false, // isTransactional
                         true,  // marshallOutsideLatch
                         false);// isTypeReplicated

    public static final LogEntryType LOG_DEL_DUPLN_TRANSACTIONAL =
        new LogEntryType((byte) 7, (byte) 0, "DelDupLN_TX",
                         new DeletedDupLNLogEntry(),
                         true,  // isTransactional
                         true,  // marshallOutsideLatch
                         true); // isTypeReplicated

    public static final LogEntryType LOG_DEL_DUPLN =
        new LogEntryType((byte) 8, (byte) 0, "DelDupLN",
                         new DeletedDupLNLogEntry(),
                         false, // isTransactional
                         true,  // marshallOutsideLatch
                         false);// isTypeReplicated

    public static final LogEntryType LOG_DUPCOUNTLN_TRANSACTIONAL =
        new LogEntryType((byte) 9, (byte) 0, "DupCountLN_TX",
                 new LNLogEntry(com.sleepycat.je.tree.DupCountLN.class),
                         true,  // isTransactional
                         true,  // marshallOutsideLatch
                         true); // isTypeReplicated

    public static final LogEntryType LOG_DUPCOUNTLN =
        new LogEntryType((byte) 10, (byte) 0, "DupCountLN",
                 new LNLogEntry(com.sleepycat.je.tree.DupCountLN.class),
                         false, // isTransactional
                         true,  // marshallOutsideLatch
                         false);// isTypeReplicated

    public static final LogEntryType LOG_FILESUMMARYLN =
        new LogEntryType((byte) 11, (byte) 3, "FileSummaryLN",
              new LNLogEntry(com.sleepycat.je.tree.FileSummaryLN.class),
                         false, // isTransactional
                         false, // marshallOutsideLatch
                         false);// isTypeReplicated

    public static final LogEntryType LOG_IN =
        new LogEntryType((byte) 12, (byte) 2, "IN",
                         new INLogEntry(com.sleepycat.je.tree.IN.class),
                         false, // isTransactional
                         true,  // marshallOutsideLatch
                         false);// isTypeReplicated

    public static final LogEntryType LOG_BIN =
        new LogEntryType((byte) 13, (byte) 2, "BIN",
                         new INLogEntry(com.sleepycat.je.tree.BIN.class),
                         false, // isTransactional
                         true,  // marshallOutsideLatch
                         false);// isTypeReplicated

    public static final LogEntryType LOG_DIN =
        new LogEntryType((byte) 14, (byte) 2, "DIN",
                         new INLogEntry(com.sleepycat.je.tree.DIN.class),
                         false, // isTransactional
                         true,  // marshallOutsideLatch
                         false);// isTypeReplicated

    public static final LogEntryType LOG_DBIN =
        new LogEntryType((byte) 15, (byte) 2, "DBIN",
                         new INLogEntry(com.sleepycat.je.tree.DBIN.class),
                         false, // isTransactional
                         true,  // marshallOutsideLatch
                         false);// isTypeReplicated

    public static final LogEntryType[] IN_TYPES = {
        LogEntryType.LOG_IN,
        LogEntryType.LOG_BIN,
        LogEntryType.LOG_DIN,
        LogEntryType.LOG_DBIN,
    };

    /*** If you add new types, be sure to update MAX_TYPE_NUM at the top.***/

    private static final int MAX_NODE_TYPE_NUM = 15;

    public static boolean isNodeType(byte typeNum, byte version) {
        return (typeNum <= MAX_NODE_TYPE_NUM);
    }

    /* Root */
    public static final LogEntryType LOG_ROOT =
        new LogEntryType((byte) 16, (byte) 1, "Root",
                         new SingleItemEntry
                         (com.sleepycat.je.dbi.DbTree.class),
                         false, // isTransactional
                         true,  // marshallOutsideLatch
                         false);// isTypeReplicated

    /* Transactional entries */
    public static final LogEntryType LOG_TXN_COMMIT =
        new LogEntryType((byte) 17, (byte) 0, "Commit",
                         new SingleItemEntry
                         (com.sleepycat.je.txn.TxnCommit.class),
                         true,  // isTransactional
                         true,  // marshallOutsideLatch
                         true); // isTypeReplicated

    public static final LogEntryType LOG_TXN_ABORT =
        new LogEntryType((byte) 18, (byte) 0, "Abort", 
                         new SingleItemEntry
                         (com.sleepycat.je.txn.TxnAbort.class),
                         true,  // isTransactional
                         true,  // marshallOutsideLatch
                         true); // isTypeReplicated

    public static final LogEntryType LOG_CKPT_START =
        new LogEntryType((byte) 19, (byte) 0, "CkptStart",
                         new SingleItemEntry
                         (com.sleepycat.je.recovery.CheckpointStart.class),
                         false, // isTransactional
                         true,  // marshallOutsideLatch
                         false);// isTypeReplicated

    public static final LogEntryType LOG_CKPT_END =
        new LogEntryType((byte) 20, (byte) 0, "CkptEnd",
                         new SingleItemEntry
                             (com.sleepycat.je.recovery.CheckpointEnd.class),
                         false, // isTransactional
                         true,  // marshallOutsideLatch
                         false);// isTypeReplicated

    public static final LogEntryType LOG_IN_DELETE_INFO =
        new LogEntryType((byte) 21, (byte) 0, "INDelete",
                         new SingleItemEntry
                             (com.sleepycat.je.tree.INDeleteInfo.class),
                         false, // isTransactional
                         true,  // marshallOutsideLatch
                         false);// isTypeReplicated

    public static final LogEntryType LOG_BIN_DELTA =
        new LogEntryType((byte) 22, (byte) 0, "BINDelta",
                         new BINDeltaLogEntry
                             (com.sleepycat.je.tree.BINDelta.class),
                         false, // isTransactional
                         true,  // marshallOutsideLatch
                         false);// isTypeReplicated
            
    public static final LogEntryType LOG_DUP_BIN_DELTA =
        new LogEntryType((byte) 23, (byte) 0, "DupBINDelta", 
                         new BINDeltaLogEntry
                         (com.sleepycat.je.tree.BINDelta.class),
                         false, // isTransactional
                         true,  // marshallOutsideLatch
                         false);// isTypeReplicated

    /* Administrative entries */
    public static final LogEntryType LOG_TRACE =
        new LogEntryType((byte) 24, (byte) 0, "Trace",
                         new SingleItemEntry
                         (com.sleepycat.je.utilint.Tracer.class),
                         false, // isTransactional
                         true,  // marshallOutsideLatch
                         false);// isTypeReplicated

    /* File header */
    public static final LogEntryType LOG_FILE_HEADER =
        new LogEntryType((byte) 25, (byte) 0, "FileHeader",
                         new SingleItemEntry
                         (com.sleepycat.je.log.FileHeader.class),
                         false, // isTransactional
                         true,  // marshallOutsideLatch
                         false);// isTypeReplicated

    public static final LogEntryType LOG_IN_DUPDELETE_INFO =
        new LogEntryType((byte) 26, (byte) 0, "INDupDelete",
                         new SingleItemEntry
                         (com.sleepycat.je.tree.INDupDeleteInfo.class),
                         false, // isTransactional
                         true,  // marshallOutsideLatch
                         false);// isTypeReplicated
    
    public static final LogEntryType LOG_TXN_PREPARE =
        new LogEntryType((byte) 27, (byte) 0, "Prepare",
                         new SingleItemEntry
                         (com.sleepycat.je.txn.TxnPrepare.class),
                         true,  // isTransactional
                         true,  // marshallOutsideLatch
                         false);// isTypeReplicated

    /*** If you add new types, be sure to update MAX_TYPE_NUM at the top.***/

    private static final byte PROVISIONAL_MASK = (byte) 0x80;
    private static final byte IGNORE_PROVISIONAL = ~PROVISIONAL_MASK;
    private static final byte REPLICATED_MASK = (byte) 0x40;
    private static final byte IGNORE_REPLICATED = ~REPLICATED_MASK;


    /* Persistent fields */
    private byte typeNum; // persistent value for this entry type
    private byte version; // persistent version and bit flags

    /* Transient fields */
    private String displayName;
    private LogEntry logEntry;

    /* If true, the log entry holds a transactional information. */
    private boolean isTransactional;
    /* If true, marshal this type of log entry outside log write latch */
    private boolean marshallOutsideLatch; 
    /* If true, replicate this type of log entry before logging. */
    private boolean isTypeReplicated; 

    /*
     * Constructors 
     */

    /** 
     * For base class support.
     */

    /* No log types can be defined outside this package. */
    LogEntryType(byte typeNum, byte version) {
        this.typeNum = typeNum;
        this.version = version;
    }

    /**
     * Create the static log types.
     * @param isTransactional true if this type of log entry holds data
     * involved in a transaction. For example, transaction commit and LN data
     * records are transactional, but INs are not.
     * @param marshallOutsideLatch true if this type of log entry may be 
     * serialized outside the log write latch. This is true of the majority of
     * types. Certain types like the FileSummaryLN rely on the log write latch
     * to enforce serial semantics.
     * @param isTypeReplicated true if this type of log entry should be shared
     * with a replication group.
     */
    private LogEntryType(byte typeNum,
                         byte version,
                         String displayName,
                         LogEntry logEntry,
                         boolean isTransactional,
                         boolean marshallOutsideLatch,
                         boolean isTypeReplicated) {

        this.typeNum = typeNum;
        this.version = version;
        this.displayName = displayName;
        this.logEntry = logEntry;
        this.isTransactional = isTransactional;
        this.marshallOutsideLatch = marshallOutsideLatch;
        this.isTypeReplicated = isTypeReplicated;
        logEntry.setLogType(this);
        LOG_TYPES[typeNum - 1] = this;
    }

    public boolean isNodeType() {
        return (typeNum <= MAX_NODE_TYPE_NUM);
    }

    /**
     * @return the static version of this type
     */
    public static LogEntryType findType(byte typeNum, byte version) {
        if (typeNum <= 0 || typeNum > MAX_TYPE_NUM) {
            return null;
        }
        return (LogEntryType) LOG_TYPES[typeNum - 1];
    }

    /**
     * Get a copy of all types for unit testing.
     */
    public static Set getAllTypes() {
        HashSet ret = new HashSet();

        for (int i = 0; i < MAX_TYPE_NUM; i++) {
            ret.add(LOG_TYPES[i]);
        }
        return ret;
    }

    /**
     * @return the log entry type owned by the shared, static version
     */
    public LogEntry getSharedLogEntry() {
        return logEntry;
    }

    /**
     * @return a clone of the log entry type for a given log type.
     */
    LogEntry getNewLogEntry()
        throws DatabaseException {

        try {
            return (LogEntry) logEntry.clone();
        } catch (CloneNotSupportedException e) {
            throw new DatabaseException(e);
        }
    }

    /**
     * Return the version value, clearing away provisional and replicated bits.
     */
    public static byte getVersionValue(byte version) {
        byte value = (byte) (version & IGNORE_PROVISIONAL);
        value &= IGNORE_REPLICATED;
        return value;
    }

    /**
     * Set the provisional bit.
     */
    static byte setEntryProvisional(byte version) {
        return (byte) (version | PROVISIONAL_MASK);
    }

    /**
     * @return true if the provisional bit is set.
     */
    static boolean isEntryProvisional(byte version) {
        return ((version & PROVISIONAL_MASK) != 0);
    }

    /**
     * Set the replicated bit
     */
    static byte setEntryReplicated(byte version) {
        return (byte) (version | REPLICATED_MASK);
    }

    /**
     * @return true if the replicated bit is set.
     */
    public static boolean isEntryReplicated(byte version) {
        return ((version & REPLICATED_MASK) != 0);
    }

    byte getTypeNum() {
        return typeNum;
    }

    byte getVersion() {
        return version;
    }

    /**
     * @return true if type number is valid.
     */
    static boolean isValidType(byte typeNum) {
        return typeNum > 0 && typeNum <= MAX_TYPE_NUM;
    }

    public String toString() {
        return displayName + "/" + version;
    }

    /**
     * Check for equality without making a new object.
     */
    boolean equalsType(byte typeNum, byte version) {
        return (this.typeNum == typeNum);
    }

    public boolean equalsType(byte typeNum) {
        return (this.typeNum == typeNum);
    }

    /* 
     * Override Object.equals. Ignore provisional bit when checking for
     * equality.
     */
    public boolean equals(Object obj) {
        // Same instance?
        if (this == obj) {
            return true;
        }

        // Is it the right type of object?
        if (!(obj instanceof LogEntryType)) {
            return false;
        }

        return typeNum == ((LogEntryType) obj).typeNum;
    }

    /**
     * This is used as a hash key.
     */
    public int hashCode() {
        return typeNum;
    }

    /**
     * Return true if this log entry has transactional information in it,
     * like a commit or abort record, or a transactional LN.
     */
    public boolean isTransactional() {
        return isTransactional;
    }

    /**
     * Return true if this log entry should be marshalled into a buffer
     * outside the log write latch. Currently, only the FileSummaryLN needs
     * to be logged inside the log write latch. 
     */
    public boolean marshallOutsideLatch() {
        return marshallOutsideLatch;
    }

    /**
     * Return true if this log entry should be transmitted to other
     * sites if the environment is part of a replication group.
     */
    public boolean isTypeReplicated() {
        return isTypeReplicated;
    }
}
