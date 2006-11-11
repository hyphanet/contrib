/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2006
 *      Oracle Corporation.  All rights reserved.
 *
 * $Id: ShortConfigParam.java,v 1.21 2006/09/12 19:16:44 cwl Exp $
 */

package com.sleepycat.je.config;

/**
 * A JE configuration parameter with an short value.
 */
public class ShortConfigParam extends ConfigParam {
    
    private static final String DEBUG_NAME =
        ShortConfigParam.class.getName();

    private Short min;
    private Short max;

    ShortConfigParam(String configName,
                     Short minVal,
                     Short maxVal,
                     Short defaultValue,
                     boolean mutable,
                     String description) {
        // defaultValue must not be null
        super(configName, defaultValue.toString(), mutable, description);

        min = minVal;
        max = maxVal;
    }

    /*
     * Self validate. Check mins and maxs.
     */
    private void validate(Short value)
	throws IllegalArgumentException {

        if (value != null) {
            if (min != null) {
                if (value.compareTo(min) < 0) {
                    throw new IllegalArgumentException
			(DEBUG_NAME + ":" +
			 " param " + name +
			 " doesn't validate, " + value +
			 " is less than min of " + min);
                }
            }
            if (max != null) {
                if (value.compareTo(max) > 0) {
                    throw new IllegalArgumentException
			(DEBUG_NAME + ":" +
			 " param " + name +
			 " doesn't validate, " + value +
			 " is greater than max of " +
			 max);
                }
            }
        }
    }

    public void validateValue(String value)
        throws IllegalArgumentException {

        try {
            validate(new Short(value));
        }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException
		(DEBUG_NAME + ": " +  value +
		 " not valid value for " + name);
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
