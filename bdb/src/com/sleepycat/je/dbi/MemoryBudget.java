/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: MemoryBudget.java,v 1.86 2008/06/06 17:11:52 linda Exp $
 */

package com.sleepycat.je.dbi;

import java.util.concurrent.atomic.AtomicLong;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.EnvironmentMutableConfig;
import com.sleepycat.je.EnvironmentStats;
import com.sleepycat.je.StatsConfig;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.tree.BIN;
import com.sleepycat.je.tree.DBIN;
import com.sleepycat.je.tree.DIN;
import com.sleepycat.je.tree.IN;
import com.sleepycat.je.utilint.Tracer;

/**
 * MemoryBudget calculates the available memory for JE and how to apportion
 * it between cache and log buffers. It is meant to centralize all memory
 * calculations. Objects that ask for memory budgets should get settings from
 * this class, rather than using the configuration parameter values directly.
 */
public class MemoryBudget implements EnvConfigObserver {

    /*
     * CLEANUP_DONE can be set to false for unit test debugging
     * that is still in progress. When we do the final regression,
     * this should be removed to be assured that it is never false.
     */
    public static boolean CLEANUP_DONE = false;

    /*
     * These DEBUG variables are public so unit tests can easily turn them
     * on and off for different sections of code.
     */
    public static boolean DEBUG_ADMIN = Boolean.getBoolean("memAdmin");
    public static boolean DEBUG_LOCK = Boolean.getBoolean("memLock");
    public static boolean DEBUG_TXN = Boolean.getBoolean("memTxn");
    public static boolean DEBUG_TREEADMIN = Boolean.getBoolean("memTreeAdmin");
    public static boolean DEBUG_TREE = Boolean.getBoolean("memTree");

    /*
     * Object overheads. These are set statically with advance measurements.
     * Java doesn't provide a way of assessing object size dynamically. These
     * overheads will not be precise, but are close enough to let the system
     * behave predictably.
     *
     * _32 values are the same on Windows and Solaris.
     * _64 values are from 1.5.0_05 on Solaris.
     *
     * Specifically:
     *
     * java.vm.version=1.5.0_05_b05 os.name=SunOS
     * java.vm.version=1.4.2_05_b04 os.name=SunOS
     * java.vm.version=1.5.0_04_b05, os.name=Windows XP
     * java.vm.version=1.4.2_06-b03, os.name=Windows XP
     *
     * The integer following the // below is the Sizeof argument used to
     * compute the value.
     */

    // 7
    private final static int LONG_OVERHEAD_32 = 16;
    private final static int LONG_OVERHEAD_64 = 24;

    // 8
    private final static int ARRAY_OVERHEAD_32 = 16;
    private final static int ARRAY_OVERHEAD_64 = 24;

    private final static int ARRAY_SIZE_INCLUDED_32 = 4;
    private final static int ARRAY_SIZE_INCLUDED_64 = 0;

    // 2
    private final static int OBJECT_OVERHEAD_32 = 8;
    private final static int OBJECT_OVERHEAD_64 = 16;

    // (4 - ARRAY_OVERHEAD) / 256
    // 64b: 4 is 2072
    private final static int OBJECT_ARRAY_ITEM_OVERHEAD_32 = 4;
    private final static int OBJECT_ARRAY_ITEM_OVERHEAD_64 = 8;

    // 20
    private final static int HASHMAP_OVERHEAD_32 = 120;
    private final static int HASHMAP_OVERHEAD_64_15 = 216;
    private final static int HASHMAP_OVERHEAD_64_16 = 218;

    // 21 - OBJECT_OVERHEAD - HASHMAP_OVERHEAD
    // 64b: 21 is max(280,...,287) on Linux/Solaris 1.5/1.6
    private final static int HASHMAP_ENTRY_OVERHEAD_32 = 24;
    private final static int HASHMAP_ENTRY_OVERHEAD_64 = 55;

    // 22
    private final static int HASHSET_OVERHEAD_32 = 136;
    private final static int HASHSET_OVERHEAD_64 = 240;

    // 23 - OBJECT_OVERHEAD - HASHSET_OVERHEAD
    // 64b: 23 is max(304,...,311) on Linux/Solaris
    private final static int HASHSET_ENTRY_OVERHEAD_32 = 24;
    private final static int HASHSET_ENTRY_OVERHEAD_64 = 55;

    // HASHMAP_OVERHEAD * 2
    private final static int TWOHASHMAPS_OVERHEAD_32 = 240;
    private final static int TWOHASHMAPS_OVERHEAD_64_15 = 432;
    private final static int TWOHASHMAPS_OVERHEAD_64_16 = 436;

    // 34
    private final static int TREEMAP_OVERHEAD_32_15 = 40;
    private final static int TREEMAP_OVERHEAD_32_16 = 48;
    private final static int TREEMAP_OVERHEAD_64_15 = 64;
    private final static int TREEMAP_OVERHEAD_64_16 = 80;

    // 35 - OBJECT_OVERHEAD - TREEMAP_OVERHEAD
    // 64b: 35 is 144 on 1.5 and 160 on 1.6, result is 64 for both
    private final static int TREEMAP_ENTRY_OVERHEAD_32 = 32;
    private final static int TREEMAP_ENTRY_OVERHEAD_64 = 64;

    // 36
    private final static int MAPLN_OVERHEAD_32_15 = 640;
    private final static int MAPLN_OVERHEAD_32_16 = 664;
    private final static int MAPLN_OVERHEAD_64_15 = 1096;
    private final static int MAPLN_OVERHEAD_64_16 = 1136;

    // 9
    private final static int LN_OVERHEAD_32 = 24;
    private final static int LN_OVERHEAD_64 = 40;

    // 19
    private final static int DUPCOUNTLN_OVERHEAD_32 = 24;
    private final static int DUPCOUNTLN_OVERHEAD_64 = 48;

    // 12
    // 64b: 12 is max(536, 539) on Linux/Solaris on 1.5
    // 64b: 12 is max(578, 576) on Linux/Solaris on 1.6
    private final static int BIN_FIXED_OVERHEAD_32 = 370; // 344 in 1.5
    private final static int BIN_FIXED_OVERHEAD_64_15 = 544;
    private final static int BIN_FIXED_OVERHEAD_64_16 = 584;

    // 18
    private final static int DIN_FIXED_OVERHEAD_32 = 377; // 352 in 1.5
    private final static int DIN_FIXED_OVERHEAD_64_15 = 552;
    private final static int DIN_FIXED_OVERHEAD_64_16 = 596;

    // 17
    // 64b: 17 is max(592,593) on Linux/Solaris on 1.6
    private final static int DBIN_FIXED_OVERHEAD_32 = 377; // 352 in 1.5
    private final static int DBIN_FIXED_OVERHEAD_64_15 = 560;
    private final static int DBIN_FIXED_OVERHEAD_64_16 = 600;

    // 13
    // 339 is max(312,339) on Solaris 1.5 vs 1.6
    private final static int IN_FIXED_OVERHEAD_32 = 339; // 312 in 1.5
    private final static int IN_FIXED_OVERHEAD_64_15 = 488;
    private final static int IN_FIXED_OVERHEAD_64_16 = 528;

    // 6
    private final static int KEY_OVERHEAD_32 = 16;
    private final static int KEY_OVERHEAD_64 = 24;

    // 24
    private final static int LOCKIMPL_OVERHEAD_32 = 24;
    private final static int LOCKIMPL_OVERHEAD_64 = 48;

    // 42
    private final static int THINLOCKIMPL_OVERHEAD_32 = 16;
    private final static int THINLOCKIMPL_OVERHEAD_64 = 32;

    // 25
    private final static int LOCKINFO_OVERHEAD_32 = 16;
    private final static int LOCKINFO_OVERHEAD_64 = 32;

    // 37
    private final static int WRITE_LOCKINFO_OVERHEAD_32 = 32;
    private final static int WRITE_LOCKINFO_OVERHEAD_64 = 40;

    /*
     * Txn memory is the size for the Txn + a hashmap entry
     * overhead for being part of the transaction table.
     */
    // 15
    private final static int TXN_OVERHEAD_32 = 186;
    private final static int TXN_OVERHEAD_64 = 281;

    // 26
    private final static int CHECKPOINT_REFERENCE_SIZE_32 = 40 +
        HASHSET_ENTRY_OVERHEAD_32;
    private final static int CHECKPOINT_REFERENCE_SIZE_64 = 56 +
        HASHSET_ENTRY_OVERHEAD_64;

    /* The per-log-file bytes used in UtilizationProfile. */
    // 29 / 10
    private final static int UTILIZATION_PROFILE_ENTRY_32 = 101;
    private final static int UTILIZATION_PROFILE_ENTRY_64 = 153;

    //  38
    private final static int DBFILESUMMARY_OVERHEAD_32 = 40;
    private final static int DBFILESUMMARY_OVERHEAD_64 = 48;

    /* Tracked File Summary overheads. */
    // 31
    private final static int TFS_LIST_INITIAL_OVERHEAD_32 = 464;
    private final static int TFS_LIST_INITIAL_OVERHEAD_64 = 504;

    // 30
    // 64b: 30 is max(464,464,464,465) on Linux/Solaris on 1.5/1.6
    private final static int TFS_LIST_SEGMENT_OVERHEAD_32 = 440;
    private final static int TFS_LIST_SEGMENT_OVERHEAD_64 = 465;

    // 33
    private final static int LN_INFO_OVERHEAD_32 = 24;
    private final static int LN_INFO_OVERHEAD_64 = 48;

    // 43
    private final static int FILESUMMARYLN_OVERHEAD_32 = 112;
    private final static int FILESUMMARYLN_OVERHEAD_64 = 168;

    /* Approximate element size in an ArrayList of Long. */
    // (28 - 27) / 10
    // 32b: 28 and 27 are 240 and 40, resp.
    // 64b: 28 and 27 are 384 and 64, resp.
    private final static int LONG_LIST_PER_ITEM_OVERHEAD_32 = 20;
    private final static int LONG_LIST_PER_ITEM_OVERHEAD_64 = 32;

    public final static int LONG_OVERHEAD;
    public final static int ARRAY_OVERHEAD;
    public final static int ARRAY_SIZE_INCLUDED;
    public final static int OBJECT_OVERHEAD;
    public final static int OBJECT_ARRAY_ITEM_OVERHEAD;
    public final static int HASHMAP_OVERHEAD;
    public final static int HASHMAP_ENTRY_OVERHEAD;
    public final static int HASHSET_OVERHEAD;
    public final static int HASHSET_ENTRY_OVERHEAD;
    public final static int TWOHASHMAPS_OVERHEAD;
    public final static int TREEMAP_OVERHEAD;
    public final static int TREEMAP_ENTRY_OVERHEAD;
    public final static int MAPLN_OVERHEAD;
    public final static int LN_OVERHEAD;
    public final static int DUPCOUNTLN_OVERHEAD;
    public final static int BIN_FIXED_OVERHEAD;
    public final static int DIN_FIXED_OVERHEAD;
    public final static int DBIN_FIXED_OVERHEAD;
    public final static int IN_FIXED_OVERHEAD;
    public final static int KEY_OVERHEAD;
    public final static int LOCKIMPL_OVERHEAD;
    public final static int THINLOCKIMPL_OVERHEAD;
    public final static int LOCKINFO_OVERHEAD;
    public final static int WRITE_LOCKINFO_OVERHEAD;
    public final static int TXN_OVERHEAD;
    public final static int CHECKPOINT_REFERENCE_SIZE;
    public final static int UTILIZATION_PROFILE_ENTRY;
    public final static int DBFILESUMMARY_OVERHEAD;
    public final static int TFS_LIST_INITIAL_OVERHEAD;
    public final static int TFS_LIST_SEGMENT_OVERHEAD;
    public final static int LN_INFO_OVERHEAD;
    public final static int FILESUMMARYLN_OVERHEAD;
    public final static int LONG_LIST_PER_ITEM_OVERHEAD;

    /* Primitive long array item size is the same on all platforms. */
    public final static int PRIMITIVE_LONG_ARRAY_ITEM_OVERHEAD = 8;

    private final static String JVM_ARCH_PROPERTY = "sun.arch.data.model";
    private final static String FORCE_JVM_ARCH = "je.forceJVMArch";

    static {
        String javaVersion = System.getProperty("java.version");
        boolean isJVM15 = javaVersion != null &&
                          javaVersion.startsWith("1.5.");

        boolean is64 = false;
        String overrideArch = System.getProperty(FORCE_JVM_ARCH);
        try {
            if (overrideArch == null) {
                String arch = System.getProperty(JVM_ARCH_PROPERTY);
                if (arch != null) {
                    is64 = Integer.parseInt(arch) == 64;
                }
            } else {
                is64 = Integer.parseInt(overrideArch) == 64;
            }
        } catch (NumberFormatException NFE) {
            NFE.printStackTrace(System.err);
        }

        if (is64) {
            LONG_OVERHEAD = LONG_OVERHEAD_64;
            ARRAY_OVERHEAD = ARRAY_OVERHEAD_64;
            ARRAY_SIZE_INCLUDED = ARRAY_SIZE_INCLUDED_64;
            OBJECT_OVERHEAD = OBJECT_OVERHEAD_64;
            OBJECT_ARRAY_ITEM_OVERHEAD = OBJECT_ARRAY_ITEM_OVERHEAD_64;
            HASHMAP_ENTRY_OVERHEAD = HASHMAP_ENTRY_OVERHEAD_64;
            HASHSET_OVERHEAD = HASHSET_OVERHEAD_64;
            HASHSET_ENTRY_OVERHEAD = HASHSET_ENTRY_OVERHEAD_64;
            if (isJVM15) {
                TREEMAP_OVERHEAD = TREEMAP_OVERHEAD_64_15;
                MAPLN_OVERHEAD = MAPLN_OVERHEAD_64_15;
                BIN_FIXED_OVERHEAD = BIN_FIXED_OVERHEAD_64_15;
                DIN_FIXED_OVERHEAD = DIN_FIXED_OVERHEAD_64_15;
                DBIN_FIXED_OVERHEAD = DBIN_FIXED_OVERHEAD_64_15;
                IN_FIXED_OVERHEAD = IN_FIXED_OVERHEAD_64_15;
                HASHMAP_OVERHEAD = HASHMAP_OVERHEAD_64_15;
                TWOHASHMAPS_OVERHEAD = TWOHASHMAPS_OVERHEAD_64_15;
            } else {
                TREEMAP_OVERHEAD = TREEMAP_OVERHEAD_64_16;
                MAPLN_OVERHEAD = MAPLN_OVERHEAD_64_16;
                BIN_FIXED_OVERHEAD = BIN_FIXED_OVERHEAD_64_16;
                DIN_FIXED_OVERHEAD = DIN_FIXED_OVERHEAD_64_16;
                DBIN_FIXED_OVERHEAD = DBIN_FIXED_OVERHEAD_64_16;
                IN_FIXED_OVERHEAD = IN_FIXED_OVERHEAD_64_16;
                HASHMAP_OVERHEAD = HASHMAP_OVERHEAD_64_16;
                TWOHASHMAPS_OVERHEAD = TWOHASHMAPS_OVERHEAD_64_16;
            }
            TREEMAP_ENTRY_OVERHEAD = TREEMAP_ENTRY_OVERHEAD_64;
            LN_OVERHEAD = LN_OVERHEAD_64;
            DUPCOUNTLN_OVERHEAD = DUPCOUNTLN_OVERHEAD_64;
            TXN_OVERHEAD = TXN_OVERHEAD_64;
            CHECKPOINT_REFERENCE_SIZE = CHECKPOINT_REFERENCE_SIZE_64;
            KEY_OVERHEAD = KEY_OVERHEAD_64;
            LOCKIMPL_OVERHEAD = LOCKIMPL_OVERHEAD_64;
            THINLOCKIMPL_OVERHEAD = THINLOCKIMPL_OVERHEAD_64;
            LOCKINFO_OVERHEAD = LOCKINFO_OVERHEAD_64;
            WRITE_LOCKINFO_OVERHEAD = WRITE_LOCKINFO_OVERHEAD_64;
            UTILIZATION_PROFILE_ENTRY = UTILIZATION_PROFILE_ENTRY_64;
            DBFILESUMMARY_OVERHEAD = DBFILESUMMARY_OVERHEAD_64;
            TFS_LIST_INITIAL_OVERHEAD = TFS_LIST_INITIAL_OVERHEAD_64;
            TFS_LIST_SEGMENT_OVERHEAD = TFS_LIST_SEGMENT_OVERHEAD_64;
            LN_INFO_OVERHEAD = LN_INFO_OVERHEAD_64;
            FILESUMMARYLN_OVERHEAD = FILESUMMARYLN_OVERHEAD_64;
            LONG_LIST_PER_ITEM_OVERHEAD = LONG_LIST_PER_ITEM_OVERHEAD_64;
        } else {
            LONG_OVERHEAD = LONG_OVERHEAD_32;
            ARRAY_OVERHEAD = ARRAY_OVERHEAD_32;
            ARRAY_SIZE_INCLUDED = ARRAY_SIZE_INCLUDED_32;
            OBJECT_OVERHEAD = OBJECT_OVERHEAD_32;
            OBJECT_ARRAY_ITEM_OVERHEAD = OBJECT_ARRAY_ITEM_OVERHEAD_32;
            HASHMAP_OVERHEAD = HASHMAP_OVERHEAD_32;
            HASHMAP_ENTRY_OVERHEAD = HASHMAP_ENTRY_OVERHEAD_32;
            HASHSET_OVERHEAD = HASHSET_OVERHEAD_32;
            HASHSET_ENTRY_OVERHEAD = HASHSET_ENTRY_OVERHEAD_32;
            TWOHASHMAPS_OVERHEAD = TWOHASHMAPS_OVERHEAD_32;
            if (isJVM15) {
                TREEMAP_OVERHEAD = TREEMAP_OVERHEAD_32_15;
                MAPLN_OVERHEAD = MAPLN_OVERHEAD_32_15;
            } else {
                TREEMAP_OVERHEAD = TREEMAP_OVERHEAD_32_16;
                MAPLN_OVERHEAD = MAPLN_OVERHEAD_32_16;
            }
            TREEMAP_ENTRY_OVERHEAD = TREEMAP_ENTRY_OVERHEAD_32;
            LN_OVERHEAD = LN_OVERHEAD_32;
            DUPCOUNTLN_OVERHEAD = DUPCOUNTLN_OVERHEAD_32;
            BIN_FIXED_OVERHEAD = BIN_FIXED_OVERHEAD_32;
            DIN_FIXED_OVERHEAD = DIN_FIXED_OVERHEAD_32;
            DBIN_FIXED_OVERHEAD = DBIN_FIXED_OVERHEAD_32;
            IN_FIXED_OVERHEAD = IN_FIXED_OVERHEAD_32;
            TXN_OVERHEAD = TXN_OVERHEAD_32;
            CHECKPOINT_REFERENCE_SIZE = CHECKPOINT_REFERENCE_SIZE_32;
            KEY_OVERHEAD = KEY_OVERHEAD_32;
            LOCKIMPL_OVERHEAD = LOCKIMPL_OVERHEAD_32;
            THINLOCKIMPL_OVERHEAD = THINLOCKIMPL_OVERHEAD_32;
            LOCKINFO_OVERHEAD = LOCKINFO_OVERHEAD_32;
            WRITE_LOCKINFO_OVERHEAD = WRITE_LOCKINFO_OVERHEAD_32;
            UTILIZATION_PROFILE_ENTRY = UTILIZATION_PROFILE_ENTRY_32;
            DBFILESUMMARY_OVERHEAD = DBFILESUMMARY_OVERHEAD_32;
            TFS_LIST_INITIAL_OVERHEAD = TFS_LIST_INITIAL_OVERHEAD_32;
            TFS_LIST_SEGMENT_OVERHEAD = TFS_LIST_SEGMENT_OVERHEAD_32;
            LN_INFO_OVERHEAD = LN_INFO_OVERHEAD_32;
            FILESUMMARYLN_OVERHEAD = FILESUMMARYLN_OVERHEAD_32;
            LONG_LIST_PER_ITEM_OVERHEAD = LONG_LIST_PER_ITEM_OVERHEAD_32;
        }
    }

    /* public for unit tests. */
    public final static long MIN_MAX_MEMORY_SIZE = 96 * 1024;
    public final static String MIN_MAX_MEMORY_SIZE_STRING =
        Long.toString(MIN_MAX_MEMORY_SIZE);
 
    /* This value prevents cache churn for apps with a high write rate. */
    @SuppressWarnings("unused")
    private final static int DEFAULT_MIN_BTREE_CACHE_SIZE = 500 * 1024;

    private final static long N_64MB = (1 << 26);

    /*
     * Note that this class contains long fields that are accessed by multiple
     * threads.  Access to these fields is synchronized when changing them but
     * not when reading them to detect cache overflow or get stats.  Although
     * inaccuracies may occur when reading the values, correcting this is not
     * worth the cost of synchronizing every time we access them.  The worst
     * that can happen is that we may invoke eviction unnecessarily.
     */

    /*
     * Amount of memory cached for tree objects.
     */
    private AtomicLong treeMemoryUsage = new AtomicLong(0);

    /*
     * Amount of memory cached for txn usage.
     */
    private AtomicLong txnMemoryUsage = new AtomicLong(0);

    /*
     * Amount of memory cached for log cleaning, dirty IN list, and other admin
     * functions.
     */
    private AtomicLong adminMemoryUsage = new AtomicLong(0);

    /*
     * Amount of memory cached for admininstrative structures that are
     * sometimes housed within tree nodes. Right now, that's
     * DbFileSummaryMap, which is sometimes referenced by a MapLN by
     * way of a DatabaseImpl, and sometimes is just referenced by
     * a DatabaseImpl without a MapLN (the id and name databases.)
     */
    private AtomicLong treeAdminMemoryUsage = new AtomicLong(0);

    /*
     * Number of lock tables (cache of EnvironmentParams.N_LOCK_TABLES).
     */
    private int nLockTables;

    /*
     * Amount of memory cached for locks. Protected by the
     * LockManager.lockTableLatches[lockTableIndex].
     */
    private long[] lockMemoryUsage;

    /*
     * Memory available to JE, based on je.maxMemory and the memory available
     * to this process.
     */
    private Totals totals;

    /* Memory available to log buffers. */
    private long logBufferBudget;

    /* Maximum allowed use of the admin budget by the UtilizationTracker. */
    private long trackerBudget;
    
    /* Mininum to prevent cache churn. */
    private long minTreeMemoryUsage;
    
    /* 
     * Overheads that are a function of node capacity.
     */
    private long inOverhead;
    private long binOverhead;
    private long dinOverhead;
    private long dbinOverhead;

    private EnvironmentImpl envImpl;

    MemoryBudget(EnvironmentImpl envImpl,
                 EnvironmentImpl sharedCacheEnv,
                 DbConfigManager configManager)
        throws DatabaseException {

        this.envImpl = envImpl;

        /* Request notification of mutable property changes. */
        envImpl.addConfigObserver(this);

        /* Peform first time budget initialization. */
        long newMaxMemory;
        if (envImpl.getSharedCache()) {
            if (sharedCacheEnv != null) {
                totals = sharedCacheEnv.getMemoryBudget().totals;
                /* For a new environment, do not override existing budget. */
                newMaxMemory = -1;
            } else {
                totals = new SharedTotals();
                newMaxMemory = calcMaxMemory(configManager);
            }
        } else {
            totals = new PrivateTotals(this);
            newMaxMemory = calcMaxMemory(configManager);
        }
        reset(newMaxMemory, true /*newEnv*/, configManager);

        /*
         * Calculate IN and BIN overheads, which are a function of capacity.
         * These values are stored in this class so that they can be calculated
         * once per environment. The logic to do the calculations is left in
         * the respective node classes so it can be done properly in the domain
         * of those objects.
         */
        inOverhead = IN.computeOverhead(configManager);
        binOverhead = BIN.computeOverhead(configManager);
        dinOverhead = DIN.computeOverhead(configManager);
        dbinOverhead = DBIN.computeOverhead(configManager);
    }

    /**
     * Respond to config updates.
     */
    public void envConfigUpdate(DbConfigManager configManager,
                                EnvironmentMutableConfig ignore)
        throws DatabaseException {

        /* Reinitialize the cache budget and the log buffer pool. */
        reset(calcMaxMemory(configManager), false /*newEnv*/, configManager);
    }

    private long calcMaxMemory(DbConfigManager configManager)
        throws DatabaseException {

        /*
         * Calculate the total memory allotted to JE.
         * 1. If je.maxMemory is specified, use that. Check that it's not more
         * than the JVM memory.
         * 2. Otherwise, take je.maxMemoryPercent * JVM max memory.
         */
        long newMaxMemory =
            configManager.getLong(EnvironmentParams.MAX_MEMORY);
        long jvmMemory = getRuntimeMaxMemory();

        if (newMaxMemory != 0) {
            /* Application specified a cache size number, validate it. */
            if (jvmMemory < newMaxMemory) {
                throw new IllegalArgumentException
                    (EnvironmentParams.MAX_MEMORY.getName() +
                     " has a value of " + newMaxMemory +
                     " but the JVM is only configured for " +
                     jvmMemory +
                     ". Consider using je.maxMemoryPercent.");
            }
            if (newMaxMemory < MIN_MAX_MEMORY_SIZE) {
                throw new IllegalArgumentException
                    (EnvironmentParams.MAX_MEMORY.getName() +
                     " is " + newMaxMemory +
                     " which is less than the minimum: " +
                     MIN_MAX_MEMORY_SIZE);
            }
        } else {

            /*
             * When no explicit cache size is specified and the JVM memory size
             * is unknown, assume a default sized (64 MB) heap.  This produces
             * a reasonable cache size when no heap size is known.
             */
            if (jvmMemory == Long.MAX_VALUE) {
                jvmMemory = N_64MB;
            }

            /* Use the configured percentage of the JVM memory size. */
            int maxMemoryPercent =
                configManager.getInt(EnvironmentParams.MAX_MEMORY_PERCENT);
            newMaxMemory = (maxMemoryPercent * jvmMemory) / 100;
        }

        return newMaxMemory;
    }

    /**
     * Initialize at construction time and when the cache is resized.
     *
     * @param newMaxMemory is the new total cache budget or is less than 0 if
     * the total should remain unchanged.
     *
     * @param newEnv is true if this is the first time we are resetting the
     * budget for a new environment.  Note that a new environment has not yet
     * been added to the set of shared cache environments.
     */
    void reset(long newMaxMemory,
               boolean newEnv,
               DbConfigManager configManager)
        throws DatabaseException {

        long oldLogBufferBudget = logBufferBudget;

        /*
         * Update the new total cache budget.
         */
        if (newMaxMemory < 0) {
            newMaxMemory = getMaxMemory();
        } else {
            totals.setMaxMemory(newMaxMemory);
        }

        /*
         * This environment's portion is adjusted for a shared cache.  Further
         * below we make buffer and tracker sizes a fixed percentage (7% and
         * 2%, by default) of the total shared cache size.  The math for this
         * starts by dividing the total size by number of environments to get
         * myCachePortion.  Then we take 7% or 2% of myCachePortion to get each
         * environment's portion.  In other words, if there are 10 environments
         * then each gets 7%/10 and 2%/10 of the total cache size, by default.
         *
         * Note that when we resize the shared cache, we resize the buffer
         * pools and tracker budgets for all environments.  Resizing the
         * tracker budget has no overhead, but resizing the buffer pools causes
         * new buffers to be allocated.  If reallocation of the log buffers is
         * not desirable, the user can configure a byte amount rather than a
         * percentage.
         */
        long myCachePortion;
        if (envImpl.getSharedCache()) {
            int nEnvs = DbEnvPool.getInstance().getNSharedCacheEnvironments();
            if (newEnv) {
                nEnvs += 1;
            }
            myCachePortion = newMaxMemory / nEnvs;
        } else {
            myCachePortion = newMaxMemory;
        }

        /*
         * Calculate the memory budget for log buffering.  If the LOG_MEM_SIZE
         * parameter is not set, start by using 7% (1/16th) of the cache
         * size. If it is set, use that explicit setting.
         *
         * No point in having more log buffers than the maximum size. If
         * this starting point results in overly large log buffers,
         * reduce the log buffer budget again.
         */
        long newLogBufferBudget =
            configManager.getLong(EnvironmentParams.LOG_MEM_SIZE);      
        if (newLogBufferBudget == 0) {
            if (EnvironmentImpl.IS_DALVIK) {
                /* If Dalvik JVM, use 1/128th instead of 1/16th of cache. */
                newLogBufferBudget = myCachePortion >> 7;
            } else {
                newLogBufferBudget = myCachePortion >> 4;
            }
        } else if (newLogBufferBudget > myCachePortion / 2) {
            newLogBufferBudget = myCachePortion / 2;
        }

        /*
         * We have a first pass at the log buffer budget. See what
         * size log buffers result. Don't let them be too big, it would
         * be a waste.
         */
        int numBuffers =
            configManager.getInt(EnvironmentParams.NUM_LOG_BUFFERS);
        long startingBufferSize = newLogBufferBudget / numBuffers;
        int logBufferSize =
            configManager.getInt(EnvironmentParams.LOG_BUFFER_MAX_SIZE);
        if (startingBufferSize > logBufferSize) {
            startingBufferSize = logBufferSize;
            newLogBufferBudget = numBuffers * startingBufferSize;
        } else if (startingBufferSize <
                   EnvironmentParams.MIN_LOG_BUFFER_SIZE) {
            startingBufferSize = EnvironmentParams.MIN_LOG_BUFFER_SIZE;
            newLogBufferBudget = numBuffers * startingBufferSize;
        }

        long newCriticalThreshold =
            (newMaxMemory *
             envImpl.getConfigManager().getInt
                (EnvironmentParams.EVICTOR_CRITICAL_PERCENTAGE))/100;

        long newTrackerBudget =
            (myCachePortion *
             envImpl.getConfigManager().getInt
                (EnvironmentParams.CLEANER_DETAIL_MAX_MEMORY_PERCENTAGE))/100;

        long newMinTreeMemoryUsage = Math.min
            (configManager.getLong(EnvironmentParams.MIN_TREE_MEMORY),
             myCachePortion - newLogBufferBudget); 

        /* 
         * If all has gone well, update the budget fields.  Once the log buffer
         * budget is determined, the remainder of the memory is left for tree
         * nodes.
         */
        logBufferBudget = newLogBufferBudget;
        totals.setCriticalThreshold(newCriticalThreshold);
        trackerBudget = newTrackerBudget;
        if (lockMemoryUsage == null) {
            nLockTables =
                configManager.getInt(EnvironmentParams.N_LOCK_TABLES);
            lockMemoryUsage = new long[nLockTables];
        }
        minTreeMemoryUsage = newMinTreeMemoryUsage;

        /* The log buffer budget is counted in the cache usage. */
        totals.updateCacheUsage(logBufferBudget - oldLogBufferBudget);

        /*
         * Only reset the log buffer pool if the log buffer has already been
         * initialized (we're updating an existing budget) and the log buffer
         * budget hasn't changed (resetting it is expensive and may cause I/O).
         */
        if (!newEnv && oldLogBufferBudget != logBufferBudget) {
            envImpl.getLogManager().resetPool(configManager);
        }
    }

    /**
     * Returns Runtime.maxMemory(), accounting for a MacOS bug.
     * May return Long.MAX_VALUE if there is no inherent limit.
     * Used by unit tests as well as by this class.
     */
    public static long getRuntimeMaxMemory() {

        /* Runtime.maxMemory is unreliable on MacOS Java 1.4.2. */
        if ("Mac OS X".equals(System.getProperty("os.name"))) {
            String jvmVersion = System.getProperty("java.version");
            if (jvmVersion != null && jvmVersion.startsWith("1.4.2")) {
                return Long.MAX_VALUE; /* Undetermined heap size. */
            }
        }

        return Runtime.getRuntime().maxMemory();
    }

    /**
     * Initialize the starting environment memory state. We really only need to
     * recalibrate the tree and treeAdmin categories, since there are no locks
     * and txns yet, and the items in the admin category are cleaner items and
     * aren't affected by the recovery splicing process.
     */
    void initCacheMemoryUsage(long dbTreeAdminMemory) 
        throws DatabaseException {

        long totalTree = 0;
        long treeAdmin = 0;
        for (IN in : envImpl.getInMemoryINs()) {
            totalTree += in.getBudgetedMemorySize();
            treeAdmin += in.getTreeAdminMemorySize();
        }
        refreshTreeMemoryUsage(totalTree);
        refreshTreeAdminMemoryUsage(treeAdmin + dbTreeAdminMemory);
    }

    /**
     * Called by INList when clearing  tree memory usage.
     */
    void refreshTreeAdminMemoryUsage(long newSize) {
        long oldSize =  treeAdminMemoryUsage.getAndSet(newSize);
        long diff = (newSize - oldSize);

        if (DEBUG_TREEADMIN) {
            System.err.println("RESET = " + newSize);
        }
        if (totals.updateCacheUsage(diff)) {
            envImpl.alertEvictor();
        }
    }

    /**
     * Called by INList when recalculating tree memory usage.
     */
    void refreshTreeMemoryUsage(long newSize) {
        long oldSize = treeMemoryUsage.getAndSet(newSize);
        long diff = (newSize - oldSize);

        if (totals.updateCacheUsage(diff)) {
            envImpl.alertEvictor();
        }
    }

    /**
     * Returns whether eviction of INList information is allowed.
     * To prevent extreme cache churn, eviction of Btree information is
     * prohibited unless the tree memory usage is above this minimum value.
     */
    public boolean isTreeUsageAboveMinimum() {
        return treeMemoryUsage.get() > minTreeMemoryUsage;
    }

    /**
     * For unit tests.
     */
    public long getMinTreeMemoryUsage() {
        return minTreeMemoryUsage;
    }

    /**
     * Update the environment wide tree memory count, wake up the evictor if
     * necessary.
     * @param increment note that increment may be negative.
     */
    public void updateTreeMemoryUsage(long increment) {
        updateCounter(increment, treeMemoryUsage, "tree", DEBUG_TREE);
    }

    /**
     * Update the environment wide txn memory count, wake up the evictor if
     * necessary.
     * @param increment note that increment may be negative.
     */
    public void updateTxnMemoryUsage(long increment) {
        updateCounter(increment, txnMemoryUsage, "txn", DEBUG_TXN);
    }

    /**
     * Update the environment wide admin memory count, wake up the evictor if
     * necessary.
     * @param increment note that increment may be negative.
     */
    public void updateAdminMemoryUsage(long increment) {
        updateCounter(increment, adminMemoryUsage, "admin", DEBUG_ADMIN);
    }

    /**
     * Update the treeAdmin memory count, wake up the evictor if necessary.
     * @param increment note that increment may be negative.
     */
    public void updateTreeAdminMemoryUsage(long increment) {
        updateCounter(increment, treeAdminMemoryUsage, "treeAdmin", 
                      DEBUG_TREEADMIN); 
    }

    private void updateCounter(long increment, 
                               AtomicLong counter,
                               String debugName,
                               boolean debug) {
        if (increment != 0) {
            long newSize = counter.addAndGet(increment);
            
            assert (sizeNotNegative(newSize)) :
                   makeErrorMessage(debugName, newSize, increment);

            if (debug) {
                if (increment > 0) {
                    System.err.println("INC-------- =" + increment + " " +
                                       debugName + " "  + newSize);
                } else {
                    System.err.println("-------DEC=" + increment + " " +
                                       debugName + " "  + newSize);
                }
            }

            if (totals.updateCacheUsage(increment)) {
                envImpl.alertEvictor();
            }
        }
    }

    private boolean sizeNotNegative(long newSize) {

        if (CLEANUP_DONE)  {
            return (newSize >= 0);
        } else {
            return true;
        }
    }

    public void updateLockMemoryUsage(long increment, int lockTableIndex) {
        if (increment != 0) {
            lockMemoryUsage[lockTableIndex] += increment;

            assert lockMemoryUsage[lockTableIndex] >= 0:
                   makeErrorMessage("lockMem",
                                     lockMemoryUsage[lockTableIndex],
                                     increment);
            if (DEBUG_LOCK) {
                if (increment > 0) {
                    System.err.println("INC-------- =" + increment +
                            " lock[" +
                                      lockTableIndex + "] " +
                                      lockMemoryUsage[lockTableIndex]);
                } else {
                    System.err.println("-------DEC=" + increment +
                            " lock[" + lockTableIndex + "] " +
                                           lockMemoryUsage[lockTableIndex]);
                }
            }

            if (totals.updateCacheUsage(increment)) {
                envImpl.alertEvictor();
            }
        }
    }

    private String makeErrorMessage(String memoryType,
                                    long total,
                                    long increment) {
        return memoryType + "=" + total +
            " increment=" + increment + " " +
            Tracer.getStackTrace(new Throwable());
    }

    void subtractCacheUsage() {
        totals.updateCacheUsage(0 - getLocalCacheUsage());
    }

    private long getLocalCacheUsage() {
        return logBufferBudget +
               treeMemoryUsage.get() +
               adminMemoryUsage.get() +
               treeAdminMemoryUsage.get() +
               getLockMemoryUsage();
    }

    long getVariableCacheUsage() {
        return treeMemoryUsage.get() +
            adminMemoryUsage.get() +
            treeAdminMemoryUsage.get() +
            getLockMemoryUsage();
    }

    /**
     * Public for unit testing.
     */
    public long getLockMemoryUsage() {
        long accLockMemoryUsage = txnMemoryUsage.get();
        if (nLockTables == 1) {
            accLockMemoryUsage += lockMemoryUsage[0];
        } else {
            for (int i = 0; i < nLockTables; i++) {
                accLockMemoryUsage += lockMemoryUsage[i];
            }
        }

        return accLockMemoryUsage;
    }

    /*
     * The following 2 methods are shorthand for getTotals.getXxx().
     */

    public long getCacheMemoryUsage() {
        return totals.getCacheUsage();
    }

    public long getMaxMemory() {
        return totals.getMaxMemory();
    }

    /**
     * Used for unit testing.
     */
    public long getTreeMemoryUsage() {
        return treeMemoryUsage.get();
    }

    /**
     * Used for unit testing.
     */
    public long getAdminMemoryUsage() {
        return adminMemoryUsage.get();
    }
    
    /*
     * For unit testing
     */
    public long getTreeAdminMemoryUsage() {
        return treeAdminMemoryUsage.get();
    }

    public long getLogBufferBudget() {
        return logBufferBudget;
    }

    public long getTrackerBudget() {
        return trackerBudget;
    }

    public long getINOverhead() {
        return inOverhead;
    }

    public long getBINOverhead() {
        return binOverhead;
    }

    public long getDINOverhead() {
        return dinOverhead;
    }

    public long getDBINOverhead() {
        return dbinOverhead;
    }

    /**
     * Returns the memory size occupied by a byte array of a given length.  All
     * arrays (regardless of element type) have the same overhead for a zero
     * length array.  On 32b Java, there are 4 bytes included in that fixed
     * overhead that can be used for the first N elements -- however many fit
     * in 4 bytes.  On 64b Java, there is no extra space included.  In all
     * cases, space is allocated in 8 byte chunks.
     */
    public static int byteArraySize(int arrayLen) {

        /*
         * ARRAY_OVERHEAD accounts for N bytes of data, which is 4 bytes on 32b
         * Java and 0 bytes on 64b Java.  Data larger than N bytes is allocated
         * in 8 byte increments.
         */
        int size = ARRAY_OVERHEAD;
        if (arrayLen > ARRAY_SIZE_INCLUDED) {
            size += ((arrayLen - ARRAY_SIZE_INCLUDED + 7) / 8) * 8;
        }

        return size;
    }

    public static int shortArraySize(int arrayLen) {
        return byteArraySize(arrayLen * 2);
    }

    public static int intArraySize(int arrayLen) {
        return byteArraySize(arrayLen * 4);
    }

    public static int objectArraySize(int arrayLen) {
        return byteArraySize(arrayLen * OBJECT_ARRAY_ITEM_OVERHEAD);
    }

    void loadStats(StatsConfig config, EnvironmentStats stats) {
        stats.setSharedCacheTotalBytes
            (totals.isSharedCache() ? totals.getCacheUsage() : 0);
        stats.setCacheTotalBytes(getLocalCacheUsage());
        stats.setDataBytes(treeMemoryUsage.get() +
                           treeAdminMemoryUsage.get());
        stats.setAdminBytes(adminMemoryUsage.get());
        stats.setLockBytes(getLockMemoryUsage());
    }

    @Override 
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("treeUsage = ").append(treeMemoryUsage.get());
        sb.append("treeAdminUsage = ").append(treeAdminMemoryUsage.get());
        sb.append("adminUsage = ").append(adminMemoryUsage.get());
        sb.append("txnUsage = ").append(txnMemoryUsage.get());
        sb.append("lockUsage = ").append(getLockMemoryUsage());
        return sb.toString();
    }

    public Totals getTotals() {
        return totals;
    }

    /**
     * Common base class for shared and private totals.  This abstraction
     * allows most other classes to be unaware of whether we're using a
     * SharedEvictor or PrivateEvictor.
     */
    public abstract static class Totals {

        long maxMemory;
        private long criticalThreshold;

        private Totals() {
            maxMemory = 0;
        }

        private final void setMaxMemory(long maxMemory) {
            this.maxMemory = maxMemory;
        }

        public final long getMaxMemory() {
            return maxMemory;
        }

        private final void setCriticalThreshold(long criticalThreshold) {
            this.criticalThreshold = criticalThreshold;
        }

        public final long getCriticalThreshold() {
            return criticalThreshold;
        }

        public abstract long getCacheUsage();
        abstract boolean updateCacheUsage(long increment);
        abstract boolean isSharedCache();
    }

    /**
     * Totals for a single environment's non-shared cache.  Used when
     * EnvironmentConfig.setSharedCache(false) and a PrivateEvictor are used.
     */
    private static class PrivateTotals extends Totals {

        private MemoryBudget parent;

        private PrivateTotals(MemoryBudget parent) {
            this.parent = parent;
        }

        public final long getCacheUsage() {
            return parent.getLocalCacheUsage();
        }

        final boolean updateCacheUsage(long increment) {
            return (parent.getLocalCacheUsage() > maxMemory);
        }

        final boolean isSharedCache() {
            return false;
        }
    }

    /**
     * Totals for the multi-environment shared cache.  Used when
     * EnvironmentConfig.setSharedCache(false) and the SharedEvictor are used.
     */
    private static class SharedTotals extends Totals {

        private AtomicLong usage;

        private SharedTotals() {
            usage = new AtomicLong();
        }

        public final long getCacheUsage() {
            return usage.get();
        }

        final boolean updateCacheUsage(long increment) {
            return (usage.addAndGet(increment) > maxMemory);
        }

        final boolean isSharedCache() {
            return true;
        }
    }
}
