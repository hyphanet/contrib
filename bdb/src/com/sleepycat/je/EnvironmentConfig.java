/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: EnvironmentConfig.java,v 1.35.2.1 2007/02/01 14:49:41 cwl Exp $
 */

package com.sleepycat.je;

import java.util.Properties;

import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.DbConfigManager;

/**
 * Javadoc for this public class is generated
 * via the doc templates in the doc_src directory.
 */
public class EnvironmentConfig extends EnvironmentMutableConfig {
    /*
     * For internal use, to allow null as a valid value for
     * the config parameter.
     */
    public static final EnvironmentConfig DEFAULT = new EnvironmentConfig();

    /**
     * For unit testing, to prevent creating the utilization profile DB.
     */
    private boolean createUP = true;

    /**
     * For unit testing, to prevent writing utilization data during checkpoint.
     */
    private boolean checkpointUP = true;

    private boolean allowCreate = false;

    /**
     * For unit testing, to set readCommitted as the default.
     */
    private boolean txnReadCommitted = false;

    private ExceptionListener exceptionListener = null;

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public EnvironmentConfig() {
        super();
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public EnvironmentConfig(Properties properties) 
        throws IllegalArgumentException {

        super(properties);
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
    public void setLockTimeout(long timeout) 
        throws IllegalArgumentException {

        DbConfigManager.setVal(props,
                               EnvironmentParams.LOCK_TIMEOUT,
                               Long.toString(timeout), 
                               validateParams);
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public long getLockTimeout() {

        String val = DbConfigManager.getVal(props,
                                            EnvironmentParams.LOCK_TIMEOUT);
        long timeout = 0;
        try {
            timeout = Long.parseLong(val);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException
		("Bad value for timeout:" + e.getMessage());
        }
        return timeout;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public void setReadOnly(boolean readOnly) {

        DbConfigManager.setVal(props,
                               EnvironmentParams.ENV_RDONLY,
                               Boolean.toString(readOnly),
                               validateParams);
    } 

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public boolean getReadOnly() {

        String val = DbConfigManager.getVal(props,
                                            EnvironmentParams.ENV_RDONLY);
        return (Boolean.valueOf(val)).booleanValue();
    } 

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public void setTransactional(boolean transactional) {

        DbConfigManager.setVal(props,
                               EnvironmentParams.ENV_INIT_TXN,
                               Boolean.toString(transactional),
                               validateParams);
    } 

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public boolean getTransactional() {

        String val = DbConfigManager.getVal(props,
                                            EnvironmentParams.ENV_INIT_TXN);
        return (Boolean.valueOf(val)).booleanValue();
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public void setLocking(boolean locking) {

        DbConfigManager.setVal(props,
                               EnvironmentParams.ENV_INIT_LOCKING,
                               Boolean.toString(locking),
                               validateParams);
    } 

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public boolean getLocking() {

        String val =
            DbConfigManager.getVal(props, EnvironmentParams.ENV_INIT_LOCKING);
        return (Boolean.valueOf(val)).booleanValue();
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public void setTxnTimeout(long timeout) 
        throws IllegalArgumentException {

        DbConfigManager.setVal(props,
                               EnvironmentParams.TXN_TIMEOUT,
                               Long.toString(timeout),
                               validateParams);
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public long getTxnTimeout() {

        String val = DbConfigManager.getVal(props,
                                            EnvironmentParams.TXN_TIMEOUT);
        long timeout = 0;
        try {
            timeout = Long.parseLong(val);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException
		("Bad value for timeout:" + e.getMessage());
        }
        return timeout;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public void setTxnSerializableIsolation(boolean txnSerializableIsolation) {
        
        DbConfigManager.setVal(props,
                               EnvironmentParams.TXN_SERIALIZABLE_ISOLATION,
                               Boolean.toString(txnSerializableIsolation),
                               validateParams);
    } 

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public boolean getTxnSerializableIsolation() {

        String val =
            DbConfigManager.getVal(props,
                                   EnvironmentParams.TXN_SERIALIZABLE_ISOLATION);
        return (Boolean.valueOf(val)).booleanValue();
    } 

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public void setExceptionListener(ExceptionListener exceptionListener) {
	this.exceptionListener = exceptionListener;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public ExceptionListener getExceptionListener() {
	return exceptionListener;
    }

    /**
     * For unit testing, to set readCommitted as the default.
     */
    void setTxnReadCommitted(boolean txnReadCommitted) {
        
        this.txnReadCommitted = txnReadCommitted;
    } 

    /**
     * For unit testing, to set readCommitted as the default.
     */
    boolean getTxnReadCommitted() {
        
        return txnReadCommitted;
    } 

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public void setConfigParam(String paramName,
			       String value) 
        throws IllegalArgumentException {

        DbConfigManager.setConfigParam(props,
                                       paramName,
                                       value,
                                       false, /* requireMutablity */
                                       validateParams,
                                       false  /* forReplication */);
    }

    /**
     * For unit testing, to prevent creating the utilization profile DB.
     */
    void setCreateUP(boolean createUP) {
        this.createUP = createUP;
    }

    /**
     * For unit testing, to prevent creating the utilization profile DB.
     */
    boolean getCreateUP() {
        return createUP;
    }

    /**
     * For unit testing, to prevent writing utilization data during checkpoint.
     */
    void setCheckpointUP(boolean checkpointUP) {
        this.checkpointUP = checkpointUP;
    }

    /**
     * For unit testing, to prevent writing utilization data during checkpoint.
     */
    boolean getCheckpointUP() {
        return checkpointUP;
    }

    /**
     * Used by Environment to create a copy of the application
     * supplied configuration.
     */
    EnvironmentConfig cloneConfig() {
        try {
            return (EnvironmentConfig) clone();
        } catch (CloneNotSupportedException willNeverOccur) {
            return null;
        }
    }

    public String toString() {
        return ("allowCreate=" + allowCreate + "\n" + super.toString());
    }
}
