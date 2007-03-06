/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2000,2006 Oracle.  All rights reserved.
 *
 * $Id: EnvConfigObserver.java,v 1.5 2006/10/30 21:14:15 bostic Exp $
 */

package com.sleepycat.je.dbi;

import com.sleepycat.je.DatabaseException;

/**
 * Implemented by observers of mutable config changes.
 */
public interface EnvConfigObserver {
    
    /**
     * Notifies the observer that one or more mutable properties have been
     * changed.
     */
    void envConfigUpdate(DbConfigManager configMgr)
        throws DatabaseException;
}
