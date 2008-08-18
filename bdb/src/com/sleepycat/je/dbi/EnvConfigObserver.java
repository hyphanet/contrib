/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2000,2008 Oracle.  All rights reserved.
 *
 * $Id: EnvConfigObserver.java,v 1.9 2008/01/07 14:28:48 cwl Exp $
 */

package com.sleepycat.je.dbi;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.EnvironmentMutableConfig;

/**
 * Implemented by observers of mutable config changes.
 */
public interface EnvConfigObserver {

    /**
     * Notifies the observer that one or more mutable properties have been
     * changed.
     */
    void envConfigUpdate(DbConfigManager configMgr,
			 EnvironmentMutableConfig newConfig)
        throws DatabaseException;
}
