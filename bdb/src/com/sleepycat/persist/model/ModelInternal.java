/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2006 Oracle.  All rights reserved.
 *
 * $Id: ModelInternal.java,v 1.5 2006/10/30 21:14:33 bostic Exp $
 */

package com.sleepycat.persist.model;

import com.sleepycat.persist.impl.PersistCatalog;

/**
 * Internal access class that does not appear in the javadoc and should not be
 * used by applications.
 *
 * @author Mark Hayes
 */
public class ModelInternal {

    public static void setCatalog(EntityModel model, PersistCatalog catalog) {
        model.setCatalog(catalog);
    }
}
