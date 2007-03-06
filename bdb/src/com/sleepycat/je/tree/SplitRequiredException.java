/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2006 Oracle.  All rights reserved.
 *
 * $Id: SplitRequiredException.java,v 1.5 2006/10/30 21:14:26 bostic Exp $
 */

package com.sleepycat.je.tree;

/**
 * Indicates that we need to return to the top of the tree in order to
 * do a forced splitting pass.
 */
class SplitRequiredException extends Exception {
    public SplitRequiredException(){
    }
}
