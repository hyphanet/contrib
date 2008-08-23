/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: EnvironmentParamsTest.java,v 1.15 2008/06/06 17:12:28 linda Exp $
 */

package com.sleepycat.je.config;

import junit.framework.TestCase;

import com.sleepycat.je.EnvironmentConfig;

public class EnvironmentParamsTest extends TestCase {

    private IntConfigParam intParam =
        new IntConfigParam("param.int",
			   new Integer(2),
			   new Integer(10),
			   new Integer(5),
                           false, // mutable
			   false);// for replication

    private LongConfigParam longParam =
        new LongConfigParam("param.long",
			    new Long(2),
			    new Long(10),
			    new Long(5),
                            false, // mutable
			    false);// for replication

    private ConfigParam mvParam =
	new ConfigParam("some.mv.param.#", null, true /* mutable */,
			false /* for replication */);

    /**
     * Test param validation.
     */
    public void testValidation() {
	assertTrue(mvParam.isMultiValueParam());

        try {
		new ConfigParam(null, "foo", false /* mutable */,
				false /* for replication */);
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

    /**
     * Check that an invalid parameter isn't mistaken for a multivalue
     * param.
     */
    public void testInvalidVsMultiValue() {
	try {
	    EnvironmentConfig envConfig = new EnvironmentConfig();
	    envConfig.setConfigParam("je.maxMemory.stuff", "true");
            fail("Should throw exception");
	} catch (IllegalArgumentException IAE) {
	    // expected
	}
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
