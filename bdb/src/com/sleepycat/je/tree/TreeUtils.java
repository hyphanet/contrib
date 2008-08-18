/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: TreeUtils.java,v 1.27 2008/01/07 14:28:56 cwl Exp $
 */

package com.sleepycat.je.tree;

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
}
