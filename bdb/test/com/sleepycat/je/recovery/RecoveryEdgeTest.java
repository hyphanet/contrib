/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: RecoveryEdgeTest.java,v 1.58.2.1 2007/02/01 14:50:17 cwl Exp $
 */

package com.sleepycat.je.recovery;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Hashtable;
import java.util.List;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.log.FileManager;
import com.sleepycat.je.tree.LN;
import com.sleepycat.je.tree.Node;
import com.sleepycat.je.util.StringDbt;
import com.sleepycat.je.util.TestUtils;

public class RecoveryEdgeTest extends RecoveryTestBase {
    public void testNoLogFiles()
        throws Throwable {

        /* Creating an environment runs recovery. */
        EnvironmentImpl env = null;
        try {
            EnvironmentConfig noFileConfig = TestUtils.initEnvConfig();
            /* Don't checkpoint utilization info for this test. */
            DbInternal.setCheckpointUP(noFileConfig, false);
            noFileConfig.setConfigParam(EnvironmentParams.LOG_MEMORY_ONLY.getName(),
                                        "true");
            noFileConfig.setTransactional(true);
            noFileConfig.setAllowCreate(true);
            env = new EnvironmentImpl(envHome, noFileConfig);
            List dbList = env.getDbMapTree().getDbNames();
            assertEquals("no dbs exist", 0, dbList.size());

            /* Fake a shutdown/startup. */
            env.close();
            env = new EnvironmentImpl(envHome, noFileConfig);
            dbList = env.getDbMapTree().getDbNames();
            assertEquals("no dbs exist", 0, dbList.size());
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        } finally {
            if (env != null)
                env.close();
        }
    }

    /**
     * Test setting of the database ids in recovery.
     */
    public void testDbId() 
        throws Throwable {

        Transaction createTxn = null;
        try {

            /* 
             * Create an environment and three databases. The first two
             * ids are allocated to the name db and the id db.
             */
            EnvironmentConfig createConfig = TestUtils.initEnvConfig();
            createConfig.setTransactional(true);
            createConfig.setAllowCreate(true);
            createConfig.setConfigParam(EnvironmentParams.NODE_MAX.getName(), "6");
            env = new Environment(envHome, createConfig);

            int numStartDbs = 1;
            createTxn = env.beginTransaction(null, null);

            /* Check id of each db. */
            DatabaseConfig dbConfig = new DatabaseConfig();
            dbConfig.setTransactional(true);
            dbConfig.setAllowCreate(true);
            for (int i = 0; i < numStartDbs; i++) { 
                Database anotherDb = env.openDatabase(createTxn, "foo" + i,
						      dbConfig);
                assertEquals((i+3),
			     DbInternal.dbGetDatabaseImpl(anotherDb).getId().getId());
                anotherDb.close();
            }
            createTxn.commit();
            env.close();
        
            /* 
             * Go through a set of open, creates, and closes. Check id after
             * recovery.
             */
            EnvironmentConfig envConfig = TestUtils.initEnvConfig();
            envConfig.setTransactional(true);
            createTxn = null;
            for (int i = numStartDbs; i < numStartDbs + 3; i++) {
                env = new Environment(envHome, envConfig);

                createTxn = env.beginTransaction(null, null);
                Database anotherDb = env.openDatabase(createTxn, "foo" + i, 
						      dbConfig);
                assertEquals(i+3,
                             DbInternal.dbGetDatabaseImpl(anotherDb).getId().getId());
                anotherDb.close();
                createTxn.commit();
                env.close();
            }
        } catch (Throwable t) {
            if (createTxn != null) {
                createTxn.abort();
            }
            t.printStackTrace();
            throw t;
        }
    }

    /**
     * Test setting the node ids in recovery.
     */
    public void testNodeId() 
        throws Throwable {

        try {
            /* Create an environment and databases. */
            createEnvAndDbs(1024, true, NUM_DBS);
            Hashtable expectedData = new Hashtable();
            Transaction txn = env.beginTransaction(null, null);
            insertData(txn, 0, 4, expectedData, 1, true, NUM_DBS);
            txn.commit();

            /* Find the largest node id that has been allocated. */
            long maxSeenNodeId = Node.getLastId();

            /* Close the environment, then recover. */
            closeEnv();
            EnvironmentConfig recoveryConfig = TestUtils.initEnvConfig();
            recoveryConfig.setConfigParam(
                           EnvironmentParams.NODE_MAX.getName(), "6");
            recoveryConfig.setConfigParam(
                           EnvironmentParams.ENV_RUN_CLEANER.getName(),
                           "false");
            /* Don't checkpoint utilization info for this test. */
            DbInternal.setCheckpointUP(recoveryConfig, false);
            env = new Environment(envHome, recoveryConfig);
            LN ln = new LN(new byte[0]);
            
            /* Recovery should have initialized the next node id to use */
            assertTrue(maxSeenNodeId + 1 < ln.getNodeId());
            maxSeenNodeId = Node.getLastId();

            /* 
             * One more time -- this recovery will get the node id off the
             * checkpoint of the environment close. This checkpoint records
             * the fact that the node id was bumped forward by the create of
             * the LN above.
             */
            env.close();
            env = new Environment(envHome, recoveryConfig);
            ln = new LN(new byte[0]);
            /*
             * The environment re-opening will increment the node id
             * several times because of the EOF node id.
             */
            assertTrue(maxSeenNodeId+1 <= ln.getNodeId());

        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    /**
     * Test setting the txn id.
     */
    public void testTxnId() 
        throws Throwable {

        try {
            /* Create an environment and databases. */
            createEnvAndDbs(1024, true, NUM_DBS);
            Hashtable expectedData = new Hashtable();

            /* Make txns before and after a checkpoint */
            Transaction txn = env.beginTransaction(null, null);
            insertData(txn, 0, 4, expectedData, 1, true, NUM_DBS);
            txn.commit();
            env.checkpoint(forceConfig);
            txn = env.beginTransaction(null, null);
            insertData(txn, 5, 6, expectedData, 1, false, NUM_DBS);

            /* Find the largest node id that has been allocated. */
            long maxTxnId = txn.getId();
            txn.abort();

            /* Close the environment, then recover. */
            closeEnv();

            EnvironmentConfig recoveryConfig = TestUtils.initEnvConfig();
            recoveryConfig.setConfigParam (EnvironmentParams.ENV_RUN_CLEANER.getName(),
                                           "false");
            recoveryConfig.setTransactional(true);
            env = new Environment(envHome, recoveryConfig);
            
            /* 
             * Check that the next txn id is larger than the last seen.
             * A few txn ids were eaten by AutoTxns during recovery, do
             * a basic check that we didn't eat more than 11.
             */
            txn = env.beginTransaction(null, null);
            createDbs(txn, NUM_DBS);
            assertTrue(maxTxnId < txn.getId());
            assertTrue((txn.getId() - maxTxnId) < 11);

            /*
             * Do something with this txn so a node with it's value shows up in
             * the log.
             */
            insertData(txn, 7, 8, expectedData, 1, false, NUM_DBS);
            long secondMaxTxnId = txn.getId();
            txn.abort();

            /* 
             * One more time -- this recovery will get the txn id off the
             * checkpoint of the second environment creation.
             */
            closeEnv();
            env = new Environment(envHome, recoveryConfig);
            txn = env.beginTransaction(null, null);
            assertTrue(secondMaxTxnId < txn.getId());
            assertTrue((txn.getId() - secondMaxTxnId) < 10);
            txn.abort();
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    /**
     * Test writing a non-transactional db in a transactional environment.
     * Make sure we can recover.
     */
    public void testNonTxnalDb ()
        throws Throwable {

        createEnv(1024, false);
        try {
            /* 
             * Create a database, write into it non-txnally. Should be 
             * allowed
             */
            DatabaseConfig dbConfig = new DatabaseConfig();
            dbConfig.setAllowCreate(true);
            Database dbA = env.openDatabase(null, "NotTxnal", dbConfig);

            DatabaseEntry key = new StringDbt("foo");
            DatabaseEntry data = new StringDbt("bar");
            dbA.put(null, key, data);

            /* close and recover -- the database should still be there
             * because we're shutting down clean.
             */
            dbA.close();
            env.close();
            createEnv(1024, false);

            dbA = env.openDatabase(null, "NotTxnal", null);
            dbA.close();

            /*
             * Create a database, auto commit. Then write a record.
             * The database should exist after recovery.
             */
            dbConfig.setTransactional(true);
            Database dbB = env.openDatabase(null, "Txnal", dbConfig);
            dbB.close();
            dbB = env.openDatabase(null, "Txnal", null);
            dbB.put(null, key, data);
            dbB.close();
            env.close();

            /*
             * Recover. We should see the database. We may or may not see
             * the records.
             */

            createEnv(1024, false);
            List dbNames = env.getDatabaseNames();
            assertEquals(2, dbNames.size());
            assertEquals("Txnal", dbNames.get(1));
            assertEquals("NotTxnal", dbNames.get(0));
            
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        } finally {
            env.close();
        }
    }

    /**
     * Test that we can recover with a bad checksum.
     */
    public void testBadChecksum() 
        throws Throwable {

        try {
            /* Create an environment and databases. */
            createEnvAndDbs(2048, false, 1);
            Hashtable expectedData = new Hashtable();

            /* Make txns before and after a checkpoint */
            Transaction txn = env.beginTransaction(null, null);
            insertData(txn, 0, 4, expectedData, 1, true, 1);
            txn.commit();
            env.checkpoint(forceConfig);

            txn = env.beginTransaction(null, null);
            insertData(txn, 5, 6, expectedData, 1, true, 1);
            txn.commit();

            txn = env.beginTransaction(null, null);
            insertData(txn, 7, 8, expectedData, 1, false, 1);

            /* Close the environment, then recover. */
            closeEnv();

            /* Write some 0's into the last file. */
            writeBadStuffInLastFile();

            recoverAndVerify(expectedData, 1);
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    /**
     * Another bad checksum test. Make sure that there is no checkpoint in the
     * last file so that this recovery will have to read backwards into the
     * previous file. Also recover in read/only mode to make sure we don't
     * process the bad portion of the log.
     */
    public void testBadChecksumReadOnlyReadPastLastFile() 
        throws Throwable {

        try {
            /* Create an environment and databases. */
            createEnvAndDbs(500, false, 1);
            Hashtable expectedData = new Hashtable();

            /* Commit some data, checkpoint. */
            Transaction txn = env.beginTransaction(null, null);
            insertData(txn, 0, 4, expectedData, 1, true, 1);
            txn.commit();
            env.checkpoint(forceConfig);

            /* 
             * Remember how many files we have, so we know where the last
             * checkpoint is.
             */
            String [] suffixes = new String [] {FileManager.JE_SUFFIX};
            String [] fileList = FileManager.listFiles(envHome, suffixes);
            int startingNumFiles = fileList.length;

            /* Now add enough non-committed data to add more files. */
            txn = env.beginTransaction(null, null);
            insertData(txn, 7, 50, expectedData, 1, false, 1);

            /* Close the environment, then recover. */
            closeEnv();

            /* Make sure that we added on files after the checkpoint. */
            fileList = FileManager.listFiles(envHome, suffixes);
            assertTrue(fileList.length > startingNumFiles);

            /* Write some 0's into the last file. */
            writeBadStuffInLastFile();

            recoverROAndVerify(expectedData, 1);
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }
    
    private void writeBadStuffInLastFile() 
        throws IOException {

        String[] files =
            FileManager.listFiles(envHome,
                                  new String [] {FileManager.JE_SUFFIX});
        File lastFile = new File(envHome, files[files.length - 1]);
        RandomAccessFile rw = new RandomAccessFile(lastFile, "rw");

        rw.seek(rw.length()-10);
        rw.writeBytes("000000");
        rw.close();
    }
}
