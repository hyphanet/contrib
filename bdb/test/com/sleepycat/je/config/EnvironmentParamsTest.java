/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2006
 *      Oracle Corporation.  All rights reserved.
 *
 * $Id: EnvironmentParamsTest.java,v 1.6 2006/09/12 19:17:14 cwl Exp $
 */

package com.sleepycat.je.config;

import junit.framework.TestCase;

public class EnvironmentParamsTest extends TestCase {

    private ShortConfigParam shortParam = 
        new ShortConfigParam("param.short", 
			     new Short((short)2),
			     new Short((short)10),
			     new Short((short)5),
                             false,
			     "test short param");

    private IntConfigParam intParam =
        new IntConfigParam("param.int", 
			   new Integer(2),
			   new Integer(10),
			   new Integer(5),
                           false,
			   "test int param");

    private LongConfigParam longParam =
        new LongConfigParam("param.long", 
			    new Long(2),
			    new Long(10),
			    new Long(5),
                            false,
			    "test long param");


    /**
     * Test param validation
     */
    public void testValidation() {
        try {
            ConfigParam param = new ConfigParam(null, 
                                                "foo",
                                                false,
                                                "foo param");
            fail("should disallow null name");
        } catch (IllegalArgumentException e) {
            // expected.
        }

        /* Test bounds. These are all invalid and should fail */
        checkValidateParam(shortParam, "xxx");
        checkValidateParam(shortParam, "1");
        checkValidateParam(shortParam, "11");
        checkValidateParam(intParam, "1");
        checkValidateParam(intParam, "11");
        checkValidateParam(longParam, "1");
        checkValidateParam(longParam, "11");
    }

    /* Helper to catch expected exceptions */
    private void checkValidateParam(ConfigParam param, String value) {
        try {
            param.validateValue(value);
            fail("Should throw exception");
        } catch (IllegalArgumentException e) {
            // expect this exception
        }
    }

}
