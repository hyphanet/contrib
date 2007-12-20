/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: ConvertAndAddTest.java,v 1.1.2.3 2007/12/08 14:43:48 mark Exp $
 */

package com.sleepycat.persist.test;

import java.io.File;
import java.io.IOException;

import junit.framework.TestCase;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.util.TestUtils;
import com.sleepycat.persist.EntityStore;
import com.sleepycat.persist.PrimaryIndex;
import com.sleepycat.persist.StoreConfig;
import com.sleepycat.persist.evolve.Conversion;
import com.sleepycat.persist.evolve.Converter;
import com.sleepycat.persist.evolve.Mutations;
import com.sleepycat.persist.model.Entity;
import com.sleepycat.persist.model.EntityModel;
import com.sleepycat.persist.model.PrimaryKey;

/**
 * Test a bug fix where an IndexOutOfBoundsException occurs when adding a field
 * and converting another field, where the latter field is alphabetically
 * higher than the former.  This is also tested by
 * EvolveClasses.FieldAddAndConvert, but that class does not test evolving an
 * entity that was created by catalog version 0.  [#15797]
 *
 * A modified version of this program was run manually with JE 3.2.30 to
 * produce a log, which is the result of the testSetup() test.  The sole log
 * file was renamed from 00000000.jdb to ConvertAndAddTest.jdb and added to CVS
 * in this directory.  When that log file is opened here, the bug is
 * reproduced.  The modifications to this program for 3.2.30 are:
 *
 *  + X in testSetup
 *  + X out testConvertAndAddField
 *  + don't remove log files in tearDown
 *  + @Entity version is 0
 *  + removed field MyEntity.a
 *
 * This test should be excluded from the BDB build because it uses a stored JE
 * log file and it tests a fix for a bug that was never present in BDB.
 *
 * @author Mark Hayes
 */ 
public class ConvertAndAddTest extends TestCase {

    private static final String STORE_NAME = "test";

    private File envHome;
    private Environment env;

    public void setUp()
        throws IOException {

        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
        TestUtils.removeLogFiles("Setup", envHome, false);
    }

    public void tearDown()
        throws IOException {

        if (env != null) {
            try {
                env.close();
            } catch (DatabaseException e) {
                System.out.println("During tearDown: " + e);
            }
        }
        try {
            TestUtils.removeLogFiles("TearDown", envHome, false);
        } catch (Error e) {
            System.out.println("During tearDown: " + e);
        }
        envHome = null;
        env = null;
    }

    private EntityStore open(boolean addConverter)
        throws DatabaseException {

        EnvironmentConfig envConfig = new EnvironmentConfig();
        envConfig.setAllowCreate(true);
        env = new Environment(envHome, envConfig);

        Mutations mutations = new Mutations();
        mutations.addConverter(new Converter
            (MyEntity.class.getName(), 0, "b", new MyConversion()));

        StoreConfig storeConfig = new StoreConfig();
        storeConfig.setAllowCreate(true);
        storeConfig.setMutations(mutations);
        return new EntityStore(env, "foo", storeConfig);
    }

    private void close(EntityStore store)
        throws DatabaseException {

        store.close();
        env.close();
        env = null;
    }

    public void testConvertAndAddField()
        throws DatabaseException, IOException {

        /* Copy log file resource to log file zero. */
        TestUtils.loadLog(getClass(), "ConvertAndAddTest.jdb", envHome);

        EntityStore store = open(true /*addConverter*/);

        PrimaryIndex<Long, MyEntity> index =
            store.getPrimaryIndex(Long.class, MyEntity.class);

        MyEntity entity = index.get(1L);
        assertNotNull(entity);
        assertEquals(123, entity.b);

        close(store);
    }

    public void xtestSetup()
        throws DatabaseException {

        EntityStore store = open(false /*addConverter*/);

        PrimaryIndex<Long, MyEntity> index =
            store.getPrimaryIndex(Long.class, MyEntity.class);

        MyEntity entity = new MyEntity();
        entity.key = 1;
        entity.b = 123;
        index.put(entity);

        close(store);
    }

    @Entity(version=1)
    static class MyEntity {

        @PrimaryKey
        long key;

        int a; // added in version 1
        int b;

        private MyEntity() {}
    }

    public static class MyConversion implements Conversion {

        public void initialize(EntityModel model) {
        }

        public Object convert(Object fromValue) {
            return fromValue;
        }
      
        @Override
        public boolean equals(Object o) {
            return o instanceof MyConversion;
        }
    }
}
