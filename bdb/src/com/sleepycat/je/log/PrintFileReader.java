/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2006 Oracle.  All rights reserved.
 *
 * $Id: PrintFileReader.java,v 1.11 2006/10/30 21:14:20 bostic Exp $
 */

package com.sleepycat.je.log;

import java.io.IOException;
import java.nio.ByteBuffer;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.log.entry.LogEntry;

/**
 * The PrintFileReader prints out the target log entries.
 */
public class PrintFileReader extends DumpFileReader {

    /**
     * Create this reader to start at a given LSN.
     */
    public PrintFileReader(EnvironmentImpl env,
			   int readBufferSize, 
			   long startLsn,
			   long finishLsn,
			   String entryTypes,
			   String txnIds,
			   boolean verbose)
	throws IOException, DatabaseException {

        super(env,
              readBufferSize,
              startLsn,
              finishLsn,
              entryTypes,
              txnIds,
              verbose);
    }

    /**
     * This reader prints the log entry item.
     */
    protected boolean processEntry(ByteBuffer entryBuffer)
        throws DatabaseException {

        /* Figure out what kind of log entry this is */
        LogEntryType lastEntryType =
            LogEntryType.findType(currentEntryTypeNum,
				  currentEntryTypeVersion);

        /* Print out a common header for each log item */
        StringBuffer sb = new StringBuffer();
        sb.append("<entry lsn=\"0x").append
            (Long.toHexString(readBufferFileNum));
        sb.append("/0x").append
            (Long.toHexString(currentEntryOffset));
        sb.append("\" type=\"").append(lastEntryType);
        if (LogEntryType.isProvisional(currentEntryTypeVersion)) {
            sb.append("\" isProvisional=\"true");
        }
        sb.append("\" prev=\"0x");
        sb.append(Long.toHexString(currentEntryPrevOffset));
        if (verbose) {
            sb.append("\" size=\"").append(currentEntrySize);
            sb.append("\" cksum=\"").append(currentEntryChecksum);
        }
        sb.append("\">");

        /* Read the entry and dump it into a string buffer. */
	LogEntry entry = lastEntryType.getSharedLogEntry();
        entry.readEntry(entryBuffer, currentEntrySize,
                        currentEntryTypeVersion, true);
	boolean dumpIt = true;
	if (targetTxnIds.size() > 0) {
	    if (entry.isTransactional()) {
		if (!targetTxnIds.contains
		    (new Long(entry.getTransactionId()))) {
		    /* Not in the list of txn ids. */
		    dumpIt = false;
		}
	    } else {
		/* If -tx spec'd and not a transactional entry, don't dump. */
		dumpIt = false;
	    }
	}

	if (dumpIt) {
	    entry.dumpEntry(sb, verbose);
	    sb.append("</entry>");
	    System.out.println(sb.toString());
	}
        
        return true;
    }
}
