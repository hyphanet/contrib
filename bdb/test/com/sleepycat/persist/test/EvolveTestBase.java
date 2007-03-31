/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2000,2007 Oracle.  All rights reserved.
 *
 * $Id: EvolveTestBase.java,v 1.5.2.1 2007/02/01 14:50:25 cwl Exp $
 */
package com.sleepycat.persist.test;

import java.io.File;
import java.util.Enumeration;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.util.TestUtils;
import com.sleepycat.persist.EntityStore;
import com.sleepycat.persist.StoreConfig;
import com.sleepycat.persist.model.AnnotationModel;
import com.sleepycat.persist.model.EntityModel;
import com.sleepycat.persist.raw.RawStore;

/**
 * Base class for EvolveTest and EvolveTestInit.
 *
 * @author Mark Hayes
 */
public class EvolveTestBase extends TestCase {

    File envHome;
    Environment env;
    EntityStore store;
    RawStore rawStore;
    EntityStore newStore;
    int caseIndex;
    Class<? extends EvolveCase> caseCls;
    EvolveCase caseObj;

    static Test getSuite(Class testClass)
        throws Exception {

        TestSuite suite = new TestSuite();
        for (int caseIndex = 0;
             caseIndex < EvolveClasses.ALL.size();
             caseIndex += 1) {
            Class<? extends EvolveCase> caseCls =
                EvolveClasses.ALL.get(caseIndex);
            TestSuite baseSuite = new TestSuite(testClass);
            Enumeration e = baseSuite.tests();
            while (e.hasMoreElements()) {
                EvolveTestBase test = (EvolveTestBase) e.nextElement();
                test.init(caseIndex, caseCls);
                suite.addTest(test);
            }
        }
        return suite;
    }

    private void init(int caseIndex, Class<? extends EvolveCase> caseCls) 
        throws Exception {

        this.caseIndex = caseIndex;
        this.caseCls = caseCls;
        caseObj = caseCls.newInstance();
    }

    File getTestInitHome() {
        return new File
            (System.getProperty(TestUtils.DEST_DIR),
             "../testevolve/C" + caseIndex);
    }

    @Override
    public void tearDown() {

        /* Set test name for reporting; cannot be done in the ctor or setUp. */
        String caseClsName = caseCls.getName();
        caseClsName = caseClsName.substring(caseClsName.lastIndexOf('$') + 1);
        setName(String.valueOf(caseIndex) + ':' +
                caseClsName + '-' +
                getName());

        if (env != null) {
            try {
                closeAll();
            } catch (Throwable e) {
                System.out.println("During tearDown: " + e);
            }
        }
        envHome = null;
        env = null;
        store = null;
        caseCls = null;
        caseObj = null;

        /* Do not delete log files so they can be used by 2nd phase of test. */
    }

    void openEnv()
        throws DatabaseException {

        EnvironmentConfig config = new EnvironmentConfig();
        config.setAllowCreate(true);
        config.setTransactional(true);
        env = new Environment(envHome, config);
    }

    /**
     * Returns true if the store was opened successfully.  Returns false if the
     * store could not be opened because an exception was expected -- this is
     * not a test failure but no further tests for an EntityStore may be run.
     */
    private boolean openStore(StoreConfig config)
        throws Exception {

        config.setTransactional(true);
        config.setMutations(caseObj.getMutations());

        EntityModel model = new AnnotationModel();
        config.setModel(model);
        caseObj.configure(model, config);

        String expectException = caseObj.getStoreOpenException();
        try {
            store = new EntityStore(env, EvolveCase.STORE_NAME, config);
            if (expectException != null) {
                fail("Expected: " + expectException);
            }
        } catch (Exception e) {
            if (expectException != null) {
                //e.printStackTrace();
                EvolveCase.checkEquals(expectException, e.toString());
                return false;
            } else {
                throw e;
            }
        }
        return true;
    }

    boolean openStoreReadOnly()
        throws Exception {

        StoreConfig config = new StoreConfig();
        config.setReadOnly(true);
        return openStore(config);
    }

    boolean openStoreReadWrite()
        throws Exception {

        StoreConfig config = new StoreConfig();
        config.setAllowCreate(true);
        return openStore(config);
    }

    void openRawStore()
        throws DatabaseException {

        rawStore = new RawStore(env, EvolveCase.STORE_NAME, null);
    }

    void closeStore()
        throws DatabaseException {

        if (store != null) {
            store.close();
            store = null;
        }
    }

    void openNewStore()
        throws Exception {

        StoreConfig config = new StoreConfig();
        config.setAllowCreate(true);
        config.setTransactional(true);

        EntityModel model = new AnnotationModel();
        config.setModel(model);
        caseObj.configure(model, config);

        newStore = new EntityStore(env, "new", config);
    }

    void closeNewStore()
        throws DatabaseException {

        if (newStore != null) {
            newStore.close();
            newStore = null;
        }
    }

    void closeRawStore()
        throws DatabaseException {

        if (rawStore != null) {
            rawStore.close();
            rawStore = null;
        }
    }

    void closeEnv()
        throws DatabaseException {

        if (env != null) {
            env.close();
            env = null;
        }
    }

    void closeAll()
        throws DatabaseException {

        closeStore();
        closeRawStore();
        closeNewStore();
        closeEnv();
    }
}
