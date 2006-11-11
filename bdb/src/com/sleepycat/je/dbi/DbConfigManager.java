/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2000-2006
 *      Oracle Corporation.  All rights reserved.
 *
 * $Id: DbConfigManager.java,v 1.34 2006/09/12 19:16:45 cwl Exp $
 */

package com.sleepycat.je.dbi;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.config.BooleanConfigParam;
import com.sleepycat.je.config.ConfigParam;
import com.sleepycat.je.config.IntConfigParam;
import com.sleepycat.je.config.LongConfigParam;
import com.sleepycat.je.config.ShortConfigParam;

/**
 * DbConfigManager holds the configuration parameters for an environment.
 */
public class DbConfigManager {

    private EnvironmentConfig environmentConfig;

    /**
     * Todo: should this even be a separate class? 
     */
    public DbConfigManager(EnvironmentConfig config)
	throws DbConfigException {

        environmentConfig = config;
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

        return environmentConfig.getConfigParam(configParam.getName());
    }

    /**
     * Get this parameter from the environment wide configuration settings.
     * @param configParam
     *
     * @return default for param if param wasn't explicitly set
     */
    public synchronized String get(String configParamName)
	throws IllegalArgumentException {

        return environmentConfig.getConfigParam(configParamName);
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
     *
     * @return default for param if it wasn't explicitly set.
     */
    public short getShort(ShortConfigParam configParam)
	throws DatabaseException {

        String val = get(configParam);
        short shortValue = 0;
        try {
            shortValue = Short.parseShort(val);
        } catch (NumberFormatException e) {
            /* 
	     * This should never happen if we put error checking into
             * the loading of config values.
	     */
	    assert false: e.getMessage();
        }
        return shortValue;
    }

    /**
     * Get this parameter from the environment wide configuration settings.
     *
     * @param configParam
     *
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
     *
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
}
