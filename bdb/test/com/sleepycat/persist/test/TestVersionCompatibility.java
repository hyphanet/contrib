/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2000,2007 Oracle.  All rights reserved.
 *
 * $Id: TestVersionCompatibility.java,v 1.1.2.4 2007/12/07 00:34:07 mark Exp $
 */
package com.sleepycat.persist.test;

import java.io.IOException;
import java.util.Enumeration;

import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * Test that the catalog and data records created with a different version of
 * the DPL are compatible with this version.  This test is run as follows:
 *
 * 1) Run EvolveTest with version X of JE.  For example:
 *
 *    cd /jeX
 *    ant -Dtestcase=com.sleepycat.persist.test.EvolveTest test
 * or
 *    ant -Dsuite=persist/test test
 * or
 *    ant test
 *
 * Step (1) leaves the log files from all tests in the testevolve directory.
 *
 * 2) Run TestVersionCompatibility with version Y of JE, passing the JE
 * testevolve directory from step (1).  For example:
 *
 *    cd /jeY
 *    ant -Dtestcase=com.sleepycat.persist.test.TestVersionCompatibility \
 *        -Dunittest.testevolvedir=/jeX/build/test/testevolve \
 *        test
 *
 * Currently there are 2 sets of X and Y that can be tested, one set for the
 * CVS branch and one for the CVS trunk:
 *
 *  CVS     Version X   Version Y
 *  branch  je-3_2_56   je-3_2_57 or greater
 *  trunk   je-3_3_41   je-3_3_42 or greater 
 *
 * This test is not run along with the regular JE test suite run, because the
 * class name does not end with Test.  It must be run separately as described
 * above.
 *
 * @author Mark Hayes
 */
public class TestVersionCompatibility extends EvolveTestBase {

    public static Test suite()
        throws Exception {

        /*
         * Run TestVersionCompatibility tests first to check previously evolved
         * data without changing it.  Then run the EvolveTest to try evolving
         * it.
         */
        TestSuite suite = new TestSuite();
        Enumeration e = getSuite(TestVersionCompatibility.class).tests();
        while (e.hasMoreElements()) {
            EvolveTestBase test = (EvolveTestBase) e.nextElement();
            if (test.getTestInitHome(true /*evolved*/).exists()) {
                suite.addTest(test);
            }
        }
        e = getSuite(EvolveTest.class).tests();
        while (e.hasMoreElements()) {
            EvolveTestBase test = (EvolveTestBase) e.nextElement();
            if (test.getTestInitHome(true /*evolved*/).exists()) {
                suite.addTest(test);
            }
        }
        return suite;
    }

    boolean useEvolvedClass() {
        return true;
    }

    @Override
    public void setUp()
        throws IOException {

        envHome = getTestInitHome(true /*evolved*/);
    }

    public void testPreviouslyEvolved()
        throws Exception {

        /* If the store cannot be opened, this test is not appropriate. */
        if (caseObj.getStoreOpenException() != null) {
            return;
        }

        /* The update occurred previously. */
        caseObj.updated = true;

        openEnv();

        /* Open read-only and double check that everything is OK. */
        openStoreReadOnly();
        caseObj.checkEvolvedModel
            (store.getModel(), env, true /*oldTypesExist*/);
        caseObj.readObjects(store, false /*doUpdate*/);
        caseObj.checkEvolvedModel
            (store.getModel(), env, true /*oldTypesExist*/);
        closeStore();

        /* Check raw objects. */
        openRawStore();
        caseObj.checkEvolvedModel
            (rawStore.getModel(), env, true /*oldTypesExist*/);
        caseObj.readRawObjects
            (rawStore, true /*expectEvolved*/, true /*expectUpdated*/);
        closeRawStore();

        closeAll();
    }
}
