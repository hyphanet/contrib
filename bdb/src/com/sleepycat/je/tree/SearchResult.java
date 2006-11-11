/*
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2006
 *      Oracle Corporation.  All rights reserved.
 *
 * $Id: SearchResult.java,v 1.11 2006/09/12 19:16:57 cwl Exp $:
 */

package com.sleepycat.je.tree;

/**
 * Contains the result of a tree search
 */
public class SearchResult {
    public boolean exactParentFound;
    public boolean keepSearching;
    /* 
     * Set to true if a search stopped because a child was not resident, and
     * we are doing a do-not-fetch kind of search.
     */
    public boolean childNotResident; 
    public IN parent;
    public int index;
	
    public SearchResult() {
        exactParentFound = false;
        keepSearching = true;
        parent = null;
        index = -1;
        childNotResident = false;
    }

    public String toString() {
        return
            "exactParentFound="+ exactParentFound +
            " keepSearching=" + keepSearching +
            " parent=" + ((parent == null)? "null":
                          Long.toString(parent.getNodeId())) +
            " index=" + index +
            " childNotResident=" + childNotResident;
    }
}
