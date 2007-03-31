/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: EnvironmentParamsTest.java,v 1.8.2.1 2007/02/01 14:50:08 cwl Exp $
 */

package com.sleepycat.je.config;

import junit.framework.TestCase;

public class EnvironmentParamsTest extends TestCase {


    private IntConfigParam intParam =
        new IntConfigParam("param.int", 
			   new Integer(2),
			   new Integer(10),
			   new Integer(5),
                           false, // mutable
			   false, // for replication
			   "test int param");

    private LongConfigParam longParam =
        new LongConfigParam("param.long", 
			    new Long(2),
			    new Long(10),
			    new Long(5),
                            false, // mutable
			    false, // for replication
			    "test long param");


    /**
     * Test param validation
     */
    public void testValidation() {
        try {
            ConfigParam param = new ConfigParam(null, 
                                                "foo",
                                                false, // mutable
                                                false, // for replication
                                                "foo param");
            fail("should disallow null name");
        } catch (IllegalArgumentException e) {
            // expected.
        }

        /* Test bounds. These are all invalid and should fail */
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
