/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2000,2008 Oracle.  All rights reserved.
 *
 * $Id: ConfigParam.java,v 1.32 2008/06/10 02:52:09 cwl Exp $
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

    protected String name;
    private String defaultValue;
    private boolean mutable;
    private boolean forReplication;
    private boolean isMultiValueParam;

    /*
     * Create a String parameter.
     */
    public ConfigParam(String configName,
                       String configDefault,
                       boolean mutable,
                       boolean forReplication)
        throws IllegalArgumentException {

	if (configName == null) {
	    name = null;
	} else {

	    /*
	     * For Multi-Value params (i.e. those who's names end with ".#"),
	     * strip the .# off the end of the name before storing and flag it
	     * with isMultiValueParam=true.
	     */
	    int mvFlagIdx = configName.indexOf(".#");
	    if (mvFlagIdx < 0) {
		name = configName;
		isMultiValueParam = false;
	    } else {
		name = configName.substring(0, mvFlagIdx);
		isMultiValueParam = true;
	    }
	}

        defaultValue = configDefault;
        this.mutable = mutable;
        this.forReplication = forReplication;

        /* Check that the name and default value are valid */
        validateName(configName);
        validateValue(configDefault);

        /* Add it the list of supported environment parameters. */
        EnvironmentParams.addSupportedParam(this);
    }

    /*
     * Return the parameter name of a multi-value parameter.  e.g.
     * "je.rep.remote.address.foo" => "je.rep.remote.address"
     */
    public static String multiValueParamName(String paramName) {
	int mvParamIdx = paramName.lastIndexOf('.');
	if (mvParamIdx < 0) {
	    return null;
	}
	return paramName.substring(0, mvParamIdx);
    }

    /*
     * Return the label of a multi-value parameter.  e.g.
     * "je.rep.remote.address.foo" => foo.
     */
    public static String mvParamIndex(String paramName) {

	int mvParamIdx = paramName.lastIndexOf('.');
	return paramName.substring(mvParamIdx + 1);
    }

    public String getName() {
        return name;
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
	
    public boolean isMultiValueParam() {
	return isMultiValueParam;
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
     * Validate your value. (No default validation for strings.)
     * May be overridden for (e.g.) Multi-value params.
     */
    public void validateValue(String value)
	throws IllegalArgumentException {

    }

    @Override
    public String toString() {
        return name;
    }
}
