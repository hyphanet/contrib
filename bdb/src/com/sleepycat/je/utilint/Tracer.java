/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: Tracer.java,v 1.43.2.1 2007/02/01 14:49:54 cwl Exp $
 */

package com.sleepycat.je.utilint;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.logging.Level;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.config.ConfigParam;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.log.LogEntryType;
import com.sleepycat.je.log.LogManager;
import com.sleepycat.je.log.LogUtils;
import com.sleepycat.je.log.Loggable;
import com.sleepycat.je.log.entry.SingleItemEntry;

/**
 * The Tracer generates debug messages that are sent to the java.util.Logging
 * facility. There are three log handlers set up for logging -- the database
 * log itself, an output file, and stdout (the "console").  By default, only
 * the database file is enabled.
 */
public class Tracer implements Loggable {

    /* 
     * Name pattern for tracing output that's been directed into a log file by
     * enabling the file handler.
     */
    public static final String INFO_FILES = "je.info";

    /* 
     * Contents of a debug message. 
     */
    private Timestamp time;
    private String msg;

    /**
     * Create a new debug record.
     */
    public Tracer(String msg) {
        this.time = getCurrentTimestamp();
        this.msg = msg;
    }

    /**
     * Create trace record that will be filled in from the log.
     */
    public Tracer() {
    }

    /*
     * Static utility methods for submitting information for logging in the
     * text log file, the database log, and stdout.
     */

    /**
     * Logger method for recording a general message.
     */
    public static void trace(Level logLevel,
                             EnvironmentImpl envImpl,
                             String msg) {
        envImpl.getLogger().log(logLevel, msg);
    }
    
    /**
     * Logger method for recording an exception and stacktrace.
     */
    public static void trace(EnvironmentImpl envImpl,
                             String sourceClass,
                             String sourceMethod,
                             String msg,
                             Throwable t) {

        /*
         * Give it to the Logger, which will funnel it to stdout and/or the
         * text file and/or the database log file
         */
        envImpl.getLogger().logp(Level.SEVERE,
				 sourceClass,
				 sourceMethod,
				 msg + "\n" + Tracer.getStackTrace(t));
    }

    /** 
     * Parse a logging level config parameter, and return a more explanatory
     * error message if it doesn't parse.
     */
    public static Level parseLevel(EnvironmentImpl envImpl,
                                   ConfigParam configParam) 
        throws DatabaseException {
        
        Level level = null;
        try {
            String levelVal = envImpl.getConfigManager().get(configParam);
            level = Level.parse(levelVal);
        } catch (IllegalArgumentException e) {
            throw new DatabaseException("Problem parsing parameter " +
					configParam.getName() +
					": " + e.getMessage(), e);
        }
        return level;
    }
             
    /*
     * Helpers
     */
    public String getMessage() {
        return msg;
    }

    /**
     * @return a timestamp for "now"
     */
    private Timestamp getCurrentTimestamp() {
        Calendar cal = Calendar.getInstance();
        return new Timestamp(cal.getTime().getTime());
    }

    /**
     * @return the stacktrace for an exception
     */
    public static String getStackTrace(Throwable t) {
        StringWriter s = new StringWriter();
        t.printStackTrace(new PrintWriter(s));
        String stackTrace = s.toString();
        stackTrace = stackTrace.replaceAll("<", "&lt;");
        stackTrace = stackTrace.replaceAll(">", "&gt;");
        return stackTrace;
    }
        
    /*
     * Logging support 
     */

    /**
     * Convenience method to create a log entry containing this trace msg.
     */
    public long log(LogManager logManager) 
        throws DatabaseException {
        return logManager.log(new SingleItemEntry(LogEntryType.LOG_TRACE,
                                                  this));
    }

    /**
     * @see Loggable#getLogSize()
     */
    public int getLogSize() {
        return (LogUtils.getTimestampLogSize() +
                LogUtils.getStringLogSize(msg));
    }

    /**
     * @see Loggable#writeToLog
     */
    public void writeToLog(ByteBuffer logBuffer) {
        /* Load the header. */
        LogUtils.writeTimestamp(logBuffer, time);
        LogUtils.writeString(logBuffer, msg);
    }

    /**
     * @see Loggable#readFromLog
     */
    public void readFromLog(ByteBuffer itemBuffer, byte entryTypeVersion) {
        /* See how many we want to read direct. */
        time = LogUtils.readTimestamp(itemBuffer);
        msg = LogUtils.readString(itemBuffer);
    }

    /**
     * @see Loggable#dumpLog
     */
    public void dumpLog(StringBuffer sb, boolean verbose) {
        sb.append("<Dbg time=\"");
        sb.append(time);
        sb.append("\">");
        sb.append("<msg val=\"");
        sb.append(msg);
        sb.append("\"/>");
        sb.append("</Dbg>");
    }

    /**
     * @see Loggable#getTransactionId
     */
    public long getTransactionId() {
	return 0;
    }

    public String toString() {
        return (time + "/" + msg);
    }

    /**
     * For unit tests.
     */

    /**
     *  Just in case it's ever used as a hash key.
     */
    public int hashCode() {
        return toString().hashCode();
    }

    /**
     * Override Object.equals
     */
    public boolean equals(Object obj) {
        /* Same instance? */
        if (this == obj) {
            return true;
        }

        /* Is it another Tracer? */
        if (!(obj instanceof Tracer)) {
            return false;
        }

        /* 
	 * We could compare all the fields individually, but since they're all
	 * placed in our toString() method, we can just compare the String
	 * version of each offer.
	 */
        return (toString().equals(obj.toString()));
    }
}
