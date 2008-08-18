/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: JEVersion.java,v 1.109 2008/06/04 18:46:47 cwl Exp $
 */

package com.sleepycat.je;

/**
 * Berkeley DB Java Edition version information.  Versions consist of major,
 * minor and patch numbers.
 *
 * There is one JEVersion object per running JVM and it may be accessed using
 * the static field JEVersion.CURRENT_VERSION.
 */
public class JEVersion {

    /**
     * Release version.
     */
    public static final JEVersion CURRENT_VERSION =
        new JEVersion(3, 3, 62, null);

    private int majorNum;
    private int minorNum;
    private int patchNum;
    private String name;

    private JEVersion(int majorNum, int minorNum, int patchNum, String name) {
        this.majorNum = majorNum;
        this.minorNum = minorNum;
        this.patchNum = patchNum;
        this.name = name;
    }

    public String toString() {
        return getVersionString();
    }

    /**
     * Major number of the release version.
     *
     * @return The major number of the release version.
     */
    public int getMajor() {
        return majorNum;
    }

    /**
     * Minor number of the release version.
     *
     * @return The minor number of the release version.
     */
    public int getMinor() {
        return minorNum;
    }

    /**
     * Patch number of the release version.
     *
     * @return The patch number of the release version.
     */
    public int getPatch() {
        return patchNum;
    }

    /**
     * The numeric version string, without the patch tag.
     *
     * @return The release version
     */
    public String getNumericVersionString() {
        StringBuffer version = new StringBuffer();
        version.append(majorNum).append(".");
        version.append(minorNum).append(".");
        version.append(patchNum);
        return version.toString();
    }

    /**
     * Release version, suitable for display.
     *
     * @return The release version, suitable for display.
     */
    public String getVersionString() {
        StringBuffer version = new StringBuffer();
        version.append(majorNum).append(".");
        version.append(minorNum).append(".");
        version.append(patchNum);
	if (name != null) {
	    version.append(" (");
	    version.append(name).append(")");
	}
        return version.toString();
    }
}
