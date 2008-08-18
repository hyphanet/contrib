/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2000,2008 Oracle.  All rights reserved.
 *
 * $Id: DbEnvPool.java,v 1.45 2008/01/07 14:28:48 cwl Exp $
 */

package com.sleepycat.je.dbi;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.EnvironmentMutableConfig;
import com.sleepycat.je.latch.LatchSupport;

/**
 * Singleton collection of environments.  Responsible for environment open and
 * close, supporting this from multiple threads by synchronizing on the pool.
 *
 * When synchronizing on two or more of the following objects the
 * synchronization order must be as follows.  Synchronization is not performed
 * in constructors, of course, because no other thread can access the object.
 *
 * Synchronization order:  Environment, DbEnvPool, EnvironmentImpl, Evictor
 *
 * Environment ctor                                 NOT synchronized
 *   calls DbEnvPool.getEnvironment                 synchronized
 *     creates new EnvironmentImpl                  NOT synchronized
 *       calls RecoveryManager.recover,buildTree    NOT synchronized
 *         calls Evictor.addEnvironment             synchronized
 *
 * EnvironmentImpl.reinit                           NOT synchronized
 *   calls DbEnvPool.reinitEnvironment              synchronized
 *     calls EnvironmentImpl.doReinit               synchronized
 *       calls RecoveryManager.recover,buildTree    NOT synchronized
 *         calls Evictor.addEnvironment             synchronized
 *
 * Environment.close                                synchronized
 *   calls EnvironmentImpl.close                    NOT synchronized
 *     calls DbEnvPool.closeEnvironment             synchronized
 *       calls EnvironmentImpl.doClose              synchronized
 *         calls Evictor.removeEnvironment          synchronized
 *
 * Environment.setMutableConfig                     synchronized
 *   calls EnvironmentImpl.setMutableConfig         NOT synchronized
 *     calls DbEnvPool.setMutableConfig             synchronized
 *       calls EnvironmentImpl.doSetMutableConfig   synchronized
 */
public class DbEnvPool {
    /* Singleton instance. */
    private static DbEnvPool pool = new DbEnvPool();

    /*
     * Collection of environment handles, mapped by canonical directory
     * name->EnvironmentImpl object.
     */
    private Map<String,EnvironmentImpl> envs;

    /* Environments (subset of envs) that share the global cache. */
    private Set<EnvironmentImpl> sharedCacheEnvs;

    /**
     * Enforce singleton behavior.
     */
    private DbEnvPool() {
        envs = new HashMap<String,EnvironmentImpl>();
        sharedCacheEnvs = new HashSet<EnvironmentImpl>();
    }

    /**
     * Access the singleton instance.
     */
    public static DbEnvPool getInstance() {
        return pool;
    }

    public synchronized int getNSharedCacheEnvironments() {
        return sharedCacheEnvs.size();
    }

    private EnvironmentImpl getAnySharedCacheEnv() {
        Iterator<EnvironmentImpl> iter = sharedCacheEnvs.iterator();
        return iter.hasNext() ? iter.next() : null;
    }

    /**
     * Find a single environment, used by Environment handles and by command
     * line utilities.
     */
    public synchronized
        EnvironmentImpl getEnvironment(File envHome,
                                       EnvironmentConfig config,
                                       boolean checkImmutableParams,
                                       boolean openIfNeeded,
                                       boolean replicationIntended)
        throws DatabaseException {

        String environmentKey = getEnvironmentMapKey(envHome);
        EnvironmentImpl envImpl = envs.get(environmentKey);
        if (envImpl != null) {
            envImpl.checkIfInvalid();
            assert envImpl.isOpen();
            if (checkImmutableParams) {

                /*
                 * If a non-null configuration parameter was passed to the
                 * Environment ctor and the underlying EnvironmentImpl already
                 * exist, check that the configuration parameters specified
                 * match those of the currently open environment. An exception
                 * is thrown if the check fails.
                 *
                 * Don't do this check if we create the environment here
                 * because the creation might modify the parameters, which
                 * would create a Catch-22 in terms of validation.  For
                 * example, je.maxMemory will be overridden if the JVM's -mx
                 * flag is less than that setting, so the new resolved config
                 * parameters won't be the same as the passed in config.
                 */
                envImpl.checkImmutablePropsForEquality(config);
            }
            /* Successful, increment reference count */
            envImpl.incReferenceCount();
        } else {
            if (openIfNeeded) {

                /*
                 * If a shared cache is used, get another (any other, doesn't
                 * matter which) environment that is sharing the global cache.
                 */
                EnvironmentImpl sharedCacheEnv = config.getSharedCache() ?
                    getAnySharedCacheEnv() : null;

                /*
                 * Environment must be instantiated. If it can be created, the
                 * configuration must have allowCreate set.  Note that the
                 * environment is added to the SharedEvictor before the
                 * EnvironmentImpl ctor returns, by RecoveryManager.buildTree.
                 */
                envImpl = new EnvironmentImpl
                    (envHome, config, sharedCacheEnv, replicationIntended);

                assert config.getSharedCache() == envImpl.getSharedCache();

                /* Successful */
                addEnvironment(envImpl);
            }
        }

        return envImpl;
    }

    /**
     * Called by EnvironmentImpl.reinit to perform the reinit operation while
     * synchronized on the DbEnvPool.
     */
    synchronized void reinitEnvironment(EnvironmentImpl envImpl,
                                        boolean replicationIntended)
	throws DatabaseException {

        assert !envs.containsKey
            (getEnvironmentMapKey(envImpl.getEnvironmentHome()));
        assert !sharedCacheEnvs.contains(envImpl);

        /*
         * If a shared cache is used, get another (any other, doesn't
         * matter which) environment that is sharing the global cache.
         */
        EnvironmentImpl sharedCacheEnv = envImpl.getSharedCache() ?
            getAnySharedCacheEnv() : null;

        envImpl.doReinit(replicationIntended, sharedCacheEnv);

        /* Successful */
        addEnvironment(envImpl);
    }

    /**
     * Called by EnvironmentImpl.setMutableConfig to perform the
     * setMutableConfig operation while synchronized on the DbEnvPool.
     *
     * In theory we shouldn't need to synchronize here when
     * envImpl.getSharedCache() is false; however, we synchronize
     * unconditionally to standardize the synchronization order and avoid
     * accidental deadlocks.
     */
    synchronized void setMutableConfig(EnvironmentImpl envImpl,
                                       EnvironmentMutableConfig mutableConfig)
        throws DatabaseException {

        envImpl.doSetMutableConfig(mutableConfig);
        if (envImpl.getSharedCache()) {
            resetSharedCache(envImpl.getMemoryBudget().getMaxMemory(),
                             envImpl);
        }
    }

    /**
     * Called by EnvironmentImpl.close to perform the close operation while
     * synchronized on the DbEnvPool.
     */
    synchronized void closeEnvironment(EnvironmentImpl envImpl,
                                       boolean doCheckpoint,
                                       boolean doCheckLeaks)
        throws DatabaseException {

        if (envImpl.decReferenceCount()) {
            try {
                envImpl.doClose(doCheckpoint, doCheckLeaks);
            } finally {
                removeEnvironment(envImpl);
            }
        }
    }

    /**
     * Called by EnvironmentImpl.closeAfterRunRecovery to perform the close
     * operation while synchronized on the DbEnvPool.
     */
    synchronized void closeEnvironmentAfterRunRecovery(EnvironmentImpl envImpl)
        throws DatabaseException {

        try {
            envImpl.doCloseAfterRunRecovery();
        } finally {
            removeEnvironment(envImpl);
        }
    }

    /**
     * Adds an EnvironmentImpl to the pool after it has been opened.  This
     * method is called while synchronized.
     */
    private void addEnvironment(EnvironmentImpl envImpl)
        throws DatabaseException {

        envImpl.incReferenceCount();
        envs.put(getEnvironmentMapKey(envImpl.getEnvironmentHome()), envImpl);
        if (envImpl.getSharedCache()) {
            sharedCacheEnvs.add(envImpl);
            assert envImpl.getEvictor().checkEnvs(sharedCacheEnvs);
            resetSharedCache(-1, envImpl);
        }
    }

    /**
     * Removes an EnvironmentImpl from the pool after it has been closed.  This
     * method is called while synchronized.  Note that the environment was
     * removed from the SharedEvictor by EnvironmentImpl.shutdownEvictor.
     */
    private void removeEnvironment(EnvironmentImpl envImpl)
        throws DatabaseException {

        String environmentKey =
            getEnvironmentMapKey(envImpl.getEnvironmentHome());
        boolean found = envs.remove(environmentKey) != null;

        if (sharedCacheEnvs.remove(envImpl)) {
            assert found && envImpl.getSharedCache();
            assert envImpl.getEvictor().checkEnvs(sharedCacheEnvs);
            if (sharedCacheEnvs.isEmpty()) {
                envImpl.getEvictor().shutdown();
            } else {
                envImpl.getMemoryBudget().subtractCacheUsage();
                resetSharedCache(-1, null);
            }
        } else {
            assert !found || !envImpl.getSharedCache();
        }

        /*
         * Latch notes may only be cleared when there is no possibility that
         * any environment is open.
         */
        if (envs.isEmpty()) {
            LatchSupport.clearNotes();
        }
    }

    /**
     * For unit testing only.
     */
    public synchronized void clear() {
        envs.clear();
    }

    /* Use the canonical path name for a normalized environment key. */
    private String getEnvironmentMapKey(File file)
        throws DatabaseException {
        try {
            return file.getCanonicalPath();
        } catch (IOException e) {
            throw new DatabaseException(e);
        }
    }

    /**
     * Resets the memory budget for all environments with a shared cache.
     *
     * @param newMaxMemory is the new total cache budget or is less than 0 if
     * the total should remain unchanged.  A total greater than zero is given
     * when it has changed via setMutableConfig.
     *
     * @param skipEnv is an environment that should not be reset, or null.
     * Non-null is passed when an environment has already been reset because
     * it was just created or the target of setMutableConfig.
     */
    private void resetSharedCache(long newMaxMemory, EnvironmentImpl skipEnv)
        throws DatabaseException {

        for (EnvironmentImpl envImpl : sharedCacheEnvs) {
            if (envImpl != skipEnv) {
                envImpl.getMemoryBudget().reset(newMaxMemory,
                                                false /*newEnv*/,
                                                envImpl.getConfigManager());
            }
        }
    }
}
