/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: DIN.java,v 1.79.2.2 2007/03/08 22:32:59 mark Exp $
 */

package com.sleepycat.je.tree;

import java.nio.ByteBuffer;
import java.util.Comparator;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.cleaner.Cleaner;
import com.sleepycat.je.dbi.DatabaseId;
import com.sleepycat.je.dbi.DatabaseImpl;
import com.sleepycat.je.dbi.DbConfigManager;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.dbi.MemoryBudget;
import com.sleepycat.je.log.LogEntryType;
import com.sleepycat.je.log.LogException;
import com.sleepycat.je.log.LogManager;
import com.sleepycat.je.log.LogUtils;
import com.sleepycat.je.txn.LockResult;
import com.sleepycat.je.txn.Locker;
import com.sleepycat.je.utilint.DbLsn;

/**
 * An DIN represents an Duplicate Internal Node in the JE tree.
 */
public final class DIN extends IN {

    private static final String BEGIN_TAG = "<din>";
    private static final String END_TAG = "</din>";

    /**
     * Full key for this set of duplicates.
     */
    private byte[] dupKey;

    /**
     * Reference to DupCountLN which stores the count.
     */
    private ChildReference dupCountLNRef;

    /**
     * Create an empty DIN, with no node id, to be filled in from the log.
     */
    public DIN() {
        super(); 

        dupCountLNRef = new ChildReference();
        init(null, Key.EMPTY_KEY, 0, 0);
    }

    /**
     * Create a new DIN.
     */
    public DIN(DatabaseImpl db,
	       byte[] identifierKey,
	       int capacity,
               byte[] dupKey,
	       ChildReference dupCountLNRef,
	       int level) {
        super(db, identifierKey, capacity, level);

        this.dupKey = dupKey;
        this.dupCountLNRef = dupCountLNRef;
        initMemorySize(); // init after adding Dup Count LN. */
    }

    /* Duplicates have no mask on their levels. */
    protected int generateLevel(DatabaseId dbId, int newLevel) {
        return newLevel;
    }

    /**
     * Create a new DIN.  Need this because we can't call newInstance()
     * without getting a 0 node.
     */
    protected IN createNewInstance(byte[] identifierKey,
                                   int maxEntries,
                                   int level) {
        return new DIN(getDatabase(),
                       identifierKey,
                       maxEntries,
                       dupKey,
                       dupCountLNRef,
                       level);
    }

    /*
     * Return whether the shared latch for this kind of node should be of the
     * "always exclusive" variety.  Presently, only IN's are actually latched
     * shared.  BINs, DINs, and DBINs are all latched exclusive only.
     */
    boolean isAlwaysLatchedExclusively() {
	return true;
    }

    /**
     * Return the key for this duplicate set.
     */
    public byte[] getDupKey() {
        return dupKey;
    }

    /**
     * Get the key (dupe or identifier) in child that is used to locate
     * it in 'this' node.
     */
    public byte[] getChildKey(IN child)
        throws DatabaseException {

        return child.getIdentifierKey();
    }

    /*
     * A DIN uses the dupTree key in its searches.
     */
    public byte[] selectKey(byte[] mainTreeKey, byte[] dupTreeKey) {
        return dupTreeKey;
    }

    /**
     * Return the key for navigating through the duplicate tree.
     */
    public byte[] getDupTreeKey() {
        return getIdentifierKey();
    }

    /**
     * Return the key for navigating through the main tree.
     */
    public byte[] getMainTreeKey() {
        return dupKey;
    }

    public ChildReference getDupCountLNRef() {
        return dupCountLNRef;
    }

    public DupCountLN getDupCountLN() 
        throws DatabaseException {

        return (DupCountLN) dupCountLNRef.fetchTarget(getDatabase(), this);
    }

    /*
     * All methods that modify the dup count LN must adjust memory sizing.
     */

    /**
     * Assign the Dup Count LN.
     */
    void setDupCountLN(ChildReference dupCountLNRef) {
        updateMemorySize(this.dupCountLNRef, dupCountLNRef);
        this.dupCountLNRef = dupCountLNRef;
    }

    /**
     * Assign the Dup Count LN node.  Does not dirty the DIN.
     */
    public void updateDupCountLN(Node target) {
        long oldSize = getEntryInMemorySize(dupCountLNRef.getKey(),
				            dupCountLNRef.getTarget());
        dupCountLNRef.setTarget(target);
        long newSize = getEntryInMemorySize(dupCountLNRef.getKey(),
				            dupCountLNRef.getTarget());
        updateMemorySize(oldSize, newSize);
    }

    /**
     * Update Dup Count LN.
     */
    public void updateDupCountLNRefAndNullTarget(long newLsn) {
        setDirty(true);
        long oldSize = getEntryInMemorySize(dupCountLNRef.getKey(),
				            dupCountLNRef.getTarget());
        dupCountLNRef.setTarget(null);
        if (notOverwritingDeferredWriteEntry(newLsn)) {
            dupCountLNRef.setLsn(newLsn);
        }
        long newSize = getEntryInMemorySize(dupCountLNRef.getKey(),
				            dupCountLNRef.getTarget());
        updateMemorySize(oldSize, newSize);
    }

    /**
     * Update dup count LSN.
     */
    public void updateDupCountLNRef(long newLsn) {
        setDirty(true);
        if (notOverwritingDeferredWriteEntry(newLsn)) {
            dupCountLNRef.setLsn(newLsn);
        }
    }

    /**
     * @return true if this node is a duplicate-bearing node type, false
     * if otherwise.
     */
    public boolean containsDuplicates() {
        return true;
    }

    /* Never true for a DIN. */
    public boolean isDbRoot() {
	return false;
    }

    /**
     * Return the comparator function to be used for DINs.  This is
     * the user defined duplicate comparison function, if defined.
     */
    public final Comparator getKeyComparator() {
        return getDatabase().getDuplicateComparator();
    }

    /**
     * Increment or decrement the DupCountLN, log the updated LN, and update
     * the lock result.
     *
     * Preconditions: This DIN is latched and the DupCountLN is write locked.
     * Postconditions: Same as preconditions.
     */
    public void incrementDuplicateCount(LockResult lockResult,
                                        byte[] key,
                                        Locker locker,
                                        boolean increment)
        throws DatabaseException {

        /* Increment/decrement the dup count and update its owning DIN. */
        long oldLsn = dupCountLNRef.getLsn();
        lockResult.setAbortLsn(oldLsn, dupCountLNRef.isKnownDeleted());
        DupCountLN dupCountLN = getDupCountLN();
        int oldSize = dupCountLN.getLastLoggedSize();
        if (increment) {
            dupCountLN.incDupCount();
        } else {
            dupCountLN.decDupCount();
	    assert dupCountLN.getDupCount() >= 0;
        }
        DatabaseImpl db = getDatabase();
        long newCountLSN = dupCountLN.optionalLog
            (db.getDbEnvironment(), db, key, oldLsn, oldSize, locker);
        updateDupCountLNRef(newCountLSN);
            
    }

    /**
     * Count up the memory usage attributable to this node alone. LNs children
     * are counted by their BIN/DIN parents, but INs are not counted by 
     * their parents because they are resident on the IN list.
     */
    protected long computeMemorySize() {
        long size = super.computeMemorySize();
        if (dupCountLNRef != null) {
            size += getEntryInMemorySize(dupCountLNRef.getKey(),
				         dupCountLNRef.getTarget());
        }
        /* XXX Need to update size when changing the dupKey.
	   if (dupKey != null && dupKey.getKey() != null) {
	   size += MemoryBudget.byteArraySize(dupKey.getKey().length);
	   }
        */
        return size;
    }

    /* Called once at environment startup by MemoryBudget. */
    public static long computeOverhead(DbConfigManager configManager) 
        throws DatabaseException {

        /* 
	 * Overhead consists of all the fields in this class plus the
	 * entry arrays in the IN class.
         */
        return MemoryBudget.DIN_FIXED_OVERHEAD +
	    IN.computeArraysOverhead(configManager);
    }
    
    protected long getMemoryOverhead(MemoryBudget mb) {
        return mb.getDINOverhead();
    }

    /*
     * Depth first search through a duplicate tree looking for an LN that
     * has nodeId.  When we find it, set location.bin and index and return
     * true.  If we don't find it, return false.
     *
     * No latching is performed.
     */
    boolean matchLNByNodeId(TreeLocation location, long nodeId)
	throws DatabaseException {

	latch();
	try {
	    for (int i = 0; i < getNEntries(); i++) {
		Node n = fetchTarget(i);
		if (n != null) {
		    boolean ret = n.matchLNByNodeId(location, nodeId);
		    if (ret) {
			return true;
		    }
		}
	    }

	    return false;
	} finally {
	    releaseLatch();
	}
    }

    /*
     * DbStat support.
     */
    void accumulateStats(TreeWalkerStatsAccumulator acc) {
	acc.processDIN(this, new Long(getNodeId()), getLevel());
    }

    /*
     * Logging Support
     */

    /**
     * @see Node#getLogType
     */
    public LogEntryType getLogType() {
        return LogEntryType.LOG_DIN;
    }

    /**
     * Handles lazy migration of DupCountLNs prior to logging a DIN.  See
     * BIN.logInternal for more information.
     */
    protected long logInternal(LogManager logManager, 
			       boolean allowDeltas,
			       boolean isProvisional,
                               boolean proactiveMigration,
                               boolean backgroundIO,
                               IN parent)
        throws DatabaseException {


        if (dupCountLNRef != null) {
            EnvironmentImpl envImpl = getDatabase().getDbEnvironment();
            DupCountLN dupCntLN = (DupCountLN) dupCountLNRef.getTarget();

            if ((dupCntLN != null) && (dupCntLN.isDirty())) {

                /* 
                 * If deferred write, write any dirty LNs now. The old LSN
                 * is NULL_LSN, a no-opt in non-txnal deferred write mode.
                 */
                long newLsn = dupCntLN.log(envImpl,
                                           getDatabaseId(),
                                           dupKey,
                                           DbLsn.NULL_LSN,// old lsn
                                           0,             // obsolete size
                                           null,          // locker
                                           false);        // backgroundIO
                dupCountLNRef.setLsn(newLsn);
            } else {

                /* 
                 * Allow the cleaner to migrate the DupCountLN before logging.
                 */
                Cleaner cleaner =
                    getDatabase().getDbEnvironment().getCleaner();
                cleaner.lazyMigrateDupCountLN
                    (this, dupCountLNRef, proactiveMigration);
            }
        }

        return super.logInternal
            (logManager, allowDeltas, isProvisional, proactiveMigration,
             backgroundIO, parent);
    }

    /**
     * @see IN#getLogSize
     */
    public int getLogSize() {
        int size = super.getLogSize();               // ancestors
        size += LogUtils.getByteArrayLogSize(dupKey);// identifier key
        size += LogUtils.getBooleanLogSize();        // dupCountLNRef null flag
        if (dupCountLNRef != null) {
            size += dupCountLNRef.getLogSize();
        }
        return size;
    }

    /**
     * @see IN#writeToLog
     */
    public void writeToLog(ByteBuffer logBuffer) {

        // ancestors
        super.writeToLog(logBuffer); 

        // identifier key
        LogUtils.writeByteArray(logBuffer, dupKey);

        /* DupCountLN */
        boolean dupCountLNRefExists = (dupCountLNRef != null);
        LogUtils.writeBoolean(logBuffer, dupCountLNRefExists);
        if (dupCountLNRefExists) {
            dupCountLNRef.writeToLog(logBuffer);    
        }
    }

    /**
     * @see IN#readFromLog
     */
    public void readFromLog(ByteBuffer itemBuffer, byte entryTypeVersion)
        throws LogException {

        // ancestors 
        super.readFromLog(itemBuffer, entryTypeVersion);

        // identifier key
        dupKey = LogUtils.readByteArray(itemBuffer);

        /* DupCountLN */
        boolean dupCountLNRefExists = LogUtils.readBoolean(itemBuffer);
        if (dupCountLNRefExists) {
            dupCountLNRef.readFromLog(itemBuffer, entryTypeVersion);
        } else {
            dupCountLNRef = null;
        }
    }
    
    /**
     * DINS need to dump their dup key
     */
    protected void dumpLogAdditional(StringBuffer sb) {
        super.dumpLogAdditional(sb);
        sb.append(Key.dumpString(dupKey, 0));
        if (dupCountLNRef != null) {
            dupCountLNRef.dumpLog(sb, true);
        }
    }

    /*
     * Dumping
     */

    public String beginTag() {
        return BEGIN_TAG;
    }

    public String endTag() {
        return END_TAG;
    }

    /**
     * For unit test support:
     * @return a string that dumps information about this DIN, without
     */
    public String dumpString(int nSpaces, boolean dumpTags) {
        StringBuffer sb = new StringBuffer();
        if (dumpTags) {
            sb.append(TreeUtils.indent(nSpaces));
            sb.append(beginTag());
            sb.append('\n');
        }

        sb.append(TreeUtils.indent(nSpaces+2));
        sb.append("<dupkey>");
        sb.append(dupKey == null ? "" : 
                  Key.dumpString(dupKey, 0));
        sb.append("</dupkey>");
        sb.append('\n');
        if (dupCountLNRef == null) {
	    sb.append(TreeUtils.indent(nSpaces+2));
            sb.append("<dupCountLN/>");
        } else {
            sb.append(dupCountLNRef.dumpString(nSpaces + 4, true));
        }
        sb.append('\n');
        sb.append(super.dumpString(nSpaces, false));

        if (dumpTags) {
            sb.append(TreeUtils.indent(nSpaces));
            sb.append(endTag());
        }
        return sb.toString();
    }

    public String toString() {
        return dumpString(0, true);
    }

    public String shortClassName() {
        return "DIN";
    }
}
