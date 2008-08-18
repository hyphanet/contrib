/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: ModelInternal.java,v 1.9 2008/02/05 23:28:22 mark Exp $
 */

package com.sleepycat.persist.model;

import com.sleepycat.persist.impl.PersistCatalog;

/**
 * <!-- begin JE only -->
 * @hidden
 * <!-- end JE only -->
 * Internal access class that should not be used by applications.
 *
 * @author Mark Hayes
 */
public class ModelInternal {

    /**
     * Internal access method that should not be used by applications.
     */
    public static void setCatalog(EntityModel model, PersistCatalog catalog) {
        model.setCatalog(catalog);
    }
}
