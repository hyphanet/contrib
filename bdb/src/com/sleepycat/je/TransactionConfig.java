/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: TransactionConfig.java,v 1.24 2008/06/10 00:21:30 cwl Exp $
 */

package com.sleepycat.je;

import com.sleepycat.je.Durability.ReplicaAckPolicy;
import com.sleepycat.je.Durability.SyncPolicy;

/**
 * Specifies the attributes of a database environment transaction.
 */
public class TransactionConfig implements Cloneable {

    /**
     * Default configuration used if null is passed to methods that create a
     * transaction.
     */
    public static final TransactionConfig DEFAULT = new TransactionConfig();

    private boolean sync = false;
    private boolean noSync = false;
    private boolean writeNoSync = false;
    private Durability durability = null;
    private ReplicaConsistencyPolicy consistencyPolicy;

    private boolean noWait = false;
    private boolean readUncommitted = false;
    private boolean readCommitted = false;
    private boolean serializableIsolation = false;

    /* Convenience constants for local (non-replicated) use. */

    /**
     * @hidden
     * Feature not yet available.
     *
     * Defines a durability policy with SYNC for local commit synchronization.
     *
     * The replicated environment policies default to SYNC for commits of
     * replicated transactions that need acknowledgment and QUORUM for the
     * acknowledgment policy.
     */
    public static final Durability SYNC =
        new Durability(SyncPolicy.SYNC,
                       SyncPolicy.SYNC,
                       ReplicaAckPolicy.QUORUM);

    /**
     * @hidden
     * Feature not yet available.
     *
     * Defines a durability policy with NO_SYNC for local commit
     * synchronization.
     *
     * The replicated environment policies default to SYNC for commits of
     * replicated transactions that need acknowledgment and QUORUM for the
     * acknowledgment policy.
     */
    public static final Durability NO_SYNC =
        new Durability(SyncPolicy.NO_SYNC,
                       SyncPolicy.SYNC,
                       ReplicaAckPolicy.QUORUM);

    /**
     * @hidden
     * Feature not yet available.
     *
     * Defines a durability policy with WRITE_NO_SYNC for local commit
     * synchronization.
     *
     * The replicated environment policies default to SYNC for commits of
     * replicated transactions that need acknowledgment and QUORUM for the
     * acknowledgment policy.
     */
    public static final Durability WRITE_NO_SYNC =
        new Durability(SyncPolicy.WRITE_NO_SYNC,
                       SyncPolicy.SYNC,
                       ReplicaAckPolicy.QUORUM);

    /**
     * An instance created using the default constructor is initialized with
     * the system's default settings.
     */
    public TransactionConfig() {
    }

    /**
     * @hidden
     * For internal use only.
     *
     * Maps the existing sync settings to the equivalent durability settings.
     * Figure out what we should do on commit. TransactionConfig could be
     * set with conflicting values; take the most stringent ones first.
     * All environment level defaults were applied by the caller.
     *
     * ConfigSync  ConfigWriteNoSync ConfigNoSync   default
     *    0                 0             0         sync
     *    0                 0             1         nosync
     *    0                 1             0         write nosync
     *    0                 1             1         write nosync
     *    1                 0             0         sync
     *    1                 0             1         sync
     *    1                 1             0         sync
     *    1                 1             1         sync
     *
     * @return the equivalent durability
     */
    public Durability getDurabilityFromSync() {
        if (sync) {
            return SYNC;
        } else if (writeNoSync) {
            return WRITE_NO_SYNC;
        } else if (noSync) {
            return NO_SYNC;
        }
        return SYNC;
    }

    /**
     * Configures the transaction to write and synchronously flush the log it
     * when commits.
     *
     * <p>This behavior may be set for a database environment using the
     * Environment.setMutableConfig method. Any value specified to this method
     * overrides that setting.</p>
     *
     * <p>The default is false for this class and true for the database
     * environment.</p>
     *
     * <p>If true is passed to both setSync and setNoSync, setSync will take
     * precedence.</p>
     *
     * @param sync If true, transactions exhibit all the ACID (atomicity,
     * consistency, isolation, and durability) properties.
     */
    public void setSync(boolean sync) {
        checkMixedMode(sync, noSync, writeNoSync, durability);
        this.sync = sync;
    }

    /**
     * Returns true if the transaction is configured to write and synchronously
     * flush the log it when commits.
     *
     * @return true if the transaction is configured to write and synchronously
     * flush the log it when commits.
     */
    public boolean getSync() {
        return sync;
    }

    /**
     * Configures the transaction to not write or synchronously flush the log
     * it when commits.
     *
     * <p>This behavior may be set for a database environment using the
     * Environment.setMutableConfig method. Any value specified to this method
     * overrides that setting.</p>
     *
     * <p>The default is false for this class and the database environment.</p>
     *
     * @param noSync If true, transactions exhibit the ACI (atomicity,
     * consistency, and isolation) properties, but not D (durability); that is,
     * database integrity will be maintained, but if the application or system
     * fails, it is possible some number of the most recently committed
     * transactions may be undone during recovery. The number of transactions
     * at risk is governed by how many log updates can fit into the log buffer,
     * how often the operating system flushes dirty buffers to disk, and how
     * often the log is checkpointed.
     */
    public void setNoSync(boolean noSync) {
        checkMixedMode(sync, noSync, writeNoSync, durability);
        this.noSync = noSync;
    }

    /**
     * Returns true if the transaction is configured to not write or
     * synchronously flush the log it when commits.
     *
     * @return true if the transaction is configured to not write or
     * synchronously flush the log it when commits.
     */
    public boolean getNoSync() {
        return noSync;
    }

    /**
     * Configures the transaction to write but not synchronously flush the log
     * it when commits.
     *
     * <p>This behavior may be set for a database environment using the
     * Environment.setMutableConfig method. Any value specified to this method
     * overrides that setting.</p>
     *
     * <p>The default is false for this class and the database environment.</p>
     *
     * @param writeNoSync If true, transactions exhibit the ACI (atomicity,
     * consistency, and isolation) properties, but not D (durability); that is,
     * database integrity will be maintained, but if the operating system
     * fails, it is possible some number of the most recently committed
     * transactions may be undone during recovery. The number of transactions
     * at risk is governed by how often the operating system flushes dirty
     * buffers to disk, and how often the log is checkpointed.
     */
    public void setWriteNoSync(boolean writeNoSync) {
        checkMixedMode(sync, noSync, writeNoSync, durability);
        this.writeNoSync = writeNoSync;
    }

    /**
     * Returns true if the transaction is configured to write but not
     * synchronously flush the log it when commits.
     *
     * @return true if the transaction is configured to not write or
     * synchronously flush the log it when commits.
     */
    public boolean getWriteNoSync() {
        return writeNoSync;
    }

    /**
     * @hidden
     * Feature not yet available.
     *
     * Configures the durability associated with a transaction when it commits.
     * Changes to durability are not reflected back to the "sync" booleans --
     * there isn't a one to one mapping.
     *
     * Note that you should not use both the durability and the XXXSync() apis
     * on the same config object.
     *
     * @param durability the durability definition
     */
    public void setDurability(Durability durability) {
        checkMixedMode(sync, noSync, writeNoSync, durability);
        this.durability = durability;
    }

    /**
     * @hidden
     * Feature not yet available.
     *
     * Returns the durability associated with the configuration. As a
     * compatibility hack, it currently returns the local durability
     * computed from the current "sync" settings, if the durability has not
     * been explicitly set by the application.
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
     * Configures the transaction to not wait if a lock request cannot be
     * immediately granted.
     *
     * <p>The default is false for this class and the database environment.</p>
     *
     * @param noWait If true, transactions will not wait if a lock request
     * cannot be immediately granted, instead {@link
     * com.sleepycat.je.DeadlockException DeadlockException} will be thrown.
     */
    public void setNoWait(boolean noWait) {
        this.noWait = noWait;
    }

    /**
     * Returns true if the transaction is configured to not wait if a lock
     * request cannot be immediately granted.
     *
     * @return true if the transaction is configured to not wait if a lock
     * request cannot be immediately granted.
     */
    public boolean getNoWait() {
        return noWait;
    }

    /**
     * Configures read operations performed by the transaction to return
     * modified but not yet committed data.
     *
     * @param readUncommitted If true, configure read operations performed by
     * the transaction to return modified but not yet committed data.
     *
     * @see LockMode#READ_UNCOMMITTED
     */
    public void setReadUncommitted(boolean readUncommitted) {
        this.readUncommitted = readUncommitted;
    }

    /**
     * Returns true if read operations performed by the transaction are
     * configured to return modified but not yet committed data.
     *
     * @return true if read operations performed by the transaction are
     * configured to return modified but not yet committed data.
     *
     * @see LockMode#READ_UNCOMMITTED
     */
    public boolean getReadUncommitted() {
        return readUncommitted;
    }

    /**
     * Configures read operations performed by the transaction to return
     * modified but not yet committed data.
     *
     * @param dirtyRead If true, configure read operations performed by the
     * transaction to return modified but not yet committed data.
     *
     * @deprecated This has been replaced by {@link #setReadUncommitted} to
     * conform to ANSI database isolation terminology.
     */
    public void setDirtyRead(boolean dirtyRead) {
        setReadUncommitted(dirtyRead);
    }

    /**
     * Returns true if read operations performed by the transaction are
     * configured to return modified but not yet committed data.
     *
     * @return true if read operations performed by the transaction are
     * configured to return modified but not yet committed data.
     *
     * @deprecated This has been replaced by {@link #getReadUncommitted} to
     * conform to ANSI database isolation terminology.
     */
    public boolean getDirtyRead() {
        return getReadUncommitted();
    }

    /**
     * Configures the transaction for read committed isolation.
     *
     * <p>This ensures the stability of the current data item read by the
     * cursor but permits data read by this transaction to be modified or
     * deleted prior to the commit of the transaction.</p>
     *
     * @param readCommitted If true, configure the transaction for read
     * committed isolation.
     *
     * @see LockMode#READ_COMMITTED
     */
    public void setReadCommitted(boolean readCommitted) {
        this.readCommitted = readCommitted;
    }

    /**
     * Returns true if the transaction is configured for read committed
     * isolation.
     *
     * @return true if the transaction is configured for read committed
     * isolation.
     *
     * @see LockMode#READ_COMMITTED
     */
    public boolean getReadCommitted() {
        return readCommitted;
    }

    /**
     * Configures this transaction to have serializable (degree 3) isolation.
     * By setting serializable isolation, phantoms will be prevented.
     *
     * <p>By default a transaction provides Repeatable Read isolation; {@link
     * EnvironmentConfig#setTxnSerializableIsolation} may be called to override
     * the default.  If the environment is configured for serializable
     * isolation, all transactions will be serializable regardless of whether
     * this method is called; calling {@link #setSerializableIsolation} with a
     * false parameter will not disable serializable isolation.</p>
     *
     * The default is false for this class and the database environment.
     *
     * @see LockMode
     */
    public void setSerializableIsolation(boolean serializableIsolation) {
        this.serializableIsolation = serializableIsolation;
    }

    /**
     * Returns true if the transaction has been explicitly configured to have
     * serializable (degree 3) isolation.
     *
     * @return true if the transaction has been configured to have serializable
     * isolation.
     *
     * @see LockMode
     */
    public boolean getSerializableIsolation() {
        return serializableIsolation;
    }

    /**
     * Used by Environment to create a copy of the application supplied
     * configuration. Done this way to provide non-public cloning.
     */
    TransactionConfig cloneConfig() {
        try {
            return (TransactionConfig) super.clone();
        } catch (CloneNotSupportedException willNeverOccur) {
            return null;
        }
    }

    /**
     *
     * Checks to catch mixing of deprecated and non-deprecated forms of the
     * API. It's invoked before setting any of the config parameters. The
     * arguments represent the new state of the durability configuration,
     * before it has been changed.
     */
    static void checkMixedMode(boolean sync,
                               boolean noSync,
                               boolean writeNoSync,
                               Durability durability)
        throws IllegalArgumentException {

        if ((sync || noSync || writeNoSync) && (durability != null)) {
            throw new IllegalArgumentException
            ("Mixed use of deprecated and current durability APIs is not " +
             " supported");
        }
    }

    /**
     * Returns the values for each configuration attribute.
     *
     * @return the values for each configuration attribute.
     */
    @Override
    public String toString() {
        return "sync=" + sync +
            "\nnoSync=" + noSync +
            "\nwriteNoSync=" + writeNoSync +
            "\ndurability=" + durability +
            "\nconsistencyPolicy=" + consistencyPolicy +
            "\nnoWait=" + noWait +
            "\nreadUncommitted=" + readUncommitted +
            "\nreadCommitted=" + readCommitted +
            "\nSerializableIsolation=" + serializableIsolation +
            "\n";
    }
}
