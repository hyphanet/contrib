/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: XACollectionTest.java,v 1.5.2.2 2007/03/28 15:53:47 cwl Exp $
 */

package com.sleepycat.collections.test;

import java.io.File;

import javax.transaction.xa.XAResource;

import junit.framework.Test;
import junit.framework.TestSuite;

import com.sleepycat.collections.TransactionRunner;
import com.sleepycat.collections.TransactionWorker;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DeadlockException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.XAEnvironment;
import com.sleepycat.je.log.LogUtils.XidImpl;
import com.sleepycat.util.ExceptionUnwrapper;

/**
 * Runs CollectionTest with special TestEnv and TransactionRunner objects to
 * simulate XA transactions.
 *
 * <p>This test is currently JE-only and will not compile on DB core.</p>
 */
public class XACollectionTest extends CollectionTest {

    public static Test suite()
        throws Exception {

        TestSuite suite = new TestSuite();

        EnvironmentConfig config = new EnvironmentConfig();
        config.setTransactional(true);
        TestEnv xaTestEnv = new XATestEnv(config);

        for (int j = 0; j < TestStore.ALL.length; j += 1) {
            for (int k = 0; k < 2; k += 1) {
                boolean entityBinding = (k != 0);

                suite.addTest(new XACollectionTest
                    (xaTestEnv, TestStore.ALL[j], entityBinding));
            }
        }

        return suite;
    }

    public XACollectionTest(TestEnv testEnv,
                            TestStore testStore,
                            boolean isEntityBinding) {

        super(testEnv, testStore, isEntityBinding, false /*isAutoCommit*/);
    }

    protected TransactionRunner newTransactionRunner(Environment env)
        throws DatabaseException {

        return new XARunner((XAEnvironment) env);
    }

    private static class XATestEnv extends TestEnv {

        private XATestEnv(EnvironmentConfig config) {
            super("XA", config);
        }

        protected Environment newEnvironment(File dir,
                                             EnvironmentConfig config)
            throws DatabaseException {

            return new XAEnvironment(dir, config);
        }
    }

    private static class XARunner extends TransactionRunner {

        private XAEnvironment xaEnv;
        private static int sequence;

        private XARunner(XAEnvironment env) {
            super(env);
            xaEnv = env;
        }

        public void run(TransactionWorker worker)
            throws Exception {

            if (xaEnv.getThreadTransaction() == null) {
                for (int i = 0;; i += 1) {
                    sequence += 1;
                    XidImpl xid = new XidImpl
                        (1, String.valueOf(sequence).getBytes(), null);
                    try {
                        xaEnv.start(xid, 0);
                        worker.doWork();
                        int ret = xaEnv.prepare(xid);
                        xaEnv.end(xid, 0);
			if (ret != XAResource.XA_RDONLY) {
			    xaEnv.commit(xid, false);
			}
                        return;
                    } catch (Exception e) {
                        e = ExceptionUnwrapper.unwrap(e);
                        try {
                            xaEnv.end(xid, 0);
                            xaEnv.rollback(xid);
                        } catch (Exception e2) {
                            e2.printStackTrace();
                            throw e;
                        }
                        if (i >= getMaxRetries() ||
                            !(e instanceof DeadlockException)) {
                            throw e;
                        }
                    }
                }
            } else { /* Nested */
                try {
                    worker.doWork();
                } catch (Exception e) {
                    throw ExceptionUnwrapper.unwrap(e);
                }
            }
        }
    }
}
