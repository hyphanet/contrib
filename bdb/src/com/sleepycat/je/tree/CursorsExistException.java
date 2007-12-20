/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: CursorsExistException.java,v 1.6.2.2 2007/11/20 13:32:35 cwl Exp $
 */

package com.sleepycat.je.tree;


/**
 * Error to indicate that a bottom level BIN has cursors on it during a
 * delete subtree operation.
 */
public class CursorsExistException extends Exception {

    /*
     * Throw this static instance, in order to reduce the cost of
     * fill in the stack trace.
     */
    public static final CursorsExistException CURSORS_EXIST =
        new CursorsExistException();

    private CursorsExistException() {
    }
}
