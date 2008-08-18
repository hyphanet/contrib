/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: INListTest.java,v 1.44 2008/01/18 22:59:52 mark Exp $
 */

package com.sleepycat.je.dbi;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import junit.framework.TestCase;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.junit.JUnitThread;
import com.sleepycat.je.log.FileManager;
import com.sleepycat.je.tree.IN;
import com.sleepycat.je.util.TestUtils;

public class INListTest extends TestCase {
    private static String DB_NAME = "INListTestDb";
    private File envHome;
    private volatile int sequencer = 0;
    private Environment env;
    private EnvironmentImpl envImpl;
    private Database db;
    private DatabaseImpl dbImpl;

    public INListTest() {
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));

    }

    public void setUp()
        throws DatabaseException, IOException {

        TestUtils.removeFiles("Setup", envHome, FileManager.JE_SUFFIX);
        sequencer = 0;
        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setAllowCreate(true);
        envConfig.setConfigParam(EnvironmentParams.ENV_RUN_EVICTOR.getName(),
                                 "false");
        env = new Environment(envHome, envConfig);
        envImpl = DbInternal.envGetEnvironmentImpl(env);

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
        db = env.openDatabase(null, DB_NAME, dbConfig);
        dbImpl = DbInternal.dbGetDatabaseImpl(db);
    }

    private void close()
        throws DatabaseException {

        if (db != null) {
            db.close();
        }
        if (env != null) {
            env.close();
        }
        db = null;
        dbImpl = null;
        env = null;
        envImpl = null;
    }

    public void tearDown()
        throws DatabaseException, IOException  {

        try {
            close();
        } catch (Exception e) {
            System.out.println("During tearDown: " + e);
        }

        TestUtils.removeFiles("TearDown", envHome, FileManager.JE_SUFFIX);

        envHome = null;
    }

    /**
     * This test was originally written when the INList had a major and minor
     * latch.  It was used to test the addition of INs holding the minor latch
     * while another thread holds the major latch.  Now that we're using
     * ConcurrentHashMap this type of testing is not important, but I've left
     * the test in place (without the latching) since it does exercise the
     * INList API a little.
     */
    public void testConcurrentAdditions()
	throws Throwable {

        final INList inList1 = new INList(envImpl);
        inList1.enable();

        JUnitThread tester1 =
            new JUnitThread("testConcurrentAdditions-Thread1") {
                public void testBody() {

                    try {
                        /* Create two initial elements. */
                        for (int i = 0; i < 2; i++) {
                            IN in = new IN(dbImpl, null, 1, 1);
                            inList1.add(in);
                        }

                        /* Wait for tester2 to try to acquire the
                           /* minor latch */
                        sequencer = 1;
                        while (sequencer <= 1) {
                            Thread.yield();
                        }

                        /*
                         * Sequencer is now 2. There should be three elements
                         * in the list right now because thread 2 added a third
                         * one.
                         */
                        int count = 0;
                        Iterator iter = inList1.iterator();
                        while (iter.hasNext()) {
                            iter.next();
                            count++;
                        }

                        assertEquals(3, count);

                        /*
                         * Allow thread2 to run again.  It will
                         * add another element and throw control
                         * back to thread 1.
                         */
                        sequencer++;   // now it's 3
                        while (sequencer <= 3) {
                            Thread.yield();
                        }

                        /*
                         * Check that the entry added by tester2 was really
                         * added.
                         */
                        count = 0;
                        iter = inList1.iterator();
                        while (iter.hasNext()) {
                            iter.next();
                            count++;
                        }

                        assertEquals(4, count);
                    } catch (Throwable T) {
                        T.printStackTrace(System.out);
                        fail("Thread 1 caught some Throwable: " + T);
                    }
                }
            };

        JUnitThread tester2 =
            new JUnitThread("testConcurrentAdditions-Thread2") {
                public void testBody() {

                    try {
                        /* Wait for tester1 to start */
                        while (sequencer < 1) {
                            Thread.yield();
                        }

                        assertEquals(1, sequencer);

                        inList1.add(new IN(dbImpl, null, 1, 1));
                        sequencer++;

                        /* Sequencer is now 2. */

                        while (sequencer < 3) {
                            Thread.yield();
                        }

                        assertEquals(3, sequencer);
                        /* Add one more element. */
                        inList1.add(new IN(dbImpl, null, 1, 1));
                        sequencer++;
                    } catch (Throwable T) {
                        T.printStackTrace(System.out);
                        fail("Thread 2 caught some Throwable: " + T);
                    }
                }
            };

        tester1.start();
        tester2.start();
        tester1.finishTest();
        tester2.finishTest();
    }

    /*
     * Variations of this loop are used in the following tests to simulate the
     * INList memory budget recalculation that is performed by the same loop
     * construct in DirtyINMap.selectDirtyINsForCheckpoint.
     *
     *  inList.memRecalcBegin();
     *  boolean completed = false;
     *  try {
     *      for (IN in : inList) {
     *          inList.memRecalcIterate(in);
     *      }
     *      completed = true;
     *  } finally {
     *      inList.memRecalcEnd(completed);
     *  }
     */

    /**
     * Scenario #1: IN size is unchanged during the iteration
     *  begin
     *   iterate -- add total IN size, mark processed
     *  end
     */
    public void testMemBudgetReset1()
        throws DatabaseException {

        INList inList = envImpl.getInMemoryINs();
        MemoryBudget mb = envImpl.getMemoryBudget();

        long origTreeMem = mb.getTreeMemoryUsage();
        inList.memRecalcBegin();
        boolean completed = false;
        try {
            for (IN in : inList) {
                inList.memRecalcIterate(in);
            }
            completed = true;
        } finally {
            inList.memRecalcEnd(completed);
        }
        assertEquals(origTreeMem, mb.getTreeMemoryUsage());

        close();
    }

    /**
     * Scenario #2: IN size is updated during the iteration
     *  begin
     *   update  -- do not add delta because IN is not yet processed
     *   iterate -- add total IN size, mark processed
     *   update  -- do add delta because IN was already processed
     *  end
     */
    public void testMemBudgetReset2()
        throws DatabaseException {

        INList inList = envImpl.getInMemoryINs();
        MemoryBudget mb = envImpl.getMemoryBudget();

        /*
         * Size changes must be greater than IN.ACCUMULATED_LIMIT to be
         * counted in the budget, and byte array lengths should be a multiple
         * of 4 to give predictable sizes, since array sizes are allowed in
         * multiples of 4.
         */
        final int SIZE = IN.ACCUMULATED_LIMIT + 100;
        DatabaseEntry key = new DatabaseEntry(new byte[1]);
        db.put(null, key, new DatabaseEntry(new byte[SIZE * 1]));

        /* Test increasing size. */
        long origTreeMem = mb.getTreeMemoryUsage();
        inList.memRecalcBegin();
        boolean completed = false;
        try {
            db.put(null, key, new DatabaseEntry(new byte[SIZE * 2]));
            for (IN in : inList) {
                inList.memRecalcIterate(in);
            }
            db.put(null, key, new DatabaseEntry(new byte[SIZE * 3]));
            completed = true;
        } finally {
            inList.memRecalcEnd(completed);
        }
        assertEquals(origTreeMem + SIZE * 2, mb.getTreeMemoryUsage());

        /* Test decreasing size. */
        inList.memRecalcBegin();
        completed = false;
        try {
            db.put(null, key, new DatabaseEntry(new byte[SIZE * 2]));
            for (IN in : inList) {
                inList.memRecalcIterate(in);
            }
            db.put(null, key, new DatabaseEntry(new byte[SIZE * 1]));
            completed = true;
        } finally {
            inList.memRecalcEnd(completed);
        }
        assertEquals(origTreeMem, mb.getTreeMemoryUsage());

        close();
    }

    /**
     * Scenario #3: IN is added during the iteration but not iterated
     *  begin
     *   add -- add IN size, mark processed
     *  end
     */
    public void testMemBudgetReset3()
        throws DatabaseException {

        INList inList = envImpl.getInMemoryINs();
        MemoryBudget mb = envImpl.getMemoryBudget();

        IN newIn = new IN(dbImpl, null, 1, 1);
        long size = newIn.getBudgetedMemorySize();

        long origTreeMem = mb.getTreeMemoryUsage();
        inList.memRecalcBegin();
        boolean completed = false;
        try {
            for (IN in : inList) {
                inList.memRecalcIterate(in);
            }
            inList.add(newIn);
            completed = true;
        } finally {
            inList.memRecalcEnd(completed);
        }
        assertEquals(origTreeMem + size, mb.getTreeMemoryUsage());

        close();
    }

    /**
     * Scenario #4: IN is added during the iteration and is iterated
     *  begin
     *   add     -- add IN size, mark processed
     *   iterate -- do not add size because IN was already processed
     *  end
     */
    public void testMemBudgetReset4()
        throws DatabaseException {

        INList inList = envImpl.getInMemoryINs();
        MemoryBudget mb = envImpl.getMemoryBudget();

        IN newIn = new IN(dbImpl, null, 1, 1);
        long size = newIn.getBudgetedMemorySize();

        long origTreeMem = mb.getTreeMemoryUsage();
        inList.memRecalcBegin();
        boolean completed = false;
        try {
            inList.add(newIn);
            for (IN in : inList) {
                inList.memRecalcIterate(in);
            }
            completed = true;
        } finally {
            inList.memRecalcEnd(completed);
        }
        assertEquals(origTreeMem + size, mb.getTreeMemoryUsage());

        close();
    }

    /**
     * Scenario #5: IN is removed during the iteration but not iterated
     *  begin
     *   remove  -- do not add delta because IN is not yet processed
     *  end
     */
    public void testMemBudgetReset5()
        throws DatabaseException {

        INList inList = envImpl.getInMemoryINs();
        MemoryBudget mb = envImpl.getMemoryBudget();

        IN oldIn = inList.iterator().next();
        long size = oldIn.getBudgetedMemorySize();

        long origTreeMem = mb.getTreeMemoryUsage();
        inList.memRecalcBegin();
        boolean completed = false;
        try {
            inList.remove(oldIn);
            for (IN in : inList) {
                inList.memRecalcIterate(in);
            }
            completed = true;
        } finally {
            inList.memRecalcEnd(completed);
        }
        assertEquals(origTreeMem - size, mb.getTreeMemoryUsage());

        close();
    }

    /**
     * Scenario #6: IN is removed during the iteration and is iterated
     *  begin
     *   iterate -- add total IN size, mark processed
     *   remove  -- add delta because IN was already processed
     *  end
     */
    public void testMemBudgetReset6()
        throws DatabaseException {

        INList inList = envImpl.getInMemoryINs();
        MemoryBudget mb = envImpl.getMemoryBudget();

        IN oldIn = inList.iterator().next();
        long size = oldIn.getBudgetedMemorySize();

        long origTreeMem = mb.getTreeMemoryUsage();
        inList.memRecalcBegin();
        boolean completed = false;
        try {
            for (IN in : inList) {
                inList.memRecalcIterate(in);
            }
            inList.remove(oldIn);
            completed = true;
        } finally {
            inList.memRecalcEnd(completed);
        }
        assertEquals(origTreeMem - size, mb.getTreeMemoryUsage());

        close();
    }
}
