/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: RecoveryDuplicatesTest.java,v 1.13.2.1 2007/02/01 14:50:17 cwl Exp $
 */

package com.sleepycat.je.recovery;

import java.util.Hashtable;

import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.Environment;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.util.TestUtils;

public class RecoveryDuplicatesTest extends RecoveryTestBase {

    public void testDuplicates()
        throws Throwable {

        createEnvAndDbs(1 << 20, true, NUM_DBS);
        int numRecs = 10;
        int numDups = N_DUPLICATES_PER_KEY;

        try {
            /* Set up an repository of expected data. */
            Hashtable expectedData = new Hashtable();

            /* Insert all the data. */
            Transaction txn = env.beginTransaction(null, null);
            insertData(txn, 0, numRecs - 1, expectedData,
                       numDups, true, NUM_DBS);
            txn.commit();
            closeEnv();
            recoverAndVerify(expectedData, NUM_DBS);
	} catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    public void testDuplicatesWithDeletion()
        throws Throwable {

        createEnvAndDbs(1 << 20, true, NUM_DBS);
        int numRecs = 10;
        int nDups = N_DUPLICATES_PER_KEY;

        try {
            /* Set up an repository of expected data. */
            Hashtable expectedData = new Hashtable();

            /* Insert all the data. */
            Transaction txn = env.beginTransaction(null, null);
            insertData(txn, 0, numRecs -1, expectedData, nDups, true, NUM_DBS);

            /* Delete all the even records. */
            deleteData(txn, expectedData, false, true, NUM_DBS);
            txn.commit();

            /* Modify all the records. */
            //    modifyData(expectedData);

            closeEnv();

            recoverAndVerify(expectedData, NUM_DBS);
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    /*
     * See SR11455 for details.
     *
     * This test is checking that the maxTxnId gets recovered properly during
     * recovery.  The SR has to do with the INFileReader not including
     * DupCountLN_TX and DelDupLN_TX's in its txnIdTrackingMap.  When these
     * were not included, it was possible for a transaction to consist solely
     * of DupCountLN_TX/DelDupLN_TX pairs.  The "deleteData" transaction below
     * does just this.  If no checkpoint occurred following such a transaction,
     * then the correct current txnid would not be written to the log and
     * determining this value during recovery would be left up to the
     * INFileReader.  However, without reading the DupCountLN_TX/DelDupLN_TX
     * records, it would not recover the correct value.
     *
     * We take the poor man's way out of creating this situation by just
     * manually asserting the txn id is correct post-recovery.  The txnid of 12
     * was determined by looking through logs before and after the fix.
     */
    public void testSR11455()
        throws Throwable {

        createEnvAndDbs(1 << 20, true, 1);
        int numRecs = 1;
        int nDups = 3;

        try {
            /* Set up an repository of expected data. */
            Hashtable expectedData = new Hashtable();

            /* Insert all the data. */
            Transaction txn = env.beginTransaction(null, null);
            insertData(txn, 0, numRecs -1, expectedData, nDups, true, 1);
	    txn.commit();

	    txn = env.beginTransaction(null, null);
            /* Delete all the even records. */
            deleteData(txn, expectedData, false, false, 1);
            txn.abort();
            closeEnv();

	    /* Open it again, which will run recovery. */
	    EnvironmentConfig recoveryConfig = TestUtils.initEnvConfig();
	    recoveryConfig.setTransactional(true);
	    recoveryConfig.setConfigParam
		(EnvironmentParams.ENV_RUN_CLEANER.getName(), "false");
	    recoveryConfig.setConfigParam
		(EnvironmentParams.ENV_RUN_EVICTOR.getName(), "false");
	    env = new Environment(envHome, recoveryConfig);

	    txn = env.beginTransaction(null, null);
	    assertEquals(6, txn.getId());
	    txn.commit();
	    env.close();

        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    public void testDuplicatesWithAllDeleted()
        throws Throwable {

        createEnvAndDbs(1 << 20, true, NUM_DBS);
        int numRecs = 10;
        int nDups = N_DUPLICATES_PER_KEY;

        try {
            /* Set up an repository of expected data. */
            Hashtable expectedData = new Hashtable();

            /* Insert all the data. */
            Transaction txn = env.beginTransaction(null, null);
            insertData(txn, 0, numRecs - 1, expectedData, nDups,
		       true, NUM_DBS);

            /* Delete all data. */
            deleteData(txn, expectedData, true, true, NUM_DBS);
            txn.commit();

            /* Modify all the records. */
	    //    modifyData(expectedData);
            closeEnv();

            recoverAndVerify(expectedData, NUM_DBS);
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }
}
