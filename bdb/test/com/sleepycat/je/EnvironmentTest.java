/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2006 Oracle.  All rights reserved.
 *
 * $Id: EnvironmentTest.java,v 1.186 2006/12/05 15:56:59 linda Exp $
 */

package com.sleepycat.je;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import junit.framework.TestCase;

import com.sleepycat.je.config.ConfigParam;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.DatabaseImpl;
import com.sleepycat.je.dbi.EnvConfigObserver;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.dbi.DbConfigManager;
import com.sleepycat.je.dbi.MemoryBudget;
import com.sleepycat.je.txn.LockInfo;
import com.sleepycat.je.util.StringDbt;
import com.sleepycat.je.util.TestUtils;
import com.sleepycat.je.utilint.DaemonRunner;
import com.sleepycat.je.utilint.DbLsn;

public class EnvironmentTest extends TestCase {

    private Environment env1;
    private Environment env2;
    private Environment env3;
    private File envHome;

    public EnvironmentTest() {
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    public void setUp()
        throws IOException {

        TestUtils.removeLogFiles("Setup", envHome, false);
    }
    
    public void tearDown()
        throws Exception {

        /* 
	 * Close down environments in case the unit test failed so that the log
	 * files can be removed.
         */
        try {
            if (env1 != null) {
                env1.close();
                env1 = null;
            }
        } catch (DatabaseException e) {
            /* ok, the test closed it */
        }
        try {
            if (env2 != null) {
                env2.close();
                env2 = null;
            }
        } catch (DatabaseException e) {
            /* ok, the test closed it */
        }
        try {
            if (env3 != null) {
                env3.close();
                env3 = null;
            }
        } catch (DatabaseException e) {
            /* ok, the test closed it */
        }

        TestUtils.removeLogFiles("TearDown", envHome, false);
    }

    /**
     * Test open and close of an environment.
     */
    public void testBasic()
        throws Throwable {

        try {
            assertEquals("Checking version", "3.2.13",
                         JEVersion.CURRENT_VERSION.getVersionString());

            EnvironmentConfig envConfig = TestUtils.initEnvConfig();
            envConfig.setTransactional(true);
            envConfig.setCacheSize(MemoryBudget.MIN_MAX_MEMORY_SIZE);
            /* Don't track detail with a tiny cache size. */
            envConfig.setConfigParam
                (EnvironmentParams.CLEANER_TRACK_DETAIL.getName(), "false");
            envConfig.setConfigParam
		(EnvironmentParams.NODE_MAX.getName(), "6");
            envConfig.setConfigParam
		(EnvironmentParams.LOG_MEM_SIZE.getName(),
		 EnvironmentParams.LOG_MEM_SIZE_MIN_STRING);
            envConfig.setConfigParam
		(EnvironmentParams.NUM_LOG_BUFFERS.getName(), "2");
            envConfig.setAllowCreate(true);
            env1 = new Environment(envHome, envConfig);

            env1.close();

            /* Try to open and close again, now that the environment exists. */
            envConfig.setAllowCreate(false);
            env1 = new Environment(envHome, envConfig);
            env1.close();
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    /**
     * Test environment reference counting.
     */
    public void testReferenceCounting()
        throws Throwable {

        try {

            /* Create two environment handles on the same environment. */
            EnvironmentConfig envConfig = TestUtils.initEnvConfig();
            envConfig.setTransactional(true);
            envConfig.setCacheSize(MemoryBudget.MIN_MAX_MEMORY_SIZE);
            /* Don't track detail with a tiny cache size. */
            envConfig.setConfigParam
                (EnvironmentParams.CLEANER_TRACK_DETAIL.getName(), "false");
            envConfig.setConfigParam(EnvironmentParams.NODE_MAX.getName(),
                                     "6");
            envConfig.setConfigParam
		(EnvironmentParams.LOG_MEM_SIZE.getName(),
		 EnvironmentParams.LOG_MEM_SIZE_MIN_STRING);
            envConfig.setConfigParam
		(EnvironmentParams.NUM_LOG_BUFFERS.getName(), "2");
            envConfig.setAllowCreate(true);
            env1 = new Environment(envHome, envConfig);
            envConfig.setAllowCreate(false);
            env2 = new Environment(envHome, envConfig);

            assertEquals("DbEnvironments should be equal",
                         env1.getEnvironmentImpl(),
                         env2.getEnvironmentImpl());

            /* Try to close one of them twice */
            env1.close();
            try {
                env1.close();
                fail("Didn't catch DatabaseException");
            } catch (DatabaseException DENOE) {
            }

            /* 
	     * Close both, open a third handle, should get a new
	     * EnvironmentImpl.
             */
            EnvironmentImpl dbenv1 = env1.getEnvironmentImpl();
            env2.close();
            env1 = new Environment(envHome, envConfig);
            assertTrue("EnvironmentImpl did not change",
                       dbenv1 != env1.getEnvironmentImpl());
            try {
                env2.close();
                fail("Didn't catch DatabaseException");
            } catch (DatabaseException DENOE) {
            }
            env1.close();
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    public void testTransactional()
        throws Throwable {

        try {
            EnvironmentConfig envConfig = TestUtils.initEnvConfig();
            envConfig.setAllowCreate(true);
            env1 = new Environment(envHome, envConfig);

            try {
                env1.beginTransaction(null, null);
                fail("should have thrown exception for non transactional "+
                     " environment");
            } catch (DatabaseException DBE) {
            }

            String databaseName = "simpleDb";
            DatabaseConfig dbConfig = new DatabaseConfig();
            dbConfig.setAllowCreate(true);
            dbConfig.setTransactional(true);
            try {
                env1.openDatabase(null, databaseName, dbConfig);
                fail("expected DatabaseException since Environment not " +
                     "transactional");
            } catch (DatabaseException DBE) {
            }

            env1.close();
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    public void testReadOnly()
        throws Throwable {

        try {
            EnvironmentConfig envConfig = TestUtils.initEnvConfig();
            envConfig.setReadOnly(true);
            envConfig.setAllowCreate(true);
            env1 = new Environment(envHome, envConfig);

            DatabaseConfig dbConfig = new DatabaseConfig();
            dbConfig.setAllowCreate(true);
            dbConfig.setTransactional(true);
            String databaseName = "simpleDb";
            try {
                env1.openDatabase(null, databaseName, dbConfig);
                fail("expected DatabaseException since Environment is " +
                     "readonly");
            } catch (DatabaseException DBE) {
                // expected.
            }

            env1.close();
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    /*
     * Tests memOnly mode with a home dir that does not exist. [#15255]
     */
    public void testMemOnly()
        throws Throwable {

        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setAllowCreate(true);
        envConfig.setTransactional(true);
        envConfig.setConfigParam
            (EnvironmentParams.LOG_MEMORY_ONLY.getName(), "true");

        File noHome = new File("fileDoesNotExist");
        assertTrue(!noHome.exists());
        env1 = new Environment(noHome, envConfig);

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
        dbConfig.setTransactional(true);
        Database db = env1.openDatabase(null, "foo", dbConfig);

        Transaction txn = env1.beginTransaction(null, null);
        Cursor cursor = db.openCursor(txn, null);
        doSimpleCursorPutAndDelete(cursor, false);
        cursor.close();
        txn.commit();
        db.close();

        env1.close();
        assertTrue(!noHome.exists());
    }

    /**
     * Tests that opening an environment after a clean close does not add to
     * the log, but that we do initialize the LastFirstActiveLSN.
     */
    public void testOpenWithoutCheckpoint()
        throws Throwable {

        /* Open, close, open. */
        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setAllowCreate(true);
        env1 = new Environment(envHome, envConfig);
        env1.close();
        env1 = new Environment(envHome, null);

        /* Check that no checkpoint was performed. */
        EnvironmentStats stats = env1.getStats(null);
        assertEquals(0, stats.getNCheckpoints());

        /* Check that the FirstActiveLSN is available for the cleaner. */
        long lsn =
            env1.getEnvironmentImpl().getCheckpointer().getFirstActiveLsn();
        assertTrue(DbLsn.compareTo(lsn, DbLsn.makeLsn(0, 0)) > 0);

        env1.close();
        env1 = null;
    }

    /**
     * Test environment configuration.
     */
    public void testConfig()
        throws Throwable {

        /* This tests assumes these props are immutable. */
        assertTrue(!isMutableConfig("je.lock.timeout"));
        assertTrue(!isMutableConfig("je.env.isReadOnly"));

        try {

            /* 
             * Make sure that the environment keeps its own copy of the
             * configuration object.
             */
            EnvironmentConfig envConfig = TestUtils.initEnvConfig();
            envConfig.setReadOnly(true);
            envConfig.setAllowCreate(true);
            envConfig.setLockTimeout(7777);
            env1 = new Environment(envHome, envConfig);

            /* 
             * Change the environment config object, make sure the
             * environment cloned a copy when it was opened.
             */
            envConfig.setReadOnly(false);
            EnvironmentConfig retrievedConfig1 = env1.getConfig();
            assertTrue(envConfig != retrievedConfig1);
            assertEquals(true, retrievedConfig1.getReadOnly());
            assertEquals(true, retrievedConfig1.getAllowCreate());
            assertEquals(7777, retrievedConfig1.getLockTimeout());
            
            /* 
             * Make sure that the environment returns a cloned config
             * object when you call Environment.getConfig.
             */
            retrievedConfig1.setReadOnly(false);
            EnvironmentConfig retrievedConfig2 = env1.getConfig();
            assertEquals(true, retrievedConfig2.getReadOnly());
            assertTrue(retrievedConfig1 != retrievedConfig2);

            /*
             * Open a second environment handle, check that it's attributes
             * are available.
             */
            env2 = new Environment(envHome, null);
            EnvironmentConfig retrievedConfig3 = env2.getConfig();
            assertEquals(true, retrievedConfig3.getReadOnly());
            assertEquals(7777, retrievedConfig3.getLockTimeout());

            /*
             * Open an environment handle on an existing environment with
             * mismatching config params.
             */
            try {
                new Environment(envHome, TestUtils.initEnvConfig());
                fail("Shouldn't open, config param has wrong number of params");
            } catch (IllegalArgumentException e) {
                /* expected */
            }

            try {
                envConfig.setLockTimeout(8888);
                new Environment(envHome, envConfig);
                fail("Shouldn't open, cache size doesn't match"); 
            } catch (IllegalArgumentException e) {
                /* expected */
            }
            
            /*
             * Ditto for the mutable attributes.
             */
            EnvironmentMutableConfig mutableConfig =
                new EnvironmentMutableConfig();
            mutableConfig.setTxnNoSync(true);
            env1.setMutableConfig(mutableConfig);
            EnvironmentMutableConfig retrievedMutableConfig1 =
                env1.getMutableConfig();
            assertTrue(mutableConfig != retrievedMutableConfig1);
            retrievedMutableConfig1.setTxnNoSync(false);
            EnvironmentMutableConfig retrievedMutableConfig2 =
                env1.getMutableConfig();
            assertEquals(true, retrievedMutableConfig2.getTxnNoSync());
            assertTrue(retrievedMutableConfig1 != retrievedMutableConfig2);

            /*
             * Plus check that mutables can be retrieved via the main config.
             */
            EnvironmentConfig retrievedConfig4 = env1.getConfig();
            assertEquals(true, retrievedConfig4.getTxnNoSync());
            retrievedConfig4 = env2.getConfig();
            assertEquals(false, retrievedConfig4.getTxnNoSync());

            /*
             * Check that mutables can be passed to the ctor.
             */
            EnvironmentConfig envConfig3 = env2.getConfig();
            assertEquals(false, envConfig3.getTxnNoSync());
            envConfig3.setTxnNoSync(true);
            env3 = new Environment(envHome, envConfig3);
            EnvironmentMutableConfig retrievedMutableConfig3 =
                env3.getMutableConfig();
            assertNotSame(envConfig3, retrievedMutableConfig3);
            assertEquals(true, retrievedMutableConfig3.getTxnNoSync());
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    /**
     * Test the semantics of env-wide mutable config properties.
     */
    public void testMutableConfig()
        throws DatabaseException {

        /*
         * Note that during unit testing the shared je.properties is expected
         * to be empty, so we don't test the application of je.properties here.
         */
        final String P1 = EnvironmentParams.ENV_RUN_INCOMPRESSOR.getName();
        final String P2 = EnvironmentParams.ENV_RUN_CLEANER.getName();
        final String P3 = EnvironmentParams.ENV_RUN_CHECKPOINTER.getName();

        assertTrue(isMutableConfig(P1));
        assertTrue(isMutableConfig(P2));
        assertTrue(isMutableConfig(P3));

        EnvironmentConfig config;
        EnvironmentMutableConfig mconfig;

        /*
         * Create env1, first handle.
         * P1 defaults to true.
         * P2 is set to true (the default).
         * P3 is set to false (not the default).
         */
        config = TestUtils.initEnvConfig();
        config.setAllowCreate(true);
        config.setConfigParam(P2, "true");
        config.setConfigParam(P3, "false");
        env1 = new Environment(envHome, config);
        check3Params(env1, P1, "true", P2, "true", P3, "false");

        MyObserver observer = new MyObserver();
        env1.getEnvironmentImpl().addConfigObserver(observer);
        assertEquals(0, observer.testAndReset());

        /*
         * Open env2, second handle, test that no mutable params can be
         * overridden.
         * P1 is set to false.
         * P2 is set to false.
         * P3 is set to true.
         */
        config = TestUtils.initEnvConfig();
        config.setConfigParam(P1, "false");
        config.setConfigParam(P2, "false");
        config.setConfigParam(P3, "true");
        env2 = new Environment(envHome, config);
        assertEquals(0, observer.testAndReset());
        check3Params(env1, P1, "true", P2, "true", P3, "false");

        /*
         * Set mutable config explicitly.
         */
        mconfig = env2.getMutableConfig();
        mconfig.setConfigParam(P1, "false");
        mconfig.setConfigParam(P2, "false");
        mconfig.setConfigParam(P3, "true");
        env2.setMutableConfig(mconfig);
        assertEquals(1, observer.testAndReset());
        check3Params(env2, P1, "false", P2, "false", P3, "true");

        env1.close();
        env1 = null;
        env2.close();
        env2 = null;
    }

    /**
     * Checks that je.txn.deadlockStackTrace is mutable and takes effect.
     */
    public void testTxnDeadlockStackTrace()
        throws DatabaseException {

        String name = EnvironmentParams.TXN_DEADLOCK_STACK_TRACE.getName();
        assertTrue(isMutableConfig(name));

        EnvironmentConfig config = TestUtils.initEnvConfig();
        config.setAllowCreate(true);
        config.setConfigParam(name, "true");
        env1 = new Environment(envHome, config);
        assertTrue(LockInfo.getDeadlockStackTrace());

        EnvironmentMutableConfig mconfig = env1.getMutableConfig();
        mconfig.setConfigParam(name, "false");
        env1.setMutableConfig(mconfig);
        assertTrue(!LockInfo.getDeadlockStackTrace());

        mconfig = env1.getMutableConfig();
        mconfig.setConfigParam(name, "true");
        env1.setMutableConfig(mconfig);
        assertTrue(LockInfo.getDeadlockStackTrace());

        env1.close();
        env1 = null;
    }

    /**
     * Checks three config parameter values.
     */
    private void check3Params(Environment env,
                              String p1, String v1,
                              String p2, String v2,
                              String p3, String v3)
        throws DatabaseException {

        EnvironmentConfig config = env.getConfig();

        assertEquals(v1, config.getConfigParam(p1));
        assertEquals(v2, config.getConfigParam(p2));
        assertEquals(v3, config.getConfigParam(p3));

        EnvironmentMutableConfig mconfig = env.getMutableConfig();

        assertEquals(v1, mconfig.getConfigParam(p1));
        assertEquals(v2, mconfig.getConfigParam(p2));
        assertEquals(v3, mconfig.getConfigParam(p3));
    }

    /**
     * Returns whether a config parameter is mutable.
     */
    private boolean isMutableConfig(String name) {
        ConfigParam param = (ConfigParam)
            EnvironmentParams.SUPPORTED_PARAMS.get(name);
        assert param != null;
        return param.isMutable();
    }

    /**
     * Observes config changes and remembers how many times it was called.
     */
    private static class MyObserver implements EnvConfigObserver {

        private int count = 0;
        
        public void envConfigUpdate(DbConfigManager mgr) {
            count += 1;
        }

        int testAndReset() {
            int result = count;
            count = 0;
            return result;
        }
    }

    /**
     * Make sure that config param loading follows the right precedence.
     */
    public void testParamLoading()
	throws Throwable {

        File testEnvHome = null;
        try {

            /* 
             * A je.properties file has been put into
             * <testdestdir>/propTest/je.properties
             */
            StringBuffer testPropsEnv = new StringBuffer();
            testPropsEnv.append(System.getProperty(TestUtils.DEST_DIR));
            testPropsEnv.append(File.separatorChar);
            testPropsEnv.append("propTest");
            testEnvHome = new File(testPropsEnv.toString());
            TestUtils.removeLogFiles("testParamLoading start",
                                     testEnvHome, false);

            /*
             * Set some configuration params programatically.  Do not use
             * TestUtils.initEnvConfig since we're counting properties.
             */
            EnvironmentConfig appConfig = new EnvironmentConfig();
            appConfig.setConfigParam("je.log.numBuffers", "88");
            appConfig.setConfigParam
		("je.log.totalBufferBytes",
		 EnvironmentParams.LOG_MEM_SIZE_MIN_STRING + 10);
            appConfig.setAllowCreate(true);

            Environment appEnv = new Environment(testEnvHome, appConfig);
            EnvironmentConfig envConfig = appEnv.getConfig();
    
            assertEquals(3, envConfig.getNumExplicitlySetParams());
            assertEquals("false",
                         envConfig.getConfigParam("je.env.recovery"));
            assertEquals("7001",
                         envConfig.getConfigParam("je.log.totalBufferBytes"));
            assertEquals("200",
                         envConfig.getConfigParam("je.log.numBuffers"));
            appEnv.close();
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
        finally {
            TestUtils.removeLogFiles("testParamLoadingEnd",
                                     testEnvHome, false);
        }
    }

    public void testDbRename()
        throws Throwable {

        try {
            EnvironmentConfig envConfig = TestUtils.initEnvConfig();
            envConfig.setTransactional(true);
            envConfig.setAllowCreate(true);
            env1 = new Environment(envHome, envConfig);

            String databaseName = "simpleDb";
            String newDatabaseName = "newSimpleDb";

            /* Try to rename a non-existent db. */
            try {
                env1.renameDatabase(null, databaseName, newDatabaseName);
                fail("Rename on non-existent db should fail");
            } catch (DatabaseException e) {
                /* expect exception */
            }

            /* Now create a test db. */
            DatabaseConfig dbConfig = new DatabaseConfig();
            dbConfig.setAllowCreate(true);
            dbConfig.setTransactional(true);
            Database exampleDb = env1.openDatabase(null, databaseName,
						   dbConfig);

            Transaction txn = env1.beginTransaction(null, null);
            Cursor cursor = exampleDb.openCursor(txn, null);
            doSimpleCursorPutAndDelete(cursor, false);
            cursor.close();
            txn.commit();
            exampleDb.close();

            dbConfig.setAllowCreate(false);
            env1.renameDatabase(null, databaseName, newDatabaseName);
            exampleDb = env1.openDatabase(null, newDatabaseName, dbConfig);
            cursor = exampleDb.openCursor(null, null);
            // XXX doSimpleVerification(cursor);
            cursor.close();

            /* Check debug name. */
            DatabaseImpl dbImpl = DbInternal.dbGetDatabaseImpl(exampleDb);
            assertEquals(newDatabaseName, dbImpl.getDebugName());
            exampleDb.close();
            try {
                exampleDb = env1.openDatabase(null, databaseName, dbConfig);
                fail("didn't get db not found exception");
            } catch (DatabaseException DBE) {
            }
            env1.close();
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    public void testDbRenameCommit()
        throws Throwable {

        try {
            EnvironmentConfig envConfig = TestUtils.initEnvConfig();
            envConfig.setTransactional(true);
            envConfig.setAllowCreate(true);
            env1 = new Environment(envHome, envConfig);

            String databaseName = "simpleRenameCommitDb";
            String newDatabaseName = "newSimpleRenameCommitDb";

            Transaction txn = env1.beginTransaction(null, null);
            DatabaseConfig dbConfig = new DatabaseConfig();
            dbConfig.setTransactional(true);
            dbConfig.setAllowCreate(true);
            Database exampleDb = env1.openDatabase(txn, databaseName,
						   dbConfig);

            Cursor cursor = exampleDb.openCursor(txn, null);
            doSimpleCursorPutAndDelete(cursor, false);
            cursor.close();
            exampleDb.close();

            dbConfig.setAllowCreate(false);
            env1.renameDatabase(txn, databaseName, newDatabaseName);
            exampleDb = env1.openDatabase(txn, newDatabaseName, dbConfig);
            cursor = exampleDb.openCursor(txn, null);
            cursor.close();
            exampleDb.close();
            try {
                exampleDb = env1.openDatabase(txn, databaseName, dbConfig);
                fail("didn't get db not found exception");
            } catch (DatabaseException DBE) {
            }
            txn.commit();

            try {
                exampleDb = env1.openDatabase(null, databaseName, null);
                fail("didn't catch DatabaseException opening old name");
            } catch (DatabaseException DBE) {
            }
            try {
                exampleDb = env1.openDatabase(null, newDatabaseName, null);
                exampleDb.close();
            } catch (DatabaseException DBE) {
                fail("caught unexpected exception");
            }

            env1.close();
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    public void testDbRenameAbort()
        throws Throwable {

        try {
            EnvironmentConfig envConfig = TestUtils.initEnvConfig();
            envConfig.setTransactional(true);
            envConfig.setAllowCreate(true);
            env1 = new Environment(envHome, envConfig);

            /* Create a database. */
            String databaseName = "simpleRenameAbortDb";
            String newDatabaseName = "newSimpleRenameAbortDb";
            Transaction txn = env1.beginTransaction(null, null);
            DatabaseConfig dbConfig = new DatabaseConfig();
            dbConfig.setTransactional(true);
            dbConfig.setAllowCreate(true);
            Database exampleDb =
		env1.openDatabase(txn, databaseName, dbConfig);

            /* Put some data in, close the database, commit. */
            Cursor cursor = exampleDb.openCursor(txn, null);
            doSimpleCursorPutAndDelete(cursor, false);
            cursor.close();
            exampleDb.close();
            txn.commit();

            /* 
             * Rename under another txn, shouldn't be able to open under the
             * old name.
             */
            txn = env1.beginTransaction(null, null);
            env1.renameDatabase(txn, databaseName, newDatabaseName);
            dbConfig.setAllowCreate(false);
            exampleDb = env1.openDatabase(txn, newDatabaseName, dbConfig);
            cursor = exampleDb.openCursor(txn, null);
            // XXX doSimpleVerification(cursor);
            cursor.close();
            exampleDb.close();
            try {
                exampleDb = env1.openDatabase(txn, databaseName, dbConfig);
                fail("didn't get db not found exception");
            } catch (DatabaseException DBE) {
            }

            /* 
             * Abort the rename, should be able to open under the old name with
             * empty props (DB_CREATE not set)
             */
            txn.abort();
            exampleDb = new Database(env1);
            try {
                exampleDb = env1.openDatabase(null, databaseName, null);
                exampleDb.close();
            } catch (DatabaseException dbe) {
                fail("caught DatabaseException opening old name:" +
                     dbe.getMessage());
            }

            /* Shouldn't be able to open under the new name. */
            try {
                exampleDb = env1.openDatabase(null, newDatabaseName, null);
                fail("didn't catch DatabaseException opening new name");
            } catch (DatabaseException dbe) {
            }

            env1.close();
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    public void testDbRemove()
        throws Throwable {

        try {        
            /* Set up an environment. */
            EnvironmentConfig envConfig = TestUtils.initEnvConfig();
            envConfig.setTransactional(true);
            envConfig.setAllowCreate(true);
            env1 = new Environment(envHome, envConfig);

            String databaseName = "simpleDb";

            /* Try to remove a non-existent db */
            try {
                env1.removeDatabase(null, databaseName);
                fail("Remove of non-existent db should fail");
            } catch (DatabaseException e) {
                /* expect exception */
            }

            DatabaseConfig dbConfig = new DatabaseConfig();
            dbConfig.setTransactional(true);
            dbConfig.setAllowCreate(true);
            Database exampleDb =
		env1.openDatabase(null, databaseName, dbConfig);

            Transaction txn = env1.beginTransaction(null, null);
            Cursor cursor = exampleDb.openCursor(txn, null);
            doSimpleCursorPutAndDelete(cursor, false);
            cursor.close();
            txn.commit();

            /* Remove should fail because database is open. */
            try {
                env1.removeDatabase(null, databaseName);
                fail("didn't get db open exception");
            } catch (DatabaseException DBE) {
            }
            exampleDb.close();

            env1.removeDatabase(null, databaseName);

            /* Remove should fail because database does not exist. */
            try {
                exampleDb = env1.openDatabase(null, databaseName, null);
                fail("did not catch db does not exist exception");
            } catch (DatabaseException DBE) {
            }
            env1.close();
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    public void testDbRemoveCommit()
        throws Throwable {

        try {        
            /* Set up an environment. */
            EnvironmentConfig envConfig = TestUtils.initEnvConfig();
            envConfig.setTransactional(true);
            envConfig.setAllowCreate(true);
            env1 = new Environment(envHome, envConfig);

            /* Make a database. */
            String databaseName = "simpleDb";
            Transaction txn = env1.beginTransaction(null, null);
            DatabaseConfig dbConfig = new DatabaseConfig();
            dbConfig.setTransactional(true);
            dbConfig.setAllowCreate(true);
            Database exampleDb =
		env1.openDatabase(txn, databaseName, dbConfig);

            /* Insert and delete data in it. */
	    Cursor cursor = exampleDb.openCursor(txn, null);
	    doSimpleCursorPutAndDelete(cursor, false);
	    cursor.close();

	    /* 
	     * Try a remove without closing the open Database handle.  Should
             * get an exception.
	     */
            try {
                env1.removeDatabase(txn, databaseName);
                fail("didn't get db open exception");
            } catch (DatabaseException DBE) {
            }
            exampleDb.close();

            /* Do a remove, try to open again. */
            env1.removeDatabase(txn, databaseName);
            try {
                dbConfig.setAllowCreate(false);
                exampleDb = env1.openDatabase(txn, databaseName, dbConfig);
                fail("did not catch db does not exist exception");
            } catch (DatabaseException DBE) {
            }
            txn.commit();

            /* Try to open, the db should have been removed. */
            try {
                exampleDb = env1.openDatabase(null, databaseName, null);
                fail("did not catch db does not exist exception");
            } catch (DatabaseException DBE) {
            }
            env1.close();
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    public void testDbRemoveAbort()
        throws Throwable {

        try {        
            /* Set up an environment. */
            EnvironmentConfig envConfig = TestUtils.initEnvConfig();
            envConfig.setTransactional(true);
            envConfig.setAllowCreate(true);
            env1 = new Environment(envHome, envConfig);

            /* Create a database, commit. */
            String databaseName = "simpleDb";
            Transaction txn = env1.beginTransaction(null, null);
            DatabaseConfig dbConfig = new DatabaseConfig();
            dbConfig.setTransactional(true);
            dbConfig.setAllowCreate(true);
            Database exampleDb =
		env1.openDatabase(txn, databaseName, dbConfig);
            txn.commit();

            /* Start a new txn and put some data in the created db. */
            txn = env1.beginTransaction(null, null);
            Cursor cursor = exampleDb.openCursor(txn, null);
            doSimpleCursorPutAndDelete(cursor, false);
            cursor.close();

            /* 
	     * Try to remove, we should get an exception because the db is
	     * open.
             */
            try {
                env1.removeDatabase(txn, databaseName);
                fail("didn't get db open exception");
            } catch (DatabaseException DBE) {
            }
            exampleDb.close();

	    /* 
	     * txn can only be aborted at this point since the removeDatabase()
	     * timed out.
	     */
	    txn.abort();
            txn = env1.beginTransaction(null, null);
            env1.removeDatabase(txn, databaseName);

            try {
                dbConfig.setAllowCreate(false);
                exampleDb = env1.openDatabase(txn, databaseName, dbConfig);
                fail("did not catch db does not exist exception");
            } catch (DatabaseException DBE) {
            }

            /* Abort, should rollback the db remove. */
            txn.abort();

            try {
                DatabaseConfig dbConfig2 = new DatabaseConfig();
                dbConfig2.setTransactional(true);
                exampleDb = env1.openDatabase(null, databaseName, dbConfig2);
            } catch (DatabaseException DBE) {
                fail("db does not exist anymore after delete/abort");
            }

            exampleDb.close();
            env1.close();
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    /**
     * Provides general testing of getDatabaseNames.  Additionally verifies a
     * fix for a bug that occurred when the first DB (lowest valued name) was
     * removed or renamed prior to calling getDatabaseNames.  A NPE occurred
     * in this case if the compressor had not yet deleted the BIN entry for
     * the removed/renamed name. [#13377]
     */
    public void testGetDatabaseNames()
        throws DatabaseException {

        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setAllowCreate(true);
        envConfig.setConfigParam
            (EnvironmentParams.ENV_RUN_INCOMPRESSOR.getName(), "false");

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);

        /* Start with no databases. */
        Set dbNames = new HashSet();
        env1 = new Environment(envHome, envConfig);
        checkDbNames(dbNames, env1.getDatabaseNames());

        /* Add DB1. */
        dbNames.add("DB1");
        Database db = env1.openDatabase(null, "DB1", dbConfig);
        db.close();
        checkDbNames(dbNames, env1.getDatabaseNames());

        /* Add DB2. */
        dbNames.add("DB2");
        db = env1.openDatabase(null, "DB2", dbConfig);
        db.close();
        checkDbNames(dbNames, env1.getDatabaseNames());

        /* Rename DB2 to DB3 (this caused NPE). */
        dbNames.remove("DB2");
        dbNames.add("DB3");
        env1.renameDatabase(null, "DB2", "DB3");
        checkDbNames(dbNames, env1.getDatabaseNames());

        /* Remove DB1. */
        dbNames.remove("DB1");
        dbNames.add("DB4");
        env1.renameDatabase(null, "DB1", "DB4");
        checkDbNames(dbNames, env1.getDatabaseNames());

        /* Add DB0. */
        dbNames.add("DB0");
        db = env1.openDatabase(null, "DB0", dbConfig);
        db.close();
        checkDbNames(dbNames, env1.getDatabaseNames());

        /* Remove DB0 (this caused NPE). */
        dbNames.remove("DB0");
        env1.removeDatabase(null, "DB0");
        checkDbNames(dbNames, env1.getDatabaseNames());

        env1.close();
        env1 = null;
    }

    /**
     * Checks that the expected set of names equals the list of names returned
     * from getDatabaseNames.  A list can't be directly compared to a set using
     * equals().
     */
    private void checkDbNames(Set expected, List actual) {
        assertEquals(expected.size(), actual.size());
        assertEquals(expected, new HashSet(actual));
    }

    /*
     * This little test case can only invoke the compressor, since the evictor,
     * cleaner and checkpointer are all governed by utilization metrics and are
     * tested elsewhere.
     */
    public void testDaemonManualInvocation()
        throws Throwable {

        try {        
            /* Set up an environment. */
            EnvironmentConfig envConfig = TestUtils.initEnvConfig();
            envConfig.setTransactional(true);
            String testPropVal = "120000000";
            envConfig.setConfigParam
                (EnvironmentParams.COMPRESSOR_WAKEUP_INTERVAL.getName(),
                 testPropVal);
            envConfig.setConfigParam
                (EnvironmentParams.ENV_RUN_INCOMPRESSOR.getName(), "false");
            envConfig.setAllowCreate(true);
            envConfig.setConfigParam
		(EnvironmentParams.LOG_MEM_SIZE.getName(), "20000");
            envConfig.setConfigParam
		(EnvironmentParams.NUM_LOG_BUFFERS.getName(), "2");
            env1 = new Environment(envHome, envConfig);

            String databaseName = "simpleDb";
            DatabaseConfig dbConfig = new DatabaseConfig();
            dbConfig.setTransactional(true);
            dbConfig.setAllowCreate(true);
            Database exampleDb =
		env1.openDatabase(null, databaseName, dbConfig);

            Transaction txn = env1.beginTransaction(null, null);
            Cursor cursor = exampleDb.openCursor(txn, null);
            doSimpleCursorPutAndDelete(cursor, false);
            cursor.close();
            txn.commit();
            exampleDb.close();
            EnvironmentStats envStats = env1.getStats(TestUtils.FAST_STATS);
            env1.compress();

            envStats = env1.getStats(TestUtils.FAST_STATS);
            int compressorTotal =
                envStats.getSplitBins() +
                envStats.getDbClosedBins() +
                envStats.getCursorsBins() +
                envStats.getNonEmptyBins() +
                envStats.getProcessedBins() +
                envStats.getInCompQueueSize();
            assertTrue(compressorTotal > 0);

            env1.close();
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    /**
     * Tests that each daemon can be turned on and off dynamically.
     */
    public void testDaemonRunPause()
        throws DatabaseException, InterruptedException {

        final String[] runProps = {
            EnvironmentParams.ENV_RUN_EVICTOR.getName(),
            EnvironmentParams.ENV_RUN_CLEANER.getName(),
            EnvironmentParams.ENV_RUN_CHECKPOINTER.getName(),
            EnvironmentParams.ENV_RUN_INCOMPRESSOR.getName(),
        };

        EnvironmentConfig config = TestUtils.initEnvConfig();
        config.setAllowCreate(true);

        config.setConfigParam
            (EnvironmentParams.MAX_MEMORY.getName(),
             MemoryBudget.MIN_MAX_MEMORY_SIZE_STRING);
        /* Don't track detail with a tiny cache size. */
        config.setConfigParam
            (EnvironmentParams.CLEANER_TRACK_DETAIL.getName(), "false");
        config.setConfigParam
            (EnvironmentParams.CLEANER_BYTES_INTERVAL.getName(),
             "100");
        config.setConfigParam
            (EnvironmentParams.CHECKPOINTER_BYTES_INTERVAL.getName(),
             "100");
        config.setConfigParam
            (EnvironmentParams.COMPRESSOR_WAKEUP_INTERVAL.getName(),
             "1000000");
	config.setConfigParam(EnvironmentParams.LOG_MEM_SIZE.getName(),
			      EnvironmentParams.LOG_MEM_SIZE_MIN_STRING);
	config.setConfigParam
	    (EnvironmentParams.NUM_LOG_BUFFERS.getName(), "2");
        setBoolConfigParams(config, runProps,
                            new boolean[] { false, false, false, false });

        env1 = new Environment(envHome, config);
        EnvironmentImpl envImpl = env1.getEnvironmentImpl();

        final DaemonRunner[] daemons = {
            envImpl.getEvictor(),
            envImpl.getCleaner(),
            envImpl.getCheckpointer(),
            envImpl.getINCompressor(),
        };

        doTestDaemonRunPause(env1, daemons, runProps,
                             new boolean[] { false, false, false, false });
        doTestDaemonRunPause(env1, daemons, runProps,
                             new boolean[] { true,  false, false, false });
	if (!envImpl.isNoLocking()) {
	    doTestDaemonRunPause(env1, daemons, runProps,
				 new boolean[] { false, true,  false, false });
	}
        doTestDaemonRunPause(env1, daemons, runProps,
                             new boolean[] { false, false, true,  false });
        doTestDaemonRunPause(env1, daemons, runProps,
                             new boolean[] { false, false, false, true  });
        doTestDaemonRunPause(env1, daemons, runProps,
                             new boolean[] { false, false, false, false });

        env1.close();
        env1 = null;
    }

    /**
     * Tests a set of daemon on/off settings.
     */
    private void doTestDaemonRunPause(Environment env,
				      DaemonRunner[] daemons,
                                      String[] runProps,
				      boolean[] runValues)
        throws DatabaseException, InterruptedException {

        /* Set daemon run properties. */
        EnvironmentMutableConfig config = env.getMutableConfig();
        setBoolConfigParams(config, runProps, runValues);
        env.setMutableConfig(config);

        /* Allow previously running daemons to come to a stop. */
        for (int i = 0; i < 10; i += 1) {
            Thread.yield();
            Thread.sleep(10);
        }

        /* Get current wakeup counts. */
        int[] prevCounts = new int[daemons.length];
        for (int i = 0; i < prevCounts.length; i += 1) {
            prevCounts[i] = daemons[i].getNWakeupRequests();
        }

        /* Write some data to wakeup the checkpointer, cleaner and evictor. */
        String dbName = "testDaemonRunPause";
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
	dbConfig.setSortedDuplicates(true);
        Database db = env1.openDatabase(null, dbName, dbConfig);
        Cursor cursor = db.openCursor(null, null);
        doSimpleCursorPutAndDelete(cursor, true);
        cursor.close();
        db.close();

        /* Sleep for a while to wakeup the compressor. */
        Thread.sleep(1000);

        /* Check that the expected daemons were woken. */
        for (int i = 0; i < prevCounts.length; i += 1) {
            int currNWakeups = daemons[i].getNWakeupRequests();
            boolean woken = prevCounts[i] < currNWakeups;
            assertEquals(daemons[i].getClass().getName() +
                         " prevNWakeups=" + prevCounts[i] +
                         " currNWakeups=" + currNWakeups,
                         runValues[i], woken);
        }
    }

    private void setBoolConfigParams(EnvironmentMutableConfig config,
                                     String[] names,
				     boolean[] values) {
        for (int i = 0; i < names.length; i += 1) {
            config.setConfigParam(names[i],
                                  Boolean.valueOf(values[i]).toString());
        }
    }

    public void testClose()
        throws Throwable {

        try {
            EnvironmentConfig envConfig = TestUtils.initEnvConfig();
            envConfig.setTransactional(true);
            envConfig.setAllowCreate(true);
            env1 = new Environment(envHome, envConfig);

            env1.close();
            try {
                env1.close();
                fail("Didn't catch DatabaseException");
            } catch (DatabaseException DENOE) {
            }

            envConfig.setAllowCreate(false);
            env1 = new Environment(envHome, envConfig);
            
           /* Create a transaction to prevent the close from succeeding */
            env1.beginTransaction(null, null);
	    try {
		env1.close();
		fail("Didn't catch DatabaseException for open transactions");
	    } catch (DatabaseException DBE) {
	    }

	    try {
		env1.close();
		fail("Didn't catch DatabaseException already closed env");
	    } catch (DatabaseException DBE) {
	    }

            env1 = new Environment(envHome, envConfig);

            String databaseName = "simpleDb";
            DatabaseConfig dbConfig = new DatabaseConfig();
            dbConfig.setTransactional(true);
            dbConfig.setAllowCreate(true);
            Database exampleDb =
		env1.openDatabase(null, databaseName, dbConfig);
	    try {
		env1.close();
		fail("Didn't catch DatabaseException for open dbs");
	    } catch (DatabaseException DBE) {
	    }
	    try {
		env1.close();
		fail("Didn't catch DatabaseException already closed env");
	    } catch (DatabaseException DBE) {
	    }

        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    protected String[] simpleKeyStrings = {
        "foo", "bar", "baz", "aaa", "fubar",
        "foobar", "quux", "mumble", "froboy" };

    protected String[] simpleDataStrings = {
        "one", "two", "three", "four", "five",
        "six", "seven", "eight", "nine" };

    protected void doSimpleCursorPutAndDelete(Cursor cursor, boolean extras)
        throws DatabaseException {

        StringDbt foundKey = new StringDbt();
        StringDbt foundData = new StringDbt();

        for (int i = 0; i < simpleKeyStrings.length; i++) {
            foundKey.setString(simpleKeyStrings[i]);
            foundData.setString(simpleDataStrings[i]);
            if (cursor.putNoOverwrite(foundKey, foundData) !=
                OperationStatus.SUCCESS) {
                throw new DatabaseException("non-0 return");
            }
	    /* Need to write some extra out to force eviction to run. */
	    if (extras) {
		for (int j = 0; j < 500; j++) {
		    foundData.setString(Integer.toString(j));
		    OperationStatus status =
			cursor.put(foundKey, foundData);
		    if (status != OperationStatus.SUCCESS) {
			throw new DatabaseException("non-0 return " + status);
		    }
		}
	    }
        }

        OperationStatus status =
            cursor.getFirst(foundKey, foundData, LockMode.DEFAULT);

        while (status == OperationStatus.SUCCESS) {
            cursor.delete();
            status = cursor.getNext(foundKey, foundData, LockMode.DEFAULT);
        }
    }

    protected void doSimpleVerification(Cursor cursor)
        throws DatabaseException {

        StringDbt foundKey = new StringDbt();
        StringDbt foundData = new StringDbt();

        int count = 0;
        OperationStatus status = cursor.getFirst(foundKey, foundData,
                                                 LockMode.DEFAULT);

        while (status == OperationStatus.SUCCESS) {
            count++;
            status = cursor.getNext(foundKey, foundData, LockMode.DEFAULT);
        }
        assertEquals(simpleKeyStrings.length, count);
    }
}
