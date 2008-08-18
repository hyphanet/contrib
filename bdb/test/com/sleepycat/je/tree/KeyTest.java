/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: KeyTest.java,v 1.18 2008/03/13 03:15:48 cwl Exp $
 */

package com.sleepycat.je.tree;

import java.io.File;
import junit.framework.TestCase;

import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.dbi.DatabaseId;
import com.sleepycat.je.dbi.DatabaseImpl;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.util.TestUtils;

public class KeyTest extends TestCase {
    private File envHome;
    private Environment env;

    public void setUp() {
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    public void tearDown() {
    }

    public void testKeyPrefixer() {
        assertEquals("aaa", makePrefix("aaaa", "aaab"));
        assertEquals("a", makePrefix("abaa", "aaab"));
        assertNull(makePrefix("baaa", "aaab"));
        assertEquals("aaa", makePrefix("aaa", "aaa"));
        assertEquals("aaa", makePrefix("aaa", "aaab"));
    }

    private String makePrefix(String k1, String k2) {
        byte[] ret = Key.createKeyPrefix(k1.getBytes(), k2.getBytes());
        if (ret == null) {
            return null;
        } else {
            return new String(ret);
        }
    }

    public void testKeyPrefixSubsetting() {
        keyPrefixSubsetTest("aaa", "aaa", true);
        keyPrefixSubsetTest("aa", "aaa", true);
        keyPrefixSubsetTest("aaa", "aa", false);
        keyPrefixSubsetTest("", "aa", false);
        keyPrefixSubsetTest(null, "aa", false);
        keyPrefixSubsetTest("baa", "aa", false);
    }

    private void keyPrefixSubsetTest(String keyPrefix,
                                     String newKey,
                                     boolean expect) {
        try {
            EnvironmentConfig envConfig = TestUtils.initEnvConfig();
            envConfig.setAllowCreate(true);
            env = new Environment(envHome, envConfig);
            byte[] keyPrefixBytes =
                (keyPrefix == null ? null : keyPrefix.getBytes());
            byte[] newKeyBytes = newKey.getBytes();
            DatabaseConfig dbConf = new DatabaseConfig();
            dbConf.setKeyPrefixing(true);
            EnvironmentImpl envImpl = DbInternal.envGetEnvironmentImpl(env);
            DatabaseImpl databaseImpl =
                new DatabaseImpl("dummy", new DatabaseId(10), envImpl, dbConf);
            IN in = new IN(databaseImpl, null, 10, 10);
            in.setKeyPrefix(keyPrefixBytes);
            boolean result = in.compareToKeyPrefix(newKeyBytes);
            assertTrue(result == expect);
        } catch (Exception E) {
            E.printStackTrace();
            fail("caught " + E);
        }
    }

    public void testKeyComparisonPerformance() {
	byte[] key1 = "abcdefghijabcdefghij".getBytes();
	byte[] key2 = "abcdefghijabcdefghij".getBytes();

	for (int i = 0; i < 1000000; i++) {
	    assertTrue(Key.compareKeys(key1, key2, null) == 0);
	}
    }

    public void testKeyComparison() {
	byte[] key1 = "aaa".getBytes();
	byte[] key2 = "aab".getBytes();
	assertTrue(Key.compareKeys(key1, key2, null) < 0);
	assertTrue(Key.compareKeys(key2, key1, null) > 0);
	assertTrue(Key.compareKeys(key1, key1, null) == 0);

	key1 = "aa".getBytes();
	key2 = "aab".getBytes();
	assertTrue(Key.compareKeys(key1, key2, null) < 0);
	assertTrue(Key.compareKeys(key2, key1, null) > 0);

	key1 = "".getBytes();
	key2 = "aab".getBytes();
	assertTrue(Key.compareKeys(key1, key2, null) < 0);
	assertTrue(Key.compareKeys(key2, key1, null) > 0);
	assertTrue(Key.compareKeys(key1, key1, null) == 0);

	key1 = "".getBytes();
	key2 = "".getBytes();
	assertTrue(Key.compareKeys(key1, key2, null) == 0);

	byte[] ba1 = { -1, -1, -1 };
	byte[] ba2 = { 0x7f, 0x7f, 0x7f };
	assertTrue(Key.compareKeys(ba1, ba2, null) > 0);

	try {
	    Key.compareKeys(key1, null, null);
	    fail("NullPointerException not caught");
	} catch (NullPointerException NPE) {
	}
    }
}
