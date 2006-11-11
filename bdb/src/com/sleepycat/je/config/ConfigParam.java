/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2000-2006
 *      Oracle Corporation.  All rights reserved.
 *
 * $Id: ConfigParam.java,v 1.23 2006/09/12 19:16:44 cwl Exp $
 */

package com.sleepycat.je.config;


/**
 * A ConfigParam embodies the metatdata about a JE configuration parameter:
 * the parameter name, default value, and a validation method.
 *
 * Validation can be done in the scope of this parameter, or as a function of
 * other parameters.
 */
public class ConfigParam {
    // Delimiter used for string parameters that hold multiple values
    public static final String CONFIG_DELIM = ";";

    String name;
    private String defaultValue;
    private String description;
    private boolean mutable;

    /*
     * Create a String parameter.
     */
    ConfigParam(String configName, String configDefault, boolean mutable,
                String description)
        throws IllegalArgumentException {
        name = configName;
        defaultValue = configDefault;
        this.mutable = mutable;
        this.description = description;

        /* Check that the name and default value are valid */
        validateName(configName);
        validateValue(configDefault);

        /* 
         * Add it the list of supported environment parameters.
         */
        EnvironmentParams.addSupportedParam(this);
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getExtraDescription() {
        // None by default.
        return null;
    }

    public String getDefault() {
        return defaultValue;
    }

    public boolean isMutable() {
        return mutable;
    }

    /**
     * Validate yourself.
     */
    public void validate()
	throws IllegalArgumentException {

        validateName(name);
        validateValue(defaultValue);
    }
    
    /*
     * A param name can't be null or 0 length
     */
    private void validateName(String name)
        throws IllegalArgumentException {

        if ((name == null) || (name.length() < 1)) {
            throw new IllegalArgumentException(" A configuration parameter" +
                                               " name can't be null or 0" +
                                               " length");
        }
    }

    /*
     * Validate your value. (No default validation for strings.
     */
    public void validateValue(String value)
	throws IllegalArgumentException {
    }

    public String toString() {
        return name;
    }
}
