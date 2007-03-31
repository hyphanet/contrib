/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: PrintFileReader.java,v 1.12.2.2 2007/03/08 22:32:55 mark Exp $
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
            LogEntryType.findType(currentEntryHeader.getType(),
				  currentEntryHeader.getVersion());

        /* Print out a common header for each log item */
        StringBuffer sb = new StringBuffer();
        sb.append("<entry lsn=\"0x").append
            (Long.toHexString(readBufferFileNum));
        sb.append("/0x").append
            (Long.toHexString(currentEntryOffset));
        sb.append("\" type=\"").append(lastEntryType);
        if (LogEntryType.isEntryProvisional(currentEntryHeader.getType())) {
            sb.append("\" isProvisional=\"true");
        }
        sb.append("\" prev=\"0x");
        sb.append(Long.toHexString(currentEntryHeader.getPrevOffset()));
        if (verbose) {
            sb.append("\" size=\"").append(currentEntryHeader.getItemSize());
            sb.append("\" cksum=\"").append(currentEntryHeader.getChecksum());
        }
        sb.append("\">");

        /* Read the entry and dump it into a string buffer. */
	LogEntry entry = lastEntryType.getSharedLogEntry();
        readEntry(entry, entryBuffer, true); // readFullItem
	boolean dumpIt = true;
	if (targetTxnIds.size() > 0) {
	    if (lastEntryType.isTransactional()) {
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
