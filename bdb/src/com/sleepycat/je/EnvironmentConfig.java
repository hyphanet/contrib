/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: EnvironmentConfig.java,v 1.52 2008/06/10 02:52:08 cwl Exp $
 */

package com.sleepycat.je;

import java.util.Properties;

import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.DbConfigManager;

/**
 * Specifies the attributes of an environment.
 *
 * <p>To change the default settings for a database environment, an application
 * creates a configuration object, customizes settings and uses it for
 * environment construction. The set methods of this class validate the
 * configuration values when the method is invoked.  An
 * IllegalArgumentException is thrown if the value is not valid for that
 * attribute.</p>
 *
 * <p>All commonly used environment attributes have convenience setter/getter
 * methods defined in this class.  For example, to change the default
 * transaction timeout setting for an environment, the application should do
 * the following:</p>
 *
 * <blockquote><pre>
 *     // customize an environment configuration
 *     EnvironmentConfig envConfig = new EnvironmentConfig();
 * envConfig.setTxnTimeout(10000);  // will throw if timeout value is
 * invalid
 *     // Open the environment.
 *     Environment myEnvironment = new Environment(home, envConfig);
 * </pre></blockquote>
 *
 * <p>Additional parameters are described by the parameter name String
 * constants in this class. These additional parameters will not be needed by
 * most applications. This category of properties can be specified for the
 * EnvironmentConfig object through a Properties object read by
 * EnvironmentConfig(Properties), or individually through
 * EnvironmentConfig.setConfigParam().</p>
 *
 * <p>For example, an application can change the default btree node size
 * with:</p>
 *
 * <blockquote><pre>
 *     envConfig.setConfigParam("je.nodeMaxEntries", "256");
 * </pre></blockquote>
 *
 * <p>Environment configuration follows this order of precedence:</p>
 * <ol>
 * <li>Configuration parameters specified in
 * &lt;environment home&gt;/je.properties take first precedence.
 * <li>Configuration parameters set in the EnvironmentConfig object used at
 * Environment construction are next.
 * <li>Any configuration parameters not set by the application are set to
 * system defaults, described along with the parameter name String constants
 * in this class.</li>
 * </ol>
 *
 * <p>An EnvironmentConfig can be used to specify both mutable and immutable
 * environment properties.  Immutable properties may be specified when the
 * first Environment handle (instance) is opened for a given physical
 * environment.  When more handles are opened for the same environment, the
 * following rules apply:</p>
 *
 * <ol> <li>Immutable properties must equal the original values specified when
 * constructing an Environment handle for an already open environment.  When a
 * mismatch occurs, an exception is thrown.
 *
 * <li>Mutable properties are ignored when constructing an Environment handle
 * for an already open environment.  </ol>
 *
 * <p>After an Environment has been constructed, its mutable properties may be
 * changed using {@link Environment#setMutableConfig}.  See {@link
 * EnvironmentMutableConfig} for a list of mutable properties; all other
 * properties are immutable.  Whether a property is mutable or immutable is
 * also described along with the parameter name String constants in this
 * class.</p>
 *
 * <h4>Getting the Current Environment Properties</h4>
 *
 * To get the current "live" properties of an environment after constructing it
 * or changing its properties, you must call {@link Environment#getConfig} or
 * {@link Environment#getMutableConfig}.  The original EnvironmentConfig or
 * EnvironmentMutableConfig object used to set the properties is not kept up to
 * date as properties are changed, and does not reflect property validation or
 * properties that are computed.
 */
public class EnvironmentConfig extends EnvironmentMutableConfig {

    /**
     * @hidden
     * For internal use, to allow null as a valid value for the config
     * parameter.
     */
    public static final EnvironmentConfig DEFAULT = new EnvironmentConfig();

    /**
     * The {@link #setCacheSize CacheSize} property.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td>
     * <td>Default</td><td>Minimum</td><td>Maximum</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Long</td>
     * <td>Yes</td>
     * <td>0</td>
     * <td>-none-</td>
     * <td>-none-</td>
     * </tr>
     * </table></p>
     *
     * @see #setCacheSize
     */
    public static final String MAX_MEMORY = "je.maxMemory";

    /**
     * The {@link #setCachePercent CachePercent} property.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td>
     * <td>Default</td><td>Minimum</td><td>Maximum</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Integer</td>
     * <td>Yes</td>
     * <td>60</td>
     * <td>1</td>
     * <td>90</td>
     * </tr>
     * </table></p>
     *
     * @see #setCachePercent
     */
    public static final String MAX_MEMORY_PERCENT = "je.maxMemoryPercent";

    /**
     * The {@link #setSharedCache SharedCache} property.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td><td>Default</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Boolean</td>
     * <td>No</td>
     * <td>false</td>
     * </tr>
     * </table></p>
     */
    public static final String SHARED_CACHE = "je.sharedCache";

    /**
     * If true, a checkpoint is forced following recovery, even if the
     * log ends with a checkpoint.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td><td>Default</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Boolean</td>
     * <td>No</td>
     * <td>false</td>
     * </tr>
     * </table></p>
     */
    public static final String ENV_RECOVERY_FORCE_CHECKPOINT =
        "je.env.recoveryForceCheckpoint";

    /**
     * If true, starts up the INCompressor thread.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td><td>Default</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Boolean</td>
     * <td>Yes</td>
     * <td>true</td>
     * </tr>
     * </table></p>
     */
    public static final String ENV_RUN_IN_COMPRESSOR =
        "je.env.runINCompressor";

    /**
     * If true, starts up the checkpointer thread.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td><td>Default</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Boolean</td>
     * <td>Yes</td>
     * <td>true</td>
     * </tr>
     * </table></p>
     */
    public static final String ENV_RUN_CHECKPOINTER = "je.env.runCheckpointer";

    /**
     * If true, starts up the cleaner thread.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td><td>Default</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Boolean</td>
     * <td>Yes</td>
     * <td>true</td>
     * </tr>
     * </table></p>
     */
    public static final String ENV_RUN_CLEANER = "je.env.runCleaner";

    /**
     * The maximum number of read operations performed by JE background
     * activities (e.g., cleaning) before sleeping to ensure that application
     * threads can perform I/O.  If zero (the default) then no limitation on
     * I/O is enforced.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td>
     * <td>Default</td><td>Minimum</td><td>Maximum</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Integer</td>
     * <td>Yes</td>
     * <td>0</td>
     * <td>0</td>
     * <td>-none-</td>
     * </tr>
     * </table></p>
     *
     * @see #ENV_BACKGROUND_SLEEP_INTERVAL
     */
    public static final String ENV_BACKGROUND_READ_LIMIT =
        "je.env.backgroundReadLimit";

    /**
     * The maximum number of write operations performed by JE background
     * activities (e.g., checkpointing and eviction) before sleeping to ensure
     * that application threads can perform I/O.  If zero (the default) then no
     * limitation on I/O is enforced.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td>
     * <td>Default</td><td>Minimum</td><td>Maximum</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Integer</td>
     * <td>Yes</td>
     * <td>0</td>
     * <td>0</td>
     * <td>-none-</td>
     * </tr>
     * </table></p>
     *
     * @see #ENV_BACKGROUND_SLEEP_INTERVAL
     */
    public static final String ENV_BACKGROUND_WRITE_LIMIT =
        "je.env.backgroundWriteLimit";

    /**
     * The maximum time in milliseconds to wait for an API call to start
     * executing when the API is locked.  The default timeout is indefinite.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td>
     * <td>Default</td><td>Minimum</td><td>Maximum</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Integer</td>
     * <td>Yes</td>
     * <td>{@value java.lang.Integer#MAX_VALUE}</td>
     * <td>0</td>
     * <td>-none</td>
     * </tr>
     * </table></p>
     */
    public static final String ENV_LOCKOUT_TIMEOUT = "je.env.lockoutTimeout";

    /**
     * The number of microseconds that JE background activities will sleep when
     * the {@link #ENV_BACKGROUND_WRITE_LIMIT} or {@link
     * #ENV_BACKGROUND_WRITE_LIMIT} is reached.  If {@link
     * #ENV_BACKGROUND_WRITE_LIMIT} and {@link #ENV_BACKGROUND_WRITE_LIMIT} are
     * zero, this setting is not used.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td>
     * <td>Default</td><td>Minimum</td><td>Maximum</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Long</td>
     * <td>Yes</td>
     * <td>1000</td>
     * <td>1000</td>
     * <td>{@value java.lang.Long#MAX_VALUE}</td>
     * </tr>
     * </table></p>
     */
    public static final String ENV_BACKGROUND_SLEEP_INTERVAL =
        "je.env.backgroundSleepInterval";

    /**
     * Debugging support: check leaked locks and txns at env close.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td><td>Default</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Boolean</td>
     * <td>No</td>
     * <td>true</td>
     * </tr>
     * </table></p>
     */
    public static final String ENV_CHECK_LEAKS = "je.env.checkLeaks";

    /**
     * Debugging support: call Thread.yield() at strategic points.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td><td>Default</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Boolean</td>
     * <td>No</td>
     * <td>false</td>
     * </tr>
     * </table></p>
     */
    public static final String ENV_FORCED_YIELD = "je.env.forcedYield";

    /**
     * If true, create an environment that is capable of performing
     * transactions.  If true is not passed, transactions may not be used.  For
     * licensing purposes, the use of this method distinguishes the use of the
     * Transactional product.  Note that if transactions are not used,
     * specifying true does not create additional overhead in the environment.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td><td>Default</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Boolean</td>
     * <td>No</td>
     * <td>false</td>
     * </tr>
     * </table></p>
     */
    public static final String ENV_IS_TRANSACTIONAL = "je.env.isTransactional";

    /**
     * If true, create the environment with record locking.  This property
     * should be set to false only in special circumstances when it is safe to
     * run without record locking.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td><td>Default</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Boolean</td>
     * <td>No</td>
     * <td>true</td>
     * </tr>
     * </table></p>
     */
    public static final String ENV_IS_LOCKING = "je.env.isLocking";

    /**
     * If true, open the environment read-only.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td><td>Default</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Boolean</td>
     * <td>No</td>
     * <td>false</td>
     * </tr>
     * </table></p>
     */
    public static final String ENV_READ_ONLY = "je.env.isReadOnly";

    /**
     * If true, use latches instead of synchronized blocks to implement the
     * lock table and log write mutexes. Latches require that threads queue to
     * obtain the mutex in question and therefore guarantee that there will be
     * no mutex starvation, but do incur a performance penalty. Latches should
     * not be necessary in most cases, so synchronized blocks are the default.
     * An application that puts heavy load on JE with threads with different
     * thread priorities might find it useful to use latches.  In a Java 5 JVM,
     * where java.util.concurrent.locks.ReentrantLock is used for the latch
     * implementation, this parameter will determine whether they are 'fair' or
     * not.  This parameter is 'static' across all environments.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td><td>Default</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Boolean</td>
     * <td>No</td>
     * <td>false</td>
     * </tr>
     * </table></p>
     */
    public static final String ENV_FAIR_LATCHES = "je.env.fairLatches";

    /**
     * If true, enable eviction of metadata for closed databases.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td><td>Default</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Boolean</td>
     * <td>No</td>
     * <td>true</td>
     * </tr>
     * </table></p>
     */
    public static final String ENV_DB_EVICTION = "je.env.dbEviction";

    /**
     * By default, JE passes an entire log record to the Adler32 class for
     * checksumming.  This can cause problems with the GC in some cases if the
     * records are large and there is concurrency.  Setting this parameter will
     * cause JE to pass chunks of the log record to the checksumming class so
     * that the GC does not block.  0 means do not chunk.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td>
     * <td>Default</td><td>Minimum</td><td>Maximum</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Integer</td>
     * <td>Yes</td>
     * <td>0</td>
     * <td>0</td>
     * <td>1048576 (1M)</td>
     * </tr>
     * </table></p>
     */
    public static final String ADLER32_CHUNK_SIZE = "je.adler32.chunkSize";

    /**
     * The total memory taken by log buffers, in bytes. If 0, use 7% of
     * je.maxMemory. If 0 and je.sharedCache=true, use 7% divided by N where N
     * is the number of environments sharing the global cache.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td>
     * <td>Default</td><td>Minimum</td><td>Maximum</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Long</td>
     * <td>No</td>
     * <td>0</td>
     * <td>{@value
     * com.sleepycat.je.config.EnvironmentParams#LOG_MEM_SIZE_MIN}</td>
     * <td>-none-</td>
     * </tr>
     * </table></p>
     */
    public static final String LOG_TOTAL_BUFFER_BYTES =
        "je.log.totalBufferBytes";

    /**
     * The number of JE log buffers.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td>
     * <td>Default</td><td>Minimum</td><td>Maximum</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Integer</td>
     * <td>No</td>
     * <td>{@value
     * com.sleepycat.je.config.EnvironmentParams#NUM_LOG_BUFFERS_DEFAULT}</td>
     * <td>2</td>
     * <td>-none-</td>
     * </tr>
     * </table></p>
     */
    public static final String LOG_NUM_BUFFERS = "je.log.numBuffers";

    /**
     * The maximum starting size of a JE log buffer.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td>
     * <td>Default</td><td>Minimum</td><td>Maximum</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Integer</td>
     * <td>No</td>
     * <td>1048576 (1M)</td>
     * <td>1024 (1K)</td>
     * <td>-none-</td>
     * </tr>
     * </table></p>
     */
    public static final String LOG_BUFFER_SIZE = "je.log.bufferSize";

    /**
     * The buffer size for faulting in objects from disk, in bytes.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td>
     * <td>Default</td><td>Minimum</td><td>Maximum</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Integer</td>
     * <td>No</td>
     * <td>2048 (2K)</td>
     * <td>32</td>
     * <td>-none-</td>
     * </tr>
     * </table></p>
     */
    public static final String LOG_FAULT_READ_SIZE = "je.log.faultReadSize";

    /**
     * The read buffer size for log iterators, which are used when scanning the
     * log during activities like log cleaning and environment open, in bytes.
     * This may grow as the system encounters larger log entries.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td>
     * <td>Default</td><td>Minimum</td><td>Maximum</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Integer</td>
     * <td>No</td>
     * <td>8192 (8K)</td>
     * <td>128</td>
     * <td>-none-</td>
     * </tr>
     * </table></p>
     */
    public static final String LOG_ITERATOR_READ_SIZE =
        "je.log.iteratorReadSize";

    /**
     * The maximum read buffer size for log iterators, which are used when
     * scanning the log during activities like log cleaning and environment
     * open, in bytes.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td>
     * <td>Default</td><td>Minimum</td><td>Maximum</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Integer</td>
     * <td>No</td>
     * <td>16777216 (16M)</td>
     * <td>128</td>
     * <td>-none-</td>
     * </tr>
     * </table></p>
     */
    public static final String LOG_ITERATOR_MAX_SIZE =
        "je.log.iteratorMaxSize";

    /**
     * The maximum size of each individual JE log file, in bytes.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td>
     * <td>Default</td><td>Minimum</td><td>Maximum</td><td>JVM</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Long</td>
     * <td>No</td>
     * <td>10000000 (10M)</td>
     * <td>1000000 (1M)</td>
     * <td>4294967296 (4G)</td>
     * <td>Conventional JVM</td>
     * </tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Long</td>
     * <td>No</td>
     * <td>100000 (100K)</td>
     * <td>10000 (10K)</td>
     * <td>4294967296 (4G)</td>
     * <td>Dalvik JVM</td>
     * </tr>
     * </table></p>
     */
    public static final String LOG_FILE_MAX = "je.log.fileMax";

    /**
     * If true, perform a checksum check when reading entries from log.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td><td>Default</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Boolean</td>
     * <td>No</td>
     * <td>true</td>
     * </tr>
     * </table></p>
     */
    public static final String LOG_CHECKSUM_READ = "je.log.checksumRead";

    /**
     * If true, perform a checksum verification just before and after writing
     * to the log.  This is primarily used for debugging.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td><td>Default</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Boolean</td>
     * <td>No</td>
     * <td>false</td>
     * </tr>
     * </table></p>
     */
    public static final String LOG_VERIFY_CHECKSUMS = "je.log.verifyChecksums";

    /**
     * If true, operates in an in-memory test mode without flushing the log to
     * disk. An environment directory must be specified, but it need not exist
     * and no files are written.  The system operates until it runs out of
     * memory, at which time an OutOfMemoryError is thrown.  Because the entire
     * log is kept in memory, this mode is normally useful only for testing.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td><td>Default</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Boolean</td>
     * <td>No</td>
     * <td>false</td>
     * </tr>
     * </table></p>
     */
    public static final String LOG_MEM_ONLY = "je.log.memOnly";

    /**
     * The size of the file handle cache.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td>
     * <td>Default</td><td>Minimum</td><td>Maximum</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Integer</td>
     * <td>No</td>
     * <td>100</td>
     * <td>3</td>
     * <td>-none-</td>
     * </tr>
     * </table></p>
     */
    public static final String LOG_FILE_CACHE_SIZE = "je.log.fileCacheSize";

    /**
     * The timeout limit for group file sync, in microseconds.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td>
     * <td>Default</td><td>Minimum</td><td>Maximum</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Long</td>
     * <td>No</td>
     * <td>500000 (0.5 sec)</td>
     * <td>10000< (0.01 sec)/td>
     * <td>-none-</td>
     * </tr>
     * </table></p>
     */
    public static final String LOG_FSYNC_TIMEOUT = "je.log.fsyncTimeout";

    /**
     * If true (default is false) O_DSYNC is used to open JE log files.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td><td>Default</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Boolean</td>
     * <td>No</td>
     * <td>false</td>
     * </tr>
     * </table></p>
     */
    public static final String LOG_USE_ODSYNC = "je.log.useODSYNC";

    /**
     * If true (default is false) NIO is used for all file I/O.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td><td>Default</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Boolean</td>
     * <td>No</td>
     * <td>false</td>
     * </tr>
     * </table></p>
     */
    public static final String LOG_USE_NIO = "je.log.useNIO";

    /**
     * If true (default is false) direct NIO buffers are used.  This setting is
     * only used if {@link #LOG_USE_NIO} is true.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td><td>Default</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Boolean</td>
     * <td>No</td>
     * <td>false</td>
     * </tr>
     * </table></p>
     */
    public static final String LOG_DIRECT_NIO = "je.log.directNIO";

    /**
     * If non-0 (default is 0) break all IO into chunks of this size.  This
     * setting is only used if {@link #LOG_USE_NIO} is true.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td>
     * <td>Default</td><td>Minimum</td><td>Maximum</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Long</td>
     * <td>No</td>
     * <td>0</td>
     * <td>0</td>
     * <td>67108864 (64M)</td>
     * </tr>
     * </table></p>
     */
    public static final String LOG_CHUNKED_NIO = "je.log.chunkedNIO";

    /**
     * The maximum number of entries in an internal btree node.  This can be
     * set per-database using the DatabaseConfig object.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td>
     * <td>Default</td><td>Minimum</td><td>Maximum</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Integer</td>
     * <td>No</td>
     * <td>128</td>
     * <td>4</td>
     * <td>32767 (32K)</td>
     * </tr>
     * </table></p>
     */
    public static final String NODE_MAX_ENTRIES = "je.nodeMaxEntries";

    /**
     * The maximum number of entries in an internal dup btree node.  This can
     * be set per-database using the DatabaseConfig object.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td>
     * <td>Default</td><td>Minimum</td><td>Maximum</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Integer</td>
     * <td>No</td>
     * <td>128</td>
     * <td>4</td>
     * <td>32767 (32K)</td>
     * </tr>
     * </table></p>
     */
    public static final String NODE_DUP_TREE_MAX_ENTRIES =
        "je.nodeDupTreeMaxEntries";

    /**
     * After this many deltas, log a full version.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td>
     * <td>Default</td><td>Minimum</td><td>Maximum</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Integer</td>
     * <td>No</td>
     * <td>10</td>
     * <td>0</td>
     * <td>100</td>
     * </tr>
     * </table></p>
     */
    public static final String TREE_MAX_DELTA = "je.tree.maxDelta";

    /**
     * If less than this percentage of entries are changed on a BIN, log a
     * delta instead of a full version.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td>
     * <td>Default</td><td>Minimum</td><td>Maximum</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Integer</td>
     * <td>No</td>
     * <td>25</td>
     * <td>0</td>
     * <td>75</td>
     * </tr>
     * </table></p>
     */
    public static final String TREE_BIN_DELTA = "je.tree.binDelta";

    /**
     * The minimum bytes allocated out of the memory cache to hold Btree data
     * including internal nodes and record keys and data.  If the specified
     * value is larger than the size initially available in the cache, it will
     * be truncated to the amount available.
     *
     * <p>{@link #TREE_MIN_MEMORY} is the minimum for a single environment.  By
     * default, 500 KB or the size initially available in the cache is used,
     * whichever is smaller.</p>
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td>
     * <td>Default</td><td>Minimum</td><td>Maximum</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Long</td>
     * <td>Yes</td>
     * <td>512000 (500K)</td>
     * <td>51200 (50K)</td>
     * <td>-none-</td>
     * </tr>
     * </table></p>
     */
    public static final String TREE_MIN_MEMORY = "je.tree.minMemory";

    /**
     * The compressor thread wakeup interval in microseconds.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td>
     * <td>Default</td><td>Minimum</td><td>Maximum</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Long</td>
     * <td>No</td>
     * <td>5000000 (5 sec)</td>
     * <td>1000000 (1 sec)</td>
     * <td>4294967296 (71.6 min)</td>
     * </tr>
     * </table></p>
     */
    public static final String COMPRESSOR_WAKEUP_INTERVAL =
        "je.compressor.wakeupInterval";

    /**
     * The number of times to retry a compression run if a deadlock occurs.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td>
     * <td>Default</td><td>Minimum</td><td>Maximum</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Integer</td>
     * <td>No</td>
     * <td>3</td>
     * <td>0</td>
     * <td>-none-</td>
     * </tr>
     * </table></p>
     */
    public static final String COMPRESSOR_DEADLOCK_RETRY =
        "je.compressor.deadlockRetry";

    /**
     * The lock timeout for compressor transactions in microseconds.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td>
     * <td>Default</td><td>Minimum</td><td>Maximum</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Long</td>
     * <td>No</td>
     * <td>500000 (0.5 sec)</td>
     * <td>0</td>
     * <td>4294967296 (71.6 min)</td>
     * </tr>
     * </table></p>
     */
    public static final String COMPRESSOR_LOCK_TIMEOUT =
        "je.compressor.lockTimeout";

    /**
     * If true, when the compressor encounters an empty tree, the root node of
     * the tree is deleted.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td><td>Default</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Boolean</td>
     * <td>No</td>
     * <td>false</td>
     * </tr>
     * </table></p>
     */
    public static final String COMPRESSOR_PURGE_ROOT =
        "je.compressor.purgeRoot";

    /**
     * When eviction occurs, the evictor will push memory usage to this number
     * of bytes below {@link #MAX_MEMORY}.  No more than 50% of je.maxMemory
     * will be evicted per eviction cycle, regardless of this setting.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td>
     * <td>Default</td><td>Minimum</td><td>Maximum</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Long</td>
     * <td>No</td>
     * <td>524288 (512K)</td>
     * <td>1024 (1K)</td>
     * <td>-none-</td>
     * </tr>
     * </table></p>
     */
    public static final String EVICTOR_EVICT_BYTES = "je.evictor.evictBytes";

    /**
     * The number of nodes in one evictor scan.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td>
     * <td>Default</td><td>Minimum</td><td>Maximum</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Integer</td>
     * <td>No</td>
     * <td>10</td>
     * <td>1</td>
     * <td>1000</td>
     * </tr>
     * </table></p>
     */
    public static final String EVICTOR_NODES_PER_SCAN =
        "je.evictor.nodesPerScan";

    /**
     * The number of times to retry the evictor if it runs into a deadlock.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td>
     * <td>Default</td><td>Minimum</td><td>Maximum</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Integer</td>
     * <td>No</td>
     * <td>3</td>
     * <td>0</td>
     * <td>-none-</td>
     * </tr>
     * </table></p>
     */
    public static final String EVICTOR_DEADLOCK_RETRY =
        "je.evictor.deadlockRetry";

    /**
     * If true (the default), use an LRU-only policy to select nodes for
     * eviction.  If false, select by Btree level first, and then by LRU.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td><td>Default</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Boolean</td>
     * <td>No</td>
     * <td>true</td>
     * </tr>
     * </table></p>
     */
    public static final String EVICTOR_LRU_ONLY = "je.evictor.lruOnly";

    /**
     * Call Thread.yield() at each check for cache overflow. This improves GC
     * performance on some systems.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td><td>Default</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Boolean</td>
     * <td>No</td>
     * <td>false</td>
     * </tr>
     * </table></p>
     */
    public static final String EVICTOR_FORCED_YIELD = "je.evictor.forcedYield";

    /**
     * Ask the checkpointer to run every time we write this many bytes to the
     * log. If set, supercedes {@link #CHECKPOINTER_WAKEUP_INTERVAL}. To use
     * time based checkpointing, set this to 0.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td>
     * <td>Default</td><td>Minimum</td><td>Maximum</td><td>JVM</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Long</td>
     * <td>No</td>
     * <td>20000000 (20M)</td>
     * <td>0</td>
     * <td>-none-</td>
     * <td>Conventional JVM</td>
     * </tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Long</td>
     * <td>No</td>
     * <td>200000 (200K)</td>
     * <td>0</td>
     * <td>-none-</td>
     * <td>Dalvik JVM</td>
     * </tr>
     * </table></p>
     */
    public static final String CHECKPOINTER_BYTES_INTERVAL =
        "je.checkpointer.bytesInterval";

    /**
     * The checkpointer wakeup interval in microseconds. By default, this
     * is inactive and we wakeup the checkpointer as a function of the
     * number of bytes written to the log ({@link
     * #CHECKPOINTER_BYTES_INTERVAL}).
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td>
     * <td>Default</td><td>Minimum</td><td>Maximum</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Long</td>
     * <td>No</td>
     * <td>0</td>
     * <td>1000000 (1 sec)</td>
     * <td>4294967296 (71.6 min)</td>
     * </tr>
     * </table></p>
     */
    public static final String CHECKPOINTER_WAKEUP_INTERVAL =
        "je.checkpointer.wakeupInterval";

    /**
     * The number of times to retry a checkpoint if it runs into a deadlock.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td>
     * <td>Default</td><td>Minimum</td><td>Maximum</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Integer</td>
     * <td>No</td>
     * <td>3</td>
     * <td>0</td>
     * <td>-none-</td>
     * </tr>
     * </table></p>
     */
    public static final String CHECKPOINTER_DEADLOCK_RETRY =
        "je.checkpointer.deadlockRetry";

    /**
     * If true, the checkpointer uses more resources in order to complete the
     * checkpoint in a shorter time interval.  Btree latches are held and other
     * threads are blocked for a longer period.  Log cleaner record migration
     * is performed by cleaner threads instead of during checkpoints.  When set
     * to true, application response time may be longer during a checkpoint,
     * and more cleaner threads may be required to maintain the configured log
     * utilization.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td><td>Default</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Boolean</td>
     * <td>Yes</td>
     * <td>false</td>
     * </tr>
     * </table></p>
     */
    public static final String CHECKPOINTER_HIGH_PRIORITY =
        "je.checkpointer.highPriority";

    /**
     * The cleaner will keep the total disk space utilization percentage above
     * this value.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td>
     * <td>Default</td><td>Minimum</td><td>Maximum</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Integer</td>
     * <td>Yes</td>
     * <td>50</td>
     * <td>0</td>
     * <td>90</td>
     * </tr>
     * </table></p>
     */
    public static final String CLEANER_MIN_UTILIZATION =
        "je.cleaner.minUtilization";

    /**
     * A log file will be cleaned if its utilization percentage is below this
     * value, irrespective of total utilization.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td>
     * <td>Default</td><td>Minimum</td><td>Maximum</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Integer</td>
     * <td>Yes</td>
     * <td>5</td>
     * <td>0</td>
     * <td>50</td>
     * </tr>
     * </table></p>
     */
    public static final String CLEANER_MIN_FILE_UTILIZATION =
        "je.cleaner.minFileUtilization";

    /**
     * The cleaner checks disk utilization every time we write this many bytes
     * to the log.  If zero (and by default) it is set to the {@link
     * #LOG_FILE_MAX} value divided by four.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td>
     * <td>Default</td><td>Minimum</td><td>Maximum</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Long</td>
     * <td>Yes</td>
     * <td>0</td>
     * <td>0</td>
     * <td>-none-</td>
     * </tr>
     * </table></p>
     */
    public static final String CLEANER_BYTES_INTERVAL =
        "je.cleaner.bytesInterval";

    /**
     * If true, the cleaner will fetch records to determine their size to more
     * accurately calculate log utilization.  This setting is used during DB
     * truncation/removal and during recovery, and will cause more I/O during
     * those operations when set to true.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td><td>Default</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Boolean</td>
     * <td>Yes</td>
     * <td>false</td>
     * </tr>
     * </table></p>
     */
    public static final String CLEANER_FETCH_OBSOLETE_SIZE =
        "je.cleaner.fetchObsoleteSize";

    /**
     * The number of times to retry cleaning if a deadlock occurs.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td>
     * <td>Default</td><td>Minimum</td><td>Maximum</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Integer</td>
     * <td>Yes</td>
     * <td>3</td>
     * <td>0</td>
     * <td>-none-</td>
     * </tr>
     * </table></p>
     */
    public static final String CLEANER_DEADLOCK_RETRY =
        "je.cleaner.deadlockRetry";

    /**
     * The lock timeout for cleaner transactions in microseconds.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td>
     * <td>Default</td><td>Minimum</td><td>Maximum</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Long</td>
     * <td>Yes</td>
     * <td>500000 (0.5 sec)</td>
     * <td>0</td>
     * <td>4294967296 (71.6 min)</td>
     * </tr>
     * </table></p>
     */
    public static final String CLEANER_LOCK_TIMEOUT = "je.cleaner.lockTimeout";

    /**
     * If true, the cleaner deletes log files after successful cleaning.  If
     * false, the cleaner changes log file extensions to .DEL instead of
     * deleting them.  The latter is useful for diagnosing log cleaning
     * problems.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td><td>Default</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Boolean</td>
     * <td>Yes</td>
     * <td>true</td>
     * </tr>
     * </table></p>
     */
    public static final String CLEANER_EXPUNGE = "je.cleaner.expunge";

    /**
     * The minimum age of a file (number of files between it and the active
     * file) to qualify it for cleaning under any conditions.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td>
     * <td>Default</td><td>Minimum</td><td>Maximum</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Integer</td>
     * <td>Yes</td>
     * <td>2</td>
     * <td>1</td>
     * <td>1000</td>
     * </tr>
     * </table></p>
     */
    public static final String CLEANER_MIN_AGE = "je.cleaner.minAge";

    /**
     * The maximum number of log files in the cleaner's backlog, or zero if
     * there is no limit.  Changing this property can impact the performance of
     * some out-of-memory applications.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td>
     * <td>Default</td><td>Minimum</td><td>Maximum</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Integer</td>
     * <td>Yes</td>
     * <td>0</td>
     * <td>0</td>
     * <td>100000</td>
     * </tr>
     * </table></p>
     */
    public static final String CLEANER_MAX_BATCH_FILES =
        "je.cleaner.maxBatchFiles";

    /**
     * The read buffer size for cleaning.  If zero (the default), then {@link
     * #LOG_ITERATOR_READ_SIZE} value is used.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td>
     * <td>Default</td><td>Minimum</td><td>Maximum</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Integer</td>
     * <td>Yes</td>
     * <td>0</td>
     * <td>128</td>
     * <td>-none-</td>
     * </tr>
     * </table></p>
     */
    public static final String CLEANER_READ_SIZE = "je.cleaner.readSize";

    /**
     * Tracking of detailed cleaning information will use no more than this
     * percentage of the cache.  The default value is 2% of {@link
     * #MAX_MEMORY}. If 0 and {@link #SHARED_CACHE} is true, use 2% divided by
     * N where N is the number of environments sharing the global cache.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td>
     * <td>Default</td><td>Minimum</td><td>Maximum</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Integer</td>
     * <td>Yes</td>
     * <td>2</td>
     * <td>1</td>
     * <td>90</td>
     * </tr>
     * </table></p>
     */
    public static final String CLEANER_DETAIL_MAX_MEMORY_PERCENTAGE =
        "je.cleaner.detailMaxMemoryPercentage";

    /**
     * Specifies a list of files or file ranges to be cleaned at a time when no
     * other log cleaning is necessary.  This parameter is intended for use in
     * forcing the cleaning of a large number of log files.  File numbers are
     * in hex and are comma separated or hyphen separated to specify ranges,
     * e.g.: '9,a,b-d' will clean 5 files.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td><td>Default</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>String</td>
     * <td>No</td>
     * <td>""</td>
     * </tr>
     * </table></p>
     */
    public static final String CLEANER_FORCE_CLEAN_FILES =
        "je.cleaner.forceCleanFiles";

    /**
     * All log files having a log version prior to the specified version will
     * be cleaned at a time when no other log cleaning is necessary.  Intended
     * for use in upgrading old format log files forward to the current log
     * format version, e.g., to take advantage of format improvements; note
     * that log upgrading is optional.  The default value zero (0) specifies
     * that no upgrading will occur.  The value negative one (-1) specifies
     * upgrading to the current log version.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td>
     * <td>Default</td><td>Minimum</td><td>Maximum</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Integer</td>
     * <td>No</td>
     * <td>0</td>
     * <td>-1</td>
     * <td>-none-</td>
     * </tr>
     * </table></p>
     */
    public static final String CLEANER_UPGRADE_TO_LOG_VERSION =
        "je.cleaner.upgradeToLogVersion";

    /**
     * The number of threads allocated by the cleaner for log file processing.
     * If the cleaner backlog becomes large, try increasing this value.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td>
     * <td>Default</td><td>Minimum</td><td>Maximum</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Integer</td>
     * <td>Yes</td>
     * <td>1</td>
     * <td>1</td>
     * <td>-none-</td>
     * </tr>
     * </table></p>
     */
    public static final String CLEANER_THREADS = "je.cleaner.threads";

    /**
     * The look ahead cache size for cleaning in bytes.  Increasing this value
     * can reduce the number of Btree lookups.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td>
     * <td>Default</td><td>Minimum</td><td>Maximum</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Integer</td>
     * <td>Yes</td>
     * <td>8192 (8K)</td>
     * <td>0</td>
     * <td>-none-</td>
     * </tr>
     * </table></p>
     */
    public static final String CLEANER_LOOK_AHEAD_CACHE_SIZE =
        "je.cleaner.lookAheadCacheSize";

    /**
     * Number of Lock Tables.  Set this to a value other than 1 when an
     * application has multiple threads performing concurrent JE operations.
     * It should be set to a prime number, and in general not higher than the
     * number of application threads performing JE operations.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td>
     * <td>Default</td><td>Minimum</td><td>Maximum</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Integer</td>
     * <td>No</td>
     * <td>1</td>
     * <td>1</td>
     * <td>32767 (32K)</td>
     * </tr>
     * </table></p>
     */
    public static final String LOCK_N_LOCK_TABLES = "je.lock.nLockTables";

    /**
     * The {@link #setLockTimeout LockTimeout} property.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td>
     * <td>Default</td><td>Minimum</td><td>Maximum</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Long</td>
     * <td>No</td>
     * <td>500000 (0.5 sec)</td>
     * <td>0</td>
     * <td>4294967296 (71.6 min)</td>
     * </tr>
     * </table></p>
     *
     * @see #setLockTimeout
     */
    public static final String LOCK_TIMEOUT = "je.lock.timeout";

    /**
     * The {@link #setTxnTimeout TxnTimeout} property.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td>
     * <td>Default</td><td>Minimum</td><td>Maximum</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Long</td>
     * <td>No</td>
     * <td>0</td>
     * <td>0</td>
     * <td>4294967296 (71.6 min)</td>
     * </tr>
     * </table></p>
     *
     * @see #setTxnTimeout
     */
    public static final String TXN_TIMEOUT = "je.txn.timeout";

    /**
     * The {@link #setTxnSerializableIsolation TxnSerializableIsolation}
     * property.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td><td>Default</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Boolean</td>
     * <td>No</td>
     * <td>false</td>
     * </tr>
     * </table></p>
     *
     * @see #setTxnSerializableIsolation
     */
    public static final String TXN_SERIALIZABLE_ISOLATION =
        "je.txn.serializableIsolation";

    /**
     * Set this parameter to true to add stacktrace information to deadlock
     * (lock timeout) exception messages.  The stack trace will show where each
     * lock was taken.  The default is false, and true should only be used
     * during debugging because of the added memory/processing cost.  This
     * parameter is 'static' across all environments.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td><td>Default</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Boolean</td>
     * <td>Yes</td>
     * <td>false</td>
     * </tr>
     * </table></p>
     */
    public static final String TXN_DEADLOCK_STACK_TRACE =
        "je.txn.deadlockStackTrace";

    /**
     * Dump the lock table when a lock timeout is encountered, for debugging
     * assistance.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td><td>Default</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Boolean</td>
     * <td>Yes</td>
     * <td>false</td>
     * </tr>
     * </table></p>
     */
    public static final String TXN_DUMP_LOCKS = "je.txn.dumpLocks";

    /**
     * Use FileHandler in logging system.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td><td>Default</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Boolean</td>
     * <td>No</td>
     * <td>false</td>
     * </tr>
     * </table></p>
     */
    public static final String TRACE_FILE = "java.util.logging.FileHandler.on";

    /**
     * Use ConsoleHandler in logging system.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td><td>Default</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Boolean</td>
     * <td>No</td>
     * <td>false</td>
     * </tr>
     * </table></p>
     */
    public static final String TRACE_CONSOLE =
        "java.util.logging.ConsoleHandler.on";

    /**
     * Use DbLogHandler in logging system.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td><td>Default</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Boolean</td>
     * <td>No</td>
     * <td>true</td>
     * </tr>
     * </table></p>
     */
    public static final String TRACE_DB = "java.util.logging.DbLogHandler.on";

    /**
     * Log file limit for FileHandler.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td>
     * <td>Default</td><td>Minimum</td><td>Maximum</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Integer</td>
     * <td>No</td>
     * <td>10000000 (10M)</td>
     * <td>1000</td>
     * <td>1000000000 (1G)</td>
     * </tr>
     * </table></p>
     */
    public static final String TRACE_FILE_LIMIT =
        "java.util.logging.FileHandler.limit";

    /**
     * Log file count for FileHandler.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td>
     * <td>Default</td><td>Minimum</td><td>Maximum</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>Integer</td>
     * <td>No</td>
     * <td>10</td>
     * <td>1</td>
     * <td>-none-</td>
     * </tr>
     * </table></p>
     */
    public static final String TRACE_FILE_COUNT =
        "java.util.logging.FileHandler.count";

    /**
     * Trace messages equal and above this level will be logged.  Value should
     * be one of the predefined java.util.logging.Level values.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td><td>Default</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>String</td>
     * <td>No</td>
     * <td>"INFO"</td>
     * </tr>
     * </table></p>
     */
    public static final String TRACE_LEVEL = "java.util.logging.level";

    /**
     * Lock manager specific trace messages will be issued at this level.
     * Value should be one of the predefined java.util.logging.Level values.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td><td>Default</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>String</td>
     * <td>No</td>
     * <td>"FINE"</td>
     * </tr>
     * </table></p>
     */
    public static final String TRACE_LEVEL_LOCK_MANAGER =
        "java.util.logging.level.lockMgr";

    /**
     * Recovery specific trace messages will be issued at this level.  Value
     * should be one of the predefined java.util.logging.Level values.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td><td>Default</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>String</td>
     * <td>No</td>
     * <td>"FINE"</td>
     * </tr>
     * </table></p>
     */
    public static final String TRACE_LEVEL_RECOVERY =
        "java.util.logging.level.recovery";

    /**
     * Evictor specific trace messages will be issued at this level.  Value
     * should be one of the predefined java.util.logging.Level values.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td><td>Default</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>String</td>
     * <td>No</td>
     * <td>"FINE"</td>
     * </tr>
     * </table></p>
     */
    public static final String TRACE_LEVEL_EVICTOR =
        "java.util.logging.level.evictor";

    /**
     * Cleaner specific detailed trace messages will be issued at this level.
     * Value should be one of the predefined java.util.logging.Level values.
     *
     * <p><table border"1">
     * <tr><td>Name</td><td>Type</td><td>Mutable</td><td>Default</td></tr>
     * <tr>
     * <td>{@value}</td>
     * <td>String</td>
     * <td>Yes</td>
     * <td>"FINE"</td>
     * </tr>
     * </table></p>
     */
    public static final String TRACE_LEVEL_CLEANER =
        "java.util.logging.level.cleaner";

    /**
     * For unit testing, to prevent creating the utilization profile DB.
     */
    private boolean createUP = true;

    /**
     * For unit testing, to prevent writing utilization data during checkpoint.
     */
    private boolean checkpointUP = true;

    private boolean allowCreate = false;

    /**
     * For unit testing, to set readCommitted as the default.
     */
    private boolean txnReadCommitted = false;

    /**
     * Creates an EnvironmentConfig initialized with the system default
     * settings.
     */
    public EnvironmentConfig() {
        super();
    }

    /**
    * Creates an EnvironmentConfig which includes the properties specified in
    * the properties parameter.
    *
    * @param properties Supported properties are described in the sample
    * property file.
    *
    * @throws IllegalArgumentException If any properties read from the
    * properties param are invalid.
     */
    public EnvironmentConfig(Properties properties)
        throws IllegalArgumentException {

        super(properties);
    }

    /**
     * If true, creates the database environment if it doesn't already exist.
     *
     * @param allowCreate If true, the database environment is created if it
     * doesn't already exist.
     */
    public void setAllowCreate(boolean allowCreate) {

        this.allowCreate = allowCreate;
    }

    /**
     * Returns a flag that specifies if we may create this environment.
     *
     * @return true if we may create this environment.
     */
    public boolean getAllowCreate() {

        return allowCreate;
    }

    /**
     * Configures the lock timeout, in microseconds.
     *
     * <p>Equivalent to setting the je.lock.timeout parameter in the
     * je.properties file.</p>
     *
     * @param timeout The lock timeout, in microseconds. A value of 0 turns off
     * lock timeouts.
     *
     * @throws IllegalArgumentException If the value of timeout is negative 
     * @see Transaction#setLockTimeout
     */
    public void setLockTimeout(long timeout)
        throws IllegalArgumentException {

        DbConfigManager.setVal(props,
                               EnvironmentParams.LOCK_TIMEOUT,
                               Long.toString(timeout),
                               validateParams);
    }

    /**
     * Returns the lock timeout setting, in microseconds.
     *
     * A value of 0 means no timeout is set.
     */
    public long getLockTimeout() {

        String val = DbConfigManager.getVal(props,
                                            EnvironmentParams.LOCK_TIMEOUT);
        long timeout = 0;
        try {
            timeout = Long.parseLong(val);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException
		("Bad value for timeout:" + e.getMessage());
        }
        return timeout;
    }

    /**
     * Configures the database environment to be read only, and any attempt to
     * modify a database will fail.
     *
     * @param readOnly If true, configure the database environment to be read
     * only, and any attempt to modify a database will fail.
     */
    public void setReadOnly(boolean readOnly) {

        DbConfigManager.setVal(props,
                               EnvironmentParams.ENV_RDONLY,
                               Boolean.toString(readOnly),
                               validateParams);
    }

    /**
     * Returns true if the database environment is configured to be read only.
     *
     * <p>This method may be called at any time during the life of the
     * application.</p>
     *
     * @return True if the database environment is configured to be read only.
     */
    public boolean getReadOnly() {

        String val = DbConfigManager.getVal(props,
                                            EnvironmentParams.ENV_RDONLY);
        return (Boolean.valueOf(val)).booleanValue();
    }

    /**
     * Configures the database environment for transactions.
     *
     * <p>This configuration option should be used when transactional
     * guarantees such as atomicity of multiple operations and durability are
     * important.</p>
     *
     * @param transactional If true, configure the database environment for
     * transactions.
     */
    public void setTransactional(boolean transactional) {

        DbConfigManager.setVal(props,
                               EnvironmentParams.ENV_INIT_TXN,
                               Boolean.toString(transactional),
                               validateParams);
    }

    /**
     * Returns true if the database environment is configured for transactions.
     *
     * <p>This method may be called at any time during the life of the
     * application.</p>
     *
     * @return True if the database environment is configured for transactions.
     */
    public boolean getTransactional() {

        String val = DbConfigManager.getVal(props,
                                            EnvironmentParams.ENV_INIT_TXN);
        return (Boolean.valueOf(val)).booleanValue();
    }

    /**
     * Configures the database environment for no locking.
     *
     * <p>This configuration option should be used when locking guarantees such
     * as consistency and isolation are not important.  If locking mode is
     * disabled (it is enabled by default), the cleaner is automatically
     * disabled.  The user is responsible for invoking the cleaner and ensuring
     * that there are no concurrent operations while the cleaner is
     * running.</p>
     *
     * @param locking If false, configure the database environment for no
     * locking.  The default is true.
     */
    public void setLocking(boolean locking) {

        DbConfigManager.setVal(props,
                               EnvironmentParams.ENV_INIT_LOCKING,
                               Boolean.toString(locking),
                               validateParams);
    }

    /**
     * Returns true if the database environment is configured for locking.
     *
     * <p>This method may be called at any time during the life of the
     * application.</p>
     *
     * @return True if the database environment is configured for locking.
     */
    public boolean getLocking() {

        String val =
            DbConfigManager.getVal(props, EnvironmentParams.ENV_INIT_LOCKING);
        return (Boolean.valueOf(val)).booleanValue();
    }

    /**
     * Configures the transaction timeout, in microseconds.
     *
     * <p>Equivalent to setting the je.txn.timeout parameter in the
     * je.properties file.</p>
     *
     * @param timeout The transaction timeout, in microseconds. A value of 0
     * turns off transaction timeouts.
     *
     * @throws IllegalArgumentException If the value of timeout is negative 
     *
     * @see Transaction#setTxnTimeout
     */
    public void setTxnTimeout(long timeout)
        throws IllegalArgumentException {

        DbConfigManager.setVal(props,
                               EnvironmentParams.TXN_TIMEOUT,
                               Long.toString(timeout),
                               validateParams);
    }

    /**
     * Returns the transaction timeout, in microseconds.
     *
     * <p>A value of 0 means transaction timeouts are not configured.</p>
     *
     * @return The transaction timeout, in microseconds.
     */
    public long getTxnTimeout() {

        String val = DbConfigManager.getVal(props,
                                            EnvironmentParams.TXN_TIMEOUT);
        long timeout = 0;
        try {
            timeout = Long.parseLong(val);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException
		("Bad value for timeout:" + e.getMessage());
        }
        return timeout;
    }

    /**
     * Configures all transactions for this environment to have Serializable
     * (Degree 3) isolation.  By setting Serializable isolation, phantoms will
     * be prevented.  By default transactions provide Repeatable Read
     * isolation.
     *
     * The default is false for the database environment.
     *
     * @see LockMode
     */
    public void setTxnSerializableIsolation(boolean txnSerializableIsolation) {

        DbConfigManager.setVal(props,
                               EnvironmentParams.TXN_SERIALIZABLE_ISOLATION,
                               Boolean.toString(txnSerializableIsolation),
                               validateParams);
    }

    /**
     * Returns true if all transactions for this environment has been
     * configured to have Serializable (Degree 3) isolation.
     *
     * @return true if the environment has been configured to have repeatable
     * read isolation.
     *
     * @see LockMode
     */
    public boolean getTxnSerializableIsolation() {

        String val = DbConfigManager.getVal
	    (props, EnvironmentParams.TXN_SERIALIZABLE_ISOLATION);
        return (Boolean.valueOf(val)).booleanValue();
    }

    /**
     * For unit testing, sets readCommitted as the default.
     */
    void setTxnReadCommitted(boolean txnReadCommitted) {

        this.txnReadCommitted = txnReadCommitted;
    }

    /**
     * For unit testing, to set readCommitted as the default.
     */
    boolean getTxnReadCommitted() {

        return txnReadCommitted;
    }

    /**
     * If true, the shared cache is used by this environment.
     *
     * <p>By default this parameter is false and this environment uses a
     * private cache.  If this parameter is set to true, this environment will
     * use a cache that is shared with all other open environments in this
     * process that also set this parameter to true.  There is a single shared
     * cache per process.</p>
     *
     * <p>By using the shared cache, multiple open environments will make
     * better use of memory because the cache LRU algorithm is applied across
     * all information in all environments sharing the cache.  For example, if
     * one environment is open but not recently used, then it will only use a
     * small portion of the cache, leaving the rest of the cache for
     * environments that have been recently used.</p>
     *
     * @param sharedCache If true, the shared cache is used by this
     * environment.
     */
    public void setSharedCache(boolean sharedCache) {

        DbConfigManager.setVal(props,
                               EnvironmentParams.ENV_SHARED_CACHE,
                               Boolean.toString(sharedCache),
                               validateParams);
    }

    /**
     * Returns true if the shared cache is used by this environment.
     *
     * @return true if the shared cache is used by this environment. @see
     * #setSharedCache
     */
    public boolean getSharedCache() {

        String val = DbConfigManager.getVal
            (props, EnvironmentParams.ENV_SHARED_CACHE);
        return (Boolean.valueOf(val)).booleanValue();
    }

    /* Documentation inherited from EnvironmentMutableConfig.setConfigParam. */
    @Override
    public void setConfigParam(String paramName,
			       String value)
        throws IllegalArgumentException {

        DbConfigManager.setConfigParam(props,
                                       paramName,
                                       value,
                                       false, /* requireMutablity */
                                       validateParams,
                                       false  /* forReplication */,
				       true   /* verifyForReplication */);
    }

    /**
     * For unit testing, to prevent creating the utilization profile DB.
     */
    void setCreateUP(boolean createUP) {
        this.createUP = createUP;
    }

    /**
     * For unit testing, to prevent creating the utilization profile DB.
     */
    boolean getCreateUP() {
        return createUP;
    }

    /**
     * For unit testing, to prevent writing utilization data during checkpoint.
     */
    void setCheckpointUP(boolean checkpointUP) {
        this.checkpointUP = checkpointUP;
    }

    /**
     * For unit testing, to prevent writing utilization data during checkpoint.
     */
    boolean getCheckpointUP() {
        return checkpointUP;
    }

    /**
     * Used by Environment to create a copy of the application
     * supplied configuration.
     */
    EnvironmentConfig cloneConfig() {
        try {
            return (EnvironmentConfig) clone();
        } catch (CloneNotSupportedException willNeverOccur) {
            return null;
        }
    }

    /**
     * Display configuration values.
     */
    @Override
    public String toString() {
        return ("allowCreate=" + allowCreate + "\n" + super.toString());
    }
}
