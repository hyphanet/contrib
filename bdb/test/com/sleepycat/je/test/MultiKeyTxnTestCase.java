/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: MultiKeyTxnTestCase.java,v 1.4.2.1 2007/02/01 14:50:19 cwl Exp $
 */

package com.sleepycat.je.test;

import java.util.Enumeration;
import java.util.Set;

import junit.framework.TestSuite;

import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.SecondaryDatabase;
import com.sleepycat.je.SecondaryKeyCreator;
import com.sleepycat.je.SecondaryMultiKeyCreator;

/**
 * Permutes a TxnTestCase over a boolean property for using multiple secondary
 * keys.
 */
public abstract class MultiKeyTxnTestCase  extends TxnTestCase {

    boolean useMultiKey = false;

    static TestSuite multiKeyTxnTestSuite(Class testCaseClass,
                                          EnvironmentConfig envConfig,
                                          String[] txnTypes) {

        TestSuite suite = new TestSuite();
        for (int i = 0; i < 2; i += 1) {
            boolean multiKey = (i == 1);
            TestSuite txnSuite = txnTestSuite(testCaseClass, envConfig,
                                              txnTypes);
            Enumeration e = txnSuite.tests();
            while (e.hasMoreElements()) {
                MultiKeyTxnTestCase test =
                    (MultiKeyTxnTestCase) e.nextElement();
                test.useMultiKey = multiKey;
                suite.addTest(test);
            }
        }
        return suite;
    }
    
    public void tearDown()
        throws Exception {

        super.tearDown();
        if (useMultiKey) {
            setName("multi-key:" + getName());
        }
    }

    /**
     * Wraps a single key creator to exercise the multi-key code for tests that
     * only create a single secondary key.
     */
    static class SimpleMultiKeyCreator
        implements SecondaryMultiKeyCreator {

        private SecondaryKeyCreator keyCreator;

        SimpleMultiKeyCreator(SecondaryKeyCreator keyCreator) {
            this.keyCreator = keyCreator;
        }

        public void createSecondaryKeys(SecondaryDatabase secondary,
                                        DatabaseEntry key,
                                        DatabaseEntry data,
                                        Set results)
            throws DatabaseException {

            DatabaseEntry result = new DatabaseEntry();
            if (keyCreator.createSecondaryKey(secondary, key, data, result)) {
                results.add(result);
            }
        }
    }
}
