/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: INTest.java,v 1.59.2.1 2007/02/01 14:50:21 cwl Exp $
 */

package com.sleepycat.je.tree;

import java.io.File;
import java.util.Random;

import junit.framework.TestCase;

import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.DatabaseId;
import com.sleepycat.je.dbi.DatabaseImpl;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.util.TestUtils;
import com.sleepycat.je.utilint.DbLsn;

public class INTest extends TestCase {
    static private final int N_BYTES_IN_KEY = 3;
    private int initialINCapacity;
    private DatabaseImpl db = null;
    static private long FAKE_LSN = DbLsn.makeLsn(0, 0);
    private EnvironmentImpl noLogEnv;
    private File envHome;

    public INTest()
	throws DatabaseException {

        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    public void setUp()
	throws DatabaseException {

        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setConfigParam(EnvironmentParams.NODE_MAX.getName(), "6");
        envConfig.setAllowCreate(true);
        noLogEnv = new EnvironmentImpl(envHome, envConfig);
	initialINCapacity =
	    noLogEnv.getConfigManager().getInt(EnvironmentParams.NODE_MAX);
        db = new DatabaseImpl("foo", new DatabaseId(11), noLogEnv,
			      new DatabaseConfig());
    }

    public void tearDown()
	throws DatabaseException {

	noLogEnv.close();
    }

    public void testFindEntry()
	throws DatabaseException {

	IN in = new IN(db, new byte[0], initialINCapacity, 7);
	in.latch();

	byte[] zeroBytes = new byte[N_BYTES_IN_KEY];
	for (int i = 0; i < N_BYTES_IN_KEY; i++) {
	    zeroBytes[i] = 0x00;
	}

	byte[] maxBytes = new byte[N_BYTES_IN_KEY];
	for (int i = 0; i < N_BYTES_IN_KEY; i++) {
	    /* Use FF since that sets the sign bit negative on a byte.
	       This checks the Key.compareTo routine for proper unsigned
	       comparisons. */
	    maxBytes[i] = (byte) 0xFF;
	}

	assertTrue(in.findEntry(zeroBytes, false, false) == -1);
	assertTrue(in.findEntry(maxBytes, false, false) == -1);
	assertTrue(in.findEntry(zeroBytes, false, true) == -1);
	assertTrue(in.findEntry(maxBytes, false, true) == -1);
	assertTrue(in.findEntry(zeroBytes, true, false) == -1);
	assertTrue(in.findEntry(maxBytes, true, false) == -1);
	assertTrue(in.findEntry(zeroBytes, true, true) == -1);
	assertTrue(in.findEntry(maxBytes, true, true) == -1);
	for (int i = 0; i < initialINCapacity; i++) {
	    /* Insert a key and check that we get the same index
	       in return from the binary search.  Check the next
	       highest and next lowest keys also. */
	    byte[] keyBytes = new byte[N_BYTES_IN_KEY];
	    byte[] nextKeyBytes = new byte[N_BYTES_IN_KEY];
	    byte[] prevKeyBytes = new byte[N_BYTES_IN_KEY];
	    nextKeyBytes[0] = prevKeyBytes[0] = keyBytes[0] = 0x01;
	    nextKeyBytes[1] = prevKeyBytes[1] = keyBytes[1] = (byte) i;
	    nextKeyBytes[2] = prevKeyBytes[2] = keyBytes[2] = 0x10;
	    nextKeyBytes[2]++;
	    prevKeyBytes[2]--;
	    in.setEntry(i, null, keyBytes, FAKE_LSN, (byte) 0);
	    assertTrue(in.findEntry(zeroBytes, false, false) == 0);
	    assertTrue(in.findEntry(maxBytes, false, false) == i);
	    assertTrue(in.findEntry(zeroBytes, false, true) == -1);
	    assertTrue(in.findEntry(maxBytes, false, true) == -1);
	    assertTrue(in.findEntry(zeroBytes, true, false) == -1);
	    assertTrue(in.findEntry(maxBytes, true, false) == i);
	    assertTrue(in.findEntry(zeroBytes, true, true) == -1);
	    assertTrue(in.findEntry(maxBytes, true, true) == -1);
	    for (int j = 1; j < in.getNEntries(); j++) { // 0th key is virtual
		assertTrue(in.findEntry(in.getKey(j), false, false)
			   == j);
		assertTrue(in.findEntry(in.getKey(j), false, true)
			   == j);
		assertTrue(in.findEntry(in.getKey(j), true, false) ==
			   (j | IN.EXACT_MATCH));
		assertTrue(in.findEntry(in.getKey(j), true, true) ==
			   (j | IN.EXACT_MATCH));
		assertTrue(in.findEntry(nextKeyBytes, false, false) == i);
		assertTrue(in.findEntry(prevKeyBytes, false, false) == i - 1);
		assertTrue(in.findEntry(nextKeyBytes, false, true) == -1);
		assertTrue(in.findEntry(prevKeyBytes, false, true) == -1);
	    }
	}
	in.releaseLatch();
    }

    public void testInsertEntry()
	throws DatabaseException {

	for (int i = 0; i < 10; i++) {          // cwl: consider upping this
	    doInsertEntry(false);
	    doInsertEntry(true);
	}
    }

    private void doInsertEntry(boolean withMinMax)
	throws DatabaseException {

	IN in = new IN(db, new byte[0], initialINCapacity, 7);
	in.latch();

	byte[] zeroBytes = new byte[N_BYTES_IN_KEY];
	for (int i = 0; i < N_BYTES_IN_KEY; i++) {
	    zeroBytes[i] = 0x00;
	}

	byte[] maxBytes = new byte[N_BYTES_IN_KEY];
	for (int i = 0; i < N_BYTES_IN_KEY; i++) {
	    maxBytes[i] = (byte) 0xFF;
	}

	if (withMinMax) {
	    try {
		in.insertEntry(new ChildReference(null, zeroBytes, FAKE_LSN));
		in.verify(null);
		in.insertEntry(new ChildReference(null, maxBytes, FAKE_LSN));
		in.verify(null);
	    } catch (InconsistentNodeException INE) {
		fail("caught InconsistentNodeException");
	    }

	    assertTrue(in.findEntry(zeroBytes, false, false) == 0);
	    assertTrue(in.findEntry(maxBytes, false, false) == 1);
	    /* shadowed by the virtual 0'th key */
	    assertTrue(in.findEntry(zeroBytes, false, true) == 0);
	    assertTrue(in.findEntry(maxBytes, false, true) == 1);

	    assertTrue(in.findEntry(zeroBytes, true, false) == IN.EXACT_MATCH);
	    assertTrue(in.findEntry(maxBytes, true, false) ==
		       (1 | IN.EXACT_MATCH));
	    /* shadowed by the virtual 0'th key */
	    assertTrue(in.findEntry(zeroBytes, true, true) == IN.EXACT_MATCH);
	    assertTrue(in.findEntry(maxBytes, true, true) ==
		       (1 | IN.EXACT_MATCH));
	}

	Random rnd = new Random();

	try {
	    for (int i = 0;
		 i < initialINCapacity - (withMinMax ? 2 : 0);
		 i++) {
		/* Insert a key and check that we get the same index
		   in return from the binary search.  Check the next
		   highest and next lowest keys also. */
		byte[] keyBytes = new byte[N_BYTES_IN_KEY];

		/* There's a small chance that we may generate the
		   same sequence of bytes that are already present. */
		while (true) {
		    rnd.nextBytes(keyBytes);
		    int index = in.findEntry(keyBytes, true, false);
		    if ((index & IN.EXACT_MATCH) != 0 &&
			index >= 0) {
			continue;
		    }
		    break;
		}

		in.insertEntry(new ChildReference(null, keyBytes, FAKE_LSN));
		try {
		    in.verify(null);
		} catch (InconsistentNodeException INE) {
		    Key.DUMP_BINARY = true;
		    in.dump(0);
		}

		if (withMinMax) {
		    assertTrue(in.findEntry(zeroBytes, false, false) == 0);
		    assertTrue(in.findEntry(maxBytes, false, false) ==
			       in.getNEntries() - 1);
		    /* shadowed by the virtual 0'th key */
		    assertTrue(in.findEntry(zeroBytes, false, true) == 0);
		    assertTrue(in.findEntry(maxBytes, false, true) ==
			       in.getNEntries() - 1);

		    assertTrue(in.findEntry(zeroBytes, true, false) ==
			       IN.EXACT_MATCH);
		    assertTrue(in.findEntry(maxBytes, true, false) ==
			       ((in.getNEntries() - 1) | IN.EXACT_MATCH));
		    /* shadowed by the virtual 0'th key */
		    assertTrue(in.findEntry(zeroBytes, true, true) ==
			       IN.EXACT_MATCH);
		    assertTrue(in.findEntry(maxBytes, true, true) ==
			       ((in.getNEntries() - 1) | IN.EXACT_MATCH));
		} else {
		    assertTrue(in.findEntry(zeroBytes, false, false) == 0);
		    assertTrue(in.findEntry(maxBytes, false, false) ==
			       in.getNEntries() - 1);
		    assertTrue(in.findEntry(zeroBytes, false, true) == -1);
		    assertTrue(in.findEntry(maxBytes, false, true) == -1);

		    assertTrue(in.findEntry(zeroBytes, true, false) == -1);
		    assertTrue(in.findEntry(maxBytes, true, false) ==
			       in.getNEntries() - 1);
		}

		for (int j = 1; j < in.getNEntries(); j++) {
		    assertTrue(in.findEntry(in.getKey(j), false, false) == j);
		    assertTrue(in.findEntry(in.getKey(j), false, true) == j);

		    assertTrue(in.findEntry(in.getKey(j), false, true) == j);
		    assertTrue(in.findEntry(in.getKey(j), true, false) ==
			       (j | IN.EXACT_MATCH));
		}
	    }
	} catch (InconsistentNodeException INE) {
	    fail("caught InconsistentNodeException");
	}

	/* Should be full so insertEntry should return false */
	byte[] keyBytes = new byte[N_BYTES_IN_KEY];
	rnd.nextBytes(keyBytes);

	try {
	    in.insertEntry(new ChildReference(null, keyBytes, FAKE_LSN));
	    fail("should have caught InconsistentNodeException, but didn't");
	} catch (InconsistentNodeException INE) {
	}
	in.releaseLatch();
    }

    public void testDeleteEntry()
	throws DatabaseException {

	for (int i = 0; i < 10; i++) {           // cwl: consider upping this
	    doDeleteEntry(true);
	    doDeleteEntry(false);
	}
    }

    private void doDeleteEntry(boolean withMinMax)
	throws DatabaseException {

	IN in = new IN(db, new byte[0], initialINCapacity, 7);
	in.latch();

	byte[] zeroBytes = new byte[N_BYTES_IN_KEY];
	for (int i = 0; i < N_BYTES_IN_KEY; i++) {
	    zeroBytes[i] = 0x00;
	}

	byte[] maxBytes = new byte[N_BYTES_IN_KEY];
	for (int i = 0; i < N_BYTES_IN_KEY; i++) {
	    maxBytes[i] = (byte) 0xFF;
	}

	if (withMinMax) {
	    try {
		in.insertEntry(new ChildReference(null, zeroBytes, FAKE_LSN));
		in.verify(null);
		in.insertEntry(new ChildReference(null, maxBytes, FAKE_LSN));
		in.verify(null);
	    } catch (InconsistentNodeException INE) {
		fail("caught InconsistentNodeException");
	    }

	    assertTrue(in.findEntry(zeroBytes, false, false) == 0);
	    assertTrue(in.findEntry(maxBytes, false, false) == 1);
	    /* shadowed by the virtual 0'th key */
	    assertTrue(in.findEntry(zeroBytes, false, true) == 0);
	    assertTrue(in.findEntry(maxBytes, false, true) == 1);

	    assertTrue(in.findEntry(zeroBytes, true, false) == IN.EXACT_MATCH);
	    assertTrue(in.findEntry(maxBytes, true, false) ==
		       (1 | IN.EXACT_MATCH));
	    /* shadowed by the virtual 0'th key */
	    assertTrue(in.findEntry(zeroBytes, true, true) == IN.EXACT_MATCH);
	    assertTrue(in.findEntry(maxBytes, true, true) ==
		       (1 | IN.EXACT_MATCH));
	}

	Random rnd = new Random();

	try {
	    /* Fill up the IN with random entries. */
	    for (int i = 0;
		 i < initialINCapacity - (withMinMax ? 2 : 0);
		 i++) {
		/* Insert a key and check that we get the same index
		   in return from the binary search.  Check the next
		   highest and next lowest keys also. */
		byte[] keyBytes = new byte[N_BYTES_IN_KEY];

		/* There's a small chance that we may generate the
		   same sequence of bytes that are already present. */
		while (true) {
		    rnd.nextBytes(keyBytes);
		    int index = in.findEntry(keyBytes, true, false);
		    if ((index & IN.EXACT_MATCH) != 0 &&
			index >= 0) {
			continue;
		    }
		    break;
		}

		in.insertEntry(new ChildReference(null, keyBytes, FAKE_LSN));
	    }

	    if (withMinMax) {
		assertTrue(in.findEntry(zeroBytes, false, false) == 0);
		assertTrue(in.findEntry(maxBytes, false, false) ==
			   in.getNEntries() - 1);
		/* zeroBytes is in the 0th entry, but that's the
		   virtual key so it's not an exact match. */
		assertTrue(in.findEntry(zeroBytes, false, true) == 0);
		assertTrue(in.findEntry(maxBytes, false, true) ==
			   in.getNEntries() - 1);

		assertTrue(in.findEntry(zeroBytes, false, true) == 0);
		assertTrue(in.findEntry(maxBytes, false, true) ==
			   in.getNEntries() - 1);
		assertTrue(in.findEntry(zeroBytes, true, false) == IN.EXACT_MATCH);
		assertTrue(in.findEntry(maxBytes, true, false) ==
			   ((in.getNEntries() - 1) | IN.EXACT_MATCH));
	    }

	    while (in.getNEntries() > 1) {
		int i = rnd.nextInt(in.getNEntries() - 1) + 1;
		assertTrue(in.deleteEntry(in.getKey(i), false));
	    }

	    /* 
	     * We should only be able to delete the zero Key if it was inserted
	     * in the first place.
	     */
	    assertEquals(withMinMax, in.deleteEntry(zeroBytes, false));
	} catch (InconsistentNodeException INE) {
	    fail("caught InconsistentNodeException");
	}
	in.releaseLatch();
    }
}
