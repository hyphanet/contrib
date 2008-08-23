/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: DatabaseConfig.java,v 1.48 2008/06/10 00:21:30 cwl Exp $
 */

package com.sleepycat.je;

import java.util.Arrays;
import java.util.Comparator;

import com.sleepycat.je.dbi.DatabaseImpl;

/**
 * Specifies the attributes of a database.
 */
public class DatabaseConfig implements Cloneable {

    /**
     * An instance created using the default constructor is initialized with
     * the system's default settings.
     */
    public static final DatabaseConfig DEFAULT = new DatabaseConfig();

    private boolean allowCreate = false;
    private boolean exclusiveCreate = false;
    private boolean transactional = false;
    private boolean readOnly = false;
    private boolean duplicatesAllowed = false;
    private boolean deferredWrite = false;
    private boolean temporary = false;
    private boolean keyPrefixingEnabled = false;

    /*
     * An internal attibute indicating that the database is replicated in an
     * HA system. Not yet publically settable.
     */
    private boolean replicated = true;

    /* User defined Btree and duplicate comparison functions, if specified.*/
    private int nodeMax;
    private int nodeMaxDupTree;
    private Comparator<byte[]> btreeComparator = null;
    private Comparator<byte[]> duplicateComparator = null;
    private boolean btreeComparatorByClassName = false;
    private boolean duplicateComparatorByClassName = false;
    private boolean overrideBtreeComparator = false;
    private boolean overrideDupComparator = false;
    private boolean useExistingConfig = false;

    /**
     * An instance created using the default constructor is initialized with
     * the system's default settings.
     */
    public DatabaseConfig() {
    }

    /**
     * Configures the {@link com.sleepycat.je.Environment#openDatabase
     * Environment.openDatabase} method to create the database if it does not
     * already exist.
     *
     * @param allowCreate If true, configure the {@link
     * com.sleepycat.je.Environment#openDatabase Environment.openDatabase}
     * method to create the database if it does not already exist.
     */
    public void setAllowCreate(boolean allowCreate) {
        this.allowCreate = allowCreate;
    }

    /**
     * Returns true if the {@link com.sleepycat.je.Environment#openDatabase
     * Environment.openDatabase} method is configured to create the database
     * if it does not already exist.
     *
     * <p>This method may be called at any time during the life of the
     * application.</p>
     *
     * @return True if the {@link com.sleepycat.je.Environment#openDatabase
     * Environment.openDatabase} method is configured to create the database
     * if it does not already exist.
     */
    public boolean getAllowCreate() {
        return allowCreate;
    }

    /**
     * Configure the {@link com.sleepycat.je.Environment#openDatabase
     * Environment.openDatabase} method to fail if the database already exists.
     *
     * <p>The exclusiveCreate mode is only meaningful if specified with the
     * allowCreate mode.</p>
     *
     * @param exclusiveCreate If true, configure the {@link
     * com.sleepycat.je.Environment#openDatabase Environment.openDatabase}
     * method to fail if the database already exists.
     */
    public void setExclusiveCreate(boolean exclusiveCreate) {
        this.exclusiveCreate = exclusiveCreate;
    }

    /**
     * Returns true if the {@link com.sleepycat.je.Environment#openDatabase
     * Environment.openDatabase} method is configured to fail if the database
     * already exists.
     *
     * <p>This method may be called at any time during the life of the
     * application.</p>
     *
     * @return True if the {@link com.sleepycat.je.Environment#openDatabase
     * Environment.openDatabase} method is configured to fail if the database
     * already exists.
     */
    public boolean getExclusiveCreate() {
        return exclusiveCreate;
    }

    /**
     * Configure the database to support sorted, duplicate data items.
     *
     * <p>Insertion when the key of the key/data pair being inserted already
     * exists in the database will be successful.  The ordering of duplicates
     * in the database is determined by the duplicate comparison function.</p>
     *
     * <p>If the application does not specify a duplicate data item comparison
     * function, a default lexical comparison will be used.</p>
     *
     * <p>If a primary database is to be associated with one or more secondary
     * databases, it may not be configured for duplicates.</p>
     *
     * <p>Calling this method affects the database, including all threads of
     * control accessing the database.</p>
     *
     * <p>If the database already exists when the database is opened, any
     * database configuration specified by this method must be the same as the
     * existing database or an error will be returned.</p>
     *
     * @param duplicatesAllowed If true, configure the database to support
     * duplicate data items. A value of false is illegal to this method, that
     * is, once set, the configuration cannot be cleared.
     */
    public void setSortedDuplicates(boolean duplicatesAllowed) {
        this.duplicatesAllowed = duplicatesAllowed;
    }

    /**
     * Returns true if the database is configured to support sorted duplicate
     * data items.
     *
     * <p>This method may be called at any time during the life of the
     * application.</p>
     *
     * @return True if the database is configured to support sorted duplicate
     * data items.
     */
    public boolean getSortedDuplicates() {
        return duplicatesAllowed;
    }

    /**
     * Returns the key prefixing configuration.
     *
     * @return true if key prefixing has been enabled in this database.
     */
    public boolean getKeyPrefixing() {
        return keyPrefixingEnabled;
    }

    /**
     * Configure the database to support key prefixing. Key prefixing causes
     * the representation of keys in the b-tree internal nodes to be split
     * between the common prefix of all keys and the suffixes.  Using this
     * may result in a more space-efficient representation in both the
     * in-memory and on-disk formats, but at some possible performance cost.
     *
     * @param keyPrefixingEnabled If true, enables keyPrefixing for the
     * database.
     */
    public void setKeyPrefixing(boolean keyPrefixingEnabled) {
        this.keyPrefixingEnabled = keyPrefixingEnabled;
    }

    /**
     * Encloses the database open within a transaction.
     *
     * <p>If the call succeeds, the open operation will be recoverable.  If the
     * call fails, no database will have been created.</p>
     *
     * <p>All future operations on this database, which are not explicitly
     * enclosed in a transaction by the application, will be enclosed in in a
     * transaction within the library.</p>
     *
     * @param transactional If true, enclose the database open within a
     * transaction.
     */
    public void setTransactional(boolean transactional) {
        this.transactional = transactional;
    }
    /**
     * Returns true if the database open is enclosed within a transaction.
     *
     * <p>This method may be called at any time during the life of the
     * application.</p>
     *
     * @return True if the database open is enclosed within a transaction.
     */
    public boolean getTransactional() {
        return transactional;
    }

    /**
     * Configures the database in read-only mode.
     *
     * <p>Any attempt to modify items in the database will fail, regardless of
     * the actual permissions of any underlying files.</p>
     *
     * @param readOnly If true, configure the database in read-only mode.
     */
    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    /**
     * Returns true if the database is configured in read-only mode.
     *
     * <p>This method may be called at any time during the life of the
     * application.</p>
     *
     * @return True if the database is configured in read-only mode.
     */
    public boolean getReadOnly() {
        return readOnly;
    }

    /**
     * Configures the {@link com.sleepycat.je.Environment#openDatabase
     * Environment.openDatabase} method to have a B+Tree fanout of
     * nodeMaxEntries.
     *
     * <p>The nodeMaxEntries parameter is only meaningful if specified with the
     * allowCreate mode.</p>
     *
     * @param nodeMaxEntries The maximum children per B+Tree node.
     */
    public void setNodeMaxEntries(int nodeMaxEntries) {
        this.nodeMax = nodeMaxEntries;
    }

    /**
     * Configures the {@link com.sleepycat.je.Environment#openDatabase
     * Environment.openDatabase} method to have a B+Tree duplicate tree fanout
     * of nodeMaxDupTreeEntries.
     *
     * <p>The nodeMaxDupTreeEntries parameter is only meaningful if specified
     * with the allowCreate mode.</p>
     *
     * @param nodeMaxDupTreeEntries The maximum children per duplicate B+Tree
     * node.
     */
    public void setNodeMaxDupTreeEntries(int nodeMaxDupTreeEntries) {
        this.nodeMaxDupTree = nodeMaxDupTreeEntries;
    }

    /**
     * Returns the maximum number of children a B+Tree node can have.
     *
     * <p>This method may be called at any time during the life of the
     * application.</p>
     *
     * @return The maximum number of children a B+Tree node can have.
     */
    public int getNodeMaxEntries() {
        return nodeMax;
    }

    /**
     * Returns the maximum number of children a B+Tree duplicate tree node can
     * have.
     *
     * <p>This method may be called at any time during the life of the
     * application.</p>
     *
     * @return The maximum number of children a B+Tree duplicate tree node can
     * have.
     */
    public int getNodeMaxDupTreeEntries() {
        return nodeMaxDupTree;
    }

    /**
     * By default, a byte by byte lexicographic comparison is used for btree
     * keys. To customize the comparison, supply a different Comparator.
     *
     * <p>Note that there are two ways to set the comparator: by specifying the
     * class or by specifying a serializable object.  This method is used to
     * specify a serializable object.  The comparator class must implement
     * java.util.Comparator and must be serializable.  JE will serialize the
     * Comparator and deserialize it when subsequently opening the
     * database.</p>
     *
     * <p>The Comparator.compare() method is passed the byte arrays that are
     * stored in the database. If you know how your data is organized in the
     * byte array, then you can write a comparison routine that directly
     * examines the contents of the arrays. Otherwise, you have to reconstruct
     * your original objects, and then perform the comparison.  See the <a
     * href="{@docRoot}/../GettingStartedGuide/comparator.html"
     * target="_top">Getting Started Guide</a> for examples.</p>
     *
     * <p><em>WARNING:</em> There are several special considerations that must
     * be taken into account when implementing a comparator.<p>
     * <ul>
     *   <li>Comparator instances are shared by multiple threads and comparator
     *   methods are called without any special synchronization. Therefore,
     *   comparators must be thread safe.  In general no shared state should be
     *   used and any caching of computed values must be done with proper
     *   synchronization.</li>
     *
     *   <li>Because records are stored in the order determined by the
     *   Comparator, the Comparator's behavior must not change over time and
     *   therefore should not be dependent on any state that may change over
     *   time.  In addition, although it is possible to change the comparator
     *   for an existing database, care must be taken that the new comparator
     *   provides compatible results with the previous comparator, or database
     *   corruption will occur.</li>
     *
     *   <li>JE uses comparators internally in a wide variety of circumstances,
     *   so custom comparators must be sure to return valid values for any two
     *   arbitrary keys.  The user must not make any assumptions about the
     *   range of key values that might be compared. For example, it's possible
     *   for the comparator may be used against previously deleted values.</li>
     * </ul>
     *
     * <p>A special type of comparator is a <em>partial comparator</em>, which
     * compares a proper subset (not all bytes) of the key.  A partial
     * comparator allows uniquely identifying a record by a partial key value.
     * For example, the key could contain multiple fields but could uniquely
     * identify the record with a single field.  The partial comparator could
     * then compare only the single identifying field.  A query ({@link
     * Cursor#getSearchKey Cursor.getSearchKey}, for example) could then be
     * performed by passing a partial key that contains only the identifying
     * field.</p>
     *
     * <p>A partial comparator has limited value when used as a Btree
     * comparator. Instead of using a partial comparator, the non-identifying
     * fields of the key could be placed in the data portion of the key/data
     * pair.  This makes the key smaller, which normally provides better
     * performance.  A partial comparator is much more useful when used as a
     * duplicate comparator (see {@link #setDuplicateComparator
     * setDuplicateComparator}).</p>
     *
     * <p>However, if you do use a partial comparator as a Btree comparator, be
     * aware that you may not configure the database for duplicates (true may
     * not be passed to {@link #setSortedDuplicates setSortedDuplicates}).  In
     * a duplicate set, each key must have the same (identical bytes) key.  The
     * internal structure of JE's Btree cannot support duplicates with
     * non-identical keys, and cannot support specifying non-identical keys for
     * addressing the records in a duplicate set.</p>
     *
     * The comparator for an existing database will not be overridden unless
     * setOverrideBtreeComparator() is set to true.
     */
    public void setBtreeComparator(Comparator<byte[]> btreeComparator) {
        /* Note: comparator may be null */
        this.btreeComparator = validateComparator(btreeComparator, "Btree");
        this.btreeComparatorByClassName = false;
    }

    /**
     * By default, a byte by byte lexicographic comparison is used for btree
     * keys. To customize the comparison, supply a different Comparator.
     *
     * <p>Note that there are two ways to set the comparator: by specifying the
     * class or by specifying a serializable object.  This method is used to
     * specify a Comparator class.  The comparator class must implement
     * java.util.Comparator and must have a public zero-parameter constructor.
     * JE will store the class name and instantiate the Comparator by class
     * name (using <code>Class.forName</code> and <code>newInstance</code>)
     * when subsequently opening the database.  Because the Comparator is
     * instantiated using its default constructor, it should not be dependent
     * on other constructor parameters.</p>
     *
     * <p>The Comparator.compare() method is passed the byte arrays that are
     * stored in the database. If you know how your data is organized in the
     * byte array, then you can write a comparison routine that directly
     * examines the contents of the arrays. Otherwise, you have to reconstruct
     * your original objects, and then perform the comparison.  See the <a
     * href="{@docRoot}/../GettingStartedGuide/comparator.html"
     * target="_top">Getting Started Guide</a> for examples.</p>
     *
     * <p><em>WARNING:</em> There are several special considerations that must
     * be taken into account when implementing a comparator.<p>
     * <ul>
     *   <li>Comparator instances are shared by multiple threads and comparator
     *   methods are called without any special synchronization. Therefore,
     *   comparators must be thread safe.  In general no shared state should be
     *   used and any caching of computed values must be done with proper
     *   synchronization.</li>
     *
     *   <li>Because records are stored in the order determined by the
     *   Comparator, the Comparator's behavior must not change over time and
     *   therefore should not be dependent on any state that may change over
     *   time.  In addition, although it is possible to change the comparator
     *   for an existing database, care must be taken that the new comparator
     *   provides compatible results with the previous comparator, or database
     *   corruption will occur.</li>
     *
     *   <li>JE uses comparators internally in a wide variety of circumstances,
     *   so custom comparators must be sure to return valid values for any two
     *   arbitrary keys.  The user must not make any assumptions about the
     *   range of key values that might be compared. For example, it's possible
     *   for the comparator may be used against previously deleted values.</li>
     * </ul>
     *
     * <p>A special type of comparator is a <em>partial comparator</em>, which
     * compares a proper subset (not all bytes) of the key.  A partial
     * comparator allows uniquely identifying a record by a partial key value.
     * For example, the key could contain multiple fields but could uniquely
     * identify the record with a single field.  The partial comparator could
     * then compare only the single identifying field.  A query ({@link
     * Cursor#getSearchKey Cursor.getSearchKey}, for example) could then be
     * performed by passing a partial key that contains only the identifying
     * field.</p>
     *
     * <p>A partial comparator has limited value when used as a Btree
     * comparator. Instead of using a partial comparator, the non-identifying
     * fields of the key could be placed in the data portion of the key/data
     * pair.  This makes the key smaller, which normally provides better
     * performance.  A partial comparator is much more useful when used as a
     * duplicate comparator (see {@link #setDuplicateComparator
     * setDuplicateComparator}).</p>
     *
     * <p>However, if you do use a partial comparator as a Btree comparator,
     * please be aware that you may not configure the database for duplicates
     * (true may not be passed to {@link #setSortedDuplicates
     * setSortedDuplicates}).  In a duplicate set, each key must have the same
     * (identical bytes) key.  The internal structure of JE's Btree cannot
     * support duplicates with non-identical keys, and cannot support
     * specifying non-identical keys for addressing the records in a duplicate
     * set.</p>
     *
     * The comparator for an existing database will not be overridden unless
     * setOverrideBtreeComparator() is set to true.
     */
    public void setBtreeComparator(Class<? extends Comparator<byte[]>> 
                                   btreeComparatorClass) {

        /* Note: comparator may be null */
        this.btreeComparator = validateComparator(btreeComparatorClass, 
                                                  "Btree");
        this.btreeComparatorByClassName = true;
    }

    /**
     * Returns the Comparator used for key comparison on this database.
     */
    public Comparator<byte[]> getBtreeComparator() {
        return btreeComparator;
    }

    /**
     * Returns true if the btree comparator is set by class name, not by
     * serializable Comparator object
     * @return true if the comparator is set by class name, not by serializable
     * Comparator object.
     */
    public boolean getBtreeComparatorByClassName() {
        return btreeComparatorByClassName;
    }

    /**
     * Sets to true if the database exists and the btree comparator specified
     * in this configuration object should override the current comparator.
     *
     * @param override Set to true to override the existing comparator.
     */
    public void setOverrideBtreeComparator(boolean override) {
        overrideBtreeComparator = override;
    }

    /**
     * Returns the override setting for the btree comparator.
     */
    public boolean getOverrideBtreeComparator() {
        return overrideBtreeComparator;
    }

    /**
     * By default, a byte by byte lexicographic comparison is used for
     * duplicate data items in a duplicate set.  To customize the comparison,
     * supply a different Comparator.
     *
     * <p>Note that there are two ways to set the comparator: by specifying the
     * class or by specifying a serializable object.  This method is used to
     * specify a serializable object.  The comparator class must implement
     * java.util.Comparator and must be serializable.  JE will serialize the
     * Comparator and deserialize it when subsequently opening the
     * database.</p>
     *
     * <p>The Comparator.compare() method is passed the byte arrays that are
     * stored in the database. If you know how your data is organized in the
     * byte array, then you can write a comparison routine that directly
     * examines the contents of the arrays. Otherwise, you have to reconstruct
     * your original objects, and then perform the comparison.  See the <a
     * href="{@docRoot}/../GettingStartedGuide/comparator.html"
     * target="_top">Getting Started Guide</a> for examples.</p>
     *
     * <p><em>WARNING:</em> There are several special considerations that must
     * be taken into account when implementing a comparator.<p>
     * <ul>
     *   <li>Comparator instances are shared by multiple threads and comparator
     *   methods are called without any special synchronization. Therefore,
     *   comparators must be thread safe.  In general no shared state should be
     *   used and any caching of computed values must be done with proper
     *   synchronization.</li>
     *
     *   <li>Because records are stored in the order determined by the
     *   Comparator, the Comparator's behavior must not change over time and
     *   therefore should not be dependent on any state that may change over
     *   time.  In addition, although it is possible to change the comparator
     *   for an existing database, care must be taken that the new comparator
     *   provides compatible results with the previous comparator, or database
     *   corruption will occur.</li>
     *
     *   <li>JE uses comparators internally in a wide variety of circumstances,
     *   so custom comparators must be sure to return valid values for any two
     *   arbitrary keys.  The user must not make any assumptions about the
     *   range of key values that might be compared. For example, it's possible
     *   for the comparator may be used against previously deleted values.</li>
     * </ul>
     *
     * <p>A special type of comparator is a <em>partial comparator</em>, which
     * is a comparator that compares a proper subset (not all bytes) of the
     * data.  A partial comparator allows uniquely identifying a record within
     * a duplicate set by a partial data value.  For example, the data could
     * contain multiple fields but could uniquely identify the record with a
     * single field.  The partial comparator could then compare only the single
     * identifying field.  A query ({@link Cursor#getSearchBoth
     * Cursor.getSearchBoth}, for example) could then be performed by passing a
     * partial data value that contains only the identifying field.</p>
     *
     * <p>When using a partial comparator, it is possible to update the data
     * for a duplicate record, as long as only the non-identifying fields in
     * the data are changed.  See {@link Cursor#putCurrent Cursor.putCurrent}
     * for more information.</p>
     *
     * The comparator for an existing database will not be overridden unless
     * setOverrideDuplicateComparator() is set to true.
     */
    public void 
        setDuplicateComparator(Comparator<byte[]> duplicateComparator) {

        /* Note: comparator may be null */
        this.duplicateComparator =
            validateComparator(duplicateComparator, "Duplicate");
        this.duplicateComparatorByClassName = false;
    }

    /**
     * By default, a byte by byte lexicographic comparison is used for
     * duplicate data items in a duplicate set.  To customize the comparison,
     * supply a different Comparator.
     *
     * <p>Note that there are two ways to set the comparator: by specifying the
     * class or by specifying a serializable object.  This method is used to
     * specify a Comparator class.  The comparator class must implement
     * java.util.Comparator and must have a public zero-parameter constructor.
     * JE will store the class name and instantiate the Comparator by class
     * name (using <code>Class.forName</code> and <code>newInstance</code>)
     * when subsequently opening the database.  Because the Comparator is
     * instantiated using its default constructor, it should not be dependent
     * on other constructor parameters.</p>
     *
     * <p>The Comparator.compare() method is passed the byte arrays that are
     * stored in the database. If you know how your data is organized in the
     * byte array, then you can write a comparison routine that directly
     * examines the contents of the arrays. Otherwise, you have to reconstruct
     * your original objects, and then perform the comparison.  See the <a
     * href="{@docRoot}/../GettingStartedGuide/comparator.html"
     * target="_top">Getting Started Guide</a> for examples.</p>
     *
     * <p><em>WARNING:</em> There are several special considerations that must
     * be taken into account when implementing a comparator.<p>
     * <ul>
     *   <li>Comparator instances are shared by multiple threads and comparator
     *   methods are called without any special synchronization. Therefore,
     *   comparators must be thread safe.  In general no shared state should be
     *   used and any caching of computed values must be done with proper
     *   synchronization.</li>
     *
     *   <li>Because records are stored in the order determined by the
     *   Comparator, the Comparator's behavior must not change over time and
     *   therefore should not be dependent on any state that may change over
     *   time.  In addition, although it is possible to change the comparator
     *   for an existing database, care must be taken that the new comparator
     *   provides compatible results with the previous comparator, or database
     *   corruption will occur.</li>
     *
     *   <li>JE uses comparators internally in a wide variety of circumstances,
     *   so custom comparators must be sure to return valid values for any two
     *   arbitrary keys.  The user must not make any assumptions about the
     *   range of key values that might be compared. For example, it's possible
     *   for the comparator may be used against previously deleted values.</li>
     * </ul>
     *
     * <p>A special type of comparator is a <em>partial comparator</em>, which
     * is a comparator that compares a proper subset (not all bytes) of the
     * data.  A partial comparator allows uniquely identifying a record within
     * a duplicate set by a partial data value.  For example, the data could
     * contain multiple fields but could uniquely identify the record with a
     * single field.  The partial comparator could then compare only the single
     * identifying field.  A query ({@link Cursor#getSearchBoth
     * Cursor.getSearchBoth}, for example) could then be performed by passing a
     * partial data value that contains only the identifying field.</p>
     *
     * <p>When using a partial comparator, it is possible to update the data
     * for a duplicate record, as long as only the non-identifying fields in
     * the data are changed.  See {@link Cursor#putCurrent Cursor.putCurrent}
     * for more information.</p>
     *
     * The comparator for an existing database will not be overridden unless
     * setOverrideDuplicateComparator() is set to true.
     */
    public void setDuplicateComparator(Class<? extends Comparator<byte[]>> 
                                       duplicateComparatorClass) {

        /* Note: comparator may be null */
        this.duplicateComparator = validateComparator(duplicateComparatorClass,
                                                      "Duplicate");
        this.duplicateComparatorByClassName = true;
    }

    /**
     * Returns the Comparator used for duplicate record comparison on this
     * database.
     */
    public Comparator<byte[]> getDuplicateComparator() {
        return duplicateComparator;
    }

    /**
     * Returns true if the duplicate comparator is set by class name, not by
     * serializable Comparator object.
     *
     * @return true if the duplicate comparator is set by class name, not by
     * serializable Comparator object.
     */
    public boolean getDuplicateComparatorByClassName() {
        return duplicateComparatorByClassName;
    }

    /**
     * Sets to true if the database exists and the duplicate comparator
     * specified in this configuration object should override the current
     * comparator.
     *
     * @param override Set to true to override the existing comparator.
     */
    public void setOverrideDuplicateComparator(boolean override) {
        overrideDupComparator = override;
    }

    /**
     * Returns the override setting for the duplicate comparator.
     */
    public boolean getOverrideDuplicateComparator() {
        return overrideDupComparator;
    }

    /**
     * Sets the temporary database option.
     *
     * <p> Temporary databases operate internally in deferred-write mode to
     * provide reduced disk I/O and increased concurrency.  But unlike an
     * ordinary deferred-write database, the information in a temporary
     * database is not durable or persistent.
     *
     * <p> A temporary database is not flushed to disk when the database is
     * closed or when a checkpoint is performed, and the Database.sync method
     * may not be called.  When all handles for a temporary database are
     * closed, the database is automatically removed.  If a crash occurs before
     * closing a temporary database, the database will be automatically removed
     * when the environment is re-opened.
     *
     * <p> Note that although temporary databases can page to disk if the cache
     * is not large enough to hold the databases, they are much more efficient
     * if the database remains in memory. See the JE FAQ on the Oracle
     * Technology Network site for information on how to estimate the cache
     * size needed by a given database.
     *
     * <p>
     * See the Getting Started Guide, Database chapter for a full description
     * of temporary databases.
     * <p>
     * @param temporary if true, the database will be opened as a temporary
     * database.
     */
    public void setTemporary(boolean temporary) {
        this.temporary = temporary;
    }

    /**
     * Returns the temporary database option.
     * @return boolean if true, the database is temporary.
     */
    public boolean getTemporary() {
        return temporary;
    }

    /**
     * Sets the deferred-write option.
     *
     * <p> Deferred-write databases have reduced disk I/O and improved
     * concurrency.  Disk I/O is reduced when data records are frequently
     * modified or deleted.  The information in a deferred-write database is
     * not guaranteed to be durable or persistent until Database.close() or
     * Database.sync() is called, or a checkpoint is performed.
     *
     * <p> After a deferred-write database is closed it may be re-opened as an
     * ordinary transactional or non-transactional database.  For example, this
     * can be used to initially load a large data set in deferred-write mode
     * and then switch to transactional mode for subsequent operations.
     *
     * <p> Note that although deferred-write databases can page to disk if the
     * cache is not large enough to hold the databases, they are much more
     * efficient if the database remains in memory. See the JE FAQ on the
     * Oracle Technology Network site for information on how to estimate the
     * cache size needed by a given database.
     *
     * <p> See the Getting Started Guide, Database chapter for a full
     * description of deferred-write databases.
     *
     * <p>
     * @param deferredWrite if true, the database will be opened as a
     * deferred-write database.
     */
    public void setDeferredWrite(boolean deferredWrite) {
        this.deferredWrite = deferredWrite;
    }

    /**
     * Returns the deferred-write option.
     *
     * @return boolean if true, deferred-write is enabled.
     */
    public boolean getDeferredWrite() {
        return deferredWrite;
    }

    /**
     * Used to set the comparator when filling in a configuration from an
     * existing database.
     */
    void setBtreeComparatorInternal(Comparator<byte[]> comparator,
                                    boolean byClassName) {
        btreeComparator = comparator;
        btreeComparatorByClassName = byClassName;
    }

    /**
     * Used to set the comparator when filling in a configuration from an
     * existing database.
     */
    void setDuplicateComparatorInternal(Comparator<byte[]> comparator,
                                        boolean byClassName) {
        duplicateComparator = comparator;
        duplicateComparatorByClassName = byClassName;
    }

    /**
     * For utilities, to avoid having to know the configuration of a database.
     */
    void setUseExistingConfig(boolean useExistingConfig) {
        this.useExistingConfig = useExistingConfig;
    }

    /**
     * For utilities, to avoid having to know the configuration of a database.
     */
    boolean getUseExistingConfig() {
        return useExistingConfig;
    }

    /** Not for public use yet. */
    void setReplicated(boolean replicated) {
        this.replicated = replicated;
    }

    /** Not for public use yet. */
    boolean getReplicated() {
        return replicated;
    }

    /**
     * Returns a copy of this configuration object.
     */
    public DatabaseConfig cloneConfig() {
        try {
            return (DatabaseConfig) super.clone();
        } catch (CloneNotSupportedException willNeverOccur) {
            return null;
        }
    }

    /*
     * For JCA Database handle caching.
     */
    void validate(DatabaseConfig config)
        throws DatabaseException {

        if (config == null) {
            config = DatabaseConfig.DEFAULT;
        }

        boolean txnMatch = (config.transactional == transactional);
        boolean roMatch = (config.readOnly == readOnly);
        boolean sdMatch = (config.duplicatesAllowed == duplicatesAllowed);
        boolean dwMatch = (config.getDeferredWrite() == deferredWrite);
        boolean btCmpMatch = true;
        if (config.overrideBtreeComparator) {
            if (btreeComparator == null) {
                btCmpMatch = (config.btreeComparator == null);
            } else if (config.btreeComparatorByClassName !=
                       btreeComparatorByClassName) {
                btCmpMatch = false;
            } else if (btreeComparatorByClassName) {
                btCmpMatch = btreeComparator.getClass() ==
                             config.btreeComparator.getClass();
            } else {
                btCmpMatch = Arrays.equals
                    (DatabaseImpl.objectToBytes
                        (btreeComparator, "Btree"),
                     DatabaseImpl.objectToBytes
                        (config.btreeComparator, "Btree"));
            }
        }
        boolean dtCmpMatch = true;
        if (config.overrideDupComparator) {
            if (duplicateComparator == null) {
                dtCmpMatch = (config.duplicateComparator == null);
            } else if (config.duplicateComparatorByClassName !=
                       duplicateComparatorByClassName) {
                dtCmpMatch = false;
            } else if (duplicateComparatorByClassName) {
                dtCmpMatch = duplicateComparator.getClass() ==
                             config.duplicateComparator.getClass();
            } else {
                dtCmpMatch = Arrays.equals
                    (DatabaseImpl.objectToBytes
                        (duplicateComparator, "Duplicate"),
                     DatabaseImpl.objectToBytes
                        (config.duplicateComparator, "Duplicate"));
            }
        }

        if (txnMatch &&
            roMatch &&
            sdMatch &&
            dwMatch &&
            btCmpMatch &&
            dtCmpMatch) {
            return;
        } else {
            String message =
                genDatabaseConfigMismatchMessage
                (config, txnMatch, roMatch, sdMatch, dwMatch,
                 btCmpMatch, dtCmpMatch);
            throw new DatabaseException(message);
        }
    }

    private String genDatabaseConfigMismatchMessage(DatabaseConfig config,
                                                    boolean txnMatch,
                                                    boolean roMatch,
                                                    boolean sdMatch,
                                                    boolean dwMatch,
                                                    boolean btCmpMatch,
                                                    boolean dtCmpMatch) {
        StringBuilder ret = new StringBuilder
            ("The following DatabaseConfig parameters for the\n" +
             "cached Database do not match the parameters for the\n" +
             "requested Database:\n");
        if (!txnMatch) {
            ret.append(" Transactional\n");
        }
        
        if (!roMatch) {
            ret.append(" Read-Only\n");
        }
        
        if (!sdMatch) {
            ret.append(" Sorted Duplicates\n");
        }
        
        if (!dwMatch) {
            ret.append(" Deferred Write");
        }

        if (!btCmpMatch) {
            ret.append(" Btree Comparator\n");
        }
        
        if (!dtCmpMatch) {
            ret.append(" Duplicate Comparator\n");
        }

        return ret.toString();
    }

    /**
     * Checks that this comparator can be serialized by JE.
     */
    private Comparator<byte[]> 
        validateComparator(Comparator<byte[]> comparator, String type)

        throws IllegalArgumentException {

        if (comparator == null) {
            return null;
        }

        try {
            return DatabaseImpl.instantiateComparator(comparator, type);
        } catch (DatabaseException e) {
            throw new IllegalArgumentException
                (type +
                 " comparator is not valid: " +
                 e.getMessage() +
                 "\nThe comparator object must be serializable.");
        }
    }

    /**
     * Checks that this comparator class can be instantiated by JE.
     */
    private Comparator<byte[]> 
        validateComparator(Class<? extends Comparator<byte[]>> comparatorClass,
                           String type)
        throws IllegalArgumentException {

        if (comparatorClass == null) {
            return null;
        }

        if (!Comparator.class.isAssignableFrom(comparatorClass)) {
            throw new IllegalArgumentException
                (comparatorClass.getName() +
                 " is is not valid as a " + type +
                 " comparator because it does not " +
                 " implement java.util.Comparator.");
        }

        try {
            return DatabaseImpl.instantiateComparator(comparatorClass, type);
        } catch (DatabaseException e) {
            throw new IllegalArgumentException
                (type +
                 " comparator is not valid: " +
                 e.getMessage() +
                 "\nPerhaps you have not implemented a zero-parameter " +
                 "constructor for the comparator or the comparator class " +
                 "cannot be found.");
        }
    }

    /**
     * Checks that this database configuration is valid for a new, non-existant
     * database.
     */
    void validateForNewDb()
        throws DatabaseException {

        if (readOnly) {
            throw new DatabaseException
                ("DatabaseConfig.setReadOnly() must be set to false " +
                 "when creating a Database");
        }

        if (transactional && deferredWrite) {
            throw new DatabaseException("deferredWrite mode is not yet " +
                                        "supported for transactional " +
                                        "databases");
        }
    }

    /**
     * For unit tests, checks that the database configuration attributes that
     * are saved persistently are equal.
     */
    boolean persistentEquals(DatabaseConfig other) {
        if (duplicatesAllowed != other.duplicatesAllowed)
            return false;

        if (temporary != other.temporary)
            return false;

        if (replicated != other.replicated)
            return false;

        if (nodeMax != other.nodeMax)
            return false;

        if (nodeMaxDupTree != other.nodeMaxDupTree)
            return false;

        if (((btreeComparator == null) && (other.btreeComparator != null)) ||
            ((btreeComparator != null) && (other.btreeComparator == null))) {
            return false;
        }

        if (btreeComparator != null) {
            if (btreeComparator.getClass() !=
                other.btreeComparator.getClass())
            return false;
        }

        if (((duplicateComparator == null) &&
             (other.duplicateComparator != null)) ||
            ((duplicateComparator != null) &&
             (other.duplicateComparator == null))) {
            return false;
        }

        if ((duplicateComparator != null)) {
            if (duplicateComparator.getClass() !=
                other.duplicateComparator.getClass())
                return false;
        }

        return true;
    }

    /**
     * Returns the values for each configuration attribute.
     *
     * @return the values for each configuration attribute.
     */
    @Override
    public String toString() {
        return "allowCreate=" + allowCreate +
            "\nexclusiveCreate=" + exclusiveCreate +
            "\ntransactional=" + transactional +
            "\nreadOnly=" + readOnly +
            "\nduplicatesAllowed=" + duplicatesAllowed +
            "\ndeferredWrite=" + deferredWrite +
            "\ntemporary=" + temporary +
            "\nkeyPrefixingEnabled=" + keyPrefixingEnabled +
            "\n";
    }
}
