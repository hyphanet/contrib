/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: DbSpace.java,v 1.23.2.2 2007/03/08 17:26:42 mark Exp $
 */

package com.sleepycat.je.util;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedMap;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.JEVersion;
import com.sleepycat.je.cleaner.FileSummary;
import com.sleepycat.je.cleaner.UtilizationProfile;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.log.UtilizationFileReader;
import com.sleepycat.je.utilint.CmdUtil;

public class DbSpace {

    private static final String USAGE =
	"usage: " + CmdUtil.getJavaCommand(DbSpace.class) + "\n" +
        "       -h <dir> # environment home directory\n" +
        "       [-q]     # quiet, print grand totals only\n" +
        "       [-u]     # sort by utilization\n" +
        "       [-d]     # dump file summary details\n" +
        "       [-r]     # recalculate utilization (reads entire log)\n" +
        "       [-V]     # print JE version number";

    public static void main(String argv[])
	throws DatabaseException {

	DbSpace space = new DbSpace();
	space.parseArgs(argv);

        EnvironmentConfig envConfig = new EnvironmentConfig();
        envConfig.setReadOnly(true);
        Environment env = new Environment(space.envHome, envConfig);
        space.envImpl = DbInternal.envGetEnvironmentImpl(env);

	try {
	    space.print(System.out);
	    System.exit(0);
	} catch (Throwable e) {
            e.printStackTrace(System.err);
            System.exit(1);
	} finally {
            try {
                env.close();
            } catch (Throwable e) {
                e.printStackTrace(System.err);
                System.exit(1);
            }
	}
    }

    private File envHome = null;
    private EnvironmentImpl envImpl;
    private boolean quiet = false;
    private boolean sorted = false;
    private boolean details = false;
    private boolean recalc = false;

    private DbSpace() {
    }

    public DbSpace(Environment env,
		   boolean quiet,
                   boolean details,
                   boolean sorted) {
        this(DbInternal.envGetEnvironmentImpl(env), quiet, details, sorted);
    }

    public DbSpace(EnvironmentImpl envImpl,
		   boolean quiet,
                   boolean details,
                   boolean sorted) {
	this.envImpl = envImpl;
	this.quiet = quiet;
        this.details = details;
	this.sorted = sorted;
    }

    private void printUsage(String msg) {
        if (msg != null) {
            System.err.println(msg);
        }
	System.err.println(USAGE);
	System.exit(-1);
    }

    private void parseArgs(String argv[]) {

	int argc = 0;
	int nArgs = argv.length;
        
        if (nArgs == 0) {
	    printUsage(null);
            System.exit(0);
        }

	while (argc < nArgs) {
	    String thisArg = argv[argc++];
	    if (thisArg.equals("-q")) {
		quiet = true;
            } else if (thisArg.equals("-u")) {
		sorted = true;
            } else if (thisArg.equals("-d")) {
		details = true;
            } else if (thisArg.equals("-r")) {
		recalc = true;
	    } else if (thisArg.equals("-V")) {
		System.out.println(JEVersion.CURRENT_VERSION);
		System.exit(0);
	    } else if (thisArg.equals("-h")) {
		if (argc < nArgs) {
		    envHome = new File(argv[argc++]);
		} else {
		    printUsage("-h requires an argument");
		}
	    }
	}

	if (envHome == null) {
	    printUsage("-h is a required argument");
	}
    }

    public void print(PrintStream out)
	throws IOException, DatabaseException {

        UtilizationProfile profile = envImpl.getUtilizationProfile();
        SortedMap map = profile.getFileSummaryMap(false);
        Map recalcMap =
            recalc ? UtilizationFileReader.calcFileSummaryMap(envImpl)
                   : null;
        int fileIndex = 0;

        Summary totals = new Summary();
        Summary[] summaries = null;
        if (!quiet) {
            summaries = new Summary[map.size()];
        }

	Iterator iter = map.entrySet().iterator();
	while (iter.hasNext()) {
	    Map.Entry entry = (Map.Entry) iter.next();
	    Long fileNum = (Long) entry.getKey();
	    FileSummary fs = (FileSummary) entry.getValue();
            FileSummary recalcFs = null;
            if (recalcMap != null) {
                 recalcFs = (FileSummary) recalcMap.get(fileNum);
            }
            Summary summary = new Summary(fileNum, fs, recalcFs);
            if (summaries != null) {
                summaries[fileIndex] = summary;
            }
            if (details) {
                out.println
                    ("File 0x" + Long.toHexString(fileNum.longValue()) +
                     ": " + fs);
                if (recalcMap != null) {
                    out.println
                        ("Recalculated File 0x" +
                         Long.toHexString(fileNum.longValue()) +
                         ": " + recalcFs);
                }
            }
            totals.add(summary);
            fileIndex += 1;
        }

        if (details) {
            out.println();
        }
        out.println(recalc ? Summary.RECALC_HEADER : Summary.HEADER);

        if (summaries != null) {
            if (sorted) {
                Arrays.sort(summaries);
            }
            for (int i = 0; i < summaries.length; i += 1) {
                summaries[i].print(out, recalc);
            }
        }

        totals.print(out, recalc);
    }

    private static class Summary implements Comparable {

        static final String HEADER = "  File    Size (KB)  % Used\n" +
                                     "--------  ---------  ------";
                                   // 12345678  123456789     123
                                   //         12         12345
                                   // TOTALS:

        static final String RECALC_HEADER =
                   "  File    Size (KB)  % Used  % Used (recalculated)\n" +
                   "--------  ---------  ------  ------";
                 // 12345678  123456789     123     123
                 //         12         12345   12345
                 // TOTALS:

        Long fileNum;
        long totalSize;
        long obsoleteSize;
        long recalcObsoleteSize;

        Summary() {}

        Summary(Long fileNum, FileSummary summary, FileSummary recalcSummary)
            throws DatabaseException {

            this.fileNum = fileNum;
            totalSize = summary.totalSize;
            obsoleteSize = summary.getObsoleteSize();
            if (recalcSummary != null) {
                recalcObsoleteSize = recalcSummary.getObsoleteSize();
            }
        }

        public int compareTo(Object other) {
            Summary o = (Summary) other;
            return utilization() - o.utilization();
        }

	public boolean equals(Object o) {
	    if (o == null) {
		return false;
	    }

	    if (o instanceof Summary) {
		return utilization() == ((Summary) o).utilization();
	    } else {
		return false;
	    }
	}

        void add(Summary o) {
            totalSize += o.totalSize;
            obsoleteSize += o.obsoleteSize;
            recalcObsoleteSize += o.recalcObsoleteSize;
        }

        void print(PrintStream out, boolean recalc) {
            if (fileNum != null) {
                pad(out, Long.toHexString(fileNum.longValue()), 8, '0');
            } else {
                out.print(" TOTALS ");
            }
            int kb = (int) (totalSize / 1024);
            out.print("  ");
            pad(out, Integer.toString(kb), 9, ' ');
            out.print("     ");
            pad(out, Integer.toString(utilization()), 3, ' ');
            if (recalc) {
                out.print("     ");
                pad(out, Integer.toString(recalcUtilization()), 3, ' ');
            }
            out.println();
        }

        int utilization() {
            return UtilizationProfile.utilization(obsoleteSize, totalSize);
        }

        int recalcUtilization() {
            return UtilizationProfile.utilization
                    (recalcObsoleteSize, totalSize);
        }

        private void pad(PrintStream out, String val, int digits,
                           char padChar) {
            int padSize = digits - val.length();
            for (int i = 0; i < padSize; i += 1) {
                out.print(padChar);
            }
            out.print(val);
        }
    }
}
