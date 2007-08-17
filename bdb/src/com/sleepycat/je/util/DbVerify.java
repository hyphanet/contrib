/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: DbVerify.java,v 1.42.2.2 2007/07/02 19:54:53 mark Exp $
 */

package com.sleepycat.je.util;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DatabaseStats;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.JEVersion;
import com.sleepycat.je.VerifyConfig;
import com.sleepycat.je.cleaner.VerifyUtils;
import com.sleepycat.je.dbi.DatabaseImpl;
import com.sleepycat.je.dbi.DbTree;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.utilint.CmdUtil;
import com.sleepycat.je.utilint.Tracer;

public class DbVerify {
    private static final String usageString =
	"usage: " + CmdUtil.getJavaCommand(DbVerify.class) + "\n" +
        "       -h <dir>             # environment home directory\n" +
        "       [-c ]                # check cleaner metadata\n" +
        "       [-q ]                # quiet, exit with success or failure\n" +
        "       [-s <databaseName> ] # database to verify\n" +
        "       [-v <interval>]      # progress notification interval\n" +
        "       [-V]                 # print JE version number";

    protected File envHome = null;
    protected Environment env;
    protected String dbName = null;
    protected boolean quiet = false;
    protected boolean checkLsns = false;
    protected boolean openReadOnly = true;
    private boolean doClose; 

    private int progressInterval = 0;

    static public void main(String argv[])
	throws DatabaseException {

	DbVerify verifier = new DbVerify();
	verifier.parseArgs(argv);

        boolean ret = false;
	try {
            ret = verifier.verify(System.err);
	} catch (Throwable T) {
	    if (!verifier.quiet) {
		T.printStackTrace(System.err);
	    }
	} finally {

	    verifier.closeEnv();

            /* 
             * Show the status, only omit if the user asked for a quiet
             * run and didn't specify a progress interval, in which case
             * we can assume that they really don't want any status output.
             *
             * If the user runs this from the command line, presumably they'd
             * like to see the status. 
             */
            if ((!verifier.quiet) || (verifier.progressInterval > 0)) {
                System.err.println("Exit status = " + ret);
            }

	    System.exit(ret ? 0 : -1);
	}
    }

    DbVerify() {
        doClose = true;
    }

    public DbVerify(Environment env,
		    String dbName,
		    boolean quiet) {
	this.env = env;
	this.dbName = dbName;
	this.quiet = quiet;
        doClose = false;
    }

    protected void printUsage(String msg) {
	System.err.println(msg);
	System.err.println(usageString);
	System.exit(-1);
    }

    protected void parseArgs(String argv[]) {

	int argc = 0;
	int nArgs = argv.length;
	while (argc < nArgs) {
	    String thisArg = argv[argc++];
	    if (thisArg.equals("-q")) {
		quiet = true;
	    } else if (thisArg.equals("-V")) {
		System.out.println(JEVersion.CURRENT_VERSION);
		System.exit(0);
	    } else if (thisArg.equals("-h")) {
		if (argc < nArgs) {
		    envHome = new File(argv[argc++]);
		} else {
		    printUsage("-h requires an argument");
		}
	    } else if (thisArg.equals("-s")) {
		if (argc < nArgs) {
		    dbName = argv[argc++];
		} else {
		    printUsage("-s requires an argument");
		}
	    } else if (thisArg.equals("-v")) {
		if (argc < nArgs) {
		    progressInterval = Integer.parseInt(argv[argc++]);
		    if (progressInterval <= 0) {
			printUsage("-v requires a positive argument");
		    }
		} else {
		    printUsage("-v requires an argument");
		}
            } else if (thisArg.equals("-c")) {
                checkLsns = true;
            } else if (thisArg.equals("-rw")) {

                /* 
                 * Unadvertised option. Open the environment read/write
                 * so that a checkLsns pass gets an accurate root LSN to
                 * start from in the event that a recovery split the root.
                 * A read/only environment open will keep any logging in 
                 * the log buffers, and the LSNs stored in the INs will
                 * be converted to DbLsn.NULL_LSN.
                 */
                openReadOnly = false;
            }
	}

	if (envHome == null) {
	    printUsage("-h is a required argument");
	}
    }

    protected void openEnv()
	throws DatabaseException {

	if (env == null) {
            EnvironmentConfig envConfig = new EnvironmentConfig();
            envConfig.setReadOnly(openReadOnly);
	    env = new Environment(envHome, envConfig);
	}
    }

    void closeEnv()
	throws DatabaseException {

	try {
	    if (env != null) {
	        env.close();
	    }
        } finally {
            env = null;
	}
    }

    public boolean verify(PrintStream out)
	throws DatabaseException {

	boolean ret = true;
	try {
            VerifyConfig verifyConfig = new VerifyConfig();
            verifyConfig.setPrintInfo(!quiet);
            if (progressInterval > 0) {
                verifyConfig.setShowProgressInterval(progressInterval);
                verifyConfig.setShowProgressStream(out);
            }

	    openEnv();
            EnvironmentImpl envImpl = DbInternal.envGetEnvironmentImpl(env);

            /* If no database is specified, verify all. */
            List dbNameList = null;
            List internalDbs = null;
            DbTree dbMapTree = envImpl.getDbMapTree();
                
            if (dbName == null) {
                dbNameList = env.getDatabaseNames();

                dbNameList.addAll(dbMapTree.getInternalDbNames());
                internalDbs = dbMapTree.getInternalNoLookupDbNames();
            } else {
                dbNameList = new ArrayList();
                dbNameList.add(dbName);
                internalDbs = new ArrayList();
            }
            
            /* Check application data. */
            Iterator iter = dbNameList.iterator();
            while (iter.hasNext()) {
                String targetDb = (String) iter.next();
                Tracer.trace(Level.INFO, envImpl,
                             "DbVerify.verify of " + targetDb + " starting");

                DatabaseConfig dbConfig = new DatabaseConfig();
                dbConfig.setReadOnly(true);
                dbConfig.setAllowCreate(false);
                DbInternal.setUseExistingConfig(dbConfig, true);
                Database db = env.openDatabase(null, targetDb, dbConfig);

                try {
                    if (!verifyOneDbImpl(DbInternal.dbGetDatabaseImpl(db),
                                         targetDb,
                                         verifyConfig,
                                         out)) {
                        ret = false;
                    }
                } finally {
                    if (db != null) {
                        db.close();
                    }
                    Tracer.trace(Level.INFO, envImpl, 
                                 "DbVerify.verify of " + targetDb + " ending");
                }
            }

            /* 
             * Check internal databases, which don't have to be opened
             * through a Database handle.
             */
            iter = internalDbs.iterator();
            while (iter.hasNext()) {
                String targetDb = (String) iter.next();
                Tracer.trace(Level.INFO, envImpl,
                             "DbVerify.verify of " + targetDb + " starting");
                
                try {
                    DatabaseImpl dbImpl = dbMapTree.getDb(null, targetDb,
                                                          null);
                    try {
                        if (!verifyOneDbImpl(dbImpl,  targetDb,
                                             verifyConfig, out)) {
                            ret = false;
                        }
                    } finally {
                        dbMapTree.releaseDb(dbImpl);
                    }
                } finally {
                    Tracer.trace(Level.INFO, envImpl, 
                                 "DbVerify.verify of " + targetDb + " ending");
                }
            }

            if (doClose) {
            closeEnv();
            }
        } catch (DatabaseException DE) {
	    ret = false;
            try {
                closeEnv();
	    } catch (Throwable ignored) {

		/* 
		 * Klockwork - ok
		 * Don't say anything about exceptions here.
		 */
	    }
	    throw DE;
        }

	return ret;
    }

    private boolean verifyOneDbImpl(DatabaseImpl dbImpl,
                                    String name,
                                    VerifyConfig verifyConfig,
                                    PrintStream out) 
        throws DatabaseException {
        boolean status = true;
        
        if (verifyConfig.getPrintInfo()) {
            out.println("Verifying database " + name);
        }

        /* 
         * First check the tree. Use DatabaseImpl.verify so we can get a status
         * return.
         */
        if (verifyConfig.getPrintInfo()) {
            out.println("Checking tree for " + name);
        }
        DatabaseStats stats = dbImpl.getEmptyStats();
        status = dbImpl.verify(verifyConfig, stats);
        if (verifyConfig.getPrintInfo()) {
            /* 
             * Intentionally use print, not println, because stats.toString()
             * puts in a newline too.
             */
            out.print(stats);
        }

        /* Then check the obsolete lsns */
        if (verifyConfig.getPrintInfo()) {
            out.println("Checking obsolete offsets for " + name);
        }
        try {
            VerifyUtils.checkLsns(dbImpl, out);
        } catch (DatabaseException e) {
            if (verifyConfig.getPrintInfo()) {
                out.println("Problem from checkLsns:" + e);
            }
            status = false;
        }
        if (verifyConfig.getPrintInfo()) {
            out.println();
        }
        return status;
    }
}
