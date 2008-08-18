/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: ApiTest.java,v 1.19 2008/01/07 14:29:04 cwl Exp $
 */

package com.sleepycat.je;

import junit.framework.TestCase;


/**
 * Test parameter handling for api methods.
 */
public class ApiTest extends TestCase {

    public void testBasic()
        throws Exception {

        try {
            new Environment(null, null);
            fail("Should get exception");
        } catch (NullPointerException e) {
            // expected exception
        } catch (Exception e) {
            fail("Shouldn't get other exception");
        }
    }
}
