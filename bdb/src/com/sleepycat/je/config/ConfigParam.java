/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2000,2007 Oracle.  All rights reserved.
 *
 * $Id: ConfigParam.java,v 1.26.2.1 2007/02/01 14:49:43 cwl Exp $
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

    protected String name;
    private String defaultValue;
    private String description;
    private boolean mutable;
    private boolean forReplication;

    /*
     * Create a String parameter.
     */
    public ConfigParam(String configName,
                       String configDefault,
                       boolean mutable,
                       boolean forReplication,
                       String description)
        throws IllegalArgumentException {
        name = configName;
        defaultValue = configDefault;
        this.mutable = mutable;
        this.description = description;
        this.forReplication = forReplication;

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

    public boolean isForReplication() {
        return forReplication;
    }

    public void setForReplication(boolean forReplication) {
        this.forReplication = forReplication;
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
