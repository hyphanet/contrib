/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: SplitTest.java,v 1.23.2.1 2007/02/01 14:50:21 cwl Exp $
 */

package com.sleepycat.je.tree;

import java.io.File;

import junit.framework.TestCase;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.util.TestUtils;

public class SplitTest extends TestCase {
    private static final boolean DEBUG = false;

    private File envHome;
    private Environment env;
    private Database db;

    public SplitTest() {
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    public void setUp() 
        throws Exception {
        TestUtils.removeLogFiles("Setup", envHome, false);
        initEnv();
    }

    public void tearDown() 
        throws Exception {
        try {
            db.close();
            env.close();
        } catch (DatabaseException E) {
        }
                
        TestUtils.removeLogFiles("TearDown", envHome, true);
    }

    private void initEnv()
        throws DatabaseException {

        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setConfigParam(EnvironmentParams.NODE_MAX.getName(), "4");
        envConfig.setTransactional(true);
        envConfig.setAllowCreate(true);
        env = new Environment(envHome, envConfig);

        String databaseName = "testDb";
        Transaction txn = env.beginTransaction(null, null);
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setTransactional(true);
        dbConfig.setAllowCreate(true);
        db = env.openDatabase(txn, databaseName, dbConfig);
        txn.commit();
    }

    /**
     * Test splits on a case where the 0th entry gets promoted.
     */
    public void test0Split()
        throws Exception {

        Key.DUMP_BINARY = true;
        try {
            /* Build up a tree. */
            for (int i = 160; i > 0; i-= 10) {
                assertEquals(OperationStatus.SUCCESS,
                             db.put(null, new DatabaseEntry
				 (new byte[] {(byte)i }),
                                    new DatabaseEntry(new byte[] {1})));
            }
            if (DEBUG) {
                System.out.println("<dump>");
                DbInternal.dbGetDatabaseImpl(db).getTree().dump();
            }
            assertEquals(OperationStatus.SUCCESS,
                         db.put(null, new DatabaseEntry(new byte[]{(byte)151}),
                                new DatabaseEntry(new byte[] {1})));
            assertEquals(OperationStatus.SUCCESS,
                         db.put(null, new DatabaseEntry(new byte[]{(byte)152}),
                                new DatabaseEntry(new byte[] {1})));
            assertEquals(OperationStatus.SUCCESS,
                         db.put(null, new DatabaseEntry(new byte[]{(byte)153}),
                                new DatabaseEntry(new byte[] {1})));

            if (DEBUG) {
                DbInternal.dbGetDatabaseImpl(db).getTree().dump();
                System.out.println("</dump>");
            }

            /* 
             * These inserts make a tree where the right most mid-level IN
             * has an idkey greater than its parent entry.
             *
             *     +---------------+               
             *     | id = 90       |
             *     | 50 | 90 | 130 |
             *     +---------------+               
             *       |     |    |
             *                  |
             *              +-----------------+               
             *              | id = 160        |
             *              | 130 | 150 | 152 |
             *              +-----------------+               
             *                 |      |    |
             *                 |      |    +-----------+
             *                 |      |                |
             *       +-----------+  +-----------+ +-----------------+
             *       | BIN       |  | BIN       | | BIN             |
             *       | id = 130  |  | id = 150  | | id=160          |
             *       | 130 | 140 |  | 150 | 151 | | 152 | 153 | 160 |    
             *       +-----------+  +-----------+ +-----------------+
	     *
             * Now delete records 130 and 140 to empty out the subtree with BIN
             * with id=130.
             */
            assertEquals(OperationStatus.SUCCESS,
                         db.delete(null,
                                   new DatabaseEntry(new byte[]{(byte) 130})));
            assertEquals(OperationStatus.SUCCESS,
                         db.delete(null,
                                   new DatabaseEntry(new byte[]{(byte) 140})));
            env.compress();

            /* 
             * These deletes make the mid level IN's 0th entry > its parent
             * reference.
	     *
             *     +---------------+               
             *     | id = 90       |
             *     | 50 | 90 | 130 |
             *     +---------------+               
             *       |     |    |
             *                  |
             *              +-----------+               
             *              | id = 160  |
             *              | 150 | 152 |
             *              +-----------+               
             *                 |      | 
             *                 |      | 
             *                 |      | 
             *       +-----------+ +-----------------+
             *       | BIN       | | BIN             |
             *       | id = 150  | | id=160          |
             *       | 150 | 151 | | 152 | 153 | 160 |    
             *       +-----------+ +-----------------+
             *
             * Now insert 140 into BIN (id = 160) so that its first entry is
             * less than the mid level IN.
             */
            assertEquals(OperationStatus.SUCCESS,
                         db.put(null, new DatabaseEntry(new byte[]{(byte)140}),
                                new DatabaseEntry(new byte[] {1})));

            /* 
             * Now note that the mid level tree's 0th entry is greater than its
             * reference in the root.
             *
             *     +---------------+               
             *     | id = 90       |
             *     | 50 | 90 | 130 |
             *     +---------------+               
             *       |     |    |
             *                  |
             *              +-----------+               
             *              | id = 160  |
             *              | 150 | 152 |
             *              +-----------+               
             *                 |      | 
             *                 |      | 
             *                 |      | 
             *   +----------------+ +-----------------+
             *   | BIN            | | BIN             |
             *   | id = 150       | | id=160          |
             *   | 140 |150 | 151 | | 152 | 153 | 160 |
             *   +----------------+ +-----------------+    
             *
             * Now split the mid level node, putting the new child on the left.
             */
            for (int i = 154; i < 159; i++) {
                assertEquals(OperationStatus.SUCCESS,
                             db.put(null,
                                    new DatabaseEntry(new byte[]{(byte)i}),
                                    new DatabaseEntry(new byte[] {1})));
            }

            /* 
             * This used to result in the following broken tree, which would
             * cause us to not be able to retrieve record 140. With the new
             * split code, entry "150" in the root should stay 130.
	     *
             *     +---------------------+               
             *     | id = 90             |
             *     | 50 | 90 | 150 | 154 |  NOTE: we'v lost record 140
             *     +---------------------+               
             *       |     |    |        \
             *                  |         \
             *              +-----------+  +----------+                
             *              | id = 150  |  |id=160    |
             *              | 150 | 152 |  |154 | 156 |
             *              +-----------+  +----------+                
             *                 |      | 
             *                 |      | 
             *                 |      | 
             *   +------------+ +-------+ 
             *   | BIN        | | BIN   | 
             *   | id = 150   | | id=152| 
             *   | 140|150|151| |152|153| 
             *   +------------+ +-------+
             */
            DatabaseEntry data = new DatabaseEntry();
            assertEquals(OperationStatus.SUCCESS,
                         db.get(null, new DatabaseEntry(new byte[]
			     { (byte)140 }),
                                data, LockMode.DEFAULT));

        } catch (Throwable t) {
            t.printStackTrace();
            throw new Exception(t);
        }
    }
}
