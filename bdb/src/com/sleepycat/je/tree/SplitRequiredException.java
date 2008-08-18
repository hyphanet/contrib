/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: SplitRequiredException.java,v 1.8 2008/05/20 17:52:36 linda Exp $
 */

package com.sleepycat.je.tree;

/**
 * Indicates that we need to return to the top of the tree in order to
 * do a forced splitting pass.
 */
@SuppressWarnings("serial")
class SplitRequiredException extends Exception {
    public SplitRequiredException(){
    }
}
