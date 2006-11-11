/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2006
 *      Oracle Corporation.  All rights reserved.
 *
 * $Id: FileSummary.java,v 1.15 2006/09/12 19:16:43 cwl Exp $
 */

package com.sleepycat.je.cleaner;

import java.nio.ByteBuffer;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.log.LogReadable;
import com.sleepycat.je.log.LogUtils;
import com.sleepycat.je.log.LogWritable;

public class FileSummary implements LogWritable, LogReadable {

    /* Persistent fields. */
    public int totalCount;      // Total # of log entries
    public int totalSize;       // Total bytes in log file
    public int totalINCount;    // Number of IN log entries
    public int totalINSize;     // Byte size of IN log entries
    public int totalLNCount;    // Number of LN log entries
    public int totalLNSize;     // Byte size of LN log entries
    public int obsoleteINCount; // Number of obsolete IN log entries
    public int obsoleteLNCount; // Number of obsolete LN log entries

    /**
     * Creates an empty summary.
     */
    public FileSummary() {
    }

    /**
     * Returns whether this summary contains any non-zero totals.
     */
    public boolean isEmpty() {

        return totalCount == 0 &&
               totalSize == 0 &&
               obsoleteINCount == 0 &&
               obsoleteLNCount == 0;
    }

    /**
     * Returns the approximate byte size of all obsolete LN entries.
     */
    public int getObsoleteLNSize() {

        if (totalLNCount == 0) {
            return 0;
        }
        /* Use long arithmetic. */
        long totalSize = totalLNSize;
        /* Scale by 255 to reduce integer truncation error. */
        totalSize <<= 8;
        long avgSizePerLN = totalSize / totalLNCount;
        return (int) ((obsoleteLNCount * avgSizePerLN) >> 8);
    }

    /**
     * Returns the approximate byte size of all obsolete IN entries.
     */
    public int getObsoleteINSize() {

        if (totalINCount == 0) {
            return 0;
        }
        /* Use long arithmetic. */
        long totalSize = totalINSize;
        /* Scale by 255 to reduce integer truncation error. */
        totalSize <<= 8;
        long avgSizePerIN = totalSize / totalINCount;
        return (int) ((obsoleteINCount * avgSizePerIN) >> 8);
    }

    /**
     * Returns an estimate of the total bytes that are obsolete.
     */
    public int getObsoleteSize()
        throws DatabaseException {

        if (totalSize > 0) {
            /* Leftover (non-IN non-LN) space is considered obsolete. */
            int leftoverSize = totalSize - (totalINSize + totalLNSize);
            int obsoleteSize = getObsoleteLNSize() +
                               getObsoleteINSize() +
                               leftoverSize;

            /*
             * Don't report more obsolete bytes than the total.  We may
             * calculate more than the total because of (intentional)
             * double-counting during recovery.
             */
            if (obsoleteSize > totalSize) {
                obsoleteSize = totalSize;
            }
            return obsoleteSize;
        } else {
            return 0;
        }
    }

    /**
     * Returns the total number of entries counted.  This value is guaranted
     * to increase whenever the tracking information about a file changes.  It
     * is used a key discriminator for FileSummaryLN records.
     */
    public int getEntriesCounted() {
        return totalCount + obsoleteLNCount + obsoleteINCount;
    }

    /**
     * Returns the number of non-obsolete LN and IN entries.
     */
    public int getNonObsoleteCount() {
        return totalLNCount +
               totalINCount -
               obsoleteLNCount -
               obsoleteINCount;
    }

    /**
     * Reset all totals to zero.
     */
    public void reset() {

        totalCount = 0;
        totalSize = 0;
        totalINCount = 0;
        totalINSize = 0;
        totalLNCount = 0;
        totalLNSize = 0;
        obsoleteINCount = 0;
        obsoleteLNCount = 0;
    }

    /**
     * Add the totals of the given summary object to the totals of this object.
     */
    public void add(FileSummary o) {

        totalCount += o.totalCount;
        totalSize += o.totalSize;
        totalINCount += o.totalINCount;
        totalINSize += o.totalINSize;
        totalLNCount += o.totalLNCount;
        totalLNSize += o.totalLNSize;
        obsoleteINCount += o.obsoleteINCount;
        obsoleteLNCount += o.obsoleteLNCount;
    }

    /**
     * @see LogWritable#getLogSize
     */
    public int getLogSize() {

        return 8 * LogUtils.getIntLogSize();
    }

    /**
     * @see LogWritable#writeToLog
     */
    public void writeToLog(ByteBuffer buf) {

        LogUtils.writeInt(buf, totalCount);
        LogUtils.writeInt(buf, totalSize);
        LogUtils.writeInt(buf, totalINCount);
        LogUtils.writeInt(buf, totalINSize);
        LogUtils.writeInt(buf, totalLNCount);
        LogUtils.writeInt(buf, totalLNSize);
        LogUtils.writeInt(buf, obsoleteINCount);
        LogUtils.writeInt(buf, obsoleteLNCount);
    }

    /**
     * @see LogReadable#readFromLog
     */
    public void readFromLog(ByteBuffer buf, byte entryTypeVersion) {

        totalCount = LogUtils.readInt(buf);
        totalSize = LogUtils.readInt(buf);
        totalINCount = LogUtils.readInt(buf);
        totalINSize = LogUtils.readInt(buf);
        totalLNCount = LogUtils.readInt(buf);
        totalLNSize = LogUtils.readInt(buf);
        obsoleteINCount = LogUtils.readInt(buf);
        if (obsoleteINCount == -1) {

            /*
             * If INs were not counted in an older log file written by 1.5.3 or
             * earlier, consider all INs to be obsolete.  This causes the file
             * to be cleaned, and then IN counting will be accurate.
             */
            obsoleteINCount = totalINCount;
        }
        obsoleteLNCount = LogUtils.readInt(buf);
    }

    /**
     * @see LogReadable#dumpLog
     */
    public void dumpLog(StringBuffer buf, boolean verbose) {

        buf.append("<summary totalCount=\"");
        buf.append(totalCount);
        buf.append("\" totalSize=\"");
        buf.append(totalSize);
        buf.append("\" totalINCount=\"");
        buf.append(totalINCount);
        buf.append("\" totalINSize=\"");
        buf.append(totalINSize);
        buf.append("\" totalLNCount=\"");
        buf.append(totalLNCount);
        buf.append("\" totalLNSize=\"");
        buf.append(totalLNSize);
        buf.append("\" obsoleteINCount=\"");
        buf.append(obsoleteINCount);
        buf.append("\" obsoleteLNCount=\"");
        buf.append(obsoleteLNCount);
        buf.append("\"/>");
    }

    /**
     * Never called.
     * @see LogReadable#getTransactionId
     */
    public long getTransactionId() {
	return -1;
    }

    /**
     * Never called.
     * @see LogReadable#logEntryIsTransactional
     */
    public boolean logEntryIsTransactional() {
	return false;
    }

    public String toString() {
        StringBuffer buf = new StringBuffer();
        dumpLog(buf, true);
        return buf.toString();
    }
}
