/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: Tree.java,v 1.418.2.5 2007/07/02 19:54:52 mark Exp $
 */

package com.sleepycat.je.tree;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.cleaner.UtilizationTracker;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.CursorImpl;
import com.sleepycat.je.dbi.DatabaseImpl;
import com.sleepycat.je.dbi.DbConfigManager;
import com.sleepycat.je.dbi.DbTree;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.dbi.INList;
import com.sleepycat.je.latch.LatchSupport;
import com.sleepycat.je.latch.SharedLatch;
import com.sleepycat.je.log.LogManager;
import com.sleepycat.je.log.LogUtils;
import com.sleepycat.je.log.Loggable;
import com.sleepycat.je.recovery.RecoveryManager;
import com.sleepycat.je.txn.BasicLocker;
import com.sleepycat.je.txn.LockGrantType;
import com.sleepycat.je.txn.LockResult;
import com.sleepycat.je.txn.LockType;
import com.sleepycat.je.txn.Locker;
import com.sleepycat.je.txn.WriteLockInfo;
import com.sleepycat.je.utilint.DbLsn;
import com.sleepycat.je.utilint.TestHook;
import com.sleepycat.je.utilint.TestHookExecute;
import com.sleepycat.je.utilint.Tracer;

/**
 * Tree implements the JE B+Tree.
 * 
 * A note on tree search patterns: 
 * There's a set of Tree.search* methods. Some clients of the tree use
 * those search methods directly, whereas other clients of the tree
 * tend to use methods built on top of search.
 *
 * The semantics of search* are
 *   they leave you pointing at a BIN or IN
 *   they don't tell you where the reference of interest is.
 *   they traverse a single tree, to jump into the duplicate tree, the 
 *   caller has to take explicit action.
 * The semantics of the get* methods are:
 *   they leave you pointing at a BIN or IN
 *   they return the index of the slot of interest
 *   they traverse down to whatever level is needed -- they'll take care of
 *   jumping into the duplicate tree.
 *   they are built on top of search* methods.
 * For the future:
 * Over time, we need to clarify which methods are to be used by clients
 * of the tree. Preferably clients that call the tree use get*, although 
 * their are cases where they need visibility into the tree structure. For 
 * example, tee cursors use search* because they want to add themselves to 
 * BIN before jumping into the duplicate tree.
 * 
 * Also, search* should return the location of the slot to save us a 
 * second binary search.
 */
public final class Tree implements Loggable {

    /* For debug tracing */
    private static final String TRACE_ROOT_SPLIT = "RootSplit:";
    private static final String TRACE_DUP_ROOT_SPLIT = "DupRootSplit:";
    private static final String TRACE_MUTATE = "Mut:";
    private static final String TRACE_INSERT = "Ins:";
    private static final String TRACE_INSERT_DUPLICATE = "InsD:";

    private DatabaseImpl database;
    private ChildReference root;
    private int maxMainTreeEntriesPerNode;
    private int maxDupTreeEntriesPerNode;
    private boolean purgeRoot;

    /* 
     * Latch that must be held when using/accessing the root node.  Protects
     * against the root being changed out from underneath us by splitRoot.
     */
    private SharedLatch rootLatch;

    private TreeStats treeStats;

    private ThreadLocal treeStatsAccumulatorTL = new ThreadLocal();

    /*
     * We don't need the stack trace on this so always throw a static and
     * avoid the cost of Throwable.fillInStack() every time it's thrown.
     * [#13354].
     */
    private static SplitRequiredException splitRequiredException =
	new SplitRequiredException();

    /**
     * Embodies an enum for the type of search being performed.  NORMAL means
     * do a regular search down the tree.  LEFT/RIGHT means search down the
     * left/right side to find the first/last node in the tree. 
     */
    public static class SearchType {
        /* Search types */
        public static final SearchType NORMAL = new SearchType();
        public static final SearchType LEFT   = new SearchType();
        public static final SearchType RIGHT  = new SearchType();

        /* No lock types can be defined outside this class. */
        private SearchType() {
        }
    }

    /* For unit tests */
    private TestHook waitHook; // used for generating race conditions
    private TestHook searchHook; // [#12736]
    private TestHook ckptHook; // [#13897]

    /**
     * Create a new tree.
     */
    public Tree(DatabaseImpl database)
        throws DatabaseException {

        init(database);
        setDatabase(database);
    }
    
    /**
     * Create a tree that's being read in from the log.
     */
    public Tree()
        throws DatabaseException {

        init(null);
        maxMainTreeEntriesPerNode = 0;
        maxDupTreeEntriesPerNode = 0;
    }

    /**
     * constructor helper
     */
    private void init(DatabaseImpl database) {
        rootLatch =
	    LatchSupport.makeSharedLatch
	    ("RootLatch",
	     (database != null) ? database.getDbEnvironment() : null);
        treeStats = new TreeStats();
        this.root = null;
        this.database = database;
    }

    /**
     * Set the database for this tree. Used by recovery when recreating an
     * existing tree.
     */
    public void setDatabase(DatabaseImpl database)
        throws DatabaseException {

        this.database = database;
        maxMainTreeEntriesPerNode = database.getNodeMaxEntries();
	maxDupTreeEntriesPerNode = database.getNodeMaxDupTreeEntries();
        DbConfigManager configManager =
            database.getDbEnvironment().getConfigManager();
        purgeRoot = configManager.getBoolean
            (EnvironmentParams.COMPRESSOR_PURGE_ROOT);
    }
    
    /**
     * @return the database for this Tree.
     */
    public DatabaseImpl getDatabase() {
        return database;
    }

    /**
     * Set the root for the tree. Should only be called within the root latch.
     */
    public void setRoot(ChildReference newRoot, boolean notLatched) {
	assert (notLatched || rootLatch.isWriteLockedByCurrentThread());
        root = newRoot;
    }

    public ChildReference makeRootChildReference(Node target,
						 byte[] key,
						 long lsn) {
	return new RootChildReference(target, key, lsn);
    }

    private ChildReference makeRootChildReference() {
	return new RootChildReference();
    }

    /* 
     * A tree doesn't have a root if (a) the root field is null, or (b)
     * the root is non-null, but has neither a valid target nor a valid 
     * LSN. Case (b) can happen if the dataabase is or was previously opened in
     * deferred write mode.
     * @return false if there is no real root.
     */
    public boolean rootExists() {
        if (root == null) {
            return false;
        } 
        
        if ((root.getTarget() == null) && 
            (root.getLsn() == DbLsn.NULL_LSN)) {
            return false;
        }

        return true;
    }

    /**
     * Perform a fast check to see if the root IN is resident.  No latching is
     * performed.  To ensure that the root IN is not loaded by another thread,
     * this method should be called while holding a write lock on the MapLN.
     * That will prevent opening the DB in another thread, and potentially
     * loading the root IN. [#13415]
     */
    public boolean isRootResident() {
        return root != null && root.getTarget() != null;
    }

    /*
     * Class that overrides fetchTarget() so that if the rootLatch is not
     * held exclusively when the root is fetched, we upgrade it to exclusive.
     */
    private class RootChildReference extends ChildReference {

	private RootChildReference() {
	    super();
	}

	private RootChildReference(Node target, byte[] key, long lsn) {
	    super(target, key, lsn);
	}

	/* Not used. */
	private RootChildReference(Node target,
				   byte[] key,
				   long lsn,
				   byte existingState) {
	    super(target, key, lsn, existingState);
	}

	/* Caller is responsible for releasing rootLatch. */
	public Node fetchTarget(DatabaseImpl database, IN in)
	    throws DatabaseException {

	    if (getTarget() == null &&
		!rootLatch.isWriteLockedByCurrentThread()) {
		rootLatch.release();
		rootLatch.acquireExclusive();
	    }

	    return super.fetchTarget(database, in);
	}

	public void setTarget(Node target) {
	    assert rootLatch.isWriteLockedByCurrentThread();
	    super.setTarget(target);
	}

	public void clearTarget() {
	    assert rootLatch.isWriteLockedByCurrentThread();
	    super.clearTarget();
	}

	public void setLsn(long lsn) {
	    assert rootLatch.isWriteLockedByCurrentThread();
	    super.setLsn(lsn);
	}

	void updateLsnAfterOptionaLog(DatabaseImpl dbImpl, long lsn) {
	    assert rootLatch.isWriteLockedByCurrentThread();
	    super.updateLsnAfterOptionalLog(dbImpl, lsn);
	}
    }

    /**
     * Get LSN of the rootIN. Obtained without latching, should only be
     * accessed while quiescent.
     */
    public long getRootLsn() {
        if (root == null) {
            return DbLsn.NULL_LSN;
        } else {
            return root.getLsn();
        }
    }

    /**
     * @return the TreeStats for this tree.
     */
    TreeStats getTreeStats() {
        return treeStats;
    }

    private TreeWalkerStatsAccumulator getTreeStatsAccumulator() {
	if (EnvironmentImpl.getThreadLocalReferenceCount() > 0) {
	    return (TreeWalkerStatsAccumulator) treeStatsAccumulatorTL.get();
	} else {
	    return null;
	}
    }

    public void setTreeStatsAccumulator(TreeWalkerStatsAccumulator tSA) {
	treeStatsAccumulatorTL.set(tSA);
    }

    public IN withRootLatchedExclusive(WithRootLatched wrl)
        throws DatabaseException {

        try {
            rootLatch.acquireExclusive();
            return wrl.doWork(root);
        } finally {
            rootLatch.release();
        }
    }

    public IN withRootLatchedShared(WithRootLatched wrl)
        throws DatabaseException {

        try {
            rootLatch.acquireShared();
            return wrl.doWork(root);
        } finally {
            rootLatch.release();
        }
    }

    /**
     * Deletes a BIN specified by key from the tree. If the BIN resides in a 
     * subtree that can be pruned away, prune as much as possible, so we 
     * don't leave a branch that has no BINs.
     *
     * It's possible that the targeted BIN will now have entries, or will
     * have resident cursors. Either will prevent deletion.
     *
     * @param idKey - the identifier key of the node to delete.
     * @param tracker is used for tracking obsolete node info.
     */
    public void delete(byte[] idKey,
                       UtilizationTracker tracker)
        throws DatabaseException,
               NodeNotEmptyException,
               CursorsExistException {

        IN subtreeRootIN = null;

        /* 
         * A delete is a reverse split that must be propagated up to the root.
         * [#13501] Keep all nodes from the rootIN to the parent of the
         * deletable subtree latched as we descend so we can log the
         * IN deletion and cascade the logging up the tree. The latched
         * nodes are kept in order in the nodeLadder.
         */
        ArrayList nodeLadder = new ArrayList();

        IN rootIN = null;
        boolean rootNeedsUpdating = false;
        rootLatch.acquireExclusive();
        try {
            if (!rootExists()) {
                /* no action, tree is deleted or was never persisted. */
                return; 
            } 

            rootIN = (IN) root.fetchTarget(database, null);
            rootIN.latch(false);

            searchDeletableSubTree(rootIN, idKey, nodeLadder);
            if (nodeLadder.size() == 0) {

                /*
                 * The root is the top of the deletable subtree. Delete the
                 * whole tree if the purge root je property is set.
                 * In general, there's no reason to delete this last
                 * IN->...IN->BIN subtree since we're likely to to add more
                 * nodes to this tree again.  Deleting the subtree also
                 * adds to the space used by the log since a MapLN needs to
                 * be written when the root is nulled, and a MapLN, IN
                 * (root), BIN needs to be written when the root is
                 * recreated.
                 *
                 * Consider a queue application which frequently inserts
                 * and deletes entries and often times leaves the tree
                 * empty, but will insert new records again.
                 *
                 * An optimization might be to prune the multiple IN path
                 * to the last BIN (if it even exists) to just a root IN
                 * pointing to the single BIN, but this doesn't feel like
                 * it's worth the trouble since the extra depth doesn't
                 * matter all that much.
                 */

                if (purgeRoot) {
                    subtreeRootIN = logTreeRemoval(rootIN);
                    if (subtreeRootIN != null) {
                        rootNeedsUpdating = true;
                    }
                }
            } else {
                /* Detach this subtree. */
                SplitInfo detachPoint = 
                    (SplitInfo) nodeLadder.get(nodeLadder.size() - 1);
                boolean deleteOk =
                    detachPoint.parent.deleteEntry(detachPoint.index,
                                                   true);
                assert deleteOk;

                /* Cascade updates upward, including writing the root IN. */
                rootNeedsUpdating = cascadeUpdates(nodeLadder, null, -1);
                subtreeRootIN = detachPoint.child;
            }
        } finally {
            releaseNodeLadderLatches(nodeLadder);

            if (rootIN != null) {
                rootIN.releaseLatch();
            }

            rootLatch.release();
        }


        if (subtreeRootIN != null) {

            EnvironmentImpl envImpl = database.getDbEnvironment();
            if (rootNeedsUpdating) {
                /*
                 * modifyDbRoot will grab locks and we can't have the INList
                 * latches or root latch held while it tries to acquire locks.
                 */
                DbTree dbTree = envImpl.getDbMapTree();
                dbTree.optionalModifyDbRoot(database);
                RecoveryManager.traceRootDeletion(Level.FINE, database);
            } 

            /* 
             * Count obsolete nodes after logging the delete. We can do
             * this without having the nodes of the subtree latched because the
             * subtree has been detached from the tree.
             */
            INList inList = envImpl.getInMemoryINs();
            accountForSubtreeRemoval(inList, subtreeRootIN, tracker);
        }
    }

    private void releaseNodeLadderLatches(ArrayList nodeLadder) 
        throws DatabaseException {

        /*
         * Clear any latches left in the node ladder. Release from the
         * bottom up.
         */
        ListIterator iter = nodeLadder.listIterator(nodeLadder.size());
        while (iter.hasPrevious()) {
            SplitInfo info = (SplitInfo) iter.previous();
            info.child.releaseLatch();
        }
    }
                
    /**
     * This entire tree is empty, clear the root and log a new MapLN
     * @return the rootIN that has been detached, or null if there
     * hasn't been any removal.
     */
    private IN logTreeRemoval(IN rootIN)
        throws DatabaseException {

	assert rootLatch.isWriteLockedByCurrentThread();
        IN detachedRootIN = null;

        /**
         * XXX: Suspect that validateSubtree is no longer needed, now that we
         * hold all latches.
         */
        if ((rootIN.getNEntries() <= 1) &&
            (rootIN.validateSubtreeBeforeDelete(0))) {

            root = null;

            /*
             * Record the root deletion for recovery. Do this within
             * the root latch. We need to put this log entry into the
             * log before another thread comes in and creates a new
             * rootIN for this database.
             *
             * For example,
             * LSN 1000 IN delete info entry
             * LSN 1010 new IN, for next set of inserts
             * LSN 1020 new BIN, for next set of inserts.
             *
             * The entry at 1000 is needed so that LSN 1010 will
             * properly supercede all previous IN entries in the tree.
             * Without the INDelete, we may not use the new root, because
             * it has a different node id.
             */
            INDeleteInfo info = new INDeleteInfo(rootIN.getNodeId(),
                                                 rootIN.getIdentifierKey(),
                                                 database.getId());
            info.optionalLog(database.getDbEnvironment().getLogManager(),
                     database);

        
            detachedRootIN = rootIN;
        }
        return detachedRootIN;
    }

    /**
     * Update nodes for a delete, going upwards. For example, suppose a
     * node ladder holds:
     * INa, INb, index for INb in INa
     * INb, INc, index for INc in INb
     * INc, BINd, index for BINd in INc
     *
     * When we enter this method, BINd has already been removed from INc. We
     * need to 
     *  - log INc
     *  - update INb, log INb
     *  - update INa, log INa
     *
     * @param nodeLadder List of SplitInfos describing each node pair on the
     * downward path
     * @param binRoot parent of the dup tree, or null if this is not for
     * dups.
     * @param index slot occupied by this din tree.
     * @return whether the DB root needs updating.
     */
    private boolean cascadeUpdates(ArrayList nodeLadder, 
                                   BIN binRoot,
                                   int index) 
        throws DatabaseException {

        ListIterator iter = nodeLadder.listIterator(nodeLadder.size());
        EnvironmentImpl envImpl = database.getDbEnvironment();
        LogManager logManager = envImpl.getLogManager();

        long newLsn = DbLsn.NULL_LSN;
        SplitInfo info = null;
        while (iter.hasPrevious()) {
            info = (SplitInfo) iter.previous();

            if (newLsn != DbLsn.NULL_LSN) {
                info.parent.updateEntry(info.index, newLsn);
	    }
            newLsn = info.parent.optionalLog(logManager);
        }

        boolean rootNeedsUpdating = false;
        if (info != null) {
            /* We've logged the top of this subtree, record it properly. */
            if (info.parent.isDbRoot()) {
                /* We updated the rootIN of the database. */
                assert rootLatch.isWriteLockedByCurrentThread();
                root.updateLsnAfterOptionalLog(database, newLsn);
                rootNeedsUpdating = true;
            } else if ((binRoot != null) && info.parent.isRoot()) {
                /* We updated the DIN root of the database. */
                binRoot.updateEntry(index, newLsn);
            } else {
                assert false;
            }
        }
        return rootNeedsUpdating;
    }

    /**
     * Delete a subtree of a duplicate tree.  Find the duplicate tree using
     * mainKey in the top part of the tree and idKey in the duplicate tree.
     *
     * @param idKey the identifier key to be used in the duplicate subtree to
     * find the duplicate path.
     * @param mainKey the key to be used in the main tree to find the
     * duplicate subtree.
     * @param tracker is used for tracking obsolete node info.
     *
     * @return true if the delete succeeded, false if there were still cursors
     * present on the leaf DBIN of the subtree that was located.
     */
    public void deleteDup(byte[] idKey,
                          byte[] mainKey,
                          UtilizationTracker tracker)
        throws DatabaseException,
               NodeNotEmptyException,
               CursorsExistException {

        /* Find the BIN that is the parent of this duplicate tree. */
        IN in = search(mainKey, SearchType.NORMAL, -1, null,
                       false /*updateGeneration*/);

        IN deletedSubtreeRoot = null;
        try {
            assert in.isLatchOwnerForWrite();
            assert in instanceof BIN;
            assert in.getNEntries() > 0;

            /* Find the appropriate entry in this BIN. */
            int index = in.findEntry(mainKey, false, true);
            if (index >= 0) {
                deletedSubtreeRoot = deleteDupSubtree(idKey, (BIN) in, index);
            }
        } finally {
            in.releaseLatch();
        }

        if (deletedSubtreeRoot != null) {
            EnvironmentImpl envImpl = database.getDbEnvironment();
            accountForSubtreeRemoval(envImpl.getInMemoryINs(),
                                     deletedSubtreeRoot, tracker);
        }
    }

    /**
     * We enter and leave this method with 'bin' latched.
     * @return the root of the subtree we have deleted, so it can be
     * properly accounted for. May be null if nothing was deleted.
     */
    private IN deleteDupSubtree(byte[] idKey,
                                BIN bin,
                                int index)
        throws DatabaseException,
               NodeNotEmptyException, 
               CursorsExistException {

        EnvironmentImpl envImpl = database.getDbEnvironment();
	boolean dupCountLNLocked = false;
	DupCountLN dcl = null;
        BasicLocker locker = new BasicLocker(envImpl);

        /*  Latch the DIN root. */
        DIN duplicateRoot = (DIN) bin.fetchTarget(index);
        duplicateRoot.latch(false);
        
        ArrayList nodeLadder = new ArrayList();
        IN subtreeRootIN = null;

	try {

            /* 
             * Read lock the dup count LN to ascertain whether there are any
             * writers in the tree. XXX: This seems unnecessary now, revisit.
             */
            ChildReference dclRef = duplicateRoot.getDupCountLNRef();
            dcl = (DupCountLN) dclRef.fetchTarget(database, duplicateRoot);
            
            LockResult lockResult = locker.nonBlockingLock(dcl.getNodeId(),
                                                           LockType.READ,
                                                           database);
            if (lockResult.getLockGrant() == LockGrantType.DENIED) {
                throw CursorsExistException.CURSORS_EXIST;
            } else {
                dupCountLNLocked = true;
            }

            /*
             * We don't release the latch on bin before we search the
             * duplicate tree below because we might be deleting the whole
             * subtree from the IN and we want to keep it latched until we
             * know.
             */
            searchDeletableSubTree(duplicateRoot, idKey, nodeLadder);


            if (nodeLadder.size() == 0) {
                /* We're deleting the duplicate root. */
                if (bin.nCursors() == 0) {
                    boolean deleteOk = bin.deleteEntry(index, true);
                    assert deleteOk;

                    /*
                     * Use an INDupDeleteInfo to make it clear that
                     * this duplicate tree has been eradicated. This 
                     * is analagous to deleting a root; we must be sure
                     * that we can overlay another subtree onto this slot
                     * at recovery redo.
                     */
                    INDupDeleteInfo info =
                        new INDupDeleteInfo(duplicateRoot.getNodeId(),
                                            duplicateRoot.getMainTreeKey(),
                                            duplicateRoot.getDupTreeKey(),
                                            database.getId());
                    info.optionalLog(envImpl.getLogManager(), database);

                    subtreeRootIN = duplicateRoot;

                    if (bin.getNEntries() == 0) {
                        database.getDbEnvironment().
                            addToCompressorQueue(bin, null, false);
                    }
                } else {

                    /*
                     * Cursors prevent us from deleting this dup tree, we'll
                     * have to retry.
                     */
                    throw CursorsExistException.CURSORS_EXIST;
                }
            } else {

                /* We're deleting a portion of the duplicate tree. */
                SplitInfo detachPoint = 
                    (SplitInfo) nodeLadder.get(nodeLadder.size() - 1);
                boolean deleteOk =
                    detachPoint.parent.deleteEntry(detachPoint.index,
                                                   true);
                assert deleteOk;

                /* 
                 * Cascade updates upward, including writing the root
                 * DIN and parent BIN.
                 */
                cascadeUpdates(nodeLadder, bin, index);
                subtreeRootIN = detachPoint.child;
	    }
	} finally {
            releaseNodeLadderLatches(nodeLadder);

	    /* FindBugs -- ignore dcl possibly null warning. */
	    if (dupCountLNLocked) {
		locker.releaseLock(dcl.getNodeId());
	    }

	    duplicateRoot.releaseLatch();
	}

        return subtreeRootIN;
    }

    /**
     * Find the leftmost node (IN or BIN) in the tree.  Do not descend into a
     * duplicate tree if the leftmost entry of the first BIN refers to one.
     *
     * @return the leftmost node in the tree, null if the tree is empty.  The
     * returned node is latched and the caller must release it.
     */
    public IN getFirstNode()
        throws DatabaseException {

        return search
            (null, SearchType.LEFT, -1, null, true /*updateGeneration*/);
    }

    /**
     * Find the rightmost node (IN or BIN) in the tree.  Do not descend into a
     * duplicate tree if the rightmost entry of the last BIN refers to one.
     *
     * @return the rightmost node in the tree, null if the tree is empty.  The
     * returned node is latched and the caller must release it.
     */
    public IN getLastNode()
        throws DatabaseException {

        return search
            (null, SearchType.RIGHT, -1, null, true /*updateGeneration*/);
    }

    /**
     * Find the leftmost node (DBIN) in a duplicate tree.
     *
     * @return the leftmost node in the tree, null if the tree is empty.  The
     * returned node is latched and the caller must release it.
     */
    public DBIN getFirstNode(DIN dupRoot)
        throws DatabaseException {

        if (dupRoot == null) {
            throw new IllegalArgumentException
                ("getFirstNode passed null root");
        }

        assert dupRoot.isLatchOwnerForWrite();

        IN ret = searchSubTree
            (dupRoot, null, SearchType.LEFT, -1, null,
             true /*updateGeneration*/);
        return (DBIN) ret;
    }

    /**
     * Find the rightmost node (DBIN) in a duplicate tree.
     *
     * @return the rightmost node in the tree, null if the tree is empty.  The
     * returned node is latched and the caller must release it.
     */
    public DBIN getLastNode(DIN dupRoot)
        throws DatabaseException {

        if (dupRoot == null) {
            throw new IllegalArgumentException
                ("getLastNode passed null root");
        }

        assert dupRoot.isLatchOwnerForWrite();

        IN ret = searchSubTree
            (dupRoot, null, SearchType.RIGHT, -1, null,
             true /*updateGeneration*/);
        return (DBIN) ret;
    }

    /**
     * GetParentNode without optional tracking.
     */
    public SearchResult getParentINForChildIN(IN child,
					      boolean requireExactMatch,
					      boolean updateGeneration)
        throws DatabaseException {

        return getParentINForChildIN
            (child, requireExactMatch, updateGeneration, -1, null);
    }

    /**
     * Return a reference to the parent or possible parent of the child.  Used
     * by objects that need to take a standalone node and find it in the tree,
     * like the evictor, checkpointer, and recovery.
     *
     * @param child The child node for which to find the parent.  This node is
     * latched by the caller and is released by this function before returning
     * to the caller.
     *
     * @param requireExactMatch if true, we must find the exact parent, not a
     * potential parent.
     *
     * @param updateGeneration if true, set the generation count during
     * latching.  Pass false when the LRU should not be impacted, such as
     * during eviction and checkpointing.
     *
     * @param trackingList if not null, add the LSNs of the parents visited
     * along the way, as a debug tracing mechanism. This is meant to stay in
     * production, to add information to the log.
     *
     * @return a SearchResult object. If the parent has been found,
     * result.foundExactMatch is true. If any parent, exact or potential has
     * been found, result.parent refers to that node.
     */
    public SearchResult getParentINForChildIN(IN child,
					      boolean requireExactMatch,
					      boolean updateGeneration,
                                              int targetLevel,
					      List trackingList)
        throws DatabaseException {

        /* Sanity checks */
        if (child == null) {
            throw new IllegalArgumentException("getParentNode passed null");
        }

        assert child.isLatchOwnerForWrite();

        /* 
         * Get information from child before releasing latch. 
         */
        byte[] mainTreeKey = child.getMainTreeKey();
        byte[] dupTreeKey = child.getDupTreeKey();
        boolean isRoot = child.isRoot();
        child.releaseLatch();

        return getParentINForChildIN(child.getNodeId(),
                                     child.containsDuplicates(),
                                     isRoot,
                                     mainTreeKey,
                                     dupTreeKey,
                                     requireExactMatch,
                                     updateGeneration,
                                     targetLevel,
                                     trackingList,
                                     true);
    }

    /**
     * Return a reference to the parent or possible parent of the child.  Used
     * by objects that need to take a node id and find it in the tree,
     * like the evictor, checkpointer, and recovery.
     *
     * @param requireExactMatch if true, we must find the exact parent, not a
     * potential parent.
     *
     * @param updateGeneration if true, set the generation count during
     * latching.  Pass false when the LRU should not be impacted, such as
     * during eviction and checkpointing.
     * 
     * @param trackingList if not null, add the LSNs of the parents visited
     * along the way, as a debug tracing mechanism. This is meant to stay in
     * production, to add information to the log.
     *
     * @param doFetch if false, stop the search if we run into a non-resident 
     * child. Used by the checkpointer to avoid conflicting with work done
     * by the evictor.
     *
     * @param child The child node for which to find the parent.  This node is
     * latched by the caller and is released by this function before returning
     * to the caller.
     *
     * @return a SearchResult object. If the parent has been found,
     * result.foundExactMatch is true. If any parent, exact or potential has
     * been found, result.parent refers to that node.
     */
    public SearchResult getParentINForChildIN(long targetNodeId,
                                              boolean targetContainsDuplicates,
                                              boolean targetIsRoot,
                                              byte[] targetMainTreeKey,
                                              byte[] targetDupTreeKey,
					      boolean requireExactMatch,
					      boolean updateGeneration,
                                              int targetLevel,
					      List trackingList,
                                              boolean doFetch)
        throws DatabaseException {

        IN rootIN = getRootINLatchedExclusive(updateGeneration);

        SearchResult result = new SearchResult();
        if (rootIN != null) {
            /* The tracking list is a permanent tracing aid. */
            if (trackingList != null) {
                trackingList.add(new TrackingInfo(root.getLsn(),
                                                  rootIN.getNodeId()));
            }
                
            IN potentialParent = rootIN;

            try {
                while (result.keepSearching) {

		    /*
		     * [12736] Prune away oldBin.  Assert has intentional
		     * side effect.
		     */
		    assert TestHookExecute.doHookIfSet(searchHook);

                    potentialParent.findParent(SearchType.NORMAL,
                                               targetNodeId,
                                               targetContainsDuplicates,
                                               targetIsRoot,
                                               targetMainTreeKey,
                                               targetDupTreeKey,
                                               result,
                                               requireExactMatch,
					       updateGeneration,
                                               targetLevel,
                                               trackingList,
                                               doFetch);
                    potentialParent = result.parent;
                }
            } catch (Exception e) {
                potentialParent.releaseLatchIfOwner();

                throw new DatabaseException(e);
            }
        } 
        return result;
    }

    /**
     * Return a reference to the parent of this LN. This searches through the
     * main and duplicate tree and allows splits. Set the tree location to the
     * proper BIN parent whether or not the LN child is found. That's because
     * if the LN is not found, recovery or abort will need to place it within
     * the tree, and so we must point at the appropriate position.
     *
     * <p>When this method returns with location.bin non-null, the BIN is
     * latched and must be unlatched by the caller.  Note that location.bin may
     * be non-null even if this method returns false.</p>
     *
     * @param location a holder class to hold state about the location 
     * of our search. Sort of an internal cursor.
     *
     * @param mainKey key to navigate through main key
     *
     * @param dupKey key to navigate through duplicate tree. May be null, since
     * deleted lns have no data.
     *
     * @param ln the node instantiated from the log
     *
     * @param splitsAllowed true if this method is allowed to cause tree splits
     * as a side effect. In practice, recovery can cause splits, but abort
     * can't.
     *
     * @param searchDupTree true if a search through the dup tree looking for
     * a match on the ln's node id should be made (only in the case where
     * dupKey == null).  See SR 8984.
     *
     * @param updateGeneration if true, set the generation count during
     * latching.  Pass false when the LRU should not be impacted, such as
     * during eviction and checkpointing.
     *
     * @return true if node found in tree.
     * If false is returned and there is the possibility that we can insert 
     * the record into a plausible parent we must also set
     * - location.bin (may be null if no possible parent found)
     * - location.lnKey (don't need to set if no possible parent).
     */
    public boolean getParentBINForChildLN(TreeLocation location,
                                          byte[] mainKey,
                                          byte[] dupKey,
                                          LN ln,
                                          boolean splitsAllowed,
					  boolean findDeletedEntries,
					  boolean searchDupTree,
                                          boolean updateGeneration)
        throws DatabaseException {

        /* 
         * Find the BIN that either points to this LN or could be its
         * ancestor.
         */
        IN searchResult = null;
        try {
            if (splitsAllowed) {
                searchResult = searchSplitsAllowed
                    (mainKey, -1, updateGeneration);
            } else {
                searchResult = search
                    (mainKey, SearchType.NORMAL, -1, null, updateGeneration);
            }
            location.bin = (BIN) searchResult;
        } catch (Exception e) {
            /* SR 11360 tracing. */
            StringBuffer msg = new StringBuffer();
            if (searchResult != null) {
                searchResult.releaseLatchIfOwner();
                msg.append("searchResult=" + searchResult.getClass() +
                           " nodeId=" + searchResult.getNodeId() +
                           " nEntries=" + searchResult.getNEntries());
            }
            throw new DatabaseException(msg.toString(), e);
        }

	if (location.bin == null) {
	    return false;
	}

	/*
	 * If caller wants us to consider knownDeleted entries then do an
	 * inexact search in findEntry since that will find knownDeleted
	 * entries.  If caller doesn't want us to consider knownDeleted entries
	 * then do an exact search in findEntry since that will not return
	 * knownDeleted entries.
	 */
	boolean exactSearch = false;
	boolean indicateIfExact = true;
	if (!findDeletedEntries) {
	    exactSearch = true;
	    indicateIfExact = false;
	}
        location.index =
	    location.bin.findEntry(mainKey, indicateIfExact, exactSearch);

	boolean match = false;
	if (findDeletedEntries) {
	    match = (location.index >= 0 &&
		     (location.index & IN.EXACT_MATCH) != 0);
	    location.index &= ~IN.EXACT_MATCH;
	} else {
	    match = (location.index >= 0);
	}

        if (match) {

            /*
             * A BIN parent was found and a slot matches the key. See if
             * we have to search further into what may be a dup tree.
             */
	    if (!location.bin.isEntryKnownDeleted(location.index)) {
                
                /*
                 * If this database doesn't support duplicates, no point in
                 * incurring the potentially large cost of fetching in
                 * the child to check for dup trees. In the future, we could
                 * optimize further by storing state per slot as to whether
                 * a dup tree hangs below.
                 */
                if (database.getSortedDuplicates()) {

                    Node childNode = location.bin.fetchTarget(location.index);
                    try {

                        /* 
                         * Is our target LN a regular record or a dup count?
                         */
                        if (childNode == null) {
                            /* Child is a deleted cleaned LN. */
                        } else if (ln.containsDuplicates()) {
                            /* This is a duplicate count LN. */
                            return searchDupTreeForDupCountLNParent
                                (location, mainKey, childNode);
                        } else {

                            /* 
                             * This is a regular LN. If this is a dup tree,
                             * descend and search. If not, we've found the
                             * parent.
                             */
                            if (childNode.containsDuplicates()) {
                                if (dupKey == null) {

                                    /*
                                     * We are at a dup tree but our target LN
                                     * has no dup key because it's a deleted
                                     * LN.  We've encountered the case of SR
                                     * 8984 where we are searching for an LN
                                     * that was deleted before the conversion
                                     * to a duplicate tree.
                                     */
				    return searchDupTreeByNodeId
                                        (location, childNode, ln,
                                         searchDupTree, updateGeneration);
                                } else {
                                    return searchDupTreeForDBIN
                                        (location, dupKey, (DIN) childNode,
                                         ln, findDeletedEntries,
                                         indicateIfExact, exactSearch,
                                         splitsAllowed, updateGeneration);
                                }
                            }
                        }
                    } catch (DatabaseException e) {
                        location.bin.releaseLatchIfOwner();
                        throw e;
                    }
                }
            }

            /* We had a match, we didn't need to search the duplicate tree. */
            location.childLsn = location.bin.getLsn(location.index);
            return true;
        } else {
            location.lnKey = mainKey;
            return false;
        }
    }

    /**
     * For SR [#8984]: our prospective child is a deleted LN, and
     * we're facing a dup tree. Alas, the deleted LN has no data, and
     * therefore nothing to guide the search in the dup tree. Instead,
     * we search by node id.  This is very expensive, but this
     * situation is a very rare case.
     */
    private boolean searchDupTreeByNodeId(TreeLocation location,
                                          Node childNode,
                                          LN ln,
                                          boolean searchDupTree,
                                          boolean updateGeneration)
        throws DatabaseException {

        if (searchDupTree) {
            BIN oldBIN = location.bin;
            if (childNode.matchLNByNodeId
                (location, ln.getNodeId())) {
                location.index &= ~IN.EXACT_MATCH;
                if (oldBIN != null) {
                    oldBIN.releaseLatch();
                }
                location.bin.latch(updateGeneration);
                return true;
            } else {
                return false;
            }
        } else {
            
            /*
             * This is called from undo() so this LN can
             * just be ignored.
             */
            return false;
        }
    }
    
    /**
     * @return true if childNode is the DIN parent of this DupCountLN
     */
    private boolean searchDupTreeForDupCountLNParent(TreeLocation location,
                                                     byte[] mainKey,
                                                     Node childNode) 
        throws DatabaseException {
        location.lnKey = mainKey;
        if (childNode instanceof DIN) {
            DIN dupRoot = (DIN) childNode;
            location.childLsn = dupRoot.getDupCountLNRef().getLsn();
            return true;
        } else {

            /*
             * If we're looking for a DupCountLN but don't find a
             * duplicate tree, then the key now refers to a single
             * datum.  This can happen when all dups for a key are
             * deleted, the compressor runs, and then a single
             * datum is inserted.  [#10597]
             */
            return false;
        }
    }

    /**
     * Search the dup tree for the DBIN parent of this ln.
     */
    private boolean searchDupTreeForDBIN(TreeLocation location,
                                         byte[] dupKey,
                                         DIN dupRoot,
                                         LN ln,
                                         boolean findDeletedEntries,
                                         boolean indicateIfExact,
                                         boolean exactSearch,
                                         boolean splitsAllowed,
                                         boolean updateGeneration)
        throws DatabaseException {                                     

        assert dupKey != null;

        dupRoot.latch();

        try {
            /* Make sure there's room for inserts. */
            if (maybeSplitDuplicateRoot(location.bin, location.index)) {
                dupRoot = (DIN) location.bin.fetchTarget(location.index);
            }

            /* 
             * Wait until after any duplicate root splitting to unlatch the
             * bin.
             */
            location.bin.releaseLatch();       
             
            /*
             * The dupKey is going to be the key that represents the LN in this
             * BIN parent.
             */
            location.lnKey = dupKey;

            /* Search the dup tree */
            if (splitsAllowed) {
                try {
                    location.bin = (BIN) searchSubTreeSplitsAllowed
                        (dupRoot, location.lnKey, ln.getNodeId(),
                         updateGeneration);
                } catch (SplitRequiredException e) {

                    /* 
                     * Shouldn't happen; the only caller of this method which
                     * allows splits is from recovery, which is single
                     * threaded.
                     */
                    throw new DatabaseException(e);
                }
            } else {
                location.bin = (BIN) searchSubTree
                    (dupRoot, location.lnKey, SearchType.NORMAL,
                     ln.getNodeId(), null, updateGeneration);
            }

            /* Search for LN w/exact key. */
            location.index = location.bin.findEntry
                (location.lnKey, indicateIfExact, exactSearch);
            boolean match;
            if (findDeletedEntries) {
                match = (location.index >= 0 &&
                         (location.index & IN.EXACT_MATCH) != 0);
                location.index &= ~IN.EXACT_MATCH;
            } else {
                match = (location.index >= 0);
            }

            if (match) {
                location.childLsn = location.bin.getLsn(location.index);
                return true;
            } else {
                return false;
            }
        } catch (DatabaseException e) {
            dupRoot.releaseLatchIfOwner();
            throw e;
        }
    }


    /**
     * Return a reference to the adjacent BIN.
     *
     * @param bin The BIN to find the next BIN for.  This BIN is latched.
     * @param traverseWithinDupTree if true, only search within the dup tree
     * and return null when the traversal runs out of duplicates.
     *
     * @return The next BIN, or null if there are no more.  The returned node
     * is latched and the caller must release it.  If null is returned, the
     * argument BIN remains latched.
     */
    public BIN getNextBin(BIN bin, boolean traverseWithinDupTree)
        throws DatabaseException {

        return getNextBinInternal(traverseWithinDupTree, bin, true);
    }

    /**
     * Return a reference to the previous BIN.
     *
     * @param bin The BIN to find the next BIN for.  This BIN is latched.
     * @param traverseWithinDupTree if true, only search within the dup tree
     * and return null when the traversal runs out of duplicates.
     *
     * @return The previous BIN, or null if there are no more.  The returned
     * node is latched and the caller must release it.  If null is returned,
     * the argument bin remains latched.
     */
    public BIN getPrevBin(BIN bin, boolean traverseWithinDupTree)
        throws DatabaseException {

        return getNextBinInternal(traverseWithinDupTree, bin, false);
    }

    /**
     * Helper routine for above two routines to iterate through BIN's.
     */
    private BIN getNextBinInternal(boolean traverseWithinDupTree,
				   BIN bin,
				   boolean forward)
        throws DatabaseException {

        /*
         * Use the right most key (for a forward progressing cursor) or the
         * left most key (for a backward progressing cursor) as the idkey.  The
         * reason is that the BIN may get split while finding the next BIN so
         * it's not safe to take the BIN's identifierKey entry.  If the BIN
         * gets splits, then the right (left) most key will still be on the
         * resultant node.  The exception to this is that if there are no
         * entries, we just use the identifier key.
         */
        byte[] idKey = null;

	if (bin.getNEntries() == 0) {
	    idKey = bin.getIdentifierKey();
	} else if (forward) {
	    idKey = bin.getKey(bin.getNEntries() - 1);
	} else {
	    idKey = bin.getKey(0);
	}

        IN next = bin;
	boolean nextIsLatched = false;

        assert LatchSupport.countLatchesHeld() == 1:
            LatchSupport.latchesHeldToString();

        /* 
         * Ascend the tree until we find a level that still has nodes to the
         * right (or left if !forward) of the path that we're on.  If we reach
         * the root level, we're done. If we're searching within a duplicate
         * tree, stay within the tree.
         */
        IN parent = null;
        IN nextIN = null;
	boolean nextINIsLatched = false;
        try {
            while (true) {

                /* 
                 * Move up a level from where we are now and check to see if we
                 * reached the top of the tree.
                 */
                SearchResult result = null;
                if (!traverseWithinDupTree) {
                    /* Operating on a regular tree -- get the parent. */
		    nextIsLatched = false;
                    result = getParentINForChildIN
                        (next, true /* requireExactMatch */,
                         true /* updateGeneration */);
                    if (result.exactParentFound) {
                        parent = result.parent;
                    } else {
                        /* We've reached the root of the tree. */
                        assert (LatchSupport.countLatchesHeld() == 0):
                            LatchSupport.latchesHeldToString();
                        return null;
                    }
                } else {
                    /* This is a duplicate tree, stay within the tree.*/
                    if (next.isRoot()) {
                        /* We've reached the top of the dup tree. */
                        next.releaseLatch();
			nextIsLatched = false;
                        return null;
                    } else {
                        result = getParentINForChildIN
                            (next, true /* requireExactMatch */,
                             true /* updateGeneration */);
			nextIsLatched = false;
                        if (result.exactParentFound) {
                            parent = result.parent;
                        } else {
                            return null;
                        }
                    }
                }

                assert (LatchSupport.countLatchesHeld() == 1) :
                    LatchSupport.latchesHeldToString();

                /* 
                 * Figure out which entry we are in the parent.  Add (subtract)
                 * 1 to move to the next (previous) one and check that we're
                 * still pointing to a valid child.  Don't just use the result
                 * of the parent.findEntry call in getParentNode, because we
                 * want to use our explicitly chosen idKey.
                 */
                int index = parent.findEntry(idKey, false, false);
                boolean moreEntriesThisBin = false;
                if (forward) {
                    index++;
                    if (index < parent.getNEntries()) {
                        moreEntriesThisBin = true;
                    }
                } else {
                    if (index > 0) {
                        moreEntriesThisBin = true;
                    }
                    index--;
                }

                if (moreEntriesThisBin) {

                    /* 
                     * There are more entries to the right of the current path
                     * in parent.  Get the entry, and then descend down the
                     * left most path to a BIN.
                     */
                    nextIN = (IN) parent.fetchTarget(index);
                    nextIN.latch();
		    nextINIsLatched = true;

                    assert (LatchSupport.countLatchesHeld() == 2):
                        LatchSupport.latchesHeldToString();

                    if (nextIN instanceof BIN) {
                        /* We landed at a leaf (i.e. a BIN). */
                        parent.releaseLatch();
                        TreeWalkerStatsAccumulator treeStatsAccumulator =
                            getTreeStatsAccumulator();
                        if (treeStatsAccumulator != null) {
                            nextIN.accumulateStats(treeStatsAccumulator);
                        }

                        return (BIN) nextIN;
                    } else {

                        /* 
                         * We landed at an IN.  Descend down to the appropriate
                         * leaf (i.e. BIN) node.
                         */
			nextINIsLatched = false;
                        IN ret = searchSubTree(nextIN, null,
                                               (forward ?
                                                SearchType.LEFT :
                                                SearchType.RIGHT),
                                               -1,
                                               null,
                                               true /*updateGeneration*/);
                        parent.releaseLatch();

                        assert LatchSupport.countLatchesHeld() == 1:
                            LatchSupport.latchesHeldToString();

                        if (ret instanceof BIN) {
                            return (BIN) ret;
                        } else {
                            throw new InconsistentNodeException
                                ("subtree did not have a BIN for leaf");
                        }
                    }
                }
                next = parent;
		nextIsLatched = true;
            }
        } catch (DatabaseException e) {
	    if (nextIsLatched) {
		next.releaseLatchIfOwner();
		nextIsLatched = false;
	    }
            if (parent != null) {
                parent.releaseLatchIfOwner();
            }
            if (nextIN != null &&
		nextINIsLatched) {
                nextIN.releaseLatchIfOwner();
            }
            throw e;
        }
    }

    /**
     * Split the root of the tree.
     */
    private void splitRoot()
        throws DatabaseException {

        /* 
         * Create a new root IN, insert the current root IN into it, and then
         * call split.
         */
        EnvironmentImpl env = database.getDbEnvironment();
        LogManager logManager = env.getLogManager();
        INList inMemoryINs = env.getInMemoryINs();

        IN curRoot = null;
        curRoot = (IN) root.fetchTarget(database, null);
        curRoot.latch();
        long curRootLsn = 0;
        long logLsn = 0;
        IN newRoot = null;
        try {

            /*
             * Make a new root IN, giving it an id key from the previous root.
             */
            byte[] rootIdKey = curRoot.getKey(0);
            newRoot = new IN(database, rootIdKey, maxMainTreeEntriesPerNode,
			     curRoot.getLevel() + 1);
	    newRoot.latch();
            newRoot.setIsRoot(true);
            curRoot.setIsRoot(false);

            /*
             * Make the new root IN point to the old root IN. Log the old root
             * provisionally, because we modified it so it's not the root
             * anymore, then log the new root. We are guaranteed to be able to
             * insert entries, since we just made this root.
             */
            try {
                curRootLsn =
                    curRoot.optionalLogProvisional(logManager, newRoot);
                boolean insertOk = newRoot.insertEntry
                    (new ChildReference(curRoot, rootIdKey, curRootLsn));
                assert insertOk;

                logLsn = newRoot.optionalLog(logManager);
            } catch (DatabaseException e) {
                /* Something went wrong when we tried to log. */
                curRoot.setIsRoot(true);
                throw e;
            }
            inMemoryINs.add(newRoot);

            /*
             * Make the tree's root reference point to this new node. Now the
             * MapLN is logically dirty, but the change hasn't been logged.  Be
             * sure to flush the MapLN if we ever evict the root.
             */
            root.setTarget(newRoot);
            root.updateLsnAfterOptionalLog(database, logLsn);
            curRoot.split(newRoot, 0, maxMainTreeEntriesPerNode);
            root.setLsn(newRoot.getLastFullVersion());

        } finally {
	    /* FindBugs ignore possible null pointer dereference of newRoot. */
	    newRoot.releaseLatch();
            curRoot.releaseLatch();
        }
        treeStats.nRootSplits++;
        traceSplitRoot(Level.FINE, TRACE_ROOT_SPLIT, newRoot, logLsn,
                       curRoot, curRootLsn);
    }

    /**
     * Search the tree, starting at the root.  Depending on search type either
     * search using key, or search all the way down the right or left sides.
     * Stop the search either when the bottom of the tree is reached, or a node
     * matching nid is found (see below) in which case that node's parent is
     * returned. 
     *
     * Preemptive splitting is not done during the search.
     *
     * @param key - the key to search for, or null if searchType is LEFT or
     * RIGHT.
     *
     * @param searchType - The type of tree search to perform.  NORMAL means
     * we're searching for key in the tree.  LEFT/RIGHT means we're descending
     * down the left or right side, resp.  DELETE means we're descending the
     * tree and will return the lowest node in the path that has > 1 entries.
     *
     * @param nid - The nodeid to search for in the tree.  If found, returns
     * its parent.  If the nodeid of the root is passed, null is returned.
     *
     * @param binBoundary - If non-null, information is returned about whether
     * the BIN found is the first or last BIN in the database.
     *
     * @return - the Node that matches the criteria, if any.  This is the node
     * that is farthest down the tree with a match.  Returns null if the root
     * is null.  Node is latched (unless it's null) and must be unlatched by
     * the caller.  Only IN's and BIN's are returned, not LN's.  In a NORMAL
     * search, It is the caller's responsibility to do the findEntry() call on
     * the key and BIN to locate the entry that matches key.  The return value
     * node is latched upon return and it is the caller's responsibility to
     * unlatch it.
     */
    public IN search(byte[] key,
                     SearchType searchType,
                     long nid,
                     BINBoundary binBoundary,
                     boolean updateGeneration)
        throws DatabaseException {

        IN rootIN = getRootIN(true /* updateGeneration */);

        if (rootIN != null) {
            return searchSubTree
                (rootIN, key, searchType, nid, binBoundary, updateGeneration);
        } else {
            return null;
        }
    }

    /**
     * Do a key based search, permitting pre-emptive splits. Returns the 
     * target node's parent.
     */
    public IN searchSplitsAllowed(byte[] key,
                                  long nid,
                                  boolean updateGeneration)
        throws DatabaseException {

        IN insertTarget = null;
        while (insertTarget == null) {
            rootLatch.acquireShared();
            boolean rootLatched = true;
	    boolean rootLatchedExclusive = false;
	    boolean rootINLatched = false;
	    boolean success = false;
            IN rootIN = null;
	    try {
		while (true) {
		    if (rootExists()) {
			rootIN = (IN) root.fetchTarget(database, null);

			/* Check if root needs splitting. */
			if (rootIN.needsSplitting()) {
			    if (!rootLatchedExclusive) {
				rootIN = null;
				rootLatch.release();
				rootLatch.acquireExclusive();
				rootLatchedExclusive = true;
				continue;
			    }
			    splitRoot();

			    /*
			     * We can't hold any latches while we lock.  If the
			     * root splits again between latch release and
			     * DbTree.db lock, no problem.  The latest root
			     * will still get written out.
			     */
			    rootLatch.release();
			    rootLatched = false;
			    EnvironmentImpl env = database.getDbEnvironment();
			    env.getDbMapTree().optionalModifyDbRoot(database);
			    rootLatched = true;
			    rootLatch.acquireExclusive();
			    rootIN = (IN) root.fetchTarget(database, null);
			}
			rootIN.latch();
			rootINLatched = true;
		    }
		    break;
		}
		success = true;
	    } finally {
		if (!success && rootINLatched) {
		    rootIN.releaseLatch();
		}
		if (rootLatched) {
		    rootLatch.release();	
		}
	    }

            /* Don't loop forever if the root is null. [#13897] */
            if (rootIN == null) {
                break;
            } 

            try {
		success = false;
                insertTarget =
		    searchSubTreeSplitsAllowed(rootIN, key, nid,
					       updateGeneration);
		success = true;
            } catch (SplitRequiredException e) {

		success = true;

                /* 
                 * The last slot in the root was used at the point when this
                 * thread released the rootIN latch in order to force splits.
                 * Retry. SR [#11147].
                 */
                continue;
            } finally {
		if (!success) {
		    rootIN.releaseLatchIfOwner();
		    if (insertTarget != null) {
			insertTarget.releaseLatchIfOwner();
		    }
		}
	    }
        }

        return insertTarget;
    }

    /* 
     * Singleton class to indicate that root IN needs to be relatched for
     * exclusive access due to a fetch occurring.
     */
    private static class RelatchRequiredException extends DatabaseException {
	public synchronized Throwable fillInStackTrace() {
	    return this;
	}
    }

    private static RelatchRequiredException relatchRequiredException =
	new RelatchRequiredException();

    /**
     * Wrapper for searchSubTreeInternal that does a restart if a
     * RelatchRequiredException is thrown (i.e. a relatch of the root is
     * needed.
     */
    public IN searchSubTree(IN parent,
			    byte[] key,
			    SearchType searchType,
                            long nid,
                            BINBoundary binBoundary,
                            boolean updateGeneration)
        throws DatabaseException {

	/* 
	 * Max of two iterations required.  First is root latched shared, and
	 * second is root latched exclusive.
	 */
	for (int i = 0; i < 2; i++) {
	    try {
		return searchSubTreeInternal(parent, key, searchType, nid,
					     binBoundary, updateGeneration);
	    } catch (RelatchRequiredException RRE) {
		parent = getRootINLatchedExclusive(updateGeneration);
	    }
	}

	throw new DatabaseException
	    ("searchSubTreeInternal should have completed in two tries");
    }

    /**
     * Searches a portion of the tree starting at parent using key.  If during
     * the search a node matching a non-null nid argument is found, its parent
     * is returned.  If searchType is NORMAL, then key must be supplied to
     * guide the search.  If searchType is LEFT (or RIGHT), then the tree is
     * searched down the left (or right) side to find the first (or last) leaf,
     * respectively.  
     * <p>
     * Enters with parent latched, assuming it's not null.  Exits with the
     * return value latched, assuming it's not null.
     * <p>
     * @param parent - the root of the subtree to start the search at.  This
     * node should be latched by the caller and will be unlatched prior to
     * return.
     * 
     * @param key - the key to search for, unless searchType is LEFT or RIGHT
     *
     * @param searchType - NORMAL means search using key and, optionally, nid.
     *                     LEFT means find the first (leftmost) leaf
     *                     RIGHT means find the last (rightmost) leaf
     *
     * @param nid - The nodeid to search for in the tree.  If found, returns
     * its parent.  If the nodeid of the root is passed, null is returned.
     * Pass -1 if no nodeid based search is desired.
     *
     * @return - the node matching the argument criteria, or null.  The node is
     * latched and must be unlatched by the caller.  The parent argument and
     * any other nodes that are latched during the search are unlatched prior
     * to return.
     *
     * @throws RelatchRequiredException if the root node (parent) must be
     * relatched exclusively because a null target was encountered (i.e. a
     * fetch must be performed on parent's child and the parent is latched
     * shared.
     */
    public IN searchSubTreeInternal(IN parent,
				    byte[] key,
				    SearchType searchType,
				    long nid,
				    BINBoundary binBoundary,
				    boolean updateGeneration)
        throws DatabaseException {

        /* Return null if we're passed a null arg. */
        if (parent == null) {
            return null;
        }

        if ((searchType == SearchType.LEFT ||
             searchType == SearchType.RIGHT) &&
            key != null) {

            /* 
	     * If caller is asking for a right or left search, they shouldn't
	     * be passing us a key.
	     */
            throw new IllegalArgumentException
                ("searchSubTree passed key and left/right search");
        }

        assert parent.isLatchOwnerForRead();

        if (parent.getNodeId() == nid) {
            parent.releaseLatch();
            return null;
        }

        if (binBoundary != null) {
            binBoundary.isLastBin = true;
            binBoundary.isFirstBin = true;
        }

        int index;
        IN child = null;
	IN grandParent = null;
	boolean childIsLatched = false;
	boolean grandParentIsLatched = false;
	boolean maintainGrandParentLatches = !parent.isLatchOwnerForWrite();

        TreeWalkerStatsAccumulator treeStatsAccumulator =
            getTreeStatsAccumulator();

        try {
            do {
                if (treeStatsAccumulator != null) {
                    parent.accumulateStats(treeStatsAccumulator);
                }

                if (parent.getNEntries() == 0) {
                    /* No more children, can't descend anymore. */
                    return parent;
                } else if (searchType == SearchType.NORMAL) {
                    /* Look for the entry matching key in the current node. */
                    index = parent.findEntry(key, false, false);
                } else if (searchType == SearchType.LEFT) {
                    /* Left search, always take the 0th entry. */
                    index = 0;
                } else if (searchType == SearchType.RIGHT) {
                    /* Right search, always take the highest entry. */
                    index = parent.getNEntries() - 1;
                } else {
                    throw new IllegalArgumentException
                        ("Invalid value of searchType: " + searchType);
                }

                assert index >= 0;

                if (binBoundary != null) {
                    if (index != parent.getNEntries() - 1) {
                        binBoundary.isLastBin = false;
                    }
                    if (index != 0) {
                        binBoundary.isFirstBin = false;
                    }
                }

		/*
		 * Get the child node.  If target is null, and we don't have
		 * parent latched exclusively, then we need to relatch this
		 * parent so that we can fill in the target.  Fetching a target
		 * is a write to a node so it must be exclusively latched.
		 * Once we have the parent relatched exclusively, then we can
		 * release the grand parent.
		 */
		if (maintainGrandParentLatches &&
		    parent.getTarget(index) == null &&
		    !parent.isAlwaysLatchedExclusively()) {

		    if (grandParent == null) {

			/* 
			 * grandParent is null which implies parent is the root
			 * so throw RelatchRequiredException.
			 */
			throw relatchRequiredException;
		    } else {
			/* Release parent shared and relatch exclusive. */
			parent.releaseLatch();
			parent.latch();
		    }

		    /*
		     * Once parent has been re-latched exclusive we can release
		     * grandParent now (sooner), rather than after the
		     * fetchTarget (later).
		     */
		    if (grandParent != null) {
			grandParent.releaseLatch();
			grandParentIsLatched = false;
			grandParent = null;
		    }
		}
                child = (IN) parent.fetchTarget(index);

		/*
		 * We know we're done with grandParent for sure, so release
		 * now.
		 */
		if (grandParent != null) {
		    grandParent.releaseLatch();
		    grandParentIsLatched = false;
		}

		/* See if we're even using shared latches. */
		if (maintainGrandParentLatches) {
		    child.latchShared(updateGeneration);
		} else {
		    child.latch(updateGeneration);
		}
		childIsLatched = true;

                if (treeStatsAccumulator != null) {
                    child.accumulateStats(treeStatsAccumulator);
                }

                /* 
                 * If this child matches nid, then stop the search and return
                 * the parent.
                 */
                if (child.getNodeId() == nid) {
                    child.releaseLatch();
		    childIsLatched = false;
                    return parent;
                }

                /* Continue down a level */
		if (maintainGrandParentLatches) {
		    grandParent = parent;
		    grandParentIsLatched = true;
		} else {
		    parent.releaseLatch();
		}
                parent = child;
            } while (!(parent instanceof BIN));

            return child;
        } catch (Exception t) {

            /*
             * In [#14903] we encountered a latch exception below and the
             * original exception t was lost.  Print the stack trace and
             * rethrow the original exception if this happens again, to get
             * more information about the problem.
             */
            try {
                if (child != null &&
                    childIsLatched) {
                    child.releaseLatchIfOwner();
                }

                if (parent != child) {
                    parent.releaseLatchIfOwner();
                }
            } catch (Exception t2) {
                t2.printStackTrace();
            }

            if (t instanceof DatabaseException) {
                /* don't re-wrap a DatabaseException; we may need its type. */
                throw (DatabaseException) t;
            } else {
                throw new DatabaseException(t);
            }
        } finally {
            if (grandParent != null &&
		grandParentIsLatched) {
                grandParent.releaseLatch();
		grandParentIsLatched = false;
            }
	}
    }

    /**
     * Search down the tree using a key, but instead of returning the BIN that
     * houses that key, find the point where we can detach a deletable
     * subtree. A deletable subtree is a branch where each IN has one child,
     * and the bottom BIN has no entries and no resident cursors. That point
     * can be found by saving a pointer to the lowest node in the path with
     * more than one entry.
     *
     *              INa
     *             /   \
     *          INb    INc
     *          |       |
     *         INd     ..
     *         / \
     *      INe  ..
     *       |
     *     BINx (suspected of being empty)
     *
     * In this case, we'd like to prune off the subtree headed by INe. INd
     * is the parent of this deletable subtree. As we descend, we must keep
     * latches for all the nodes that will be logged. In this case, we
     * will need to keep INa, INb and INd latched when we return from this
     * method.
     *
     * The method returns a list of parent/child/index structures. In this
     * example, the list will hold:
     *  INa/INb/index
     *  INb/INd/index
     *  INd/INe/index
     * Every node is latched, and every node except for the bottom most child
     * (INe) must be logged. 
     */
    public void searchDeletableSubTree(IN parent,
                                       byte[] key,
                                       ArrayList nodeLadder)
        throws DatabaseException,
               NodeNotEmptyException,
               CursorsExistException {

        assert (parent!=null);
        assert (key!= null);
        assert parent.isLatchOwnerForWrite();

        int index;
        IN child = null;

        /* Save the lowest IN in the path that has multiple entries. */
        IN lowestMultipleEntryIN = null;

        do {
            if (parent.getNEntries() == 0) {
                break;
            }
 
            /* Remember if this is the lowest multiple point. */
            if (parent.getNEntries() > 1) {
                lowestMultipleEntryIN = parent;
            }

            index = parent.findEntry(key, false, false);
            assert index >= 0;

            /* Get the child node that matches. */
            child = (IN) parent.fetchTarget(index);
            child.latch(false);
            nodeLadder.add(new SplitInfo(parent, child, index));

            /* Continue down a level */
            parent = child;
        } while (!(parent instanceof BIN));

        /*
         * See if there is a reason we can't delete this BIN -- i.e.
         * new items have been inserted, or a cursor exists on it.
         */
        if ((child != null) && (child instanceof BIN)) {
            if (child.getNEntries() != 0) {
                throw NodeNotEmptyException.NODE_NOT_EMPTY;
            }

            /* 
             * This case can happen if we are keeping a BIN on an empty
             * cursor as we traverse.
             */
            if (((BIN) child).nCursors() > 0) {
                throw CursorsExistException.CURSORS_EXIST;
            }
        }
            
        if (lowestMultipleEntryIN != null) {

            /* 
             * Release all nodes up to the pair that holds the detach 
             * point. We won't be needing those nodes, since they'll be 
             * pruned and won't need to be updated.
             */
            ListIterator iter = nodeLadder.listIterator(nodeLadder.size());
            while (iter.hasPrevious()) {
                SplitInfo info = (SplitInfo) iter.previous();
                if (info.parent == lowestMultipleEntryIN) {
                    break;
                } else {
                    info.child.releaseLatch();
                    iter.remove();
                }
            }
        } else {

            /* 
             * We actually have to prune off the entire tree. Release
             * all latches, and clear the node ladder. 
             */
            releaseNodeLadderLatches(nodeLadder);
            nodeLadder.clear();
        }
    }

    /**
     * Search the portion of the tree starting at the parent, permitting 
     * preemptive splits. 
     */
    private IN searchSubTreeSplitsAllowed(IN parent,
                                          byte[] key,
                                          long nid,
                                          boolean updateGeneration)
        throws DatabaseException, SplitRequiredException {

        if (parent != null) {

            /*
             * Search downward until we hit a node that needs a split. In
             * that case, retreat to the top of the tree and force splits
             * downward.
             */
            while (true) {
                try {
                    return searchSubTreeUntilSplit
                        (parent, key, nid, updateGeneration);
                } catch (SplitRequiredException e) {
                    /* SR [#11144]*/
                    if (waitHook != null) {
                        waitHook.doHook();
                    }

                    /* 
                     * ForceSplit may itself throw SplitRequiredException if it
                     * finds that the parent doesn't have room to hold an extra
                     * entry. Allow the exception to propagate up to a place
                     * where it's safe to split the parent. We do this rather
                     * than
                     */
                    forceSplit(parent, key);
                }
            }
        } else {
            return null;
        }
    }

    /**
     * Search the subtree, but throw an exception when we see a node 
     * that has to be split.
     */
    private IN searchSubTreeUntilSplit(IN parent,
                                       byte[] key,
                                       long nid,
                                       boolean updateGeneration)
        throws DatabaseException, SplitRequiredException {

        /* Return null if we're passed a null arg. */
        if (parent == null) {
            return null;
        }

        assert parent.isLatchOwnerForWrite();

        if (parent.getNodeId() == nid) {
            parent.releaseLatch();
            return null;
        }

        int index;
        IN child = null;
	boolean childIsLatched = false;
	boolean success = false;

        try {
            do {
                if (parent.getNEntries() == 0) {
                    /* No more children, can't descend anymore. */
		    success = true;
                    return parent;
                } else {
                    /* Look for the entry matching key in the current node. */
                    index = parent.findEntry(key, false, false);
                }

                assert index >= 0;

                /* Get the child node that matches. */
		child = (IN) parent.fetchTarget(index);
                child.latch(updateGeneration);
		childIsLatched = true;

                /* Throw if we need to split. */
                if (child.needsSplitting()) {
                    child.releaseLatch();
		    childIsLatched = false;
                    parent.releaseLatch();
		    success = true;
                    throw splitRequiredException;
                }

                /* 
                 * If this child matches nid, then stop the search and return
                 * the parent.
                 */
                if (child.getNodeId() == nid) {
                    child.releaseLatch();
		    childIsLatched = false;
		    success = true;
                    return parent;
                }

                /* Continue down a level */
                parent.releaseLatch();
                parent = child;
            } while (!(parent instanceof BIN));

	    success = true;
            return parent;
        } finally {
	    if (!success) {
		if (child != null &&
		    childIsLatched) {
		    child.releaseLatchIfOwner();
		}
		if (parent != child) {
		    parent.releaseLatchIfOwner();
		}
	    }
        }
    }

    /**
     * Do pre-emptive splitting in the subtree topped by the "parent" node.
     * Search down the tree until we get to the BIN level, and split any nodes
     * that fit the splittable requirement.
     * 
     * Note that more than one node in the path may be splittable. For example,
     * a tree might have a level2 IN and a BIN that are both splittable, and 
     * would be encountered by the same insert operation.
     */
    private void forceSplit(IN parent,
                            byte[] key)
        throws DatabaseException, SplitRequiredException {

        ArrayList nodeLadder = new ArrayList();

	boolean allLeftSideDescent = true;
	boolean allRightSideDescent = true;
        int index;
        IN child = null;
        IN originalParent = parent;
        ListIterator iter = null;

        boolean isRootLatched = false;
        boolean success = false;
        try {

            /*
             * Latch the root in order to update the root LSN when we're done.
             * Latch order must be: root, root IN.  We'll leave this method
             * with the original parent latched.
             */
            if (originalParent.isDbRoot()) {
                rootLatch.acquireExclusive();
                isRootLatched = true;
            }
            originalParent.latch();

            /* 
             * Another thread may have crept in and 
             *  - used the last free slot in the parent, making it impossible 
             *    to correctly progagate the split. 
             *  - actually split the root, in which case we may be looking at 
             *    the wrong subtree for this search. 
             * If so, throw and retry from above. SR [#11144]
             */
            if (originalParent.needsSplitting() || !originalParent.isRoot()) {
                throw splitRequiredException;
            }

            /* 
             * Search downward to the BIN level, saving the information
             * needed to do a split if necessary.
             */
            do {
                if (parent.getNEntries() == 0) {
                    /* No more children, can't descend anymore. */
                    break;
                } else {
                    /* Look for the entry matching key in the current node. */
                    index = parent.findEntry(key, false, false);
                    if (index != 0) {
                        allLeftSideDescent = false;
                    }
                    if (index != (parent.getNEntries() - 1)) {
                        allRightSideDescent = false;
                    }
                }

                assert index >= 0;

                /* 
                 * Get the child node that matches. We only need to work on
                 * nodes in residence.
                 */
                child = (IN) parent.getTarget(index);
                if (child == null) {
                    break;
                } else {
                    child.latch();
                    nodeLadder.add(new SplitInfo(parent, child, index));
                } 

                /* Continue down a level */
                parent = child;
            } while (!(parent instanceof BIN));

            boolean startedSplits = false;
            LogManager logManager =
                database.getDbEnvironment().getLogManager();

            /* 
             * Process the accumulated nodes from the bottom up. Split each
             * node if required. If the node should not split, we check if
             * there have been any splits on the ladder yet. If there are none,
             * we merely release the node, since there is no update.  If splits
             * have started, we need to propagate new LSNs upward, so we log
             * the node and update its parent.
             *
             * Start this iterator at the end of the list.
             */
            iter = nodeLadder.listIterator(nodeLadder.size());
            long lastParentForSplit = -1;
            while (iter.hasPrevious()) {
                SplitInfo info = (SplitInfo) iter.previous();
                child = info.child;
                parent = info.parent;
                index = info.index;

                /* Opportunistically split the node if it is full. */
                if (child.needsSplitting()) {
		    int maxEntriesPerNode = (child.containsDuplicates() ?
					     maxDupTreeEntriesPerNode :
					     maxMainTreeEntriesPerNode);
                    if (allLeftSideDescent || allRightSideDescent) {
                        child.splitSpecial(parent,
                                           index,
                                           maxEntriesPerNode,
                                           key,
                                           allLeftSideDescent);
                    } else {
                        child.split(parent, index, maxEntriesPerNode);
                    }
                    lastParentForSplit = parent.getNodeId();
                    startedSplits = true;

                    /*
                     * If the DB root IN was logged, update the DB tree's child
                     * reference.  Now the MapLN is logically dirty, but the
                     * change hasn't been logged. Set the rootIN to be dirty
                     * again, to force flushing the rootIN and mapLN in the 
                     * next checkpoint. Be sure to flush the MapLN
                     * if we ever evict the root.
                     */
                    if (parent.isDbRoot()) {
                        assert isRootLatched;
                        root.setLsn(parent.getLastFullVersion());
                        parent.setDirty(true);
                    }
                } else {
                    if (startedSplits) {
                        long newLsn = 0;

                        /* 
                         * If this child was the parent of a split, it's
                         * already logged by the split call. We just need to
                         * propagate the logging upwards. If this child is just
                         * a link in the chain upwards, log it.
                         */
                        if (lastParentForSplit == child.getNodeId()) {
                            newLsn = child.getLastFullVersion();
                        } else {
                            newLsn = child.optionalLog(logManager);
                        }
                        parent.updateEntry(index, newLsn);
                    } 
                }
                child.releaseLatch();
                child = null;
                iter.remove();
            }

            success = true;
        } finally {

            if (!success) {
                if (child != null) {
                    child.releaseLatchIfOwner();
                }
                originalParent.releaseLatchIfOwner();
            }

            /*
             * Unlatch any remaining children. There should only be remainders
             * in the event of an exception.
             */
            if (nodeLadder.size() > 0) {
                iter = nodeLadder.listIterator(nodeLadder.size());
                while (iter.hasPrevious()) {
                    SplitInfo info = (SplitInfo) iter.previous();
                    info.child.releaseLatchIfOwner();
                }
            }

            if (isRootLatched) {
                rootLatch.release();
            }
        }
    }

    /**
     * Helper to obtain the root IN with shared root latching.  Optionally
     * updates the generation of the root when latching it.
     */
    public IN getRootIN(boolean updateGeneration) 
        throws DatabaseException {

	return getRootINInternal(updateGeneration, false);
    }

    /**
     * Helper to obtain the root IN with exclusive root latching.  Optionally
     * updates the generation of the root when latching it.
     */
    public IN getRootINLatchedExclusive(boolean updateGeneration) 
        throws DatabaseException {

	return getRootINInternal(updateGeneration, true);
    }

    private IN getRootINInternal(boolean updateGeneration, boolean exclusive)
        throws DatabaseException {

	rootLatch.acquireShared();
        IN rootIN = null;
        try {
            if (rootExists()) {
                rootIN = (IN) root.fetchTarget(database, null);
		if (exclusive) {
		    rootIN.latch(updateGeneration);
		} else {
		    rootIN.latchShared(updateGeneration);
		}
            }
            return rootIN;
        } finally {
	    rootLatch.release();
        }
    }

    /**
     * Inserts a new LN into the tree.
     * @param ln The LN to insert into the tree.
     * @param key Key value for the node
     * @param allowDuplicates whether to allow duplicates to be inserted
     * @param cursor the cursor to update to point to the newly inserted
     * key/data pair, or null if no cursor should be updated.
     * @return true if LN was inserted, false if it was a duplicate
     * duplicate or if an attempt was made to insert a duplicate when
     * allowDuplicates was false.
     */
    public boolean insert(LN ln,
                          byte[] key,
                          boolean allowDuplicates,
                          CursorImpl cursor,
			  LockResult lnLock)
        throws DatabaseException {

        validateInsertArgs(allowDuplicates);

        EnvironmentImpl env = database.getDbEnvironment();
        LogManager logManager = env.getLogManager();
        INList inMemoryINs = env.getInMemoryINs();

        /* Find and latch the relevant BIN. */
        BIN bin = null;
        try {
            bin = findBinForInsert(key, logManager, inMemoryINs, cursor);
            assert bin.isLatchOwnerForWrite();
            
            /* Make a child reference as a candidate for insertion. */
            ChildReference newLNRef =
		new ChildReference(ln, key, DbLsn.NULL_LSN);

	    /*
	     * If we're doing a put that is not a putCurrent, then the cursor
	     * passed in may not be pointing to BIN (it was set to the BIN that
	     * the search landed on which may be different than BIN).  Set the
	     * BIN correctly here so that adjustCursorsForInsert doesn't blow
	     * an assertion.  We'll finish the job by setting the index below.
	     */
	    cursor.setBIN(bin);

            int index = bin.insertEntry1(newLNRef);
            if ((index & IN.INSERT_SUCCESS) != 0) {

                /* 
                 * Update the cursor to point to the entry that has been
                 * successfully inserted.
                 */
                index &= ~IN.INSERT_SUCCESS;
		cursor.updateBin(bin, index);

                /* Log the new LN. */
                long newLsn = DbLsn.NULL_LSN;

		try {
		    newLsn = ln.optionalLog
                        (env, database, key, DbLsn.NULL_LSN, 0,
                         cursor.getLocker());
		} finally {
                    if ((newLsn == DbLsn.NULL_LSN) &&
                	!database.isDeferredWrite()) {

                        /*
                         * Possible buffer overflow, out-of-memory, or I/O
                         * exception during logging.  The BIN entry will
                         * contain a NULL_LSN.  To prevent an exception during
                         * a fetch, we set the KnownDeleted flag.  We do not
                         * call BIN.deleteEntry because cursors will not be
                         * adjusted.  We do not add this entry to the
                         * compressor queue to avoid complexity (this is rare).
                         * [13126, 12605, 11271]
                         */
                        bin.setKnownDeleted(index);
                    }
		}
		lnLock.setAbortLsn(DbLsn.NULL_LSN, true, true);
                bin.updateEntry(index, newLsn);

                traceInsert(Level.FINER, env, bin, ln, newLsn, index);
                return true;
            } else {

                /* 
		 * Entry may have been a duplicate. Insertion was not
		 * successful.
		 */
                index &= ~IN.EXACT_MATCH;
		cursor.updateBin(bin, index);

                LN currentLN = null;
		boolean isDup = false;
		Node n = bin.fetchTarget(index);
		if (n == null || n instanceof LN) {
		    currentLN = (LN) n;
		} else {
                    isDup = true;
		}

                /* If an LN is present, lock it and check deleted-ness. */
		boolean isDeleted = false;
                LockResult currentLock = null;

                if (!isDup) {
                    if (currentLN == null) {
                        /* The LN was cleaned. */
                        isDeleted = true;
                    } else {
                        currentLock = cursor.lockLNDeletedAllowed
                            (currentLN, LockType.WRITE);
                        currentLN = currentLock.getLN();
                        /* The BIN/index may have changed while locking. */
                        bin = cursor.getBIN();
                        index = cursor.getIndex();
                        if (cursor.getDupBIN() != null) {

                            /*
                             * A dup tree appeared during locking.  We will
                             * position to a different dup tree entry later in
                             * insertDuplicate, so we must remove the cursor
                             * from this dup tree entry.  This is a rare case
                             * so performance is not an issue.
                             */
                            cursor.clearDupBIN(true /*alreadyLatched*/);
                            isDup = true;
                        } else if (bin.isEntryKnownDeleted(index) ||
                                   currentLN == null ||
                                   currentLN.isDeleted()) {
                            /* The current LN is deleted/cleaned. */
                            isDeleted = true;
                        }
                    }
                }

                if (isDeleted) {

                    /*
                     * Set the abort LSN to that of the lock held on the
                     * current LN, if the current LN was previously locked by
                     * this txn.  This is needed when we change the node ID of
                     * this slot.
                     *
                     * If reusing a slot with a deleted LN deleted in a prior
                     * transaction (the LockGrantType is NEW or UPGRADE),
                     * always set abortKnownDeleted=true.  It may be that the
                     * existing slot is PENDING_DELETED, but we restore to
                     * KNOWN_DELETED in the event of an abort.
                     */
                    long abortLsn = bin.getLsn(index);
                    boolean abortKnownDeleted = true;
                    if (currentLN != null &&
                        currentLock.getLockGrant() == LockGrantType.EXISTING) {
                        long nodeId = currentLN.getNodeId();
                        Locker locker = cursor.getLocker();
			WriteLockInfo info = locker.getWriteLockInfo(nodeId);
			abortLsn = info.getAbortLsn();
			abortKnownDeleted = info.getAbortKnownDeleted();
                    }
		    lnLock.setAbortLsn(abortLsn, abortKnownDeleted);

                    /*
                     * Current entry is a deleted entry. Replace it with LN.
                     * Pass NULL_LSN for the oldLsn parameter of the log()
                     * method because the old LN was counted obsolete when it
                     * was deleted.
                     */
                    long newLsn = ln.optionalLog(env, database,
					 key, DbLsn.NULL_LSN, 0,
					 cursor.getLocker());

                    bin.updateEntry(index, ln, newLsn, key);
                    bin.clearKnownDeleted(index);
                    bin.clearPendingDeleted(index);

                    traceInsert(Level.FINER, env, bin, ln, newLsn, index);
                    return true;
                } else {

		    /* 
		     * Attempt to insert a duplicate in an exception dup tree
                     * or create a dup tree if none exists.
		     */		       
		    return insertDuplicate
                        (key, bin, ln, logManager, inMemoryINs, cursor, lnLock,
                         allowDuplicates);
                }
            }
        } finally {
            cursor.releaseBIN();
        }
    }

    /**
     * Attempts to insert a duplicate at the current cursor BIN position.  If
     * an existing dup tree exists, insert into it; otherwise, create a new
     * dup tree and place the new LN and the existing LN into it.  If the
     * current BIN entry contains an LN, the caller guarantees that it is not
     * deleted.
     *
     * @return true if duplicate inserted successfully, false if it was a
     * duplicate duplicate, false if a there is an existing LN and
     * allowDuplicates is false.
     */
    private boolean insertDuplicate(byte[] key,
				    BIN bin,
                                    LN newLN,
                                    LogManager logManager,
                                    INList inMemoryINs,
                                    CursorImpl cursor,
				    LockResult lnLock,
                                    boolean allowDuplicates)
        throws DatabaseException {

        EnvironmentImpl env = database.getDbEnvironment();
	int index = cursor.getIndex();
        boolean successfulInsert = false;

        DIN dupRoot = null;
        Node n = bin.fetchTarget(index);
	long binNid = bin.getNodeId();

        if (n instanceof DIN) {
            DBIN dupBin = null;

            /*
             * A duplicate tree exists.  Find the relevant DBIN and insert the
             * new entry into it.
             */
            try {
                dupRoot = (DIN) n;
                dupRoot.latch();

                /* Lock the DupCountLN before logging any LNs. */
                LockResult dclLockResult =
                    cursor.lockDupCountLN(dupRoot, LockType.WRITE);
                /* The BIN/index may have changed during locking. */
                bin = cursor.getBIN();
                index = cursor.getIndex();

                /*
                 * Do not proceed if duplicates are not allowed and there are
                 * one or more duplicates already present.  Note that if the
                 * dup count is zero, we allow the insert.
                 */
                if (!allowDuplicates) {
                    DupCountLN dcl = (DupCountLN) dclLockResult.getLN();
                    if (dcl.getDupCount() > 0) {
                        return false;
                    }
                }

                /*
                 * Split the dup root if necessary.  The dup root may have
                 * changed during locking above or by the split, so refetch it.
                 * In either case it will be latched.
                 */
                maybeSplitDuplicateRoot(bin, index);
                dupRoot = (DIN) bin.fetchTarget(index);

                /* 
                 * Search the duplicate tree for the right place to insert this
                 * new record. Releases the latch on duplicateRoot. If the
                 * duplicateRoot got logged as a result of some splitting,
                 * update the BIN's LSN information. The SortedLSNTreeWalker
                 * relies on accurate LSNs in the in-memory tree.
                 */
                byte[] newLNKey = newLN.getData();
                long previousLsn = dupRoot.getLastFullVersion();
                try {
                    dupBin = (DBIN) searchSubTreeSplitsAllowed
                        (dupRoot, newLNKey, -1, true /*updateGeneration*/);
                } catch (SplitRequiredException e) {

                    /* 
                     * Shouldn't happen -- we have the DIN in the root of the
                     * dup tree latched during this insert, so there should be
                     * no possibility of being unable to insert a new entry
                     * into the DIN root of the dup tree.
                     */
                    throw new DatabaseException(e) ;
                }

                long currentLsn = dupRoot.getLastFullVersion();
                if (currentLsn != previousLsn) {
                    bin.updateEntry(index, currentLsn);
                }

                /* Release the BIN latch to increase concurrency. */
                cursor.releaseBIN();
                bin = null;

                /* The search above released the dup root latch. */
                dupRoot = null;

                /* 
                 * Try to insert a new reference object. If successful, we'll
                 * log the LN and update the LSN in the reference.
                 */
                ChildReference newLNRef = 
                    new ChildReference(newLN, newLNKey, DbLsn.NULL_LSN);
                                       
                int dupIndex = dupBin.insertEntry1(newLNRef);
                if ((dupIndex & IN.INSERT_SUCCESS) != 0) {

                    /* 
                     * Update the cursor to point to the entry that has been
                     * successfully inserted.
                     */
		    dupIndex &= ~IN.INSERT_SUCCESS;
		    cursor.updateDBin(dupBin, dupIndex);

                    /* Log the new LN. */
                    long newLsn = DbLsn.NULL_LSN;
		    try {
			newLsn = newLN.optionalLog
                            (env, database, key, DbLsn.NULL_LSN, 0,
                             cursor.getLocker());
                    } finally {
                        if ((newLsn == DbLsn.NULL_LSN) &&
                            (!database.isDeferredWrite())) {

                            /* 
                             * See Tree.insert for an explanation of handling
                             * of IOException and OOME.
                             */
                            dupBin.setKnownDeleted(dupIndex);
                        }
		    }

		    lnLock.setAbortLsn(DbLsn.NULL_LSN, true, true);

		    /* 
                     * Use updateEntry to be sure to mark the dupBin as dirty. 
                     */
		    dupBin.updateEntry(dupIndex, newLsn);

                    traceInsertDuplicate(Level.FINER,
                                         database.getDbEnvironment(),
                                         dupBin, newLN, newLsn, binNid);
                    successfulInsert = true;
                } else {

                    /* 
                     * The insert was not successful. Either this is a
                     * duplicate duplicate or there is an existing entry but
                     * that entry is deleted.
                     */
                    dupIndex &= ~IN.EXACT_MATCH;
		    cursor.updateDBin(dupBin, dupIndex);
                    LN currentLN = (LN) dupBin.fetchTarget(dupIndex);

                    /* If an LN is present, lock it and check deleted-ness. */
                    boolean isDeleted = false;
                    LockResult currentLock = null;
                    if (currentLN == null) {
                        /* The LN was cleaned. */
                        isDeleted = true;
                    } else {
                        currentLock = cursor.lockLNDeletedAllowed
                            (currentLN, LockType.WRITE);
                        currentLN = currentLock.getLN();

                        /*
                         * The BIN may have been latched while locking above.
                         * Release the latch here because we released it above
                         * to improve concurrency, and we will latch it again
                         * below to increment the duplicate count. [#15574]
                         */
                        cursor.releaseBIN();

                        /* The DBIN/index may have changed while locking. */
                        dupBin = cursor.getDupBIN();
			dupIndex = cursor.getDupIndex();
                        if (dupBin.isEntryKnownDeleted(dupIndex) ||
                            currentLN == null ||
                            currentLN.isDeleted()) {
                            /* The current LN is deleted/cleaned. */
                            isDeleted = true;
                        }
                    }

                    if (isDeleted) {
                        /* See Tree.insert for an explanation. */
                        long abortLsn = dupBin.getLsn(dupIndex);
                        boolean abortKnownDeleted = true;
                        if (currentLN != null &&
                            currentLock.getLockGrant() ==
                            LockGrantType.EXISTING) {
                            long nodeId = currentLN.getNodeId();
                            Locker locker = cursor.getLocker();
			    WriteLockInfo info =
				locker.getWriteLockInfo(nodeId);
			    abortLsn = info.getAbortLsn();
			    abortKnownDeleted = info.getAbortKnownDeleted();
			}
			lnLock.setAbortLsn(abortLsn, abortKnownDeleted);

                        /*
                         * Current entry is a deleted entry. Replace it with
                         * LN.  Pass NULL_LSN for the oldLsn parameter of the
                         * log() method because the old LN was counted obsolete
                         * when it was deleted.
                         */
                        long newLsn =
			    newLN.optionalLog(env, database, key,
				      DbLsn.NULL_LSN, 0, cursor.getLocker());

                        dupBin.updateEntry(dupIndex, newLN, newLsn, newLNKey);
                        dupBin.clearKnownDeleted(dupIndex);
                        dupBin.clearPendingDeleted(dupIndex);

                        traceInsertDuplicate(Level.FINER,
                                             database.getDbEnvironment(),
                                             dupBin, newLN, newLsn, binNid);
                        successfulInsert = true;
                    } else {
                        /* Duplicate duplicate. */
                        successfulInsert = false;
                    }
                }

                /*
                 * To avoid latching out of order (up the tree), release the
                 * DBIN latch before latching the BIN and dup root.
                 */
                dupBin.releaseLatch();
                dupBin = null;

		if (successfulInsert) {
                    cursor.latchBIN();
                    dupRoot =
                        cursor.getLatchedDupRoot(false /*isDBINLatched*/);
                    cursor.releaseBIN();
                    dupRoot.incrementDuplicateCount
                        (dclLockResult, key, cursor.getLocker(),
                         true /*increment*/);
		}
            } finally {
                if (dupBin != null) {
                    dupBin.releaseLatchIfOwner();
                }
		
                if (dupRoot != null) {
                    dupRoot.releaseLatchIfOwner();
                }
            }
        } else if (n instanceof LN) {

            /*
             * There is no duplicate tree yet.  The existing LN is guaranteed
             * to be non-deleted, so to insert we must create a dup tree.
             */
            if (!allowDuplicates) {
                return false;
            }

            /*
             * Mutate the current BIN/LN pair into a BIN/DupCountLN/DIN/DBIN/LN
             * duplicate tree.  Log the new entries.
             */
            try {
		lnLock.setAbortLsn(DbLsn.NULL_LSN, true, true);
                dupRoot = createDuplicateTree
                    (key, logManager, inMemoryINs, newLN, cursor);
            } finally {
                if (dupRoot != null) {
                    dupRoot.releaseLatch();
                    successfulInsert = true;
                } else {
                    successfulInsert = false;
                }
            }
        } else {
            throw new InconsistentNodeException
                ("neither LN or DIN found in BIN");
        }

        return successfulInsert;
    }

    /**
     * Check if the duplicate root needs to be split.  The current duplicate
     * root is latched.  Exit with the new root (even if it's unchanged)
     * latched and the old root (unless the root is unchanged) unlatched.
     * 
     * @param bin the BIN containing the duplicate root.
     * @param index the index of the duplicate root in bin.
     * @return true if the duplicate root was split.
     */
    private boolean maybeSplitDuplicateRoot(BIN bin,
                                            int index)
        throws DatabaseException {

        DIN curRoot = (DIN) bin.fetchTarget(index);

        if (curRoot.needsSplitting()) {

            EnvironmentImpl env = database.getDbEnvironment();
            LogManager logManager = env.getLogManager();
            INList inMemoryINs = env.getInMemoryINs();

            /* 
             * Make a new root DIN, giving it an id key from the previous root.
             */
            byte[] rootIdKey = curRoot.getKey(0);
            DIN newRoot = new DIN(database,
                                  rootIdKey,
                                  maxDupTreeEntriesPerNode,
                                  curRoot.getDupKey(),
                                  curRoot.getDupCountLNRef(),
                                  curRoot.getLevel() + 1);

            newRoot.latch();
            long curRootLsn = 0;
            long logLsn = 0;
            try {
                newRoot.setIsRoot(true);
                curRoot.setDupCountLN(null);
                curRoot.setIsRoot(false);

                /* 
                 * Make the new root DIN point to the old root DIN, and then
                 * log. We should be able to insert into the root because the
                 * root is newly created.
                 */
                try {
                    curRootLsn =
                        curRoot.optionalLogProvisional(logManager, newRoot);
                    boolean insertOk = newRoot.insertEntry
                        (new ChildReference(curRoot, rootIdKey,
                                            bin.getLsn(index)));
                    assert insertOk;

                    logLsn = newRoot.optionalLog(logManager);
                } catch (DatabaseException e) {

                    /* Something went wrong when we tried to log. */
                    curRoot.setIsRoot(true);
                    throw e;
                }

                inMemoryINs.add(newRoot);
                bin.updateEntry(index, newRoot, logLsn);
                curRoot.split(newRoot, 0, maxDupTreeEntriesPerNode);
            } finally {
                curRoot.releaseLatch();
            }
            traceSplitRoot(Level.FINE, TRACE_DUP_ROOT_SPLIT,
			   newRoot, logLsn, curRoot, curRootLsn);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Convert an existing BIN entry from a single (non-duplicate) LN to a new
     * DIN/DupCountLN->DBIN->LN subtree.
     *
     * @param key the key of the entry which will become the duplicate key
     * for the duplicate subtree.
     * @param logManager the logManager
     * @param inMemoryINs the in memory IN list
     * @param newLN the new record to be inserted
     * @param cursor points to the target position for this new dup tree.
     * @return the new duplicate subtree root (a DIN).  It is latched
     * when it is returned and the caller should unlatch it.  If new entry
     * to be inserted is a duplicate of the existing LN, null is returned.
     */
    private DIN createDuplicateTree(byte[] key,
                                    LogManager logManager,
                                    INList inMemoryINs,
                                    LN newLN,
                                    CursorImpl cursor)
        throws DatabaseException {

        EnvironmentImpl env = database.getDbEnvironment();
        DIN dupRoot = null;
        DBIN dupBin = null;
        BIN bin = cursor.getBIN();
        int index = cursor.getIndex();

        /*
         * fetchTarget returned an LN before this method was called, and we're
         * still latched, so the target should never be null here.
         */
        LN existingLN = (LN) bin.fetchTarget(index);
 	boolean existingLNIsDeleted = bin.isEntryKnownDeleted(index) ||
 	    existingLN.isDeleted();
        assert existingLN != null;

        byte[] existingKey = existingLN.getData();
        byte[] newLNKey = newLN.getData();

        /* Check for duplicate duplicates. */
        boolean keysEqual = Key.compareKeys
            (newLNKey, existingKey, database.getDuplicateComparator()) == 0;
        if (keysEqual) {
            return null;
        }

        /* 
         * Replace the existing LN with a duplicate tree. 
         * 
         * Once we create a dup tree, we don't revert back to the LN.  Create
         * a DupCountLN to hold the count for this dup tree. Since we don't
         * roll back the internal nodes of a duplicate tree, we need to create
         * a pre-transaction version of the DupCountLN. This version must hold
         * a count of either 0 or 1, depending on whether the current
         * transaction created the exising lN or not. If the former, the count
         * must roll back to 0, if the latter, the count must roll back to 1.
         *
         * Note that we are logging a sequence of nodes and must make sure the
         * log can be correctly recovered even if the entire sequence doesn't
         * make it to the log. We need to make all children provisional to the
         * DIN. This works:
         *
         * Entry 1: (provisional) DupCountLN (first version)
         * Entry 2: (provisional) DupBIN 
         * Entry 3: DIN
         * Entry 4: DupCountLN (second version, incorporating the new count.
         *           This can't be provisional because we need to possibly
         *            roll it back.)
         * Entry 5: new LN.
         * See [SR #10203] for a description of the bug that existed before
         * this change.
         */

        /* Create the first version of DupCountLN and log it. (Entry 1). */
        Locker locker = cursor.getLocker();
 	long nodeId = existingLN.getNodeId();
 
 	/*
 	 * If the existing entry is known to be deleted or was created by this
 	 * transaction, then the DCL should get rolled back to 0, not 1.
 	 * [13726].
 	 */
 	int startingCount =
 	    (locker.createdNode(nodeId) ||
 	     existingLNIsDeleted ||
 	     locker.getWriteLockInfo(nodeId).getAbortKnownDeleted()) ?
 	    0 : 1;

        DupCountLN dupCountLN = new DupCountLN(startingCount);
        long firstDupCountLNLsn =
            dupCountLN.optionalLogProvisional(env, database,
				      key, DbLsn.NULL_LSN, 0);
        int firstDupCountLNSize = dupCountLN.getLastLoggedSize();

        /* Make the duplicate root and DBIN. */
        dupRoot = new DIN(database,
                          existingKey,                   // idkey
                          maxDupTreeEntriesPerNode,
                          key,                           // dup key
                          new ChildReference
                          (dupCountLN, key, firstDupCountLNLsn),
                          2);                            // level
        dupRoot.latch();
        dupRoot.setIsRoot(true);

        dupBin = new DBIN(database,
                          existingKey,                   // idkey
                          maxDupTreeEntriesPerNode,
                          key,                           // dup key
                          1);                            // level
        dupBin.latch();

        /* 
         * Attach the existing LN child to the duplicate BIN. Since this is a
         * newly created BIN, insertEntry will be successful.
         */
        ChildReference newExistingLNRef = new ChildReference
            (existingLN, existingKey, bin.getLsn(index), bin.getState(index));

        boolean insertOk = dupBin.insertEntry(newExistingLNRef);
        assert insertOk;

        try {

            /* Entry 2: DBIN. */
            long dbinLsn = dupBin.optionalLogProvisional(logManager, dupRoot);
            inMemoryINs.add(dupBin);
        
            /* Attach the duplicate BIN to the duplicate IN root. */
            dupRoot.setEntry(0, dupBin, dupBin.getKey(0),
                             dbinLsn, dupBin.getState(0));

            /* Entry 3:  DIN */
            long dinLsn = dupRoot.optionalLog(logManager);
            inMemoryINs.add(dupRoot);

            /*
             * Now that the DIN is logged, we've created a duplicate tree that
             * holds the single, preexisting LN. We can safely create the non
             * provisional LNs that pertain to this insert -- the new LN and
             * the new DupCountLN.
             *
             * We request a lock while holding latches which is usually
             * forbidden, but safe in this case since we know it will be
             * immediately granted (we just created dupCountLN above).
             */
            LockResult lockResult = locker.lock
                (dupCountLN.getNodeId(), LockType.WRITE, false /*noWait*/,
                 database);
            lockResult.setAbortLsn(firstDupCountLNLsn, false);

            dupCountLN.setDupCount(2);
            long dupCountLsn = dupCountLN.optionalLog
                (env, database, key, firstDupCountLNLsn, firstDupCountLNSize,
                 locker);
            dupRoot.updateDupCountLNRef(dupCountLsn);
        
            /* Add the newly created LN. */
            long newLsn = newLN.optionalLog
                (env, database, key, DbLsn.NULL_LSN, 0, locker);
            int dupIndex = dupBin.insertEntry1
                (new ChildReference(newLN, newLNKey, newLsn));
            dupIndex &= ~IN.INSERT_SUCCESS;
            cursor.updateDBin(dupBin, dupIndex);

            /*
             * Adjust any cursors positioned on the mutated BIN entry to point
             * to the DBIN at the location of the entry we moved there.  The
             * index of the moved entry is 1 or 0, the XOR of the index of the
             * new entry.
             */
            bin.adjustCursorsForMutation(index, dupBin, dupIndex ^ 1, cursor);
            dupBin.releaseLatch();

            /* 
             * Update the "regular" BIN to point to the new duplicate tree
             * instead of the existing LN.  Clear the MIGRATE flag since it
             * applies only to the original LN.
             */
            bin.updateEntry(index, dupRoot, dinLsn);
            bin.setMigrate(index, false);

            traceMutate(Level.FINE, bin, existingLN, newLN, newLsn,
                        dupCountLN, dupCountLsn, dupRoot, dinLsn,
                        dupBin, dbinLsn);
        } catch (DatabaseException e) {

            /* 
             * Strictly speaking, not necessary to release latches, because if
             * we fail to log the entries, we just throw them away, but our
             * unit tests check for 0 latches held in the event of a logging
             * error.
             */
            dupBin.releaseLatchIfOwner();
            dupRoot.releaseLatchIfOwner();
            throw e;
        }
        return dupRoot;
    }

    /**
     * Validate args passed to insert.  Presently this just means making sure
     * that if they say duplicates are allowed that the database supports
     * duplicates.
     */
    private void validateInsertArgs(boolean allowDuplicates)
        throws DatabaseException {
        if (allowDuplicates && !database.getSortedDuplicates()) {
            throw new DatabaseException
                ("allowDuplicates passed to insert but database doesn't " +
                 "have allow duplicates set.");
        }
    }

    /**
     * Find the BIN that is relevant to the insert.  If the tree doesn't exist
     * yet, then create the first IN and BIN.
     * @return the BIN that was found or created and return it latched.
     */
    private BIN findBinForInsert(byte[] key,
                                 LogManager logManager,
                                 INList inMemoryINs,
                                 CursorImpl cursor)
        throws DatabaseException {

	BIN bin;

        /* First try using the BIN at the cursor position to avoid a search. */
        bin = cursor.latchBIN();
        if (bin != null) {
            if (!bin.needsSplitting() && bin.isKeyInBounds(key)) {
                return bin;
            } else {
                bin.releaseLatch();
            }
        }

	boolean rootLatchIsHeld = false;
        try {
	    long logLsn;

	    /* 
	     * We may have to try several times because of a small
	     * timing window, explained below.
	     */
	    while (true) {
		rootLatchIsHeld = true;
		rootLatch.acquireShared();
		if (!rootExists()) {
		    rootLatch.release();
		    rootLatch.acquireExclusive();
		    if (rootExists()) {
			rootLatch.release();
			rootLatchIsHeld = false;
			continue;
		    }

		    /* 
		     * This is an empty tree, either because it's brand new
		     * tree or because everything in it was deleted. Create an
		     * IN and a BIN.  We could latch the rootIN here, but
		     * there's no reason to since we're just creating the
		     * initial tree and we have the rootLatch held. Log the
		     * nodes as soon as they're created, but remember that
		     * referred-to children must come before any references to
		     * their LSNs.
		     */
                    /* First BIN in the tree, log provisionally right away. */
                    bin = new BIN(database, key, maxMainTreeEntriesPerNode, 1);
                    bin.latch();
                    logLsn = bin.optionalLogProvisional(logManager, null);

		    /* 
                     * Log the root right away. Leave the root dirty, because
                     * the MapLN is not being updated, and we want to avoid
                     * this scenario from [#13897], where the LN has no
                     * possible parent.
                     *  provisional BIN
                     *  root IN
                     *  checkpoint start
                     *  LN is logged
                     *  checkpoint end
                     *  BIN is dirtied, but is not part of checkpoint
                     */

		    IN rootIN =
			new IN(database, key, maxMainTreeEntriesPerNode, 2);

		    /* 
		     * OK to latch the root after a child BIN because it's
		     * during creation.
		     */
		    rootIN.latch();
		    rootIN.setIsRoot(true);

		    boolean insertOk = rootIN.insertEntry
			(new ChildReference(bin, key, logLsn));
		    assert insertOk;

                    logLsn = rootIN.optionalLog(logManager);
                    rootIN.setDirty(true);  /*force re-logging, see [#13897]*/

                    root = makeRootChildReference(rootIN,
                                                  new byte[0], 
                                                  logLsn);

		    rootIN.releaseLatch();

		    /* Add the new nodes to the in memory list. */
		    inMemoryINs.add(bin);
		    inMemoryINs.add(rootIN);
		    rootLatch.release();
		    rootLatchIsHeld = false;

		    break;
		} else {
		    rootLatch.release();
		    rootLatchIsHeld = false;

		    /* 
		     * There's a tree here, so search for where we should
		     * insert. However, note that a window exists after we
		     * release the root latch. We release the latch because the
		     * search method expects to take the latch. After the
		     * release and before search, the INCompressor may come in
		     * and delete the entire tree, so search may return with a
		     * null.
		     */
		    IN in = searchSplitsAllowed
                        (key, -1, true /*updateGeneration*/);
		    if (in == null) {
			/* The tree was deleted by the INCompressor. */
			continue;
		    } else {
			/* search() found a BIN where this key belongs. */
			bin = (BIN) in;
			break;
		    } 
		} 
	    }
        } finally {
	    if (rootLatchIsHeld) {
		rootLatch.release();
	    }
        }

        /* testing hook to insert item into log. */
        if (ckptHook != null) {
            ckptHook.doHook();
        }

        return bin;
    }

    /*
     * Given a subtree root (an IN), remove it and all of its children from the
     * in memory IN list. Also count removed nodes as obsolete and gather the
     * set of file summaries that should be logged. The tracker will be flushed
     * to the log later.
     */
    private void accountForSubtreeRemoval(INList inList,
                                          IN subtreeRoot,
                                          UtilizationTracker tracker)
        throws DatabaseException {

        inList.latchMajor(); 
        try {
            subtreeRoot.accountForSubtreeRemoval(inList, tracker);
        } finally {
            inList.releaseMajorLatch();
        }

        Tracer.trace(Level.FINE, database.getDbEnvironment(),
		     "SubtreeRemoval: subtreeRoot = " +
		     subtreeRoot.getNodeId());
    }

    /*
     * Logging support
     */

    /**
     * @see Loggable#getLogSize
     */
    public int getLogSize() {
        int size = LogUtils.getBooleanLogSize();  // root exists?
        if (root != null) {      
            size += root.getLogSize();
        }
        return size;
    }

    /**
     * @see Loggable#writeToLog
     */
    public void writeToLog(ByteBuffer logBuffer) {
        LogUtils.writeBoolean(logBuffer, (root != null));
        if (root != null) {
            root.writeToLog(logBuffer);
        }
    }

    /**
     * @see Loggable#readFromLog
     */
    public void readFromLog(ByteBuffer itemBuffer, byte entryTypeVersion) {
        boolean rootExists = LogUtils.readBoolean(itemBuffer);
        if (rootExists) {
            root = makeRootChildReference();
            root.readFromLog(itemBuffer, entryTypeVersion);
        }
    }

    /**
     * @see Loggable#dumpLog
     */
    public void dumpLog(StringBuffer sb, boolean verbose) {
        sb.append("<root>");
        if (root != null) {
            root.dumpLog(sb, verbose);
        }
        sb.append("</root>");
    }

    /**
     * @see Loggable#getTransactionId
     */
    public long getTransactionId() {
	return 0;
    }

    /** 
     * rebuildINList is used by recovery to add all the resident nodes to the
     * IN list.
     */
    public void rebuildINList()
        throws DatabaseException {

        INList inMemoryList = database.getDbEnvironment().getInMemoryINs();

        if (root != null) {
            rootLatch.acquireShared();
            try {
                Node rootIN = root.getTarget();
                if (rootIN != null) {
                    rootIN.rebuildINList(inMemoryList);
                }
            } finally {
                rootLatch.release();
            }
        }
    }

    /*
     * Debugging stuff.
     */
    public void dump()
        throws DatabaseException {

        System.out.println(dumpString(0));
    }   

    public String dumpString(int nSpaces)
        throws DatabaseException {

        StringBuffer sb = new StringBuffer();
        sb.append(TreeUtils.indent(nSpaces));
        sb.append("<tree>");
        sb.append('\n');
        if (root != null) {
            sb.append(DbLsn.dumpString(root.getLsn(), nSpaces));
            sb.append('\n');
            IN rootIN = (IN) root.getTarget();
            if (rootIN == null) {
                sb.append("<in/>");
            } else {
                sb.append(rootIN.toString());
            }
            sb.append('\n');
        }
        sb.append(TreeUtils.indent(nSpaces));
        sb.append("</tree>");
        return sb.toString();
    }   

    /**
     * Unit test support to validate subtree pruning. Didn't want to make root
     * access public.
     */
    boolean validateDelete(int index)
        throws DatabaseException {

        rootLatch.acquireShared();
        try {
            IN rootIN = (IN) root.fetchTarget(database, null);
            return rootIN.validateSubtreeBeforeDelete(index);
        } finally {
            rootLatch.release();
        }
    }

    /**
     * Debugging check that all resident nodes are on the INList and no stray
     * nodes are present in the unused portion of the IN arrays.
     */
    public void validateINList(IN parent)
        throws DatabaseException {

        if (parent == null) {
            parent = (IN) root.getTarget();
        }
        if (parent != null) {
            INList inList = database.getDbEnvironment().getInMemoryINs();
            if (!inList.getINs().contains(parent)) {
                throw new DatabaseException
                    ("IN " + parent.getNodeId() + " missing from INList");
            }
            for (int i = 0;; i += 1) {
                try {
                    Node node = parent.getTarget(i);
                    if (i >= parent.getNEntries()) {
                        if (node != null) {
                            throw new DatabaseException
                                ("IN " + parent.getNodeId() +
                                 " has stray node " + node.getNodeId() + 
                                 " at index " + i);
                        }
                        byte[] key = parent.getKey(i);
                        if (key != null) {
                            throw new DatabaseException
                                ("IN " + parent.getNodeId() +
                                 " has stray key " + key + 
                                 " at index " + i);
                        }
                    }
                    if (node instanceof IN) {
                        validateINList((IN) node);
                    }
                } catch (ArrayIndexOutOfBoundsException e) {
                    break;
                }
            }
        }
    }

    /* For unit testing only. */
    public void setWaitHook(TestHook hook) {
        waitHook = hook;
    }

    /* For unit testing only. */
    public void setSearchHook(TestHook hook) {
        searchHook = hook;
    }

    /* For unit testing only. */
    public void setCkptHook(TestHook hook) {
        ckptHook = hook;
    }

    /**
     * Send trace messages to the java.util.logger. Don't rely on the logger
     * alone to conditionalize whether we send this message, we don't even want
     * to construct the message if the level is not enabled.
     */
    private void traceSplitRoot(Level level,
                                String splitType,
                                IN newRoot,
                                long newRootLsn,
                                IN oldRoot,
                                long oldRootLsn) {
        Logger logger = database.getDbEnvironment().getLogger();
        if (logger.isLoggable(level)) {
            StringBuffer sb = new StringBuffer();
            sb.append(splitType);
            sb.append(" newRoot=").append(newRoot.getNodeId());
            sb.append(" newRootLsn=").
		append(DbLsn.getNoFormatString(newRootLsn));
            sb.append(" oldRoot=").append(oldRoot.getNodeId());
            sb.append(" oldRootLsn=").
		append(DbLsn.getNoFormatString(oldRootLsn));
            logger.log(level, sb.toString());
        }
    }

    /**
     * Send trace messages to the java.util.logger. Don't rely on the logger
     * alone to conditionalize whether we send this message, we don't even want
     * to construct the message if the level is not enabled.
     */
    private void traceMutate(Level level,
                             BIN theBin,
                             LN existingLn,
                             LN newLn,
                             long newLsn,
                             DupCountLN dupCountLN,
                             long dupRootLsn,
                             DIN dupRoot,
                             long ddinLsn,
                             DBIN dupBin,
                             long dbinLsn) {
        Logger logger = database.getDbEnvironment().getLogger();
        if (logger.isLoggable(level)) {
            StringBuffer sb = new StringBuffer();
            sb.append(TRACE_MUTATE);
            sb.append(" existingLn=");
            sb.append(existingLn.getNodeId());
            sb.append(" newLn=");
            sb.append(newLn.getNodeId());
            sb.append(" newLnLsn=");
            sb.append(DbLsn.getNoFormatString(newLsn));
            sb.append(" dupCountLN=");
            sb.append(dupCountLN.getNodeId());
            sb.append(" dupRootLsn=");
            sb.append(DbLsn.getNoFormatString(dupRootLsn));
            sb.append(" rootdin=");
            sb.append(dupRoot.getNodeId());
            sb.append(" ddinLsn=");
            sb.append(DbLsn.getNoFormatString(ddinLsn));
            sb.append(" dbin=");
            sb.append(dupBin.getNodeId());
            sb.append(" dbinLsn=");
            sb.append(DbLsn.getNoFormatString(dbinLsn));
            sb.append(" bin=");
            sb.append(theBin.getNodeId());
	
            logger.log(level, sb.toString());
        }
    }

    /**
     * Send trace messages to the java.util.logger. Don't rely on the logger
     * alone to conditionalize whether we send this message, we don't even want
     * to construct the message if the level is not enabled.
     */
    private void traceInsert(Level level,
                             EnvironmentImpl env,
                             BIN insertingBin,
                             LN ln,
                             long lnLsn,
			     int index) {
        Logger logger = env.getLogger();
        if (logger.isLoggable(level)) {
            StringBuffer sb = new StringBuffer();
            sb.append(TRACE_INSERT);
            sb.append(" bin=");
            sb.append(insertingBin.getNodeId());
            sb.append(" ln=");
            sb.append(ln.getNodeId());
            sb.append(" lnLsn=");
            sb.append(DbLsn.getNoFormatString(lnLsn));
            sb.append(" index=");
	    sb.append(index);
	
            logger.log(level, sb.toString());
        }
    }

    /**
     * Send trace messages to the java.util.logger. Don't rely on the logger
     * alone to conditionalize whether we send this message, we don't even want
     * to construct the message if the level is not enabled.
     */
    private void traceInsertDuplicate(Level level,
                                      EnvironmentImpl env,
                                      BIN insertingDBin,
                                      LN ln,
                                      long lnLsn,
                                      long binNid) {
        Logger logger = env.getLogger();
        if (logger.isLoggable(level)) {
            StringBuffer sb = new StringBuffer();
            sb.append(TRACE_INSERT_DUPLICATE);
            sb.append(" dbin=");
            sb.append(insertingDBin.getNodeId());
            sb.append(" bin=");
            sb.append(binNid);
            sb.append(" ln=");
            sb.append(ln.getNodeId());
            sb.append(" lnLsn=");
            sb.append(DbLsn.getNoFormatString(lnLsn));
	
            logger.log(level, sb.toString());
        }
    }

    private static class SplitInfo {
        IN parent;
        IN child;
        int index;

        SplitInfo(IN parent, IN child, int index) {
            this.parent = parent;
            this.child = child;
            this.index = index;
        }
    }
}
