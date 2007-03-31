/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: EnvironmentConfigTest.java,v 1.10.2.1 2007/02/01 14:50:05 cwl Exp $
 */

package com.sleepycat.je;

import java.io.File;
import java.util.Properties;

import junit.framework.TestCase;

import com.sleepycat.je.Environment;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.util.TestUtils;

public class EnvironmentConfigTest extends TestCase {

    /**
     * Try out the validation in EnvironmentConfig.
     */
    public void testValidation()
	throws DatabaseException {

        /* 
         * This validation should be successfull
         */
        Properties props = new Properties();
        props.setProperty("java.util.logging.FileHandler.limit", "2000");
        props.setProperty("java.util.logging.FileHandler.on", "false");
        new EnvironmentConfig(props); // Just instantiate a config object.

        /*
         * Should fail: we should throw because leftover.param is not 
         * a valid parameter.
         */
        props.clear();
        props.setProperty("leftover.param", "foo");
        checkEnvironmentConfigValidation(props);
                                           
        /*
         * Should fail: we should throw because FileHandlerLimit
         * is less than its minimum
         */
        props.clear();
        props.setProperty("java.util.logging.FileHandler.limit", "1");
        checkEnvironmentConfigValidation(props);

        /*
         * Should fail: we should throw because FileHandler.on is not
         * a valid value.
         */
        props.clear();
        props.setProperty("java.util.logging.FileHandler.on", "xxx");
        checkEnvironmentConfigValidation(props);
    }

    /**
     * Test single parameter setting.
     */
    public void testSingleParam() 
        throws Exception {

        try {
            EnvironmentConfig config = new EnvironmentConfig();
            config.setConfigParam("foo", "7");
            fail("Should fail because of invalid param name");
        } catch (IllegalArgumentException e) {
            // expected.
        }

        EnvironmentConfig config = new EnvironmentConfig();
        config.setConfigParam(EnvironmentParams.MAX_MEMORY_PERCENT.getName(),
                              "81");
        assertEquals(81, config.getCachePercent());
    }

    public void testInconsistentParams()
	throws Exception {

	try {
            EnvironmentConfig config = new EnvironmentConfig();
	    config.setAllowCreate(true);
	    config.setLocking(false);
	    config.setTransactional(true);
	    File envHome = new File(System.getProperty(TestUtils.DEST_DIR));
	    Environment env = new Environment(envHome, config);
            fail("Should fail because of inconsistent param values");
        } catch (IllegalArgumentException e) {
            // expected.
        }
    }

    /* Helper to catch expected exceptions. */
    private void checkEnvironmentConfigValidation(Properties props) {
        try {
            new EnvironmentConfig(props);
            fail("Should fail because of a parameter validation problem");
        } catch (IllegalArgumentException e) {
            // expected.
        }
    }
}

