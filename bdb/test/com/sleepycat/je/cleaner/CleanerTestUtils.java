/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: CleanerTestUtils.java,v 1.8.2.2 2007/03/08 22:33:01 mark Exp $
 */

package com.sleepycat.je.cleaner;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

import junit.framework.TestCase;

import com.sleepycat.je.Cursor;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbTestProxy;
import com.sleepycat.je.dbi.CursorImpl;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.log.UtilizationFileReader;
import com.sleepycat.je.tree.BIN;
import com.sleepycat.je.utilint.DbLsn;

/**
 * Package utilities.
 */
public class CleanerTestUtils {

    /**
     * Gets the file of the LSN at the cursor position, using internal methods.
     */
    static long getLogFile(TestCase test, Cursor cursor)
        throws DatabaseException {

        CursorImpl impl = DbTestProxy.dbcGetCursorImpl(cursor);
        int index;
        BIN bin = impl.getDupBIN();
        if (bin != null) {
            index = impl.getDupIndex();
        } else {
            bin = impl.getBIN();
            TestCase.assertNotNull(bin);
            index = impl.getIndex();
        }
        TestCase.assertNotNull(bin.getTarget(index));
        long lsn = bin.getLsn(index);
        TestCase.assertTrue(lsn != DbLsn.NULL_LSN);
        long file = DbLsn.getFileNumber(lsn);
        return file;
    }

    /**
     * Compare utilization as calculated by UtilizationProfile to utilization
     * as calculated by UtilizationFileReader.
     */
    static void verifyUtilization(EnvironmentImpl env,
                                  boolean expectAccurateObsoleteLNCount,
                                  boolean expectAccurateObsoleteLNSize)
        throws DatabaseException {

        Map profileMap = env.getCleaner()
                            .getUtilizationProfile()
                            .getFileSummaryMap(true);
        /* Flush the log before reading. */
        env.getLogManager().flushNoSync();
        Map recalcMap;
        try {
            recalcMap = UtilizationFileReader.calcFileSummaryMap(env);
        } catch (IOException e) {
            throw new DatabaseException(e);
        }
        Iterator i = profileMap.entrySet().iterator();
        while (i.hasNext()) {
            Map.Entry entry = (Map.Entry) i.next();
            Long file = (Long) entry.getKey();
            FileSummary profileSummary = (FileSummary) entry.getValue();
            FileSummary recalcSummary = (FileSummary) recalcMap.remove(file);
            TestCase.assertNotNull(recalcSummary);
            //*
            if (expectAccurateObsoleteLNCount &&
                profileSummary.obsoleteLNCount !=
                recalcSummary.obsoleteLNCount) {
                System.out.println("file=" + file);
                System.out.println("profile=" + profileSummary);
                System.out.println("recalc=" + recalcSummary);
            }
            //*/
            TestCase.assertEquals(recalcSummary.totalCount,
                                 profileSummary.totalCount);
            TestCase.assertEquals(recalcSummary.totalSize,
                                 profileSummary.totalSize);
            TestCase.assertEquals(recalcSummary.totalINCount,
                                 profileSummary.totalINCount);
            TestCase.assertEquals(recalcSummary.totalINSize,
                                 profileSummary.totalINSize);
            TestCase.assertEquals(recalcSummary.totalLNCount,
                                 profileSummary.totalLNCount);
            TestCase.assertEquals(recalcSummary.totalLNSize,
                                 profileSummary.totalLNSize);
            /*
             * Currently we cannot verify obsolete INs because
             * UtilizationFileReader does not count them accurately.
             */
            if (false) {
                TestCase.assertEquals(recalcSummary.obsoleteINCount,
                                     profileSummary.obsoleteINCount);
            }

            /*
             * The obsolete LN count/size is not accurate when running recovery
             * and the IN for the newer LN is already flushed.  For example,
             * it is not accurate in INUtilizationTest when truncating the
             * log before the FileSummaryLNs that are part of the checkpoint.
             */
            if (expectAccurateObsoleteLNCount) {
                TestCase.assertEquals("file=" + file,
                                      recalcSummary.obsoleteLNCount,
                                     profileSummary.obsoleteLNCount);

                /*
                 * The obsoletely LN size is only accurate when running
                 * recovery or doing a DB truncate/remove iff the
                 * je.cleaner.fetchObsoleteSize configuration is set to true.
                 * For example, it is not accurate in TruncateAndRemoveTest
                 * when fetchObsoleteSize is not set to true.
                 */
                if (expectAccurateObsoleteLNSize) {
                    TestCase.assertEquals("file=" + file,
                                          recalcSummary.getObsoleteLNSize(),
                                         profileSummary.obsoleteLNSize);
                }
            }
        }
        TestCase.assertTrue(recalcMap.toString(), recalcMap.isEmpty());
    }
}
