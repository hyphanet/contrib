/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: MemoryBudget.java,v 1.54.2.6 2007/07/13 02:32:05 cwl Exp $
 */

package com.sleepycat.je.dbi;

import java.util.Iterator;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.EnvironmentStats;
import com.sleepycat.je.StatsConfig;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.latch.LatchSupport;
import com.sleepycat.je.tree.BIN;
import com.sleepycat.je.tree.DBIN;
import com.sleepycat.je.tree.DIN;
import com.sleepycat.je.tree.IN;

/**
 * MemoryBudget calculates the available memory for JE and how to apportion
 * it between cache and log buffers. It is meant to centralize all memory 
 * calculations. Objects that ask for memory budgets should get settings from
 * this class, rather than using the configuration parameter values directly.
 */
public class MemoryBudget implements EnvConfigObserver {

    /*
     * Object overheads. These are set statically with advance measurements.
     * Java doesn't provide a way of assessing object size dynamically. These
     * overheads will not be precise, but are close enough to let the system
     * behave predictably.
     *
     * _32 values are the same on Windows and Solaris.
     * _64 values are from 1.5.0_05 on Solaris.
     * _14 values are from 1.4.2 on Windows and Solaris.
     * _15 values are from 1.5.0_05 on Solaris and Windows.
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

    // 8 - 2560
    private final static int BYTE_ARRAY_OVERHEAD_32 = 16;
    private final static int BYTE_ARRAY_OVERHEAD_64 = 24;

    // 2
    private final static int OBJECT_OVERHEAD_32 = 8;
    private final static int OBJECT_OVERHEAD_64 = 16;

    // (4 - BYTE_ARRAY_OVERHEAD_32) / 256
    private final static int ARRAY_ITEM_OVERHEAD_32 = 4;
    private final static int ARRAY_ITEM_OVERHEAD_64 = 8;

    // 20
    private final static int HASHMAP_OVERHEAD_32 = 120;
    private final static int HASHMAP_OVERHEAD_64 = 216;

    // 21 - OBJECT_OVERHEAD_32 - HASHMAP_OVERHEAD_32
    private final static int HASHMAP_ENTRY_OVERHEAD_32 = 24;
    private final static int HASHMAP_ENTRY_OVERHEAD_64 = 48;

    // 22
    private final static int HASHSET_OVERHEAD_32 = 136;
    private final static int HASHSET_OVERHEAD_64 = 240;

    // 23 - OBJECT_OVERHEAD_32 - HASHSET_OVERHEAD_32
    private final static int HASHSET_ENTRY_OVERHEAD_32 = 24;
    private final static int HASHSET_ENTRY_OVERHEAD_64 = 48;

    // 2 * HASHMAP_OVERHEAD_32
    private final static int TWOHASHMAPS_OVERHEAD_32 = 240;
    private final static int TWOHASHMAPS_OVERHEAD_64 = 432;

    // 34
    private final static int TREEMAP_OVERHEAD_32 = 40;
    private final static int TREEMAP_OVERHEAD_64 = 64;

    // 35 - OBJECT_OVERHEAD_32 - TREEMAP_OVERHEAD_32
    private final static int TREEMAP_ENTRY_OVERHEAD_32 = 32;
    private final static int TREEMAP_ENTRY_OVERHEAD_64 = 53;

    // 36
    private final static int MAPLN_OVERHEAD_32 = 464;
    private final static int MAPLN_OVERHEAD_64 = 776;

    // 9
    private final static int LN_OVERHEAD_32 = 24;
    private final static int LN_OVERHEAD_64 = 32;

    // 19
    private final static int DUPCOUNTLN_OVERHEAD_32 = 24;
    private final static int DUPCOUNTLN_OVERHEAD_64 = 40;

    // 12
    private final static int BIN_FIXED_OVERHEAD_32_14 = 344;
    private final static int BIN_FIXED_OVERHEAD_32_15 = 360;
    private final static int BIN_FIXED_OVERHEAD_64_15 = 528;

    // 18
    private final static int DIN_FIXED_OVERHEAD_32_14 = 352;
    private final static int DIN_FIXED_OVERHEAD_32_15 = 360;
    private final static int DIN_FIXED_OVERHEAD_64_15 = 536;

    // 17
    private final static int DBIN_FIXED_OVERHEAD_32_14 = 352;
    private final static int DBIN_FIXED_OVERHEAD_32_15 = 368;
    private final static int DBIN_FIXED_OVERHEAD_64_15 = 544;

    // 13
    private final static int IN_FIXED_OVERHEAD_32_14 = 312;
    private final static int IN_FIXED_OVERHEAD_32_15 = 320;
    private final static int IN_FIXED_OVERHEAD_64_15 = 472;

    // 6
    private final static int KEY_OVERHEAD_32 = 16;
    private final static int KEY_OVERHEAD_64 = 24;

    // 24
    private final static int LOCK_OVERHEAD_32 = 24;
    private final static int LOCK_OVERHEAD_64 = 48;

    // 25
    private final static int LOCKINFO_OVERHEAD_32 = 16;
    private final static int LOCKINFO_OVERHEAD_64 = 32;

    // 37
    private final static int WRITE_LOCKINFO_OVERHEAD_32 = 24;
    private final static int WRITE_LOCKINFO_OVERHEAD_64 = 32;

    /* 
     * Txn memory is the size for the Txn + a hashmap entry
     * overhead for being part of the transaction table. 
     */
    // 15
    private final static int TXN_OVERHEAD_32_14 = 167;
    private final static int TXN_OVERHEAD_32_15 = 175;
    private final static int TXN_OVERHEAD_64_15 = 293;

    // 26
    private final static int CHECKPOINT_REFERENCE_SIZE_32_14 = 32 +
        HASHSET_ENTRY_OVERHEAD_32;
    private final static int CHECKPOINT_REFERENCE_SIZE_32_15 = 40 +
        HASHSET_ENTRY_OVERHEAD_32;
    private final static int CHECKPOINT_REFERENCE_SIZE_64_15 = 56 +
        HASHSET_ENTRY_OVERHEAD_64;

    /* The per-log-file bytes used in UtilizationProfile. */
    // 29 / 500
    private final static int UTILIZATION_PROFILE_ENTRY_32 = 96;
    private final static int UTILIZATION_PROFILE_ENTRY_64 = 144;

    /* Tracked File Summary overheads. */
    // 31
    private final static int TFS_LIST_INITIAL_OVERHEAD_32 = 464;
    private final static int TFS_LIST_INITIAL_OVERHEAD_64 = 504;

    // 30
    private final static int TFS_LIST_SEGMENT_OVERHEAD_32 = 440;
    private final static int TFS_LIST_SEGMENT_OVERHEAD_64 = 464;

    // 33
    private final static int LN_INFO_OVERHEAD_32 = 24;
    private final static int LN_INFO_OVERHEAD_64 = 48;

    /* Approximate element size in an ArrayList of Long. */
    // (28 - 27) / 100
    private final static int LONG_LIST_PER_ITEM_OVERHEAD_32 = 20;
    private final static int LONG_LIST_PER_ITEM_OVERHEAD_64 = 32;

    public final static int LONG_OVERHEAD;
    public final static int BYTE_ARRAY_OVERHEAD;
    public final static int OBJECT_OVERHEAD;
    public final static int ARRAY_ITEM_OVERHEAD;
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
    public final static int LOCK_OVERHEAD;
    public final static int LOCKINFO_OVERHEAD;
    public final static int WRITE_LOCKINFO_OVERHEAD;
    public final static int TXN_OVERHEAD;
    public final static int CHECKPOINT_REFERENCE_SIZE;
    public final static int UTILIZATION_PROFILE_ENTRY;
    public final static int TFS_LIST_INITIAL_OVERHEAD;
    public final static int TFS_LIST_SEGMENT_OVERHEAD;
    public final static int LN_INFO_OVERHEAD;
    public final static int LONG_LIST_PER_ITEM_OVERHEAD;

    private final static String JVM_ARCH_PROPERTY = "sun.arch.data.model";
    private final static String FORCE_JVM_ARCH = "je.forceJVMArch";

    static {
	boolean is64 = false;
	boolean isJVM14 = (LatchSupport.getJava5LatchClass() == null);
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
	    if (isJVM14) {
		RuntimeException RE = new RuntimeException
		    ("1.4 based 64 bit JVM not supported");
		RE.printStackTrace(System.err);
		throw RE;
	    }
	    LONG_OVERHEAD = LONG_OVERHEAD_64;
	    BYTE_ARRAY_OVERHEAD = BYTE_ARRAY_OVERHEAD_64;
	    OBJECT_OVERHEAD = OBJECT_OVERHEAD_64;
	    ARRAY_ITEM_OVERHEAD = ARRAY_ITEM_OVERHEAD_64;
	    HASHMAP_OVERHEAD = HASHMAP_OVERHEAD_64;
	    HASHMAP_ENTRY_OVERHEAD = HASHMAP_ENTRY_OVERHEAD_64;
	    HASHSET_OVERHEAD = HASHSET_OVERHEAD_64;
	    HASHSET_ENTRY_OVERHEAD = HASHSET_ENTRY_OVERHEAD_64;
	    TWOHASHMAPS_OVERHEAD = TWOHASHMAPS_OVERHEAD_64;
	    TREEMAP_OVERHEAD = TREEMAP_OVERHEAD_64;
	    TREEMAP_ENTRY_OVERHEAD = TREEMAP_ENTRY_OVERHEAD_64;
	    MAPLN_OVERHEAD = MAPLN_OVERHEAD_64;
	    LN_OVERHEAD = LN_OVERHEAD_64;
	    DUPCOUNTLN_OVERHEAD = DUPCOUNTLN_OVERHEAD_64;
	    BIN_FIXED_OVERHEAD = BIN_FIXED_OVERHEAD_64_15;
	    DIN_FIXED_OVERHEAD = DIN_FIXED_OVERHEAD_64_15;
	    DBIN_FIXED_OVERHEAD = DBIN_FIXED_OVERHEAD_64_15;
	    IN_FIXED_OVERHEAD = IN_FIXED_OVERHEAD_64_15;
	    TXN_OVERHEAD = TXN_OVERHEAD_64_15;
	    CHECKPOINT_REFERENCE_SIZE = CHECKPOINT_REFERENCE_SIZE_64_15;
	    KEY_OVERHEAD = KEY_OVERHEAD_64;
	    LOCK_OVERHEAD = LOCK_OVERHEAD_64;
	    LOCKINFO_OVERHEAD = LOCKINFO_OVERHEAD_64;
	    WRITE_LOCKINFO_OVERHEAD = WRITE_LOCKINFO_OVERHEAD_64;
	    UTILIZATION_PROFILE_ENTRY = UTILIZATION_PROFILE_ENTRY_64;
	    TFS_LIST_INITIAL_OVERHEAD = TFS_LIST_INITIAL_OVERHEAD_64;
	    TFS_LIST_SEGMENT_OVERHEAD = TFS_LIST_SEGMENT_OVERHEAD_64;
	    LN_INFO_OVERHEAD = LN_INFO_OVERHEAD_64;
	    LONG_LIST_PER_ITEM_OVERHEAD = LONG_LIST_PER_ITEM_OVERHEAD_64;
	} else {
	    LONG_OVERHEAD = LONG_OVERHEAD_32;
	    BYTE_ARRAY_OVERHEAD = BYTE_ARRAY_OVERHEAD_32;
	    OBJECT_OVERHEAD = OBJECT_OVERHEAD_32;
	    ARRAY_ITEM_OVERHEAD = ARRAY_ITEM_OVERHEAD_32;
	    HASHMAP_OVERHEAD = HASHMAP_OVERHEAD_32;
	    HASHMAP_ENTRY_OVERHEAD = HASHMAP_ENTRY_OVERHEAD_32;
	    HASHSET_OVERHEAD = HASHSET_OVERHEAD_32;
	    HASHSET_ENTRY_OVERHEAD = HASHSET_ENTRY_OVERHEAD_32;
	    TWOHASHMAPS_OVERHEAD = TWOHASHMAPS_OVERHEAD_32;
	    TREEMAP_OVERHEAD = TREEMAP_OVERHEAD_32;
	    TREEMAP_ENTRY_OVERHEAD = TREEMAP_ENTRY_OVERHEAD_32;
	    MAPLN_OVERHEAD = MAPLN_OVERHEAD_32;
	    LN_OVERHEAD = LN_OVERHEAD_32;
	    DUPCOUNTLN_OVERHEAD = DUPCOUNTLN_OVERHEAD_32;
	    if (isJVM14) {
		BIN_FIXED_OVERHEAD = BIN_FIXED_OVERHEAD_32_14;
		DIN_FIXED_OVERHEAD = DIN_FIXED_OVERHEAD_32_14;
		DBIN_FIXED_OVERHEAD = DBIN_FIXED_OVERHEAD_32_14;
		IN_FIXED_OVERHEAD = IN_FIXED_OVERHEAD_32_14;
		TXN_OVERHEAD = TXN_OVERHEAD_32_14;
		CHECKPOINT_REFERENCE_SIZE = CHECKPOINT_REFERENCE_SIZE_32_14;
	    } else {
		BIN_FIXED_OVERHEAD = BIN_FIXED_OVERHEAD_32_15;
		DIN_FIXED_OVERHEAD = DIN_FIXED_OVERHEAD_32_15;
		DBIN_FIXED_OVERHEAD = DBIN_FIXED_OVERHEAD_32_15;
		IN_FIXED_OVERHEAD = IN_FIXED_OVERHEAD_32_15;
		TXN_OVERHEAD = TXN_OVERHEAD_32_15;
		CHECKPOINT_REFERENCE_SIZE = CHECKPOINT_REFERENCE_SIZE_32_15;
	    }
	    KEY_OVERHEAD = KEY_OVERHEAD_32;
	    LOCK_OVERHEAD = LOCK_OVERHEAD_32;
	    LOCKINFO_OVERHEAD = LOCKINFO_OVERHEAD_32;
	    WRITE_LOCKINFO_OVERHEAD = WRITE_LOCKINFO_OVERHEAD_32;
	    UTILIZATION_PROFILE_ENTRY = UTILIZATION_PROFILE_ENTRY_32;
	    TFS_LIST_INITIAL_OVERHEAD = TFS_LIST_INITIAL_OVERHEAD_32;
	    TFS_LIST_SEGMENT_OVERHEAD = TFS_LIST_SEGMENT_OVERHEAD_32;
	    LN_INFO_OVERHEAD = LN_INFO_OVERHEAD_32;
	    LONG_LIST_PER_ITEM_OVERHEAD = LONG_LIST_PER_ITEM_OVERHEAD_32;
	}
    }

    /* public for unit tests. */
    public final static long MIN_MAX_MEMORY_SIZE = 96 * 1024;
    public final static String MIN_MAX_MEMORY_SIZE_STRING =
	Long.toString(MIN_MAX_MEMORY_SIZE);

    private final static long N_64MB = (1 << 26);

    /*
     * Note that this class contains long fields that are accessed by multiple
     * threads, and access to these fields is intentionally not synchronized.
     * Although inaccuracies may result, correcting them is not worth the cost
     * of synchronizing every time we adjust the treeMemoryUsage or
     * miscMemoryUsage.
     */

    /* 
     * Amount of memory cached for tree objects.
     */
    private long treeMemoryUsage;

    /*
     * Amount of memory cached for Txn and other objects.
     */
    private long miscMemoryUsage;

    /*
     * Used to protect treeMemoryUsage and miscMemoryUsage updates.
     */
    private Object memoryUsageSynchronizer = new Object();

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
    private long maxMemory;
    private long criticalThreshold; // experimental mark for sync eviction.
                           
    /* Memory available to log buffers. */
    private long logBufferBudget;
                           
    /* Maximum allowed use of the misc budget by the UtilizationTracker. */
    private long trackerBudget;

    /*
     * Memory to hold internal nodes and misc memory (locks), controlled by the
     * evictor.  Does not include the log buffers.
     */
    private long cacheBudget;
    
    /* 
     * Overheads that are a function of node capacity.
     */
    private long inOverhead;
    private long binOverhead;
    private long dinOverhead;
    private long dbinOverhead;

    private EnvironmentImpl envImpl;

    MemoryBudget(EnvironmentImpl envImpl,
                 DbConfigManager configManager) 
        throws DatabaseException {

        this.envImpl = envImpl;

        /* Request notification of mutable property changes. */
        envImpl.addConfigObserver(this);

        /* Peform first time budget initialization. */
        reset(configManager, true);

        /*
         * Calculate IN and BIN overheads, which are a function of
         * capacity. These values are stored in this class so that they can be
         * calculated once per environment. The logic to do the calculations is
         * left in the respective node classes so it can be done properly in
         * the domain of those objects.
         */
        inOverhead = IN.computeOverhead(configManager);
        binOverhead = BIN.computeOverhead(configManager);
        dinOverhead = DIN.computeOverhead(configManager);
        dbinOverhead = DBIN.computeOverhead(configManager);
    }

    /**
     * Respond to config updates.
     */
    public void envConfigUpdate(DbConfigManager configManager) 
        throws DatabaseException {

        /*
         * Reinitialize the cache budget and the log buffer pool, in that
         * order.  Do not reset the log buffer pool if the log buffer budget
         * hasn't changed, since that is expensive and may cause I/O.
         */
        long oldLogBufferBudget = logBufferBudget;
        reset(configManager, false);
        if (oldLogBufferBudget != logBufferBudget) {
            envImpl.getLogManager().resetPool(configManager);
        }
    }

    /**
     * Initialize at construction time and when the cache is resized.
     */
    private void reset(DbConfigManager configManager,
		       boolean resetLockMemoryUsage)
        throws DatabaseException {

        /* 
         * Calculate the total memory allotted to JE.
         * 1. If je.maxMemory is specified, use that. Check that it's
         * not more than the jvm memory.
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
	    newLogBufferBudget = newMaxMemory >> 4;
	} else if (newLogBufferBudget > newMaxMemory / 2) {
            newLogBufferBudget = newMaxMemory / 2;
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
            (newMaxMemory * 
             envImpl.getConfigManager().getInt
                (EnvironmentParams.CLEANER_DETAIL_MAX_MEMORY_PERCENTAGE))/100;

        /* 
         * If all has gone well, update the budget fields.  Once the log buffer
         * budget is determined, the remainder of the memory is left for tree
         * nodes.
         */
        maxMemory = newMaxMemory;
        criticalThreshold = newCriticalThreshold;
        logBufferBudget = newLogBufferBudget;
        trackerBudget = newTrackerBudget;
        cacheBudget = newMaxMemory - newLogBufferBudget;
	if (resetLockMemoryUsage) {
	    nLockTables = 
		configManager.getInt(EnvironmentParams.N_LOCK_TABLES);
	    lockMemoryUsage = new long[nLockTables];
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
     * Initialize the starting environment memory state.
     */
    void initCacheMemoryUsage() 
        throws DatabaseException {

        /* 
         * The memoryUsageSynchronizer mutex is at the bottom of the lock
         * hierarchy and should always be taken last. Since
         * calcTreeCacheUsage() takes the INList latch, we get the usage value
         * outside the mutex and then assign the value, in order to preserve
         * correct lock hierarchy.  That said, initCacheMemoryUsage should be
         * called while the system is quiescent, and there should be no lock
         * conflict possible even if the locks were taken in reverse. [#15364]
         */
        long calculatedUsage = calcTreeCacheUsage(); 
	synchronized (memoryUsageSynchronizer) {
	    treeMemoryUsage = calculatedUsage;
	}
        assert LatchSupport.countLatchesHeld() == 0;
    }

    /**
     * Public for testing.
     */
    public long calcTreeCacheUsage() 
        throws DatabaseException {

        long totalSize = 0;
        INList inList = envImpl.getInMemoryINs();

        inList.latchMajor();
        try {
            Iterator iter = inList.iterator();
            while (iter.hasNext()) {
                IN in = (IN) iter.next();
                long size = in.getInMemorySize();
                totalSize += size;
            }
        } finally {
            inList.releaseMajorLatch();
        }
        return totalSize;
    }

    /**
     * Update the environment wide tree memory count, wake up the evictor if
     * necessary.
     * @param increment note that increment may be negative.
     */
    public void updateTreeMemoryUsage(long increment) {
	synchronized (memoryUsageSynchronizer) {
	    treeMemoryUsage += increment;
	}
        if (getCacheMemoryUsage() > cacheBudget) {
            envImpl.alertEvictor();
        }
    }

    /**
     * Update the environment wide misc memory count, wake up the evictor if
     * necessary.
     * @param increment note that increment may be negative.
     */
    public void updateMiscMemoryUsage(long increment) {
	synchronized (memoryUsageSynchronizer) {
	    miscMemoryUsage += increment;
	}
        if (getCacheMemoryUsage() > cacheBudget) {
            envImpl.alertEvictor();
        }
    }

    public void updateLockMemoryUsage(long increment, int lockTableIndex) {
	lockMemoryUsage[lockTableIndex] += increment;
        if (getCacheMemoryUsage() > cacheBudget) {
            envImpl.alertEvictor();
        }
    }

    public long accumulateNewUsage(IN in, long newSize) {
        return in.getInMemorySize() + newSize;
    }

    public void refreshTreeMemoryUsage(long newSize) {
	synchronized (memoryUsageSynchronizer) {
	    treeMemoryUsage = newSize;
	}
    }

    public long getCacheMemoryUsage() {
	long accLockMemoryUsage = accumulateLockUsage();

	return treeMemoryUsage + miscMemoryUsage + accLockMemoryUsage;
    }

    private long accumulateLockUsage() {
	long accLockMemoryUsage = 0;
	if (nLockTables == 1) {
	    accLockMemoryUsage = lockMemoryUsage[0];
	} else {
	    for (int i = 0; i < nLockTables; i++) {
		accLockMemoryUsage += lockMemoryUsage[i];
	    }
	}
        return accLockMemoryUsage;
    }

    /**
     * Used for unit testing.
     */
    public long getTreeMemoryUsage() {
        return treeMemoryUsage;
    }

    /**
     * Used for unit testing.
     */
    public long getMiscMemoryUsage() {
        return miscMemoryUsage;
    }

    public long getLogBufferBudget() {
        return logBufferBudget;
    }

    public long getTrackerBudget() {
        return trackerBudget;
    }

    public long getMaxMemory() {
        return maxMemory;
    }

    public long getCriticalThreshold() {
        return criticalThreshold;
    }

    public long getCacheBudget() {
        return cacheBudget;
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
     * Returns the memory size occupied by a byte array of a given length.
     */
    public static int byteArraySize(int arrayLen) {

        /*
         * BYTE_ARRAY_OVERHEAD accounts for 4 bytes of data.  Data larger than
         * 4 bytes is allocated in 8 byte increments.
         */
        int size = BYTE_ARRAY_OVERHEAD;
        if (arrayLen > 4) {
            size += ((arrayLen - 4 + 7) / 8) * 8;
        }

        return size;
    }

    void loadStats(StatsConfig config, EnvironmentStats stats) {
        stats.setCacheDataBytes(getCacheMemoryUsage());
        stats.setAdminBytes(miscMemoryUsage);
        stats.setLockBytes(accumulateLockUsage());
    }
}
