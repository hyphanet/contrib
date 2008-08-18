/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: PropUtilTest.java,v 1.22 2008/05/22 19:35:40 linda Exp $
 */

package com.sleepycat.je.util;

import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import junit.framework.TestCase;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.utilint.PropUtil;

public class PropUtilTest extends TestCase {
    public void testGetBoolean() {
        Properties props = new Properties();

        props.setProperty("foo", "true");
        props.setProperty("bar", "True");
        props.setProperty("baz", "false");

        assertTrue(PropUtil.getBoolean(props, "foo"));
        assertTrue(PropUtil.getBoolean(props, "bar"));
        assertFalse(PropUtil.getBoolean(props, "baz"));
    }

    public void testValidate()
        throws DatabaseException {

        Properties props = new Properties();

        props.setProperty("foo", "true");
        props.setProperty("bar", "True");
        props.setProperty("baz", "false");

        Set<String> allowedSet = new HashSet<String>();
        allowedSet.add("foo");
        allowedSet.add("bar");
        allowedSet.add("baz");

        PropUtil.validateProps(props, allowedSet, "test");

        // test negative case
        allowedSet.remove("foo");

        try {
            PropUtil.validateProps(props, allowedSet, "test");
            fail();
        } catch (DatabaseException e) {
            //System.out.println(e);
            assertEquals(DatabaseException.getVersionHeader() +
                         "foo is not a valid property for test",
                         e.getMessage());
        }
    }

    public void testMicrosToMillis() {

        assertEquals(0, PropUtil.microsToMillis(0));
        assertEquals(1, PropUtil.microsToMillis(1));
        assertEquals(1, PropUtil.microsToMillis(999));
        assertEquals(1, PropUtil.microsToMillis(1000));
        assertEquals(2, PropUtil.microsToMillis(1001));
    }
}
