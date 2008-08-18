/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: CacheMode.java,v 1.2 2008/05/29 03:17:28 linda Exp $
 */
package com.sleepycat.je;

/**
 * Modes that can be specified for control over caching of records in the JE
 * in-memory cache.  When a record is stored or retrieved, the cache mode
 * determines how long the record is subsequently retained in the JE in-memory
 * cache, relative to other records in the cache.
 *
 * <p>When the cache overflows, JE must evict some records from the cache.  By
 * default, JE uses a Least Recently Used (LRU) algorithm for determining which
 * records to evict.  With the LRU algorithm, JE makes a best effort to evict
 * the "coldest" (least recently used or accessed) records and to retain the
 * "hottest" records in the cache for as long as possible.</p>
 *
 * <p>A non-default cache mode may be explicitly specified to override the
 * default behavior of the LRU algorithm.  See {@link #KEEP_HOT} and {@link
 * #UNCHANGED} for more information.  When no cache mode is explicitly
 * specified, the default cache mode is {@link #DEFAULT}.  The default mode
 * causes the normal LRU algorithm to be used.</p>
 *
 * <p>Note that JE makes a best effort to implement an approximation of an LRU
 * algorithm, and the very coldest record is not always evicted from the cache
 * first.  In addition, hotness and coldness are applied to the portion of the
 * in-memory BTree that is accessed to perform the operation, not just to the
 * record itself.</p>
 *
 * <p>The cache mode for cursor operations can be specified by calling {@link
 * Cursor#setCacheMode Cursor.setCacheMode} after opening a {@link Cursor}.
 * The cache mode applies to all operations subsequently performed with that
 * cursor until the cursor is closed or its cache mode is changed.  The cache
 * mode for {@link Database} methods may not be specified and the default cache
 * mode is always used.  To override the default cache mode, you must open a
 * Cursor.</p>
 */
public enum CacheMode {

    /**
     * The record's hotness is changed to "most recently used" by the operation
     * where this cache mode is specified.
     *
     * <p>The record will be colder then other records accessed with a {@code
     * KEEP_HOT} cache mode.  Otherwise, the record will be hotter than
     * other records accessed before it and colder then other records accessed
     * after it.</p>
     *
     * <p>This cache mode is used when the application does not need explicit
     * control over the cache and a standard LRU implementation is
     * sufficient.</p>
     */
    DEFAULT,

    /**
     * The record's hotness or coldness is unchanged by the operation where
     * this cache mode is specified.
     *
     * <p>If the record was present in the cache prior to this operation, then
     * its pre-existing hotness or coldness will not be changed.  If the record
     * was added to the cache by this operation, it will have "maximum
     * coldness" and will therefore be colder than other records.</p>
     *
     * <p>This cache mode is normally used when the application does not intend
     * to access this record again soon.</p>
     */
    UNCHANGED,

    /**
     * The record is assigned "maximum hotness" by the operation where this
     * cache mode is specified.
     *
     * <p>The record will have the same hotness as other records accessed with
     * this cache mode.  Its relative hotness will not be reduced over time as
     * other records are accessed.  It can only become colder over time if it
     * is subsequently accessed with the {@code DEFAULT} cache mode.</p>
     *
     * <p>This cache mode is normally used when the application intends to
     * access this record again soon.</p>
     */
    KEEP_HOT
}
