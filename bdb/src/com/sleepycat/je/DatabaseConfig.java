/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: DatabaseConfig.java,v 1.34.2.1 2007/02/01 14:49:41 cwl Exp $
 */

package com.sleepycat.je;

import java.util.Arrays;
import java.util.Comparator;

import com.sleepycat.je.dbi.DatabaseImpl;

/**
 * Javadoc for this public class is generated
 * via the doc templates in the doc_src directory.
 */
public class DatabaseConfig implements Cloneable {

    /*
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

    /* User defined Btree and duplicate comparison functions, if specified.*/
    private int nodeMax;
    private int nodeMaxDupTree;
    private Comparator btreeComparator = null;
    private Comparator duplicateComparator = null;
    private boolean btreeComparatorByClassName = false;
    private boolean duplicateComparatorByClassName = false;
    private boolean overrideBtreeComparator = false;
    private boolean overrideDupComparator = false;
    private boolean useExistingConfig = false;

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public DatabaseConfig() {
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public void setAllowCreate(boolean allowCreate) {
        this.allowCreate = allowCreate;
    } 

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public boolean getAllowCreate() {
        return allowCreate;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public void setExclusiveCreate(boolean exclusiveCreate) {
        this.exclusiveCreate = exclusiveCreate;
    } 

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public boolean getExclusiveCreate() {
        return exclusiveCreate;
    } 

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public void setSortedDuplicates(boolean duplicatesAllowed) {
        this.duplicatesAllowed = duplicatesAllowed;
    } 

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public boolean getSortedDuplicates() {
        return duplicatesAllowed;
    } 

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public void setTransactional(boolean transactional) {
        this.transactional = transactional;
    } 
    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public boolean getTransactional() {
        return transactional;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    } 

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public boolean getReadOnly() {
        return readOnly;
    } 

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public void setNodeMaxEntries(int nodeMaxEntries) {
	this.nodeMax = nodeMaxEntries;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public void setNodeMaxDupTreeEntries(int nodeMaxDupTreeEntries) {
	this.nodeMaxDupTree = nodeMaxDupTreeEntries;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public int getNodeMaxEntries() {
	return nodeMax;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public int getNodeMaxDupTreeEntries() {
	return nodeMaxDupTree;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public void setBtreeComparator(Comparator btreeComparator) {
        /* Note: comparator may be null */
        this.btreeComparator = validateComparator(btreeComparator, "Btree");
        this.btreeComparatorByClassName = false;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public void setBtreeComparator(Class btreeComparator) {
        /* Note: comparator may be null */
        this.btreeComparator = validateComparator(btreeComparator, "Btree");
        this.btreeComparatorByClassName = true;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public Comparator getBtreeComparator() {
        return btreeComparator;
    }

    /**
     * @return whether Comparator is set by class name, not by serializable
     * Comparator object.
     */
    public boolean getBtreeComparatorByClassName() {
        return btreeComparatorByClassName;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public void setOverrideBtreeComparator(boolean override) {
        overrideBtreeComparator = override;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public boolean getOverrideBtreeComparator() {
        return overrideBtreeComparator;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public void setDuplicateComparator(Comparator duplicateComparator) {
        /* Note: comparator may be null */
        this.duplicateComparator =
	    validateComparator(duplicateComparator, "Duplicate");
        this.duplicateComparatorByClassName = false;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public void setDuplicateComparator(Class duplicateComparator) {
        /* Note: comparator may be null */
        this.duplicateComparator =
	    validateComparator(duplicateComparator, "Duplicate");
        this.duplicateComparatorByClassName = true;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public Comparator getDuplicateComparator() {
        return duplicateComparator;
    }

    /**
     * @return whether Comparator is set by class name, not by serializable
     * Comparator object.
     */
    public boolean getDuplicateComparatorByClassName() {
        return duplicateComparatorByClassName;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public void setOverrideDuplicateComparator(boolean override) {
        overrideDupComparator = override;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public boolean getOverrideDuplicateComparator() {
        return overrideDupComparator;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public void setDeferredWrite(boolean deferredWrite) {
        this.deferredWrite = deferredWrite;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public boolean getDeferredWrite() {
        return deferredWrite;
    }

    /**
     * Used to set the comparator when filling in a configuration from an
     * existing database.
     */
    void setBtreeComparatorInternal(Comparator comparator,
                                    boolean byClassName) {
        btreeComparator = comparator;
        btreeComparatorByClassName = byClassName;
    }

    /**
     * Used to set the comparator when filling in a configuration from an
     * existing database.
     */
    void setDuplicateComparatorInternal(Comparator comparator,
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

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
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
	StringBuffer ret = new StringBuffer
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
     * Check that this comparator can be serialized by JE.
     */
    private Comparator validateComparator(Comparator comparator, String type) 
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
     * Check that this comparator class can be instantiated by JE.
     */
    private Comparator validateComparator(Class comparator, String type) 
        throws IllegalArgumentException {

	if (comparator == null) {
	    return null;
	}

        if (!Comparator.class.isAssignableFrom(comparator)) {
            throw new IllegalArgumentException
                (comparator.getName() +
                 " is is not valid as a " + type +
                 " comparator because it does not " +
                 " implement java.util.Comparator.");
        }

        try {
	    return DatabaseImpl.instantiateComparator(comparator, type);
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
     * Check that this database configuration is valid for a new, non-existant
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
}
