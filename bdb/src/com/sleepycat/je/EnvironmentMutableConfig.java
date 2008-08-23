/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: EnvironmentMutableConfig.java,v 1.44 2008/06/30 20:54:46 linda Exp $
 */

package com.sleepycat.je;

import java.util.Enumeration;
import java.util.Iterator;
import java.util.Properties;

import com.sleepycat.je.config.ConfigParam;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.DbConfigManager;
import com.sleepycat.je.dbi.EnvironmentImpl;

/**
 * Specifies the environment attributes that may be changed after the
 * environment has been opened.  EnvironmentMutableConfig is a parameter to
 * {@link Environment#setMutableConfig} and is returned by {@link
 * Environment#getMutableConfig}.
 *
 * <p>There are two types of mutable environment properties: per-environment
 * handle properties, and environment wide properties.</p>
 *
 * <h4>Per-Environment Handle Properties</h4>
 *
 * <p>Per-environment handle properties apply only to a single Environment
 * instance.  For example, to change the default transaction commit behavior
 * for a single environment handle, do this:</p>
 *
 * <blockquote><pre>
 *     // Specify no-sync behavior for a given handle.
 *     EnvironmentMutableConfig mutableConfig = myEnvHandle.getMutableConfig();
 *     mutableConfig.setTxnNoSync(true);
 *     myEnvHandle.setMutableConfig(mutableConfig);
 * </pre></blockquote>
 *
 * <p>The per-environment handle properties are listed below.  These properties
 * are accessed using the setter and getter methods listed, as shown in the
 * example above.</p>
 *
 * <ul>
 * <li>{@link #setTxnNoSync}, {@link #getTxnNoSync}</li>
 * <li>{@link #setTxnWriteNoSync}, {@link #getTxnWriteNoSync}</li>
 * </ul>
 *
 * <h4>Environment-Wide Mutable Properties</h4>
 *
 * <p>Environment-wide mutable properties are those that can be changed for an
 * environment as a whole, irrespective of which environment instance (for the
 * same physical environment) is used.  For example, to stop the cleaner daemon
 * thread, do this:</p>
 *
 * <blockquote><pre>
 *     // Stop the cleaner daemon thread for the environment.
 *     EnvironmentMutableConfig mutableConfig = myEnvHandle.getMutableConfig();
 *     mutableConfig.setConfigParam("je.env.runCleaner", "false");
 *     myEnvHandle.setMutableConfig(mutableConfig);
 * </pre></blockquote>
 *
 * <p>The environment-wide mutable properties are listed below.  These
 * properties are accessed using the {@link #setConfigParam} and {@link
 * #getConfigParam} methods, as shown in the example above, using the property
 * names listed below.  In some cases setter and getter methods are also
 * available.</p>
 *
 * <ul>
 * <li>je.maxMemory ({@link #setCacheSize}, {@link #getCacheSize})</li>
 * <li>je.maxMemoryPercent ({@link #setCachePercent},
 * {@link #getCachePercent})</li>
 * <li>je.env.runINCompressor</li>
 * <li>je.env.runEvictor</li>
 * <li>je.env.runCheckpointer</li>
 * <li>je.env.runCleaner</li>
 * </ul>
 *
 * <h4>Getting the Current Environment Properties</h4>
 *
 * To get the current "live" properties of an environment after constructing it
 * or changing its properties, you must call {@link Environment#getConfig} or
 * {@link Environment#getMutableConfig}.  The original EnvironmentConfig or
 * EnvironmentMutableConfig object used to set the properties is not kept up to
 * date as properties are changed, and does not reflect property validation or
 * properties that are computed. @see EnvironmentConfig
 */
public class EnvironmentMutableConfig implements Cloneable {

    /*
     * Change copyHandlePropsTo and Environment.copyToHandleConfig when adding
     * fields here.
     */
    private boolean txnNoSync = false;
    private boolean txnWriteNoSync = false;
    private Durability durability = null;
    private ReplicaConsistencyPolicy consistencyPolicy = null;

    /**
     * @hidden
     * Cache size is a category of property that is calculated within the
     * environment.  It is only supplied when returning the cache size to the
     * application and never used internally; internal code directly checks
     * with the MemoryBudget class;
     */
    protected long cacheSize;

    /**
     * @hidden
     * Note that in the implementation we choose not to extend Properties in
     * order to keep the configuration type safe.
     */
    protected Properties props;

    /**
     * For unit testing, to prevent loading of je.properties.
     */
    private boolean loadPropertyFile = true;

    /**
     * Internal boolean that says whether or not to validate params.  Setting
     * it to false means that parameter value validatation won't be performed
     * during setVal() calls.  Only should be set to false by unit tests using
     * DbInternal.
     */
    boolean validateParams = true;

    private ExceptionListener exceptionListener = null;

    /**
     * An instance created using the default constructor is initialized with
     * the system's default settings.
     */
    public EnvironmentMutableConfig() {
        props = new Properties();
    }

    /**
     * Used by EnvironmentConfig to construct from properties.
     */
    EnvironmentMutableConfig(Properties properties)
        throws IllegalArgumentException {

        DbConfigManager.validateProperties(properties,
                                           false,  // forReplication
                                           this.getClass().getName(),
					   true);  // verifyForReplication
        /* For safety, copy the passed in properties. */
        props = new Properties();
        props.putAll(properties);
    }

    /**
     * Configures the database environment for asynchronous transactions.
     *
     * @param noSync If true, do not write or synchronously flush the log on
     * transaction commit. This means that transactions exhibit the ACI
     * (Atomicity, Consistency, and Isolation) properties, but not D
     * (Durability); that is, database integrity is maintained, but if the JVM
     * or operating system fails, it is possible some number of the most
     * recently committed transactions may be undone during recovery. The
     * number of transactions at risk is governed by how many updates fit into
     * a log buffer, how often the operating system flushes dirty buffers to
     * disk, and how often the database environment is checkpointed.
     *
     * <p>This attribute is false by default for this class and for the
     * database environment.</p>
     *
     * @throws DatabaseException if a failure occurs.
     */
    public void setTxnNoSync(boolean noSync) {
        TransactionConfig.checkMixedMode
            (false, noSync, txnWriteNoSync, durability);
        txnNoSync = noSync;
    }

    /**
     * Returns true if the database environment is configured for asynchronous
     * transactions.
     *
     * @return true if the database environment is configured for asynchronous
     * transactions.
     */
    public boolean getTxnNoSync() {
        return txnNoSync;
    }

    /**
     * Configures the database environment for transactions which write but do
     * not flush the log.
     *
     * @param writeNoSync If true, write but do not synchronously flush the log
     * on transaction commit. This means that transactions exhibit the ACI
     * (Atomicity, Consistency, and Isolation) properties, but not D
     * (Durability); that is, database integrity is maintained, but if the
     * operating system fails, it is possible some number of the most recently
     * committed transactions may be undone during recovery. The number of
     * transactions at risk is governed by how often the operating system
     * flushes dirty buffers to disk, and how often the database environment is
     * checkpointed.
     *
     * <p>The motivation for this attribute is to provide a transaction that
     * has more durability than asynchronous (nosync) transactions, but has
     * higher performance than synchronous transactions.</p>
     *
     * <p>This attribute is false by default for this class and for the
     * database environment.</p>
     *
     * @throws DatabaseException if a failure occurs.
     */
    public void setTxnWriteNoSync(boolean writeNoSync) {
        TransactionConfig.checkMixedMode
            (false, txnNoSync, writeNoSync, durability);
        txnWriteNoSync = writeNoSync;
    }

    /**
     * Returns true if the database environment is configured for transactions
     * which write but do not flush the log.
     *
     * @return true if the database environment is configured for transactions
     * which write but do not flush the log.
     */
    public boolean getTxnWriteNoSync() {
        return txnWriteNoSync;
    }

    /**
     * @hidden
     * Feature not yet available.
     *
     * Configures the durability associated with transactions.
     *
     * @param durability the durability definition
     */
    public void setDurability(Durability durability) {
        TransactionConfig.checkMixedMode
            (false, txnNoSync, txnWriteNoSync, durability);
        this.durability = durability;
    }

    /**
     * @hidden
     * Feature not yet available.
     *
     * Returns the durability associated with the configuration.
     *
     * @return the durability setting currently associated with this config.
     */
    public Durability getDurability() {
        return durability;
    }

    /**
     * @hidden
     * Feature not yet available.
     *
     * Associates a consistency policy with this configuration.
     *
     * @param consistencyPolicy the consistency definition
     */
    public void setConsistencyPolicy
        (ReplicaConsistencyPolicy consistencyPolicy) {
        this.consistencyPolicy = consistencyPolicy;
    }

    /**
     * @hidden
     * Feature not yet available.
     *
     * Returns the consistency policy associated with the configuration.
     *
     * @return the consistency policy currently associated with this config.
     */
    public ReplicaConsistencyPolicy getConsistencyPolicy() {
        return consistencyPolicy;
    }

    /**
     * Configures the memory available to the database system, in bytes.
     *
     * <p>Equivalent to setting the je.maxMemory property in the je.properties
     * file. The system will evict database objects when it comes within a
     * prescribed margin of the limit.</p>
     *
     * <p>By default, JE sets the cache size to:</p>
     *
     * <pre><blockquote>
     *         je.maxMemoryPercent *  JVM maximum memory
     * </pre></blockquote>
     *
     * <p>where JVM maximum memory is specified by the JVM -Xmx flag. However,
     * calling setCacheSize() with a non-zero value overrides the percentage
     * based calculation and sets the cache size explicitly.</p>
     *
     * <p>Note that the cache does not include transient objects created by the
     * JE library, such as cursors, locks and transactions.</p>
     *
     * <p>Note that the log buffer cache may be cleared if the cache size is
     * changed after the environment has been opened.</p>
     *
     * <p>If setSharedCache(true) is called, setCacheSize and setCachePercent
     * specify the total size of the shared cache, and changing these
     * parameters will change the size of the shared cache.</p>
     *
     * @param totalBytes The memory available to the database system, in bytes.
     *
     * @throws IllegalArgumentException if an invalid parameter was specified.
     *
     * @throws DatabaseException if a failure occurs.
     */
    public void setCacheSize(long totalBytes)
        throws IllegalArgumentException {

        DbConfigManager.setVal(props, EnvironmentParams.MAX_MEMORY,
                               Long.toString(totalBytes), validateParams);
    }

    /**
     * Returns the memory available to the database system, in bytes. A valid
     * value is only available if this EnvironmentConfig object has been
     * returned from Environment.getConfig();
     *
     * @return The memory available to the database system, in bytes.
     */
    public long getCacheSize() {

        /*
         * CacheSize is filled in from the EnvironmentImpl by way of
         * fillInEnvironmentGeneratedProps.
         */
        return cacheSize;
    }

    /**
     * <p>By default, JE sets its cache size proportionally to the JVM
     * memory. This formula is used:</p>
     *
     * <blockquote><pre>
     *         je.maxMemoryPercent *  JVM maximum memory
     * </pre></blockquote>
     *
     * <p>where JVM maximum memory is specified by the JVM -Xmx flag.
     * setCachePercent() specifies the percentage used and is equivalent to
     * setting the je.maxMemoryPercent property in the je.properties file.</p>
     *
     * <p>Calling setCacheSize() with a non-zero value overrides the percentage
     * based calculation and sets the cache size explicitly.</p>
     *
     * <p>Note that the log buffer cache may be cleared if the cache size is
     * changed after the environment has been opened.</p>
     *
     * <p>If setSharedCache(true) is called, setCacheSize and setCachePercent
     * specify the total size of the shared cache, and changing these
     * parameters will change the size of the shared cache.</p>
     *
     * @param percent The percent of JVM memory to allocate to the JE cache.
     *
     * @throws IllegalArgumentException if an invalid parameter was specified.
     *
     * @throws DatabaseException if a failure occurs.
     */
    public void setCachePercent(int percent)
        throws IllegalArgumentException {

        DbConfigManager.setVal(props, EnvironmentParams.MAX_MEMORY_PERCENT,
                               Integer.toString(percent), validateParams);
    }

    /**
     * Returns the percentage value used in the JE cache size calculation.
     *
     * @return the percentage value used in the JE cache size calculation.
     */
    public int getCachePercent() {

        String val =
            DbConfigManager.getVal(props,
                                   EnvironmentParams.MAX_MEMORY_PERCENT);
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException
		("Cache percent is not a valid integer: " + e.getMessage());
        }
    }

    /**
     * Sets the exception listener for an Environment.  The listener is called
     * when a daemon thread throws an exception, in order to provide a
     * notification mechanism for these otherwise asynchronous exceptions.
     * Daemon thread exceptions are also printed through stderr.
     * <p>
     * Not all daemon exceptions are fatal, and the application bears
     * responsibility for choosing how to respond to the notification. Since
     * exceptions may repeat, the application should also choose how to handle
     * a spate of exceptions. For example, the application may choose to act
     * upon each notification, or it may choose to batch up its responses
     * by implementing the listener so it stores exceptions, and only acts
     * when a certain number have been received.
     * @param exceptionListener the callback to be executed when an exception
     * occurs.
     */
    public void setExceptionListener(ExceptionListener exceptionListener) {
	this.exceptionListener = exceptionListener;
    }

    /**
     * Returns the exception listener, if set.
     */
    public ExceptionListener getExceptionListener() {
	return exceptionListener;
    }

    /**
     * Validates the value prescribed for the configuration parameter; if it is
     * valid, the value is set in the configuration.
     *
     * @param paramName The name of the configuration parameter. See
     * the sample je.properties file for descriptions of all parameters.
     *
     * @param value The value for this configuration parameter.
     *
     * @throws IllegalArgumentException if an invalid parameter was specified.
     *
     * @throws DatabaseException if a failure occurs.
     */
    public void setConfigParam(String paramName, String value)
        throws IllegalArgumentException {

        DbConfigManager.setConfigParam(props,
                                       paramName,
                                       value,
                                       true, /* require mutability. */
                                       validateParams,
                                       false /* forReplication */,
				       true  /* verifyForReplication */);
    }

    /**
     * Returns the value for this configuration parameter.
     *
     * @param paramName Name of the requested parameter.
     *
     * @throws IllegalArgumentException if the configParamName is invalid.
     */
    public String getConfigParam(String paramName)
        throws IllegalArgumentException {

       return DbConfigManager.getConfigParam(props, paramName);
    }

    /*
     * Helpers
     */
    void setValidateParams(boolean validateParams) {
	this.validateParams = validateParams;
    }

    /**
     * Checks that the immutable values in the environment config used to open
     * an environment match those in the config object saved by the underlying
     * shared EnvironmentImpl.
     */
    void checkImmutablePropsForEquality(EnvironmentMutableConfig passedConfig)
        throws IllegalArgumentException {

        Properties passedProps = passedConfig.props;
        Iterator<String> iter = EnvironmentParams.SUPPORTED_PARAMS.keySet().iterator();
        while (iter.hasNext()) {
            String paramName = iter.next();
            ConfigParam param = EnvironmentParams.SUPPORTED_PARAMS.get(paramName);
            assert param != null;
            if (!param.isMutable()) {
                String paramVal = props.getProperty(paramName);
                String useParamVal = passedProps.getProperty(paramName);
                if ((paramVal != null) ? (!paramVal.equals(useParamVal))
                                       : (useParamVal != null)) {
                    throw new IllegalArgumentException
                        (paramName + " is set to " +
                         useParamVal +
                         " in the config parameter" +
                         " which is incompatible" +
                         " with the value of " +
                         paramVal + " in the" +
                         " underlying environment");
                }
            }
        }
    }

    /**
     * @hidden
     * For internal use only.
     * Overrides Object.clone() to clone all properties, used by this class and
     * EnvironmentConfig.
     */
    @Override
    protected Object clone()
        throws CloneNotSupportedException {

        EnvironmentMutableConfig copy =
            (EnvironmentMutableConfig) super.clone();
        copy.props = (Properties) props.clone();
        return copy;
    }

    /**
     * Used by Environment to create a copy of the application supplied
     * configuration. Done this way to provide non-public cloning.
     */
    EnvironmentMutableConfig cloneMutableConfig() {
        try {
            EnvironmentMutableConfig copy = (EnvironmentMutableConfig) clone();
            /* Remove all immutable properties. */
            copy.clearImmutableProps();
            return copy;
        } catch (CloneNotSupportedException willNeverOccur) {
            return null;
        }
    }

    /**
     * Copies the per-handle properties of this object to the given config
     * object.
     */
    void copyHandlePropsTo(EnvironmentMutableConfig other) {
        other.txnNoSync = txnNoSync;
        other.txnWriteNoSync = txnWriteNoSync;
        other.durability = durability;
        other.consistencyPolicy = consistencyPolicy;
    }

    /**
     * Copies all mutable props to the given config object.
     * Unchecked suppress here because Properties don't play well with 
     * generics in Java 1.5 
     */
    @SuppressWarnings("unchecked") 
	void copyMutablePropsTo(EnvironmentMutableConfig toConfig) {

        Properties toProps = toConfig.props;
        Enumeration propNames = props.propertyNames();
        while (propNames.hasMoreElements()) {
            String paramName = (String) propNames.nextElement();
            ConfigParam param = (ConfigParam)
                EnvironmentParams.SUPPORTED_PARAMS.get(paramName);
            assert param != null;
            if (param.isMutable()) {
                String newVal = props.getProperty(paramName);
                toProps.setProperty(paramName, newVal);
            }
        }
	toConfig.exceptionListener = this.exceptionListener;
    }

    /**
     * Fills in the properties calculated by the environment to the given
     * config object.
     */
    void fillInEnvironmentGeneratedProps(EnvironmentImpl envImpl) {
        cacheSize = envImpl.getMemoryBudget().getMaxMemory();
    }

   /**
    * Removes all immutable props.
    * Unchecked suppress here because Properties don't play well with 
    * generics in Java 1.5
    */ 
    @SuppressWarnings("unchecked")
    private void clearImmutableProps() {
        Enumeration propNames = props.propertyNames();
        while (propNames.hasMoreElements()) {
            String paramName = (String) propNames.nextElement();
            ConfigParam param = (ConfigParam)
                EnvironmentParams.SUPPORTED_PARAMS.get(paramName);
            assert param != null;
            if (!param.isMutable()) {
                props.remove(paramName);
            }
        }
    }

    Properties getProps() {
        return props;
    }

    /**
     * For unit testing, to prevent loading of je.properties.
     */
    void setLoadPropertyFile(boolean loadPropertyFile) {
        this.loadPropertyFile = loadPropertyFile;
    }

    /**
     * For unit testing, to prevent loading of je.properties.
     */
    boolean getLoadPropertyFile() {
        return loadPropertyFile;
    }

    /**
     * Testing support
     */
    public int getNumExplicitlySetParams() {
        return props.size();
    }

    /**
     * Display configuration values.
     */
    @Override
    public String toString() {
        return ("cacheSize=" + cacheSize + "\n" +
                "txnNoSync=" + txnNoSync + "\n" +
                "txnWriteNoSync=" + txnWriteNoSync + "\n" +
                props.toString() + "\n");
    }
}
