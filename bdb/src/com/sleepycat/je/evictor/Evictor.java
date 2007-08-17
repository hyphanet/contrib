/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: Evictor.java,v 1.86.2.6 2007/07/02 19:54:50 mark Exp $
 */

package com.sleepycat.je.evictor;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.EnvironmentStats;
import com.sleepycat.je.StatsConfig;
import com.sleepycat.je.cleaner.TrackedFileSummary;
import com.sleepycat.je.cleaner.UtilizationTracker;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.DatabaseImpl;
import com.sleepycat.je.dbi.DbConfigManager;
import com.sleepycat.je.dbi.DbTree;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.dbi.INList;
import com.sleepycat.je.dbi.MemoryBudget;
import com.sleepycat.je.latch.LatchSupport;
import com.sleepycat.je.log.LogManager;
import com.sleepycat.je.recovery.Checkpointer;
import com.sleepycat.je.tree.BIN;
import com.sleepycat.je.tree.ChildReference;
import com.sleepycat.je.tree.IN;
import com.sleepycat.je.tree.Node;
import com.sleepycat.je.tree.SearchResult;
import com.sleepycat.je.tree.Tree;
import com.sleepycat.je.tree.WithRootLatched;
import com.sleepycat.je.utilint.DaemonThread;
import com.sleepycat.je.utilint.DbLsn;
import com.sleepycat.je.utilint.TestHook;
import com.sleepycat.je.utilint.Tracer;

/**
 * The Evictor looks through the INList for IN's and BIN's that are worthy of
 * eviction.  Once the nodes are selected, it removes all references to them so
 * that they can be GC'd by the JVM.
 */
public class Evictor extends DaemonThread {
    public static final String SOURCE_DAEMON = "daemon";
    public static final String SOURCE_MANUAL = "manual";
    public static final String SOURCE_CRITICAL = "critical";
    private static final boolean DEBUG = false;

    private EnvironmentImpl envImpl;
    private LogManager logManager;
    private Level detailedTraceLevel;  // level value for detailed trace msgs
    private volatile boolean active;   // true if eviction is happening.

    /* Round robin marker in the INList, indicates start of eviction scans. */
    private IN nextNode;

    /* The number of bytes we need to evict in order to get under budget. */
    private long currentRequiredEvictBytes;

    /* 1 node out of <nodesPerScan> are chosen for eviction. */
    private int nodesPerScan;

    /* je.evictor.evictBytes */
    private long evictBytesSetting;

    /* je.evictor.lruOnly */
    private boolean evictByLruOnly;

    /* je.evictor.forceYield */
    private boolean forcedYield;

    /* for trace messages. */
    private NumberFormat formatter; 

    /*
     * Stats
     */

    /* Number of passes made to the evictor. */
    private int nEvictPasses = 0;

    /* Number of nodes selected to evict. */
    private long nNodesSelected = 0;
    private long nNodesSelectedThisRun;

    /* Number of nodes scanned in order to select the eviction set */
    private int nNodesScanned = 0;
    private int nNodesScannedThisRun;

    /* 
     * Number of nodes evicted on this run. This could be understated, as a
     * whole subtree may have gone out with a single node.
     */
    private long nNodesEvicted = 0;
    private long nNodesEvictedThisRun;

    /* Number of BINs stripped. */
    private long nBINsStripped = 0;
    private long nBINsStrippedThisRun;

    /* Debugging and unit test support. */
    EvictProfile evictProfile;  
    private TestHook runnableHook;

    public Evictor(EnvironmentImpl envImpl, String name)
        throws DatabaseException {

        super(0, name, envImpl);
        this.envImpl = envImpl;
        logManager = envImpl.getLogManager();
        nextNode = null;

        DbConfigManager configManager = envImpl.getConfigManager();
        nodesPerScan = configManager.getInt
            (EnvironmentParams.EVICTOR_NODES_PER_SCAN);
        evictBytesSetting = configManager.getLong
            (EnvironmentParams.EVICTOR_EVICT_BYTES);
        evictByLruOnly = configManager.getBoolean
            (EnvironmentParams.EVICTOR_LRU_ONLY);
        forcedYield = configManager.getBoolean
            (EnvironmentParams.EVICTOR_FORCED_YIELD);
        detailedTraceLevel = Tracer.parseLevel
            (envImpl, EnvironmentParams.JE_LOGGING_LEVEL_EVICTOR);

        evictProfile = new EvictProfile();
        formatter = NumberFormat.getNumberInstance();

        active = false;
    }

    /**
     * Evictor doesn't have a work queue so just throw an exception if it's
     * ever called.
     */
    public void addToQueue(Object o)
        throws DatabaseException {

        throw new DatabaseException
            ("Evictor.addToQueue should never be called.");
    }

    /**
     * Load stats.
     */
    public void loadStats(StatsConfig config, EnvironmentStats stat) 
        throws DatabaseException {

        stat.setNEvictPasses(nEvictPasses);
        stat.setNNodesSelected(nNodesSelected);
        stat.setNNodesScanned(nNodesScanned);
        stat.setNNodesExplicitlyEvicted(nNodesEvicted);
        stat.setNBINsStripped(nBINsStripped);
        stat.setRequiredEvictBytes(currentRequiredEvictBytes);

        if (config.getClear()) {
            nEvictPasses = 0;
            nNodesSelected = 0;
            nNodesScanned = 0;
            nNodesEvicted = 0;
            nBINsStripped = 0;
        }
    }

    synchronized public void clearEnv() {
        envImpl = null;
    }

    /**
     * Return the number of retries when a deadlock exception occurs.
     */
    protected int nDeadlockRetries()
        throws DatabaseException {

        return envImpl.getConfigManager().getInt
            (EnvironmentParams.EVICTOR_RETRY);
    }

    /**
     * Wakeup the evictor only if it's not already active.
     */
    public void alert() {
        if (!active) {
            wakeup();
        }
    }

    /**
     * Called whenever the daemon thread wakes up from a sleep. 
     */
    public void onWakeup()
        throws DatabaseException {

        if (envImpl.isClosed()) {
            return;
        }

        doEvict(SOURCE_DAEMON,
                false, // criticalEviction
                true); // backgroundIO
    }

    /**
     * May be called by the evictor thread on wakeup or programatically.
     */
    public void doEvict(String source) 
        throws DatabaseException {

        doEvict(source,
                false, // criticalEviction
                true); // backgroundIO
    }

    /**
     * Allows performing eviction during shutdown, which is needed when
     * during checkpointing and cleaner log file deletion.
     */
    private synchronized void doEvict(String source,
                                      boolean criticalEviction,
                                      boolean backgroundIO) 
        throws DatabaseException {

        /*
         * We use an active flag to prevent reentrant calls.  This is simpler
         * than ensuring that no reentrant eviction can occur in any caller.
         * We also use the active flag to determine when it is unnecessary to
         * wake up the evictor thread.
         */
        if (active) {
            return;
        }
        active = true;
        try {

            /*
             * Repeat as necessary to keep up with allocations.  Stop if no
             * progress is made, to prevent an infinite loop.
             */
            boolean progress = true;
            while (progress &&
                   (criticalEviction || !isShutdownRequested()) &&
                   isRunnable(source)) {
                if (evictBatch
                    (source, backgroundIO, currentRequiredEvictBytes) == 0) {
                    progress = false;
                }
            }
        } finally {
            active = false;
        }
    }

    /**
     * Do a check on whether synchronous eviction is needed.
     */
    public void doCriticalEviction(boolean backgroundIO)
        throws DatabaseException {

        MemoryBudget mb = envImpl.getMemoryBudget();
        long currentUsage  = mb.getCacheMemoryUsage();
        long maxMem = mb.getCacheBudget();
        long over = currentUsage - maxMem;
        
        if (over > mb.getCriticalThreshold()) {
            if (DEBUG) {
                System.out.println("***critical detected:" + over);
            }
            doEvict(SOURCE_CRITICAL,
                    true, // criticalEviction
                    backgroundIO);
        }

        if (forcedYield) {
            Thread.yield();
        }
    }

    /**
     * Each iteration will latch and unlatch the major INList, and will attempt
     * to evict requiredEvictBytes, but will give up after a complete pass
     * over the major INList. Releasing the latch is important because it
     * provides an opportunity for to add the minor INList to the major INList.
     *
     * @return the number of bytes evicted, or zero if no progress was made.
     */
    long evictBatch(String source,
                    boolean backgroundIO,
                    long requiredEvictBytes)
        throws DatabaseException {

        nNodesSelectedThisRun = 0;
        nNodesEvictedThisRun = 0;
        nNodesScannedThisRun = 0;
        nBINsStrippedThisRun = 0;
        nEvictPasses++;

        assert evictProfile.clear(); // intentional side effect
        int nBatchSets = 0;
        boolean finished = false;
        long evictBytes = 0;

        /* Evict utilization tracking info without holding the INList latch. */
        evictBytes += envImpl.getUtilizationTracker().evictMemory();

        INList inList = envImpl.getInMemoryINs();
        inList.latchMajor();
        int inListStartSize = inList.getSize();
        
        /*
         * Use a tracker to count lazily compressed, deferred write, LNs as
         * obsolete.  A separate tracker is used to accumulate tracked obsolete
         * info so it can be added in a single call under the log write latch.
         * [#15365]
         */
        UtilizationTracker tracker = new UtilizationTracker(envImpl);

        try {

            /* 
             * Setup the round robin iterator. Note that because critical
             * eviction is now called during recovery, when the INList is
             * sometimes abruptly cleared, nextNode may not be null when the
             * INList is empty.
             */
            if (inListStartSize == 0) {
                nextNode = null;
                return 0;
            } else {
                if (nextNode == null) {
                    nextNode = inList.first();
                }
            }

            ScanIterator scanIter = new ScanIterator(nextNode, inList);

            /*
             * Keep evicting until we've freed enough memory or we've visited
             * the maximum number of nodes allowed. Each iteration of the while
             * loop is called an eviction batch.
             *
             * In order to prevent endless evicting and not keep the INList
             * major latch for too long, limit this run to one pass over the IN
             * list.
             */
            while ((evictBytes < requiredEvictBytes) &&
                   (nNodesScannedThisRun <= inListStartSize)) {

                IN target = selectIN(inList, scanIter);

                if (target == null) {
                    break;
                } else {
                    assert evictProfile.count(target);//intentional side effect
                    if (target.isDbRoot()) {
                        evictBytes += evictRoot
                            (inList, target, scanIter, backgroundIO);
                    } else {
                        evictBytes += evict
                            (inList, target, scanIter, backgroundIO, tracker);
                    }
                }
                nBatchSets++;
            }

            /*
             * At the end of the scan, look at the next element in the INList
             * and put it in nextNode for the next time we scan the INList.
             */
            nextNode = scanIter.mark();
            finished = true;

        } finally {
            nNodesScanned += nNodesScannedThisRun;
            inList.releaseMajorLatch();

            Logger logger = envImpl.getLogger();
            if (logger.isLoggable(detailedTraceLevel)) {
                /* Ugh, only create trace message when logging. */
                Tracer.trace(detailedTraceLevel, envImpl,
                             "Evictor: pass=" + nEvictPasses +
                             " finished=" + finished +
                             " source=" + source +
                             " requiredEvictBytes=" +
                             formatter.format(requiredEvictBytes) +
                             " evictBytes=" + 
                             formatter.format(evictBytes) +
                             " inListSize=" + inListStartSize +
                             " nNodesScanned=" + nNodesScannedThisRun +
                             " nNodesSelected=" + nNodesSelectedThisRun +
                             " nEvicted=" + nNodesEvictedThisRun +
                             " nBINsStripped=" + nBINsStrippedThisRun +
                             " nBatchSets=" + nBatchSets);
            }
        }

        assert LatchSupport.countLatchesHeld() == 0: "latches held = " +
            LatchSupport.countLatchesHeld();

        /*
         * Count obsolete nodes and write out modified file summaries for
         * recovery.  All latches must have been released. [#15365]
         */
        TrackedFileSummary[] summaries = tracker.getTrackedFiles();
        if (summaries.length > 0) {
            envImpl.getUtilizationProfile().countAndLogSummaries(summaries);
        }

        return evictBytes;
    }

    /**
     * Return true if eviction should happen.
     */
    boolean isRunnable(String source)
        throws DatabaseException {

        MemoryBudget mb = envImpl.getMemoryBudget();
        long currentUsage  = mb.getCacheMemoryUsage();
        long maxMem = mb.getCacheBudget();
        boolean doRun = ((currentUsage - maxMem) > 0);

        /* If running, figure out how much to evict. */
        if (doRun) {
            currentRequiredEvictBytes =
                (currentUsage - maxMem) + evictBytesSetting;
            if (DEBUG) {
                if (source == SOURCE_CRITICAL) {
                    System.out.println("executed: critical runnable");
                }
            }
        }

        /* unit testing, force eviction */
        if (runnableHook != null) {
            doRun = ((Boolean) runnableHook.getHookValue()).booleanValue();
            currentRequiredEvictBytes = maxMem;
        }

        /* 
         * This trace message is expensive, only generate if tracing at this
         * level is enabled.
         */
        Logger logger = envImpl.getLogger();
        if (logger.isLoggable(detailedTraceLevel)) {

            /* 
             * Generate debugging output. Note that Runtime.freeMemory
             * fluctuates over time as the JVM grabs more memory, so you really
             * have to do totalMemory - freeMemory to get stack usage.  (You
             * can't get the concept of memory available from free memory.)
             */
            Runtime r = Runtime.getRuntime();
            long totalBytes = r.totalMemory();
            long freeBytes= r.freeMemory();
            long usedBytes = r.totalMemory() - r.freeMemory(); 
            StringBuffer sb = new StringBuffer();
            sb.append(" source=").append(source);
            sb.append(" doRun=").append(doRun);
            sb.append(" JEusedBytes=").append(formatter.format(currentUsage));
            sb.append(" requiredEvict=").
                append(formatter.format(currentRequiredEvictBytes));
            sb.append(" JVMtotalBytes= ").append(formatter.format(totalBytes));
            sb.append(" JVMfreeBytes= ").append(formatter.format(freeBytes));
            sb.append(" JVMusedBytes= ").append(formatter.format(usedBytes));
            logger.log(detailedTraceLevel, sb.toString());
        }

        return doRun;
    }

    /**
     * Select a single node to evict.
     */
    private IN selectIN(INList inList, ScanIterator scanIter)
        throws DatabaseException {

        /* Find the best target in the next <nodesPerScan> nodes. */
        IN target = null;
        long targetGeneration = Long.MAX_VALUE;
        int targetLevel = Integer.MAX_VALUE;
        boolean targetDirty = true;
	boolean envIsReadOnly = envImpl.isReadOnly();
        int scanned = 0;
        boolean wrapped = false;
        while (scanned < nodesPerScan) {
            if (scanIter.hasNext()) {
                IN in = scanIter.next();
                nNodesScannedThisRun++;

                DatabaseImpl db = in.getDatabase();

                /* 
                 * We don't expect to see an IN with a database that has
                 * finished delete processing, because it would have been
                 * removed from the inlist during post-delete cleanup.
                 */
                if (db == null || db.isDeleteFinished()) {
                    String inInfo = " IN type=" + in.getLogType() + " id=" +
                        in.getNodeId() + " not expected on INList";
                    String errMsg = (db == null) ? inInfo :
                        "Database " + db.getDebugName() + " id=" + db.getId() +
                        " rootLsn=" +
                        DbLsn.getNoFormatString(db.getTree().getRootLsn()) +
                        ' ' + inInfo;
                    throw new DatabaseException(errMsg);
                }

                /* Ignore if the db is in the middle of delete processing. */
                if (db.isDeleted()) {
                    continue;
                }

                /* 
                 * If this is a read only database and we have at least one
                 * target, skip any dirty INs (recovery dirties INs even in a
                 * read-only environment). We take at least one target so we
                 * don't loop endlessly if everything is dirty.
                 */
                if (envIsReadOnly && (target != null) && in.getDirty()) {
                    continue;
                }

                /*
                 * Only scan evictable or strippable INs.  This prevents higher
                 * level INs from being selected for eviction, unless they are
                 * part of an unused tree.
                 */
                int evictType = in.getEvictionType();
                if (evictType == IN.MAY_NOT_EVICT) {
                    continue;
                }

                /*
                 * This node is in the scanned node set.  Select according to
                 * the configured eviction policy.
                 */
                if (evictByLruOnly) {

                    /*
                     * Select the node with the lowest generation number,
                     * irrespective of tree level or dirtyness.
                     */
                    if (targetGeneration > in.getGeneration()) {
                        targetGeneration = in.getGeneration();
                        target = in;
                    }
                } else {

                    /*
                     * Select first by tree level, then by dirtyness, then by
                     * generation/LRU.
                     */
                    int level = normalizeLevel(in, evictType);
                    if (targetLevel != level) {
                        if (targetLevel > level) {
                            targetLevel = level;
                            targetDirty = in.getDirty();
                            targetGeneration = in.getGeneration();
                            target = in;
                        }
                    } else if (targetDirty != in.getDirty()) {
                        if (targetDirty) {
                            targetDirty = false;
                            targetGeneration = in.getGeneration();
                            target = in;
                        }
                    } else {
                        if (targetGeneration > in.getGeneration()) {
                            targetGeneration = in.getGeneration();
                            target = in;
                        }
                    }
                }
                scanned++;
            } else {
                /* We wrapped around in the list. */
                if (wrapped) {
                    break;
                } else {
                    nextNode = inList.first();
                    scanIter.reset(nextNode);
                    wrapped = true;
                }
            }
        }

        if (target != null) {
            nNodesSelectedThisRun++;
            nNodesSelected++;
        }
        return target;
    }

    /**
     * Normalize the tree level of the given IN.
     *
     * Is public for unit testing.
     *
     * A BIN containing evictable LNs is given level 0, so it will be stripped
     * first.  For non-duplicate and DBMAP trees, the high order bits are
     * cleared to make their levels correspond; that way, all bottom level
     * nodes (BINs and DBINs) are given the same eviction priority.
     *
     * Note that BINs in a duplicate tree are assigned the same level as BINs
     * in a non-duplicate tree.  This isn't always optimimal, but is the best
     * we can do considering that BINs in duplicate trees may contain a mix of
     * LNs and DINs.
     *
     * BINs in the mapping tree are also assigned the same level as user DB
     * BINs.  When doing by-level eviction (lruOnly=false), this seems
     * counter-intuitive since we should evict user DB nodes before mapping DB
     * nodes.  But that does occur because mapping DB INs referencing an open
     * DB are unevictable.  The level is only used for selecting among
     * evictable nodes.
     *
     * If we did NOT normalize the level for the mapping DB, then INs for
     * closed evictable DBs would not be evicted until after all nodes in all
     * user DBs were evicted.  If there were large numbers of closed DBs, this
     * would have a negative performance impact.
     */
    public int normalizeLevel(IN in, int evictType) {

        int level = in.getLevel() & IN.LEVEL_MASK;

        if (level == 1 && evictType == IN.MAY_EVICT_LNS) {
            level = 0;
        }

        return level;
    }

    /**
     * Evict this DB root node.  [#13415]
     * @return number of bytes evicted.
     */
    private long evictRoot(final INList inList,
                           final IN target,
                           final ScanIterator scanIter,
                           final boolean backgroundIO) 
        throws DatabaseException {

        final DatabaseImpl db = target.getDatabase();

        class RootEvictor implements WithRootLatched {

            boolean flushed = false;
            long evictBytes = 0;

            public IN doWork(ChildReference root)
                throws DatabaseException {

                IN rootIN = (IN) root.fetchTarget(db, null);
                rootIN.latch(false);
                try {
                    /* Re-check that all conditions still hold. */
                    if (rootIN == target &&
                        rootIN.isDbRoot() &&
                        rootIN.isEvictable()) {

                        /* Flush if dirty. */
                        if (!envImpl.isReadOnly() && rootIN.getDirty()) {
                            long newLsn = rootIN.log 
                                (envImpl.getLogManager(),
                                 false, // allowDeltas
                                 isProvisionalRequired(rootIN),
                                 true,  // proactiveMigration
                                 backgroundIO,
                                 null); // parent
                            root.setLsn(newLsn);
                            flushed = true;
                        }

                        /* Take off the INList and adjust memory budget. */
                        scanIter.mark();
                        inList.removeLatchAlreadyHeld(rootIN);
                        scanIter.resetToMark();
                        evictBytes = rootIN.getInMemorySize();

                        /* Evict IN. */
                        root.clearTarget();
                    }
                } finally {
                    rootIN.releaseLatch();
                }
                return null;
            }
        }

        /* Attempt to evict the DB root IN. */
        RootEvictor evictor = new RootEvictor();
        db.getTree().withRootLatchedExclusive(evictor);

        /* If the root IN was flushed, write the dirtied MapLN. */
        if (evictor.flushed) {
            envImpl.getDbMapTree().modifyDbRoot(db);
        }

        return evictor.evictBytes;
    }

    /**
     * Strip or evict this node.
     * @return number of bytes evicted.
     */
    private long evict(INList inList,
                       IN target,
                       ScanIterator scanIter,
                       boolean backgroundIO,
                       UtilizationTracker tracker) 
        throws DatabaseException {
        
	boolean envIsReadOnly = envImpl.isReadOnly();
        long evictedBytes = 0;

        /*
         * Non-BIN INs are evicted by detaching them from their parent.  For
         * BINS, the first step is to remove deleted entries by compressing
         * the BIN. The evictor indicates that we shouldn't fault in 
         * non-resident children during compression. After compression, 
         * LN logging and LN stripping may be performed.
         *
         * If LN stripping is used, first we strip the BIN by logging any dirty
         * LN children and detaching all its resident LN targets.  If we make
         * progress doing that, we stop and will not evict the BIN itself until
         * possibly later.  If it has no resident LNs then we evict the BIN
         * itself using the "regular" detach-from-parent routine.
         *
         * If the cleaner is doing clustering, we don't do BIN stripping if we
         * can write out the BIN.  Specifically LN stripping is not performed
         * if the BIN is dirty AND the BIN is evictable AND cleaner
         * clustering is enabled.  In this case the BIN is going to be written
         * out soon, and with clustering we want to be sure to write out the
         * LNs with the BIN; therefore we don't do stripping
         */

        /*
         * Use latchNoWait because if it's latched we don't want the cleaner
         * to hold up eviction while it migrates an entire BIN.  Latched INs
         * have a high generation value, so not evicting makes sense.  Pass
         * false because we don't want to change the generation during the
         * eviction process.
         */
        if (target.latchNoWait(false)) { 
	    boolean targetIsLatched = true;
            try {
                if (target instanceof BIN) {
                    /* first attempt to compress deleted, resident children.*/
                    envImpl.lazyCompress(target, tracker);

                    /* 
                     * Strip any resident LN targets right now. This may dirty
                     * the BIN if dirty LNs were written out. Note that
                     * migrated BIN entries cannot be stripped.
                     */
                    evictedBytes = ((BIN) target).evictLNs();
                    if (evictedBytes > 0) {
                        nBINsStrippedThisRun++;
                        nBINsStripped++;
                    } 
                }

                /*
                 * If we were able to free any memory by LN stripping above,
                 * then we postpone eviction of the BIN until a later pass.
                 * The presence of migrated entries would have inhibited LN
                 * stripping. In that case, the BIN can still be evicted,
                 * but the marked entries will have to be migrated. That would
                 * happen when the target is logged in evictIN.
                 */
                if (evictedBytes == 0 && target.isEvictable()) {
                    /* Regular eviction. */
                    Tree tree = target.getDatabase().getTree();

                    /* getParentINForChildIN unlatches target. */
		    targetIsLatched = false;
                    SearchResult result =
                        tree.getParentINForChildIN
                        (target,
                         true,   // requireExactMatch
                         false); // updateGeneration
                    if (result.exactParentFound) {
                        evictedBytes =  evictIN(target, result.parent,
                                                result.index,
                                                inList, scanIter,
                                                envIsReadOnly,
                                                backgroundIO);
                    }
                }
            } finally {
		if (targetIsLatched) {
		    target.releaseLatchIfOwner();
		}
            }
        }
            
        return evictedBytes;
    }

    /**
     * Evict an IN. Dirty nodes are logged before they're evicted. inlist is
     * latched with the major latch by the caller.
     */
    private long evictIN(IN child,
                         IN parent,
                         int index,
                         INList inlist,
                         ScanIterator scanIter,
			 boolean envIsReadOnly,
                         boolean backgroundIO)
        throws DatabaseException {

        long evictBytes = 0;
        try {
            assert parent.isLatchOwnerForWrite();

            long oldGenerationCount = child.getGeneration();
            
            /* 
             * Get a new reference to the child, in case the reference
             * saved in the selection list became out of date because of
             * changes to that parent.
             */
            IN renewedChild = (IN) parent.getTarget(index);
            
            /*
             * See the evict() method in this class for an explanation for
             * calling latchNoWait(false).
             */
            if ((renewedChild != null) &&
                (renewedChild.getGeneration() <= oldGenerationCount) &&
                renewedChild.latchNoWait(false)) {

                try {
                    if (renewedChild.isEvictable()) {

                        /* 
                         * Log the child if dirty and env is not r/o. Remove
                         * from IN list.
                         */
                        long renewedChildLsn = DbLsn.NULL_LSN;
                        boolean newChildLsn = false;
                        if (renewedChild.getDirty()) {
                            if (!envIsReadOnly) {
				boolean logProvisional =
                                    isProvisionalRequired(renewedChild);

                                /*
                                 * Log a full version (no deltas) and with
                                 * cleaner migration allowed.
                                 */
                                renewedChildLsn = renewedChild.log 
                                    (logManager,
                                     false, // allowDeltas
                                     logProvisional,
                                     true,  // proactiveMigration
                                     backgroundIO,
                                     parent);
                                newChildLsn = true;
                            }
                        } else {
                            renewedChildLsn = parent.getLsn(index);
                        }

                        if (renewedChildLsn != DbLsn.NULL_LSN) {
                            /* Take this off the inlist. */
                            scanIter.mark();
                            inlist.removeLatchAlreadyHeld(renewedChild);
                            scanIter.resetToMark();

                            evictBytes = renewedChild.getInMemorySize();
                            if (newChildLsn) {

                                /* 
                                 * Update the parent so its reference is
                                 * null and it has the proper LSN.
                                 */
                                parent.updateEntry
                                    (index, null, renewedChildLsn);
                            } else {

                                /*
                                 * Null out the reference, but don't dirty
                                 * the node since only the reference
                                 * changed.
                                 */
                                parent.updateEntry(index, (Node) null);
                            }

                            /* Stats */
                            nNodesEvictedThisRun++;
                            nNodesEvicted++;
                        }
                    }
                } finally {
                    renewedChild.releaseLatch();
                }
            }
        } finally {
            parent.releaseLatch();
        }

        return evictBytes;
    }

    /* 
     * @return true if the node must be logged provisionally.
     */
    private boolean isProvisionalRequired(IN target) {

        /* 
         * The evictor has to log provisionally in two cases:
         * a - the checkpointer is in progress, and is at a level above the
         * target eviction victim. We don't want the evictor's actions to 
         * introduce an IN that has not cascaded up properly.
         * b - the eviction target is part of a deferred write database.
         */
        if (target.getDatabase().isDeferredWrite()) {
            return true;
        }

        /* 
         * The checkpointer could be null if it was shutdown or never
         * started.
         */
        Checkpointer ckpter = envImpl.getCheckpointer();
        if ((ckpter != null) &&
            (target.getLevel() < ckpter.getHighestFlushLevel())) {
            return true;
        }

        return false;
    }

    /**
     * Used by unit tests.
     */
    IN getNextNode() {
        return nextNode;
    }

    /* For unit testing only. */
    public void setRunnableHook(TestHook hook) {
        runnableHook = hook;
    }

    /* For debugging and unit tests. */
    static public class EvictProfile {
        /* Keep a list of candidate nodes. */
        private List candidates = new ArrayList();

        /* Remember that this node was targetted. */
        public boolean count(IN target) {
            candidates.add(new Long(target.getNodeId()));
            return true;
        }

        public List getCandidates() {
            return candidates;
        }

        public boolean clear() {
            candidates.clear();
            return true;
        }
    }

    /*
     * ScanIterator keeps a handle onto the current round robin INList 
     * iterator. It's deliberately not a member of the class in order to keep
     * less common state in the class.
     */
    private static class ScanIterator {
        private INList inList;
        private Iterator iter;
        private IN nextMark;

        ScanIterator(IN startingIN, INList inList) 
            throws DatabaseException {

            this.inList = inList;
            reset(startingIN);
        }

        void reset(IN startingIN) 
            throws DatabaseException {

            iter = inList.tailSet(startingIN).iterator();
        }

        IN mark() 
            throws DatabaseException {

            if (iter.hasNext()) {
                nextMark = (IN) iter.next();
            } else {
                nextMark = (IN) inList.first();
            }
            return (IN) nextMark;
        }

        void resetToMark() 
            throws DatabaseException {

            reset(nextMark);
        }

        boolean hasNext() {
            return iter.hasNext();
        }

        IN next() {
            return (IN) iter.next();
        }

        void remove() {
            iter.remove();
        }
    }
}
