/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2006 Oracle.  All rights reserved.
 *
 * $Id: CursorsExistException.java,v 1.6 2006/10/30 21:14:25 bostic Exp $
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
