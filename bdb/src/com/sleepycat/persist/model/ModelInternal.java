/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2006
 *      Oracle Corporation.  All rights reserved.
 *
 * $Id: ModelInternal.java,v 1.4 2006/09/13 15:48:25 mark Exp $
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
