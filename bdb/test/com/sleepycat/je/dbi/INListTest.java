/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: INListTest.java,v 1.38.2.1 2007/02/01 14:50:10 cwl Exp $
 */

package com.sleepycat.je.dbi;

import java.io.File;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import junit.framework.TestCase;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
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
    private DatabaseConfig dbConfig;
    private INList inList1 = null;

    public INListTest() {
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
        dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);

    }

    public void setUp()
        throws Exception {

        TestUtils.removeFiles("Setup", envHome, FileManager.JE_SUFFIX);
        sequencer = 0;
        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setAllowCreate(true);
        envConfig.setConfigParam(EnvironmentParams.ENV_RUN_EVICTOR.getName(),
                                 "false");
        env = new Environment(envHome, envConfig);
        envImpl = DbInternal.envGetEnvironmentImpl(env);

        inList1 = new INList(envImpl);
        db = env.openDatabase(null, DB_NAME, dbConfig);
        dbImpl = DbInternal.dbGetDatabaseImpl(db);
    }

    public void tearDown()
	throws Exception {

        inList1 = null;
        db.close();
	env.close();
        TestUtils.removeFiles("TearDown", envHome, FileManager.JE_SUFFIX);
    }

    public void testMajorMinorLatching()
	throws Throwable {
        
        JUnitThread tester1 =
            new JUnitThread("testMajorMinorLatching-Thread1") {
                public void testBody() {

                    try {
                        /* Create two initial elements. */
                        for (int i = 0; i < 2; i++) {
                            IN in = new IN(dbImpl, null, 1, 1);
                            inList1.add(in);
                        }

                        /* 
                         * Acquire the major latch in preparation for an
                         * iteration.
                         */
                        inList1.latchMajor();

                        /* Wait for tester2 to try to acquire the
                           /* minor latch */
                        sequencer = 1;
                        while (sequencer <= 1) {
                            Thread.yield();
                        }

                        /* 
                         * Sequencer is now 2. There should only be
                         * two elements in the list right now even
                         * though thread 2 added a third one.
                         */
                        int count = 0;
                        Iterator iter = inList1.iterator();
                        while (iter.hasNext()) {
                            iter.next();
                            count++;
                        }

                        assertEquals(2, count);

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
                         * Thread2 has exited.  Release the major
                         * latch so that the addedINs can be added
                         * into the main in set.
                         */
                        inList1.releaseMajorLatch();

                        /*
                         * Check that the entry added by tester2 was really
                         * added.
                         */
                        inList1.latchMajor();
                        count = 0;
                        iter = inList1.iterator();
                        while (iter.hasNext()) {
                            iter.next();
                            count++;
                        }

                        assertEquals(4, count);
                        inList1.releaseMajorLatch();
                    } catch (Throwable T) {
                        T.printStackTrace(System.out);
                        fail("Thread 1 caught some Throwable: " + T);
                    }
                }
            };

        JUnitThread tester2 =
            new JUnitThread("testMajorMinorLatching-Thread2") {
                public void testBody() {

                    try {
                        /* Wait for tester1 to start */
                        while (sequencer < 1) {
                            Thread.yield();
                        }

                        assertEquals(1, sequencer);

                        /* 
                         * Acquire the minor latch in preparation for some
                         * concurrent additions.
                         */
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
     * Some actions hold the major inlist latch, but can provoke additions or
     * removals of objects from the inlist. For example, the evictor may cause
     * a fetch of an IN. Make sure the latching works, and iterators can safely
     * be used during the time of the latch.
     */
    public void testFetchingWhenHoldingLatch() 
        throws Exception {

        Set expectedNodes = new HashSet();

        /* Create 3 initial elements. */
        IN startIN = null;
        for (int i = 0; i < 3; i++) {
            startIN = new IN(dbImpl, null, 1, 1);
            inList1.add(startIN);
            expectedNodes.add(startIN);
        }

        inList1.latchMajor();
        try {
            /* Add two more nodes; they should go onto the minor list. */
            IN inA = new IN(dbImpl, null, 1, 1);
            inList1.add(inA);
            IN inB = new IN(dbImpl, null, 1, 1);
            inList1.add(inB);

            /* We should see the original 3 items. */
            checkContents(expectedNodes);

            /* 
             * Now remove an item on the major list, and one from the
             * minor list. (i.e, what would happen if we evicted.)
             */
            inList1.removeLatchAlreadyHeld(startIN);

            /* We should see the original 2 items. */
            expectedNodes.remove(startIN);
            checkContents(expectedNodes);

            /* 
             * Remove an item from the minor list. This ends up flushing the
             * minor list into the major list.
             */
            inList1.removeLatchAlreadyHeld(inA);
            expectedNodes.add(inB);
            checkContents(expectedNodes);

            /* re-add INA */
            inList1.add(inA);

            /* release the major latch, should flush the major list. */
            inList1.releaseMajorLatch();

            inList1.latchMajor();
            expectedNodes.add(inA);
            checkContents(expectedNodes);

        } finally {
            inList1.releaseMajorLatchIfHeld();
        }
    }

    private void checkContents(Set expectedNodes) 
        throws Exception {

        Set seen = new HashSet();
        Iterator iter = inList1.iterator();
        while (iter.hasNext()) {
            IN foo = (IN)iter.next();
            assertTrue(expectedNodes.contains(foo));
            assertTrue(!seen.contains(foo));
            seen.add(foo);
        }
        assertEquals(expectedNodes.size(), seen.size());
    }
}
