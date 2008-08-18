/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2008 Oracle.  All rights reserved.
 *
 * $Id: DbPrintLog.java,v 1.46 2008/01/07 14:28:57 cwl Exp $
 */

package com.sleepycat.je.util;

import java.io.File;
import java.io.IOException;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.log.DumpFileReader;
import com.sleepycat.je.log.FileManager;
import com.sleepycat.je.log.PrintFileReader;
import com.sleepycat.je.log.StatsFileReader;
import com.sleepycat.je.tree.Key;
import com.sleepycat.je.tree.Key.DumpType;
import com.sleepycat.je.utilint.CmdUtil;
import com.sleepycat.je.utilint.DbLsn;

/**
 * Dumps the contents of the log in XML format to System.out.
 *
 * <p>To print an environment log:</p>
 *
 * <pre>
 *      DbPrintLog.main(argv);
 * </pre>
 */
public class DbPrintLog {

    /**
     * Dump a JE log into human readable form.
     */
    private void dump(File envHome,
		      String entryTypes,
		      String txnIds,
		      long startLsn,
		      long endLsn,
		      boolean verbose,
                      boolean stats)
        throws IOException, DatabaseException {

        EnvironmentImpl env =
	    CmdUtil.makeUtilityEnvironment(envHome, true);
        FileManager fileManager = env.getFileManager();
        fileManager.setIncludeDeletedFiles(true);
        int readBufferSize =
            env.getConfigManager().getInt
            (EnvironmentParams.LOG_ITERATOR_READ_SIZE);

        /* Make a reader. */
        DumpFileReader reader = null;
        if (stats) {
            reader = new StatsFileReader(env, readBufferSize, startLsn, endLsn,
                                         entryTypes, txnIds, verbose);
        } else {
            reader =  new PrintFileReader(env, readBufferSize, startLsn,
					  endLsn, entryTypes, txnIds, verbose);
        }

        /* Enclose the output in a tag to keep proper XML syntax. */
        System.out.println("<DbPrintLog>");
        while (reader.readNextEntry()) {
        }
        reader.summarize();
        System.out.println("</DbPrintLog>");
        env.close();
    }

    /**
     * The main used by the DbPrintLog utility.
     *
     * @param argv An array of command line arguments to the DbPrintLog
     * utility.
     *
     * <pre>
     * usage: java { com.sleepycat.je.util.DbPrintLog | -jar
     * je-&lt;version&gt;.jar DbPrintLog }
     *  -h &lt;envHomeDir&gt;
     *  -e  &lt;end file number, in hex&gt;
     *  -k  &lt;binary|hex|text|obfuscate&gt; (format for dumping the key)
     *  -s  &lt;start file number, in hex&gt;
     *  -tx &lt;targeted txn ids, comma separated
     *  -ty &lt;targeted entry types, comma separated
     *  -v  &lt;true | false&gt; (verbose option
     *      If true, full entry is printed,
     *      else short version. True by default.)
     * All arguments are optional
     * </pre>
     */
    public static void main(String[] argv) {
        try {
            int whichArg = 0;
            String entryTypes = null;
            String txnIds = null;
            long startLsn = DbLsn.NULL_LSN;
            long endLsn = DbLsn.NULL_LSN;
            boolean verbose = true;
            boolean stats = false;

            /* Default to looking in current directory. */
            File envHome = new File(".");
            Key.DUMP_TYPE = DumpType.BINARY;

            while (whichArg < argv.length) {
                String nextArg = argv[whichArg];
                if (nextArg.equals("-h")) {
                    whichArg++;
                    envHome = new File(CmdUtil.getArg(argv, whichArg));
                } else if (nextArg.equals("-ty")) {
                    whichArg++;
                    entryTypes = CmdUtil.getArg(argv, whichArg);
                } else if (nextArg.equals("-tx")) {
                    whichArg++;
                    txnIds = CmdUtil.getArg(argv, whichArg);
                } else if (nextArg.equals("-s")) {
                    whichArg++;
		    String arg = CmdUtil.getArg(argv, whichArg);
		    int slashOff = arg.indexOf("/");
		    if (slashOff < 0) {
			long startFileNum = CmdUtil.readLongNumber(arg);
			startLsn = DbLsn.makeLsn(startFileNum, 0);
		    } else {
			long startFileNum =
			    CmdUtil.readLongNumber(arg.substring(0, slashOff));
			long startOffset = CmdUtil.readLongNumber
			    (arg.substring(slashOff + 1));
			startLsn = DbLsn.makeLsn(startFileNum, startOffset);
		    }
                } else if (nextArg.equals("-e")) {
                    whichArg++;
		    String arg = CmdUtil.getArg(argv, whichArg);
		    int slashOff = arg.indexOf("/");
		    if (slashOff < 0) {
			long endFileNum = CmdUtil.readLongNumber(arg);
			endLsn = DbLsn.makeLsn(endFileNum, 0);
		    } else {
			long endFileNum =
			    CmdUtil.readLongNumber(arg.substring(0, slashOff));
			long endOffset = CmdUtil.readLongNumber
			    (arg.substring(slashOff + 1));
			endLsn = DbLsn.makeLsn(endFileNum, endOffset);
		    }
                } else if (nextArg.equals("-k")) {
                    whichArg++;
                    String dumpType = CmdUtil.getArg(argv, whichArg);
                    if (dumpType.equalsIgnoreCase("text")) {
                        Key.DUMP_TYPE = DumpType.TEXT;
                    } else if (dumpType.equalsIgnoreCase("hex")) {
			Key.DUMP_TYPE = DumpType.HEX;
                    } else if (dumpType.equalsIgnoreCase("binary")) {
			Key.DUMP_TYPE = DumpType.BINARY;
                    } else if (dumpType.equalsIgnoreCase("obfuscate")) {
			Key.DUMP_TYPE = DumpType.OBFUSCATE;
		    } else {
			System.err.println
			    (dumpType +
			     " is not a supported dump format type.");
		    }
                } else if (nextArg.equals("-q")) {
                    whichArg++;
                    verbose = false;
                } else if (nextArg.equals("-S")) {
                    whichArg++;
                    stats = true;
                } else {
		    System.err.println
                        (nextArg + " is not a supported option.");
                    usage();
		    System.exit(-1);
                }
                whichArg++;
            }

            DbPrintLog printer = new DbPrintLog();
            printer.dump(envHome, entryTypes, txnIds,
			 startLsn, endLsn, verbose, stats);

        } catch (Throwable e) {
            e.printStackTrace();
            System.out.println(e.getMessage());
            usage();
            System.exit(1);
        }
    }

    private static void usage() {
        System.out.println("Usage: " +
                           CmdUtil.getJavaCommand(DbPrintLog.class));
        System.out.println(" -h  <envHomeDir>");
        System.out.println(" -e  <end file number or LSN, in hex>");
        System.out.println(" -k  <binary|text|hex|obfuscate> " +
			   "(format for dumping the key)");
        System.out.println(" -s  <start file number or LSN, in hex>");
        System.out.println(" -tx <targetted txn ids, comma separated>");
        System.out.println(" -ty <targetted entry types, comma separated>");
        System.out.println(" -S  show Summary of log entries");
        System.out.println(" -q  if specified, concise version is printed");
	System.out.println("     Default is verbose version.)");
        System.out.println("All arguments are optional");
    }
}
