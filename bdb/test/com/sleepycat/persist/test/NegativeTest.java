/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: NegativeTest.java,v 1.4.2.5 2007/12/08 14:43:48 mark Exp $
 */

package com.sleepycat.persist.test;

import java.util.ArrayList;

import static com.sleepycat.persist.model.Relationship.ONE_TO_ONE;
import junit.framework.Test;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.test.TxnTestCase;
import com.sleepycat.persist.EntityStore;
import com.sleepycat.persist.PrimaryIndex;
import com.sleepycat.persist.StoreConfig;
import com.sleepycat.persist.model.Entity;
import com.sleepycat.persist.model.KeyField;
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
    
    public void testBadSequenceKeys() 
        throws DatabaseException {

        open();
        try {
            PrimaryIndex<Boolean,BadSequenceKeyEntity1> index =
                store.getPrimaryIndex
                    (Boolean.class, BadSequenceKeyEntity1.class);
            fail();
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().indexOf
                ("Type not allowed for sequence") >= 0);
        }
        try {
            PrimaryIndex<BadSequenceKeyEntity2.Key,
                         BadSequenceKeyEntity2> index =
                store.getPrimaryIndex
                    (BadSequenceKeyEntity2.Key.class,
                     BadSequenceKeyEntity2.class);
            fail();
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().indexOf
                ("Type not allowed for sequence") >= 0);
        }
        try {
            PrimaryIndex<BadSequenceKeyEntity3.Key,
                         BadSequenceKeyEntity3> index =
                store.getPrimaryIndex
                    (BadSequenceKeyEntity3.Key.class,
                     BadSequenceKeyEntity3.class);
            fail();
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().indexOf
                ("A composite key class used with a sequence may contain " +
                 "only a single integer key field")>= 0);
        }
        close();
    }
    
    /** Boolean not allowed for sequence key. */
    @Entity
    static class BadSequenceKeyEntity1 {

        @PrimaryKey(sequence="X")
        private boolean key;
    }
    
    /** Composite key with non-integer field not allowed for sequence key. */
    @Entity
    static class BadSequenceKeyEntity2 {

        @PrimaryKey(sequence="X")
        private Key key;

        @Persistent
        static class Key {
            @KeyField(1)
            boolean key;
        }
    }
    
    /** Composite key with multiple key fields not allowed for sequence key. */
    @Entity
    static class BadSequenceKeyEntity3 {

        @PrimaryKey(sequence="X")
        private Key key;

        @Persistent
        static class Key {
            @KeyField(1)
            int key;
            @KeyField(2)
            int key2;
        }
    }
    
    /**
     * A proxied object may not current contain a field that references the
     * parent proxy.  [#15815]
     */
    public void testProxyNestedRef() 
        throws DatabaseException {

        open();
        PrimaryIndex<Integer,ProxyNestedRef> index = store.getPrimaryIndex
            (Integer.class, ProxyNestedRef.class);
        ProxyNestedRef entity = new ProxyNestedRef();
        entity.list.add(entity.list);
        try {
            index.put(entity);
            fail();
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().indexOf
                ("Cannot embed a reference to a proxied object") >= 0);
        }
        close();
    }

    @Entity
    static class ProxyNestedRef {

        @PrimaryKey
        private int key;

        ArrayList<Object> list = new ArrayList<Object>();
    }
}
