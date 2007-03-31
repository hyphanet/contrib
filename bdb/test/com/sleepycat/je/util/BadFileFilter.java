/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: BadFileFilter.java,v 1.12.2.1 2007/02/01 14:50:23 cwl Exp $
 */

package com.sleepycat.je.util;

import java.io.File;
import java.io.FilenameFilter;
import java.util.StringTokenizer;

public class BadFileFilter implements FilenameFilter {

    /**
     * Accept files of this format:
     * <nnnnnnnn>.bad.<n>
     */
    public boolean accept(File dir, String name) {
        boolean ok = false;
        StringTokenizer tokenizer = new StringTokenizer(name, ".");
        /* There should be two parts. */
        if (tokenizer.countTokens() == 3) {
            String fileNumber = tokenizer.nextToken();
            String fileSuffix = tokenizer.nextToken();
            String repeat = tokenizer.nextToken();

            /* Check the length and the suffix. */
            if ((fileNumber.length() == 8) &&
                (fileSuffix.equals("bad"))) {

                /* The first and third parts should be a numbers. */
                try {
                    Integer.parseInt(fileNumber);
                    Integer.parseInt(repeat);
                    ok = true;
                } catch (NumberFormatException e) {
                    ok = false;
                }
            }
        }

        return ok;
    }
}

