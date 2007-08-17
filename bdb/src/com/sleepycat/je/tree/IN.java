/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: IN.java,v 1.295.2.5 2007/07/02 19:54:52 mark Exp $
 */

package com.sleepycat.je.tree;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.cleaner.UtilizationTracker;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.DatabaseId;
import com.sleepycat.je.dbi.DatabaseImpl;
import com.sleepycat.je.dbi.DbConfigManager;
import com.sleepycat.je.dbi.DbTree;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.dbi.INList;
import com.sleepycat.je.dbi.MemoryBudget;
import com.sleepycat.je.latch.LatchNotHeldException;
import com.sleepycat.je.latch.LatchSupport;
import com.sleepycat.je.latch.SharedLatch;
import com.sleepycat.je.log.LogEntryType;
import com.sleepycat.je.log.LogException;
import com.sleepycat.je.log.LogFileNotFoundException;
import com.sleepycat.je.log.LogManager;
import com.sleepycat.je.log.LogUtils;
import com.sleepycat.je.log.Loggable;
import com.sleepycat.je.log.entry.INLogEntry;
import com.sleepycat.je.utilint.DbLsn;
import com.sleepycat.je.utilint.Tracer;

/**
 * An IN represents an Internal Node in the JE tree.
 */
public class IN extends Node implements Comparable, Loggable {

    private static final String BEGIN_TAG = "<in>";
    private static final String END_TAG = "</in>";
    private static final String TRACE_SPLIT = "Split:";
    private static final String TRACE_DELETE = "Delete:";

    private static final byte KNOWN_DELETED_BIT = 0x1;
    private static final byte CLEAR_KNOWN_DELETED_BIT = ~0x1;
    private static final byte DIRTY_BIT = 0x2;
    private static final byte CLEAR_DIRTY_BIT = ~0x2;
    private static final byte MIGRATE_BIT = 0x4;
    private static final byte CLEAR_MIGRATE_BIT = ~0x4;
    private static final byte PENDING_DELETED_BIT = 0x8;
    private static final byte CLEAR_PENDING_DELETED_BIT = ~0x8;

    private static final int BYTES_PER_LSN_ENTRY = 4;
    private static final int MAX_FILE_OFFSET = 0xfffffe;
    private static final int THREE_BYTE_NEGATIVE_ONE = 0xffffff;
    private static final int GROWTH_INCREMENT = 5; // for future

    /* 
     * Levels: 
     * The mapping tree has levels in the 0x20000 -> 0x2ffffnumber space.
     * The main tree has levels in the 0x10000 -> 0x1ffff number space.
     * The duplicate tree levels are in 0-> 0xffff number space.
     */
    public static final int DBMAP_LEVEL = 0x20000;
    public static final int MAIN_LEVEL = 0x10000;
    public static final int LEVEL_MASK = 0x0ffff;
    public static final int MIN_LEVEL = -1;
    public static final int MAX_LEVEL = Integer.MAX_VALUE;
    public static final int BIN_LEVEL = MAIN_LEVEL | 1;

    /*
     * IN eviction types returned by getEvictionType.
     */
    public static final int MAY_NOT_EVICT = 0;
    public static final int MAY_EVICT_LNS = 1;
    public static final int MAY_EVICT_NODE = 2;

    protected SharedLatch latch;
    private long generation;
    private boolean dirty;
    private int nEntries;
    private byte[] identifierKey;

    /*
     * The following four arrays could more easily be embodied in an array of
     * ChildReferences.  However, for in-memory space savings, we save the
     * overhead of ChildReference and DbLsn objects by in-lining the elements
     * of the ChildReference directly in the IN.
     */
    private Node[] entryTargets;
    private byte[][] entryKeyVals; // byte[][] instead of Key[] to save space

    /*
     * The following entryLsnXXX fields are used for storing LSNs.  There are
     * two possible representations: a byte array based rep, and a long array
     * based one.  For compactness, the byte array rep is used initially.  A
     * single byte[] that uses four bytes per LSN is used. The baseFileNumber
     * field contains the lowest file number of any LSN in the array.  Then for
     * each entry (four bytes each), the first byte contains the offset from
     * the baseFileNumber of that LSN's file number.  The remaining three bytes
     * contain the file offset portion of the LSN.  Three bytes will hold a
     * maximum offset of 16,777,214 (0xfffffe), so with the default JE log file
     * size of 10,000,000 bytes this works well.
     *
     * If either (1) the difference in file numbers exceeds 127
     * (Byte.MAX_VALUE) or (2) the file offset is greater than 16,777,214, then
     * the byte[] based rep mutates to a long[] based rep.
     *
     * In the byte[] rep, DbLsn.NULL_LSN is represented by setting the file
     * offset bytes for a given entry to -1 (0xffffff).
     */
    private long baseFileNumber;
    private byte[] entryLsnByteArray;
    private long[] entryLsnLongArray;
    private byte[] entryStates;
    private DatabaseImpl databaseImpl;
    private boolean isRoot; // true if this is the root of a tree
    private int level;
    private long inMemorySize;
    private boolean inListResident; // true if this IN is on the IN list
    // Location of last full version.
    private long lastFullVersion = DbLsn.NULL_LSN;

    /*
     * A list of Long LSNs that cannot be counted as obsolete until an ancestor
     * IN is logged non-provisionally.
     */
    private List provisionalObsolete;

    /* Used to indicate that an exact match was found in findEntry. */
    public static final int EXACT_MATCH = (1 << 16);

    /* Used to indicate that an insert was successful. */
    public static final int INSERT_SUCCESS = (1 << 17);

    /*
     * accumluted memory budget delta.  Once this exceeds
     * MemoryBudget.ACCUMULATED_LIMIT we inform the MemoryBudget that a change
     * has occurred.  See SR 12273.
     */
    private int accumulatedDelta = 0;

    /*
     * Max allowable accumulation of memory budget changes before MemoryBudget
     * should be updated. This allows for consolidating multiple calls to
     * updateXXXMemoryBudget() into one call.  Not declared final so that the
     * unit tests can modify it.  See SR 12273.
     */
    public static int ACCUMULATED_LIMIT = 1000;

    /**
     * Create an empty IN, with no node id, to be filled in from the log.
     */
    public IN() {
        super(false); 
        init(null, Key.EMPTY_KEY, 0, 0);
    }

    /**
     * Create a new IN.
     */
    public IN(DatabaseImpl db, byte[] identifierKey, int capacity, int level) {

        super(true);
        init(db, identifierKey, capacity, generateLevel(db.getId(), level));
        initMemorySize();
    }

    /**
     * Initialize IN object.
     */
    protected void init(DatabaseImpl db,
                        byte[] identifierKey,
                        int initialCapacity,
                        int level) {
        setDatabase(db);
	EnvironmentImpl env =
	    (databaseImpl == null) ? null : databaseImpl.getDbEnvironment();
        latch =
	    LatchSupport.makeSharedLatch(shortClassName() + getNodeId(), env);
	latch.setExclusiveOnly(EnvironmentImpl.getSharedLatches() ?
			       isAlwaysLatchedExclusively() :
			       true);
	assert latch.setNoteLatch(true);
        generation = 0;
        dirty = false;
        nEntries = 0;
        this.identifierKey = identifierKey;
	entryTargets = new Node[initialCapacity];
	entryKeyVals = new byte[initialCapacity][];
	baseFileNumber = -1;
	entryLsnByteArray = new byte[initialCapacity << 2];
	entryLsnLongArray = null;
	entryStates = new byte[initialCapacity];
        isRoot = false;
        this.level = level;
        inListResident = false;
    }

    /**
     * Initialize the per-node memory count by computing its memory usage.
     */
    protected void initMemorySize() {
        inMemorySize = computeMemorySize();
    }

    /* 
     * To get an inexpensive but random distribution of INs in the INList,
     * equality and comparison for INs is based on a combination of the node
     * id and identify hash code. Note that this will still give a correct
     * equality value for any comparisons outside the INList sorted set.
     */
    private long getEqualityKey() {
        int hash = System.identityHashCode(this);
        long hash2 = (((long) hash) << 32) | hash;
        return hash2 ^ getNodeId(); 
    }
    
    public boolean equals(Object obj) {
        if (!(obj instanceof IN)) {
            return false;
        }

        IN in = (IN) obj;
        return (this.getEqualityKey() == in.getEqualityKey());
    }

    public int hashCode() {
        return (int) getEqualityKey();
    }

    /**
     * Sort based on node id.
     */
    public int compareTo(Object o) {
        if (o == null) {
            throw new NullPointerException();
        }

        IN argIN = (IN) o;

        long argEqualityKey = argIN.getEqualityKey();
        long myEqualityKey = getEqualityKey();

        if (myEqualityKey < argEqualityKey) {
            return -1;
        } else if (myEqualityKey > argEqualityKey) {
            return 1;
        } else {
            return 0;
        }
    }

    /**
     * Create a new IN.  Need this because we can't call newInstance() without
     * getting a 0 for nodeid.
     */
    protected IN createNewInstance(byte[] identifierKey,
                                   int maxEntries,
                                   int level) {
        return new IN(databaseImpl, identifierKey, maxEntries, level);
    }

    /*
     * Return whether the shared latch for this kind of node should be of the
     * "always exclusive" variety.  Presently, only IN's are actually latched
     * shared.  BINs, DINs, and DBINs are all latched exclusive only.
     */
    boolean isAlwaysLatchedExclusively() {
	return false;
    }

    /**
     * Initialize a node that has been read in from the log.
     */
    public void postFetchInit(DatabaseImpl db, long sourceLsn)
        throws DatabaseException {

        setDatabase(db);
        setLastFullLsn(sourceLsn);
        EnvironmentImpl env = db.getDbEnvironment();
        initMemorySize(); // compute before adding to inlist
        env.getInMemoryINs().add(this);
    }

    /**
     * Initialize a node read in during recovery.
     */
    public void postRecoveryInit(DatabaseImpl db, long sourceLsn) {
        setDatabase(db);
        setLastFullLsn(sourceLsn);
        initMemorySize();
    }

    /**
     * Sets the last logged LSN.
     */
    void setLastFullLsn(long lsn) {
        lastFullVersion = lsn;
    }
        
    /**
     * Returns the last logged LSN, or null if never logged.  Is public for
     * unit testing.
     */
    public long getLastFullVersion() {
        return lastFullVersion;
    }

    /**
     * Latch this node exclusive, optionally setting the generation.
     */
    public void latch(boolean updateGeneration)
        throws DatabaseException {

        if (updateGeneration) {
            setGeneration();
        }
        latch.acquireExclusive();
    }

    /**
     * Latch this node shared, optionally setting the generation.
     */
    public void latchShared(boolean updateGeneration)
	throws DatabaseException {

	if (updateGeneration) {
	    setGeneration();
	}

	latch.acquireShared();
    }

    /**
     * Latch this node if it is not latched by another thread, optionally
     * setting the generation if the latch succeeds.
     */
    public boolean latchNoWait(boolean updateGeneration)
        throws DatabaseException {

        if (latch.acquireExclusiveNoWait()) {
            if (updateGeneration) {
                setGeneration();
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * Latch this node exclusive and set the generation.
     */
    public void latch()
        throws DatabaseException {

        latch(true);
    }

    /**
     * Latch this node shared and set the generation.
     */
    public void latchShared()
	throws DatabaseException {

	latchShared(true);
    }

    /**
     * Latch this node if it is not latched by another thread, and set the
     * generation if the latch succeeds.
     */
    public boolean latchNoWait()
        throws DatabaseException {

        return latchNoWait(true);
    }
    
    /**
     * Release the latch on this node.
     */
    public void releaseLatch()
        throws LatchNotHeldException {

        latch.release();
    }

    /**
     * Release the latch on this node.
     */
    public void releaseLatchIfOwner()
        throws LatchNotHeldException {

        latch.releaseIfOwner();
    }

    /**
     * @return true if this thread holds the IN's latch
     */
    public boolean isLatchOwnerForRead() {
	return latch.isOwner();
    }

    public boolean isLatchOwnerForWrite() {
	return latch.isWriteLockedByCurrentThread();
    }

    public long getGeneration() {
        return generation;
    }

    public void setGeneration() {
        generation = Generation.getNextGeneration();
    }

    public void setGeneration(long newGeneration) {
        generation = newGeneration;
    }

    public int getLevel() {
        return level;
    }

    protected int generateLevel(DatabaseId dbId, int newLevel) {
        if (dbId.equals(DbTree.ID_DB_ID)) {
            return newLevel | DBMAP_LEVEL;
        } else {
            return newLevel | MAIN_LEVEL;
        }
    }

    public boolean getDirty() {
        return dirty;
    }

    /* public for unit tests */
    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    public boolean isRoot() {
        return isRoot;
    }

    public boolean isDbRoot() {
	return isRoot;
    }

    void setIsRoot(boolean isRoot) {
        this.isRoot = isRoot;
        setDirty(true);
    }

    /**
     * @return the identifier key for this node.
     */
    public byte[] getIdentifierKey() {
        return identifierKey;
    }

    /**
     * Set the identifier key for this node.
     *
     * @param key - the new identifier key for this node.
     */
    void setIdentifierKey(byte[] key) {
        identifierKey = key;
        setDirty(true);
    }

    /**
     * Get the key (dupe or identifier) in child that is used to locate it in
     * 'this' node.
     */
    public byte[] getChildKey(IN child)
        throws DatabaseException {

        return child.getIdentifierKey();
    }

    /*
     * An IN uses the main key in its searches.
     */
    public byte[] selectKey(byte[] mainTreeKey, byte[] dupTreeKey) {
        return mainTreeKey;
    }

    /**
     * Return the key for this duplicate set.
     */
    public byte[] getDupKey()
        throws DatabaseException {

        throw new DatabaseException(shortClassName() + ".getDupKey() called");
    }

    /**
     * Return the key for navigating through the duplicate tree.
     */
    public byte[] getDupTreeKey() {
        return null;
    }
    /**
     * Return the key for navigating through the main tree.
     */
    public byte[] getMainTreeKey() {
        return getIdentifierKey();
    }

    /**
     * Get the database for this IN.
     */
    public DatabaseImpl getDatabase() {
        return databaseImpl;
    }

    /**
     * Set the database reference for this node.
     */
    public void setDatabase(DatabaseImpl db) {
        databaseImpl = db;
    }
        
    /* 
     * Get the database id for this node.
     */
    public DatabaseId getDatabaseId() {
        return databaseImpl.getId();
    }

    private void setEntryInternal(int from, int to) {
	entryTargets[to] = entryTargets[from];
	entryKeyVals[to] = entryKeyVals[from];
	entryStates[to] = entryStates[from];
	/* Will implement this in the future. Note, don't adjust if mutating.*/
	//maybeAdjustCapacity(offset);
	if (entryLsnLongArray == null) {
	    int fromOff = from << 2;
	    int toOff = to << 2;
	    entryLsnByteArray[toOff++] = entryLsnByteArray[fromOff++];
	    entryLsnByteArray[toOff++] = entryLsnByteArray[fromOff++];
	    entryLsnByteArray[toOff++] = entryLsnByteArray[fromOff++];
	    entryLsnByteArray[toOff] = entryLsnByteArray[fromOff];
	} else {
	    entryLsnLongArray[to] = entryLsnLongArray[from];
	}
    }

    private void clearEntry(int idx) {
	entryTargets[idx] = null;
	entryKeyVals[idx] = null;
	setLsnElement(idx, DbLsn.NULL_LSN);
	entryStates[idx] = 0;
    }

    /**
     * Return the idx'th key.
     */
    public byte[] getKey(int idx) {
        return entryKeyVals[idx];
    }

    /**
     * Set the idx'th key.
     */
    private void setKey(int idx, byte[] keyVal) {
	entryKeyVals[idx] = keyVal;
        entryStates[idx] |= DIRTY_BIT;
    }

    /**
     * Get the idx'th migrate status.
     */
    public boolean getMigrate(int idx) {
        return (entryStates[idx] & MIGRATE_BIT) != 0;
    }

    /**
     * Set the idx'th migrate status.
     */
    public void setMigrate(int idx, boolean migrate) {
        if (migrate) {
            entryStates[idx] |= MIGRATE_BIT;
        } else {
            entryStates[idx] &= CLEAR_MIGRATE_BIT;
        }
    }

    public byte getState(int idx) {
	return entryStates[idx];
    }

    /**
     * Return the idx'th target.
     */
    public Node getTarget(int idx) {
        return entryTargets[idx];
    }

    /**
     * Sets the idx'th target. No need to make dirty, that state only applies
     * to key and LSN.
     *
     * <p>WARNING: This method does not update the memory budget.  The caller
     * must update the budget.</p>
     */
    void setTarget(int idx, Node target) {
        entryTargets[idx] = target;
    }

    /**
     * Return the idx'th LSN for this entry.
     *
     * @return the idx'th LSN for this entry.
     */
    public long getLsn(int idx) {
	if (entryLsnLongArray == null) {
	    int offset = idx << 2;
	    int fileOffset = getFileOffset(offset);
	    if (fileOffset == -1) {
		return DbLsn.NULL_LSN;
	    } else {
		return DbLsn.makeLsn((long) (baseFileNumber +
					     getFileNumberOffset(offset)),
				     fileOffset);
	    }
	} else {
	    return entryLsnLongArray[idx];
	}
    }

    /**
     * Sets the idx'th target LSN. Make this a private helper method, so we're
     * sure to set the IN dirty where appropriate.
     */
    private void setLsn(int idx, long lsn) {

	int oldSize = computeLsnOverhead();
	/* setLsnElement can mutate to an array of longs. */
	setLsnElement(idx, lsn);
	changeMemorySize(computeLsnOverhead() - oldSize);
        entryStates[idx] |= DIRTY_BIT;
    }

    /* For unit tests. */
    long[] getEntryLsnLongArray() {
	return entryLsnLongArray;
    }

    /* For unit tests. */
    byte[] getEntryLsnByteArray() {
	return entryLsnByteArray;
    }

    /* For unit tests. */
    void initEntryLsn(int capacity) {
	entryLsnLongArray = null;
	entryLsnByteArray = new byte[capacity << 2];
	baseFileNumber = -1;
    }

    /* Use default protection for unit tests. */
    void setLsnElement(int idx, long value) {

	int offset = idx << 2;
	/* Will implement this in the future. Note, don't adjust if mutating.*/
	//maybeAdjustCapacity(offset);
	if (entryLsnLongArray != null) {
	    entryLsnLongArray[idx] = value;
	    return;
	}

	if (value == DbLsn.NULL_LSN) {
	    setFileNumberOffset(offset, (byte) 0);
	    setFileOffset(offset, -1);
	    return;
	}

	long thisFileNumber = DbLsn.getFileNumber(value);

	if (baseFileNumber == -1) {
	    /* First entry. */
	    baseFileNumber = thisFileNumber;
	    setFileNumberOffset(offset, (byte) 0);
	} else {
	    if (thisFileNumber < baseFileNumber) {
		if (!adjustFileNumbers(thisFileNumber)) {
		    mutateToLongArray(idx, value);
		    return;
		}
		baseFileNumber = thisFileNumber;
	    } 
	    long fileNumberDifference = thisFileNumber - baseFileNumber;
	    if (fileNumberDifference > Byte.MAX_VALUE) {
		mutateToLongArray(idx, value);
		return;
	    }
	    setFileNumberOffset
		(offset, (byte) (thisFileNumber - baseFileNumber));
	    //assert getFileNumberOffset(offset) >= 0;
	}

	int fileOffset = (int) DbLsn.getFileOffset(value);
	if (fileOffset > MAX_FILE_OFFSET) {
	    mutateToLongArray(idx, value);
	    return;
	}

	setFileOffset(offset, fileOffset);
	//assert getLsn(offset) == value;
    }

    private void mutateToLongArray(int idx, long value) {
	int nElts = entryLsnByteArray.length >> 2;
	long[] newArr = new long[nElts];
	for (int i = 0; i < nElts; i++) {
	    newArr[i] = getLsn(i);
	}
	newArr[idx] = value;
	entryLsnLongArray = newArr;
	entryLsnByteArray = null;
    }

    /* Will implement this in the future. Note, don't adjust if mutating.*/
    /***
    private void maybeAdjustCapacity(int offset) {
	if (entryLsnLongArray == null) {
	    int bytesNeeded = offset + BYTES_PER_LSN_ENTRY;
	    int currentBytes = entryLsnByteArray.length;
	    if (currentBytes < bytesNeeded) {
		int newBytes = bytesNeeded +
		    (GROWTH_INCREMENT * BYTES_PER_LSN_ENTRY);
		byte[] newArr = new byte[newBytes];
		System.arraycopy(entryLsnByteArray, 0, newArr, 0,
				 currentBytes);
		entryLsnByteArray = newArr;
		for (int i = currentBytes;
		     i < newBytes;
		     i += BYTES_PER_LSN_ENTRY) {
		    setFileNumberOffset(i, (byte) 0);
		    setFileOffset(i, -1);
		}
	    }
	} else {
	    int currentEntries = entryLsnLongArray.length;
	    int idx = offset >> 2;
	    if (currentEntries < idx + 1) {
		int newEntries = idx + GROWTH_INCREMENT;
		long[] newArr = new long[newEntries];
		System.arraycopy(entryLsnLongArray, 0, newArr, 0,
				 currentEntries);
		entryLsnLongArray = newArr;
		for (int i = currentEntries; i < newEntries; i++) {
		    entryLsnLongArray[i] = DbLsn.NULL_LSN;
		}
	    }
	}
    }
    ***/

    private boolean adjustFileNumbers(long newBaseFileNumber) {
	long oldBaseFileNumber = baseFileNumber;
	for (int i = 0;
	     i < entryLsnByteArray.length;
	     i += BYTES_PER_LSN_ENTRY) {
	    if (getFileOffset(i) == -1) {
		continue;
	    }

	    long curEntryFileNumber =
		oldBaseFileNumber + getFileNumberOffset(i);
	    long newCurEntryFileNumberOffset =
		(curEntryFileNumber - newBaseFileNumber);
	    if (newCurEntryFileNumberOffset > Byte.MAX_VALUE) {
		long undoOffset = oldBaseFileNumber - newBaseFileNumber;
		for (int j = i - BYTES_PER_LSN_ENTRY;
		     j >= 0;
		     j -= BYTES_PER_LSN_ENTRY) {
		    if (getFileOffset(j) == -1) {
			continue;
		    }
		    setFileNumberOffset
			(j, (byte) (getFileNumberOffset(j) - undoOffset));
		    //assert getFileNumberOffset(j) >= 0;
		}
		return false;
	    }
	    setFileNumberOffset(i, (byte) newCurEntryFileNumberOffset);

	    //assert getFileNumberOffset(i) >= 0;
	}
	return true;
    }

    private void setFileNumberOffset(int offset, byte fileNumberOffset) {
	entryLsnByteArray[offset] = fileNumberOffset;
    }

    private byte getFileNumberOffset(int offset) {
	return entryLsnByteArray[offset];
    }

    private void setFileOffset(int offset, int fileOffset) {
	put3ByteInt(offset + 1, fileOffset);
    }

    private int getFileOffset(int offset) {
	return get3ByteInt(offset + 1);
    }

    private void put3ByteInt(int offset, int value) {
	entryLsnByteArray[offset++] = (byte) (value >>> 0);
	entryLsnByteArray[offset++] = (byte) (value >>> 8);
	entryLsnByteArray[offset]   = (byte) (value >>> 16);
    }

    private int get3ByteInt(int offset) {
        int ret = (entryLsnByteArray[offset++] & 0xFF) << 0;
	ret += (entryLsnByteArray[offset++] & 0xFF) << 8;
	ret += (entryLsnByteArray[offset]   & 0xFF) << 16;
	if (ret == THREE_BYTE_NEGATIVE_ONE) {
	    ret = -1;
	}

	return ret;
    }

    /**
     * @return true if the idx'th entry has been deleted, although the
     * transaction that performed the deletion may not be committed.
     */
    public boolean isEntryPendingDeleted(int idx) {
        return ((entryStates[idx] & PENDING_DELETED_BIT) != 0);
    }

    /**
     * Set pendingDeleted to true.
     */
    public void setPendingDeleted(int idx) {
        entryStates[idx] |= PENDING_DELETED_BIT;
        entryStates[idx] |= DIRTY_BIT;
    }

    /**
     * Set pendingDeleted to false.
     */
    public void clearPendingDeleted(int idx) {
        entryStates[idx] &= CLEAR_PENDING_DELETED_BIT;
        entryStates[idx] |= DIRTY_BIT;
    }

    /**
     * @return true if the idx'th entry is deleted for sure.  If a transaction
     * performed the deletion, it has been committed.
     */
    public boolean isEntryKnownDeleted(int idx) {
        return ((entryStates[idx] & KNOWN_DELETED_BIT) != 0);
    }

    /**
     * Set knownDeleted to true.
     */
    void setKnownDeleted(int idx) {
        entryStates[idx] |= KNOWN_DELETED_BIT;
        entryStates[idx] |= DIRTY_BIT;
    }

    /**
     * Set knownDeleted to false.
     */
    void clearKnownDeleted(int idx) {
        entryStates[idx] &= CLEAR_KNOWN_DELETED_BIT;
        entryStates[idx] |= DIRTY_BIT;
    }

    /**
     * @return true if the object is dirty.
     */
    boolean isDirty(int idx) {
        return ((entryStates[idx] & DIRTY_BIT) != 0);
    }

    /**
     * @return the number of entries in this node.
     */
    public int getNEntries() {
        return nEntries;
    }

    /*
     * In the future we may want to move the following static methods to an
     * EntryState utility class and share all state bit twidling among IN,
     * ChildReference, and DeltaInfo.
     */

    /**
     * Returns true if the given state is known deleted.
     */
    static boolean isStateKnownDeleted(byte state) {
        return ((state & KNOWN_DELETED_BIT) != 0);
    }

    /**
     * Returns true if the given state is known deleted.
     */
    static boolean isStatePendingDeleted(byte state) {
        return ((state & PENDING_DELETED_BIT) != 0);
    }

    /**
     * @return the maximum number of entries in this node.
     */
    int getMaxEntries() {
        return entryTargets.length;
    }

    /**
     * Returns the target of the idx'th entry or null if a pendingDeleted or
     * knownDeleted entry has been cleaned.  Note that null can only be
     * returned for a slot that could contain a deleted LN, not other node
     * types and not a DupCountLN since DupCountLNs are never deleted.  Null is
     * also returned for a KnownDeleted slot with a NULL_LSN.
     *
     * @return the target node or null.
     */
    public Node fetchTarget(int idx) 
        throws DatabaseException {

        if (entryTargets[idx] == null) {
            /* Fault object in from log. */
            long lsn = getLsn(idx);
            if (lsn == DbLsn.NULL_LSN) {
                if (!isEntryKnownDeleted(idx)) {
                    throw new DatabaseException(makeFetchErrorMsg
                        ("NULL_LSN without KnownDeleted", this, lsn,
                         entryStates[idx]));
                }

                /*
                 * Ignore a NULL_LSN (return null) if KnownDeleted is set.
                 * This is the remnant of an incomplete insertion -- see
                 * Tree.insert. [#13126]
                 */
            } else {
                try {
                    EnvironmentImpl env = databaseImpl.getDbEnvironment();
                    Node node = (Node) env.getLogManager().get(lsn);
                    node.postFetchInit(databaseImpl, lsn);
		    assert isLatchOwnerForWrite();
                    entryTargets[idx] = node;
                    updateMemorySize(null, node);
                } catch (LogFileNotFoundException LNFE) {
                    if (!isEntryKnownDeleted(idx) &&
                        !isEntryPendingDeleted(idx)) {
                        throw new DatabaseException
                            (makeFetchErrorMsg(LNFE.toString(),
                                               this,
                                               lsn,
                                               entryStates[idx]));
                    }

                    /*
                     * Ignore. Cleaner got to the log file, so just return
                     * null.  It is safe to ignore a deleted file for a
                     * pendingDeleted entry because the cleaner will not clean
                     * files with active transactions.
                     */
                } catch (Exception e) {
                    throw new DatabaseException
                        (makeFetchErrorMsg(e.toString(), this, lsn,
                                           entryStates[idx]),
                         e);
                }
            }
        }

        return entryTargets[idx];
    }

    static String makeFetchErrorMsg(String msg, IN in, long lsn, byte state) {

        /*
         * Bolster the exception with the LSN, which is critical for
         * debugging. Otherwise, the exception propagates upward and loses the
         * problem LSN.
         */
        StringBuffer sb = new StringBuffer();
        sb.append("fetchTarget of ");
        if (lsn == DbLsn.NULL_LSN) {
            sb.append("null lsn");
        } else {
            sb.append(DbLsn.getNoFormatString(lsn));
        }
        if (in != null) {
            sb.append(" parent IN=").append(in.getNodeId());
            sb.append(" lastFullVersion=");
            sb.append(DbLsn.getNoFormatString(in.getLastFullVersion()));
            sb.append(" parent.getDirty()=").append(in.getDirty());
        }
        sb.append(" state=").append(state);
        sb.append(" ").append(msg);
        return sb.toString();
    }

    /*
     * All methods that modify the entry array must adjust memory sizing.
     */

    /**
     * Set the idx'th entry of this node.
     */
    public void setEntry(int idx,
			 Node target,
			 byte[] keyVal,
                         long lsn,
			 byte state) {
        
	long oldSize = getEntryInMemorySize(idx);
	int newNEntries = idx + 1;
        if (newNEntries > nEntries) {

	    /*
	     * If the new entry is going to bump nEntries, then we don't need
	     * the size and LSN accounting included in oldSize.
	     */
            nEntries = newNEntries;
	    oldSize = 0;
        }
	entryTargets[idx] = target;
	entryKeyVals[idx] = keyVal;
	setLsnElement(idx, lsn);
	entryStates[idx] = state;
	long newSize = getEntryInMemorySize(idx);
	updateMemorySize(oldSize, newSize);
        setDirty(true);
    }

    /**
     * Update the idx'th entry of this node.
     *
     * Note: does not dirty the node.
     */
    public void updateEntry(int idx, Node node) {
	long oldSize = getEntryInMemorySize(idx);
	setTarget(idx, node);
	long newSize = getEntryInMemorySize(idx);
        updateMemorySize(oldSize, newSize);
    }

    /**
     * Update the idx'th entry of this node.
     */
    public void updateEntry(int idx, Node node, long lsn) {
	long oldSize = getEntryInMemorySize(idx);
        if (notOverwritingDeferredWriteEntry(lsn)) {
            setLsn(idx, lsn);
        }
	setTarget(idx, node);
	long newSize = getEntryInMemorySize(idx);
        updateMemorySize(oldSize, newSize);
        setDirty(true);
    }

    /**
     * Update the idx'th entry of this node.
     */
    public void updateEntry(int idx, Node node, long lsn, byte[] key) {
	long oldSize = getEntryInMemorySize(idx);
        if (notOverwritingDeferredWriteEntry(lsn)) {
            setLsn(idx, lsn);
        }
	setTarget(idx, node);
	setKey(idx, key);
	long newSize = getEntryInMemorySize(idx);
        updateMemorySize(oldSize, newSize);
        setDirty(true);
    }

    /**
     * Update the idx'th entry of this node.
     */
    public void updateEntry(int idx, long lsn) {
        if (notOverwritingDeferredWriteEntry(lsn)) {
            setLsn(idx, lsn);
        }
        setDirty(true);
    }

    /**
     * Update the idx'th entry of this node.
     */
    public void updateEntry(int idx, long lsn, byte state) {
        if (notOverwritingDeferredWriteEntry(lsn)) {
            setLsn(idx, lsn);
        }
	entryStates[idx] = state;
        setDirty(true);
    }

    /**
     * Update the idx'th entry of this node. This flavor is used when the
     * target LN is being modified, by an operation like a delete or update. We
     * don't have to check whether the LSN has been nulled or not, because we
     * know an LSN existed before. Also, the modification of the target is done
     * in the caller, so instead of passing in the old and new nodes, we pass
     * in the old and new node sizes.
     */
    public void updateEntry(int idx,
			    long lsn,
			    long oldLNSize,
			    long newLNSize) {
        updateMemorySize(oldLNSize, newLNSize);
        if (notOverwritingDeferredWriteEntry(lsn)) {
            setLsn(idx, lsn);
        }
        setDirty(true);
    }

    /**
     * Update the idx'th entry of this node.  Only update the key if the new
     * key is less than the existing key.
     */
    private void updateEntryCompareKey(int idx,
				       Node node,
				       long lsn,
				       byte[] key) {
	long oldSize = getEntryInMemorySize(idx);
        if (notOverwritingDeferredWriteEntry(lsn)) {
            setLsn(idx, lsn);
        }
	setTarget(idx, node);
	byte[] existingKey = getKey(idx);
	int s = Key.compareKeys(key, existingKey, getKeyComparator());
	if (s < 0) {
	    setKey(idx, key);
	}
	long newSize = getEntryInMemorySize(idx);
        updateMemorySize(oldSize, newSize);
        setDirty(true);
    }

    /**
     * When a deferred write database calls one of the optionalLog methods,
     * it may receive a DbLsn.NULL_LSN as the return value, because the
     * logging didn't really happen. A NULL_LSN should never overwrite a
     * valid lsn (that resulted from Database.sync() or eviction), lest
     * we lose the handle to the last on disk version.
     */
    boolean notOverwritingDeferredWriteEntry(long newLsn) {
        if (databaseImpl.isDeferredWrite() &&
            (newLsn == DbLsn.NULL_LSN)) {
            return false;
        } else 
            return true;
    }

    /*
     * Memory usage calculations.
     */
    public boolean verifyMemorySize() {

        long calcMemorySize = computeMemorySize();
        if (calcMemorySize != inMemorySize) {

            String msg = "-Warning: Out of sync. " +
                "Should be " + calcMemorySize +
                " / actual: " +
                inMemorySize + " node: " + getNodeId();
            Tracer.trace(Level.INFO,
                         databaseImpl.getDbEnvironment(),
                         msg);
                         
            System.out.println(msg);

            return false;
        } else {
            return true;
        }
    }

    /**
     * Return the number of bytes used by this IN.  Latching is up to the
     * caller.
     */
    public long getInMemorySize() {
        return inMemorySize;
    }

    private long getEntryInMemorySize(int idx) {
	return getEntryInMemorySize(entryKeyVals[idx], 
                                    entryTargets[idx]);
    }

    protected long getEntryInMemorySize(byte[] key, Node target) {

        /*
         * Do not count state size here, since it is counted as overhead
         * during initialization.
         */
	long ret = 0;
	if (key != null) {
            ret += MemoryBudget.byteArraySize(key.length);
	}
	if (target != null) {
	    ret += target.getMemorySizeIncludedByParent();
	}
	return ret;
    }

    /**
     * Count up the memory usage attributable to this node alone. LNs children
     * are counted by their BIN/DIN parents, but INs are not counted by their
     * parents because they are resident on the IN list.
     */
    protected long computeMemorySize() {
        MemoryBudget mb = databaseImpl.getDbEnvironment().getMemoryBudget();
        long calcMemorySize = getMemoryOverhead(mb);
	calcMemorySize += computeLsnOverhead();
        for (int i = 0; i < nEntries; i++) {
            calcMemorySize += getEntryInMemorySize(i);
        }
        /* XXX Need to update size when changing the identifierKey.
           if (identifierKey != null) {
           calcMemorySize +=
           MemoryBudget.byteArraySize(identifierKey.length);
           }
        */

        if (provisionalObsolete != null) {
            calcMemorySize += provisionalObsolete.size() *
                              MemoryBudget.LONG_LIST_PER_ITEM_OVERHEAD;
        }

        return calcMemorySize;
    }

    /* Called once at environment startup by MemoryBudget. */
    public static long computeOverhead(DbConfigManager configManager) 
        throws DatabaseException {

        /* 
	 * Overhead consists of all the fields in this class plus the
	 * entry arrays in the IN class.
         */
        return MemoryBudget.IN_FIXED_OVERHEAD +
            IN.computeArraysOverhead(configManager);
    }

    private int computeLsnOverhead() {
	if (entryLsnLongArray == null) {
	    return MemoryBudget.byteArraySize(entryLsnByteArray.length);
	} else {
	    return MemoryBudget.BYTE_ARRAY_OVERHEAD +
		entryLsnLongArray.length * MemoryBudget.LONG_OVERHEAD;
	}
    }

    protected static long computeArraysOverhead(DbConfigManager configManager) 
        throws DatabaseException {

        /* Count three array elements: states, Keys, and Nodes */
        int capacity = configManager.getInt(EnvironmentParams.NODE_MAX);
        return
            MemoryBudget.byteArraySize(capacity) + // state array
            (capacity *
	     (2 * MemoryBudget.ARRAY_ITEM_OVERHEAD)); // keys + nodes
    }
    
    /* Overridden by subclasses. */
    protected long getMemoryOverhead(MemoryBudget mb) {
        return mb.getINOverhead();
    }

    protected void updateMemorySize(ChildReference oldRef,
				    ChildReference newRef) {
        long delta = 0;
        if (newRef != null) {
            delta = getEntryInMemorySize(newRef.getKey(), newRef.getTarget());
        }

        if (oldRef != null) {
            delta -= getEntryInMemorySize(oldRef.getKey(), oldRef.getTarget());
        }
        changeMemorySize(delta);
    }

    protected void updateMemorySize(long oldSize, long newSize) {
        long delta = newSize - oldSize;
        changeMemorySize(delta);
    }

    void updateMemorySize(Node oldNode, Node newNode) {
        long delta = 0;
        if (newNode != null) {
            delta = newNode.getMemorySizeIncludedByParent();
        }

        if (oldNode != null) {
            delta -= oldNode.getMemorySizeIncludedByParent();
        }
        changeMemorySize(delta);
    }

    private void changeMemorySize(long delta) {
        inMemorySize += delta;

        /*
         * Only update the environment cache usage stats if this IN is actually
         * on the IN list. For example, when we create new INs, they are
         * manipulated off the IN list before being added; if we updated the
         * environment wide cache then, we'd end up double counting.
         */
        if (inListResident) {
            MemoryBudget mb =
                databaseImpl.getDbEnvironment().getMemoryBudget();

	    accumulatedDelta += delta;
	    if (accumulatedDelta > ACCUMULATED_LIMIT ||
		accumulatedDelta < -ACCUMULATED_LIMIT) {
		mb.updateTreeMemoryUsage(accumulatedDelta);
		accumulatedDelta = 0;
	    }
        }
    }

    public int getAccumulatedDelta() {
	return accumulatedDelta;
    }

    public void setInListResident(boolean resident) {
        inListResident = resident;
    }

    /**
     * Returns whether the given key is greater than or equal to the first key
     * in the IN and less than or equal to the last key in the IN.  This method
     * is used to determine whether a key to be inserted belongs in this IN,
     * without doing a tree search.  If false is returned it is still possible
     * that the key belongs in this IN, but a tree search must be performed to
     * find out.
     */
    public boolean isKeyInBounds(byte[] keyVal) {

        if (nEntries < 2) {
            return false;
        }

        Comparator userCompareToFcn = getKeyComparator();
        int cmp;
        byte[] myKey;

        /* Compare key given to my first key. */
        myKey = entryKeyVals[0];
        cmp = Key.compareKeys(keyVal, myKey, userCompareToFcn);
        if (cmp < 0) {
            return false;
        }

        /* Compare key given to my last key. */
        myKey = entryKeyVals[nEntries - 1];
        cmp = Key.compareKeys(keyVal, myKey, userCompareToFcn);
        if (cmp > 0) {
            return false;
        }

        return true;
    }

    /**
     * Find the entry in this IN for which key arg is >= the key.
     *
     * Currently uses a binary search, but eventually, this may use binary or
     * linear search depending on key size, number of entries, etc.
     *
     * Note that the 0'th entry's key is treated specially in an IN.  It always
     * compares lower than any other key.
     *
     * This is public so that DbCursorTest can access it.
     *
     * @param key - the key to search for.
     * @param indicateIfDuplicate - true if EXACT_MATCH should
     * be or'd onto the return value if key is already present in this node.
     * @param exact - true if an exact match must be found.
     * @return offset for the entry that has a key >= the arg.  0 if key
     * is less than the 1st entry.  -1 if exact is true and no exact match
     * is found.  If indicateIfDuplicate is true and an exact match was found
     * then EXACT_MATCH is or'd onto the return value.
     */
    public int findEntry(byte[] key,
                         boolean indicateIfDuplicate,
                         boolean exact) {
        int high = nEntries - 1;
        int low = 0;
        int middle = 0;

        Comparator userCompareToFcn = getKeyComparator();

        /*
         * IN's are special in that they have a entry[0] where the key is a
         * virtual key in that it always compares lower than any other key.
         * BIN's don't treat key[0] specially.  But if the caller asked for an
         * exact match or to indicate duplicates, then use the key[0] and
         * forget about the special entry zero comparison.
         */
        boolean entryZeroSpecialCompare =
            entryZeroKeyComparesLow() && !exact && !indicateIfDuplicate;

        assert nEntries >= 0;
        
        while (low <= high) {
            middle = (high + low) / 2;
            int s;
            byte[] middleKey = null;
            if (middle == 0 && entryZeroSpecialCompare) {
                s = 1;
            } else {
                middleKey = entryKeyVals[middle];
                s = Key.compareKeys(key, middleKey, userCompareToFcn);
            }
            if (s < 0) {
                high = middle - 1;
            } else if (s > 0) {
                low = middle + 1;
            } else {
                int ret;
                if (indicateIfDuplicate) {
                    ret = middle | EXACT_MATCH;
                } else {
                    ret = middle;
                }

                if ((ret >= 0) && exact && isEntryKnownDeleted(ret & 0xffff)) {
                    return -1;
                } else {
                    return ret;
                }
            }
        }

        /* 
	 * No match found.  Either return -1 if caller wanted exact matches
	 * only, or return entry for which arg key is > entry key.
	 */
        if (exact) {
            return -1;
        } else {
            return high;
        }
    }

    /**
     * Inserts the argument ChildReference into this IN.  Assumes this node is
     * already latched by the caller.
     *
     * @param entry The ChildReference to insert into the IN.
     *
     * @return true if the entry was successfully inserted, false
     * if it was a duplicate.
     *
     * @throws InconsistentNodeException if the node is full
     * (it should have been split earlier).
     */
    public boolean insertEntry(ChildReference entry)
        throws DatabaseException {

        return (insertEntry1(entry) & INSERT_SUCCESS) != 0;
    }

    /**
     * Same as insertEntry except that it returns the index where the dup was
     * found instead of false.  The return value is |'d with either
     * INSERT_SUCCESS or EXACT_MATCH depending on whether the entry was
     * inserted or it was a duplicate, resp.
     *
     * This returns a failure if there's a duplicate match. The caller must do
     * the processing to check if the entry is actually deleted and can be
     * overwritten. This is foisted upon the caller rather than handled in this
     * object because there may be some latch releasing/retaking in order to
     * check a child LN.
     *
     * Inserts the argument ChildReference into this IN.  Assumes this node is
     * already latched by the caller.
     *
     * @param entry The ChildReference to insert into the IN.
     *
     * @return either (1) the index of location in the IN where the entry was
     * inserted |'d with INSERT_SUCCESS, or (2) the index of the duplicate in
     * the IN |'d with EXACT_MATCH if the entry was found to be a duplicate.
     *
     * @throws InconsistentNodeException if the node is full (it should have
     * been split earlier).
     */
    public int insertEntry1(ChildReference entry)
        throws DatabaseException {

	if (nEntries >= entryTargets.length) {
	    compress(null, true, null);
	}

	if (nEntries < entryTargets.length) {
	    byte[] key = entry.getKey();

	    /* 
	     * Search without requiring an exact match, but do let us know the
	     * index of the match if there is one.
	     */
	    int index = findEntry(key, true, false);

	    if (index >= 0 && (index & EXACT_MATCH) != 0) {

		/* 
		 * There is an exact match.  Don't insert; let the caller
		 * decide what to do with this duplicate.
		 */
		return index;
	    } else {

		/* 
		 * There was no key match, so insert to the right of this
		 * entry.
		 */
		index++;
	    }

	    /* We found a spot for insert, shift entries as needed. */
	    if (index < nEntries) {
		int oldSize = computeLsnOverhead();

		/* 
		 * Adding elements to the LSN array can change the space used.
		 */
		shiftEntriesRight(index);
		changeMemorySize(computeLsnOverhead() - oldSize);
	    }
	    entryTargets[index] = entry.getTarget();
	    entryKeyVals[index] = entry.getKey();
	    setLsnElement(index, entry.getLsn());
	    entryStates[index] = entry.getState();
	    nEntries++;
	    adjustCursorsForInsert(index);
	    updateMemorySize(0, getEntryInMemorySize(index));
	    setDirty(true);
	    return (index | INSERT_SUCCESS);
	} else {
	    throw new InconsistentNodeException
		("Node " + getNodeId() +
		 " should have been split before calling insertEntry");
	}
    }

    /**
     * Deletes the ChildReference with the key arg from this IN.  Assumes this
     * node is already latched by the caller.
     *
     * This seems to only be used by INTest.
     *
     * @param key The key of the reference to delete from the IN.
     *
     * @param maybeValidate true if assert validation should occur prior to
     * delete.  Set this to false during recovery.
     *
     * @return true if the entry was successfully deleted, false if it was not
     * found.
     */
    boolean deleteEntry(byte[] key, boolean maybeValidate)
        throws DatabaseException {

        if (nEntries == 0) {
            return false; // caller should put this node on the IN cleaner list
        }

        int index = findEntry(key, false, true);
        if (index < 0) {
            return false;
        }

        return deleteEntry(index, maybeValidate);
    }

    /**
     * Deletes the ChildReference at index from this IN.  Assumes this node is
     * already latched by the caller.
     *
     * @param index The index of the entry to delete from the IN.
     *
     * @param maybeValidate true if asserts are enabled.
     *
     * @return true if the entry was successfully deleted, false if it was not
     * found.
     */
    public boolean deleteEntry(int index, boolean maybeValidate)
        throws DatabaseException {

	if (nEntries == 0) {
	    return false;
	}

        /* Check the subtree validation only if maybeValidate is true. */
        assert maybeValidate ? 
            validateSubtreeBeforeDelete(index) :
            true;

	if (index < nEntries) {
	    updateMemorySize(getEntryInMemorySize(index), 0);
	    int oldLSNArraySize = computeLsnOverhead();
	    /* LSNArray.setElement can mutate to an array of longs. */
	    for (int i = index; i < nEntries - 1; i++) {
		setEntryInternal(i + 1, i);
	    }
	    clearEntry(nEntries - 1);
	    updateMemorySize(oldLSNArraySize, computeLsnOverhead());
	    nEntries--;
	    setDirty(true);
	    setProhibitNextDelta();

	    /* 
	     * Note that we don't have to adjust cursors for delete, since
	     * there should be nothing pointing at this record.
	     */
	    traceDelete(Level.FINEST, index);
	    return true;
	} else {
	    return false;
	}
    }

    /**
     * Do nothing since INs don't support deltas.
     */
    public void setProhibitNextDelta() {
    }

    /* Called by the incompressor. */
    public boolean compress(BINReference binRef,
                            boolean canFetch,
                            UtilizationTracker tracker) 
        throws DatabaseException {

	return false;
    }

    public boolean isCompressible() {
        return false;
    }

    /* 
     * Validate the subtree that we're about to delete.  Make sure there aren't
     * more than one valid entry on each IN and that the last level of the tree
     * is empty. Also check that there are no cursors on any bins in this
     * subtree. Assumes caller is holding the latch on this parent node.
     *
     * While we could latch couple down the tree, rather than hold latches as
     * we descend, we are presumably about to delete this subtree so
     * concurrency shouldn't be an issue.
     *
     * @return true if the subtree rooted at the entry specified by "index" is
     * ok to delete.
     */
    boolean validateSubtreeBeforeDelete(int index)
        throws DatabaseException {
        
	if (index >= nEntries) {

	    /* 
	     * There's no entry here, so of course this entry is deletable.
	     */
	    return true;
	} else {
	    Node child = fetchTarget(index);
	    return child != null && child.isValidForDelete();
	}
    }

    /**
     * Return true if this node needs splitting.  For the moment, needing to be
     * split is defined by there being no free entries available.
     */
    public boolean needsSplitting() {
        if ((entryTargets.length - nEntries) < 1) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Indicates whether whether entry 0's key is "special" in that it always
     * compares less than any other key.  BIN's don't have the special key, but
     * IN's do.
     */
    boolean entryZeroKeyComparesLow() {
        return true;
    }

    /**
     * Split this into two nodes.  Parent IN is passed in parent and should be
     * latched by the caller.
     *
     * childIndex is the index in parent of where "this" can be found.
     * @return lsn of the newly logged parent
     */
    void split(IN parent, int childIndex, int maxEntries)
        throws DatabaseException {

        splitInternal(parent, childIndex, maxEntries, -1);
    }

    protected void splitInternal(IN parent,
				 int childIndex,
				 int maxEntries,
				 int splitIndex)
        throws DatabaseException {

        /* 
         * Find the index of the existing identifierKey so we know which IN
         * (new or old) to put it in.
         */
        if (identifierKey == null) {
            throw new InconsistentNodeException("idkey is null");
        }
        int idKeyIndex = findEntry(identifierKey, false, false);

	if (splitIndex < 0) {
	    splitIndex = nEntries / 2;
	}

        int low, high;
        IN newSibling = null;

        if (idKeyIndex < splitIndex) {

            /* 
             * Current node (this) keeps left half entries.  Right half entries
             * will go in the new node.
             */
            low = splitIndex;
            high = nEntries;
        } else {

            /* 
	     * Current node (this) keeps right half entries.  Left half entries
	     * and entry[0] will go in the new node.
	     */
            low = 0;
            high = splitIndex;
        }

        byte[] newIdKey = entryKeyVals[low];
	long parentLsn = DbLsn.NULL_LSN;

        newSibling = createNewInstance(newIdKey, maxEntries, level);
        newSibling.latch();
        long oldMemorySize = inMemorySize;
	try {
        
	    int toIdx = 0;
	    boolean deletedEntrySeen = false;
	    BINReference binRef = null;
	    for (int i = low; i < high; i++) {
		byte[] thisKey = entryKeyVals[i];
		if (isEntryPendingDeleted(i)) {
		    if (!deletedEntrySeen) {
			deletedEntrySeen = true;
			assert (newSibling instanceof BIN);
			binRef = ((BIN) newSibling).createReference();
		    }
		    binRef.addDeletedKey(new Key(thisKey));
		}
		newSibling.setEntry(toIdx++,
				    entryTargets[i],
				    thisKey,
				    getLsn(i),
				    entryStates[i]);
                clearEntry(i);
	    }

	    if (deletedEntrySeen) {
		databaseImpl.getDbEnvironment().
		    addToCompressorQueue(binRef, false);
	    }

	    int newSiblingNEntries = (high - low);

	    /* 
	     * Remove the entries that we just copied into newSibling from this
	     * node.
	     */
	    if (low == 0) {
		shiftEntriesLeft(newSiblingNEntries);
	    }

	    newSibling.nEntries = toIdx;
	    nEntries -= newSiblingNEntries;
	    setDirty(true);

	    adjustCursors(newSibling, low, high);

	    /* 
	     * Parent refers to child through an element of the entries array.
	     * Depending on which half of the BIN we copied keys from, we
	     * either have to adjust one pointer and add a new one, or we have
	     * to just add a new pointer to the new sibling.
	     *
	     * Note that we must use the provisional form of logging because
	     * all three log entries must be read atomically. The parent must
	     * get logged last, as all referred-to children must preceed
	     * it. Provisional entries guarantee that all three are processed
	     * as a unit. Recovery skips provisional entries, so the changed
	     * children are only used if the parent makes it out to the log.
	     */
	    EnvironmentImpl env = databaseImpl.getDbEnvironment();
	    LogManager logManager = env.getLogManager();
	    INList inMemoryINs = env.getInMemoryINs();

	    long newSiblingLsn =
                newSibling.optionalLogProvisional(logManager, parent);

	    long myNewLsn = optionalLogProvisional(logManager, parent);

	    /*
	     * When we update the parent entry, we use updateEntryCompareKey so
	     * that we don't replace the parent's key that points at 'this'
	     * with a key that is > than the existing one.  Replacing the
	     * parent's key with something > would effectively render a piece
	     * of the subtree inaccessible.  So only replace the parent key
	     * with something <= the existing one.  See tree/SplitTest.java for
	     * more details on the scenario.
	     */
	    if (low == 0) {

		/* 
		 * Change the original entry to point to the new child and add
		 * an entry to point to the newly logged version of this
		 * existing child.
		 */
                if (childIndex == 0) {
                    parent.updateEntryCompareKey(childIndex, newSibling,
                                                 newSiblingLsn, newIdKey);
                } else {
                    parent.updateEntry(childIndex, newSibling, newSiblingLsn);
                }

		boolean insertOk = parent.insertEntry
		    (new ChildReference(this, entryKeyVals[0], myNewLsn));
		assert insertOk;
	    } else {

		/* 
		 * Update the existing child's LSN to reflect the newly logged
		 * version and insert new child into parent.
		 */
		if (childIndex == 0) {

		    /*
		     * This's idkey may be < the parent's entry 0 so we need to
		     * update parent's entry 0 with the key for 'this'.
		     */
		    parent.updateEntryCompareKey
			(childIndex, this, myNewLsn, entryKeyVals[0]);
		} else {
		    parent.updateEntry(childIndex, this, myNewLsn);
		}
		boolean insertOk = parent.insertEntry
		    (new ChildReference(newSibling, newIdKey, newSiblingLsn));
		assert insertOk;
	    }

	    parentLsn = parent.optionalLog(logManager);
            
            /* 
             * Maintain dirtiness if this is the root, so this parent
             * will be checkpointed. Other parents who are not roots
             * are logged as part of the propagation of splits
             * upwards.
             */
            if (parent.isRoot()) {
                parent.setDirty(true);
            }

            /* 
             * Update size. newSibling and parent are correct, but this IN has
             * had its entries shifted and is not correct.
             */
            long newSize = computeMemorySize();
            updateMemorySize(oldMemorySize, newSize);
	    inMemoryINs.add(newSibling);
        
	    /* Debug log this information. */
	    traceSplit(Level.FINE, parent,
		       newSibling, parentLsn, myNewLsn,
		       newSiblingLsn, splitIndex, idKeyIndex, childIndex);
	} finally {
	    newSibling.releaseLatch();
	}
    }

    /**
     * Called when we know we are about to split on behalf of a key that is the
     * minimum (leftSide) or maximum (!leftSide) of this node.  This is
     * achieved by just forcing the split to occur either one element in from
     * the left or the right (i.e. splitIndex is 1 or nEntries - 1).
     */
    void splitSpecial(IN parent,
		      int parentIndex,
		      int maxEntriesPerNode,
		      byte[] key,
		      boolean leftSide)
	throws DatabaseException {

	int index = findEntry(key, false, false);
	if (leftSide &&
	    index == 0) {
	    splitInternal(parent, parentIndex, maxEntriesPerNode, 1);
	} else if (!leftSide &&
		   index == (nEntries - 1)) {
	    splitInternal(parent, parentIndex, maxEntriesPerNode,
			  nEntries - 1);
	} else {
            split(parent, parentIndex, maxEntriesPerNode);
	}
    }

    void adjustCursors(IN newSibling,
                       int newSiblingLow,
                       int newSiblingHigh) {
        /* Cursors never refer to IN's. */
    }

    void adjustCursorsForInsert(int insertIndex) {
        /* Cursors never refer to IN's. */
    }

    /**
     * Return the relevant user defined comparison function for this type of
     * node.  For IN's and BIN's, this is the BTree Comparison function.
     */
    public Comparator getKeyComparator() {
        return databaseImpl.getBtreeComparator();
    }

    /**
     * Shift entries to the right starting with (and including) the entry at
     * index. Caller is responsible for incrementing nEntries.
     *
     * @param index - The position to start shifting from.
     */
    private void shiftEntriesRight(int index) {
        for (int i = nEntries; i > index; i--) {
	    setEntryInternal(i - 1, i);
        }
        clearEntry(index);
        setDirty(true);
    }

    /**
     * Shift entries starting at the byHowMuch'th element to the left, thus
     * removing the first byHowMuch'th elements of the entries array.  This
     * always starts at the 0th entry.  Caller is responsible for decrementing
     * nEntries.
     *
     * @param byHowMuch - The number of entries to remove from the left side
     * of the entries array.
     */
    private void shiftEntriesLeft(int byHowMuch) {
        for (int i = 0; i < nEntries - byHowMuch; i++) {
	    setEntryInternal(i + byHowMuch, i);
        }
        for (int i = nEntries - byHowMuch; i < nEntries; i++) {
	    clearEntry(i);
        }
        setDirty(true);
    }

    /**
     * Check that the IN is in a valid state.  For now, validity means that the
     * keys are in sorted order and that there are more than 0 entries.
     * maxKey, if non-null specifies that all keys in this node must be less
     * than maxKey.
     */
    public void verify(byte[] maxKey)
        throws DatabaseException {

	/********* never code, but may be used for the basis of a verify()
		   method in the future.
	try {
	    Comparator userCompareToFcn =
		(databaseImpl == null ? null : getKeyComparator());

	    byte[] key1 = null;
	    for (int i = 1; i < nEntries; i++) {
		key1 = entryKeyVals[i];
		byte[] key2 = entryKeyVals[i - 1];

		int s = Key.compareKeys(key1, key2, userCompareToFcn);
		if (s <= 0) {
		    throw new InconsistentNodeException
			("IN " + getNodeId() + " key " + (i-1) +
			 " (" + Key.dumpString(key2, 0) +
			 ") and " +
			 i + " (" + Key.dumpString(key1, 0) +
			 ") are out of order");
		}
	    }

	    boolean inconsistent = false;
	    if (maxKey != null && key1 != null) {
                if (Key.compareKeys(key1, maxKey, userCompareToFcn) >= 0) {
                    inconsistent = true;
                }
	    }

	    if (inconsistent) {
		throw new InconsistentNodeException
		    ("IN " + getNodeId() +
		     " has entry larger than next entry in parent.");
	    }
	} catch (DatabaseException DE) {
	    DE.printStackTrace(System.out);
	}
	*****************/
    }

    /**
     * Add self and children to this in-memory IN list. Called by recovery, can
     * run with no latching.
     */
    void rebuildINList(INList inList) 
        throws DatabaseException {

        /* 
         * Recompute your in memory size first and then add yourself to the
         * list.
         */
        initMemorySize();
        inList.add(this);

        /* 
         * Add your children if they're resident. (LNs know how to stop the
         * flow).
         */
        for (int i = 0; i < nEntries; i++) {
            Node n = getTarget(i);
            if (n != null) {
                n.rebuildINList(inList);
            }
        }
    }

    /**
     * Remove self and children from the in-memory IN list. The INList latch is
     * already held before this is called.  Also count removed nodes as
     * obsolete.
     */
    void accountForSubtreeRemoval(INList inList,
                                  UtilizationTracker tracker) 
        throws DatabaseException {

        if (nEntries > 1) {
            throw new DatabaseException
                ("Found non-deletable IN " + getNodeId() +
                 " while flushing INList. nEntries = " + nEntries);
        }

        /* Remove self. */
        inList.removeLatchAlreadyHeld(this);

        /* Count as obsolete. */
        if (lastFullVersion != DbLsn.NULL_LSN) {
            tracker.countObsoleteNode(lastFullVersion, getLogType(), 0);
        }

        /*
         * Remove your children.  They should already be resident.  (LNs know
         * how to stop.)
         */
        for (int i = 0; i < nEntries; i++) {
            Node n = fetchTarget(i);
            if (n != null) {
                n.accountForSubtreeRemoval
                    (inList, tracker);
            }
        }
    }

    /**
     * Check if this node fits the qualifications for being part of a deletable
     * subtree. It can only have one IN child and no LN children.
     *
     * We assume that this is only called under an assert.
     */
    boolean isValidForDelete()
        throws DatabaseException {

	/* 
	 * Can only have one valid child, and that child should be
	 * deletable.
	 */
	if (nEntries > 1) {            // more than 1 entry.
	    return false;
	} else if (nEntries == 1) {    // 1 entry, check child
	    Node child = fetchTarget(0);
	    if (child == null) {
		return false;
	    }
	    child.latchShared();
	    boolean ret = child.isValidForDelete();
	    child.releaseLatch();
	    return ret;
	} else {                       // 0 entries.
	    return true;
	}
    }

    /**
     * See if you are the parent of this child. If not, find a child of your's
     * that may be the parent, and return it. If there are no possiblities,
     * return null. Note that the keys of the target are passed in so we don't
     * have to latch the target to look at them. Also, this node is latched
     * upon entry.
     *
     * @param doFetch If true, fetch the child in the pursuit of this search.
     * If false, give up if the child is not resident. In that case, we have
     * a potential ancestor, but are not sure if this is the parent.
     */
    void findParent(Tree.SearchType searchType,
                    long targetNodeId,
		    boolean targetContainsDuplicates,
                    boolean targetIsRoot,
                    byte[] targetMainTreeKey,
                    byte[] targetDupTreeKey,
                    SearchResult result,
                    boolean requireExactMatch,
                    boolean updateGeneration,
                    int targetLevel,
                    List trackingList,
                    boolean doFetch)
        throws DatabaseException {

        assert isLatchOwnerForWrite();

        /* We are this node -- there's no parent in this subtree. */
        if (getNodeId() == targetNodeId) {
            releaseLatch();
            result.exactParentFound = false;  // no parent exists
            result.keepSearching = false;
            result.parent = null;
            return;
        }

        /* Find an entry */
        if (getNEntries() == 0) {

            /*
             * No more children, can't descend anymore. Return this node, you
             * could be the parent.
             */
            result.keepSearching = false;
            result.exactParentFound = false;
            if (requireExactMatch) {
                releaseLatch();
                result.parent = null;
            } else {
                result.parent = this;
                result.index = -1;
            }
            return;
        } else {
            if (searchType == Tree.SearchType.NORMAL) {
                /* Look for the entry matching key in the current node. */
                result.index = findEntry(selectKey(targetMainTreeKey,
                                                   targetDupTreeKey),
                                         false, false);
            } else if (searchType == Tree.SearchType.LEFT) {
                /* Left search, always take the 0th entry. */
                result.index = 0;
            } else if (searchType == Tree.SearchType.RIGHT) {
                /* Right search, always take the highest entry. */
                result.index = nEntries - 1;
            } else {
                throw new IllegalArgumentException
                    ("Invalid value of searchType: " + searchType);
            }

            if (result.index < 0) {
                result.keepSearching = false;
                result.exactParentFound = false;
                if (requireExactMatch) {
                    releaseLatch();
                    result.parent = null;
                } else {
                    /* This node is going to be the prospective parent. */
                    result.parent = this;
                }
                return;
            }
            
            /*
             * Get the child node that matches.  If fetchTarget returns null, a
             * deleted LN was cleaned.
             */
            Node child = null;
            boolean isDeleted = false;
            if (isEntryKnownDeleted(result.index)) {
                isDeleted = true;
            } else if (doFetch) {
                child = fetchTarget(result.index);
                if (child == null) {
                    isDeleted = true;
                }
            } else {
                child = getTarget(result.index);
            }

            /* The child is a deleted cleaned entry or is knownDeleted. */
            if (isDeleted) {
                result.exactParentFound = false;
                result.keepSearching = false;
                if (requireExactMatch) {
                    result.parent = null;
                    releaseLatch();
                } else {
                    result.parent = this;
                }
                return;
            }

            /* Try matching by level. */
            if (targetLevel >= 0 && level == targetLevel + 1) {
                result.exactParentFound = true;
                result.parent = this;
                result.keepSearching = false;
                return;
            }
            
            if (child == null) {
                assert !doFetch;

                /*
                 * This node will be the possible parent. 
                 */
                result.keepSearching = false;
                result.exactParentFound = false;
                result.parent = this;
                result.childNotResident = true;
                return;
            }

            long childLsn = getLsn(result.index);

            /* 
             * Note that if the child node needs latching, it's done in
             * isSoughtNode.
             */
            if (child.isSoughtNode(targetNodeId, updateGeneration)) {
                /* We found the child, so this is the parent. */
                result.exactParentFound = true;
                result.parent = this;
                result.keepSearching = false;
                return;
            } else {

                /* 
                 * Decide whether we can descend, or the search is going to be
                 * unsuccessful or whether this node is going to be the future
                 * parent. It depends on what this node is, the target, and the
                 * child.
                 */
                descendOnParentSearch(result,
                                      targetContainsDuplicates,
                                      targetIsRoot,
                                      targetNodeId,
                                      child,
                                      requireExactMatch);

                /* If we're tracking, save the lsn and node id */
                if (trackingList != null) {
                    if ((result.parent != this) && (result.parent != null)) {
                        trackingList.add(new TrackingInfo(childLsn, 
                                                          child.getNodeId()));
                    }
                }
                return; 
            }
        }
    }

    /*
     * If this search can go further, return the child. If it can't, and you
     * are a possible new parent to this child, return this IN. If the search
     * can't go further and this IN can't be a parent to this child, return
     * null.
     */
    protected void descendOnParentSearch(SearchResult result,
                                         boolean targetContainsDuplicates,
                                         boolean targetIsRoot,
                                         long targetNodeId,
                                         Node child,
                                         boolean requireExactMatch) 
        throws DatabaseException {

        if (child.canBeAncestor(targetContainsDuplicates)) {
            /* We can search further. */
            releaseLatch();     
            result.parent = (IN) child;
        } else {

            /*
             * Our search ends, we didn't find it. If we need an exact match,
             * give up, if we only need a potential match, keep this node
             * latched and return it.
             */
            ((IN) child).releaseLatch();
            result.exactParentFound = false;
            result.keepSearching = false;

            if (requireExactMatch) {
                releaseLatch();
                result.parent = null;
            } else {
                result.parent = this;
            }
        }
    }

    /*
     * @return true if this IN is the child of the search chain. Note that
     * if this returns false, the child remains latched.
     */
    protected boolean isSoughtNode(long nid, boolean updateGeneration)
        throws DatabaseException {

        latch(updateGeneration);
        if (getNodeId() == nid) {
            releaseLatch();
            return true;
        } else {
            return false;
        }
    }

    /* 
     * An IN can be an ancestor of any internal node.
     */
    protected boolean canBeAncestor(boolean targetContainsDuplicates) {
        return true;
    }

    /**
     * Returns whether this node can be evicted.  This is faster than
     * (getEvictionType() == MAY_EVICT_NODE) because it does a more static,
     * stringent check and is used by the evictor after a node has been
     * selected, to check that it is still evictable. The more complex
     * evaluation done by getEvictionType() is used when initially selecting
     * a node for inclusion in the eviction set.
     */
    public boolean isEvictable() {

        if (isEvictionProhibited()) {
            return false;
        }

        /*
         * An IN can be evicted only if its resident children are all evictable
         * LNs, because those children can be logged (if dirty) and stripped
         * before this node is evicted.  Non-LN children or pinned LNs (MapLNs
         * for open DBs) will prevent eviction.
         */
        if (hasPinnedChildren()) {
            return false;
        }

        for (int i = 0; i < getNEntries(); i++) {
	    /* Target and LSN can be null in DW. Not evictable in that case. */
            if (getLsn(i) == DbLsn.NULL_LSN &&
		getTarget(i) == null) {
                return false;
            }
        }

        return true;
    }

    /**
     * Returns the eviction type for this IN, for use by the evictor.  Uses the
     * internal isEvictionProhibited and getChildEvictionType methods that may
     * be overridden by subclasses.
     *
     * This differs from isEvictable() because it does more detailed evaluation
     * about the degree of evictability. It's used generally when selecting
     * candidates for eviction.
     *
     * @return MAY_EVICT_LNS if evictable LNs may be stripped; otherwise,
     * MAY_EVICT_NODE if the node itself may be evicted; otherwise,
     * MAY_NOT_EVICT.
     */
    public int getEvictionType() {

        if (isEvictionProhibited()) {
            return MAY_NOT_EVICT;
        } else {
            return getChildEvictionType();
        }
    }

    /**
     * Returns whether the node is not evictable, irrespective of the status
     * of the children nodes.
     */
    boolean isEvictionProhibited() {

        if (isDbRoot()) {

            /*
             * Disallow eviction of a dirty DW DB root, since logging the MapLN
             * (via DbTree.modifyDbRoot) will make the all other changes to the
             * DW DB effectively non-provisional (durable).  This implies that
             * a DW DB root cannot be evicted until it is synced (or removed).
             * [#13415]
             */
            if (databaseImpl.isDeferredWrite() && getDirty()) {
                return true;
            }

            /*
             * Disallow eviction of the mapping and naming DB roots, because
             * the use count is not incremented for these DBs.  In addition,
             * their eviction and re-fetching is a special case that is not
             * worth supporting.  [#13415]
             */
            DatabaseId dbId = databaseImpl.getId();
            if (dbId.equals(DbTree.ID_DB_ID) ||
                dbId.equals(DbTree.NAME_DB_ID)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns whether any resident children are not LNs (are INs).
     * For an IN, that equates to whether there are any resident children
     * at all.
     */
    boolean hasPinnedChildren() {

        return hasResidentChildren();
    }

    /**
     * Returns the eviction type based on the status of child nodes,
     * irrespective of isEvictionProhibited.
     */
    int getChildEvictionType() {

        return hasResidentChildren() ? MAY_NOT_EVICT : MAY_EVICT_NODE;
    }

    /**
     * Returns whether any child is non-null.  Is final to indicate it is not
     * overridden (unlike hasPinnedChildren, isEvictionProhibited, etc).
     */
    final boolean hasResidentChildren() {

        for (int i = 0; i < getNEntries(); i++) {
            if (getTarget(i) != null) {
                return true;
            }
        }

        return false;
    }

    /*
     * DbStat support.
     */
    void accumulateStats(TreeWalkerStatsAccumulator acc) {
	acc.processIN(this, new Long(getNodeId()), getLevel());
    }

    /*
     * Logging support
     */

    /**
     * When splits and checkpoints intermingle in a deferred write databases,
     * a checkpoint target may appear which has a valid target but a null LSN.
     * Deferred write dbs are written out in checkpoint style by either
     * Database.sync() or a checkpoint which has cleaned a file containing
     * deferred write entries. For example,
     *   INa
     *    |
     *   BINb
     *
     *  A checkpoint or Database.sync starts
     *  The INList is traversed, dirty nodes are selected
     *  BINb is bypassed on the INList, since it's not dirty
     *  BINb is split, creating a new sibling, BINc, and dirtying INa
     *  INa is selected as a dirty node for the ckpt
     *
     * If this happens, INa is in the selected dirty set, but not its dirty
     * child BINb and new child BINc.
     * 
     * In a durable db, the existence of BINb and BINc are logged
     * anyway. But in a deferred write db, there is an entry that points to 
     * BINc, but no logged version.
     *
     * This will not cause problems with eviction, because INa can't be 
     * evicted until BINb and BINc are logged, are non-dirty, and are detached.
     * But it can cause problems at recovery, because INa will have a null LSN
     * for a valid entry, and the LN children of BINc will not find a home.
     * To prevent this, search for all dirty children that might have been
     * missed during the selection phase, and write them out. It's not 
     * sufficient to write only null-LSN children, because the existing sibling
     * must be logged lest LN children recover twice (once in the new sibling,
     * once in the old existing sibling.
     */
    public void logDirtyChildren() 
        throws DatabaseException {

        EnvironmentImpl envImpl = getDatabase().getDbEnvironment();

        /* Look for targets that are dirty. */
        for (int i = 0; i < getNEntries(); i++) {

            IN child = (IN) getTarget(i);
            if (child != null) {
                child.latch(false);
                try {
                    if (child.getDirty()) {
                        /* Ask descendents to log their children. */
                        child.logDirtyChildren();
                        long childLsn =
                            child.log(envImpl.getLogManager(),
                                      false, // allow deltas 
                                      true,  // is provisional 
                                      false, // proactive migration
                                      true,  // backgroundIO
                                      this); // provisional parent 
                        updateEntry(i, childLsn);
                    }
                } finally {
                    child.releaseLatch();
                }
            }
        }
    }
        
    /**
     * Log this IN and clear the dirty flag.
     */
    public long log(LogManager logManager)
        throws DatabaseException {

        return logInternal(logManager,
                           false,  // allowDeltas
                           false,  // isProvisional
                           false,  // proactiveMigration
                           false,  // backgroundIO
                           null);  // parent
    }

    /**
     * Log this node with all available options.
     */
    public long log(LogManager logManager,
                    boolean allowDeltas,
                    boolean isProvisional,
                    boolean proactiveMigration,
                    boolean backgroundIO,
                    IN parent) // for provisional
        throws DatabaseException {

        return logInternal(logManager,
                           allowDeltas,
                           isProvisional,
                           proactiveMigration,
                           backgroundIO,
                           parent);
    }

    /**
     * Log this IN and clear the dirty flag.
     */
    public long optionalLog(LogManager logManager)
        throws DatabaseException {

        if (databaseImpl.isDeferredWrite()) {
            return DbLsn.NULL_LSN;
        } else {
            return logInternal(logManager,
                               false,  // allowDeltas
                               false,  // isProvisional
                               false,  // proactiveMigration
                               false,  // backgroundIO
                               null);  // parent
        }
    }


    /**
     * Log this node provisionally and clear the dirty flag.
     * @param item object to be logged
     * @return LSN of the new log entry
     */
    public long optionalLogProvisional(LogManager logManager, IN parent)
        throws DatabaseException {

        if (databaseImpl.isDeferredWrite()) {
            return DbLsn.NULL_LSN;
        } else {
            return logInternal(logManager,
                               false,  // allowDeltas
                               true,   // isProvisional
                               false,  // proactiveMigration
                               false,  // backgroundIO
                               parent);
        }
    }

    /**
     * Decide how to log this node. INs are always logged in full.  Migration
     * never performed since it only applies to BINs.
     */
    protected long logInternal(LogManager logManager,
			       boolean allowDeltas,
                               boolean isProvisional,
                               boolean proactiveMigration,
                               boolean backgroundIO,
                               IN parent)
        throws DatabaseException {

        /*
         * The last version of this node must be counted obsolete at the
         * correct time. If logging non-provisionally, the last version of this
         * node and any provisionally logged descendants are immediately
         * obsolete and can be flushed. If logging provisionally, the last
         * version isn't obsolete until an ancestor is logged
         * non-provisionally, so propagate obsolete lsns upwards.
         */
        long lsn = logManager.log
            (new INLogEntry(this), isProvisional, backgroundIO,
             isProvisional ? DbLsn.NULL_LSN : lastFullVersion, 0);

        if (isProvisional) {
            if (parent != null) {
                parent.trackProvisionalObsolete
                    (this, lastFullVersion, DbLsn.NULL_LSN);
            }
        } else {
            flushProvisionalObsolete(logManager);
        }

        setLastFullLsn(lsn);
        setDirty(false);
        return lsn;
    }

    /**
     * Adds the given obsolete LSNs and any tracked obsolete LSNs for the given
     * child IN to this IN's tracking list.  This method is called to track
     * obsolete LSNs when a child IN is logged provisionally.  Such LSNs cannot
     * be considered obsolete until an ancestor IN is logged non-provisionally.
     */
    void trackProvisionalObsolete(IN child,
                                  long obsoleteLsn1,
                                  long obsoleteLsn2) {

        int memDelta = 0;

        if (child.provisionalObsolete != null) {

            int childMemDelta = child.provisionalObsolete.size() *
                                MemoryBudget.LONG_LIST_PER_ITEM_OVERHEAD;

            if (provisionalObsolete != null) {
                provisionalObsolete.addAll(child.provisionalObsolete);
            } else {
                provisionalObsolete = child.provisionalObsolete;
            }
            child.provisionalObsolete = null;

            child.changeMemorySize(0 - childMemDelta);
            memDelta += childMemDelta;
        }

        if (obsoleteLsn1 != DbLsn.NULL_LSN || obsoleteLsn2 != DbLsn.NULL_LSN) {

            if (provisionalObsolete == null) {
                provisionalObsolete = new ArrayList();
            }

            if (obsoleteLsn1 != DbLsn.NULL_LSN) {
                provisionalObsolete.add(new Long(obsoleteLsn1));
                memDelta += MemoryBudget.LONG_LIST_PER_ITEM_OVERHEAD;
            }

            if (obsoleteLsn2 != DbLsn.NULL_LSN) {
                provisionalObsolete.add(new Long(obsoleteLsn2));
                memDelta += MemoryBudget.LONG_LIST_PER_ITEM_OVERHEAD;
            }
        }

        if (memDelta != 0) {
            changeMemorySize(memDelta);
        }
    }

    /**
     * Adds the provisional obsolete tracking information in this node to the
     * live tracker.  This method is called when this node is logged
     * non-provisionally.
     */
    void flushProvisionalObsolete(LogManager logManager)
        throws DatabaseException {

        if (provisionalObsolete != null) {

            int memDelta = provisionalObsolete.size() *
                           MemoryBudget.LONG_LIST_PER_ITEM_OVERHEAD;

            logManager.countObsoleteINs(provisionalObsolete);
            provisionalObsolete = null;

            changeMemorySize(0 - memDelta);
        }
    }

    /**
     * @see Node#getLogType
     */
    public LogEntryType getLogType() {
        return LogEntryType.LOG_IN;
    }

    /**
     * @see Loggable#getLogSize
     */
    public int getLogSize() {
        int size = super.getLogSize(); // ancestors
        size += LogUtils.getByteArrayLogSize(identifierKey); // identifier key
        size += LogUtils.getBooleanLogSize();   // isRoot
        size += LogUtils.INT_BYTES;             // nentries;
        size += LogUtils.INT_BYTES;             // level
        size += LogUtils.INT_BYTES;             // length of entries array
	size += LogUtils.getBooleanLogSize();   // compactLsnsRep
	boolean compactLsnsRep = (entryLsnLongArray == null);
	if (compactLsnsRep) {
	    size += LogUtils.INT_BYTES;         // baseFileNumber
	}

        for (int i = 0; i < nEntries; i++) {    // entries
	    size += LogUtils.getByteArrayLogSize(entryKeyVals[i]) + // key
		(compactLsnsRep ? LogUtils.INT_BYTES :
		 LogUtils.getLongLogSize()) +                       // LSN
		1;                                                  // state
        }
        return size;
    }

    /**
     * @see Loggable#writeToLog
     */
    public void writeToLog(ByteBuffer logBuffer) {

        // ancestors
        super.writeToLog(logBuffer); 

        // identifier key
        LogUtils.writeByteArray(logBuffer, identifierKey);

        // isRoot
        LogUtils.writeBoolean(logBuffer, isRoot);

        // nEntries
        LogUtils.writeInt(logBuffer, nEntries); 

        // level
        LogUtils.writeInt(logBuffer, level); 

        // length of entries array
        LogUtils.writeInt(logBuffer, entryTargets.length); 

	// true if compact representation
	boolean compactLsnsRep = (entryLsnLongArray == null);
	LogUtils.writeBoolean(logBuffer, compactLsnsRep);
	if (compactLsnsRep) {
	    LogUtils.writeInt(logBuffer, (int) baseFileNumber);
	}

        // entries
        for (int i = 0; i < nEntries; i++) {
            LogUtils.writeByteArray(logBuffer, entryKeyVals[i]); // key

            /*
             * A NULL_LSN may be stored when an incomplete insertion occurs,
             * but in that case the KnownDeleted flag must be set. See
             * Tree.insert.  [#13126]
             */
            assert checkForNullLSN(i) :
                "logging IN " + getNodeId() + " with null lsn child " +
                " db=" + databaseImpl.getDebugName() +
                " isDeferredWrite=" + databaseImpl.isDeferredWrite();

	    if (compactLsnsRep) {                                // LSN
		int offset = i << 2;
		int fileOffset = getFileOffset(offset);
		logBuffer.put(getFileNumberOffset(offset));
		logBuffer.put((byte) ((fileOffset >>> 0) & 0xff));
		logBuffer.put((byte) ((fileOffset >>> 8) & 0xff));
		logBuffer.put((byte) ((fileOffset >>> 16) & 0xff));
	    } else {
		LogUtils.writeLong(logBuffer, entryLsnLongArray[i]);
	    }
	    logBuffer.put(entryStates[i]);                       // state
	    entryStates[i] &= CLEAR_DIRTY_BIT;
        }
    }

    /* 
     * Used for assertion to prevent writing a null lsn to the log.
     */
    private boolean checkForNullLSN(int index) {
        boolean ok;
        if (this instanceof BIN) {
            ok = !(getLsn(index) == DbLsn.NULL_LSN &&
                   (entryStates[index] & KNOWN_DELETED_BIT) == 0);
        } else {
            ok = (getLsn(index) != DbLsn.NULL_LSN);
        }
        return ok;
    }

    /**
     * @see Loggable#readFromLog
     */
    public void readFromLog(ByteBuffer itemBuffer, byte entryTypeVersion)
        throws LogException {

        // ancestors 
        super.readFromLog(itemBuffer, entryTypeVersion);

        // identifier key
        identifierKey = LogUtils.readByteArray(itemBuffer);

        // isRoot
        isRoot = LogUtils.readBoolean(itemBuffer);

        // nEntries
        nEntries = LogUtils.readInt(itemBuffer);

        // level
        level = LogUtils.readInt(itemBuffer);

        // nentries
        int length = LogUtils.readInt(itemBuffer);

	entryTargets = new Node[length];
	entryKeyVals = new byte[length][];
	baseFileNumber = -1;
	long storedBaseFileNumber = -1;
	entryLsnByteArray = new byte[length << 2];
	entryLsnLongArray = null;
	entryStates = new byte[length];
	boolean compactLsnsRep = false;
	if (entryTypeVersion > 1) {
	    compactLsnsRep = LogUtils.readBoolean(itemBuffer);
	    if (compactLsnsRep) {
		baseFileNumber = LogUtils.readInt(itemBuffer) & 0xffffffff;
                storedBaseFileNumber = baseFileNumber;
	    }
	}
	for (int i = 0; i < nEntries; i++) {
	    entryKeyVals[i] = LogUtils.readByteArray(itemBuffer); // key
            long lsn;
	    if (compactLsnsRep) {
		/* LSNs in compact form. */
		byte fileNumberOffset = itemBuffer.get();
		int fileOffset = (itemBuffer.get() & 0xff);
		fileOffset |= ((itemBuffer.get() & 0xff) << 8);
		fileOffset |= ((itemBuffer.get() & 0xff) << 16);
                if (fileOffset == THREE_BYTE_NEGATIVE_ONE) {
                    lsn = DbLsn.NULL_LSN;
                } else {
                    lsn = DbLsn.makeLsn
                        (storedBaseFileNumber + fileNumberOffset, fileOffset);
                }
	    } else {
		/* LSNs in long form. */
		lsn = LogUtils.readLong(itemBuffer);              // LSN
	    }
            setLsnElement(i, lsn);

	    byte entryState = itemBuffer.get();                   // state
	    entryState &= CLEAR_DIRTY_BIT;
	    entryState &= CLEAR_MIGRATE_BIT;

            /*
             * A NULL_LSN is the remnant of an incomplete insertion and the
             * KnownDeleted flag should be set.  But because of bugs in prior
             * releases, the KnownDeleted flag may not be set.  So set it here.
             * See Tree.insert.  [#13126]
             */
            if (lsn == DbLsn.NULL_LSN) {
                entryState |= KNOWN_DELETED_BIT;
            }

            entryStates[i] = entryState;
	}

        latch.setName(shortClassName() + getNodeId());
    }
    
    /**
     * @see Loggable#dumpLog
     */
    public void dumpLog(StringBuffer sb, boolean verbose) {
        sb.append(beginTag());

        super.dumpLog(sb, verbose);
        sb.append(Key.dumpString(identifierKey, 0));

        // isRoot
        sb.append("<isRoot val=\"");
        sb.append(isRoot);
        sb.append("\"/>");

        // level
        sb.append("<level val=\"");
        sb.append(Integer.toHexString(level));
        sb.append("\"/>");

        // nEntries, length of entries array
        sb.append("<entries numEntries=\"");
        sb.append(nEntries);
        sb.append("\" length=\"");
        sb.append(entryTargets.length);
	boolean compactLsnsRep = (entryLsnLongArray == null);
        if (compactLsnsRep) {
            sb.append("\" baseFileNumber=\"");
            sb.append(baseFileNumber);
        }
        sb.append("\">");

        if (verbose) {
            for (int i = 0; i < nEntries; i++) {
		sb.append("<ref knownDeleted=\"").
		    append(isEntryKnownDeleted(i));
                sb.append("\" pendingDeleted=\"").
		    append(isEntryPendingDeleted(i));
		sb.append("\">");
                sb.append(Key.dumpString(entryKeyVals[i], 0));
		sb.append(DbLsn.toString(getLsn(i)));
		sb.append("</ref>");
            }
        }

        sb.append("</entries>");

        /* Add on any additional items from subclasses before the end tag. */
        dumpLogAdditional(sb);

        sb.append(endTag());
    }

    /** 
     * Allows subclasses to add additional fields before the end tag. If they
     * just overload dumpLog, the xml isn't nested.
     */
    protected void dumpLogAdditional(StringBuffer sb) {
    }

    public String beginTag() {
        return BEGIN_TAG;
    }

    public String endTag() {
        return END_TAG;
    }

    void dumpKeys() throws DatabaseException {
        for (int i = 0; i < nEntries; i++) {
            System.out.println(Key.dumpString(entryKeyVals[i], 0));
        }
    }

    /**
     * For unit test support:
     * @return a string that dumps information about this IN, without
     */
    public String dumpString(int nSpaces, boolean dumpTags) {
        StringBuffer sb = new StringBuffer();
        if (dumpTags) {
            sb.append(TreeUtils.indent(nSpaces));
            sb.append(beginTag());
            sb.append('\n');
        }

        sb.append(super.dumpString(nSpaces+2, true));
        sb.append('\n');

        sb.append(TreeUtils.indent(nSpaces+2));
        sb.append("<idkey>");
        sb.append(identifierKey == null ? "" :
                  Key.dumpString(identifierKey, 0));
        sb.append("</idkey>");
        sb.append('\n');
        sb.append(TreeUtils.indent(nSpaces+2));
        sb.append("<dirty val=\"").append(dirty).append("\"/>");
        sb.append('\n');
        sb.append(TreeUtils.indent(nSpaces+2));
        sb.append("<generation val=\"").append(generation).append("\"/>");
        sb.append('\n');
        sb.append(TreeUtils.indent(nSpaces+2));
        sb.append("<level val=\"");
        sb.append(Integer.toHexString(level)).append("\"/>");
        sb.append('\n');
        sb.append(TreeUtils.indent(nSpaces+2));
        sb.append("<isRoot val=\"").append(isRoot).append("\"/>");
        sb.append('\n');

        sb.append(TreeUtils.indent(nSpaces+2));
        sb.append("<entries nEntries=\"");
        sb.append(nEntries);
        sb.append("\">");
        sb.append('\n');

        for (int i = 0; i < nEntries; i++) {
            sb.append(TreeUtils.indent(nSpaces+4));
            sb.append("<entry id=\"" + i + "\">");
            sb.append('\n');
            if (getLsn(i) == DbLsn.NULL_LSN) {
                sb.append(TreeUtils.indent(nSpaces + 6));
                sb.append("<lsn/>");
            } else {
                sb.append(DbLsn.dumpString(getLsn(i), nSpaces + 6));
            }
            sb.append('\n');
            if (entryKeyVals[i] == null) {
                sb.append(TreeUtils.indent(nSpaces + 6));
                sb.append("<key/>");
            } else {
                sb.append(Key.dumpString(entryKeyVals[i], (nSpaces + 6)));
            }
            sb.append('\n');
            if (entryTargets[i] == null) {
                sb.append(TreeUtils.indent(nSpaces + 6));
                sb.append("<target/>");
            } else {
                sb.append(entryTargets[i].dumpString(nSpaces + 6, true));
            }
            sb.append('\n');
            sb.append(TreeUtils.indent(nSpaces + 6));
            dumpDeletedState(sb, getState(i));
            sb.append("<dirty val=\"").append(isDirty(i)).append("\"/>");
            sb.append('\n');
            sb.append(TreeUtils.indent(nSpaces+4));
            sb.append("</entry>");
            sb.append('\n');
        }

        sb.append(TreeUtils.indent(nSpaces+2));
        sb.append("</entries>");
        sb.append('\n');
        if (dumpTags) {
            sb.append(TreeUtils.indent(nSpaces));
            sb.append(endTag());
        }
        return sb.toString();
    }

    /**
     * Utility method for output of knownDeleted and pendingDelete.
     */
    static void dumpDeletedState(StringBuffer sb, byte state) {
        sb.append("<knownDeleted val=\"");
        sb.append(isStateKnownDeleted(state)).append("\"/>");
        sb.append("<pendingDeleted val=\"");
        sb.append(isStatePendingDeleted(state)).append("\"/>");
    }

    public String toString() {
        return dumpString(0, true);
    }

    public String shortClassName() {
        return "IN";
    }

    /**
     * Send trace messages to the java.util.logger. Don't rely on the logger
     * alone to conditionalize whether we send this message, we don't even want
     * to construct the message if the level is not enabled.
     */
    private void traceSplit(Level level,
                            IN parent,
                            IN newSibling,
                            long parentLsn,
                            long myNewLsn,
                            long newSiblingLsn,
                            int splitIndex,
                            int idKeyIndex,
                            int childIndex) {
        Logger logger = databaseImpl.getDbEnvironment().getLogger();
        if (logger.isLoggable(level)) {
            StringBuffer sb = new StringBuffer();
            sb.append(TRACE_SPLIT);
            sb.append(" parent=");
            sb.append(parent.getNodeId());
            sb.append(" child=");
            sb.append(getNodeId());
            sb.append(" newSibling=");
            sb.append(newSibling.getNodeId());
            sb.append(" parentLsn = ");
            sb.append(DbLsn.getNoFormatString(parentLsn));
            sb.append(" childLsn = ");
            sb.append(DbLsn.getNoFormatString(myNewLsn));
            sb.append(" newSiblingLsn = ");
            sb.append(DbLsn.getNoFormatString(newSiblingLsn));
            sb.append(" splitIdx=");
            sb.append(splitIndex);
            sb.append(" idKeyIdx=");
            sb.append(idKeyIndex);
            sb.append(" childIdx=");
            sb.append(childIndex);
            logger.log(level, sb.toString());
        }
    }

    /**
     * Send trace messages to the java.util.logger. Don't rely on the logger
     * alone to conditionalize whether we send this message, we don't even want
     * to construct the message if the level is not enabled.
     */
    private void traceDelete(Level level, int index) {
        Logger logger = databaseImpl.getDbEnvironment().getLogger();
        if (logger.isLoggable(level)) {
            StringBuffer sb = new StringBuffer();
            sb.append(TRACE_DELETE);
            sb.append(" in=").append(getNodeId());
            sb.append(" index=");
            sb.append(index);
            logger.log(level, sb.toString());
        }
    }
}
