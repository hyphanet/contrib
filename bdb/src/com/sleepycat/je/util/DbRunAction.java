/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: DbRunAction.java,v 1.31.2.1 2007/02/01 14:49:53 cwl Exp $
 */

package com.sleepycat.je.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.DecimalFormat;

import com.sleepycat.je.CheckpointConfig;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.EnvironmentMutableConfig;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.utilint.CmdUtil;

/**
 * DbRunAction is a debugging aid that can invoke a JE operation or
 * background activity from the command line.
 *
 *   batchClean calls Environment.cleanLog() in a loop
 *   checkpoint calls Environment.checkpoint() with force=true
 *   compress calls Environment.compress
 *   evict calls Environment.preload, then evictMemory
 *   removeDb calls Environment.removeDatabase, but doesn't do any cleaning
 *   removeDbAndClean calls removeDatabase, then cleanLog in a loop
 *   activateCleaner wakes up the cleaner, and then the main thread waits 
 *     until you type "y" to the console before calling Environment.close().
 *     The control provided by the prompt is necessary for daemon activities
 *     because often threads check and bail out if the environment is closed.
 */
public class DbRunAction {

    private static final int NONE = 0;
    private static final int BATCH_CLEAN = 1;   // app-driven batch cleaning
    private static final int COMPRESS = 2;
    private static final int EVICT = 3;
    private static final int CHECKPOINT = 4;
    private static final int REMOVEDB = 5;
    private static final int REMOVEDB_AND_CLEAN = 6;
    private static final int ACTIVATE_CLEANER_THREADS = 7;
                                           // wake up cleaner threads

    public static void main(String [] argv) {

        long recoveryStart = 0;
        long actionStart = 0;
        long actionEnd = 0;

        try {
            int whichArg = 0;
            if (argv.length == 0) {
                usage();
                System.exit(1);
            }

            String dbName = null;
            int doAction = 0;
            String envHome = ".";
            boolean readOnly = false;

            while (whichArg < argv.length) {
                String nextArg = argv[whichArg];

                if (nextArg.equals("-h")) {
                    whichArg++;
                    envHome = CmdUtil.getArg(argv, whichArg);
                } else if (nextArg.equals("-a")) {
                    whichArg++;
                    String action = CmdUtil.getArg(argv, whichArg);
                    if (action.equalsIgnoreCase("batchClean")) {
                        doAction = BATCH_CLEAN;
                    } else if (action.equalsIgnoreCase("compress")) {
                        doAction = COMPRESS;
                    } else if (action.equalsIgnoreCase("checkpoint")) {
                        doAction = CHECKPOINT;
                    } else if (action.equalsIgnoreCase("evict")) {
                        doAction = EVICT;
                    } else if (action.equalsIgnoreCase("removedb")) {
                        doAction = REMOVEDB;
                    } else if (action.equalsIgnoreCase("removedbAndClean")) {
                        doAction = REMOVEDB_AND_CLEAN;
                    } else if (action.equalsIgnoreCase("activateCleaner")) {
                        doAction = ACTIVATE_CLEANER_THREADS;
                    } else {
                        usage();
                        System.exit(1);
                    }
                } else if (nextArg.equals("-ro")) {
                    readOnly = true;
                } else if (nextArg.equals("-s")) {
                    dbName = argv[++whichArg];
                } else {
                    throw new IllegalArgumentException
                        (nextArg + " is not a supported option.");
                }
                whichArg++;
            }

            /* Make an environment */
            EnvironmentConfig envConfig = new EnvironmentConfig();

            /* Do debug log to the console.*/
            envConfig.setConfigParam
                (EnvironmentParams.JE_LOGGING_CONSOLE.getName(), "true");

            /* Don't debug log to the database log. */
            if (readOnly) {
                envConfig.setConfigParam
                    (EnvironmentParams.JE_LOGGING_DBLOG.getName(), "false");

                envConfig.setReadOnly(true);
            }

            /* 
             * If evicting, scan the given database first and don't run the
             * background evictor.
             */
            if (doAction == EVICT) {

                envConfig.setConfigParam(
                  EnvironmentParams.ENV_RUN_EVICTOR.getName(), "false");
                envConfig.setConfigParam(
                  EnvironmentParams.EVICTOR_CRITICAL_PERCENTAGE.getName(),
                                          "1000");
            }
                
            recoveryStart = System.currentTimeMillis();

            Environment env =
		new Environment(new File(envHome), envConfig);

            CheckpointConfig forceConfig = new CheckpointConfig();
            forceConfig.setForce(true);
            
            boolean promptForShutdown = false;
            actionStart = System.currentTimeMillis();
            switch(doAction) {
            case BATCH_CLEAN:
                /* Since this is batch cleaning, repeat until no progress. */
                while (true) {
                    int nFiles = env.cleanLog();
                    System.out.println("Files cleaned: " + nFiles);
                    if (nFiles == 0) {
                        break;
                    }
                }
                env.checkpoint(forceConfig);
                break;
            case COMPRESS:
                env.compress();
                break;
            case CHECKPOINT:
                env.checkpoint(forceConfig);
                break;
            case EVICT:
                preload(env, dbName);
                break;
            case REMOVEDB:
                removeAndClean(env, dbName, false);
                break;
            case REMOVEDB_AND_CLEAN:
                removeAndClean(env, dbName, true);
                break;
            case ACTIVATE_CLEANER_THREADS:
                EnvironmentImpl envImpl =
                    DbInternal.envGetEnvironmentImpl(env);
                envImpl.getCleaner().wakeup();
                promptForShutdown = true;
                break;
            }
            actionEnd = System.currentTimeMillis();

            if (promptForShutdown) {
                /* 
                 * If the requested action is a daemon driven one, we 
                 * don't want the main thread to shutdown the environment
                 * until we say we're ready
                 */
                waitForShutdown();
            }
            env.close();
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println(e.getMessage());
            usage();
            System.exit(1);
        } finally {
            DecimalFormat f = new DecimalFormat();
            f.setMaximumFractionDigits(2);

            long recoveryDuration = actionStart - recoveryStart;
            System.out.println("\nrecovery time = " +
                               f.format(recoveryDuration) +
                               " millis " +
                               f.format((double)recoveryDuration/60000) +
                               " minutes");

            long actionDuration = actionEnd - actionStart;
            System.out.println("action time = " +
                               f.format(actionDuration) +
                               " millis " +
                               f.format(actionDuration/60000) +
                               " minutes");

        }
    }
    
    private static void removeAndClean(Environment env,
                                       String name,
                                       boolean doCleaning) 
        throws DatabaseException {
        
        long a, b, c, d, e, f;

        //        Transaction txn = env.beginTransaction(null, null);
        Transaction txn = null;
        CheckpointConfig force = new CheckpointConfig();
        force.setForce(true);

        a = System.currentTimeMillis();
        env.removeDatabase(txn, name);
        b = System.currentTimeMillis();
        //        txn.commit();
        c = System.currentTimeMillis();

        int cleanedCount = 0;
        if (doCleaning) {
            while (env.cleanLog() > 0) {
                cleanedCount++;
            }
        }
        d = System.currentTimeMillis();

        System.out.println("cleanedCount=" + cleanedCount);
        e = 0;
        f = 0;
        if (cleanedCount > 0) {
            e = System.currentTimeMillis();
            env.checkpoint(force);
            f = System.currentTimeMillis();
        }

        System.out.println("Remove of " + name  +
                           " remove: " + getSecs(a, b) +
                           " commit: " + getSecs(b, c) +
                           " clean: " + getSecs(c, d) +
                           " checkpoint: " + getSecs(e, f));
    }

    private static String getSecs(long start, long end) {
        return (end-start)/1000 + " secs";
    }

    private static void preload(Environment env, String dbName)
        throws DatabaseException {

        System.out.println("Preload starting");
        Database db = env.openDatabase(null, dbName, null);
        Cursor cursor = db.openCursor(null, null);
        try {
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry data = new DatabaseEntry();
            int count = 0;
            while (cursor.getNext(key, data, LockMode.DEFAULT) ==
                   OperationStatus.SUCCESS) {
                count++;
                if ((count % 50000) == 0) {
                    System.out.println(count + "...");
                }
            }
            System.out.println("Preloaded " + count + " records");
        } finally {
            cursor.close();
            db.close();
        }
    }

    private static void doEvict(Environment env) 
        throws DatabaseException {
    	
        /* push the cache size down by half to force eviction. */
        EnvironmentImpl envImpl = DbInternal.envGetEnvironmentImpl(env);
        long cacheUsage = envImpl.getMemoryBudget().getCacheMemoryUsage();
        EnvironmentMutableConfig c = new EnvironmentMutableConfig();
        c.setCacheSize(cacheUsage/2);
        env.setMutableConfig(c);

        long start = System.currentTimeMillis();
        env.evictMemory();
        long end = System.currentTimeMillis();

        DecimalFormat f = new DecimalFormat();
        f.setMaximumFractionDigits(2);
        System.out.println("evict time=" + f.format(end-start));
    }

    private static void waitForShutdown() 
        throws IOException {

        System.out.println
	    ("Wait for daemon activity to run. When ready to stop, type (y)");
        BufferedReader reader =
            new BufferedReader(new InputStreamReader(System.in));
        do {
            String val = reader.readLine();
            if (val != null &&
		(val.equalsIgnoreCase("y") ||
                 val.equalsIgnoreCase("yes"))) {
                break;
            } else {
                System.out.println("Shutdown? (y)");
            }
        } while (true);
    }

    private static void usage() {
        System.out.println("Usage: \n " +
			   CmdUtil.getJavaCommand(DbRunAction.class));
	System.out.println("  -h <environment home> ");
        System.out.println("  -a <batchClean|compress|evict|checkpoint|" +
			   "removeDb|removeDbAndClean|activateCleaner>");
        System.out.println("  -ro (read-only - defaults to read-write)");
        System.out.println("  -s <dbName> (for removeDb)");
    }
}
