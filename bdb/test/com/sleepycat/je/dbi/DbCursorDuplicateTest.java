/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: DbCursorDuplicateTest.java,v 1.53.2.2 2007/05/23 14:07:30 mark Exp $
 */

package com.sleepycat.je.dbi;

import java.util.Comparator;
import java.util.Hashtable;

import com.sleepycat.je.Cursor;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.tree.DuplicateEntryException;
import com.sleepycat.je.util.StringDbt;

/**
 * Various unit tests for CursorImpl using duplicates.
 */
public class DbCursorDuplicateTest extends DbCursorTestBase {

    public DbCursorDuplicateTest() 
        throws DatabaseException {

        super();
    }

    /**
     * Rudimentary insert/retrieve test.  Walk over the results forwards.
     */
    public void testDuplicateCreationForward()
	throws Throwable {

        initEnv(true);
        try {
            doDuplicateTest(true, false);
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    /**
     * Same as testDuplicateCreationForward except uses keylast.
     */
    public void testDuplicateCreationForwardKeyLast()
	throws Throwable {

        initEnv(true);
        try {
            doDuplicateTest(true, true);
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    /**
     * Rudimentary insert/retrieve test.  Walk over the results backwards.
     */
    public void testDuplicateCreationBackwards()
	throws Throwable {

        initEnv(true);
        try {
            doDuplicateTest(false, false);
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    /**
     * Insert N_KEYS data items into a tree.  Set a btreeComparison function.
     * Iterate through the tree in ascending order.  Ensure that the elements
     * are returned in ascending order.
     */
    public void testLargeGetForwardTraverseWithNormalComparisonFunction() 
        throws Throwable {

        try {
            tearDown();
            duplicateComparisonFunction = duplicateComparator;
            setUp();
            initEnv(true);
            doDuplicateTest(true, false);
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    /**
     * Insert N_KEYS data items into a tree.  Set a reverse order
     * btreeComparison function. Iterate through the tree in ascending order.
     * Ensure that the elements are returned in ascending order.
     */
    public void testLargeGetForwardTraverseWithReverseComparisonFunction() 
        throws Throwable {

        try {
            tearDown();
            duplicateComparisonFunction = reverseDuplicateComparator;
            setUp();
            initEnv(true);
            doDuplicateTest(false, false);
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    /**
     * Put a bunch of data items into the database in a specific order and
     * ensure that when read back that we can't putNoDupData without receiving
     * an error return code.
     */
    public void testPutNoDupData()
	throws Throwable {

        try {
            initEnv(true);
            createRandomDuplicateData(null, false);

            DataWalker dw = new DataWalker(simpleDataMap) {
                    void perData(String foundKey, String foundData)
                        throws DatabaseException {

                        assertEquals
                            (OperationStatus.KEYEXIST,
                             cursor.putNoDupData(new StringDbt(foundKey),
                                                 new StringDbt(foundData)));
                    }
                };
            dw.setIgnoreDataMap(true);
            dw.walkData();
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    public void testPutNoDupData2()
	throws Throwable {

        try {
            initEnv(true);
	    for (int i = 0; i < simpleKeyStrings.length; i++) {
		OperationStatus status =
		    cursor.putNoDupData(new StringDbt("oneKey"),
					new StringDbt(simpleDataStrings[i]));
		assertEquals(OperationStatus.SUCCESS, status);
	    }
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    public void testAbortDuplicateTreeCreation()
	throws Throwable {

        try {
            initEnvTransactional(true);
	    Transaction txn = exampleEnv.beginTransaction(null, null);
	    Cursor c = exampleDb.openCursor(txn, null);
	    OperationStatus status =
		c.put(new StringDbt("oneKey"),
		      new StringDbt("firstData"));
	    assertEquals(OperationStatus.SUCCESS, status);
	    c.close();
	    txn.commit();
	    txn = exampleEnv.beginTransaction(null, null);
	    c = exampleDb.openCursor(txn, null);
	    status =
		c.put(new StringDbt("oneKey"),
		      new StringDbt("secondData"));
	    assertEquals(OperationStatus.SUCCESS, status);
	    c.close();
	    txn.abort();
	    txn = exampleEnv.beginTransaction(null, null);
	    c = exampleDb.openCursor(txn, null);
	    DatabaseEntry keyRet = new DatabaseEntry();
	    DatabaseEntry dataRet = new DatabaseEntry();
	    assertEquals(OperationStatus.SUCCESS,
			 c.getFirst(keyRet, dataRet, LockMode.DEFAULT));
	    assertEquals(1, c.count());
	    assertEquals(OperationStatus.NOTFOUND,
			 c.getNext(keyRet, dataRet, LockMode.DEFAULT));
	    c.close();
	    txn.commit();
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    /**
     * Create the usual random duplicate data.  Iterate back over it calling
     * count at each element.  Make sure the number of duplicates returned for
     * a particular key is N_DUPLICATE_PER_KEY.  Note that this is somewhat
     * inefficient, but cautious, in that it calls count for every duplicate
     * returned, rather than just once for each unique key returned.
     */
    public void testDuplicateCount() 
        throws Throwable {

        try {
            initEnv(true);
            Hashtable dataMap = new Hashtable();

            createRandomDuplicateData(N_COUNT_TOP_KEYS,
                                      N_COUNT_DUPLICATES_PER_KEY,
                                      dataMap, false, true);

            DataWalker dw = new DataWalker(dataMap) {
                    void perData(String foundKey, String foundData)
                        throws DatabaseException {

                        assertEquals(N_COUNT_DUPLICATES_PER_KEY,
				     cursor.count());
                    }
                };
            dw.setIgnoreDataMap(true);
            dw.walkData();
            assertEquals(N_COUNT_DUPLICATES_PER_KEY, dw.nEntries);
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    public void testDuplicateDuplicates()
	throws Throwable {

        try {
            initEnv(true);
            Hashtable dataMap = new Hashtable();

            String keyString = "aaaa";
            String dataString = "d1d1";
            DatabaseEntry keyDbt = new DatabaseEntry();
            DatabaseEntry dataDbt = new DatabaseEntry();
            keyDbt.setData(keyString.getBytes());
            dataDbt.setData(dataString.getBytes());
            assertTrue(cursor.putNoDupData(keyDbt, dataDbt) ==
                       OperationStatus.SUCCESS);
            assertTrue(cursor.putNoDupData(keyDbt, dataDbt) !=
                       OperationStatus.SUCCESS);
            assertTrue(cursor.put(keyDbt, dataDbt) ==
                       OperationStatus.SUCCESS);
            dataString = "d2d2";
            dataDbt.setData(dataString.getBytes());
            assertTrue(cursor.putNoDupData(keyDbt, dataDbt) ==
                       OperationStatus.SUCCESS);
            assertTrue(cursor.putNoDupData(keyDbt, dataDbt) !=
                       OperationStatus.SUCCESS);
            assertTrue(cursor.put(keyDbt, dataDbt) ==
                       OperationStatus.SUCCESS);
            DataWalker dw = new DataWalker(dataMap) {
                    void perData(String foundKey, String foundData) {
                    }
                };
            dw.setIgnoreDataMap(true);
            dw.walkData();
            assertTrue(dw.nEntries == 2);
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    public void testDuplicateDuplicatesWithComparators() //cwl
	throws Throwable {

        try {
            tearDown();
            duplicateComparisonFunction = invocationCountingComparator;
	    btreeComparisonFunction = invocationCountingComparator;
	    invocationCountingComparator.setInvocationCount(0);
            setUp();
            initEnv(true);

            String keyString = "aaaa";
            String dataString = "d1d1";
            DatabaseEntry keyDbt = new DatabaseEntry();
            DatabaseEntry dataDbt = new DatabaseEntry();
            keyDbt.setData(keyString.getBytes());
            dataDbt.setData(dataString.getBytes());
            assertTrue(cursor.put(keyDbt, dataDbt) ==
                       OperationStatus.SUCCESS);
            assertTrue(cursor.put(keyDbt, dataDbt) ==
                       OperationStatus.SUCCESS);

	    InvocationCountingBtreeComparator bTreeICC =
		(InvocationCountingBtreeComparator)
		(exampleDb.getConfig().getBtreeComparator());

	    InvocationCountingBtreeComparator dupICC =
		(InvocationCountingBtreeComparator)
		(exampleDb.getConfig().getDuplicateComparator());

            assertEquals(1, bTreeICC.getInvocationCount());
            assertEquals(1, dupICC.getInvocationCount());
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    public void testDuplicateReplacement()
	throws Throwable {

        try {
            initEnv(true);
            String keyString = "aaaa";
            String dataString = "d1d1";
            DatabaseEntry keyDbt = new DatabaseEntry();
            DatabaseEntry dataDbt = new DatabaseEntry();
            keyDbt.setData(keyString.getBytes());
            dataDbt.setData(dataString.getBytes());
            assertTrue(cursor.putNoDupData(keyDbt, dataDbt) ==
		       OperationStatus.SUCCESS);
            assertTrue(cursor.putNoDupData(keyDbt, dataDbt) !=
		       OperationStatus.SUCCESS);
            dataString = "d2d2";
            dataDbt.setData(dataString.getBytes());
            assertTrue(cursor.putNoDupData(keyDbt, dataDbt) ==
		       OperationStatus.SUCCESS);
            assertTrue(cursor.putNoDupData(keyDbt, dataDbt) !=
		       OperationStatus.SUCCESS);
            DataWalker dw = new DataWalker(null) {
                    void perData(String foundKey, String foundData)
                        throws DatabaseException {

                        StringDbt dataDbt = new StringDbt();
                        dataDbt.setString(foundData);
                        assertEquals(OperationStatus.SUCCESS,
				     cursor.putCurrent(dataDbt));
                    }
                };
            dw.setIgnoreDataMap(true);
            dw.walkData();
            assertTrue(dw.nEntries == 2);
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    public void testDuplicateReplacementFailure()
	throws Throwable {

        try {
            initEnv(true);
            String keyString = "aaaa";
            String dataString = "d1d1";
            DatabaseEntry keyDbt = new DatabaseEntry();
            DatabaseEntry dataDbt = new DatabaseEntry();
            keyDbt.setData(keyString.getBytes());
            dataDbt.setData(dataString.getBytes());
            assertTrue(cursor.putNoDupData(keyDbt, dataDbt) ==
                       OperationStatus.SUCCESS);
            assertTrue(cursor.putNoDupData(keyDbt, dataDbt) !=
                       OperationStatus.SUCCESS);
            dataString = "d2d2";
            dataDbt.setData(dataString.getBytes());
            assertTrue(cursor.putNoDupData(keyDbt, dataDbt) ==
                       OperationStatus.SUCCESS);
            assertTrue(cursor.putNoDupData(keyDbt, dataDbt) !=
                       OperationStatus.SUCCESS);
            DataWalker dw = new DataWalker(null) {
                    void perData(String foundKey, String foundData)
                        throws DatabaseException {

                        StringDbt dataDbt = new StringDbt();
                        dataDbt.setString("blort");
                        try {
                            cursor.putCurrent(dataDbt);
                            fail("didn't catch DatabaseException");
                        } catch (DatabaseException DBE) {
                        }
                    }
                };
            dw.setIgnoreDataMap(true);
            dw.walkData();
            assertTrue(dw.nEntries == 2);
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    public void testDuplicateReplacementFailure1Dup()
	throws Throwable {

        try {
            initEnv(true);
            String keyString = "aaaa";
            String dataString = "d1d1";
            DatabaseEntry keyDbt = new DatabaseEntry();
            DatabaseEntry dataDbt = new DatabaseEntry();
            keyDbt.setData(keyString.getBytes());
            dataDbt.setData(dataString.getBytes());
            assertTrue(cursor.putNoDupData(keyDbt, dataDbt) ==
                       OperationStatus.SUCCESS);
            assertTrue(cursor.putNoDupData(keyDbt, dataDbt) !=
                       OperationStatus.SUCCESS);
            DataWalker dw = new DataWalker(null) {
                    void perData(String foundKey, String foundData)
                        throws DatabaseException {

                        StringDbt dataDbt = new StringDbt();
                        dataDbt.setString("blort");
                        try {
                            cursor.putCurrent(dataDbt);
                            fail("didn't catch DatabaseException");
                        } catch (DatabaseException DBE) {
                        }
                    }
                };
            dw.setIgnoreDataMap(true);
            dw.walkData();
            assertTrue(dw.nEntries == 1);
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    /**
     * When using a duplicate comparator that does not compare all bytes,
     * attempting to change the data for a duplicate data item should cause an
     * error even if a byte not compared is changed. [#15527]
     */
    public void testDuplicateReplacementFailureWithComparisonFunction1()
	throws Throwable {

        try {
            tearDown();
            duplicateComparisonFunction = truncatedComparator;
            setUp();
            initEnv(true);
            String keyString = "aaaa";
            String dataString = "d1d1";
            DatabaseEntry keyDbt = new DatabaseEntry();
            DatabaseEntry dataDbt = new DatabaseEntry();
            keyDbt.setData(keyString.getBytes());
            dataDbt.setData(dataString.getBytes());
            assertTrue(cursor.putNoDupData(keyDbt, dataDbt) ==
                       OperationStatus.SUCCESS);
            assertTrue(cursor.putNoDupData(keyDbt, dataDbt) !=
                       OperationStatus.SUCCESS);
            dataString = "d2d2";
            dataDbt.setData(dataString.getBytes());
            assertTrue(cursor.putNoDupData(keyDbt, dataDbt) ==
                       OperationStatus.SUCCESS);
            assertTrue(cursor.putNoDupData(keyDbt, dataDbt) !=
                       OperationStatus.SUCCESS);
            DataWalker dw = new DataWalker(null) {
                    void perData(String foundKey, String foundData)
                        throws DatabaseException {

                        StringDbt dataDbt = new StringDbt();
                        StringBuffer sb = new StringBuffer(foundData);
                        sb.replace(3, 4, "3");
                        dataDbt.setString(sb.toString());
                        try {
                            cursor.putCurrent(dataDbt);
                            fail("didn't catch DatabaseException");
                        } catch (DatabaseException DBE) {
                        }
                    }
                };
            dw.setIgnoreDataMap(true);
            dw.walkData();
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    /**
     * When using a duplicate comparator that compares all bytes, attempting to
     * change the data for a duplicate data item should cause an error.
     * [#15527]
     */
    public void testDuplicateReplacementFailureWithComparisonFunction2()
	throws Throwable {

        try {
            tearDown();
            duplicateComparisonFunction = truncatedComparator;
            setUp();
            initEnv(true);

            String keyString = "aaaa";
            String dataString = "d1d1";
            DatabaseEntry keyDbt = new DatabaseEntry();
            DatabaseEntry dataDbt = new DatabaseEntry();
            keyDbt.setData(keyString.getBytes());
            dataDbt.setData(dataString.getBytes());
            assertTrue(cursor.putNoDupData(keyDbt, dataDbt) ==
                       OperationStatus.SUCCESS);
            assertTrue(cursor.putNoDupData(keyDbt, dataDbt) !=
                       OperationStatus.SUCCESS);
            dataString = "d2d2";
            dataDbt.setData(dataString.getBytes());
            assertTrue(cursor.putNoDupData(keyDbt, dataDbt) ==
                       OperationStatus.SUCCESS);
            assertTrue(cursor.putNoDupData(keyDbt, dataDbt) !=
                       OperationStatus.SUCCESS);
            DataWalker dw = new DataWalker(null) {
                    void perData(String foundKey, String foundData)
                        throws DatabaseException {

                        StringDbt dataDbt = new StringDbt();
                        StringBuffer sb = new StringBuffer(foundData);
                        sb.replace(2, 2, "3");
                        sb.setLength(4);
                        dataDbt.setString(sb.toString());
                        try {
                            cursor.putCurrent(dataDbt);
                            fail("didn't catch DatabaseException");
                        } catch (DatabaseException DBE) {
                        }
                    }
                };
            dw.setIgnoreDataMap(true);
            dw.walkData();
            assertTrue(dw.nEntries == 2);
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    private void doDuplicateTest(boolean forward, boolean useKeyLast)
	throws Throwable {

	Hashtable dataMap = new Hashtable();
	createRandomDuplicateData(dataMap, useKeyLast);

	DataWalker dw;
	if (forward) {
	    dw = new DataWalker(dataMap) {
		    void perData(String foundKey, String foundData) {
			Hashtable ht = (Hashtable) dataMap.get(foundKey);
			if (ht == null) {
			    fail("didn't find ht " + foundKey + "/" +
				 foundData);
			}

			if (ht.get(foundData) != null) {
			    ht.remove(foundData);
			    if (ht.size() == 0) {
				dataMap.remove(foundKey);
			    }
			} else {
			    fail("didn't find " + foundKey + "/" + foundData);
			}

			assertTrue(foundKey.compareTo(prevKey) >= 0);

			if (prevKey.equals(foundKey)) {
			    if (duplicateComparisonFunction == null) {
				assertTrue(foundData.compareTo(prevData) >= 0);
			    } else {
				assertTrue
				    (duplicateComparisonFunction.compare
				     (foundData.getBytes(),
				      prevData.getBytes()) >= 0);
			    }
			    prevData = foundData;
			} else {
			    prevData = "";
			}

			prevKey = foundKey;
		    }
		};
	} else {
	    dw = new BackwardsDataWalker(dataMap) {
		    void perData(String foundKey, String foundData) {
			Hashtable ht = (Hashtable) dataMap.get(foundKey);
			if (ht == null) {
			    fail("didn't find ht " + foundKey + "/" +
				 foundData);
			}

			if (ht.get(foundData) != null) {
			    ht.remove(foundData);
			    if (ht.size() == 0) {
				dataMap.remove(foundKey);
			    }
			} else {
			    fail("didn't find " + foundKey + "/" + foundData);
			}

			if (!prevKey.equals("")) {
			    assertTrue(foundKey.compareTo(prevKey) <= 0);
			}

			if (prevKey.equals(foundKey)) {
			    if (!prevData.equals("")) {
				if (duplicateComparisonFunction == null) {
				    assertTrue
					(foundData.compareTo(prevData) <= 0);
				} else {
				    assertTrue
					(duplicateComparisonFunction.compare
					 (foundData.getBytes(),
					  prevData.getBytes()) <= 0);
				}
			    }
			    prevData = foundData;
			} else {
			    prevData = "";
			}

			prevKey = foundKey;
		    }
		};
	}
	dw.setIgnoreDataMap(true);
	dw.walkData();
	assertTrue(dataMap.size() == 0);
    }

    /**
     * Create a bunch of random duplicate data.  Iterate over it using
     * getNextDup until the end of the dup set.  At end of set, handleEndOfSet
     * is called to do a getNext onto the next dup set.  Verify that ascending
     * order is maintained and that we reach end of set the proper number of
     * times.
     */
    public void testGetNextDup()
	throws Throwable {

        try {
            initEnv(true);
            Hashtable dataMap = new Hashtable();

            createRandomDuplicateData(dataMap, false);

            DataWalker dw = new DupDataWalker(dataMap) {
                    void perData(String foundKey, String foundData) {
                        Hashtable ht = (Hashtable) dataMap.get(foundKey);
                        if (ht == null) {
                            fail("didn't find ht " +
				 foundKey + "/" + foundData);
                        }

                        if (ht.get(foundData) != null) {
                            ht.remove(foundData);
                            if (ht.size() == 0) {
                                dataMap.remove(foundKey);
                            }
                        } else {
                            fail("didn't find " + foundKey + "/" + foundData);
                        }

                        assertTrue(foundKey.compareTo(prevKey) >= 0);

                        if (prevKey.equals(foundKey)) {
                            if (duplicateComparisonFunction == null) {
                                assertTrue(foundData.compareTo(prevData) >= 0);
                            } else {
                                assertTrue
                                    (duplicateComparisonFunction.compare
                                     (foundData.getBytes(),
                                      prevData.getBytes()) >= 0);
                            }
                            prevData = foundData;
                        } else {
                            prevData = "";
                        }

                        prevKey = foundKey;
                    }

                    OperationStatus handleEndOfSet(OperationStatus status)
                        throws DatabaseException {

                        String foundKeyString = foundKey.getString();
                        Hashtable ht = (Hashtable) dataMap.get(foundKeyString);
                        assertNull(ht);
                        return cursor.getNext(foundKey, foundData,
                                              LockMode.DEFAULT);
                    }
                };
            dw.setIgnoreDataMap(true);
            dw.walkData();
            assertEquals(N_TOP_LEVEL_KEYS, dw.nHandleEndOfSet);
            assertTrue(dataMap.size() == 0);
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    /**
     * Create a bunch of random duplicate data.  Iterate over it using
     * getNextDup until the end of the dup set.  At end of set, handleEndOfSet
     * is called to do a getNext onto the next dup set.  Verify that descending
     * order is maintained and that we reach end of set the proper number of
     * times.
     */
    public void testGetPrevDup()
	throws Throwable {

        try {
            initEnv(true);
            Hashtable dataMap = new Hashtable();

            createRandomDuplicateData(dataMap, false);

            DataWalker dw = new BackwardsDupDataWalker(dataMap) {
                    void perData(String foundKey, String foundData) {
                        Hashtable ht = (Hashtable) dataMap.get(foundKey);
                        if (ht == null) {
                            fail("didn't find ht " +
				 foundKey + "/" + foundData);
                        }

                        if (ht.get(foundData) != null) {
                            ht.remove(foundData);
                            if (ht.size() == 0) {
                                dataMap.remove(foundKey);
                            }
                        } else {
                            fail("didn't find " + foundKey + "/" + foundData);
                        }

                        if (!prevKey.equals("")) {
                            assertTrue(foundKey.compareTo(prevKey) <= 0);
                        }

                        if (prevKey.equals(foundKey)) {
                            if (!prevData.equals("")) {
                                if (duplicateComparisonFunction == null) {
                                    assertTrue(foundData.compareTo
					       (prevData) <= 0);
                                } else {
                                    assertTrue
                                        (duplicateComparisonFunction.compare
                                         (foundData.getBytes(),
                                          prevData.getBytes()) <= 0);
                                }
                            }
                            prevData = foundData;
                        } else {
                            prevData = "";
                        }

                        prevKey = foundKey;
                    }

                    OperationStatus handleEndOfSet(OperationStatus status)
                        throws DatabaseException {

                        String foundKeyString = foundKey.getString();
                        Hashtable ht = (Hashtable) dataMap.get(foundKeyString);
                        assertNull(ht);
                        return cursor.getPrev(foundKey, foundData,
                                              LockMode.DEFAULT);
                    }
                };
            dw.setIgnoreDataMap(true);
            dw.walkData();
            assertEquals(N_TOP_LEVEL_KEYS, dw.nHandleEndOfSet);
            assertTrue(dataMap.size() == 0);
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    /**
     * Create a bunch of random duplicate data.  Iterate over it using
     * getNextNoDup until the end of the top level set.  Verify that
     * ascending order is maintained and that we reach see the proper
     * number of top-level keys.
     */
    public void testGetNextNoDup()
	throws Throwable {

        try {
            initEnv(true);
            Hashtable dataMap = new Hashtable();

            createRandomDuplicateData(dataMap, false);

            DataWalker dw = new NoDupDataWalker(dataMap) {
                    void perData(String foundKey, String foundData) {
                        Hashtable ht = (Hashtable) dataMap.get(foundKey);
                        if (ht == null) {
                            fail("didn't find ht " +
				 foundKey + "/" + foundData);
                        }

                        if (ht.get(foundData) != null) {
                            dataMap.remove(foundKey);
                        } else {
                            fail("saw " +
				 foundKey + "/" + foundData + " twice.");
                        }

                        assertTrue(foundKey.compareTo(prevKey) > 0);
                        prevKey = foundKey;
                    }
                };
            dw.setIgnoreDataMap(true);
            dw.walkData();
            assertEquals(N_TOP_LEVEL_KEYS, dw.nEntries);
            assertTrue(dataMap.size() == 0);
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    /**
     * Create a bunch of random duplicate data.  Iterate over it using
     * getNextNoDup until the end of the top level set.  Verify that descending
     * order is maintained and that we reach see the proper number of top-level
     * keys.
     */
    public void testGetPrevNoDup()
	throws Throwable {

        try {
            initEnv(true);
            Hashtable dataMap = new Hashtable();

            createRandomDuplicateData(dataMap, false);

            DataWalker dw = new NoDupBackwardsDataWalker(dataMap) {
                    void perData(String foundKey, String foundData) {
                        Hashtable ht = (Hashtable) dataMap.get(foundKey);
                        if (ht == null) {
                            fail("didn't find ht " +
				 foundKey + "/" + foundData);
                        }

                        if (ht.get(foundData) != null) {
                            dataMap.remove(foundKey);
                        } else {
                            fail("saw " +
				 foundKey + "/" + foundData + " twice.");
                        }

                        if (!prevKey.equals("")) {
                            assertTrue(foundKey.compareTo(prevKey) < 0);
                        }
                        prevKey = foundKey;
                    }
                };
            dw.setIgnoreDataMap(true);
            dw.walkData();
            assertEquals(N_TOP_LEVEL_KEYS, dw.nEntries);
            assertTrue(dataMap.size() == 0);
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    public void testIllegalDuplicateCreation() 
        throws Throwable {

        try {
            initEnv(false);
            Hashtable dataMap = new Hashtable();

            try {
                createRandomDuplicateData(dataMap, false);
                fail("didn't throw DuplicateEntryException");
            } catch (DuplicateEntryException DEE) {
            }
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    /**
     * Just use the BtreeComparator that's already available.
     */
    private static Comparator duplicateComparator =
	new DuplicateAscendingComparator();

    private static Comparator reverseDuplicateComparator =
	new DuplicateReverseComparator();

    private static InvocationCountingBtreeComparator
	invocationCountingComparator =
	new InvocationCountingBtreeComparator();

    public static class DuplicateAscendingComparator
        extends BtreeComparator {

	public DuplicateAscendingComparator() {
	    super();
	}
    }

    public static class DuplicateReverseComparator
        extends ReverseBtreeComparator {

	public DuplicateReverseComparator() {
	    super();
	}
    }

    public static class InvocationCountingBtreeComparator
	extends BtreeComparator {

	private int invocationCount = 0;

	public int compare(Object o1, Object o2) {
	    invocationCount++;
	    return super.compare(o1, o2);
	}

	public int getInvocationCount() {
	    return invocationCount;
	}

	public void setInvocationCount(int invocationCount) {
	    this.invocationCount = invocationCount;
	}
    }

    /*
     * A special comparator that only looks at the first length-1 bytes of data
     * so that the last byte can be changed without affecting "equality".  Use
     * this for putCurrent tests of duplicates.
     */
    private static Comparator truncatedComparator = new TruncatedComparator();

    private static class TruncatedComparator implements Comparator {
	protected TruncatedComparator() {
	}

	public int compare(Object o1, Object o2) {
	    byte[] arg1;
	    byte[] arg2;
	    arg1 = (byte[]) o1;
	    arg2 = (byte[]) o2;
	    int a1Len = arg1.length - 1;
	    int a2Len = arg2.length - 1;

	    int limit = Math.min(a1Len, a2Len);

	    for (int i = 0; i < limit; i++) {
		byte b1 = arg1[i];
		byte b2 = arg2[i];
		if (b1 == b2) {
		    continue;
		} else {
		    /* 
		     * Remember, bytes are signed, so convert to
		     * shorts so that we effectively do an unsigned
		     * byte comparison.
		     */
		    short s1 = (short) (b1 & 0x7F);
		    short s2 = (short) (b2 & 0x7F);
		    if (b1 < 0) {
			s1 |= 0x80;
		    }
		    if (b2 < 0) {
			s2 |= 0x80;
		    }
		    return (s1 - s2);
		}
	    }

	    return (a1Len - a2Len);
	}
    }
}
