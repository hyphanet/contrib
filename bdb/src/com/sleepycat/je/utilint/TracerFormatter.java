/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: TracerFormatter.java,v 1.5 2008/05/13 01:44:54 cwl Exp $
 */

package com.sleepycat.je.utilint;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * Formatter for debug log file output.
 */
public class TracerFormatter extends Formatter {

    private Date date = new Date();
    private DateFormat formatter;

    public TracerFormatter() {
        date = new Date();
        formatter = makeDateFormat();
    }

    /* The date and formatter are not thread safe. */
    private synchronized String getDate(long millis) {
	date.setTime(millis);
        return formatter.format(date);
    }

    /**
     * Format the log record in this form:
     *   <short date> <short time> <message level> <message>
     * @param record the log record to be formatted.
     * @return a formatted log record
     */
    public String format(LogRecord record) {
	StringBuilder sb = new StringBuilder();

        String dateVal = getDate(record.getMillis());
        sb.append(dateVal);
	sb.append(" ");
	sb.append(record.getLevel().getLocalizedName());
	sb.append(" ");
        sb.append(formatMessage(record));
        sb.append("\n");

	if (record.getThrown() != null) {
	    try {
	        StringWriter sw = new StringWriter();
	        PrintWriter pw = new PrintWriter(sw);
	        record.getThrown().printStackTrace(pw);
	        pw.close();
		sb.append(sw.toString());
	    } catch (Exception ex) {
                /* Ignored. */
	    }
	}
	return sb.toString();
    }

    /* For unit test support */
    public static DateFormat makeDateFormat() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS:z");
    }
}
