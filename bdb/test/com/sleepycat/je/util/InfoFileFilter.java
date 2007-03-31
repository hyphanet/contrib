/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: InfoFileFilter.java,v 1.11.2.1 2007/02/01 14:50:23 cwl Exp $
 */

package com.sleepycat.je.util;

import java.io.File;
import java.io.FilenameFilter;
import java.util.StringTokenizer;

public class InfoFileFilter implements FilenameFilter {

    /**
     * Accept files of this format:
     * je.info.#
     */
    public boolean accept(File dir, String name) {
        boolean ok = false;
        StringTokenizer tokenizer = new StringTokenizer(name, ".");
        // there should be two parts
        if (tokenizer.countTokens() == 3) {
            String filePrefix = tokenizer.nextToken();
            String fileSuffix = tokenizer.nextToken();
            String repeat = tokenizer.nextToken();

            // check the length and the suffix
            if (filePrefix.equals("je") && fileSuffix.equals("info")) {
                // The last part should be a number
                try {
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

