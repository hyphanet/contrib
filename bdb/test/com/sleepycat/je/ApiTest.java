/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: ApiTest.java,v 1.15.2.1 2007/02/01 14:50:04 cwl Exp $
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
