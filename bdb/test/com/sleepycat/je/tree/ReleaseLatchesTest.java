/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: ReleaseLatchesTest.java,v 1.15.2.1 2007/02/01 14:50:21 cwl Exp $
 */
package com.sleepycat.je.tree;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import com.sleepycat.bind.tuple.IntegerBinding;
import com.sleepycat.je.CheckpointConfig;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.RunRecoveryException;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.latch.LatchSupport;
import com.sleepycat.je.log.FileManager;
import com.sleepycat.je.util.TestUtils;
import com.sleepycat.je.utilint.TestHook;

/**
 * Check that latches are release properly even if we run into read errors.
 */
public class ReleaseLatchesTest extends TestCase {
    private static final boolean DEBUG = false;

    private Environment env;
    private File envHome;
    private Database db;
    private TestDescriptor testActivity;

    /* 
     * The OPERATIONS declared here define the test cases for this test.  Each
     * TestDescriptor describes a particular JE activity. The
     * testCheckLatchLeaks method generates read i/o exceptions during the test
     * descriptor's action, and will check that we come up clean.
     */
    public static TestDescriptor [] OPERATIONS = {

        /* 
         * TestDescriptor params:
         *  - operation name: for debugging
         *  - number of times to generate an exception. For example if N,
         *   the test action will be executed in a loop N times, with an
         *   read/io on read 1, read 2, read 3 ... read n-1
         *  - number of records in the database.
         */
        new TestDescriptor("database put", 6, 30, false) {
            void doAction(ReleaseLatchesTest test, int exceptionCount) 
                throws DatabaseException {

                test.populate(false);
            }

            void reinit(ReleaseLatchesTest test) 
                throws DatabaseException{

                test.closeDb();
            	test.getEnv().truncateDatabase(null, "foo", false);
            }
        },
        new TestDescriptor("cursor scan", 31, 20, false) {
            void doAction(ReleaseLatchesTest test, int exceptionCount) 
		throws DatabaseException {

                test.scan();
            }
        },
        new TestDescriptor("cursor scan duplicates", 23, 3, true) {
            void doAction(ReleaseLatchesTest test, int exceptionCount) 
		throws DatabaseException {

                test.scan();
            }
        },
        new TestDescriptor("database get", 31, 20, false) {
            void doAction(ReleaseLatchesTest test, int exceptionCount) 
                throws DatabaseException {

                test.get();
            }
        },
        new TestDescriptor("database delete", 40, 30, false) {
            void doAction(ReleaseLatchesTest test, int exceptionCount) 
                throws DatabaseException {

                test.delete();
            }

            void reinit(ReleaseLatchesTest test) 
                throws DatabaseException{

                test.populate(false);
            }
        },
        new TestDescriptor("checkpoint", 40, 10, false) {
            void doAction(ReleaseLatchesTest test, int exceptionCount) 
                throws DatabaseException { 

                test.modify(exceptionCount);
                CheckpointConfig config = new CheckpointConfig();
                config.setForce(true);
                if (DEBUG) {
                    System.out.println("Got to checkpoint");
                }
                test.getEnv().checkpoint(config);
            }
        },
        new TestDescriptor("clean", 100, 5, false) {
            void doAction(ReleaseLatchesTest test, int exceptionCount) 
                throws DatabaseException { 

                test.modify(exceptionCount);
                CheckpointConfig config = new CheckpointConfig();
                config.setForce(true);
                if (DEBUG) {
                    System.out.println("Got to cleaning");
                }
                test.getEnv().cleanLog();
            }
        },
        new TestDescriptor("compress", 20, 10, false) {
            void doAction(ReleaseLatchesTest test, int exceptionCount) 
                throws DatabaseException { 

                     test.delete();
                     if (DEBUG) {
                         System.out.println("Got to compress");
                     }
                     test.getEnv().compress();
            }

            void reinit(ReleaseLatchesTest test) 
                throws DatabaseException{

                test.populate(false);
            }
        }
    };

    public static Test suite() {
        TestSuite allTests = new TestSuite();
        for (int i = 0; i < OPERATIONS.length; i++) {
            TestSuite suite = new TestSuite(ReleaseLatchesTest.class);
            Enumeration e = suite.tests();
            while (e.hasMoreElements()) {
                ReleaseLatchesTest t = (ReleaseLatchesTest) e.nextElement();
                t.initTest(OPERATIONS[i]);
                allTests.addTest(t);
            }
        }
        return allTests;
    }

    public ReleaseLatchesTest() {

        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    public void setUp()
	throws IOException, DatabaseException {

        TestUtils.removeFiles("Setup", envHome, FileManager.JE_SUFFIX);
    }

    public void tearDown()
	throws IOException, DatabaseException {

        setName(getName() + ":" + testActivity.getName());
        TestUtils.removeFiles("TearDown", envHome,
                              FileManager.JE_SUFFIX, true);
    }

    private void init(boolean duplicates)
        throws DatabaseException {

        openEnvAndDb();

        populate(duplicates);
        env.checkpoint(null);
        db.close();
        db = null;
        env.close();
        env = null;
    }

    private void openEnvAndDb()
        throws DatabaseException {

        /* 
         * Make an environment with small nodes and no daemons.
         */
        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
	DbInternal.disableParameterValidation(envConfig);
        envConfig.setAllowCreate(true);
        envConfig.setConfigParam(EnvironmentParams.NODE_MAX.getName(), "4");
        envConfig.setConfigParam("je.env.runEvictor", "false");
        envConfig.setConfigParam("je.env.runCheckpointer", "false");
        envConfig.setConfigParam("je.env.runCleaner", "false");
        envConfig.setConfigParam("je.env.runINCompressor", "false");
        envConfig.setConfigParam
            (EnvironmentParams.CLEANER_MIN_UTILIZATION.getName(), "90");
        envConfig.setConfigParam(EnvironmentParams.LOG_FILE_MAX.getName(),
                  Integer.toString(20000));

        env = new Environment(envHome, envConfig);

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
        dbConfig.setSortedDuplicates(true);
        db = env.openDatabase(null, "foo", dbConfig);
    }

    /* Calling close under -ea will check for leaked latches. */
    private void doCloseAndCheckLeaks()
        throws Throwable {

        try {
            if (db != null) {
                db.close();
                db = null;
            }

            if (env != null) {
                env.close();
                env = null;
            }
        } catch (Throwable t) {
            System.out.println("operation = " + testActivity.name);
            t.printStackTrace();
            throw t;
        }
    }
    
    private void closeDb() 
        throws DatabaseException {

        if (db != null) {
            db.close();
            db = null;
        }
    }

    private Environment getEnv() {
        return env;
    }

    private void initTest(TestDescriptor action) {
        this.testActivity = action;
    }

    /* 
     * This is the heart of the unit test. Given a TestDescriptor, run the
     * operation's activity in a loop, generating read i/o exceptions at
     * different points. Check for latch leaks after the i/o exception
     * happens.
     */
    public void testCheckLatchLeaks()
        throws Throwable {

        int maxExceptionCount = testActivity.getNumExceptions();
        if (DEBUG) {
            System.out.println("Starting test: " + testActivity.getName());
        }

        try {
            init(testActivity.getDuplicates());

            /* 
             * Run the action repeatedly, generating exceptions at different
             * points.
             */
            for (int i = 1; i <= maxExceptionCount; i++) {
                
                /* 
                 * Open the env and database anew each time, so that we need to
                 * fault in objects and will trigger read i/o exceptions.
                 */
                openEnvAndDb();
                EnvironmentImpl envImpl =
                    DbInternal.envGetEnvironmentImpl(env);
                boolean exceptionOccurred = false;

                try {
                    ReadIOExceptionHook readHook = new ReadIOExceptionHook(i);
                    envImpl.getLogManager().setReadHook(readHook);
                    testActivity.doAction(this, i);
                } catch (RunRecoveryException e) {

                    /* 
		     * It's possible for a read error to induce a
		     * RunRecoveryException if the read error happens when we
		     * are opening a new write file channel. (We read and
		     * validate the file header). In that case, check for
		     * latches, and re-open the database.
                     */
                    checkLatchCount(e, i);                    
                    env.close();
                    openEnvAndDb();
                    exceptionOccurred = true;
                } catch (DatabaseException e) {
                    checkLatchCount(e, i);
                    exceptionOccurred = true;
                }
                
                if (DEBUG && !exceptionOccurred) {
                    System.out.println("Don't need ex count " + i +
                                       " for test activity " +
                                       testActivity.getName());
                }

                envImpl.getLogManager().setReadHook(null);
                testActivity.reinit(this);
                doCloseAndCheckLeaks();
            }
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    private void checkLatchCount(DatabaseException e,
                                 int exceptionCount) 
        throws DatabaseException {

	/* Only rethrow the exception if we didn't clean up latches. */
        if (LatchSupport.countLatchesHeld() > 0) {
            LatchSupport.dumpLatchesHeld();
            System.out.println("Operation = " + testActivity.getName() +
                               " exception count=" + exceptionCount +
                               " Held latches = " +
                               LatchSupport.countLatchesHeld());
            /* Show stacktrace where the latch was lost. */
            e.printStackTrace();
            throw e;
        }
    }

    /* Insert records into a database. */
    private void populate(boolean duplicates) 
        throws DatabaseException {

        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();
        DatabaseEntry data1 = new DatabaseEntry();
        DatabaseEntry data2 = new DatabaseEntry();
        DatabaseEntry data3 = new DatabaseEntry();
        DatabaseEntry data4 = new DatabaseEntry();
        IntegerBinding.intToEntry(0, data);
        IntegerBinding.intToEntry(1, data1);
        IntegerBinding.intToEntry(2, data2);
        IntegerBinding.intToEntry(3, data3);
        IntegerBinding.intToEntry(4, data4);

        for (int i = 0; i < testActivity.getNumRecords(); i++) {
            IntegerBinding.intToEntry(i, key);
            assertEquals(OperationStatus.SUCCESS,  db.put(null, key, data));
	    if (duplicates) {
		assertEquals(OperationStatus.SUCCESS,
			     db.put(null, key, data1));
		assertEquals(OperationStatus.SUCCESS,
			     db.put(null, key, data2));
		assertEquals(OperationStatus.SUCCESS,
			     db.put(null, key, data3));
		assertEquals(OperationStatus.SUCCESS,
			     db.put(null, key, data4));
	    }
        }
    }

    /* Modify the database. */
    private void modify(int dataVal) 
        throws DatabaseException {

        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();
        IntegerBinding.intToEntry(dataVal, data);

        for (int i = 0; i < testActivity.getNumRecords(); i++) {
            IntegerBinding.intToEntry(i, key);
            assertEquals(OperationStatus.SUCCESS,  db.put(null, key, data));
        }
    }

    /* Cursor scan the data. */
    private void scan()
        throws DatabaseException {

        Cursor cursor = null;
        try {
            cursor = db.openCursor(null, null);
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry data = new DatabaseEntry();

            while (cursor.getNext(key, data, LockMode.DEFAULT) ==
                   OperationStatus.SUCCESS) {
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /* Database.get() for all records. */
    private void get() 
        throws DatabaseException {

        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();
        for (int i = 0; i < testActivity.getNumRecords(); i++) {
            IntegerBinding.intToEntry(i, key);
            assertEquals(OperationStatus.SUCCESS,
                         db.get(null, key, data, LockMode.DEFAULT));
        }
    }

    /* Delete all records. */
    private void delete()
        throws DatabaseException {

        DatabaseEntry key = new DatabaseEntry();
        for (int i = 0; i < testActivity.getNumRecords(); i++) {
            IntegerBinding.intToEntry(i, key);
            assertEquals("key = " + IntegerBinding.entryToInt(key),
                         OperationStatus.SUCCESS, db.delete(null, key));
        }
    }
    /* 
     * This TestHook implementation generates io exceptions during reads.
     */
    static class ReadIOExceptionHook implements TestHook {
        private int counter = 0;
        private int throwCount;
        
        ReadIOExceptionHook(int throwCount) {
            this.throwCount = throwCount;
        }
        
        public void doIOHook()
            throws IOException {

            if (throwCount == counter) {
                counter++;
                throw new IOException("Generated exception: " +
                                      this.getClass().getName());
            } else {
                counter++;
            }
        }

	public void doHook() {}

        public Object getHookValue() {
            return null; 
        }
    }

    static abstract class TestDescriptor {
        private String name;
        private int numExceptions;
        private int numRecords;
	private boolean duplicates;

        TestDescriptor(String name,
		       int numExceptions,
		       int numRecords,
		       boolean duplicates) {
            this.name = name;
            this.numExceptions = numExceptions;
            this.numRecords = numRecords;
	    this.duplicates = duplicates;
        }

        int getNumRecords() {
            return numRecords;
        }

        int getNumExceptions() {
            return numExceptions;
        }
        
        String getName() {
            return name;
        }
            
	boolean getDuplicates() {
	    return duplicates;
	}

        /* Do a series of operations. */
        abstract void doAction(ReleaseLatchesTest test,
                               int exceptionCount)
            throws DatabaseException;

        /* Reinitialize the database if doAction modified it. */
        void reinit(ReleaseLatchesTest test)
	    throws DatabaseException {

	}
    }
}
