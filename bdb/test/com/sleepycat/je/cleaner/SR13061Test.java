/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2005,2007 Oracle.  All rights reserved.
 *
 * $Id: SR13061Test.java,v 1.4.2.1 2007/02/01 14:50:06 cwl Exp $
 */

package com.sleepycat.je.cleaner;

import junit.framework.TestCase;

import com.sleepycat.bind.tuple.TupleOutput;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.cleaner.FileSummary;
import com.sleepycat.je.tree.FileSummaryLN;

/**
 * Tests that a FileSummaryLN with an old style string key can be read.  When
 * we relied solely on log entry version to determine whether an LN had a
 * string key, we could fail when an old style LN was migrated by the cleaner.
 * In that case the key was still a string key but the log entry version was
 * updated to something greater than zero.  See FileSummaryLN.hasStringKey for
 * details of how we now guard against this.
 */
public class SR13061Test extends TestCase {

    public SR13061Test() {
    }

    public void testSR13061()
	throws DatabaseException {

        FileSummaryLN ln = new FileSummaryLN(new FileSummary());

        /*
         * All of these tests failed before checking that the byte array must
         * be eight bytes for integer keys.
         */
        assertTrue(ln.hasStringKey(stringKey(0)));
        assertTrue(ln.hasStringKey(stringKey(1)));
        assertTrue(ln.hasStringKey(stringKey(12)));
        assertTrue(ln.hasStringKey(stringKey(123)));
        assertTrue(ln.hasStringKey(stringKey(1234)));
        assertTrue(ln.hasStringKey(stringKey(12345)));
        assertTrue(ln.hasStringKey(stringKey(123456)));
        assertTrue(ln.hasStringKey(stringKey(1234567)));
        assertTrue(ln.hasStringKey(stringKey(123456789)));
        assertTrue(ln.hasStringKey(stringKey(1234567890)));

        /*
         * These tests failed before checking that the first byte of the
         * sequence number (in an eight byte key) must not be '0' to '9' for
         * integer keys.
         */
        assertTrue(ln.hasStringKey(stringKey(12345678)));
        assertTrue(ln.hasStringKey(stringKey(12340000)));

        /* These tests are just for good measure. */
        assertTrue(!ln.hasStringKey(intKey(0, 1)));
        assertTrue(!ln.hasStringKey(intKey(1, 1)));
        assertTrue(!ln.hasStringKey(intKey(12, 1)));
        assertTrue(!ln.hasStringKey(intKey(123, 1)));
        assertTrue(!ln.hasStringKey(intKey(1234, 1)));
        assertTrue(!ln.hasStringKey(intKey(12345, 1)));
        assertTrue(!ln.hasStringKey(intKey(123456, 1)));
        assertTrue(!ln.hasStringKey(intKey(1234567, 1)));
        assertTrue(!ln.hasStringKey(intKey(12345678, 1)));
        assertTrue(!ln.hasStringKey(intKey(123456789, 1)));
        assertTrue(!ln.hasStringKey(intKey(1234567890, 1)));
    }

    private byte[] stringKey(long fileNum) {

        try {
            return String.valueOf(fileNum).getBytes("UTF-8");
        } catch (Exception e) {
            fail(e.toString());
            return null;
        }
    }

    private byte[] intKey(long fileNum, long seqNum) {

        TupleOutput out = new TupleOutput();
        out.writeUnsignedInt(fileNum);
        out.writeUnsignedInt(seqNum);
        return out.toByteArray();
    }
}
