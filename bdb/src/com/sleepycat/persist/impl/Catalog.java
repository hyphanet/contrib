/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: Catalog.java,v 1.9.2.1 2007/02/01 14:49:56 cwl Exp $
 */

package com.sleepycat.persist.impl;

import java.util.IdentityHashMap;
import java.util.Map;

import com.sleepycat.persist.raw.RawObject;

/**
 * Catalog operation interface used by format classes.
 *
 * @see PersistCatalog
 * @see SimpleCatalog
 * @see ReadOnlyCatalog
 *
 * @author Mark Hayes
 */
interface Catalog {

    /**
     * Returns a format for a given ID, or throws an exception.  This method is
     * used when reading an object from the byte array format.
     *
     * @throws IllegalStateException if the formatId does not correspond to a
     * persistent class.  This is an internal consistency error.
     */
    Format getFormat(int formatId);

    /**
     * Returns a format for a given class, or throws an exception.  This method
     * is used when writing an object that was passed in by the user.
     *
     * @throws IllegalArgumentException if the class is not persistent.  This
     * is a user error.
     */
    Format getFormat(Class cls);

    /**
     * Returns a format by class name.  Unlike {@link #getFormat(Class)}, the
     * format will not be created if it is not already known.
     */
    Format getFormat(String className);

    /**
     * @see PersistCatalog#createFormat
     */
    Format createFormat(String clsName, Map<String,Format> newFormats);

    /**
     * @see PersistCatalog#createFormat
     */
    Format createFormat(Class type, Map<String,Format> newFormats);

    /**
     * @see PersistCatalog#isRawAccess
     */
    boolean isRawAccess();

    /**
     * @see PersistCatalog#convertRawObject
     */
    Object convertRawObject(RawObject o, IdentityHashMap converted);
}
