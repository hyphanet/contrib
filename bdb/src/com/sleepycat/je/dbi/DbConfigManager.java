/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2000,2007 Oracle.  All rights reserved.
 *
 * $Id: DbConfigManager.java,v 1.38.2.1 2007/02/01 14:49:44 cwl Exp $
 */

package com.sleepycat.je.dbi;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.config.BooleanConfigParam;
import com.sleepycat.je.config.ConfigParam;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.config.IntConfigParam;
import com.sleepycat.je.config.LongConfigParam;

/**
 * DbConfigManager holds the configuration parameters for an environment.
 *
 * In general, all configuration parameters are represented by a ConfigParam
 * defined in com.sleepycat.je.config.EnvironmentParams and can be represented
 * by a property described in the top level example.properties. Environment
 * parameters have some interesting twists because there are some attributes
 * that are scoped by handle, such as the commit durability (txnSync, 
 * txnNoSync, etc) parameters.
 *
 * DbConfigManager is instantiated first by the EnvironmentImpl, and is
 * loaded with the base configuration parameters. If replication is enabled,
 * additional properties are added when the Replicator is instantiated.
 * In order to keep replication code out of the base code, replication
 * parameters are loaded by way of the addConfigurations method.
 */
public class DbConfigManager {

    /* 
     * The name of the JE properties file, to be found in the environment
     * directory.
     */
    private static final String PROPFILE_NAME = "je.properties";

    /*
     * All properties in effect for this JE instance, both environment
     * and replicator scoped, are stored in this Properties field.
     */
    private Properties props;

    /* 
     * Save a reference to the environment config to access debug properties
     * that are fields in EnvironmentConfig, must be set before the 
     * environment is created, and are not represented as JE properties.
     */
    private EnvironmentConfig environmentConfig;

    public DbConfigManager(EnvironmentConfig config)
	throws DbConfigException {

        environmentConfig = config;
	if (config == null) {
	    props = new Properties();
	} else {
	    props = DbInternal.getProps(config);
	}
    }

    /**
     * Add all configuration properties in the specified property bag
     * to this environment's configuration. Used to add replication
     * specific configurations from ReplicatorConfig without referring
     * to replication classes.
     */
    public void addConfigurations(Properties additionalProps) {
        props.putAll(additionalProps);
    }

    public EnvironmentConfig getEnvironmentConfig() {
        return environmentConfig;
    }

    /* 
     * Parameter Access 
     */

    /**
     * Get this parameter from the environment wide configuration settings.
     * @param configParam
     *
     * @return default for param if param wasn't explicitly set
     */
    public synchronized String get(ConfigParam configParam)
	throws IllegalArgumentException {

        return getConfigParam(props, configParam.getName());
    }

    /**
     * Get this parameter from the environment wide configuration settings.
     * @param configParam
     *
     * @return default for param if param wasn't explicitly set
     */
    public synchronized String get(String configParamName)
	throws IllegalArgumentException {

        return getConfigParam(props, configParamName);
    }

    /**
     * Get this parameter from the environment wide configuration settings.
     *
     * @param configParam
     *
     * @return default for param if it wasn't explicitly set.
     */
    public boolean getBoolean(BooleanConfigParam configParam)
        throws DatabaseException {

        /* See if it's specified. */
        String val = get(configParam);
        return Boolean.valueOf(val).booleanValue();
    }

    /**
     * Get this parameter from the environment wide configuration settings.
     *
     * @param configParam
     * @return default for param if it wasn't explicitly set.
     */
    public int getInt(IntConfigParam configParam)
	throws DatabaseException {

        // See if it's specified
        String val = get(configParam);
        int intValue = 0;
        if (val != null) {
            try {
                intValue = Integer.parseInt(val);
            } catch (NumberFormatException e) {
                /*
		 * This should never happen if we put error checking into
                 * the loading of config values.
		 */
                assert false: e.getMessage();
            }
        }
        return intValue;
    }

    /**
     * Get this parameter from the environment wide configuration settings.
     *
     * @param configParam
     * @return default for param if it wasn't explicitly set
     */
    public long getLong(LongConfigParam configParam)
	throws DatabaseException {

        /* See if it's specified. */
        String val = get(configParam);
        long longValue = 0;
        if (val != null) {
            try {
                longValue = Long.parseLong(val);
            } catch (NumberFormatException e) {
                /*
		 * This should never happen if we put error checking
		 * into the loading of config values.
		 */
                assert false : e.getMessage();
            }
        }
        return longValue;
    }

    /*
     * Helper methods used by EnvironmentConfig and ReplicatorConfig.
     */

    /**
     * Validate a collection of configurations at Environment and Replicator
     * startup time. Check for valid configuration names and values.
     */
    public static void validateProperties(Properties props,
                                          boolean forReplication,
                                          String configClassName)
        throws IllegalArgumentException {

        /* Check that the properties have valid names and values */
        Enumeration propNames = props.propertyNames();
        while (propNames.hasMoreElements()) {
            String name = (String) propNames.nextElement();
            /* Is this a valid property name? */
            ConfigParam param =
                (ConfigParam) EnvironmentParams.SUPPORTED_PARAMS.get(name);
            if (param == null) {
                throw new IllegalArgumentException
		    (name + " is not a valid BDBJE environment configuration");
            }

            if (forReplication) {
                if (!param.isForReplication()) {
                    throw new IllegalArgumentException
                        (name +
                         " is not a replication environment configuration" +
                         " and cannot be used in " + configClassName);
                }
            } else {
                if (param.isForReplication()) {
                    throw new IllegalArgumentException
                        (name +
                         " is a replication environment configuration" +
                         " and cannot be used in " + configClassName);
                }
            }

            /* Is this a valid property value? */
            param.validateValue(props.getProperty(name));
        }
    }

    /**
     * Apply the configurations specified in the je.properties file to override
     * the programatically set configuration values held in the property bag.
     */
    public static void applyFileConfig(File envHome,
                                       Properties props,
                                       boolean forReplication,
                                       String errorClassName)
        throws IllegalArgumentException {

        File paramFile = null;
        try {
	    Properties fileProps = new Properties();	 
	    if (envHome != null) {
		if (envHome.isFile()) {
		    paramFile = envHome;
		} else {
		    paramFile = new File(envHome, PROPFILE_NAME);
		}
		FileInputStream fis = new FileInputStream(paramFile);
		fileProps.load(fis);
		fis.close();
	    }

            /* Validate the existing file. */
            validateProperties(fileProps,
                               forReplication,
                               errorClassName);
                
            /* Add them to the configuration object. */
            Iterator iter = fileProps.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry propPair = (Map.Entry) iter.next();
                String name = (String) propPair.getKey();
                String value = (String) propPair.getValue();
                setConfigParam(props,
                               name,
                               value,
                               false, /* don't need mutability, we're 
                                         initializing */
                               false, /* value already validated when set in
                                          config object */
                               forReplication);
            }
        } catch (FileNotFoundException e) {	 
            
            /* 
             * Klockwork - ok
             * Eat the exception, okay if the file doesn't exist.
             */
        } catch (IOException e) {	 
            IllegalArgumentException e2 = new IllegalArgumentException
                ("An error occurred when reading " + paramFile);
            e2.initCause(e);
            throw e2;
        }
    }

    /**
     * Helper method for environment and replicator configuration classes.
     * Set a configuration parameter. Check that the name is valid. 
     * If specified, also check that the value is valid.Value checking
     * may be disabled for unit testing.
     * 
     * @param props Property bag held within the configuration object.
     */
    public static void setConfigParam(Properties props,
                                      String paramName,
                                      String value,
                                      boolean requireMutability,
                                      boolean validateValue,
                                      boolean forReplication) 
        throws IllegalArgumentException {
        
        /* Is this a valid property name? */
        ConfigParam param =
            (ConfigParam) EnvironmentParams.SUPPORTED_PARAMS.get(paramName);
        if (param == null) {
            throw new IllegalArgumentException
		(paramName +
		 " is not a valid BDBJE environment configuration");
        }

        if (forReplication) {
            if (!param.isForReplication()) {
                throw new IllegalArgumentException
                    (paramName + " is not a BDBJE replication configuration.");
            }
        } else {
            if (param.isForReplication()) {
                throw new IllegalArgumentException
                    (paramName + " is only available for BDBJE replication.");
            }
        }

        /* Is this a mutable property? */
        if (requireMutability && !param.isMutable()) {
            throw new IllegalArgumentException
		(paramName +
		 " is not a mutable BDBJE environment configuration");
        }

        setVal(props, param, value, validateValue);
    }

    /**
     * Helper method for environment and replicator configuration classes.
     * Get the configuration value for the specified parameter, checking
     * that the parameter name is valid.
     * @param props Property bag held within the configuration object.
     */
    public static String getConfigParam(Properties props, String paramName)
        throws IllegalArgumentException {
        
        /* Is this a valid property name? */
        ConfigParam param =
            (ConfigParam) EnvironmentParams.SUPPORTED_PARAMS.get(paramName);
        if (param == null) {
            throw new IllegalArgumentException
		(paramName +
		 " is not a valid BDBJE environment configuration");
        }

        return DbConfigManager.getVal(props, param);
    }

    /**
     * Helper method for environment and replicator configuration classes.
     * Gets either the value stored in this configuration or the
     * default value for this param.
     */   
    public static String getVal(Properties props, ConfigParam param) {
        String val = props.getProperty(param.getName());
        if (val == null) {
            val = param.getDefault();
        }
        return val;
    }

    /**
     * Helper method for environment and replicator configuration classes.
     * Set and validate the value for the specified parameter.
     */
    public static void setVal(Properties props,
                              ConfigParam param,
                              String val,
                              boolean validateValue)
        throws IllegalArgumentException {

	if (validateValue) {
	    param.validateValue(val);
	}
        props.setProperty(param.getName(), val);
    }

}
