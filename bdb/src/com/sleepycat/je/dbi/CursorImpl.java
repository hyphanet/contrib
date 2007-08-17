/*
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: CursorImpl.java,v 1.320.2.3 2007/06/13 21:22:17 mark Exp $
 */

package com.sleepycat.je.dbi;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.LockNotGrantedException;
import com.sleepycat.je.LockStats;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.RunRecoveryException;
import com.sleepycat.je.latch.LatchNotHeldException;
import com.sleepycat.je.latch.LatchSupport;
import com.sleepycat.je.log.LogUtils;
import com.sleepycat.je.tree.BIN;
import com.sleepycat.je.tree.BINBoundary;
import com.sleepycat.je.tree.DBIN;
import com.sleepycat.je.tree.DIN;
import com.sleepycat.je.tree.DupCountLN;
import com.sleepycat.je.tree.IN;
import com.sleepycat.je.tree.Key;
import com.sleepycat.je.tree.LN;
import com.sleepycat.je.tree.Node;
import com.sleepycat.je.tree.Tree;
import com.sleepycat.je.tree.TreeWalkerStatsAccumulator;
import com.sleepycat.je.txn.BasicLocker;
import com.sleepycat.je.txn.LockGrantType;
import com.sleepycat.je.txn.LockResult;
import com.sleepycat.je.txn.LockType;
import com.sleepycat.je.txn.Locker;
import com.sleepycat.je.txn.ThreadLocker;
import com.sleepycat.je.utilint.DbLsn;
import com.sleepycat.je.utilint.TestHook;
import com.sleepycat.je.utilint.TestHookExecute;

/**
 * A CursorImpl is the internal implementation of the cursor.
 */
public class CursorImpl implements Cloneable {

    private static final boolean DEBUG = false;

    private static final byte CURSOR_NOT_INITIALIZED = 1;
    private static final byte CURSOR_INITIALIZED = 2;
    private static final byte CURSOR_CLOSED = 3;
    private static final String TRACE_DELETE = "Delete";
    private static final String TRACE_MOD = "Mod:";

    /*
     * Cursor location in the database, represented by a BIN and an index in
     * the BIN.  bin/index must have a non-null/non-negative value if dupBin is
     * set to non-null.
     */
    volatile private BIN bin;
    volatile private int index;

    /*
     * Cursor location in a given duplicate set.  If the cursor is not
     * referencing a duplicate set then these are null.
     */
    volatile private DBIN dupBin;
    volatile private int dupIndex;

    /*
     * BIN and DBIN that are no longer referenced by this cursor but have not
     * yet been removed.  If non-null, the BIN/DBIN will be removed soon.
     * BIN.adjustCursors should ignore cursors that are to be removed.
     */
    volatile private BIN binToBeRemoved;
    volatile private DBIN dupBinToBeRemoved;

    /*
     * The cursor location used for a given operation. 
     */
    private BIN targetBin;
    private int targetIndex;
    private byte[] dupKey;

    /* The database behind the handle. */
    private DatabaseImpl database;

    /* Owning transaction. */
    private Locker locker;
    private CursorImpl lockerPrev; // lockPrev, lockNext used for simple Locker
    private CursorImpl lockerNext; // chain.

    /*
     * Do not release non-transactional locks when cursor is closed.  This flag
     * is used to support handle locks, which may be non-transactional but must
     * be retained across cursor operations and cursor close.
     */
    private boolean retainNonTxnLocks;

    /* State of the cursor. See CURSOR_XXX above. */
    private byte status;

    private boolean allowEviction;
    private TestHook testHook;

    private boolean nonCloning = false;

    /*
     * Unique id that we can return as a hashCode to prevent calls to
     * Object.hashCode(). [#13896]
     */
    private int thisId;

    /*
     * Allocate hashCode ids from this. [#13896]
     */
    private static long lastAllocatedId = 0;

    private ThreadLocal treeStatsAccumulatorTL = new ThreadLocal();

    /*
     * Allocate a new hashCode id.  Doesn't need to be synchronized since it's
     * ok for two objects to have the same hashcode.
     */
    private static long getNextCursorId() {
        return ++lastAllocatedId;
    }

    public int hashCode() {
	return thisId;
    }

    private TreeWalkerStatsAccumulator getTreeStatsAccumulator() {
	if (EnvironmentImpl.getThreadLocalReferenceCount() > 0) {
	    return (TreeWalkerStatsAccumulator) treeStatsAccumulatorTL.get();
	} else {
	    return null;
	}
    }

    public void incrementLNCount() {
        TreeWalkerStatsAccumulator treeStatsAccumulator =
	    getTreeStatsAccumulator();
	if (treeStatsAccumulator != null) {
	    treeStatsAccumulator.incrementLNCount();
	}
    }

    public void setNonCloning(boolean nonCloning) {
	this.nonCloning = nonCloning;
    }

    /**
     * public for Cursor et al
     */
    public static class SearchMode {
        public static final SearchMode SET =
            new SearchMode(true, false, "SET");
        public static final SearchMode BOTH =
            new SearchMode(true, true, "BOTH");
        public static final SearchMode SET_RANGE =
            new SearchMode(false, false, "SET_RANGE");
        public static final SearchMode BOTH_RANGE =
            new SearchMode(false, true, "BOTH_RANGE");

        private boolean exactSearch;
        private boolean dataSearch;
	private String name;

        private SearchMode(boolean exactSearch,
			   boolean dataSearch,
			   String name) {
            this.exactSearch = exactSearch;
            this.dataSearch = dataSearch;
	    this.name = "SearchMode." + name;
        }

        /**
         * Returns true when the key or key/data search is exact, i.e., for SET
         * and BOTH.
         */
        public final boolean isExactSearch() {
            return exactSearch;
        }

        /**
         * Returns true when the data value is included in the search, i.e.,
         * for BOTH and BOTH_RANGE.
         */
        public final boolean isDataSearch() {
            return dataSearch;
        }

	public String toString() {
	    return name;
	}
    }

    /**
     * Holder for an OperationStatus and a keyChange flag.  Is used for search
     * and getNextWithKeyChangeStatus operations.
     */
    public static class KeyChangeStatus {
        
        /**
         * Operation status;
         */
        public OperationStatus status;

        /**
         * Whether the operation moved to a new key.
         */
        public boolean keyChange;

        public KeyChangeStatus(OperationStatus status, boolean keyChange) {
            this.status = status;
            this.keyChange = keyChange;
        }
    }

    /**
     * Creates a cursor with retainNonTxnLocks=true.
     */
    public CursorImpl(DatabaseImpl database, Locker locker) 
        throws DatabaseException {

        this(database, locker, true);
    }

    /**
     * Creates a cursor.
     *
     * @param retainNonTxnLocks is true if non-transactional locks should be
     * retained (not released automatically) when the cursor is closed.
     */
    public CursorImpl(DatabaseImpl database,
		      Locker locker,
                      boolean retainNonTxnLocks)
        throws DatabaseException {

	thisId = (int) getNextCursorId();
        bin = null;
        index = -1;
        dupBin = null;
        dupIndex = -1;

        // retainNonTxnLocks=true should not be used with a ThreadLocker
        assert !(retainNonTxnLocks && (locker instanceof ThreadLocker));

        // retainNonTxnLocks=false should not be used with a BasicLocker
        assert !(!retainNonTxnLocks && locker.getClass() == BasicLocker.class);

        this.retainNonTxnLocks = retainNonTxnLocks;

        // Associate this cursor with the database
        this.database = database;
        this.locker = locker;
        this.locker.registerCursor(this);

        status = CURSOR_NOT_INITIALIZED;

        /*
         * Do not perform eviction here because we may be synchronized on the
         * Database instance. For example, this happens when we call
         * Database.openCursor().  Also eviction may be disabled after the
         * cursor is constructed.
         */
    }

    /**
     * Disables or enables eviction during cursor operations.  For example, a
     * cursor used to implement eviction (e.g., in some UtilizationProfile and
     * most DbTree methods) should not itself perform eviction, but eviction
     * should be enabled for user cursors.  Eviction is disabled by default.
     */
    public void setAllowEviction(boolean allowed) {
        allowEviction = allowed;
    }

    /**
     * Shallow copy.  addCursor() is optionally called.
     */
    public CursorImpl cloneCursor(boolean addCursor)
        throws DatabaseException {

        return cloneCursor(addCursor, null);
    }

    /**
     * Shallow copy.  addCursor() is optionally called.  Allows inheriting the
     * BIN position from some other cursor.
     */
    public CursorImpl cloneCursor(boolean addCursor, CursorImpl usePosition)
        throws DatabaseException {

        CursorImpl ret = null;
	if (nonCloning) {
	    ret = this;
	} else {
	    try {
		latchBINs();
		ret = (CursorImpl) super.clone();

		if (!retainNonTxnLocks) {
		    ret.locker = locker.newNonTxnLocker();
		}

		ret.locker.registerCursor(ret);
		if (usePosition != null &&
		    usePosition.status == CURSOR_INITIALIZED) {
		    ret.bin = usePosition.bin;
		    ret.index = usePosition.index;
		    ret.dupBin = usePosition.dupBin;
		    ret.dupIndex = usePosition.dupIndex;
		}
		if (addCursor) {
		    ret.addCursor();
		}
	    } catch (CloneNotSupportedException cannotOccur) {
		return null;
	    } finally {
		releaseBINs();
	    }
	}

        /* Perform eviction before and after each cursor operation. */
        if (allowEviction) {
            database.getDbEnvironment().getEvictor().doCriticalEviction
                (false); // backgroundIO
        }
        return ret;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int idx) {
        index = idx;
    }

    public BIN getBIN() {
        return bin;
    }

    public void setBIN(BIN newBin) {
        bin = newBin;
    }

    public BIN getBINToBeRemoved() {
        return binToBeRemoved;
    }

    public int getDupIndex() {
        return dupIndex;
    }

    public void setDupIndex(int dupIdx) {
        dupIndex = dupIdx;
    }

    public DBIN getDupBIN() {
        return dupBin;
    }

    public void setDupBIN(DBIN newDupBin) {
        dupBin = newDupBin;
    }

    public DBIN getDupBINToBeRemoved() {
        return dupBinToBeRemoved;
    }

    public void setTreeStatsAccumulator(TreeWalkerStatsAccumulator tSA) {
	treeStatsAccumulatorTL.set(tSA);
    }

    /**
     * Figure out which BIN/index set to use.
     */
    private boolean setTargetBin() {
        targetBin = null;
        targetIndex = 0;
        boolean isDup = (dupBin != null);
        dupKey = null;
        if (isDup) {
            targetBin = dupBin;
            targetIndex = dupIndex;
            dupKey = dupBin.getDupKey();
        } else {
            targetBin = bin;
            targetIndex = index;
        }
        return isDup;
    }

    /**
     * Advance a cursor.  Used so that verify can advance a cursor even in the
     * face of an exception [12932].
     * @param key on return contains the key if available, or null.
     * @param data on return contains the data if available, or null.
     */
    public boolean advanceCursor(DatabaseEntry key, DatabaseEntry data) {

        BIN oldBin = bin;
        BIN oldDupBin = dupBin;
        int oldIndex = index;
        int oldDupIndex = dupIndex;

        key.setData(null);
        data.setData(null);

        try {
            getNext(key, data, LockType.NONE,
		    true /* forward */,
		    false /* alreadyLatched */);
        } catch (DatabaseException ignored) {
	    /* Klockwork - ok */
	}

        /*
         * If the position changed, regardless of an exception, then we believe
         * that we have advanced the cursor.
         */
        if (bin != oldBin ||
            dupBin != oldDupBin ||
            index != oldIndex ||
            dupIndex != oldDupIndex) {

            /*
             * Return the key and data from the BIN entries, if we were not
             * able to read it above.
             */
            if (key.getData() == null &&
                bin != null &&
                index > 0) {
                setDbt(key, bin.getKey(index));
            }
            if (data.getData() == null &&
                dupBin != null &&
                dupIndex > 0) {
                setDbt(data, dupBin.getKey(dupIndex));
            }
            return true;
        } else {
            return false;
        }
    }

    public BIN latchBIN()
        throws DatabaseException {

	while (bin != null) {
	    BIN waitingOn = bin;
	    waitingOn.latch();
	    if (bin == waitingOn) {
		return bin;
	    }
	    waitingOn.releaseLatch();
	}

	return null;
    }

    public void releaseBIN()
        throws LatchNotHeldException {

        if (bin != null) {
            bin.releaseLatchIfOwner();
        }
    }

    public void latchBINs()
        throws DatabaseException {

        latchBIN();
        latchDBIN();
    }

    public void releaseBINs()
        throws LatchNotHeldException {

        releaseBIN();
        releaseDBIN();
    }

    public DBIN latchDBIN()
        throws DatabaseException {

	while (dupBin != null) {
	    BIN waitingOn = dupBin;
	    waitingOn.latch();
	    if (dupBin == waitingOn) {
		return dupBin;
	    }
	    waitingOn.releaseLatch();
	}
	return null;
    }

    public void releaseDBIN()
        throws LatchNotHeldException {

        if (dupBin != null) {
            dupBin.releaseLatchIfOwner();
        }
    }

    public Locker getLocker() {
        return locker;
    }

    public void addCursor(BIN bin) {
        if (bin != null) {
            assert bin.isLatchOwnerForWrite();
            bin.addCursor(this);
        }
    }

    /**
     * Add to the current cursor. (For dups)
     */
    public void addCursor() {
        if (dupBin != null) {
            addCursor(dupBin);
        }
        if (bin != null) {
            addCursor(bin);
        }
    }

    /*
     * Update a cursor to refer to a new BIN or DBin following an insert.
     * Don't bother removing this cursor from the previous bin.  Cursor will do
     * that with a cursor swap thereby preventing latch deadlocks down here.
     */
    public void updateBin(BIN bin, int index) 
        throws DatabaseException {

        removeCursorDBIN();
        setDupIndex(-1);
        setDupBIN(null);
        setIndex(index);
        setBIN(bin);
        addCursor(bin);
    }

    public void updateDBin(DBIN dupBin, int dupIndex) {
        setDupIndex(dupIndex);
        setDupBIN(dupBin);
        addCursor(dupBin);
    }

    private void removeCursor()
        throws DatabaseException {

        removeCursorBIN();
        removeCursorDBIN();
    }

    private void removeCursorBIN()
	throws DatabaseException {

	BIN abin = latchBIN();
	if (abin != null) {
	    abin.removeCursor(this);
	    abin.releaseLatch();
	}
    }

    private void removeCursorDBIN()
	throws DatabaseException {
	
	DBIN abin = latchDBIN();
	if (abin != null) {
	    abin.removeCursor(this);
	    abin.releaseLatch();
	}
    }

    /**
     * Clear the reference to the dup tree, if any.
     */
    public void clearDupBIN(boolean alreadyLatched)
	throws DatabaseException {

        if (dupBin != null) {
            if (alreadyLatched) {
                dupBin.removeCursor(this);
                dupBin.releaseLatch();
            } else {
                removeCursorDBIN();
            }
            dupBin = null;
            dupIndex = -1;
        }
    }

    public void dumpTree()
        throws DatabaseException {

        database.getTree().dump();
    }

    /**
     * @return true if this cursor is closed
     */
    public boolean isClosed() {
        return (status == CURSOR_CLOSED);
    }

    /**
     * @return true if this cursor is not initialized
     */
    public boolean isNotInitialized() {
        return (status == CURSOR_NOT_INITIALIZED);
    }

    /**
     * Reset a cursor to an uninitialized state, but unlike close(), allow it
     * to be used further.
     */
    public void reset()
        throws DatabaseException {

        removeCursor();

        if (!retainNonTxnLocks) {
            locker.releaseNonTxnLocks();
        }

        bin = null;
        index = -1;
        dupBin = null;
        dupIndex = -1;

        status = CURSOR_NOT_INITIALIZED;

        /* Perform eviction before and after each cursor operation. */
        if (allowEviction) {
            database.getDbEnvironment().getEvictor().doCriticalEviction
                (false); // backgroundIO
        }
    }

    /**
     * Close a cursor with releaseNonTxnLocks=true.
     */
    public void close()
        throws DatabaseException {

        close(true /*releaseNonTxnLocks*/);
    }

    /**
     * Close a cursor.
     *
     * @param releaseNonTxnLocks should normally be true to release
     * non-transactional locks when a cursor is closed.  However, some
     * operations may wish to hold non-transactional locks if the locker is
     * re-used in another cursor.  For example, see
     * SecondaryCursor.readPrimaryAfterGet. [#15573]
     *
     * @throws DatabaseException if the cursor was previously closed.
     */
    public void close(boolean releaseNonTxnLocks)
        throws DatabaseException {

        assert assertCursorState(false) : dumpToString(true);

        removeCursor();
        locker.unRegisterCursor(this);

        if (releaseNonTxnLocks && !retainNonTxnLocks) {
            locker.releaseNonTxnLocks();
        }

        status = CURSOR_CLOSED;

        /* Perform eviction before and after each cursor operation. */
        if (allowEviction) {
            database.getDbEnvironment().getEvictor().doCriticalEviction
                (false); // backgroundIO
        }
    }

    public int count(LockType lockType)
        throws DatabaseException {

        assert assertCursorState(true) : dumpToString(true);

        if (!database.getSortedDuplicates()) {
            return 1;
        }

        if (bin == null) {
            return 0;
        }

	latchBIN();
        try {
            if (bin.getNEntries() <= index) {
                return 0;
            }

            /* If fetchTarget returns null, a deleted LN was cleaned. */
            Node n = bin.fetchTarget(index);
            if (n != null && n.containsDuplicates()) {
                DIN dupRoot = (DIN) n;

                /* Latch couple down the tree. */
                dupRoot.latch();
                releaseBIN();
                DupCountLN dupCountLN = (DupCountLN)
                    dupRoot.getDupCountLNRef().fetchTarget(database, dupRoot);

                /* We can't hold latches when we acquire locks. */
                dupRoot.releaseLatch();

                /*
                 * Call lock directly.  There is no need to call lockLN because
                 * the node ID cannot change (a slot cannot be reused) for a
                 * DupCountLN.
                 */
                if (lockType != LockType.NONE) {
                    locker.lock
                        (dupCountLN.getNodeId(), lockType, false /*noWait*/,
                         database);
                }
                return dupCountLN.getDupCount();
            } else {
                /* If an LN is in the slot, the count is one. */
                return 1;
            }
        } finally {
	    releaseBIN();
        }
    }

    /**
     * Delete the item pointed to by the cursor. If cursor is not initialized
     * or item is already deleted, return appropriate codes. Returns with
     * nothing latched.  bin and dupBin are latched as appropriate.
     *
     * @return 0 on success, appropriate error code otherwise.
     */
    public OperationStatus delete()
        throws DatabaseException {

        assert assertCursorState(true) : dumpToString(true);
        boolean isDup = setTargetBin();

        /* If nothing at current position, return. */
        if (targetBin == null) {
            return OperationStatus.KEYEMPTY;
        }

        /* 
         * Check if this is already deleted. We may know that the record is
         * deleted w/out seeing the LN.
         */
        if (targetBin.isEntryKnownDeleted(targetIndex)) {
            releaseBINs();
            return OperationStatus.KEYEMPTY;
        }

        /* If fetchTarget returns null, a deleted LN was cleaned. */
        LN ln = (LN) targetBin.fetchTarget(targetIndex);
        if (ln == null) {
	    releaseBINs();
            return OperationStatus.KEYEMPTY;
        }

        /* Get a write lock. */
	LockResult lockResult = lockLN(ln, LockType.WRITE);
	ln = lockResult.getLN();
            
        /* Check LN deleted status under the protection of a write lock. */
        if (ln == null) {
            releaseBINs();
            return OperationStatus.KEYEMPTY;
        }

        /* Lock the DupCountLN before logging any LNs. */
        LockResult dclLockResult = null;
        DIN dupRoot = null;
	boolean dupRootIsLatched = false;
        try {
            isDup = (dupBin != null);
            if (isDup) {
                dupRoot = getLatchedDupRoot(true /*isDBINLatched*/);
                dclLockResult = lockDupCountLN(dupRoot, LockType.WRITE);
		/* Don't mark latched until after locked. */
		dupRootIsLatched = true;

                /* 
                 * Refresh the dupRoot variable because it may have changed
                 * during locking, but is sure to be resident and latched by
                 * lockDupCountLN.
                 */
                dupRoot = (DIN) bin.getTarget(index);
                /* Release BIN to increase concurrency. */
                releaseBIN();
            }

            /*
             * Between the release of the BIN latch and acquiring the write
             * lock any number of operations may have executed which would
             * result in a new abort LSN for this record. Therefore, wait until
             * now to get the abort LSN.
             */
            setTargetBin();
            long oldLsn = targetBin.getLsn(targetIndex);
            byte[] lnKey = targetBin.getKey(targetIndex);
            lockResult.setAbortLsn
                (oldLsn, targetBin.isEntryKnownDeleted(targetIndex));

            /* Log the LN. */
            long oldLNSize = ln.getMemorySizeIncludedByParent();
            long newLsn = ln.delete(database, lnKey, dupKey, oldLsn, locker);
            long newLNSize = ln.getMemorySizeIncludedByParent();

            /*
             * Now update the parent of the LN (be it BIN or DBIN) to correctly
             * reference the LN and adjust the memory sizing.  Be sure to do
             * this update of the LSN before updating the dup count LN. In case
             * we encounter problems there we need the LSN to match the latest
             * version to ensure that undo works.
             */
            targetBin.updateEntry(targetIndex, newLsn, oldLNSize, newLNSize);
            targetBin.setPendingDeleted(targetIndex);
            releaseBINs();

            if (isDup) {
                dupRoot.incrementDuplicateCount
                    (dclLockResult, dupKey, locker, false /*increment*/);
                dupRoot.releaseLatch();
		dupRootIsLatched = false;
                dupRoot = null;

                locker.addDeleteInfo(dupBin, new Key(lnKey));
            } else {
                locker.addDeleteInfo(bin, new Key(lnKey));
            } 

            trace(Level.FINER, TRACE_DELETE, targetBin,
                  ln, targetIndex, oldLsn, newLsn);
        } finally {
            if (dupRoot != null &&
		dupRootIsLatched) {
                dupRoot.releaseLatchIfOwner();
            }
        }
            
        return OperationStatus.SUCCESS;
    }

    /**
     * Return a new copy of the cursor. If position is true, position the
     * returned cursor at the same position.
     */
    public CursorImpl dup(boolean samePosition)
        throws DatabaseException {

        assert assertCursorState(false) : dumpToString(true);

        CursorImpl ret = cloneCursor(samePosition);

        if (!samePosition) {
            ret.bin = null;
            ret.index = -1;
            ret.dupBin = null;
            ret.dupIndex = -1;

            ret.status = CURSOR_NOT_INITIALIZED;
        }

        return ret;
    }

    /**
     * Evict the LN node at the cursor position.  This is used for internal
     * databases only.
     */
    public void evict()
        throws DatabaseException {

        evict(false); // alreadyLatched
    }

    /**
     * Evict the LN node at the cursor position.  This is used for internal
     * databases only.
     */
    public void evict(boolean alreadyLatched)
        throws DatabaseException {

        try {
            if (!alreadyLatched) {
                latchBINs();
            }
            setTargetBin();
            if (targetIndex >= 0) {
                targetBin.evictLN(targetIndex);
            }
        } finally {
            if (!alreadyLatched) {
                releaseBINs();
            }
        }
    }

    /*
     * Puts 
     */

    /**
     * Search for the next key (or duplicate) following the given key (and
     * datum), and acquire a range insert lock on it.  If there are no more
     * records following the given key and datum, lock the special EOF node
     * for the database.
     */
    public void lockNextKeyForInsert(DatabaseEntry key, DatabaseEntry data)
        throws DatabaseException {

        DatabaseEntry tempKey = new DatabaseEntry
            (key.getData(), key.getOffset(), key.getSize());
        DatabaseEntry tempData = new DatabaseEntry
            (data.getData(), data.getOffset(), data.getSize());
        tempKey.setPartial(0, 0, true);
        tempData.setPartial(0, 0, true);
        boolean lockedNextKey = false;

        /* Don't search for data if duplicates are not configured. */
        SearchMode searchMode = database.getSortedDuplicates() ?
            SearchMode.BOTH_RANGE : SearchMode.SET_RANGE;
        boolean latched = true;
        try {
            /* Search. */
            int searchResult = searchAndPosition
                (tempKey, tempData, searchMode, LockType.RANGE_INSERT);
            if ((searchResult & FOUND) != 0 &&
                (searchResult & FOUND_LAST) == 0) {

                /*
                 * If searchAndPosition found a record (other than the last
                 * one), in all cases we should advance to the next record:
                 *
                 * 1- found a deleted record,
                 * 2- found an exact match, or
                 * 3- found the record prior to the given key/data.
                 *
                 * If we didn't match the key, skip over duplicates to the next
                 * key with getNextNoDup.
                 */
                OperationStatus status;
                if ((searchResult & EXACT_KEY) != 0) {
                    status = getNext
                        (tempKey, tempData, LockType.RANGE_INSERT, true, true);
                } else {
                    status = getNextNoDup
                        (tempKey, tempData, LockType.RANGE_INSERT, true, true);
                }
                if (status == OperationStatus.SUCCESS) {
                    lockedNextKey = true;
                }
                latched = false;
            }
        } finally {
            if (latched) {
                releaseBINs();
            }
        }

        /* Lock the EOF node if no next key was found. */
        if (!lockedNextKey) {
            lockEofNode(LockType.RANGE_INSERT);
        }
    }

    /**
     * Insert the given LN in the tree or return KEYEXIST if the key is already
     * present.
     *
     * <p>This method is called directly internally for putting tree map LNs
     * and file summary LNs.  It should not be used otherwise, and in the
     * future we should find a way to remove this special case.</p>
     */
    public OperationStatus putLN(byte[] key, LN ln, boolean allowDuplicates)
        throws DatabaseException {

        assert assertCursorState(false) : dumpToString(true);

        assert LatchSupport.countLatchesHeld() == 0;
	LockResult lockResult = locker.lock
            (ln.getNodeId(), LockType.WRITE, false /*noWait*/, database);

	/* 
	 * We'll set abortLsn down in Tree.insert when we know whether we're
	 * re-using a BIN entry or not.
	 */
        if (database.getTree().insert
            (ln, key, allowDuplicates, this, lockResult)) {
            status = CURSOR_INITIALIZED;
            return OperationStatus.SUCCESS;
        } else {
            locker.releaseLock(ln.getNodeId());
            return OperationStatus.KEYEXIST;
        }
    }

    /**
     * Insert or overwrite the key/data pair.
     * @param key
     * @param data
     * @return 0 if successful, failure status value otherwise
     */
    public OperationStatus put(DatabaseEntry key,
			       DatabaseEntry data,
                               DatabaseEntry foundData) 
        throws DatabaseException {

        assert assertCursorState(false) : dumpToString(true);

        OperationStatus result = putLN
            (Key.makeKey(key), new LN(data), database.getSortedDuplicates());
        if (result == OperationStatus.KEYEXIST) {
            status = CURSOR_INITIALIZED;

            /* 
             * If dups are allowed and putLN() returns KEYEXIST, the duplicate
             * already exists.  However, we still need to get a write lock, and
             * calling putCurrent does that.  Without duplicates, we have to
             * update the data of course.
             */
            result = putCurrent(data, null, foundData);
        }
        return result;
    }

    /**
     * Insert the key/data pair in No Overwrite mode.
     * @param key
     * @param data
     * @return 0 if successful, failure status value otherwise
     */
    public OperationStatus putNoOverwrite(DatabaseEntry key,
					  DatabaseEntry data) 
        throws DatabaseException {

        assert assertCursorState(false) : dumpToString(true);

        return putLN(Key.makeKey(key), new LN(data), false);
    }

    /**
     * Insert the key/data pair as long as no entry for key/data exists yet.
     */
    public OperationStatus putNoDupData(DatabaseEntry key, DatabaseEntry data)
        throws DatabaseException {

        assert assertCursorState(false) : dumpToString(true);

        if (!database.getSortedDuplicates()) {
            throw new DatabaseException
                ("putNoDupData() called, but database is not configured " +
                 "for duplicate data.");
        }
        return putLN(Key.makeKey(key), new LN(data), true);
    }

    /**
     * Modify the current record with this data.
     * @param data
     */
    public OperationStatus putCurrent(DatabaseEntry data,
                                      DatabaseEntry foundKey,
                                      DatabaseEntry foundData)
        throws DatabaseException {

        assert assertCursorState(true) : dumpToString(true);

        if (foundKey != null) {
            foundKey.setData(null);
        }
        if (foundData != null) {
            foundData.setData(null);
        }

        if (bin == null) {
            return OperationStatus.KEYEMPTY;
        }
        
	latchBINs();
        boolean isDup = setTargetBin();

        try {

            /* 
             * Find the existing entry and get a reference to all BIN fields
             * while latched.
             */
            LN ln = (LN) targetBin.fetchTarget(targetIndex);
            byte[] lnKey = targetBin.getKey(targetIndex);

            /* If fetchTarget returned null, a deleted LN was cleaned. */
	    if (targetBin.isEntryKnownDeleted(targetIndex) ||
                ln == null) {
                releaseBINs();
		return OperationStatus.NOTFOUND;
	    }

            /* Get a write lock. */
	    LockResult lockResult = lockLN(ln, LockType.WRITE);
	    ln = lockResult.getLN();

	    /* Check LN deleted status under the protection of a write lock. */
	    if (ln == null) {
                releaseBINs();
		return OperationStatus.NOTFOUND;
	    }

            /*
             * If cursor points at a dup, then we can only replace the entry
             * with a new entry that is "equal" to the old one.  Since a user
             * defined comparison function may actually compare equal for two
             * byte sequences that are actually different we still have to do
             * the replace.  Arguably we could skip the replacement if there is
             * no user defined comparison function and the new data is the
             * same.
             */
	    byte[] foundDataBytes;
	    byte[] foundKeyBytes;
	    isDup = setTargetBin();
	    if (isDup) {
		foundDataBytes = lnKey;
		foundKeyBytes = targetBin.getDupKey();
	    } else {
		foundDataBytes = ln.getData();
		foundKeyBytes = lnKey;
	    }
            byte[] newData;

            /* Resolve partial puts. */
            if (data.getPartial()) {
                int dlen = data.getPartialLength();
                int doff = data.getPartialOffset();
                int origlen = (foundDataBytes != null) ?
                    foundDataBytes.length : 0;
                int oldlen = (doff + dlen > origlen) ? doff + dlen : origlen;
                int len = oldlen - dlen + data.getSize();

		if (len == 0) {
		    newData = LogUtils.ZERO_LENGTH_BYTE_ARRAY;
		} else {
		    newData = new byte[len];
		}
                int pos = 0;

                /*
		 * Keep 0..doff of the old data (truncating if doff > length).
		 */
                int slicelen = (doff < origlen) ? doff : origlen;
                if (slicelen > 0)
                    System.arraycopy(foundDataBytes, 0, newData,
				     pos, slicelen);
                pos += doff;

                /* Copy in the new data. */
                slicelen = data.getSize();
                System.arraycopy(data.getData(), data.getOffset(),
                                 newData, pos, slicelen);
                pos += slicelen;

                /* Append the rest of the old data (if any). */
                slicelen = origlen - (doff + dlen);
                if (slicelen > 0)
                    System.arraycopy(foundDataBytes, doff + dlen, newData, pos,
                                     slicelen);
            } else {
                int len = data.getSize();
		if (len == 0) {
		    newData = LogUtils.ZERO_LENGTH_BYTE_ARRAY;
		} else {
		    newData = new byte[len];
		}
                System.arraycopy(data.getData(), data.getOffset(),
                                 newData, 0, len);
            }

            if (database.getSortedDuplicates()) {
		/* Check that data compares equal before replacing it. */
		boolean keysEqual = false;

                /*
                 * Do not use a custom duplicate comaprator here because we do
                 * not support replacing the data if it is different, even if
                 * the custom comparator considers it equal.  If we were to
                 * support this we would have to update the key in the BIN
                 * slot. [#15527]
                 */
		if (foundDataBytes != null) {
                    keysEqual =
                        Key.compareKeys(foundDataBytes, newData, null) == 0;
		}

		if (!keysEqual) {
		    revertLock(ln, lockResult);
		    throw new DatabaseException
			("Can't replace a duplicate with different data.");
		}
	    }

	    if (foundData != null) {
		setDbt(foundData, foundDataBytes);
	    }
	    if (foundKey != null) {
		setDbt(foundKey, foundKeyBytes);
	    }

            /*
             * Between the release of the BIN latch and acquiring the write
             * lock any number of operations may have executed which would
             * result in a new abort LSN for this record. Therefore, wait until
             * now to get the abort LSN.
             */
	    long oldLsn = targetBin.getLsn(targetIndex);
	    lockResult.setAbortLsn
		(oldLsn, targetBin.isEntryKnownDeleted(targetIndex));

            /* 
	     * The modify has to be inside the latch so that the BIN is updated
	     * inside the latch.
	     */
            long oldLNSize = ln.getMemorySizeIncludedByParent();
	    byte[] newKey = (isDup ? targetBin.getDupKey() : lnKey);
            long newLsn = ln.modify(newData, database, newKey, oldLsn, locker);
            long newLNSize = ln.getMemorySizeIncludedByParent();

            /* Update the parent BIN. */
            targetBin.updateEntry(targetIndex, newLsn, oldLNSize, newLNSize);
            releaseBINs();

            trace(Level.FINER, TRACE_MOD, targetBin,
                  ln, targetIndex, oldLsn, newLsn);

            status = CURSOR_INITIALIZED;
            return OperationStatus.SUCCESS;
        } finally {
            releaseBINs();
        }
    }

    /*
     * Gets 
     */

    /**
     * Retrieve the current record.
     */
    public OperationStatus getCurrent(DatabaseEntry foundKey,
				      DatabaseEntry foundData,
				      LockType lockType)
        throws DatabaseException {

        assert assertCursorState(true) : dumpToString(true);

        // If not pointing at valid entry, return failure
        if (bin == null) {
            return OperationStatus.KEYEMPTY;
        }

        if (dupBin == null) {
	    latchBIN();
        } else {
	    latchDBIN();
        }

        return getCurrentAlreadyLatched(foundKey, foundData, lockType, true);
    }

    /**
     * Retrieve the current record. Assume the bin is already latched.  Return
     * with the target bin unlatched.
     */
    public OperationStatus getCurrentAlreadyLatched(DatabaseEntry foundKey,
						    DatabaseEntry foundData,
						    LockType lockType,
						    boolean first)
        throws DatabaseException {

        assert assertCursorState(true) : dumpToString(true);
        assert checkAlreadyLatched(true) : dumpToString(true);

        try {
            return fetchCurrent(foundKey, foundData, lockType, first);
        } finally {
            releaseBINs();
        }
    }

    /**
     * Retrieve the current LN, return with the target bin unlatched.
     */
    public LN getCurrentLN(LockType lockType) 
        throws DatabaseException {

        assert assertCursorState(true) : dumpToString(true);

        if (bin == null) {
            return null;
        } else {
	    latchBIN();
            return getCurrentLNAlreadyLatched(lockType);
        }
    }

    /**
     * Retrieve the current LN, assuming the BIN is already latched.  Return
     * with the target BIN unlatched.
     */
    public LN getCurrentLNAlreadyLatched(LockType lockType) 
        throws DatabaseException {

        try {
            assert assertCursorState(true) : dumpToString(true);
            assert checkAlreadyLatched(true) : dumpToString(true);

            if (bin == null) {
                return null;
            }

            /*
             * Get a reference to the LN under the latch.  Check the deleted
             * flag in the BIN.  If fetchTarget returns null, a deleted LN was
             * cleaned.
             */
            LN ln = null;
            if (!bin.isEntryKnownDeleted(index)) {
                ln = (LN) bin.fetchTarget(index);
            }
            if (ln == null) {
		releaseBIN();
                return null;
            }

            addCursor(bin);
        
            /* Lock LN.  */
            LockResult lockResult = lockLN(ln, lockType);
	    ln = lockResult.getLN();

            /* Don't set abort LSN for a read operation! */
            return ln;

        } finally {
            releaseBINs();
        }
    }

    public OperationStatus getNext(DatabaseEntry foundKey,
				   DatabaseEntry foundData,
				   LockType lockType,
				   boolean forward,
				   boolean alreadyLatched)
        throws DatabaseException {

        return getNextWithKeyChangeStatus
            (foundKey, foundData, lockType, forward, alreadyLatched).status;
    }

    /**
     * Move the cursor forward and return the next record. This will cross BIN
     * boundaries and dip into duplicate sets.
     *
     * @param foundKey DatabaseEntry to use for returning key
     *
     * @param foundData DatabaseEntry to use for returning data
     *
     * @param forward if true, move forward, else move backwards
     *
     * @param alreadyLatched if true, the bin that we're on is already
     * latched.
     *
     * @return the status and an indication of whether we advanced to a new
     * key during the operation.
     */
    public KeyChangeStatus
        getNextWithKeyChangeStatus(DatabaseEntry foundKey,
				   DatabaseEntry foundData,
				   LockType lockType,
				   boolean forward,
				   boolean alreadyLatched)
        throws DatabaseException {

        assert assertCursorState(true) : dumpToString(true);
        assert checkAlreadyLatched(alreadyLatched) : dumpToString(true);

        KeyChangeStatus result =
            new KeyChangeStatus(OperationStatus.NOTFOUND, true);

	try {
            while (bin != null) {

                /* Are we positioned on a DBIN? */
                if (dupBin != null) {
                    if (DEBUG) {
                        verifyCursor(dupBin);
                    }
                    if (getNextDuplicate(foundKey, foundData, lockType,
                                         forward, alreadyLatched) ==
                        OperationStatus.SUCCESS) {
                        result.status = OperationStatus.SUCCESS;
                        /* We returned a duplicate. */
                        result.keyChange = false;
                        break;
                    } else {
                        removeCursorDBIN();
                        alreadyLatched = false;
                        dupBin = null;
                        dupIndex = -1;
                        continue;
                    }
                }

                assert checkAlreadyLatched(alreadyLatched) :
                    dumpToString(true);
                if (!alreadyLatched) {
                    latchBIN();
                } else {
                    alreadyLatched = false;
                }

                if (DEBUG) {
                    verifyCursor(bin);
                }           

                /* Is there anything left on this BIN? */
                if ((forward && ++index < bin.getNEntries()) ||
                    (!forward && --index > -1)) {

                    OperationStatus ret =
                        getCurrentAlreadyLatched(foundKey, foundData,
                                                 lockType, forward);
                    if (ret == OperationStatus.SUCCESS) {
                        incrementLNCount();
                        result.status = OperationStatus.SUCCESS;
                        break;
                    } else {
                        assert LatchSupport.countLatchesHeld() == 0;

                        if (binToBeRemoved != null) {
                            flushBINToBeRemoved();
                        }

                        continue;
                    }

                } else {
                    
                    /*
                     * PriorBIN is used to release a BIN earlier in the 
                     * traversal chain when we move onto the next BIN. When
                     * we traverse across BINs, there is a point when two BINs
                     * point to the same cursor.
                     *
                     * Example:  BINa(empty) BINb(empty) BINc(populated)
                     *           Cursor (C) is traversing
                     * loop, leaving BINa: 
                     *   priorBIN is null, C points to BINa, BINa points to C
                     *   set priorBin to BINa
                     *   find BINb, make BINb point to C 
                     *   note that BINa and BINb point to C.
                     * loop, leaving BINb:
                     *   priorBIN == BINa, remove C from BINa
                     *   set priorBin to BINb
                     *   find BINc, make BINc point to C
                     *   note that BINb and BINc point to C
                     * finally, when leaving this method, remove C from BINb.
                     */
                    if (binToBeRemoved != null) {
                        releaseBIN();
                        flushBINToBeRemoved();
                        latchBIN();
                    }
                    binToBeRemoved = bin;
                    bin = null;

                    BIN newBin;

                    /* 
                     * SR #12736 
                     * Prune away oldBin. Assert has intentional side effect
                     */
                    assert TestHookExecute.doHookIfSet(testHook); 

                    if (forward) {
                        newBin = database.getTree().getNextBin
                            (binToBeRemoved,
                             false /* traverseWithinDupTree */);
                    } else {
                        newBin = database.getTree().getPrevBin
                            (binToBeRemoved,
                             false /* traverseWithinDupTree */);
                    }
                    if (newBin == null) {
                        result.status = OperationStatus.NOTFOUND;
                        break;
                    } else {
                        if (forward) {
                            index = -1;
                        } else {
                            index = newBin.getNEntries();
                        }
                        addCursor(newBin);
                        /* Ensure that setting bin is under newBin's latch */
                        bin = newBin;
                        alreadyLatched = true;
                    }
                }
            }
	} finally {
	    assert LatchSupport.countLatchesHeld() == 0 :
		LatchSupport.latchesHeldToString();
	    if (binToBeRemoved != null) {
		flushBINToBeRemoved();
	    }
	}
        return result;
    }

    private void flushBINToBeRemoved()
	throws DatabaseException {

	binToBeRemoved.latch();
	binToBeRemoved.removeCursor(this);
	binToBeRemoved.releaseLatch();
	binToBeRemoved = null;
    }

    public OperationStatus getNextNoDup(DatabaseEntry foundKey,
					DatabaseEntry foundData, 
					LockType lockType,
                                        boolean forward,
                                        boolean alreadyLatched)
        throws DatabaseException {

        assert assertCursorState(true) : dumpToString(true);

        if (dupBin != null) {
            clearDupBIN(alreadyLatched);
            alreadyLatched = false;
        }

        return getNext(foundKey, foundData, lockType, forward, alreadyLatched);
    }

    /**
     * Retrieve the first duplicate at the current cursor position.
     */
    public OperationStatus getFirstDuplicate(DatabaseEntry foundKey,
                                             DatabaseEntry foundData, 
                                             LockType lockType)
        throws DatabaseException {

        assert assertCursorState(true) : dumpToString(true);

        /*
         * By clearing the dupBin, the next call to fetchCurrent will move to
         * the first duplicate.
         */
        if (dupBin != null) {
            removeCursorDBIN();
            dupBin = null;
            dupIndex = -1;
        }

        return getCurrent(foundKey, foundData, lockType);
    }

    /** 
     * Enter with dupBin unlatched.  Pass foundKey == null to just advance
     * cursor to next duplicate without fetching data.
     */
    public OperationStatus getNextDuplicate(DatabaseEntry foundKey,
					    DatabaseEntry foundData, 
					    LockType lockType,
					    boolean forward,
					    boolean alreadyLatched)
        throws DatabaseException {

        assert assertCursorState(true) : dumpToString(true);
        assert checkAlreadyLatched(alreadyLatched) : dumpToString(true);
	try {
	    while (dupBin != null) {
		if (!alreadyLatched) {
		    latchDBIN();
		} else {
		    alreadyLatched = false;
		}

		if (DEBUG) {
		    verifyCursor(dupBin);
		}

		/* Are we still on this DBIN? */
		if ((forward && ++dupIndex < dupBin.getNEntries()) ||
		    (!forward && --dupIndex > -1)) {

		    OperationStatus ret = OperationStatus.SUCCESS;
		    if (foundKey != null) {
			ret = getCurrentAlreadyLatched(foundKey, foundData,
                                                       lockType, forward);
		    } else {
			releaseDBIN();
		    }
		    if (ret == OperationStatus.SUCCESS) {
			incrementLNCount();
			return ret;
		    } else {
			assert LatchSupport.countLatchesHeld() == 0;

			if (dupBinToBeRemoved != null) {
			    flushDBINToBeRemoved();
			}

			continue;
		    }

		} else {

		    /*
		     * We need to go to the next DBIN.  Remove the cursor and
		     * be sure to change the dupBin field after removing the
		     * cursor.
		     */
		    if (dupBinToBeRemoved != null) {
			flushDBINToBeRemoved();
		    }
		    dupBinToBeRemoved = dupBin;

		    dupBin = null;
		    dupBinToBeRemoved.releaseLatch();
                
                    TreeWalkerStatsAccumulator treeStatsAccumulator =
                        getTreeStatsAccumulator();
                    if (treeStatsAccumulator != null) {
                        latchBIN();
                        try {
                            if (index < 0) {
                                /* This duplicate tree has been deleted. */
                                return OperationStatus.NOTFOUND;
                            }

                            DIN duplicateRoot = (DIN) bin.fetchTarget(index);
                            duplicateRoot.latch();
                            try {
                                DupCountLN dcl = duplicateRoot.getDupCountLN();
                                if (dcl != null) {
                                    dcl.accumulateStats(treeStatsAccumulator);
                                }
                            } finally {
                                duplicateRoot.releaseLatch();
                            }
                        } finally {
                            releaseBIN();
                        }
                    }
		    assert (LatchSupport.countLatchesHeld() == 0);

		    dupBinToBeRemoved.latch();
		    DBIN newDupBin;

		    if (forward) {
			newDupBin = (DBIN) database.getTree().getNextBin
			    (dupBinToBeRemoved, 	
		   	     true /* traverseWithinDupTree*/);
		    } else {
			newDupBin = (DBIN) database.getTree().getPrevBin
			    (dupBinToBeRemoved,
			     true /* traverseWithinDupTree*/);
		    }

		    if (newDupBin == null) {
			return OperationStatus.NOTFOUND;
		    } else {
			if (forward) {
			    dupIndex = -1;
			} else {
			    dupIndex = newDupBin.getNEntries();
			}
			addCursor(newDupBin);

			/* 
			 * Ensure that setting dupBin is under newDupBin's
			 * latch.
			 */
			dupBin = newDupBin;
			alreadyLatched = true;
		    }
		}
	    }
	} finally {
	    assert LatchSupport.countLatchesHeld() == 0;
	    if (dupBinToBeRemoved != null) {
		flushDBINToBeRemoved();
	    }
	}

        return OperationStatus.NOTFOUND;
    }

    private void flushDBINToBeRemoved()
	throws DatabaseException {

	dupBinToBeRemoved.latch();
	dupBinToBeRemoved.removeCursor(this);
	dupBinToBeRemoved.releaseLatch();
	dupBinToBeRemoved = null;
    }

    /**
     * Position the cursor at the first or last record of the database.  It's
     * okay if this record is deleted. Returns with the target BIN latched.
     * 
     * @return true if a first or last position is found, false if the 
     * tree being searched is empty.
     */
    public boolean positionFirstOrLast(boolean first, DIN duplicateRoot)
        throws DatabaseException {

        assert assertCursorState(false) : dumpToString(true);

        IN in = null;
        boolean found = false;
        try {
            if (duplicateRoot == null) {
                removeCursorBIN();
                if (first) {
                    in = database.getTree().getFirstNode();
                } else {
                    in = database.getTree().getLastNode();
                }

                if (in != null) {

                    assert (in instanceof BIN);

                    dupBin = null;
                    dupIndex = -1;
                    bin = (BIN) in;
                    index = (first ? 0 : (bin.getNEntries() - 1));
                    addCursor(bin);

                    TreeWalkerStatsAccumulator treeStatsAccumulator =
                        getTreeStatsAccumulator();

                    if (bin.getNEntries() == 0) {

                        /*
                         * An IN was found. Even if it's empty, let Cursor
                         * handle moving to the first non-deleted entry.
                         */
                        found = true;
                    } else {

                        /*
                         * See if we need to descend further.  If fetchTarget
                         * returns null, a deleted LN was cleaned.
                         */
                        Node n = null;
                        if (!in.isEntryKnownDeleted(index)) {
                            n = in.fetchTarget(index);
                        }

                        if (n != null && n.containsDuplicates()) {
                            DIN dupRoot = (DIN) n;
                            dupRoot.latch();
                            in.releaseLatch();
                            in = null;
                            found = positionFirstOrLast(first, dupRoot);
                        } else {

                            /* 
                             * Even if the entry is deleted, just leave our
                             * position here and return.
                             */
                            if (treeStatsAccumulator != null) {
                                if (n == null || ((LN) n).isDeleted()) {
                                    treeStatsAccumulator.
                                        incrementDeletedLNCount();
                                } else {
                                    treeStatsAccumulator.
                                        incrementLNCount();
                                }
                            }
                            found = true;
                        }
                    }
                }
            } else {
                removeCursorDBIN();
                if (first) {
                    in = database.getTree().getFirstNode(duplicateRoot);
                } else {
                    in = database.getTree().getLastNode(duplicateRoot);
                }

                if (in != null) {

		    /* 
		     * An IN was found. Even if it's empty, let Cursor handle
		     * moving to the first non-deleted entry.
		     */
		    assert (in instanceof DBIN);

		    dupBin = (DBIN) in;
                    dupIndex = (first ? 0 : (dupBin.getNEntries() - 1));
		    addCursor(dupBin);
		    found = true;
                }
            }
            status = CURSOR_INITIALIZED;
            return found;
        } catch (DatabaseException e) {
            /* Release latch on error. */
            if (in != null) {
                in.releaseLatch();
            }
            throw e;
        }
    }

    public static final int FOUND = 0x1;
    /* Exact match on the key portion. */
    public static final int EXACT_KEY = 0x2;
    /* Exact match on the DATA portion when searchAndPositionBoth used. */
    public static final int EXACT_DATA = 0x4;
    /* Record found is the last one in the database. */
    public static final int FOUND_LAST = 0x8;

    /**
     * Position the cursor at the key. This returns a three part value that's
     * bitwise or'ed into the int. We find out if there was any kind of match
     * and if the match was exact. Note that this match focuses on whether the
     * searching criteria (key, or key and data, depending on the search type)
     * is met.
     *
     * <p>Note this returns with the BIN latched!</p>
     *
     * <p>If this method returns without the FOUND bit set, the caller can
     * assume that no match is possible.  Otherwise, if the FOUND bit is set,
     * the caller should check the EXACT_KEY and EXACT_DATA bits.  If EXACT_KEY
     * is not set (or for BOTH and BOTH_RANGE, if EXACT_DATA is not set), an
     * approximate match was found.  In an approximate match, the cursor is
     * always positioned before the target key/data.  This allows the caller to
     * perform a 'next' operation to advance to the value that is equal or
     * higher than the target key/data.</p>
     *
     * <p>Even if the search returns an exact result, the record may be
     * deleted.  The caller must therefore check for both an approximate match
     * and for whether the cursor is positioned on a deleted record.</p>
     *
     * <p>If SET or BOTH is specified, the FOUND bit will only be returned if
     * an exact match is found.  However, the record found may be deleted.</p>
     *
     * <p>There is one special case where this method may be called without
     * checking the EXACT_KEY (and EXACT_DATA) bits and without checking for a
     * deleted record:  If SearchMode.SET is specified then only the FOUND bit
     * need be checked.  When SET is specified and FOUND is returned, it is
     * guaranteed to be an exact match on a non-deleted record.  It is for this
     * case only that this method is public.</p>
     *
     * <p>If FOUND is set, FOUND_LAST may also be set if the cursor is
     * positioned on the last record in the database.  Note that this state can
     * only be counted on as long as the BIN is latched, so it is not set if
     * this method must release the latch to lock the record.  Therefore, it
     * should only be used for optimizations.  If FOUND_LAST is set, the cursor
     * is positioned on the last record and the BIN is latched.  If FOUND_LAST
     * is not set, the cursor may or may not be positioned on the last record.
     * Note that exact searches always perform an unlatch and a lock, so
     * FOUND_LAST will only be set for inexact (range) searches.</p>
     *
     * <p>Be aware that when an approximate match is returned, the index or
     * dupIndex may be set to -1.  This is done intentionally so that a 'next'
     * operation will increment it.</p>
     */
    public int searchAndPosition(DatabaseEntry matchKey,
				 DatabaseEntry matchData,
				 SearchMode searchMode,
				 LockType lockType)
        throws DatabaseException {

        assert assertCursorState(false) : dumpToString(true);

        removeCursor();

	/* Reset the cursor. */
        bin = null;
	dupBin = null;
	dupIndex = -1;

        boolean foundSomething = false;
        boolean foundExactKey = false;
        boolean foundExactData = false;
        boolean foundLast = false;
        boolean exactSearch = searchMode.isExactSearch();
        BINBoundary binBoundary = new BINBoundary();

        try {
            byte[] key = Key.makeKey(matchKey);
            bin = (BIN) database.getTree().search
                (key, Tree.SearchType.NORMAL, -1, binBoundary,
                 true /*updateGeneration*/);

            if (bin != null) {
                addCursor(bin);

		/*
                 * If we're doing an exact search, tell bin.findEntry we
                 * require an exact match. If it's a range search, we don't
                 * need that exact match.
		 */
                index = bin.findEntry(key, true, exactSearch);

		/* 
                 * If we're doing an exact search, as a starting point, we'll
                 * assume that we haven't found anything. If this is a range
                 * search, we'll assume the opposite, that we have found a
                 * record. That's because for a range search, the higher level
                 * will take care of sorting out whether anything is really
                 * there or not.
		 */
		foundSomething = !exactSearch;
                boolean containsDuplicates = false;

                if (index >= 0) {
		    if ((index & IN.EXACT_MATCH) != 0) {

                        /* 
                         * The binary search told us we had an exact match.
                         * Note that this really only tells us that the key
                         * matched. The underlying LN may be deleted or the
                         * reference may be knownDeleted, or maybe there's a
                         * dup tree w/no entries, but the next layer up will
                         * find these cases.
                         */
			foundExactKey = true;

                        /* 
                         * Now turn off the exact match bit so the index will
                         * be a valid value, before we use it to retrieve the
                         * child reference from the bin.
                         */
                        index &= ~IN.EXACT_MATCH;
		    }

                    /* 
		     * If fetchTarget returns null, a deleted LN was cleaned.
		     */
                    Node n = null;
                    if (!bin.isEntryKnownDeleted(index)) {
                        n = bin.fetchTarget(index);
                    }
                    if (n != null) {
                        containsDuplicates = n.containsDuplicates();
                        if (searchMode.isDataSearch()) {
                            if (foundExactKey) {
                                /* If the key matches, try the data. */
                                int searchResult = searchAndPositionBoth
                                    (containsDuplicates, n, matchData,
                                     exactSearch, lockType);
                                foundSomething =
                                    (searchResult & FOUND) != 0;
                                foundExactData =
                                    (searchResult & EXACT_DATA) != 0;
                            }
                        } else {
                            foundSomething = true;
                            if (!containsDuplicates && exactSearch) {
				/* Lock LN, check if deleted. */
				LN ln = (LN) n;
				LockResult lockResult = lockLN(ln, lockType);
				ln = lockResult.getLN();

				if (ln == null) {
				    foundSomething = false;
				}

				/*
				 * Note that we must not set the abort LSN for
                                 * a read operation, lest false obsoletes are
                                 * set. [13158]
                                 */
                            }
                        }
		    }

                    /*
                     * Determine whether the last record was found.  This is
                     * only possible when we don't lock the record, and when
                     * there are no duplicates.
                     */
                    foundLast = (searchMode == SearchMode.SET_RANGE &&
                                 foundSomething &&
                                 !containsDuplicates &&
                                 binBoundary.isLastBin &&
                                 index == bin.getNEntries() - 1);
		}
            }
            status = CURSOR_INITIALIZED;

            /* Return a two part status value */
            return (foundSomething ? FOUND : 0) |
                (foundExactKey ? EXACT_KEY : 0) |
                (foundExactData ? EXACT_DATA : 0) |
                (foundLast ? FOUND_LAST : 0);
        } catch (DatabaseException e) {
            /* Release latch on error. */
	    releaseBIN();
            throw e;
        }
    }

    /**
     * For this type of search, we need to match both key and data.  This
     * method is called after the key is matched to perform the data portion of
     * the match. We may be matching just against an LN, or doing further
     * searching into the dup tree.  See searchAndPosition for more details.
     */
    private int searchAndPositionBoth(boolean containsDuplicates,
				      Node n,
				      DatabaseEntry matchData,
				      boolean exactSearch,
				      LockType lockType)
        throws DatabaseException {

        assert assertCursorState(false) : dumpToString(true);

	boolean found = false;
	boolean exact = false;
	assert (matchData != null);
	byte[] data = Key.makeKey(matchData);

        if (containsDuplicates) {
            /* It's a duplicate tree. */
            DIN duplicateRoot = (DIN) n;
            duplicateRoot.latch();
	    releaseBIN();
            dupBin = (DBIN) database.getTree().searchSubTree
                (duplicateRoot, data, Tree.SearchType.NORMAL, -1, null,
                 true /*updateGeneration*/);
            if (dupBin != null) {
                /* Find an exact match. */
                addCursor(dupBin);
                dupIndex = dupBin.findEntry(data, true, exactSearch);
                if (dupIndex >= 0) {
		    if ((dupIndex & IN.EXACT_MATCH) != 0) {
			exact = true;
		    }
		    dupIndex &= ~IN.EXACT_MATCH;
                    found = true;
                } else {

                    /*
                     * The first duplicate is greater than the target data.
                     * Set index so that a 'next' operation moves to the first
                     * duplicate.
                     */
                    dupIndex = -1;
                    found = !exactSearch;
                }
            }
        } else {
	    /* Not a duplicate, but checking for both key and data match. */
            LN ln = (LN) n;

	    /* Lock LN, check if deleted. */
	    LockResult lockResult = lockLN(ln, lockType);

	    /*
	     * Note that during the lockLN call, this cursor may have been
	     * adjusted to refer to an LN in a duplicate tree.  This happens in
	     * the case where we entered with a non-duplicate tree LN and
	     * during the lock call it was mutated to a duplicate tree.  The LN
	     * is still the correct LN, but our cursor is now down in a
	     * duplicate tree. [#14230].
	     */

	    ln = lockResult.getLN();

	    if (ln == null) {
                found = !exactSearch;
	    } else {

		/* Don't set abort LSN for read operation. [#13158] */

                /*
                 * The comparison logic below mimics IN.findEntry as used above
                 * for duplicates.
                 */
		int cmp = Key.compareKeys
                    (ln.getData(), data, database.getDuplicateComparator());
                if (cmp == 0 || (cmp <= 0 && !exactSearch)) {
                    if (cmp == 0) {
                        exact = true;
                    }
                    found = true;
                } else {

                    /*
                     * The current record's data is greater than the target
                     * data.  Set index so that a 'next' operation moves to the
                     * current record.
                     */
		    if (dupBin == null) {
			index--;
		    } else {
			/* We may now be pointing at a dup tree. [#14230]. */
			dupIndex--;
		    }
                    found = !exactSearch;
                }
	    }
        }

	return (found ? FOUND : 0) |
            (exact ? EXACT_DATA : 0);
    }

    /* 
     * Lock and copy current record into the key and data DatabaseEntry. Enter
     * with the BIN/DBIN latched.
     */
    private OperationStatus fetchCurrent(DatabaseEntry foundKey,
					 DatabaseEntry foundData,
					 LockType lockType,
					 boolean first)
        throws DatabaseException {

        TreeWalkerStatsAccumulator treeStatsAccumulator =
	    getTreeStatsAccumulator();

        boolean duplicateFetch = setTargetBin();
        if (targetBin == null) {
            return OperationStatus.NOTFOUND;
        }

        assert targetBin.isLatchOwnerForWrite();

        /* 
	 * Check the deleted flag in the BIN and make sure this isn't an empty
	 * BIN.  The BIN could be empty by virtue of the compressor running the
	 * size of this BIN to 0 but not having yet deleted it from the tree.
         *
         * The index may be negative if we're at an intermediate stage in an
         * higher level operation, and we expect a higher level method to do a
         * next or prev operation after this returns KEYEMPTY. [#11700]
	 */
        Node n = null;

        if (targetIndex < 0 ||
            targetIndex >= targetBin.getNEntries() ||
	    targetBin.isEntryKnownDeleted(targetIndex)) {
            /* Node is no longer present. */
        } else {

	    /* 
	     * If we encounter a pendingDeleted entry, add it to the compressor
	     * queue.
	     */
	    if (targetBin.isEntryPendingDeleted(targetIndex)) {
		EnvironmentImpl envImpl = database.getDbEnvironment();
		envImpl.addToCompressorQueue
		    (targetBin, new Key(targetBin.getKey(targetIndex)), false);
	    }

            /* If fetchTarget returns null, a deleted LN was cleaned. */
	    try {
		n = targetBin.fetchTarget(targetIndex);
	    } catch (DatabaseException DE) {
		targetBin.releaseLatchIfOwner();
		throw DE;
	    }
        }
        if (n == null) {
	    if (treeStatsAccumulator != null) {
		treeStatsAccumulator.incrementDeletedLNCount();
	    }
            targetBin.releaseLatchIfOwner();
            return OperationStatus.KEYEMPTY;
        }

        /*
         * Note that since we have the BIN/DBIN latched, we can safely check
         * the node type. Any conversions from an LN to a dup tree must have
         * the bin latched.
         */
        addCursor(targetBin);
        if (n.containsDuplicates()) {
            assert !duplicateFetch;
            /* Descend down duplicate tree, doing latch coupling. */
            DIN duplicateRoot = (DIN) n;
            duplicateRoot.latch();
            targetBin.releaseLatch();
            if (positionFirstOrLast(first, duplicateRoot)) {
		try {
		    return fetchCurrent(foundKey, foundData, lockType, first);
		} catch (DatabaseException DE) {
		    releaseBINs();
		    throw DE;
		}
            } else {
                return OperationStatus.NOTFOUND;
            }
        }

        LN ln = (LN) n;

	assert TestHookExecute.doHookIfSet(testHook);

        /*
         * Lock the LN.  For dirty-read, the data of the LN can be set to null
         * at any time.  Cache the data in a local variable so its state does
         * not change before calling setDbt further below.
         */
	LockResult lockResult = lockLN(ln, lockType);
	try {
            ln = lockResult.getLN();
            byte[] lnData = (ln != null) ? ln.getData() : null;
            if (ln == null || lnData == null) {
                if (treeStatsAccumulator != null) {
                    treeStatsAccumulator.incrementDeletedLNCount();
                }
                return OperationStatus.KEYEMPTY;
            }

	    duplicateFetch = setTargetBin();
            
            /*
             * Don't set the abort LSN here since we are not logging yet, even
             * if this is a write lock.  Tree.insert depends on the fact that
             * the abortLSN is not already set for deleted items.
             */
            if (duplicateFetch) {
                if (foundData != null) {
                    setDbt(foundData, targetBin.getKey(targetIndex));
                }
                if (foundKey != null) {
                    setDbt(foundKey, targetBin.getDupKey());
                }
            } else {
                if (foundData != null) {
                    setDbt(foundData, lnData);
                }
                if (foundKey != null) {
                    setDbt(foundKey, targetBin.getKey(targetIndex));
                }
            }

            return OperationStatus.SUCCESS;
	} finally {
	    releaseBINs();
	}
    }

    /**
     * Locks the given LN's node ID; a deleted LN will not be locked or
     * returned.  Attempts to use a non-blocking lock to avoid
     * unlatching/relatching.  Retries if necessary, to handle the case where
     * the LN is changed while the BIN is unlatched.
     *
     * Preconditions: The target BIN must be latched.  When positioned in a dup
     * tree, the BIN may be latched on entry also and if so it will be latched
     * on exit.
     *
     * Postconditions: The target BIN is latched.  When positioned in a dup
     * tree, the BIN will be latched if it was latched on entry or a blocking
     * lock was needed.  Therefore, when positioned in a dup tree, releaseDBIN
     * should be called.
     *
     * @param ln the LN to be locked.
     * @param lockType the type of lock requested.
     * @return the LockResult containing the LN that was locked, or containing
     * a null LN if the LN was deleted or cleaned.  If the LN is deleted, a
     * lock will not be held.
     */
    private LockResult lockLN(LN ln, LockType lockType)
	throws DatabaseException {

        LockResult lockResult = lockLNDeletedAllowed(ln, lockType);
        ln = lockResult.getLN();
        if (ln != null) {
            setTargetBin();
            if (targetBin.isEntryKnownDeleted(targetIndex) ||
                ln.isDeleted()) {
                revertLock(ln.getNodeId(), lockResult.getLockGrant());
                lockResult.setLN(null);
            }
        }
        return lockResult;
    }

    /**
     * Locks the given LN's node ID; a deleted LN will be locked and returned.
     * Attempts to use a non-blocking lock to avoid unlatching/relatching.
     * Retries if necessary, to handle the case where the LN is changed while
     * the BIN is unlatched.
     *
     * Preconditions: The target BIN must be latched.  When positioned in a dup
     * tree, the BIN may be latched on entry also and if so it will be latched
     * on exit.
     *
     * Postconditions: The target BIN is latched.  When positioned in a dup
     * tree, the BIN will be latched if it was latched on entry or a blocking
     * lock was needed.  Therefore, when positioned in a dup tree, releaseDBIN
     * should be called.
     *
     * @param ln the LN to be locked.
     * @param lockType the type of lock requested.
     * @return the LockResult containing the LN that was locked, or containing
     * a null LN if the LN was cleaned.
     */
    public LockResult lockLNDeletedAllowed(LN ln, LockType lockType)
	throws DatabaseException {

        LockResult lockResult;

        /* For dirty-read, there is no need to fetch the node. */
        if (lockType == LockType.NONE) {
            lockResult = new LockResult(LockGrantType.NONE_NEEDED, null);
            lockResult.setLN(ln);
            return lockResult;
        }

        /*
         * Try a non-blocking lock first, to avoid unlatching.  If the default
         * is no-wait, use the standard lock method so LockNotGrantedException
         * is thrown; there is no need to try a non-blocking lock twice.
         */
        if (locker.getDefaultNoWait()) {
            try {
                lockResult = locker.lock
                    (ln.getNodeId(), lockType, true /*noWait*/, database);
            } catch (LockNotGrantedException e) {
                /* Release all latches. */
                releaseBINs();
                throw e;
            }
        } else {
            lockResult = locker.nonBlockingLock
                (ln.getNodeId(), lockType, database);
        }
        if (lockResult.getLockGrant() != LockGrantType.DENIED) {
            lockResult.setLN(ln);
            return lockResult;
        }

        /*
         * Unlatch, get a blocking lock, latch, and get the current node from
         * the slot.  If the node ID changed while unlatched, revert the lock
         * and repeat.
         */
	while (true) {

            /* Save the node ID we're locking and request a lock. */
	    long nodeId = ln.getNodeId();
	    releaseBINs();
            lockResult = locker.lock
                (nodeId, lockType, false /*noWait*/, database);

            /* Fetch the current node after locking. */
	    latchBINs();
	    setTargetBin();
	    ln = (LN) targetBin.fetchTarget(targetIndex);

            if (ln != null && nodeId != ln.getNodeId()) {
                /* If the node ID changed, revert the lock and try again. */
                revertLock(nodeId, lockResult.getLockGrant());
                continue;
            } else {
                /* If null (cleaned) or locked correctly, return the LN. */
                lockResult.setLN(ln);
                return lockResult;
            }
	}
    }

    /**
     * Locks the DupCountLN for the given duplicate root.  Attempts to use a
     * non-blocking lock to avoid unlatching/relatching.
     *
     * Preconditions: The dupRoot, BIN and DBIN are latched.
     * Postconditions: The dupRoot, BIN and DBIN are latched.
     *
     * Note that the dupRoot may change during locking and should be refetched
     * if needed.
     *
     * @param dupRoot the duplicate root containing the DupCountLN to be
     * locked.
     * @param lockType the type of lock requested.
     * @return the LockResult containing the LN that was locked.
     */
    public LockResult lockDupCountLN(DIN dupRoot, LockType lockType)
	throws DatabaseException {

        DupCountLN ln = dupRoot.getDupCountLN();
        LockResult lockResult;

        /*
         * Try a non-blocking lock first, to avoid unlatching.  If the default
         * is no-wait, use the standard lock method so LockNotGrantedException
         * is thrown; there is no need to try a non-blocking lock twice.
         */
        if (locker.getDefaultNoWait()) {
            try {
                lockResult = locker.lock
                    (ln.getNodeId(), lockType, true /*noWait*/, database);
            } catch (LockNotGrantedException e) {
                /* Release all latches. */
                dupRoot.releaseLatch();
                releaseBINs();
                throw e;
            }
        } else {
            lockResult = locker.nonBlockingLock
                (ln.getNodeId(), lockType, database);
        }

        if (lockResult.getLockGrant() == LockGrantType.DENIED) {
            /* Release all latches. */
            dupRoot.releaseLatch();
            releaseBINs();
            /* Request a blocking lock. */
            lockResult = locker.lock
                (ln.getNodeId(), lockType, false /*noWait*/, database);
            /* Reacquire all latches. */
            latchBIN();
            dupRoot = (DIN) bin.fetchTarget(index);
            dupRoot.latch();
            latchDBIN();
            ln = dupRoot.getDupCountLN();
        }
        lockResult.setLN(ln);
        return lockResult;
    }

    /**
     * Fetch, latch and return the DIN root of the duplicate tree at the cursor
     * position.
     *
     * Preconditions: The BIN must be latched and the current BIN entry must
     * contain a DIN.
     *
     * Postconditions: The BIN and DIN will be latched.  The DBIN will remain
     * latched if isDBINLatched is true.
     *
     * @param isDBINLatched is true if the DBIN is currently latched.
     */
    public DIN getLatchedDupRoot(boolean isDBINLatched) 
        throws DatabaseException {

        assert bin != null;
        assert bin.isLatchOwnerForWrite();
        assert index >= 0;

        DIN dupRoot = (DIN) bin.fetchTarget(index);

        if (isDBINLatched) {

	    /*
             * The BIN and DBIN are currently latched and we need to latch the
             * dupRoot, which is between the BIN and DBIN in the tree.  First
             * trying latching the dupRoot no-wait; if this works, we have
             * latched out of order, but in a way that does not cause
             * deadlocks.  If we don't get the no-wait latch, then release the
             * DBIN latch and latch in the proper order down the tree.
	     */
            if (!dupRoot.latchNoWait()) {
                releaseDBIN();
                dupRoot.latch();
                latchDBIN();
            }
        } else {
            dupRoot.latch();
        }

        return dupRoot;
    }

    /**
     * Helper to return a Data DBT from a BIN.
     */
    public static void setDbt(DatabaseEntry data, byte[] bytes) {

        if (bytes != null) {
	    boolean partial = data.getPartial();
            int off = partial ? data.getPartialOffset() : 0;
            int len = partial ? data.getPartialLength() : bytes.length;
	    if (off + len > bytes.length) {
		len = (off > bytes.length) ? 0 : bytes.length  - off;
	    }

	    byte[] newdata = null;
	    if (len == 0) {
		newdata = LogUtils.ZERO_LENGTH_BYTE_ARRAY;
	    } else {
		newdata = new byte[len];
		System.arraycopy(bytes, off, newdata, 0, len);
	    }
            data.setData(newdata);
            data.setOffset(0);
            data.setSize(len);
        } else {
            data.setData(null);
            data.setOffset(0);
            data.setSize(0);
        }
    }

    /*
     * For debugging. Verify that a BINs cursor set refers to the BIN.
     */
    private void verifyCursor(BIN bin)
        throws DatabaseException {

        if (!bin.getCursorSet().contains(this)) {
            throw new DatabaseException("BIN cursorSet is inconsistent.");
        }
    }

    /**
     * Calls checkCursorState and returns false is an exception is thrown.
     */
    private boolean assertCursorState(boolean mustBeInitialized) {
        try {
            checkCursorState(mustBeInitialized);
            return true;
        } catch (DatabaseException e) {
            return false;
        }
    }

    /**
     * Check that the cursor is open and optionally if it is initialized.
     */
    public void checkCursorState(boolean mustBeInitialized)
        throws DatabaseException {

        if (status == CURSOR_INITIALIZED) {

            if (DEBUG) {
                if (bin != null) {
                    verifyCursor(bin);
                }
                if (dupBin != null) {
                    verifyCursor(dupBin);
                }
            }           

            return;
        } else if (status == CURSOR_NOT_INITIALIZED) {
            if (mustBeInitialized) {
                throw new DatabaseException
                    ("Cursor Not Initialized.");
            }
        } else if (status == CURSOR_CLOSED) {
            throw new DatabaseException
                ("Cursor has been closed.");
        } else {
            throw new DatabaseException
                ("Unknown cursor status: " + status);
        }
    }

    /**
     * Return this lock to its prior status. If the lock was just obtained,
     * release it. If it was promoted, demote it.
     */
    private void revertLock(LN ln, LockResult lockResult) 
        throws DatabaseException {

        revertLock(ln.getNodeId(), lockResult.getLockGrant());
    }

    /**
     * Return this lock to its prior status. If the lock was just obtained,
     * release it. If it was promoted, demote it.
     */
    private void revertLock(long nodeId, LockGrantType lockStatus) 
        throws DatabaseException {

        if ((lockStatus == LockGrantType.NEW) ||
            (lockStatus == LockGrantType.WAIT_NEW)) {
            locker.releaseLock(nodeId);
        } else if ((lockStatus == LockGrantType.PROMOTION) ||
                   (lockStatus == LockGrantType.WAIT_PROMOTION)){
            locker.demoteLock(nodeId);
        }
    }

    /**
     * Locks the logical EOF node for the database.
     */
    public void lockEofNode(LockType lockType)
        throws DatabaseException {

        locker.lock
            (database.getEofNodeId(), lockType, false /*noWait*/, database);
    }

    /**
     * @throws RunRecoveryException if the underlying environment is invalid.
     */
    public void checkEnv() 
        throws RunRecoveryException {
        
        database.getDbEnvironment().checkIfInvalid();
    }

    /*
     * Support for linking cursors onto lockers.
     */
    public CursorImpl getLockerPrev() {
        return lockerPrev;
    }

    public CursorImpl getLockerNext() {
        return lockerNext;
    }

    public void setLockerPrev(CursorImpl p) {
        lockerPrev = p;
    }

    public void setLockerNext(CursorImpl n) {
        lockerNext = n;
    }

    /**
     * Dump the cursor for debugging purposes.  Dump the bin and dbin that the
     * cursor refers to if verbose is true.
     */
    public void dump(boolean verbose) {
        System.out.println(dumpToString(verbose));
    }

    /**
     * dump the cursor for debugging purposes.  
     */
    public void dump() {
        System.out.println(dumpToString(true));
    }

    /* 
     * dumper
     */
    private String statusToString(byte status) {
        switch(status) {
        case CURSOR_NOT_INITIALIZED:
            return "CURSOR_NOT_INITIALIZED";
        case CURSOR_INITIALIZED:
            return "CURSOR_INITIALIZED";
        case CURSOR_CLOSED:
            return "CURSOR_CLOSED";
        default:
            return "UNKNOWN (" + Byte.toString(status) + ")";
        }
    }

    /* 
     * dumper
     */
    public String dumpToString(boolean verbose) {
        StringBuffer sb = new StringBuffer();

        sb.append("<Cursor idx=\"").append(index).append("\"");
        if (dupBin != null) {
            sb.append(" dupIdx=\"").append(dupIndex).append("\"");
        }
        sb.append(" status=\"").append(statusToString(status)).append("\"");
        sb.append(">\n");
	if (verbose) {
	    sb.append((bin == null) ? "" : bin.dumpString(2, true));
	    sb.append((dupBin == null) ? "" : dupBin.dumpString(2, true));
	}
        sb.append("\n</Cursor>");

        return sb.toString();
    }

    /*
     * For unit tests
     */
    public LockStats getLockStats() 
        throws DatabaseException {

        return locker.collectStats(new LockStats());
    }

    /**
     * Send trace messages to the java.util.logger. Don't rely on the logger
     * alone to conditionalize whether we send this message, we don't even want
     * to construct the message if the level is not enabled.
     */
    private void trace(Level level,
                       String changeType,
                       BIN theBin,
                       LN ln,
                       int lnIndex,
                       long oldLsn,
                       long newLsn) {
        Logger logger = database.getDbEnvironment().getLogger();
        if (logger.isLoggable(level)) {
            StringBuffer sb = new StringBuffer();
            sb.append(changeType);
            sb.append(" bin=");
            sb.append(theBin.getNodeId());
            sb.append(" ln=");
            sb.append(ln.getNodeId());
            sb.append(" lnIdx=");
            sb.append(lnIndex);
            sb.append(" oldLnLsn=");
            sb.append(DbLsn.getNoFormatString(oldLsn));
            sb.append(" newLnLsn=");
            sb.append(DbLsn.getNoFormatString(newLsn));
	
            logger.log(level, sb.toString());
        }
    }

    /* For unit testing only. */
    public void setTestHook(TestHook hook) {
        testHook = hook;
    }

    /* Check that the target bin is latched. For use in assertions. */
    private boolean checkAlreadyLatched(boolean alreadyLatched) {
        if (alreadyLatched) {
            if (dupBin != null) {
                return dupBin.isLatchOwnerForWrite();
            } else if (bin != null) {
                return bin.isLatchOwnerForWrite();
            }
        } 
        return true;
    }
}
