/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: DatabaseImpl.java,v 1.157.2.7 2007/07/02 19:54:49 mark Exp $
 */

package com.sleepycat.je.dbi;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.sleepycat.je.BtreeStats;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DatabaseStats;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.PreloadConfig;
import com.sleepycat.je.PreloadStats;
import com.sleepycat.je.PreloadStatus;
import com.sleepycat.je.SecondaryDatabase;
import com.sleepycat.je.StatsConfig;
import com.sleepycat.je.VerifyConfig;
import com.sleepycat.je.cleaner.UtilizationTracker;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.SortedLSNTreeWalker.ExceptionPredicate;
import com.sleepycat.je.dbi.SortedLSNTreeWalker.TreeNodeProcessor;
import com.sleepycat.je.latch.LatchSupport;
import com.sleepycat.je.log.LogEntryType;
import com.sleepycat.je.log.LogException;
import com.sleepycat.je.log.LogFileNotFoundException;
import com.sleepycat.je.log.LogUtils;
import com.sleepycat.je.log.Loggable;
import com.sleepycat.je.recovery.Checkpointer;
import com.sleepycat.je.tree.BIN;
import com.sleepycat.je.tree.ChildReference;
import com.sleepycat.je.tree.DBIN;
import com.sleepycat.je.tree.DIN;
import com.sleepycat.je.tree.DupCountLN;
import com.sleepycat.je.tree.IN;
import com.sleepycat.je.tree.LN;
import com.sleepycat.je.tree.Node;
import com.sleepycat.je.tree.Tree;
import com.sleepycat.je.tree.TreeUtils;
import com.sleepycat.je.tree.TreeWalkerStatsAccumulator;
import com.sleepycat.je.tree.WithRootLatched;
import com.sleepycat.je.txn.Locker;
import com.sleepycat.je.txn.ThreadLocker;
import com.sleepycat.je.utilint.CmdUtil;
import com.sleepycat.je.utilint.DbLsn;
import com.sleepycat.je.utilint.TestHook;

/**
 * The underlying object for a given database.
 */
public class DatabaseImpl implements Loggable, Cloneable {

    /* 
     * Delete processing states. See design note on database deletion and 
     * truncation 
     */
    private static final short NOT_DELETED = 1;
    private static final short DELETED_CLEANUP_INLIST_HARVEST = 2;
    private static final short DELETED_CLEANUP_LOG_HARVEST = 3;
    private static final short DELETED = 4;

    private DatabaseId id;             // unique id
    private Tree tree;             
    private EnvironmentImpl envImpl;   // Tree operations find the env this way
    private boolean duplicatesAllowed; // duplicates allowed
    private boolean transactional;     // All open handles are transactional
    private boolean deferredWrite;     // deferred write mode set
    private Set referringHandles; // Set of open Database handles    
    private BtreeStats stats;     // most recent btree stats w/ !DB_FAST_STAT
    private long eofNodeId;       // Logical EOF node for range locking
    private short deleteState;    // one of four delete states.
    private int useCount = 0;     // If non-zero, eviction is prohibited

    /*
     * The user defined Btree and duplicate comparison functions, if specified.
     */
    private Comparator btreeComparator = null;
    private Comparator duplicateComparator = null;
    private byte[] btreeComparatorBytes = LogUtils.ZERO_LENGTH_BYTE_ARRAY;
    private byte[] duplicateComparatorBytes = LogUtils.ZERO_LENGTH_BYTE_ARRAY;
    private boolean btreeComparatorByClassName = false;
    private boolean duplicateComparatorByClassName = false;

    /*
     * Cache some configuration values.
     */
    private int binDeltaPercent;
    private int binMaxDeltas;
    private int maxMainTreeEntriesPerNode;
    private int maxDupTreeEntriesPerNode;

    /* 
     * The debugDatabaseName is used for error messages only, to avoid
     * accessing the db mapping tree in error situations. Currently it's not
     * guaranteed to be transactionally correct, nor is it updated by rename.
     */
    private String debugDatabaseName;

    /* For unit tests */
    private TestHook pendingDeletedHook;

    /**
     * Create a database object for a new database.
     */
    public DatabaseImpl(String dbName,
			DatabaseId id,
			EnvironmentImpl envImpl,
			DatabaseConfig dbConfig)
        throws DatabaseException {

        this.id = id;
        this.envImpl = envImpl;
        setBtreeComparator(dbConfig.getBtreeComparator(),
                           dbConfig.getBtreeComparatorByClassName());
        setDuplicateComparator(dbConfig.getDuplicateComparator(),
                               dbConfig.getDuplicateComparatorByClassName());
        duplicatesAllowed = dbConfig.getSortedDuplicates();
        transactional = dbConfig.getTransactional();
        deferredWrite = dbConfig.getDeferredWrite();
	maxMainTreeEntriesPerNode = dbConfig.getNodeMaxEntries();
	maxDupTreeEntriesPerNode = dbConfig.getNodeMaxDupTreeEntries();

        initDefaultSettings();

        deleteState = NOT_DELETED;

        /*
         * The tree needs the env, make sure we assign it before
         * allocating the tree.
         */
        tree = new Tree(this); 
        referringHandles = Collections.synchronizedSet(new HashSet());

        eofNodeId = Node.getNextNodeId();

        /* For error messages only. */
        debugDatabaseName = dbName;
    }

    /**
     * Create an empty database object for initialization from the log.  Note
     * that the rest of the initialization comes from readFromLog(), except
     * for the debugDatabaseName, which is set by the caller.
     */ 
    public DatabaseImpl()
        throws DatabaseException {

        id = new DatabaseId();
        envImpl = null;

        deleteState = NOT_DELETED;

        tree = new Tree();
        referringHandles = Collections.synchronizedSet(new HashSet());

        /* initDefaultSettings is called after envImpl is set.  */

        eofNodeId = Node.getNextNodeId();
    }

    public void setDebugDatabaseName(String debugName) {
        debugDatabaseName = debugName;
    }

    public String getDebugName() {
        return debugDatabaseName;
    }

    /* For unit testing only. */
    public void setPendingDeletedHook(TestHook hook) {
        pendingDeletedHook = hook;
    }

    /**
     * Initialize configuration settings when creating a new instance or after
     * reading an instance from the log.  The envImpl field must be set before
     * calling this method.
     */
    private void initDefaultSettings()
        throws DatabaseException {

        DbConfigManager configMgr = envImpl.getConfigManager();

        binDeltaPercent =
            configMgr.getInt(EnvironmentParams.BIN_DELTA_PERCENT);
        binMaxDeltas =
            configMgr.getInt(EnvironmentParams.BIN_MAX_DELTAS);

	if (maxMainTreeEntriesPerNode == 0) {
            maxMainTreeEntriesPerNode =
		configMgr.getInt(EnvironmentParams.NODE_MAX);
	}

	if (maxDupTreeEntriesPerNode == 0) {
            maxDupTreeEntriesPerNode =
		configMgr.getInt(EnvironmentParams.NODE_MAX_DUPTREE);
	}
    }

    /**
     * Clone.  For the most part, just pass off to the super class for a
     * field-by-field copy.
     */
    public DatabaseImpl cloneDb()
        throws DatabaseException {

        try {
            DatabaseImpl newDb = (DatabaseImpl) clone();
            /* The cloned DB could have a non-zero use count. [#13415] */
            newDb.useCount = 0;
            return newDb;
	} catch (CloneNotSupportedException e) {
	    throw new DatabaseException(e);
        }
    }

    /**
     * @return the database tree.
     */
    public Tree getTree() {
        return tree;
    }

    void setTree(Tree tree) {
        this.tree = tree;
    }

    /**
     * @return the database id.
     */
    public DatabaseId getId() {
        return id;
    }

    void setId(DatabaseId id) {
        this.id = id;
    }

    public long getEofNodeId() {
        return eofNodeId;
    }

    /**
     * @return true if this database is transactional.
     */
    public boolean isTransactional() {
        return transactional;
    }

    /**
     * Sets the transactional property for the first opened handle.
     */
    public void setTransactional(boolean transactional) {
        this.transactional = transactional;
    }

    /**
     * @return true if this database is in deferred write mode.
     */
    public boolean isDeferredWrite() {
        return deferredWrite;
    }

    /*
     * Set the deferred write property for the first opened handle.
     */
    public void setDeferredWrite(boolean deferredWrite) {
        this.deferredWrite = deferredWrite;
    }

    /**
     * @return true if duplicates are allowed in this database.
     */
    public boolean getSortedDuplicates() {
        return duplicatesAllowed;
    }

    public int getNodeMaxEntries() {
	return maxMainTreeEntriesPerNode;
    }

    public int getNodeMaxDupTreeEntries() {
	return maxDupTreeEntriesPerNode;
    }

    /**
     * Returns the memory size that should be added to MAPLN_OVERHEAD.
     *
     * This is a start at budgeting per-Database memory.  For future reference,
     * other things that could be budgeted are:
     * - debugDatabaseName as it is set
     * - Database handles as they are added/removed in referringHandles
     */
    public int getAdditionalMemorySize() {

        int val = 0;
        
        /*
         * If the comparator object is non-null we double the size of the
         * serialized form to account for the approximate size of the user's
         * comparator object.  This is only an approximation of course, and is
         * not a very good one if we have serialized the class name, but we
         * have no way to know the size of the user's object.
         */
        if (btreeComparator != null) {
            val += 2 * MemoryBudget.byteArraySize
                (btreeComparatorBytes.length);
        }
        if (duplicateComparator != null) {
            val += 2 * MemoryBudget.byteArraySize
                (duplicateComparatorBytes.length);
        }
        return val;
    }

    /**
     * Set the duplicate comparison function for this database.
     *
     * @param duplicateComparator - The Duplicate Comparison function.
     */
    public void setDuplicateComparator(Comparator comparator,
                                       boolean byClassName)
        throws DatabaseException {

        duplicateComparator = comparator;
        duplicateComparatorBytes =
            comparatorToBytes(comparator, byClassName, "Duplicate");
        duplicateComparatorByClassName = byClassName;
    }

    /**
     * Set the btree comparison function for this database.
     *
     * @param btreeComparator - The btree Comparison function.
     */
    public void setBtreeComparator(Comparator comparator,
                                   boolean byClassName)
        throws DatabaseException {

        btreeComparator = comparator;
        btreeComparatorBytes =
            comparatorToBytes(comparator, byClassName, "Btree");
        btreeComparatorByClassName = byClassName;
    }

    /**
     * @return the btree Comparator object.
     */
    public Comparator getBtreeComparator() {
        return btreeComparator;
    }

    /**
     * @return the duplicate Comparator object.
     */
    public Comparator getDuplicateComparator() {
        return duplicateComparator;
    }

    /**
     * @return whether Comparator is set by class name, not by serializable
     * Comparator object.
     */
    public boolean getBtreeComparatorByClass() {
        return btreeComparatorByClassName;
    }

    /**
     * @return whether Comparator is set by class name, not by serializable
     * Comparator object.
     */
    public boolean getDuplicateComparatorByClass() {
        return duplicateComparatorByClassName;
    }

    /**
     * Set the db environment during recovery, after instantiating the database
     * from the log
     */
    public void setEnvironmentImpl(EnvironmentImpl envImpl)
        throws DatabaseException {

        this.envImpl = envImpl;
        initDefaultSettings();
        tree.setDatabase(this);
    }

    /**
     * @return the database environment.
     */
    public EnvironmentImpl getDbEnvironment() {
        return envImpl;
    }

    /**
     * Returns whether one or more handles are open.
     */
    public boolean hasOpenHandles() {
        return referringHandles.size() > 0;
    }

    /**
     * Add a referring handle
     */
    public void addReferringHandle(Database db) {
        referringHandles.add(db);
    }

    /**
     * Decrement the reference count.
     */
    public void removeReferringHandle(Database db) {
        referringHandles.remove(db);
    }

    /**
     * @return the referring handle count.
     */
    synchronized int getReferringHandleCount() {
        return referringHandles.size();
    }

    /**
     * Increments the use count of this DB to prevent it from being
     * evicted.  Called by the DbTree.createDb/getDb methods that return a
     * DatabaseImpl.  Must be called while holding a lock on the MapLN. See
     * isInUse. [#13415]
     */
    void incrementUseCount() {
        if (envImpl.getDbEviction()) {
            /* Synchronize to update useCount atomically. */
            synchronized (this) {
                useCount += 1;
            }
        }
    }

    /**
     * Decrements the use count of this DB, allowing it to be evicted if the
     * use count reaches zero.  Called via DbTree.releaseDb to release a
     * DatabaseImpl that was returned by a DbTree.createDb/getDb method. See
     * isInUse. [#13415]
     */
    void decrementUseCount() {
        if (envImpl.getDbEviction()) {
            /* Synchronize to update useCount atomically. */
            synchronized (this) {
                assert useCount > 0;
                useCount -= 1;
            }
        }
    }

    /**
     * Returns whether this DB is in use and cannot be evicted.  Called by
     * MapLN.isEvictable while holding a write-lock on the MapLN and a latch on
     * its parent BIN. [#13415]
     *
     * When isInUse returns false (while holding a write-lock on the MapLN and
     * a latch on the parent BIN), it guarantees that the database object
     * is not in use and cannot be acquired by another thread (via
     * DbTree.createDb/getDb) until both the MapLN lock and BIN latch are
     * released.  This guarantee is due to the fact that DbTree.createDb/getDb
     * only increment the use count while holding a read-lock on the MapLN.
     * Therefore, it is safe to evict the MapLN when isInUse returns false.
     *
     * When isInUse returns true, it is possible that another thread may
     * decrement the use count at any time, since no locking or latching is
     * performed when calling DbTree.releaseDb (which calls decrementUseCount).
     * Therefore, it is not guaranteed that the MapLN is in use when isInUse
     * returns true.  A true result means: the DB may be in use, so it is not
     * safe to evict it.
     */
    public boolean isInUse() {
        if (envImpl.getDbEviction()) {
            /* Synchronize to read the up-to-date value of useCount. */
            synchronized (this) {
                return (useCount > 0);
            }
        } else {
            /* Always prohibit eviction when je.env.dbEviction=false. */
            return true;
        }
    }

    /**
     * Flush all dirty nodes for this database to disk.
     */
    public synchronized void sync(boolean flushLog) 
        throws DatabaseException {

        if (!isDeferredWrite()) {
            throw new DatabaseException("Database.sync() is only supported " +
                                        "for deferred-write databases");
        }

	if (tree.rootExists()) {
	    Checkpointer.syncDatabase(envImpl, this, flushLog);
	}
    }

    /**
     * For this secondary database return the primary that it is associated
     * with, or null if not associated with any primary.  Note that not all
     * handles need be associated with a primary.
     */
    public Database findPrimaryDatabase()
        throws DatabaseException {

        for (Iterator i = referringHandles.iterator(); i.hasNext();) {
            Object obj = i.next();
            if (obj instanceof SecondaryDatabase) {
                return ((SecondaryDatabase) obj).getPrimaryDatabase();
            }
        }
        return null;
    }

    public String getName() 
        throws DatabaseException {

        return envImpl.getDbMapTree().getDbName(id);
    }

    /*
     * @return true if this database is deleted. Delete cleanup
     * may still be in progress.
     */
    public boolean isDeleted() {
        return !(deleteState == NOT_DELETED);
    }

    /*
     * @return true if this database is deleted and all cleanup is finished.
     */
    public boolean isDeleteFinished() {
        return (deleteState == DELETED);
    }

    /* 
     * The delete cleanup is starting. Set this before releasing any
     * write locks held for a db operation.
     */
    public void startDeleteProcessing() {
        assert (deleteState == NOT_DELETED);

        deleteState = DELETED_CLEANUP_INLIST_HARVEST;
    }

    /* 
     * Should be called by the SortedLSNTreeWalker when it is finished with
     * the INList.
     */
    void finishedINListHarvest() {
        assert (deleteState == DELETED_CLEANUP_INLIST_HARVEST);

        deleteState = DELETED_CLEANUP_LOG_HARVEST;
    }

    /**
     * Purge a DatabaseImpl and corresponding MapLN in the db mapping tree.
     * Purging consists of removing all related INs from the db mapping tree
     * and deleting the related MapLN.
     * Used at the transaction end in these cases:
     *  - purge the deleted database after a commit of 
     *           Environment.removeDatabase
     *  - purge the deleted database after a commit of 
     *           Environment.truncateDatabase
     *  - purge the newly created database after an abort of 
     *           Environment.truncateDatabase
     */
    public void deleteAndReleaseINs() 
        throws DatabaseException {
        
        startDeleteProcessing();
        releaseDeletedINs();
    }

    public void releaseDeletedINs() 
        throws DatabaseException {

        if (pendingDeletedHook != null) {
            pendingDeletedHook.doHook();
        }

        try {

            /*
             * Get the root LSN before deleting the MapLN, as that will null
             * out the root.
             */
            long rootLsn = tree.getRootLsn();


            /*
             * Use a snapshot tracker that is accumulated under the log write
             * latch when we're doing counting.  Start by recording the LSN of
             * the root IN as obsolete.  A zero size is passed for the last
             * parameter because it is too expensive to fetch the node.
             */
            UtilizationTracker snapshot = new UtilizationTracker(envImpl);
            if (rootLsn != DbLsn.NULL_LSN) {
                snapshot.countObsoleteNodeInexact
                    (rootLsn, LogEntryType.LOG_IN, 0);
            }

            /* Fetch LNs to count LN sizes only if so configured. */
            boolean fetchLNSize =
                envImpl.getCleaner().getFetchObsoleteSize();

            /* Use the tree walker to visit every child lsn in the tree. */
            ObsoleteProcessor obsoleteProcessor =
                new ObsoleteProcessor(snapshot);
            SortedLSNTreeWalker walker = new ObsoleteTreeWalker
                (this, rootLsn, fetchLNSize, obsoleteProcessor);

            /* 
             * Delete MapLN before the walk. Note that the processing of
             * the naming tree means this MapLN is never actually
             * accessible from the current tree, but deleting the MapLN
             * will do two things:
             * (a) mark it properly obsolete 
             * (b) null out the database tree, leaving the INList the only
             * reference to the INs.
             */
            envImpl.getDbMapTree().deleteMapLN(id);

            /* 
             * At this point, it's possible for the evictor to find an IN
             * for this database on the INList. It should be ignored.
             */
            walker.walk();

            /*
             * Count obsolete nodes for a deleted database at transaction
             * end time.  Write out the modified file summaries for
             * recovery.
             */
            envImpl.getUtilizationProfile().countAndLogSummaries
                (snapshot.getTrackedFiles());
        } finally {
            deleteState = DELETED;
            /* releaseDb to balance getDb called by truncate/remove. */
            envImpl.releaseDb(this);
        }
    }

    public void checkIsDeleted(String operation)
        throws DatabaseException {

        if (isDeleted()) {
            throw new DatabaseException
                ("Attempt to " + operation + " a deleted database");
        }
    }

    private static class ObsoleteTreeWalker extends SortedLSNTreeWalker {

        private ObsoleteTreeWalker(DatabaseImpl dbImpl,
                                   long rootLsn,
                                   boolean fetchLNSize,
                                   TreeNodeProcessor callback)
            throws DatabaseException {

            super(dbImpl, 
                  true,  // remove INs from INList
                  true,  // set INList finish harvest
                  rootLsn,
                  callback,
                  null,  /* savedException */
                  null); /* exception predicate */

	    accumulateLNs = fetchLNSize;
        }
    }

    /* Mark each LSN obsolete in the utilization tracker. */
    private static class ObsoleteProcessor implements TreeNodeProcessor {

        private UtilizationTracker tracker;

        ObsoleteProcessor(UtilizationTracker tracker) {
            this.tracker = tracker;
        }

        public void processLSN(long childLsn,
			       LogEntryType childType,
			       Node node,
                               byte[] lnKey)
	    throws DatabaseException {

            assert childLsn != DbLsn.NULL_LSN;
            
            /*
             * Count the LN log size if an LN node and key are available.  But
             * do not count the size if the LN is dirty, since the logged LN is
             * not available. [#15365]
             */
            int size = 0;
            if (lnKey != null && node instanceof LN) {
                LN ln = (LN) node;
                size = ln.getLastLoggedSize();
            }

            tracker.countObsoleteNodeInexact(childLsn, childType, size);
        }

        public void processDirtyDeletedLN(long childLsn, LN ln, byte[] lnKey)
	    throws DatabaseException {

            assert ln != null;

            /*
             * Do not count the size (pass zero) because the LN is dirty and
             * the logged LN is not available.
             */
            tracker.countObsoleteNodeInexact(childLsn, ln.getLogType(), 0);
        }

	public void processDupCount(long ignore) {
	}
    }

    public DatabaseStats stat(StatsConfig config) 
        throws DatabaseException {

        if (stats == null) {

            /* 
             * Called first time w/ FAST_STATS so just give them an
             * empty one.
             */
            stats = new BtreeStats();
        }

        if (!config.getFast()) {
            if (tree == null) {
                return new BtreeStats();
            }

            PrintStream out = config.getShowProgressStream();
            if (out == null) {
                out = System.err;
            }

	    StatsAccumulator statsAcc =
		new StatsAccumulator(out,
				     config.getShowProgressInterval(),
                                     getEmptyStats());
	    walkDatabaseTree(statsAcc, out, true);
            statsAcc.copyToStats(stats);
        }

        return stats;
    }

    /*
     * @param config verify configuration
     * @param emptyStats empty database stats, to be filled by this method
     * @return true if the verify saw no errors.
     */
    public boolean verify(VerifyConfig config, DatabaseStats emptyStats)
        throws DatabaseException {

	if (tree == null) {
	    return true;
	}

	PrintStream out = config.getShowProgressStream();
	if (out == null) {
	    out = System.err;
	}

	StatsAccumulator statsAcc =
	    new StatsAccumulator(out,
                                 config.getShowProgressInterval(),
                                 emptyStats) {
		    void verifyNode(Node node) {

			try {
			    node.verify(null);
			} catch (DatabaseException INE) {
			    progressStream.println(INE);
			}
		    }
		};
	boolean ok = walkDatabaseTree(statsAcc, out, config.getPrintInfo());
	statsAcc.copyToStats(emptyStats);
        return ok;
    }

    /* @return the right kind of stats object for this database. */
    public DatabaseStats getEmptyStats() {
        return new BtreeStats();
    }

    /* 
     * @return true if no errors.
     */
    private boolean walkDatabaseTree(TreeWalkerStatsAccumulator statsAcc,
                                     PrintStream out,
                                     boolean verbose)
        throws DatabaseException {

        boolean ok = true;
        Locker locker = new ThreadLocker(envImpl);
        Cursor cursor = null;
	CursorImpl impl = null;
        try {
	    EnvironmentImpl.incThreadLocalReferenceCount();
            cursor = DbInternal.newCursor(this, locker, null);
	    impl = DbInternal.getCursorImpl(cursor);
	    tree.setTreeStatsAccumulator(statsAcc);

	    /* 
	     * This will only be used on the first call for the position()
	     * call.
	     */
	    impl.setTreeStatsAccumulator(statsAcc);
            DatabaseEntry foundData = new DatabaseEntry();
            DatabaseEntry key = new DatabaseEntry();
            OperationStatus status = DbInternal.position
                (cursor, key, foundData, LockMode.READ_UNCOMMITTED, true);
            while (status == OperationStatus.SUCCESS) {
		try {
		    status = DbInternal.retrieveNext
			(cursor, key, foundData, LockMode.READ_UNCOMMITTED,
			 GetMode.NEXT);
		} catch (DatabaseException DBE) {
                    ok = false;
		    if (DbInternal.advanceCursor(cursor, key, foundData)) {
                        if (verbose) {
                            out.println("Error encountered (continuing):");
                            out.println(DBE);
                            printErrorRecord(out, key, foundData);
                        }
                    } else {
                        throw DBE;
                    }
		}
            }
        } finally {
	    if (impl != null) {
		impl.setTreeStatsAccumulator(null);
	    }
	    tree.setTreeStatsAccumulator(null);
	    EnvironmentImpl.decThreadLocalReferenceCount();
            if (cursor != null) {
                cursor.close();
            }
        }

        return ok;
    }

    /**
     * Prints the key and data, if available, for a BIN entry that could not be
     * read/verified.  Uses the same format as DbDump and prints both the hex
     * and printable versions of the entries.
     */
    private void printErrorRecord(PrintStream out,
                                  DatabaseEntry key,
                                  DatabaseEntry data) {

        byte[] bytes = key.getData();
        StringBuffer sb = new StringBuffer("Error Key ");
        if (bytes == null) {
            sb.append("UNKNOWN");
        } else {
            CmdUtil.formatEntry(sb, bytes, false);
            sb.append(' ');
            CmdUtil.formatEntry(sb, bytes, true);
        }
        out.println(sb);

        bytes = data.getData();
        sb = new StringBuffer("Error Data ");
        if (bytes == null) {
            sb.append("UNKNOWN");
        } else {
            CmdUtil.formatEntry(sb, bytes, false);
            sb.append(' ');
            CmdUtil.formatEntry(sb, bytes, true);
        }
        out.println(sb);
    }

    static class StatsAccumulator implements TreeWalkerStatsAccumulator {
	private Set inNodeIdsSeen = new HashSet();
	private Set binNodeIdsSeen = new HashSet();
	private Set dinNodeIdsSeen = new HashSet();
	private Set dbinNodeIdsSeen = new HashSet();
	private Set dupCountLNsSeen = new HashSet();
	private long[] insSeenByLevel = null;
	private long[] binsSeenByLevel = null;
	private long[] dinsSeenByLevel = null;
	private long[] dbinsSeenByLevel = null;
	private long lnCount = 0;
	private long deletedLNCount = 0;
	private int mainTreeMaxDepth = 0;
	private int duplicateTreeMaxDepth = 0;
	private DatabaseStats useStats;

	PrintStream progressStream;
	int progressInterval;

	/* The max levels we ever expect to see in a tree. */
	private static final int MAX_LEVELS = 100;

	StatsAccumulator(PrintStream progressStream,
			 int progressInterval,
                         DatabaseStats useStats) {

	    this.progressStream = progressStream;
	    this.progressInterval = progressInterval;

	    insSeenByLevel = new long[MAX_LEVELS];
	    binsSeenByLevel = new long[MAX_LEVELS];
	    dinsSeenByLevel = new long[MAX_LEVELS];
	    dbinsSeenByLevel = new long[MAX_LEVELS];
	    
	    this.useStats = useStats;
	}

	void verifyNode(Node node) {

	}

	public void processIN(IN node, Long nid, int level) {
	    if (inNodeIdsSeen.add(nid)) {
		tallyLevel(level, insSeenByLevel);
		verifyNode(node);
	    }
	}

	public void processBIN(BIN node, Long nid, int level) {
	    if (binNodeIdsSeen.add(nid)) {
		tallyLevel(level, binsSeenByLevel);
		verifyNode(node);
	    }
	}

	public void processDIN(DIN node, Long nid, int level) {
	    if (dinNodeIdsSeen.add(nid)) {
		tallyLevel(level, dinsSeenByLevel);
		verifyNode(node);
	    }
	}

	public void processDBIN(DBIN node, Long nid, int level) {
	    if (dbinNodeIdsSeen.add(nid)) {
		tallyLevel(level, dbinsSeenByLevel);
		verifyNode(node);
	    }
	}

	public void processDupCountLN(DupCountLN node, Long nid) {
	    dupCountLNsSeen.add(nid);
	    verifyNode(node);
	}

	private void tallyLevel(int levelArg, long[] nodesSeenByLevel) {
	    int level = levelArg;
	    if (level >= IN.DBMAP_LEVEL) {
		return;
	    }
	    if (level >= IN.MAIN_LEVEL) {
		level &= ~IN.MAIN_LEVEL;
		if (level > mainTreeMaxDepth) {
		    mainTreeMaxDepth = level;
		}
	    } else {
		if (level > duplicateTreeMaxDepth) {
		    duplicateTreeMaxDepth = level;
		}
	    }

	    nodesSeenByLevel[level]++;
	}

	public void incrementLNCount() {
	    lnCount++;
	    if (progressInterval != 0) {
		if ((lnCount % progressInterval) == 0) {
                    copyToStats(useStats);
		    progressStream.println(useStats);
		}
	    }
	}

	public void incrementDeletedLNCount() {
	    deletedLNCount++;
	}

	Set getINNodeIdsSeen() {
	    return inNodeIdsSeen;
	}

	Set getBINNodeIdsSeen() {
	    return binNodeIdsSeen;
	}

	Set getDINNodeIdsSeen() {
	    return dinNodeIdsSeen;
	}

	Set getDBINNodeIdsSeen() {
	    return dbinNodeIdsSeen;
	}

	long[] getINsByLevel() {
	    return insSeenByLevel;
	}

	long[] getBINsByLevel() {
	    return binsSeenByLevel;
	}

	long[] getDINsByLevel() {
	    return dinsSeenByLevel;
	}

	long[] getDBINsByLevel() {
	    return dbinsSeenByLevel;
	}

	long getLNCount() {
	    return lnCount;
	}

	Set getDupCountLNCount() {
	    return dupCountLNsSeen;
	}

	long getDeletedLNCount() {
	    return deletedLNCount;
	}

	int getMainTreeMaxDepth() {
	    return mainTreeMaxDepth;
	}

	int getDuplicateTreeMaxDepth() {
	    return duplicateTreeMaxDepth;
	}

	private void copyToStats(DatabaseStats stats) {
            BtreeStats bStats = (BtreeStats) stats;
	    bStats.setInternalNodeCount(getINNodeIdsSeen().size());
	    bStats.setBottomInternalNodeCount
		(getBINNodeIdsSeen().size());
	    bStats.setDuplicateInternalNodeCount
		(getDINNodeIdsSeen().size());
	    bStats.setDuplicateBottomInternalNodeCount
		(getDBINNodeIdsSeen().size());
	    bStats.setLeafNodeCount(getLNCount());
	    bStats.setDeletedLeafNodeCount(getDeletedLNCount());
	    bStats.setDupCountLeafNodeCount
		(getDupCountLNCount().size());
	    bStats.setMainTreeMaxDepth(getMainTreeMaxDepth());
	    bStats.setDuplicateTreeMaxDepth(getDuplicateTreeMaxDepth());
	    bStats.setINsByLevel(getINsByLevel());
	    bStats.setBINsByLevel(getBINsByLevel());
	    bStats.setDINsByLevel(getDINsByLevel());
	    bStats.setDBINsByLevel(getDBINsByLevel());
	}
    }

    /**
     * Preload exceptions, classes, callbacks.
     */

    /**
     * Undeclared exception used to throw through SortedLSNTreeWalker code
     * when preload has either filled the user's max byte or time request.
     */
    private static class HaltPreloadException extends RuntimeException {

	private PreloadStatus status;

	HaltPreloadException(PreloadStatus status) {
	    super(status.toString());
	    this.status = status;
	}

	PreloadStatus getStatus() {
	    return status;
	}
    }

    private static final HaltPreloadException
	TIME_EXCEEDED_PRELOAD_EXCEPTION =
	new HaltPreloadException(PreloadStatus.EXCEEDED_TIME);

    private static final HaltPreloadException
	MEMORY_EXCEEDED_PRELOAD_EXCEPTION =
	new HaltPreloadException(PreloadStatus.FILLED_CACHE);

    /**
     * The processLSN() code for PreloadLSNTreeWalker.
     */
    private static class PreloadProcessor implements TreeNodeProcessor {

	private EnvironmentImpl envImpl;
	private long maxBytes;
	private long targetTime;
	private PreloadStats stats;

	PreloadProcessor(EnvironmentImpl envImpl,
			 long maxBytes,
			 long targetTime,
			 PreloadStats stats) {
	    this.envImpl = envImpl;
	    this.maxBytes = maxBytes;
	    this.targetTime = targetTime;
	    this.stats = stats;
	}

	/**
	 * Called for each LSN that the SortedLSNTreeWalker encounters.
	 */
        public void processLSN(long childLsn,
			       LogEntryType childType,
			       Node ignore,
			       byte[] ignore2)
	    throws DatabaseException {

            assert childLsn != DbLsn.NULL_LSN;

	    /*
	     * Check if we've exceeded either the max time or max bytes
	     * allowed for this preload() call.
	     */
	    if (System.currentTimeMillis() > targetTime) {
		throw TIME_EXCEEDED_PRELOAD_EXCEPTION;
	    }

	    if (envImpl.getMemoryBudget().getCacheMemoryUsage() > maxBytes) {
		throw MEMORY_EXCEEDED_PRELOAD_EXCEPTION;
	    }

	    /* Count entry types to return in the PreloadStats. */
	    if (childType.equals(LogEntryType.LOG_DUPCOUNTLN_TRANSACTIONAL) ||
		childType.equals(LogEntryType.LOG_DUPCOUNTLN)) {
		stats.nDupCountLNsLoaded++;
	    } else if (childType.equals(LogEntryType.LOG_LN_TRANSACTIONAL) ||
		       childType.equals(LogEntryType.LOG_LN)) {
		stats.nLNsLoaded++;
            } else if (childType.equals(LogEntryType.LOG_DBIN)) {
		stats.nDBINsLoaded++;
	    } else if (childType.equals(LogEntryType.LOG_BIN)) {
		stats.nBINsLoaded++;
	    } else if (childType.equals(LogEntryType.LOG_DIN)) {
		stats.nDINsLoaded++;
	    } else if (childType.equals(LogEntryType.LOG_IN)) {
		stats.nINsLoaded++;
	    }
        }

        public void processDirtyDeletedLN(long childLsn, LN ln, byte[] lnKey)
	    throws DatabaseException {
        }

	public void processDupCount(long ignore) {
	}
    }

    /*
     * An extension of SortedLSNTreeWalker that provides an LSN to IN/index
     * map.  When an LSN is processed by the tree walker, the map is used to
     * lookup the parent IN and child entry index of each LSN processed by the
     * tree walker.
     */
    private static class PreloadLSNTreeWalker extends SortedLSNTreeWalker {

	/* LSN -> INEntry */
	private Map lsnINMap = new HashMap();

	/* struct to hold IN/entry-index pair. */
	private static class INEntry {
	    INEntry(IN in, int index) {
		this.in = in;
		this.index = index;
	    }

	    IN in;
	    int index;
	}

	PreloadLSNTreeWalker(DatabaseImpl db,
			     TreeNodeProcessor callback,
			     PreloadConfig conf)
	    throws DatabaseException {

	    super(db, false, false, db.tree.getRootLsn(), callback,
		  null, null); /* savedException, exception predicate */
	    accumulateLNs = conf.getLoadLNs();
	}

	private final class PreloadWithRootLatched
	    implements WithRootLatched {

	    public IN doWork(ChildReference root)
		throws DatabaseException {

		walkInternal();
		return null;
	    }
	}

	public void walk()
	    throws DatabaseException {

	    WithRootLatched preloadWRL = new PreloadWithRootLatched();
	    dbImpl.getTree().withRootLatchedExclusive(preloadWRL);
	}

	/* 
	 * Method to get the Root IN for this DatabaseImpl's tree.  Latches
	 * the root IN.
	 */
	protected IN getRootIN(long rootLsn)
	    throws DatabaseException {

	    return dbImpl.getTree().getRootIN(false);
	}

	/*
	 * Release the latch on the root IN.
	 */
	protected void releaseRootIN(IN root)
	    throws DatabaseException {

	    root.releaseLatch();
	}

	/*
	 * Add an LSN -> IN/index entry to the map.
	 */
	protected void addToLsnINMap(Long lsn, IN in, int index) {
	    assert in.getDatabase() != null;
	    lsnINMap.put(lsn, new INEntry(in, index));
	}

	/*
	 * Process an LSN.  Get & remove its INEntry from the map, then fetch
	 * the target at the INEntry's IN/index pair.  This method will be
	 * called in sorted LSN order.
         *
         * We do not bother to set the lnkeyEntry because we never use the
         * lnKey parameter in the processLSN method.
	 */
	protected Node fetchLSN(long lsn, DatabaseEntry lnKeyEntry)
	    throws DatabaseException {

	    INEntry inEntry = (INEntry) lsnINMap.remove(new Long(lsn));
	    assert (inEntry != null);
	    IN in = inEntry.in;
	    in.latch();
	    try {
		int index = inEntry.index;
                if (index < 0) {
                    /* Negative index signifies a DupCountLN. */
                    DIN din = (DIN) in;
                    return din.getDupCountLN();
                } else {
                    if (in.isEntryKnownDeleted(index) ||
                        in.getLsn(index) != lsn) {
                        return null;
                    }
                }
		return in.fetchTarget(index);
	    } finally {
		in.releaseLatch();
	    }
	}
    }

    /**
     * Preload the cache, using up to maxBytes bytes or maxMillsecs msec.
     */
    public PreloadStats preload(PreloadConfig config)
	throws DatabaseException {

	try {
	    long maxBytes = config.getMaxBytes();
	    long maxMillisecs = config.getMaxMillisecs();
	    long targetTime = Long.MAX_VALUE;
	    if (maxMillisecs > 0) {
		targetTime = System.currentTimeMillis() + maxMillisecs;
	    }

	    long cacheBudget = envImpl.getMemoryBudget().getCacheBudget();
	    if (maxBytes == 0) {
		maxBytes = cacheBudget;
	    } else if (maxBytes > cacheBudget) {
		throw new IllegalArgumentException
		    ("maxBytes parameter to Database.preload() was " +
		     "specified as " +
		     maxBytes + " bytes \nbut the cache is only " +
		     cacheBudget + " bytes.");
	    }

	    PreloadStats ret = new PreloadStats();
	    PreloadProcessor callback =
		new PreloadProcessor(envImpl, maxBytes, targetTime, ret);
	    SortedLSNTreeWalker walker =
		new PreloadLSNTreeWalker(this, callback, config);
	    try {
		walker.walk();
	    } catch (HaltPreloadException HPE) {
		ret.status = HPE.getStatus();
	    }

	    assert LatchSupport.countLatchesHeld() == 0;
	    return ret;
	} catch (Error E) {
	    envImpl.invalidate(E);
	    throw E;
	}
    }

    /**
     * The processLSN() code for PreloadLSNTreeWalker.
     */
    private static class CountProcessor implements TreeNodeProcessor {

	private EnvironmentImpl envImpl;
	/* Use PreloadStats in case we ever want to count more than LNs. */
	private PreloadStats stats;

	CountProcessor(EnvironmentImpl envImpl,
		       PreloadStats stats) {
	    this.envImpl = envImpl;
	    this.stats = stats;
	}

	/**
	 * Called for each LSN that the SortedLSNTreeWalker encounters.
	 */
        public void processLSN(long childLsn,
			       LogEntryType childType,
			       Node ignore,
                               byte[] ignore2)
	    throws DatabaseException {

	    /* Count entry types to return in the PreloadStats. */
	    if (childType.equals(LogEntryType.LOG_DUPCOUNTLN_TRANSACTIONAL) ||
		childType.equals(LogEntryType.LOG_DUPCOUNTLN)) {
		/* Don't descend down into the dup tree -- just use the DCL. */
		long dupCount = 0;
		DupCountLN dcl = (DupCountLN)
		    envImpl.getLogManager().get(childLsn);
		dupCount = dcl.getDupCount();
		stats.nLNsLoaded += dupCount;
	    } else if (childType.equals(LogEntryType.LOG_LN_TRANSACTIONAL) ||
		       childType.equals(LogEntryType.LOG_LN)) {
		stats.nLNsLoaded++;
	    }
        }

        public void processDirtyDeletedLN(long childLsn, LN ln, byte[] lnKey)
	    throws DatabaseException {
        }

	/* Used when processing Deferred Write dbs and there are no LSNs. */
	public void processDupCount(long count) {
	    stats.nLNsLoaded += count;
	}    
    }

    private static class CountExceptionPredicate
	implements ExceptionPredicate {

	/* 
	 * Return true if the exception can be ignored.
	 * LogFileNotFoundException is the only one so far.
	 */
	public boolean ignoreException(Exception e) {
	    if (e instanceof LogFileNotFoundException) {
		return true;
	    }
	    return false;
	}
    }

    /**
     * Count entries in the database including dups, but don't dirty the cache.
     */
    public long count()
	throws DatabaseException {

	try {
	    PreloadStats ret = new PreloadStats();

	    CountProcessor callback = new CountProcessor(envImpl, ret);
	    ExceptionPredicate excPredicate = new CountExceptionPredicate();
	    SortedLSNTreeWalker walker =
		new SortedLSNTreeWalker(this, false, false,
					tree.getRootLsn(), callback, null,
					excPredicate);
	    /* Don't descend down into the dup tree. Use the DupCountLN. */
	    walker.setProcessDupTree(false);
	    if (deferredWrite) {
		walker.setPassNullLSNNodes(true);
	    }
	    walker.walk();

	    assert LatchSupport.countLatchesHeld() == 0;
	    return ret.nLNsLoaded;
	} catch (Error E) {
	    envImpl.invalidate(E);
	    throw E;
	}
    }

    /*
     * Dumping
     */
    public String dumpString(int nSpaces) {
        StringBuffer sb = new StringBuffer();
        sb.append(TreeUtils.indent(nSpaces));
        sb.append("<database id=\"" );
        sb.append(id.toString());
        sb.append("\"");
        if (btreeComparator != null) {
            sb.append(" btc=\"");
            sb.append(getComparatorClassName(btreeComparator));
            sb.append("\"");
        }
        if (duplicateComparator != null) {
            sb.append(" dupc=\"");
            sb.append(getComparatorClassName(duplicateComparator));
            sb.append("\"");
        }
        sb.append("/>");
        return sb.toString();
    }

    /*
     * Logging support
     */

    /**
     * @see Loggable#getLogSize
     */
    public int getLogSize() {
        return 
            id.getLogSize() +
            tree.getLogSize() +
            LogUtils.getBooleanLogSize() +
            LogUtils.getByteArrayLogSize(btreeComparatorBytes) +
            LogUtils.getByteArrayLogSize(duplicateComparatorBytes) +
	    (LogUtils.getIntLogSize() * 2);
    }

    /**
     * @see Loggable#writeToLog
     */
    public void writeToLog(ByteBuffer logBuffer) {
        id.writeToLog(logBuffer);
        tree.writeToLog(logBuffer);
        LogUtils.writeBoolean(logBuffer, duplicatesAllowed);
        LogUtils.writeByteArray(logBuffer, btreeComparatorBytes);
        LogUtils.writeByteArray(logBuffer, duplicateComparatorBytes);
	LogUtils.writeInt(logBuffer, maxMainTreeEntriesPerNode);
	LogUtils.writeInt(logBuffer, maxDupTreeEntriesPerNode);
    }

    /**
     * @see Loggable#readFromLog
     */
    public void readFromLog(ByteBuffer itemBuffer, byte entryTypeVersion)
        throws LogException {

        id.readFromLog(itemBuffer, entryTypeVersion);
        tree.readFromLog(itemBuffer, entryTypeVersion);
        duplicatesAllowed = LogUtils.readBoolean(itemBuffer);

	if (entryTypeVersion >= 2) {
            btreeComparatorBytes = LogUtils.readByteArray(itemBuffer);
            duplicateComparatorBytes = LogUtils.readByteArray(itemBuffer);
        } else {
            String btreeClassName = LogUtils.readString(itemBuffer);
            String dupClassName = LogUtils.readString(itemBuffer);
            if (btreeClassName.length() == 0) {
                btreeComparatorBytes = LogUtils.ZERO_LENGTH_BYTE_ARRAY;
            } else {
                btreeComparatorBytes =
                    objectToBytes(btreeClassName, "Btree");
            }
            if (dupClassName.length() == 0) {
                duplicateComparatorBytes = LogUtils.ZERO_LENGTH_BYTE_ARRAY;
            } else {
                duplicateComparatorBytes =
                    objectToBytes(dupClassName, "Duplicate");
            }
        }

        /* Don't instantiate if comparators are unnecessary (DbPrintLog). */
        if (!EnvironmentImpl.getNoComparators()) {
            try {
		if (btreeComparatorBytes.length != 0) {
                    Object obj = bytesToObject
                        (btreeComparatorBytes, "Btree");
                    if (obj instanceof String) {
                        Class cls = Class.forName((String) obj);
                        btreeComparator =
                            instantiateComparator(cls, "Btree");
                        btreeComparatorByClassName = true;
                    } else if (obj instanceof Comparator) {
                        btreeComparator = (Comparator) obj;
                        btreeComparatorByClassName = false;
                    } else {
                        assert false : obj.getClass().getName();
                    }
		} else {
                    btreeComparator = null;
                    btreeComparatorByClassName = false;
                }
		if (duplicateComparatorBytes.length != 0) {
                    Object obj = bytesToObject
                        (duplicateComparatorBytes, "Duplicate");
                    if (obj instanceof String) {
                        Class cls = Class.forName((String) obj);
                        duplicateComparator =
                            instantiateComparator(cls, "Duplicate");
                        duplicateComparatorByClassName = true;
                    } else if (obj instanceof Comparator) {
                        duplicateComparator = (Comparator) obj;
                        duplicateComparatorByClassName = false;
                    } else {
                        assert false : obj.getClass().getName();
                    }
                } else {
                    duplicateComparator = null;
                    duplicateComparatorByClassName = false;
		}
            } catch (ClassNotFoundException CNFE) {
                throw new LogException("couldn't instantiate class comparator",
                                       CNFE);
            }
        }

	if (entryTypeVersion >= 1) {
	    maxMainTreeEntriesPerNode = LogUtils.readInt(itemBuffer);
	    maxDupTreeEntriesPerNode = LogUtils.readInt(itemBuffer);
        }
    }

    /**
     * @see Loggable#dumpLog
     */
    public void dumpLog(StringBuffer sb, boolean verbose) {
        sb.append("<database>");
        id.dumpLog(sb, verbose);
        tree.dumpLog(sb, verbose);
        sb.append("<dupsort v=\"").append(duplicatesAllowed);
        sb.append("\"/>");
        sb.append("<btcf name=\"");
        sb.append(getComparatorClassName(btreeComparator));
        sb.append("\"/>");
        sb.append("<dupcf name=\"");
        sb.append(getComparatorClassName(duplicateComparator));
        sb.append("\"/>");
        sb.append("</database>");
    }

    /**
     * @see Loggable#getTransactionId
     */
    public long getTransactionId() {
	return 0;
    }

    /**
     * Used for log dumping.
     */
    private static String getComparatorClassName(Comparator comparator) {
        if (comparator != null) {
            return comparator.getClass().getName();
        } else {
            return "";
        }
    }

    /**
     * Used both to read from the log and to validate a comparator when set in 
     * DatabaseConfig.
     */
    public static Comparator instantiateComparator(Class comparator,
                                                   String comparatorType) 
        throws LogException {

	if (comparator == null) {
	    return null;
	}

        try {
	    return (Comparator) comparator.newInstance();
        } catch (InstantiationException IE) {
            throw new LogException
                ("Exception while trying to load " + comparatorType +
                 " Comparator class: " + IE);
        } catch (IllegalAccessException IAE) {
            throw new LogException
                ("Exception while trying to load " + comparatorType +
                 " Comparator class: " + IAE);
        }
    }

    /**
     * Used to validate a comparator when set in DatabaseConfig.
     */
    public static Comparator instantiateComparator(Comparator comparator,
                                                   String comparatorType) 
        throws DatabaseException {

	if (comparator == null) {
	    return null;
	}

        return (Comparator) bytesToObject
            (objectToBytes(comparator, comparatorType), comparatorType);
    }

    /**
     * Converts a comparator object to a serialized byte array, converting to
     * a class name String object if byClassName is true.
     *
     * @throws LogException if the object cannot be serialized.
     */
    private static byte[] comparatorToBytes(Comparator comparator,
                                            boolean byClassName,
                                            String comparatorType) 
        throws DatabaseException {

        if (comparator == null) {
            return LogUtils.ZERO_LENGTH_BYTE_ARRAY;
        } else {
            Object obj;
            if (byClassName) {
                obj = comparator.getClass().getName();
            } else {
                obj = comparator;
            }
            return objectToBytes(obj, comparatorType);
        }
    }

    /**
     * Converts an arbitrary object to a serialized byte array.  Assumes that
     * the object given is non-null.
     */
    public static byte[] objectToBytes(Object obj,
                                       String comparatorType) 
        throws LogException {

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(obj);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new LogException
                ("Exception while trying to load " + comparatorType +
                 ": " + e);
        }
    }

    /**
     * Converts an arbitrary serialized byte array to an object.  Assumes that
     * the byte array given is non-null and has a non-zero length.
     */
    private static Object bytesToObject(byte[] bytes,
                                        String comparatorType) 
        throws LogException {

        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            ObjectInputStream ois = new ObjectInputStream(bais);
            return ois.readObject();
        } catch (IOException e) {
            throw new LogException
                ("Exception while trying to load " + comparatorType +
                 ": " + e);
        } catch (ClassNotFoundException e) {
            throw new LogException
                ("Exception while trying to load " + comparatorType +
                 ": " + e);
        }
    }

    public int getBinDeltaPercent() {
        return binDeltaPercent;
    }

    public int getBinMaxDeltas() {
        return binMaxDeltas;
    }
}
