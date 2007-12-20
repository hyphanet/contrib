/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: TreeUtils.java,v 1.23.2.3 2007/11/20 13:32:35 cwl Exp $
 */

package com.sleepycat.je.tree;

import com.sleepycat.je.tree.Key.DumpType;

/**
 * Miscellaneous Tree utilities.
 */
public class TreeUtils {

    static private final String SPACES =
	"                                " +
	"                                " +
	"                                " +
	"                                ";

    /**
     * For tree dumper.
     */
    public static String indent(int nSpaces) {
	return SPACES.substring(0, nSpaces);
    }

    public static String dumpByteArray(byte[] b) {
        StringBuffer sb = new StringBuffer();
        if (b != null) {
	    if (Key.DUMP_TYPE == DumpType.HEX ||
		Key.DUMP_TYPE == DumpType.BINARY) {
		for (int i = 0; i < b.length; i++) {
		    if (Key.DUMP_TYPE == DumpType.HEX) {
			sb.append(Integer.toHexString(b[i] & 0xFF));
		    } else {
			sb.append(b[i] & 0xFF);
		    }
		    sb.append(" ");
		}
	    } else if (Key.DUMP_TYPE == DumpType.TEXT) {
		sb.append(new String(b));
	    } else if (Key.DUMP_TYPE == DumpType.OBFUSCATE) {
		int len = b.length;
		sb.append("[").append(len).
		    append(len == 1 ? " byte]" : " bytes]");
	    }
        } else {
            sb.append("null");
        }
        return sb.toString();
    }
}
