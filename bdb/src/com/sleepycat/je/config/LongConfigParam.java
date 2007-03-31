/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: LongConfigParam.java,v 1.24.2.1 2007/02/01 14:49:43 cwl Exp $
 */

package com.sleepycat.je.config;

/**
 * A JE configuration parameter with an integer value.
 */
public class LongConfigParam extends ConfigParam {
    
    private static final String DEBUG_NAME = LongConfigParam.class.getName();

    private Long min;
    private Long max;

    LongConfigParam(String configName,
                    Long minVal,
                    Long maxVal,
                    Long defaultValue,
                    boolean mutable,
                    boolean forReplication,
                    String description) {

        // defaultValue must not be null
        super(configName, defaultValue.toString(), mutable,
              forReplication, description);
        min = minVal;
        max = maxVal;
    }

    /*
     * Self validate. Check mins and maxs
     */
    private void validate(Long value)
	throws IllegalArgumentException {

        if (value != null) {
            if (min != null) {
                if (value.compareTo(min) < 0) {
                    throw new IllegalArgumentException
			(DEBUG_NAME + ":" +
			 " param " + name +
			 " doesn't validate, " +
			 value +
			 " is less than min of "
			 + min);
                }
            }
            if (max != null) {
                if (value.compareTo(max) > 0) {
                    throw new IllegalArgumentException
			(DEBUG_NAME + ":" +
			 " param " + name +
			 " doesn't validate, " +
			 value +
			 " is greater than max "+
			 " of " +  max);
                }
            }
        }
    }

    public void validateValue(String value)
        throws IllegalArgumentException {

        try {
            validate(new Long(value));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException
		(DEBUG_NAME + ": " +  value + " not valid value for " + name);
        }
    }

    public String getExtraDescription() {
        StringBuffer minMaxDesc = new StringBuffer();
        if (min != null) {
            minMaxDesc.append("# minimum = ").append(min);
        }
        if (max != null) {
            if (min != null) {
                minMaxDesc.append("\n");
            }
            minMaxDesc.append("# maximum = ").append(max);
        }
        return minMaxDesc.toString();
    }
}
