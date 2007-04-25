/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: MakeLogEntryVersionData.java,v 1.11.2.2 2007/03/31 22:06:14 mark Exp $
 */

package com.sleepycat.je.logversion;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.Set;

import javax.transaction.xa.XAResource;

import com.sleepycat.je.CheckpointConfig;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.JEVersion;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.XAEnvironment;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.log.LogEntryType;
import com.sleepycat.je.log.TestUtilLogReader;
import com.sleepycat.je.log.LogUtils.XidImpl;
import com.sleepycat.je.util.TestUtils;

/**
 * This standalone command line program generates log files named je-x.y.z.jdb
 * and je-x.y.z.txt, where x.y.z is the version of JE used to run the program.
 * This program needs to be run for the current version of JE when we release
 * a new major version of JE.  It does not need to be run again for older
 * versions of JE, unless it is changed to generate new types of log entries
 * and we need to verify those log entries for all versions of JE.  In that
 * the LogEntryVersionTest may also need to be changed.
 *
 * <p>Run this program with the desired version of JE in the classpath and pass
 * a home directory as the single command line argument.  After running this
 * program move the je-x.y.z.* files to the directory of this source package.
 * When adding je-x.y.z.jdb to CVS make sure to use -kb since it is a binary
 * file.</p>
 *
 * <p>This program can be run using the logversiondata ant target.</p>
 *
 * @see LogEntryVersionTest
 */
public class MakeLogEntryVersionData {

    /* Minimum child entries per BIN. */
    private static int N_ENTRIES = 4;

    private MakeLogEntryVersionData() {
    }

    public static void main(String[] args)
        throws Exception {

        if (args.length != 1) {
            throw new Exception("Home directory arg is required.");
        }

        File homeDir = new File(args[0]);
        File logFile = new File(homeDir, TestUtils.LOG_FILE_NAME);
        File renamedLogFile = new File(homeDir, "je-" +
            JEVersion.CURRENT_VERSION.getNumericVersionString() + ".jdb");
        File summaryFile = new File(homeDir, "je-" +
            JEVersion.CURRENT_VERSION.getNumericVersionString() + ".txt");

        if (logFile.exists()) {
            throw new Exception("Home directory must be empty of log files.");
        }

        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
	DbInternal.disableParameterValidation(envConfig);
        envConfig.setAllowCreate(true);
        envConfig.setTransactional(true);
        /* Make as small a log as possible to save space in CVS. */
        envConfig.setConfigParam
            (EnvironmentParams.ENV_RUN_INCOMPRESSOR.getName(), "false");
        envConfig.setConfigParam
            (EnvironmentParams.ENV_RUN_CLEANER.getName(), "false");
        envConfig.setConfigParam
            (EnvironmentParams.ENV_RUN_EVICTOR.getName(), "false");
        envConfig.setConfigParam
            (EnvironmentParams.ENV_RUN_CHECKPOINTER.getName(), "false");
	/* force trace messages at recovery. */
        envConfig.setConfigParam
            (EnvironmentParams.JE_LOGGING_LEVEL.getName(), "CONFIG");
        /* Use a 100 MB log file size to ensure only one file is written. */
        envConfig.setConfigParam(EnvironmentParams.LOG_FILE_MAX.getName(),
                                 Integer.toString(100 * (1 << 20)));
        /* Force BINDelta. */
        envConfig.setConfigParam
            (EnvironmentParams.BIN_DELTA_PERCENT.getName(),
             Integer.toString(75));
        /* Force INDelete -- only used when the root is purged. */
        envConfig.setConfigParam
            (EnvironmentParams.COMPRESSOR_PURGE_ROOT.getName(), "true");
        /* Ensure that we create two BINs with N_ENTRIES LNs. */
        envConfig.setConfigParam
            (EnvironmentParams.NODE_MAX.getName(),
             Integer.toString(N_ENTRIES));

        CheckpointConfig forceCheckpoint = new CheckpointConfig();
        forceCheckpoint.setForce(true);

        XAEnvironment env = new XAEnvironment(homeDir, envConfig);

        for (int i = 0; i < 2; i += 1) {
            boolean transactional = (i == 0);
            String dbName = transactional ? Utils.DB1_NAME : Utils.DB2_NAME;
            
            DatabaseConfig dbConfig = new DatabaseConfig();
            dbConfig.setAllowCreate(true);
            dbConfig.setTransactional(transactional);
            dbConfig.setSortedDuplicates(true);
            Database db = env.openDatabase(null, dbName, dbConfig);

            Transaction txn = null;
            if (transactional) {
                txn = env.beginTransaction(null, null);
            }

            for (int j = 0; j < N_ENTRIES; j += 1) {
                db.put(txn, Utils.entry(j), Utils.entry(0));
            }
            db.put(txn, Utils.entry(0), Utils.entry(1));

            /* Must checkpoint to generate BINDeltas. */
            env.checkpoint(forceCheckpoint);

            /* Delete everything but the last LN to cause IN deletion. */
            for (int j = 0; j < N_ENTRIES - 1; j += 1) {
                db.delete(txn, Utils.entry(j));
            }

            if (transactional) {
                txn.abort();
            }

            db.close();
        }

        /* Compress twice to delete DBIN, DIN, BIN, IN. */
        env.compress();
        env.compress();

        /* DB2 was not aborted and will contain: {3, 0} */
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(false);
        dbConfig.setReadOnly(true);
        dbConfig.setSortedDuplicates(true);
        Database db = env.openDatabase(null, Utils.DB2_NAME, dbConfig);
        Cursor cursor = db.openCursor(null, null);
        try {
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry data = new DatabaseEntry();
            OperationStatus status = cursor.getFirst(key, data, null);
            if (status != OperationStatus.SUCCESS) {
                throw new Exception("Expected SUCCESS but got: " + status);
            }
            if (Utils.value(key) != 3 || Utils.value(data) != 0) {
                throw new Exception("Expected {3,0} but got: {" +
                                    Utils.value(key) + ',' +
                                    Utils.value(data) + '}');
            }
        } finally {
            cursor.close();
        }
        db.close();

        /*
         * Generate an XA txn Prepare. The transaction must be non-empty in
         * order to actually log the Prepare.
         */
	XidImpl xid =
	    new XidImpl(1, "MakeLogEntryVersionData".getBytes(), null);
	env.start(xid, XAResource.TMNOFLAGS);
        /* Re-write the existing {3,0} record. */
        dbConfig.setReadOnly(false);
        dbConfig.setTransactional(true);
        db = env.openDatabase(null, Utils.DB2_NAME, dbConfig);
        db.put(null, Utils.entry(3), Utils.entry(0));
        db.close();
	env.prepare(xid);
	env.rollback(xid);

        env.close();

        /*
         * Get the set of all log entry types we expect to output.  We punt on
         * one type -- MapLN_TX -- because MapLN (non-transactional) is now
         * used instead.
         */
        Set expectedTypes = LogEntryType.getAllTypes();
        expectedTypes.remove(LogEntryType.LOG_MAPLN_TRANSACTIONAL);

        /* Open read-only and write all LogEntryType names to a text file. */
        envConfig.setReadOnly(true);
        Environment env2 = new Environment(homeDir, envConfig);
        PrintWriter writer = new PrintWriter
            (new BufferedOutputStream(new FileOutputStream(summaryFile)));
        TestUtilLogReader reader = new TestUtilLogReader
            (DbInternal.envGetEnvironmentImpl(env2));
        while (reader.readNextEntry()) {
            LogEntryType type = reader.getEntryType();
            writer.println(type.toString());
            expectedTypes.remove(type);
        }
        writer.close();
        env2.close();

        if (expectedTypes.size() > 0) {
            throw new Exception("Types not output: " + expectedTypes);
        }

        if (!logFile.exists()) {
            throw new Exception("What happened to: " + logFile);
        }

        if (!logFile.renameTo(renamedLogFile)) {
            throw new Exception
                ("Could not rename: " + logFile + " to " + renamedLogFile);
        }

        System.out.println("Created: " + renamedLogFile);
        System.out.println("Created: " + summaryFile);
    }
}
