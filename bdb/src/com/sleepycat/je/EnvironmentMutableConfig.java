/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: EnvironmentMutableConfig.java,v 1.29.2.1 2007/02/01 14:49:41 cwl Exp $
 */

package com.sleepycat.je;

import java.util.Enumeration;
import java.util.Iterator;
import java.util.Properties;

import com.sleepycat.je.config.ConfigParam;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.DbConfigManager;
import com.sleepycat.je.dbi.EnvironmentImpl;

/**
 * Javadoc for this public class is generated
 * via the doc templates in the doc_src directory.
 */
public class EnvironmentMutableConfig implements Cloneable {

    /* 
     * Change copyHandlePropsTo and Environment.copyToHandleConfig
     * when adding fields here.
     */
    private boolean txnNoSync = false;
    private boolean txnWriteNoSync = false;

    /* 
     * Cache size is a category of property that is calculated within the
     * environment.  It is only supplied when returning the cache size to the
     * application and never used internally; internal code directly checks
     * with the MemoryBudget class;
     */
    protected long cacheSize;

    /**
     * Note that in the implementation we choose not to extend Properties 
     * in order to keep the configuration type safe.
     */
    protected Properties props;

    /**
     * For unit testing, to prevent loading of je.properties.
     */
    private boolean loadPropertyFile = true;

    /**
     * Internal boolean that says whether or not to validate params.  Setting
     * it to false means that parameter value validatation won't be performed
     * during setVal() calls.  Only should be set to false by unit tests using
     * DbInternal.
     */
    boolean validateParams = true;

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public EnvironmentMutableConfig() {
        props = new Properties();
    }

    /**
     * Used by EnvironmentConfig to construct from properties.
     */
    EnvironmentMutableConfig(Properties properties)
        throws IllegalArgumentException {

        DbConfigManager.validateProperties(properties, 
                                           false,  // forReplication
                                           this.getClass().getName());
        /* For safety, copy the passed in properties. */
        props = new Properties();
        props.putAll(properties);
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public void setTxnNoSync(boolean noSync) {
        txnNoSync = noSync;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public boolean getTxnNoSync() {
        return txnNoSync;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public void setTxnWriteNoSync(boolean writeNoSync) {
        txnWriteNoSync = writeNoSync;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public boolean getTxnWriteNoSync() {
        return txnWriteNoSync;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public void setCacheSize(long totalBytes) 
        throws IllegalArgumentException {

        DbConfigManager.setVal(props, EnvironmentParams.MAX_MEMORY,
                               Long.toString(totalBytes), validateParams);
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public long getCacheSize() {

        /* 
         * CacheSize is filled in from the EnvironmentImpl by way of
         * copyHandleProps.
         */
        return cacheSize;
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public void setCachePercent(int percent) 
        throws IllegalArgumentException {

        DbConfigManager.setVal(props, EnvironmentParams.MAX_MEMORY_PERCENT,
                               Integer.toString(percent), validateParams);
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public int getCachePercent() {

        String val =
            DbConfigManager.getVal(props,
                                   EnvironmentParams.MAX_MEMORY_PERCENT);
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException
		("Cache percent is not a valid integer: " + e.getMessage());
        }
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public void setConfigParam(String paramName, String value) 
        throws IllegalArgumentException {
        
        DbConfigManager.setConfigParam(props,
                                       paramName,
                                       value,
                                       true, /* require mutability. */
                                       validateParams, 
                                       false /* forReplication */);
    }

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public String getConfigParam(String paramName)
        throws IllegalArgumentException {
        
       return DbConfigManager.getConfigParam(props, paramName);
    }

    /*
     * Helpers
     */
    void setValidateParams(boolean validateParams) {
	this.validateParams = validateParams;
    }

    /**
     * Check that the immutable values in the environment config used to open
     * an environment match those in the config object saved by the underlying
     * shared EnvironmentImpl.
     */
    void checkImmutablePropsForEquality(EnvironmentMutableConfig passedConfig)
        throws IllegalArgumentException {

        Properties passedProps = passedConfig.props;
        Iterator iter = EnvironmentParams.SUPPORTED_PARAMS.keySet().iterator();
        while (iter.hasNext()) {
            String paramName = (String) iter.next();
            ConfigParam param = (ConfigParam)
                EnvironmentParams.SUPPORTED_PARAMS.get(paramName);
            assert param != null;
            if (!param.isMutable()) {
                String paramVal = props.getProperty(paramName);
                String useParamVal = passedProps.getProperty(paramName);
                if ((paramVal != null) ? (!paramVal.equals(useParamVal))
                                       : (useParamVal != null)) {
                    throw new IllegalArgumentException
                        (paramName + " is set to " +
                         useParamVal +
                         " in the config parameter" +
                         " which is incompatible" +
                         " with the value of " +
                         paramVal + " in the" +
                         " underlying environment");
                }
            }
        }
    }

    /**
     * Overrides Object.clone() to clone all properties, used by this class and
     * EnvironmentConfig.
     */
    protected Object clone()
        throws CloneNotSupportedException {

        EnvironmentMutableConfig copy =
            (EnvironmentMutableConfig) super.clone();
        copy.props = (Properties) props.clone();
        return copy;
    }

    /**
     * Used by Environment to create a copy of the application
     * supplied configuration. Done this way to provide non-public cloning.
     */
    EnvironmentMutableConfig cloneMutableConfig() {
        try {
            EnvironmentMutableConfig copy = (EnvironmentMutableConfig) clone();
            /* Remove all immutable properties. */
            copy.clearImmutableProps();
            return copy;
        } catch (CloneNotSupportedException willNeverOccur) {
            return null;
        }
    }

    /**
     * Copies the per-handle properties of this object to the given config
     * object.
     */
    void copyHandlePropsTo(EnvironmentMutableConfig other) {
        other.txnNoSync = txnNoSync;
        other.txnWriteNoSync = txnWriteNoSync;
    }

    /**
     * Copies all mutable props to the given config object.
     */
    void copyMutablePropsTo(EnvironmentMutableConfig toConfig) {

        Properties toProps = toConfig.props;
        Enumeration propNames = props.propertyNames();
        while (propNames.hasMoreElements()) {
            String paramName = (String) propNames.nextElement();
            ConfigParam param = (ConfigParam)
                EnvironmentParams.SUPPORTED_PARAMS.get(paramName);
            assert param != null;
            if (param.isMutable()) {
                String newVal = props.getProperty(paramName);
                toProps.setProperty(paramName, newVal);
            }
        }
    }

    /**
     * Fill in the properties calculated by the environment to the given
     * config object.
     */
    void fillInEnvironmentGeneratedProps(EnvironmentImpl envImpl) {
        cacheSize = envImpl.getMemoryBudget().getMaxMemory();
    }

    /**
     * Removes all immutable props.
     */
    private void clearImmutableProps() {
        Enumeration propNames = props.propertyNames();
        while (propNames.hasMoreElements()) {
            String paramName = (String) propNames.nextElement();
            ConfigParam param = (ConfigParam)
                EnvironmentParams.SUPPORTED_PARAMS.get(paramName);
            assert param != null;
            if (!param.isMutable()) {
                props.remove(paramName);
            }
        }
    }

    Properties getProps() {
        return props;
    }

    /**
     * For unit testing, to prevent loading of je.properties.
     */
    void setLoadPropertyFile(boolean loadPropertyFile) {
        this.loadPropertyFile = loadPropertyFile;
    }

    /**
     * For unit testing, to prevent loading of je.properties.
     */
    boolean getLoadPropertyFile() {
        return loadPropertyFile;
    }

    /**
     * Testing support
     */
    int getNumExplicitlySetParams() {
        return props.size();
    }

    public String toString() {
        return props.toString();
    }
}
