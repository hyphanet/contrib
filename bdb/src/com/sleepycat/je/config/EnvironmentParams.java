/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: EnvironmentParams.java,v 1.84.2.4 2007/07/02 19:54:49 mark Exp $
 */

package com.sleepycat.je.config;

import java.io.File;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeSet;

/**
 * Javadoc for this public class is generated
 * via the doc templates in the doc_src directory.
 */
public class EnvironmentParams {

    /*
     * The map of supported environment parameters where the key is parameter 
     * name and the data is the configuration parameter object. Put first,
     * before any declarations of ConfigParams.
     */
    public final static Map SUPPORTED_PARAMS = new HashMap();

    /*
     * Environment
     */
    public static final LongConfigParam MAX_MEMORY =
        new LongConfigParam("je.maxMemory",
                            null,           // min
                            null,           // max
                            new Long(0),    // default uses je.maxMemoryPercent
                            true,           // mutable
                            false,          // forReplication
      "# Specify the cache size in bytes, as an absolute number. The system\n"+
      "# attempts to stay within this budget and will evict database\n" +
      "# objects when it comes within a prescribed margin of the limit.\n" +
      "# By default, this parameter is 0 and JE instead sizes the cache\n" +
      "# proportionally to the memory available to the JVM, based on\n"+
      "# je.maxMemoryPercent.");

    public static final IntConfigParam MAX_MEMORY_PERCENT =
        new IntConfigParam("je.maxMemoryPercent",
                           new Integer(1),    // min 
                           new Integer(90),   // max
                           new Integer(60),   // default
                           true,              // mutable
                           false,             // forReplication
     "# By default, JE sizes the cache as a percentage of the maximum\n" +
     "# memory available to the JVM. For example, if the JVM is\n" + 
     "# started with -Xmx128M, the cache size will be\n" +
     "#           (je.maxMemoryPercent * 128M) / 100\n" +
     "# Setting je.maxMemory to an non-zero value will override\n" +
     "# je.maxMemoryPercent");
     
    public static final BooleanConfigParam ENV_RECOVERY =
        new BooleanConfigParam("je.env.recovery",
                               true,          // default
                               false,         // mutable
                               false,         // forReplication
     "# If true, an environment is created with recovery and the related\n" +
     "# daemons threads enabled.");
     
    public static final BooleanConfigParam ENV_RECOVERY_FORCE_CHECKPOINT =
        new BooleanConfigParam("je.env.recoveryForceCheckpoint",
                               false,         // default
                               false,         // mutable
                               false,         // forReplication
     "# If true, a checkpoint is forced following recovery, even if the\n" +
     "# log ends with a checkpoint.");
     
    public static final BooleanConfigParam ENV_RUN_INCOMPRESSOR =
        new BooleanConfigParam("je.env.runINCompressor",
                               true,          // default
                               true,          // mutable
                               false,         // forReplication
     "# If true, starts up the INCompressor.\n" +
     "# This parameter is true by default");
     
    /* @deprecated As of 2.0, eviction is performed in-line. */
    public static final BooleanConfigParam ENV_RUN_EVICTOR =
        new BooleanConfigParam("je.env.runEvictor",
                               false,        // default
                               true,         // mutable
                               false,        // forReplication
     "# If true, starts up the evictor.\n" +
     "# This parameter is false by default\n" +
     "# (deprecated, eviction is performed in-line");
     
    public static final BooleanConfigParam ENV_RUN_CHECKPOINTER =
        new BooleanConfigParam("je.env.runCheckpointer",
                               true,        // default
                               true,        // mutable
                               false,       // forReplication
     "# If true, starts up the checkpointer.\n" +
     "# This parameter is true by default");

    public static final BooleanConfigParam ENV_RUN_CLEANER =
        new BooleanConfigParam("je.env.runCleaner",
                               true,        // default
                               true,        // mutable
                               false,       // forReplication
     "# If true, starts up the cleaner.\n" +
     "# This parameter is true by default");
     
    public static final IntConfigParam ENV_BACKGROUND_READ_LIMIT =
        new IntConfigParam("je.env.backgroundReadLimit",
                            new Integer(0),                 // min
                            new Integer(Integer.MAX_VALUE), // max
                            new Integer(0),                 // default
                            true,                           // mutable
                            false,                          // forReplication
     "# The maximum number of read operations performed by JE background\n" +
     "# activities (e.g., cleaning) before sleeping to ensure that\n" +
     "# application threads can perform I/O.\n" +
     "# If zero (the default) then no limitation on I/O is enforced.\n" +
     "# See je.env.backgroundSleepInterval.");
     
    public static final IntConfigParam ENV_BACKGROUND_WRITE_LIMIT =
        new IntConfigParam("je.env.backgroundWriteLimit",
                            new Integer(0),                 // min
                            new Integer(Integer.MAX_VALUE), // max
                            new Integer(0),                 // default
                            true,                           // mutable
                            false,                          // forReplication
     "# The maximum number of write operations performed by JE background\n" +
     "# activities (e.g., checkpointing and eviction) before sleeping to\n" +
     "# ensure that application threads can perform I/O.\n" +
     "# If zero (the default) then no limitation on I/O is enforced.\n" +
     "# See je.env.backgroundSleepInterval.");
     
    public static final LongConfigParam ENV_BACKGROUND_SLEEP_INTERVAL =
        new LongConfigParam("je.env.backgroundSleepInterval",
                           new Long(1000),                  // min
                           new Long(Long.MAX_VALUE),        // max
                           new Long(1000),                  // default
                           true,                            // mutable
                           false,                           // forReplication
     "# The number of microseconds that JE background activities will\n" +
     "# sleep when the je.env.backgroundWriteLimit or backgroundReadLimit\n" +
     "# is reached.  If  je.env.backgroundWriteLimit and\n" +
     "# backgroundReadLimit are zero, this setting is not used.\n" +
     "# By default this setting is 1000 or 1 millisecond.");

    public static final BooleanConfigParam ENV_CHECK_LEAKS =
        new BooleanConfigParam("je.env.checkLeaks",
                               true,              // default
                               false,             // mutable
                               false,             // forReplication
     "# Debugging support: check leaked locks and txns at env close.");

    public static final BooleanConfigParam ENV_FORCED_YIELD =
        new BooleanConfigParam("je.env.forcedYield",
                               false,             // default
                               false,             // mutable
                               false,             // forReplication
     "# Debugging support: call Thread.yield() at strategic points.");

    public static final BooleanConfigParam ENV_INIT_TXN =
        new BooleanConfigParam("je.env.isTransactional",
                               false,             // default
                               false,             // mutable
                               false,             // forReplication
     "# If true, create the environment w/ transactions.");

    public static final BooleanConfigParam ENV_INIT_LOCKING =
        new BooleanConfigParam("je.env.isLocking",
                               true,              // default
                               false,             // mutable
                               false,             // forReplication
     "# If true, create the environment with locking.");

    public static final BooleanConfigParam ENV_RDONLY =
        new BooleanConfigParam("je.env.isReadOnly",
                               false,             // default
                               false,             // mutable
                               false,             // forReplication
     "# If true, create the environment read only.");

    public static final BooleanConfigParam ENV_FAIR_LATCHES =
        new BooleanConfigParam("je.env.fairLatches",
                               false,             // default
                               false,             // mutable
                               false,             // forReplication
     "# If true, use latches instead of synchronized blocks to\n" +
     "# implement the lock table and log write mutexes. Latches require\n" +
     "# that threads queue to obtain the mutex in question and\n" +
     "# therefore guarantee that there will be no mutex starvation, but \n" +
     "# do incur a performance penalty. Latches should not be necessary in\n"+
     "# most cases, so synchronized blocks are the default. An application\n" +
     "# that puts heavy load on JE with threads with different thread\n"+
     "# priorities might find it useful to use latches.  In a Java 5 JVM,\n" +
     "# where java.util.concurrent.locks.ReentrantLock is used for the\n" +
     "# latch implementation, this parameter will determine whether they\n" +
     "# are 'fair' or not.  This parameter is 'static' across all\n" +
     "# environments.\n");
    
    public static final BooleanConfigParam ENV_SHARED_LATCHES =
        new BooleanConfigParam("je.env.sharedLatches",
                               false,            // default
                               false,            // mutable
                               false,            // forReplication
     "# If true, use shared latches for Internal Nodes (INs).\n");
    
    public static final BooleanConfigParam ENV_DB_EVICTION =
        new BooleanConfigParam("je.env.dbEviction",
                               false,            // default
                               false,            // mutable
                               false,            // forReplication
     "# *** Experimental and not fully tested in 3.2.x. ***\n" +
     "# If true, enable eviction of metadata for closed databases.\n" +
     "# The default for JE 3.2.x is false but will be changed to true\n" +
     "# in JE 3.3 and above.");

    public static final IntConfigParam ADLER32_CHUNK_SIZE =
        new IntConfigParam("je.adler32.chunkSize",
                           new Integer(0),       // min 
                           new Integer(1 << 20), // max
                           new Integer(0),       // default
                           true,                 // mutable
                           false,                // forReplication
     "# By default, JE passes an entire log record to the Adler32 class\n" +
     "# for checksumming.  This can cause problems with the GC in some\n" +
     "# cases if the records are large and there is concurrency.  Setting\n" +
     "# this parameter will cause JE to pass chunks of the log record to\n" +
     "# the checksumming class so that the GC does not block.  0 means\n" +
     "# do not chunk.\n");
     
    /*
     * Database Logs
     */
    /* default: 2k * NUM_LOG_BUFFERS */
    public static final int MIN_LOG_BUFFER_SIZE = 2048;
    private static final int NUM_LOG_BUFFERS_DEFAULT = 3;
    public static final long LOG_MEM_SIZE_MIN =
        NUM_LOG_BUFFERS_DEFAULT * MIN_LOG_BUFFER_SIZE;
    public static final String LOG_MEM_SIZE_MIN_STRING =
        Long.toString(LOG_MEM_SIZE_MIN);

    public static final LongConfigParam LOG_MEM_SIZE =
        new LongConfigParam("je.log.totalBufferBytes",
                            new Long(LOG_MEM_SIZE_MIN),// min
                            null,              // max
                            new Long(0),       // by default computed
                                               // from je.maxMemory
                            false,             // mutable
                            false,             // forReplication
     "# The total memory taken by log buffers, in bytes. If 0, use\n" +
     "# 7% of je.maxMemory");

    public static final IntConfigParam NUM_LOG_BUFFERS =
        new IntConfigParam("je.log.numBuffers",
                           new Integer(2),     // min
                           null,               // max
                           new Integer(NUM_LOG_BUFFERS_DEFAULT), // default
                           false,              // mutable
                           false,              // forReplication
     "# The number of JE log buffers");

    public static final IntConfigParam LOG_BUFFER_MAX_SIZE =
        new IntConfigParam("je.log.bufferSize",
                           new Integer(1<<10),  // min
                           null,                // max
                           new Integer(1<<20),  // default
                           false,               // mutable
                           false,               // forReplication
     "# maximum starting size of a JE log buffer");

    public static final IntConfigParam LOG_FAULT_READ_SIZE =
        new IntConfigParam("je.log.faultReadSize",
                           new Integer(32),   // min
                           null,              // max
                           new Integer(2048), // default
                           false,             // mutable
                           false,             // forReplication
     "# The buffer size for faulting in objects from disk, in bytes.");

    public static final IntConfigParam LOG_ITERATOR_READ_SIZE =
        new IntConfigParam("je.log.iteratorReadSize",
                           new Integer(128),  // min
                           null,              // max
                           new Integer(8192), // default
                           false,             // mutable
                           false,             // forReplication
     "# The read buffer size for log iterators, which are used when\n" +
     "# scanning the log during activities like log cleaning and\n" +
     "# environment open, in bytes. This may grow as the system encounters\n" +
     "# larger log entries");

    public static final IntConfigParam LOG_ITERATOR_MAX_SIZE =
        new IntConfigParam("je.log.iteratorMaxSize",
                           new Integer(128),  // min
                           null,              // max
                           new Integer(16777216), // default
                           false,             // mutable
                           false,             // forReplication
     "# The maximum read buffer size for log iterators, which are used\n" +
     "# when scanning the log during activities like log cleaning\n" +
     "# and environment open, in bytes.");
     
    public static final LongConfigParam LOG_FILE_MAX =
        new LongConfigParam("je.log.fileMax",
                            new Long(1000000),     // min
                            new Long(4294967296L), // max
                            new Long(10000000),    // default
                            false,                 // mutable
                            false,                 // forReplication
     "# The maximum size of each individual JE log file, in bytes.");
     
    public static final BooleanConfigParam LOG_CHECKSUM_READ =
        new BooleanConfigParam("je.log.checksumRead",
                               true,               // default
                               false,              // mutable
                               false,              // forReplication
     "# If true, perform a checksum check when reading entries from log.");
     
    public static final BooleanConfigParam LOG_MEMORY_ONLY =
        new BooleanConfigParam("je.log.memOnly",
                               false,              // default
                               false,              // mutable
                               false,              // forReplication
     "# If true, operates in an in-memory test mode without flushing\n" +
     "# the log to disk. An environment directory must be specified, but\n" +
     "# it need not exist and no files are written.  The system operates\n" +
     "# until it runs out of memory, at which time an OutOfMemoryError\n" +
     "# is thrown.  Because the entire log is kept in memory, this mode\n" +
     "# is normally useful only for testing.");

    public static final IntConfigParam LOG_FILE_CACHE_SIZE = 
        new IntConfigParam("je.log.fileCacheSize",
                           new Integer(3),    // min
                           null,              // max
                           new Integer(100),  // default
                           false,             // mutable
                           false,             // forReplication
     "# The size of the file handle cache.");

    public static final LongConfigParam LOG_FSYNC_TIMEOUT =
        new LongConfigParam("je.log.fsyncTimeout",
                            new Long(10000L),  // min
                            null,              // max
                            new Long(500000L), // default
                            false,             // mutable
                            false,             // forReplication
     "# Timeout limit for group file sync, in microseconds.");
     
    public static final BooleanConfigParam LOG_USE_NIO =
        new BooleanConfigParam("je.log.useNIO",
                               false,          // default
                               false,          // mutable
                               false,          // forReplication
     "# If true (default is false) NIO is used for all file I/O.");
     
    public static final BooleanConfigParam LOG_DIRECT_NIO =
        new BooleanConfigParam("je.log.directNIO",
                               false,          // default
                               false,          // mutable
                               false,          // forReplication
     "# If true (default is false) direct NIO buffers are used.\n" +
     "# This setting is only used if je.log.useNIO=true.");

    public static final LongConfigParam LOG_CHUNKED_NIO =
        new LongConfigParam("je.log.chunkedNIO",
                            new Long(0L),      // min
                            new Long(1 << 26), // max (64M)
                            new Long(0L),      // default (no chunks)
                            false,             // mutable
                            false,             // forReplication
     "# If non-0 (default is 0) break all IO into chunks of this size.\n" +
     "# This setting is only used if je.log.useNIO=true.");

    public static final BooleanConfigParam LOG_DEFERREDWRITE_TEMP =
        new BooleanConfigParam("je.deferredWrite.temp",
                               false,          // default
                               false,          // mutable
                               false,          // forReplication
     "# *** Experimental and may be removed in a future release. ***\n" +
     "# If true, assume that deferred write database will never be\n" +
     "# used after an environment is closed. This permits a more efficient\n" +
     "# form of logging of deferred write objects that overflow to disk\n" +
     "# through cache eviction or Database.sync() and reduces log cleaner\n" +
     "# overhead.");

    /* 
     * Tree
     */
    public static final IntConfigParam NODE_MAX =
        new IntConfigParam("je.nodeMaxEntries",
                           new Integer(4),     // min
                           new Integer(32767), // max
                           new Integer(128),   // default
                           false,              // mutable
                           false,              // forReplication
     "# The maximum number of entries in an internal btree node.\n" +
     "# This can be set per-database using the DatabaseConfig object.");

    public static final IntConfigParam NODE_MAX_DUPTREE =
        new IntConfigParam("je.nodeDupTreeMaxEntries",
                           new Integer(4),     // min
                           new Integer(32767), // max
                           new Integer(128),   // default
                           false,              // mutable
                           false,              // forReplication
     "# The maximum number of entries in an internal dup btree node.\n" +
     "# This can be set per-database using the DatabaseConfig object.");

    public static final IntConfigParam BIN_MAX_DELTAS =
        new IntConfigParam("je.tree.maxDelta",
                           new Integer(0),     // min 
                           new Integer(100),   // max
                           new Integer(10),    // default
                           false,              // mutable
                           false,              // forReplication
     "# After this many deltas, logs a full version.");
     
    public static final IntConfigParam BIN_DELTA_PERCENT =
        new IntConfigParam("je.tree.binDelta",
                           new Integer(0),     // min 
                           new Integer(75),    // max
                           new Integer(25),    // default
                           false,              // mutable
                           false,              // forReplication
     "# If less than this percentage of entries are changed on a BIN,\n" +
     "# logs a delta instead of a full version.");

    /*
     * IN Compressor
     */
    public static final LongConfigParam COMPRESSOR_WAKEUP_INTERVAL =
        new LongConfigParam("je.compressor.wakeupInterval",
                            new Long(1000000),     // min
                            new Long(4294967296L), // max
                            new Long(5000000),     // default
                            false,                 // mutable
                            false,                 // forReplication
     "# The compressor wakeup interval in microseconds.");
     
    public static final IntConfigParam COMPRESSOR_RETRY =
        new IntConfigParam("je.compressor.deadlockRetry",
                           new Integer(0),                // min
                           new Integer(Integer.MAX_VALUE),// max
                           new Integer(3),                // default
                           false,                         // mutable
                           false,                         // forReplication
     "# Number of times to retry a compression run if a deadlock occurs.");

    public static final LongConfigParam COMPRESSOR_LOCK_TIMEOUT =
        new LongConfigParam("je.compressor.lockTimeout",
                            new Long(0),           // min
                            new Long(4294967296L), // max
                            new Long(500000L),     // default
                            false,                 // mutable
                            false,                 // forReplication
     "# The lock timeout for compressor transactions in microseconds.");

    public static final BooleanConfigParam COMPRESSOR_PURGE_ROOT =
        new BooleanConfigParam("je.compressor.purgeRoot",
                                           false,              // default
                               false,              // mutable
                               false,              // forReplication
     "# If true, when the compressor encounters an empty tree, the root\n" +
     "# node of the tree is deleted.");
     
    /*
     * Evictor
     */ 
    public static final LongConfigParam EVICTOR_EVICT_BYTES =
        new LongConfigParam("je.evictor.evictBytes",
                             new Long(1024),       // min
                             null,                 // max
                             new Long(524288),     // default
                             false,                // mutable
                            false,                 // forReplication
     "# When eviction happens, the evictor will push memory usage to this\n" +
     "# number of bytes below je.maxMemory.  The default is 512KB and the\n" +
     "# minimum is 1 KB (1024).");

    /* @deprecated As of 2.0, this is replaced by je.evictor.evictBytes */
    public static final IntConfigParam EVICTOR_USEMEM_FLOOR =
        new IntConfigParam("je.evictor.useMemoryFloor",
                           new Integer(50),        // min
                           new Integer(100),       // max
                           new Integer(95),        // default
                           false,                  // mutable
                           false,                  // forReplication
     "# When eviction happens, the evictor will push memory usage to this\n" +
     "# percentage of je.maxMemory." +
     "# (deprecated in favor of je.evictor.evictBytes");

    /* @deprecated As of 1.7.2, this is replaced by je.evictor.nodesPerScan */
    public static final IntConfigParam EVICTOR_NODE_SCAN_PERCENTAGE =
        new IntConfigParam("je.evictor.nodeScanPercentage",
                           new Integer(1),          // min
                           new Integer(100),        // max
                           new Integer(10),         // default
                           false,                   // mutable
                           false,                   // forReplication
     "# The evictor percentage of total nodes to scan per wakeup.\n" +
     "# (deprecated in favor of je.evictor.nodesPerScan");

    /* @deprecated As of 1.7.2, 1 node is chosen per scan. */
    public static final
        IntConfigParam EVICTOR_EVICTION_BATCH_PERCENTAGE =
        new IntConfigParam("je.evictor.evictionBatchPercentage",
                           new Integer(1),          // min
                           new Integer(100),        // max
                           new Integer(10),         // default
                           false,                   // mutable
                           false,                   // forReplication
     "# The evictor percentage of scanned nodes to evict per wakeup.\n" +
     "# (deprecated)");

    public static final IntConfigParam EVICTOR_NODES_PER_SCAN =
        new IntConfigParam("je.evictor.nodesPerScan",
                           new Integer(1),           // min
                           new Integer(1000),        // max
                           new Integer(10),          // default
                           false,                    // mutable
                           false,                    // forReplication
     "# The number of nodes in one evictor scan");

    /* @deprecated As of 2.0, eviction is performed in-line. */
    public static final
        IntConfigParam EVICTOR_CRITICAL_PERCENTAGE =
        new IntConfigParam("je.evictor.criticalPercentage",
                           new Integer(0),           // min
                           new Integer(1000),        // max
                           new Integer(0),           // default
                           false,                    // mutable
                           false,                    // forReplication
     "# At this percentage over the allotted cache, critical eviction\n" +
     "# will start." +
     "# (deprecated, eviction is performed in-line");

    public static final IntConfigParam EVICTOR_RETRY =
        new IntConfigParam("je.evictor.deadlockRetry",
                           new Integer(0),                // min
                           new Integer(Integer.MAX_VALUE),// max
                           new Integer(3),                // default
                           false,                         // mutable
                           false,                         // forReplication
     "# The number of times to retry the evictor if it runs into a deadlock.");
      
    public static final BooleanConfigParam EVICTOR_LRU_ONLY =
        new BooleanConfigParam("je.evictor.lruOnly",
                               true,                  // default
                               false,                 // mutable
                               false,                 // forReplication
     "# If true (the default), use an LRU-only policy to select nodes for\n" +
     "# eviction.  If false, select by Btree level first, and then by LRU.");

    public static final BooleanConfigParam EVICTOR_FORCED_YIELD =
        new BooleanConfigParam("je.evictor.forcedYield",
                               false,             // default
                               false,             // mutable
                               false,             // forReplication
     "# Call Thread.yield() at each check for cache overflow. This\n" +
     "# improves GC performance on some systems.  The default is false.");

    /*
     * Checkpointer
     */
    public static final LongConfigParam CHECKPOINTER_BYTES_INTERVAL =
        new LongConfigParam("je.checkpointer.bytesInterval",
                            new Long(0),               // min
                            new Long(Long.MAX_VALUE),  // max
                            new Long(20000000),        // default
                            false,                     // mutable
                            false,                     // forReplication
     "# Ask the checkpointer to run every time we write this many bytes\n" +
     "# to the log. If set, supercedes je.checkpointer.wakeupInterval. To\n" +
     "# use time based checkpointing, set this to 0.");

    public static final LongConfigParam CHECKPOINTER_WAKEUP_INTERVAL =
        new LongConfigParam("je.checkpointer.wakeupInterval",
                            new Long(1000000),     // min
                            new Long(4294967296L), // max
                            new Long(0),           // default
                            false,                 // mutable
                            false,                 // forReplication
     "# The checkpointer wakeup interval in microseconds. By default, this\n"+
     "# is inactive and we wakeup the checkpointer as a function of the\n" +
     "# number of bytes written to the log. (je.checkpointer.bytesInterval)");

    public static final IntConfigParam CHECKPOINTER_RETRY =
        new IntConfigParam("je.checkpointer.deadlockRetry",
                           new Integer(0),                 // miyn
                           new Integer(Integer.MAX_VALUE), // max
                           new Integer(3),                 // default
                           false,                          // mutable
                           false,                          // forReplication
     "# The number of times to retry a checkpoint if it runs into a deadlock.");
    /*
     * Cleaner
     */
    public static final IntConfigParam CLEANER_MIN_UTILIZATION =
        new IntConfigParam("je.cleaner.minUtilization",
                           new Integer(0),           // min
                           new Integer(90),          // max
                           new Integer(50),          // default
                           true,                     // mutable
                           false,                    // forReplication
     "# The cleaner will keep the total disk space utilization percentage\n" +
     "# above this value. The default is set to 50 percent.");

    public static final IntConfigParam CLEANER_MIN_FILE_UTILIZATION =
        new IntConfigParam("je.cleaner.minFileUtilization",
                           new Integer(0),           // min
                           new Integer(50),          // max
                           new Integer(5),           // default
                           true,                     // mutable
                           false,                    // forReplication
     "# A log file will be cleaned if its utilization percentage is below\n" +
     "# this value, irrespective of total utilization. The default is\n" +
     "# set to 5 percent.");

    public static final LongConfigParam CLEANER_BYTES_INTERVAL =
        new LongConfigParam("je.cleaner.bytesInterval",
                            new Long(0),              // min
                            new Long(Long.MAX_VALUE), // max
                            new Long(0),              // default
                            true,                     // mutable
                            false,                    // forReplication
     "# The cleaner checks disk utilization every time we write this many\n" +
     "# bytes to the log.  If zero (and by default) it is set to the\n" +
     "# je.log.fileMax value divided by four.");
      
    public static final BooleanConfigParam CLEANER_FETCH_OBSOLETE_SIZE =
        new BooleanConfigParam("je.cleaner.fetchObsoleteSize",
                               false, // default
                               true,  // mutable
                               false, // forReplication
     "# If true, the cleaner will fetch records to determine their size\n" +
     "# to more accurately calculate log utilization.  This setting is\n" +
     "# used during DB truncation/removal and during recovery, and will\n" +
     "# cause more I/O during those operations when set to true.");

    public static final IntConfigParam CLEANER_DEADLOCK_RETRY =
        new IntConfigParam("je.cleaner.deadlockRetry",
                           new Integer(0),                // min
                           new Integer(Integer.MAX_VALUE),// max
                           new Integer(3),                // default
                           true,                          // mutable
                           false,                         // forReplication
     "# The number of times to retry cleaning if a deadlock occurs.\n" +
     "# The default is set to 3.");

    public static final LongConfigParam CLEANER_LOCK_TIMEOUT =
        new LongConfigParam("je.cleaner.lockTimeout",
                            new Long(0),            // min
                            new Long(4294967296L),  // max
                            new Long(500000L),      // default
                            true,                   // mutable
                            false,                  // forReplication
     "# The lock timeout for cleaner transactions in microseconds.\n" +
     "# The default is set to 0.5 seconds.");
      
    public static final BooleanConfigParam CLEANER_REMOVE =
        new BooleanConfigParam("je.cleaner.expunge",
                               true,                 // default
                               true,                 // mutable
                               false,                // forReplication
     "# If true, the cleaner deletes log files after successful cleaning.\n" +
     "# If false, the cleaner changes log file extensions to .DEL\n" +
     "# instead of deleting them. The default is set to true.");

    /* @deprecated As of 1.7.1, no longer used. */
    public static final IntConfigParam CLEANER_MIN_FILES_TO_DELETE =
        new IntConfigParam("je.cleaner.minFilesToDelete",
                           new Integer(1),           // min
                           new Integer(1000000),     // max
                           new Integer(5),           // default
                           false,                    // mutable
                           false,         // forReplication
     "# (deprecated, no longer used");

    /* @deprecated As of 2.0, no longer used. */
    public static final IntConfigParam CLEANER_RETRIES =
        new IntConfigParam("je.cleaner.retries",
                           new Integer(0),           // min
                           new Integer(1000),        // max
                           new Integer(10),          // default
                           false,                    // mutable
                           false,         // forReplication
     "# (deprecated, no longer used");

    /* @deprecated As of 2.0, no longer used. */
    public static final IntConfigParam CLEANER_RESTART_RETRIES =
        new IntConfigParam("je.cleaner.restartRetries",
                           new Integer(0),           // min
                           new Integer(1000),        // max
                           new Integer(5),           // default
                           false,                    // mutable
                           false,         // forReplication
     "# (deprecated, no longer used");

    public static final IntConfigParam CLEANER_MIN_AGE =
        new IntConfigParam("je.cleaner.minAge",
                           new Integer(1),           // min
                           new Integer(1000),        // max
                           new Integer(2),           // default
                           true,                     // mutable
                           false,                    // forReplication
     "# The minimum age of a file (number of files between it and the\n" +
     "# active file) to qualify it for cleaning under any conditions.\n" +
     "# The default is set to 2.");
      
    public static final BooleanConfigParam CLEANER_CLUSTER =
        new BooleanConfigParam("je.cleaner.cluster",
                               false,               // default
                               true,                // mutable
                               false,               // forReplication
     "# *** Experimental and may be removed in a future release. ***\n" +
     "# If true, eviction and checkpointing will cluster records by key\n" +
     "# value, migrating them from low utilization files if they are\n" +
     "# resident.\n" +
     "# The cluster and clusterAll properties may not both be set to true.");
      
    public static final BooleanConfigParam CLEANER_CLUSTER_ALL =
        new BooleanConfigParam("je.cleaner.clusterAll",
                               false,              // default
                               true,               // mutable
                               false,              // forReplication
     "# *** Experimental and may be removed in a future release. ***\n" +
     "# If true, eviction and checkpointing will cluster records by key\n" +
     "# value, migrating them from low utilization files whether or not\n" +
     "# they are resident.\n" +
     "# The cluster and clusterAll properties may not both be set to true.");
      
    public static final IntConfigParam CLEANER_MAX_BATCH_FILES =
        new IntConfigParam("je.cleaner.maxBatchFiles",
                           new Integer(0),         // min
                           new Integer(100000),    // max
                           new Integer(0),         // default
                           true,                   // mutable
                           false,                  // forReplication
     "# The maximum number of log files in the cleaner's backlog, or\n" +
     "# zero if there is no limit.  Changing this property can impact the\n" +
     "# performance of some out-of-memory applications.");

    public static final IntConfigParam CLEANER_READ_SIZE = 
        new IntConfigParam("je.cleaner.readSize",
                           new Integer(128),  // min
                           null,              // max
                           new Integer(0),    // default
                           true,              // mutable
                           false,             // forReplication
     "# The read buffer size for cleaning.  If zero (the default), then\n" +
     "# je.log.iteratorReadSize value is used.");
      
    public static final BooleanConfigParam CLEANER_TRACK_DETAIL =
        new BooleanConfigParam("je.cleaner.trackDetail",
                               true,          // default
                               false,         // mutable
                               false,         // forReplication
     "# If true, the cleaner tracks and stores detailed information that\n" +
     "# is used to decrease the cost of cleaning.");

    public static final IntConfigParam CLEANER_DETAIL_MAX_MEMORY_PERCENTAGE =
        new IntConfigParam("je.cleaner.detailMaxMemoryPercentage",
                           new Integer(1),    // min
                           new Integer(90),   // max
                           new Integer(2),    // default
                           true,              // mutable
                           false,             // forReplication
     "# Tracking of detailed cleaning information will use no more than\n" +
     "# this percentage of the cache.  The default value is two percent.\n" +
     "# This setting is only used if je.cleaner.trackDetail=true.");
      
    public static final BooleanConfigParam CLEANER_RMW_FIX =
        new BooleanConfigParam("je.cleaner.rmwFix",
                               true,          // default
                               false,         // mutable
                               false,         // forReplication
     "# If true, detail information is discarded that was added by earlier\n" +
     "# versions of JE if it may be invalid.  This may be set to false\n" +
     "# for increased performance, but only if LockMode.RMW was never used.");
      
    public static final ConfigParam CLEANER_FORCE_CLEAN_FILES =
        new ConfigParam("je.cleaner.forceCleanFiles",
                        "",                  // default
                        false,               // mutable
                        false,               // forReplication
     "# Specifies a list of files or file ranges to force clean.  This is\n" +
     "# intended for use in forcing the cleaning of a large number of log\n" +
     "# files.  File numbers are in hex and are comma separated or hyphen\n" +
     "# separated to specify ranges, e.g.: '9,a,b-d' will clean 5 files.");

    public static final IntConfigParam CLEANER_THREADS =
        new IntConfigParam("je.cleaner.threads",
                           new Integer(1),   // min
                           null,             // max
                           new Integer(1),   // default
                           true,             // mutable
                           false,            // forReplication
     "# The number of threads allocated by the cleaner for log file\n" +
     "# processing.  If the cleaner backlog becomes large, increase this\n" +
     "# value.  The default is set to 1.");

    public static final IntConfigParam CLEANER_LOOK_AHEAD_CACHE_SIZE = 
        new IntConfigParam("je.cleaner.lookAheadCacheSize",
                           new Integer(0),    // min
                           null,              // max
                           new Integer(8192), // default
                           true,              // mutable
                           false,             // forReplication
     "# The look ahead cache size for cleaning in bytes.  Increasing this\n" +
     "# value can reduce the number of Btree lookups.");

    /*
     * Transactions
     */
    public static final IntConfigParam N_LOCK_TABLES =
        new IntConfigParam("je.lock.nLockTables",
                           new Integer(1),    // min
                           new Integer(32767),// max
                           new Integer(1),    // default
                           false,             // mutable
                           false,             // forReplication
     "# Number of Lock Tables.  Set this to a value other than 1 when\n" +
     "# an application has multiple threads performing concurrent JE\n" +
     "# operations.  It should be set to a prime number, and in general\n" +
     "# not higher than the number of application threads performing JE\n" +
     "# operations.");

    public static final LongConfigParam LOCK_TIMEOUT =
        new LongConfigParam("je.lock.timeout",
                            new Long(0),           // min
                            new Long(4294967296L), // max
                            new Long(500000L),     // default
                            false,                 // mutable
                            false,                 // forReplication
     "# The lock timeout in microseconds.");

    public static final LongConfigParam TXN_TIMEOUT =
        new LongConfigParam("je.txn.timeout",
                            new Long(0),           // min
                            new Long(4294967296L), // max_value
                            new Long(0),           // default
                            false,                 // mutable
                            false,                 // forReplication
     "# The transaction timeout, in microseconds. A value of 0 means no limit.");

    public static final BooleanConfigParam TXN_SERIALIZABLE_ISOLATION =
        new BooleanConfigParam("je.txn.serializableIsolation",
                               false,              // default
                               false,              // mutable
                               false,              // forReplication
   "# Transactions have the Serializable (Degree 3) isolation level.  The\n" +
   "# default is false, which implies the Repeatable Read isolation level.");

    public static final BooleanConfigParam TXN_DEADLOCK_STACK_TRACE =
        new BooleanConfigParam("je.txn.deadlockStackTrace",
                               false,              // default
                               true,               // mutable
                               false,              // forReplication
   "# Set this parameter to true to add stacktrace information to deadlock\n" +
   "# (lock timeout) exception messages.  The stack trace will show where\n" +
   "# each lock was taken.  The default is false, and true should only be\n" +
   "# used during debugging because of the added memory/processing cost.\n" +
   "# This parameter is 'static' across all environments.");

    public static final BooleanConfigParam TXN_DUMPLOCKS =
        new BooleanConfigParam("je.txn.dumpLocks",
                               false,              // default
                               true,               // mutable
                               false,              // forReplication
   "# Dump the lock table when a lock timeout is encountered, for\n" +
   "# debugging assistance.");

    /*
     * Debug tracing system
     */
    public static final BooleanConfigParam JE_LOGGING_FILE =
        new BooleanConfigParam("java.util.logging.FileHandler.on",
                               false,              // default
                               false,              // mutable
                               false,              // forReplication
     "# Use FileHandler in logging system.");

    public static final BooleanConfigParam JE_LOGGING_CONSOLE =
        new BooleanConfigParam("java.util.logging.ConsoleHandler.on",
                               false,             // default
                               false,             // mutable
                               false,              // forReplication
     "# Use ConsoleHandler in logging system.");

    public static final BooleanConfigParam JE_LOGGING_DBLOG =
        new BooleanConfigParam("java.util.logging.DbLogHandler.on",
                               true,               // default
                               false,              // mutable
                               false,              // forReplication
     "# Use DbLogHandler in logging system.");

    public static final IntConfigParam JE_LOGGING_FILE_LIMIT =
        new IntConfigParam("java.util.logging.FileHandler.limit",
                           new Integer(1000),       // min
                           new Integer(1000000000), // max
                           new Integer(10000000),   // default
                           false,                   // mutable
                           false,                   // forReplication
     "# Log file limit for FileHandler.");

    public static final IntConfigParam JE_LOGGING_FILE_COUNT =
        new IntConfigParam("java.util.logging.FileHandler.count",
                           new Integer(1),         // min
                           null,                   // max
                           new Integer(10),        // default
                           false,                  // mutable
                           false,                  // forReplication
    "# Log file count for FileHandler.");

    public static final ConfigParam JE_LOGGING_LEVEL =
        new ConfigParam("java.util.logging.level",
                        "INFO",
                        false,                     // mutable
                        false,                     // forReplication
     "# Trace messages equal and above this level will be logged.\n" +
     "# Value should be one of the predefined java.util.logging.Level values");

    public static final ConfigParam JE_LOGGING_LEVEL_LOCKMGR =
        new ConfigParam("java.util.logging.level.lockMgr",
                        "FINE", 
                        false,                    // mutable
                        false,                    // forReplication
     "# Lock manager specific trace messages will be issued at this level.\n"+
     "# Value should be one of the predefined java.util.logging.Level values");

    public static final ConfigParam JE_LOGGING_LEVEL_RECOVERY =
        new ConfigParam("java.util.logging.level.recovery",
                        "FINE", 
                         false,                   // mutable
                        false,                    // forReplication
     "# Recovery specific trace messages will be issued at this level.\n"+
     "# Value should be one of the predefined java.util.logging.Level values");

    public static final ConfigParam JE_LOGGING_LEVEL_EVICTOR =
        new ConfigParam("java.util.logging.level.evictor",
                        "FINE", 
                         false,                   // mutable
                        false,                    // forReplication
     "# Evictor specific trace messages will be issued at this level.\n"+
     "# Value should be one of the predefined java.util.logging.Level values");

    public static final ConfigParam JE_LOGGING_LEVEL_CLEANER =
        new ConfigParam("java.util.logging.level.cleaner",
                        "FINE",
                         true,                    // mutable
                        false,                    // forReplication
     "# Cleaner specific detailed trace messages will be issued at this\n" +
     "# level. The Value should be one of the predefined \n" +
     "# java.util.logging.Level values");

    /* 
     * Replication params are in com.sleepycat.je.rep.impl.ReplicatorParams
     */

    
    /*
     * Create a sample je.properties file.
     */
    public static void main(String argv[]) {
        if (argv.length != 2) {
            throw new IllegalArgumentException("Usage: EnvironmentParams " +
                          "<includeReplicationParams, true|false> " + 
                          "<samplePropertyFile>");
        }

        try {
            boolean includeRepParams = Boolean.valueOf(argv[0]).booleanValue();
            FileWriter exampleFile = new FileWriter(new File(argv[1]));
            TreeSet paramNames = new TreeSet(SUPPORTED_PARAMS.keySet());
            Iterator iter = paramNames.iterator();
            exampleFile.write
                ("####################################################\n" +
                 "# Example Berkeley DB, Java Edition property file\n" +
                 "# Each parameter is set to its default value\n" +
                 "####################################################\n\n");
            
            while (iter.hasNext()) {
                String paramName =(String) iter.next();
                ConfigParam param =
                    (ConfigParam) SUPPORTED_PARAMS.get(paramName);

                /* 
                 * If we're not showing replication params, skip
                 * the appropriate ones.
                 */
                if (!includeRepParams &&
                    param.isForReplication()) {
                    continue;
                }

                exampleFile.write(param.getDescription() + "\n");
                String extraDesc = param.getExtraDescription();
                if (extraDesc != null) {
                    exampleFile.write(extraDesc + "\n");
                }
                exampleFile.write("# " + param.getName() + "=" +
                                  param.getDefault() +
                                  "\n# (mutable at run time: " +
                                  param.isMutable() +
                                  ")\n\n");
            }
            exampleFile.close();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }
    
    /*
     * Add a configuration parameter to the set supported by an 
     * environment.
     */
    public static void addSupportedParam(ConfigParam param) {
        SUPPORTED_PARAMS.put(param.getName(), param);
    }
}
