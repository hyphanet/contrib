/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: CmdUtil.java,v 1.21.2.1 2007/02/01 14:49:54 cwl Exp $
 */

package com.sleepycat.je.utilint;

import java.io.File;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.EnvironmentImpl;

/**
 * Convenience methods for command line utilities.
 */
public class CmdUtil {
    public static String getArg(String [] argv, int whichArg) 
        throws IllegalArgumentException {

        if (whichArg < argv.length) {
            return argv[whichArg];
        } else {
            throw new IllegalArgumentException();
        }
    }

    /**
     * Parse a string into a long. If the string starts with 0x, this is a hex
     * number, else it's decimal.
     */
    public static long readLongNumber(String longVal) {
        if (longVal.startsWith("0x")) {
            return Long.parseLong(longVal.substring(2), 16);
        } else {
            return Long.parseLong(longVal);
        }
    }

    private static final String printableChars =
	"!\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ" +
	"[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~";

    public static void formatEntry(StringBuffer sb,
                                   byte[] entryData,
                                   boolean formatUsingPrintable) {
	for (int i = 0; i < entryData.length; i++) {
	    int b = entryData[i] & 0xff;
	    if (formatUsingPrintable) {
		if (isPrint(b)) {
		    if (b == 0134) {  /* backslash */
			sb.append('\\');
		    }
		    sb.append(printableChars.charAt(b - 33));
		} else {
		    sb.append('\\');
		    String hex = Integer.toHexString(b);
		    if (b < 16) {
			sb.append('0');
		    }
		    sb.append(hex);
		}
	    } else {
		String hex = Integer.toHexString(b);
		if (b < 16) {
		    sb.append('0');
		}
		sb.append(hex);
	    }
	}
    }

    private static boolean isPrint(int b) {
	return (b < 0177) && (040 < b);
    }

    /**
     * Create an environment suitable for utilities. Utilities should in
     * general send trace output to the console and not to the db log.
     */
    public static EnvironmentImpl makeUtilityEnvironment(File envHome,
							 boolean readOnly)
        throws DatabaseException {
        
        EnvironmentConfig config = new EnvironmentConfig();
        config.setReadOnly(readOnly);
        
        /* Don't debug log to the database log. */
        config.setConfigParam(EnvironmentParams.JE_LOGGING_DBLOG.getName(),
			      "false");
        /* Do debug log to the console. */
        config.setConfigParam(EnvironmentParams.JE_LOGGING_CONSOLE.getName(),
			      "true");

        /* Set logging level to only show errors. */
        config.setConfigParam(EnvironmentParams.JE_LOGGING_LEVEL.getName(),
			      "SEVERE");

        /* Don't run recovery. */
        config.setConfigParam(EnvironmentParams.ENV_RECOVERY.getName(),
			      "false");

	EnvironmentImpl envImpl = new EnvironmentImpl(envHome, config);
	return envImpl;
    }

    /**
     * Returns a description of the java command for running a utility, without
     * arguments.  For utilities the last name of the class name can be
     * specified when "-jar je.jar" is used.
     */
    public static String getJavaCommand(Class cls) {

        String clsName = cls.getName();
        String lastName = clsName.substring(clsName.lastIndexOf('.') + 1);

        return "java { " + cls.getName() + " | -jar je-<version>.jar " + lastName + " }";
    }
}
