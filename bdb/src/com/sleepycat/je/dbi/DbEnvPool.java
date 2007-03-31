/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2000,2007 Oracle.  All rights reserved.
 *
 * $Id: DbEnvPool.java,v 1.38.2.2 2007/02/01 14:49:44 cwl Exp $
 */

package com.sleepycat.je.dbi;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.latch.LatchSupport;

/**
 * Singleton collection of database environments.
 */
public class DbEnvPool {
    /* singleton instance. */
    private static DbEnvPool pool = new DbEnvPool();

    /* 
     * Collection of environment handles, mapped by canonical directory 
     * name->EnvironmentImpl object.
     */
    private Map envs;

    /**
     * Enforce singleton behavior.
     */
    private DbEnvPool() {
        envs = new HashMap();
    }

    /**
     * Access the singleton instance.
     */
    public static DbEnvPool getInstance() {
        return pool;
    }

    /**
     * If the environment is not open, open it.
     */
    public EnvironmentImplInfo getEnvironment(File envHome,
                                               EnvironmentConfig config ) 
        throws DatabaseException {

        return getEnvironment(envHome, config, true);
    }

    /* 
     * Only return an environment if it's already bee open in this process.
     */
    public EnvironmentImplInfo getExistingEnvironment(File envHome) 
        throws DatabaseException {

        return getEnvironment(envHome, null, false);
    }

    /**
     * Find a single environment, used by Environment handles and by command
     * line utilities.
     */
    private synchronized
        EnvironmentImplInfo getEnvironment(File envHome,
                                           EnvironmentConfig config,
                                           boolean openIfNeeded)
        throws DatabaseException {

        boolean found;
        boolean firstHandle = false;

        EnvironmentImpl environmentImpl = null;
        String environmentKey = getEnvironmentMapKey(envHome);

        if (envs.containsKey(environmentKey)) {
            /* Environment is resident */
            environmentImpl = (EnvironmentImpl) envs.get(environmentKey);
            if (!environmentImpl.isOpen()) {
                if (openIfNeeded) {
                    environmentImpl.open();
                    found = true;
                } else {
                    found = false;
                }
            } else {
                found = true;
            }
        } else {
            if (openIfNeeded) {

                /* 
                 * Environment must be instantiated. If it can be created,
                 * the configuration must have allowCreate set.
                 */
                environmentImpl = new EnvironmentImpl(envHome, config);
                envs.put(environmentKey, environmentImpl);
                firstHandle = true;
                found = true;
            } else {
                found = false;
            }
        }

        if (found) {
            return new EnvironmentImplInfo(environmentImpl, firstHandle);
        } else {
            return new EnvironmentImplInfo(null, false);
        }
    }


    /**
     * Remove a EnvironmentImpl from the pool because it's been closed.
     */
    synchronized void remove(File envHome)
        throws DatabaseException {

        envs.remove(getEnvironmentMapKey(envHome));

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

    /* 
     * Struct for returning two values.
     */
    public static class EnvironmentImplInfo {
        public EnvironmentImpl envImpl;
        public boolean firstHandle = false;

        EnvironmentImplInfo(EnvironmentImpl envImpl, boolean firstHandle) {
            this.envImpl = envImpl;
            this.firstHandle = firstHandle;
        }
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
}
