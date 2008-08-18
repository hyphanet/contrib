/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: KeyLocation.java,v 1.6 2008/01/07 14:28:59 cwl Exp $
 */

package com.sleepycat.persist.impl;

/**
 * Holder for the input and format of a key.  Used when copying secondary keys.
 * Returned by RecordInput.getKeyLocation().
 *
 * @author Mark Hayes
 */
class KeyLocation {

    RecordInput input;
    Format format;

    KeyLocation(RecordInput input, Format format) {
        this.input = input;
        this.format = format;
    }
}
