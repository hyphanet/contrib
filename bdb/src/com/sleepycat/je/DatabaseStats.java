/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: DatabaseStats.java,v 1.24 2008/01/07 14:28:46 cwl Exp $
 */

package com.sleepycat.je;

import java.io.Serializable;

/**
 * Statistics for a single database.
 */
public abstract class DatabaseStats implements Serializable {
    // no public constructor
    protected DatabaseStats() {}
}
