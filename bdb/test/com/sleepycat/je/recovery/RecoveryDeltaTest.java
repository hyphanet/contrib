/*
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: RecoveryDeltaTest.java,v 1.26.2.1 2007/02/01 14:50:17 cwl Exp $
 */
package com.sleepycat.je.recovery;

import java.util.Hashtable;
import java.util.List;

import com.sleepycat.je.Cursor;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.config.EnvironmentParams;

/**
 * Exercise delta BIN logging.
 */
public class RecoveryDeltaTest extends RecoveryTestBase {

    /**
     * The recovery delta tests set extra properties.
     */
    public void setExtraProperties()
	throws DatabaseException {

        /* Always run with delta logging cranked up. */
        envConfig.setConfigParam
            (EnvironmentParams.BIN_DELTA_PERCENT.getName(), "75");

        /*
         * Make sure that the environments in this unit test always
         * run with checkpointing off, so we can call it explcitly.
         */
        envConfig.setConfigParam
            (EnvironmentParams.ENV_RUN_CHECKPOINTER.getName(), "false");
        /*
         * Make sure that the environments in this unit test always
         * run with the compressor off, so we get known results
         */
        envConfig.setConfigParam
            (EnvironmentParams.ENV_RUN_INCOMPRESSOR.getName(), "false");
    }

    /**
     * Test the interaction of compression and deltas. After a compress,
     * the next entry must be a full one.
     */
    public void testCompress() 
        throws Throwable {

        createEnvAndDbs(1 << 20, true, NUM_DBS);
        int numRecs = 20;
        try {
            // Set up an repository of expected data
            Hashtable expectedData = new Hashtable();

            // insert all the data
            Transaction txn = env.beginTransaction(null, null);
            insertData(txn, 0, numRecs - 1, expectedData, 1, true, NUM_DBS);
            txn.commit();

            // delete every other record
            txn = env.beginTransaction(null, null);
            deleteData(txn, expectedData, false, true, NUM_DBS);
            txn.commit();


            // Ask the compressor to run.
            env.compress();	
            
            // force a checkpoint, should avoid deltas..
            env.checkpoint(forceConfig);
            
            closeEnv();

            recoverAndVerify(expectedData, NUM_DBS);
            
        } catch (Throwable t) {
            // print stacktrace before trying to clean up files
            t.printStackTrace();
            throw t;
        }
    }

    /**
     * Test a recovery that processes deltas.
     */
    public void testRecoveryDelta() 
        throws Throwable {

        createEnvAndDbs(1 << 20, true, NUM_DBS);
        int numRecs = 20;
        try {
            /* Set up a repository of expected data */
            Hashtable expectedData = new Hashtable();

            /* 
             * Force a checkpoint, to flush a full version of the BIN
             * to disk, so the next checkpoint can cause deltas
             */
            env.checkpoint(forceConfig);

            /* insert data */
            Transaction txn = env.beginTransaction(null, null);
            insertData(txn, 0, numRecs - 1, expectedData, 1, true, NUM_DBS);
            txn.commit();

            /* This checkpoint should write deltas. Although there's
             * just been one spate of insertions, those insertions caused
             * some BIN splitting, so many BINS have a logged version
             * on disk. This is what causes the deltas.
             */
            env.checkpoint(forceConfig);
            
            closeEnv();
            List infoList = recoverAndVerify(expectedData, NUM_DBS);

            /* Check that this recovery processed deltas */
            RecoveryInfo firstInfo = (RecoveryInfo) infoList.get(0);
            assertTrue(firstInfo.numBinDeltas > 0);

        } catch (Throwable t) {
            // print stacktrace before trying to clean up files
            t.printStackTrace();
            throw t;
        }
    }
   
    /**
     * This test checks that reconstituting the bin deals properly with
     * the knownDeleted flag
     * insert key1, abort, checkpoint,  -- after abort, childref KD = true;
     * insert key1, commit,             -- after commit, childref KD = false
     * delete key1, abort,              -- BinDelta should have KD = false
     * checkpoint to write deltas,
     * recover. verify key1 is present. -- reconstituteBIN should make KD=false
     */
    public void testKnownDeleted()
        throws Throwable {

        createEnvAndDbs(1 << 20, true, NUM_DBS);
        int numRecs = 20;
        try {
            
            /* Set up a repository of expected data */
            Hashtable expectedData = new Hashtable();

            /* Insert data and abort. */
            Transaction txn = env.beginTransaction(null, null);
            insertData(txn, 0, numRecs - 1, expectedData, 1, false, NUM_DBS);

            /* 
             * Add cursors to pin down BINs. Otherwise the checkpoint that 
             * follows will compress away all the values. 
             */
            Cursor [][] cursors = new Cursor[NUM_DBS][numRecs];
            addCursors(cursors);
            txn.abort();

            /* 
             * Force a checkpoint, to flush a full version of the BIN
             * to disk, so the next checkpoint can cause deltas. 
             * These checkpointed BINS have known deleted flags set.
             */
            env.checkpoint(forceConfig);
            removeCursors(cursors);
            

            /* 
             * Insert every other data value, makes some known deleted flags
             * false.
             */
            txn = env.beginTransaction(null, null);
            insertData(txn, 0, numRecs - 1, expectedData, 1,
                       true,  true, NUM_DBS);
            txn.commit();

            /* Delete data and abort, keeps known delete flag true */
            txn = env.beginTransaction(null, null);
            deleteData(txn, expectedData, true, false, NUM_DBS);
            txn.abort();

            /* This checkpoint should write deltas. */
            cursors = new Cursor[NUM_DBS][numRecs/2];
            addCursors(cursors);
            env.checkpoint(forceConfig);
            removeCursors(cursors);
            
            closeEnv();
            List infoList = recoverAndVerify(expectedData, NUM_DBS);

            /* Check that this recovery processed deltas */
            RecoveryInfo firstInfo = (RecoveryInfo) infoList.get(0);
            assertTrue(firstInfo.numBinDeltas > 0);

        } catch (Throwable t) {
            // print stacktrace before trying to clean up files
            t.printStackTrace();
            throw t;
        }
    }
    
    /* Add cursors on each value to prevent compression. */
    private void addCursors(Cursor [][] cursors) 
        throws DatabaseException {

        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();

        /* Pin each record with a cursor. */
        for (int d = 0; d < NUM_DBS; d++) {
            for (int i = 0; i < cursors[d].length; i++) {
                cursors[d][i] = dbs[d].openCursor(null, null);
        
                for (int j = 0; j < i; j++) {
                    OperationStatus status =
                        cursors[d][i].getNext(key, data,
                                              LockMode.READ_UNCOMMITTED);
                    assertEquals(OperationStatus.SUCCESS, status);
                }
            }
        }
    }

    private void removeCursors(Cursor[][] cursors) 
        throws DatabaseException {
        for (int d = 0; d < NUM_DBS; d++) {
            for (int i = 0; i < cursors[d].length; i++) {
                cursors[d][i].close();
            }
        }
    }
}
