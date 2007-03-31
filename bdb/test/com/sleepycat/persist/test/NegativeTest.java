/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: NegativeTest.java,v 1.4.2.1 2007/02/01 14:50:25 cwl Exp $
 */

package com.sleepycat.persist.test;

import static com.sleepycat.persist.model.Relationship.ONE_TO_ONE;
import junit.framework.Test;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.test.TxnTestCase;
import com.sleepycat.persist.EntityStore;
import com.sleepycat.persist.PrimaryIndex;
import com.sleepycat.persist.StoreConfig;
import com.sleepycat.persist.model.Entity;
import com.sleepycat.persist.model.Persistent;
import com.sleepycat.persist.model.PrimaryKey;
import com.sleepycat.persist.model.SecondaryKey;

/**
 * Negative tests.
 *
 * @author Mark Hayes
 */ 
public class NegativeTest extends TxnTestCase {
 
    public static Test suite() {
        return txnTestSuite(NegativeTest.class, null, null);
    }

    private EntityStore store;

    private void open()
        throws DatabaseException {

        StoreConfig config = new StoreConfig();
        config.setAllowCreate(envConfig.getAllowCreate());
        config.setTransactional(envConfig.getTransactional());

        store = new EntityStore(env, "test", config);
    }

    private void close()
        throws DatabaseException {

        store.close();
    }
    
    public void testBadKeyClass1() 
        throws DatabaseException {

        open();
        try {
            PrimaryIndex<BadKeyClass1,UseBadKeyClass1> index =
                store.getPrimaryIndex
                    (BadKeyClass1.class, UseBadKeyClass1.class);
            fail();
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().indexOf("@KeyField") >= 0);
        }
        close();
    }
    
    /** Missing @KeyField in composite key class. */
    @Persistent
    static class BadKeyClass1 {

        private int f1;
    }
    
    @Entity
    static class UseBadKeyClass1 {

        @PrimaryKey
        private BadKeyClass1 f1 = new BadKeyClass1();

        @SecondaryKey(relate=ONE_TO_ONE)
        private BadKeyClass1 f2 = new BadKeyClass1();
    }
}
