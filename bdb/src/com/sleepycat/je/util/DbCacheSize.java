/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2005,2007 Oracle.  All rights reserved.
 *
 * $Id: DbCacheSize.java,v 1.10.2.1 2007/02/01 14:49:53 cwl Exp $
 */

package com.sleepycat.je.util;

import java.io.File;
import java.io.PrintStream;
import java.math.BigInteger;
import java.text.NumberFormat;
import java.util.Random;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.EnvironmentStats;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.dbi.MemoryBudget;
import com.sleepycat.je.utilint.CmdUtil;

/**
 * Estimating JE in-memory sizes as a function of key and data size is not
 * straightforward for two reasons. There is some fixed overhead for each btree
 * internal node, so tree fanout and degree of node sparseness impacts memory
 * consumption. In addition, JE compresses some of the internal nodes where
 * possible, but compression depends on on-disk layouts.
 *
 * DbCacheSize is an aid for estimating cache sizes. To get an estimate of the
 * in-memory footprint for a given database, specify the number of records and
 * record characteristics and DbCacheSize will return a minimum and maximum
 * estimate of the cache size required for holding the database in memory.
 * If the user specifies the record's data size, the utility will return both
 * values for holding just the internal nodes of the btree, and for holding the
 * entire database in cache.
 *
 * Note that "cache size" is a percentage more than "btree size", to cover
 * general environment resources like log buffers. Each invocation of the
 * utility returns an estimate for a single database in an environment.  For an
 * environment with multiple databases, run the utility for each database, add
 * up the btree sizes, and then add 10 percent.
 *
 * Note that the utility does not yet cover duplicate records and the API is
 * subject to change release to release.
 *
 * The only required parameters are the number of records and key size. 
 * Data size, non-tree cache overhead, btree fanout, and other parameters
 * can also be provided. For example:
 *
 * $ java DbCacheSize -records 554719 -key 16 -data 100
 * Inputs: records=554719 keySize=16 dataSize=100 nodeMax=128 density=80% 
 * overhead=10%
 *
 *    Cache Size      Btree Size  Description
 * --------------  --------------  -----------
 *    30,547,440      27,492,696  Minimum, internal nodes only
 *    41,460,720      37,314,648  Maximum, internal nodes only
 *   114,371,644     102,934,480  Minimum, internal nodes and leaf nodes
 *   125,284,924     112,756,432  Maximum, internal nodes and leaf nodes
 *
 * Btree levels: 3
 *
 * This says that the minimum cache size to hold only the internal nodes of the
 * btree in cache is approximately 30MB. The maximum size to hold the entire
 * database in cache, both internal nodes and datarecords, is 125Mb.
 */

public class DbCacheSize {
    
    private static final NumberFormat INT_FORMAT =
        NumberFormat.getIntegerInstance();

    private static final String HEADER =
        "    Cache Size      Btree Size  Description\n" +
        "--------------  --------------  -----------";
    //   12345678901234  12345678901234
    //                 12
    private static final int COLUMN_WIDTH = 14;
    private static final int COLUMN_SEPARATOR = 2;

    private long records;
    private int keySize;
    private int dataSize;
    private int nodeMax;
    private int density;
    private long overhead;
    private long minInBtreeSize;
    private long maxInBtreeSize;
    private long minInCacheSize;
    private long maxInCacheSize;
    private long maxInBtreeSizeWithData;
    private long maxInCacheSizeWithData;
    private long minInBtreeSizeWithData;
    private long minInCacheSizeWithData;
    private int nLevels = 1;

    public DbCacheSize (long records,
			int keySize,
			int dataSize,
			int nodeMax,
			int density,
			long overhead) {
	this.records = records;
	this.keySize = keySize;
	this.dataSize = dataSize;
	this.nodeMax = nodeMax;
	this.density = density;
	this.overhead = overhead;
    }
	
    public long getMinCacheSizeInternalNodesOnly() {
	return minInCacheSize;
    }

    public long getMaxCacheSizeInternalNodesOnly() {
	return maxInCacheSize;
    }

    public long getMinBtreeSizeInternalNodesOnly() {
	return minInBtreeSize;
    }

    public long getMaxBtreeSizeInternalNodesOnly() {
	return maxInBtreeSize;
    }

    public long getMinCacheSizeWithData() {
	return minInCacheSizeWithData;
    }

    public long getMaxCacheSizeWithData() {
	return maxInCacheSizeWithData;
    }

    public long getMinBtreeSizeWithData() {
	return minInBtreeSizeWithData;
    }

    public long getMaxBtreeSizeWithData() {
	return maxInBtreeSizeWithData;
    }

    public int getNLevels() {
	return nLevels;
    }

    public static void main(String[] args) {

        try {
            long records = 0;
            int keySize = 0;
            int dataSize = 0;
            int nodeMax = 128;
            int density = 80;
            long overhead = 0;
            File measureDir = null;
            boolean measureRandom = false;

            for (int i = 0; i < args.length; i += 1) {
                String name = args[i];
                String val = null;
                if (i < args.length - 1 && !args[i + 1].startsWith("-")) {
                    i += 1;
                    val = args[i];
                }
                if (name.equals("-records")) {
                    if (val == null) {
                        usage("No value after -records");
                    }
                    try {
                        records = Long.parseLong(val);
                    } catch (NumberFormatException e) {
                        usage(val + " is not a number");
                    }
                    if (records <= 0) {
                        usage(val + " is not a positive integer");
                    }
                } else if (name.equals("-key")) {
                    if (val == null) {
                        usage("No value after -key");
                    }
                    try {
                        keySize = Integer.parseInt(val);
                    } catch (NumberFormatException e) {
                        usage(val + " is not a number");
                    }
                    if (keySize <= 0) {
                        usage(val + " is not a positive integer");
                    }
                } else if (name.equals("-data")) {
                    if (val == null) {
                        usage("No value after -data");
                    }
                    try {
                        dataSize = Integer.parseInt(val);
                    } catch (NumberFormatException e) {
                        usage(val + " is not a number");
                    }
                    if (dataSize <= 0) {
                        usage(val + " is not a positive integer");
                    }
                } else if (name.equals("-nodemax")) {
                    if (val == null) {
                        usage("No value after -nodemax");
                    }
                    try {
                        nodeMax = Integer.parseInt(val);
                    } catch (NumberFormatException e) {
                        usage(val + " is not a number");
                    }
                    if (nodeMax <= 0) {
                        usage(val + " is not a positive integer");
                    }
                } else if (name.equals("-density")) {
                    if (val == null) {
                        usage("No value after -density");
                    }
                    try {
                        density = Integer.parseInt(val);
                    } catch (NumberFormatException e) {
                        usage(val + " is not a number");
                    }
                    if (density < 1 || density > 100) {
                        usage(val + " is not betwen 1 and 100");
                    }
                } else if (name.equals("-overhead")) {
                    if (val == null) {
                        usage("No value after -overhead");
                    }
                    try {
                        overhead = Long.parseLong(val);
                    } catch (NumberFormatException e) {
                        usage(val + " is not a number");
                    }
                    if (overhead < 0) {
                        usage(val + " is not a non-negative integer");
                    }
                } else if (name.equals("-measure")) {
                    if (val == null) {
                        usage("No value after -measure");
                    }
                    measureDir = new File(val);
                } else if (name.equals("-measurerandom")) {
                    measureRandom = true;
                } else {
                    usage("Unknown arg: " + name);
                }
            }

            if (records == 0) {
                usage("-records not specified");
            }

            if (keySize == 0) {
                usage("-key not specified");
            }

	    DbCacheSize dbCacheSize = new DbCacheSize
		(records, keySize, dataSize, nodeMax, density, overhead);
	    dbCacheSize.caclulateCacheSizes();
	    dbCacheSize.printCacheSizes(System.out);

            if (measureDir != null) {
                measure(System.out, measureDir, records, keySize, dataSize,
                        nodeMax, measureRandom);
            }
        } catch (Throwable e) {
            e.printStackTrace(System.out);
        }
    }

    private static void usage(String msg) {

        if (msg != null) {
            System.out.println(msg);
        }

        System.out.println
            ("usage:" +
             "\njava "  + CmdUtil.getJavaCommand(DbCacheSize.class) +
             "\n   -records <count>" +
             "\n      # Total records (key/data pairs); required" +
             "\n   -key <bytes> " +
             "\n      # Average key bytes per record; required" +
             "\n  [-data <bytes>]" +
             "\n      # Average data bytes per record; if omitted no leaf" +
             "\n      # node sizes are included in the output" +
             "\n  [-nodemax <entries>]" +
             "\n      # Number of entries per Btree node; default: 128" +
             "\n  [-density <percentage>]" +
             "\n      # Percentage of node entries occupied; default: 80" +
             "\n  [-overhead <bytes>]" +
             "\n      # Overhead of non-Btree objects (log buffers, locks," +
             "\n      # etc); default: 10% of total cache size" +
             "\n  [-measure <environmentHomeDirectory>]" +
             "\n      # An empty directory used to write a database to find" +
             "\n      # the actual cache size; default: do not measure" +
             "\n  [-measurerandom" +
             "\n      # With -measure insert randomly generated keys;" +
             "\n      # default: insert sequential keys");

        System.exit(2);
    }

    private void caclulateCacheSizes() {
        int nodeAvg = (nodeMax * density) / 100;
        long nBinEntries = (records * nodeMax) / nodeAvg;
        long nBinNodes = (nBinEntries + nodeMax - 1) / nodeMax;

        long nInNodes = 0;
	long lnSize = 0;

        for (long n = nBinNodes; n > 0; n /= nodeMax) {
            nInNodes += n;
            nLevels += 1;
        }

        minInBtreeSize = nInNodes *
	    calcInSize(nodeMax, nodeAvg, keySize, true);
        maxInBtreeSize = nInNodes *
	    calcInSize(nodeMax, nodeAvg, keySize, false);
	minInCacheSize = calculateOverhead(minInBtreeSize, overhead);
	maxInCacheSize = calculateOverhead(maxInBtreeSize, overhead);

        if (dataSize > 0) {
            lnSize = records * calcLnSize(dataSize);
        }

	maxInBtreeSizeWithData = maxInBtreeSize + lnSize;
	maxInCacheSizeWithData = calculateOverhead(maxInBtreeSizeWithData,
						    overhead);
	minInBtreeSizeWithData = minInBtreeSize + lnSize;
	minInCacheSizeWithData = calculateOverhead(minInBtreeSizeWithData,
						    overhead);
    }

    private void printCacheSizes(PrintStream out) {
	
        out.println("Inputs:" +
                    " records=" + records +
                    " keySize=" + keySize +
                    " dataSize=" + dataSize +
                    " nodeMax=" + nodeMax +
                    " density=" + density + '%' +
                    " overhead=" + ((overhead > 0) ? overhead : 10) + "%");

        out.println();
        out.println(HEADER);
        out.println(line(minInBtreeSize, minInCacheSize,
			 "Minimum, internal nodes only"));
        out.println(line(maxInBtreeSize, maxInCacheSize,
			 "Maximum, internal nodes only"));
        if (dataSize > 0) {
            out.println(line(minInBtreeSizeWithData,
			     minInCacheSizeWithData,
			     "Minimum, internal nodes and leaf nodes"));
            out.println(line(maxInBtreeSizeWithData,
			     maxInCacheSizeWithData,
                        "Maximum, internal nodes and leaf nodes"));
        } else {
            out.println("\nTo get leaf node sizing specify -data");
        }

        out.println("\nBtree levels: " + nLevels);
    }

    private int calcInSize(int nodeMax,
			   int nodeAvg,
			   int keySize,
			   boolean lsnCompression) {

        /* Fixed overhead */
        int size = MemoryBudget.IN_FIXED_OVERHEAD;

        /* Byte state array plus keys and nodes arrays */
        size += MemoryBudget.byteArraySize(nodeMax) +
                (nodeMax * (2 * MemoryBudget.ARRAY_ITEM_OVERHEAD));

        /* LSN array */
	if (lsnCompression) {
	    size += MemoryBudget.byteArraySize(nodeMax * 2);
	} else {
	    size += MemoryBudget.BYTE_ARRAY_OVERHEAD +
                    (nodeMax * MemoryBudget.LONG_OVERHEAD);
	}

        /* Keys for populated entries plus the identifier key */
        size += (nodeAvg + 1) * MemoryBudget.byteArraySize(keySize);

        return size;
    }

    private int calcLnSize(int dataSize) {

        return MemoryBudget.LN_OVERHEAD +
               MemoryBudget.byteArraySize(dataSize);
    }

    private long calculateOverhead(long btreeSize, long overhead) {
        long cacheSize;
        if (overhead == 0) {
            cacheSize = (100 * btreeSize) / 90;
        } else {
            cacheSize = btreeSize + overhead;
        }
	return cacheSize;
    }

    private String line(long btreeSize,
			long cacheSize,
			String comment) {

        StringBuffer buf = new StringBuffer(100);

        column(buf, INT_FORMAT.format(cacheSize));
        column(buf, INT_FORMAT.format(btreeSize));
        column(buf, comment);

        return buf.toString();
    }

    private void column(StringBuffer buf, String str) {

        int start = buf.length();

        while (buf.length() - start + str.length() < COLUMN_WIDTH) {
            buf.append(' ');
        }

        buf.append(str);

        for (int i = 0; i < COLUMN_SEPARATOR; i += 1) {
            buf.append(' ');
        }
    }

    private static void measure(PrintStream out,
                                File dir,
                                long records,
                                int keySize,
                                int dataSize,
                                int nodeMax,
                                boolean randomKeys)
        throws DatabaseException {

        String[] fileNames = dir.list();
        if (fileNames != null && fileNames.length > 0) {
            usage("Directory is not empty: " + dir);
        }

        Environment env = openEnvironment(dir, true);
        Database db = openDatabase(env, nodeMax, true);

        try {
            out.println("\nMeasuring with cache size: " +
                        INT_FORMAT.format(env.getConfig().getCacheSize()));
            insertRecords(out, env, db, records, keySize, dataSize, randomKeys);
            printStats(out, env,
                       "Stats for internal and leaf nodes (after insert)");

            db.close();
            env.close();
            env = openEnvironment(dir, false);
            db = openDatabase(env, nodeMax, false);

            out.println("\nPreloading with cache size: " +
                        INT_FORMAT.format(env.getConfig().getCacheSize()));
            preloadRecords(out, db);
            printStats(out, env,
                       "Stats for internal nodes only (after preload)");
        } finally {
            try {
                db.close();
                env.close();
            } catch (Exception e) {
                out.println("During close: " + e);
            }
        }
    }

    private static Environment openEnvironment(File dir, boolean allowCreate)
        throws DatabaseException {

        EnvironmentConfig envConfig = new EnvironmentConfig();
        envConfig.setAllowCreate(allowCreate);
        envConfig.setCachePercent(90);
        return new Environment(dir, envConfig);
    }

    private static Database openDatabase(Environment env, int nodeMax,
                                         boolean allowCreate)
        throws DatabaseException {

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(allowCreate);
        dbConfig.setNodeMaxEntries(nodeMax);
        return env.openDatabase(null, "foo", dbConfig);
    }

    private static void insertRecords(PrintStream out,
                                      Environment env,
                                      Database db,
                                      long records,
                                      int keySize,
                                      int dataSize,
                                      boolean randomKeys)
        throws DatabaseException {

        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry(new byte[dataSize]);
        BigInteger bigInt = BigInteger.ZERO;
        Random rnd = new Random(123);

        for (int i = 0; i < records; i += 1) {

            if (randomKeys) {
                byte[] a = new byte[keySize];
                rnd.nextBytes(a);
                key.setData(a);
            } else {
                bigInt = bigInt.add(BigInteger.ONE);
                byte[] a = bigInt.toByteArray();
                if (a.length < keySize) {
                    byte[] a2 = new byte[keySize];
                    System.arraycopy(a, 0, a2, a2.length - a.length, a.length);
                    a = a2;
                } else if (a.length > keySize) {
                    out.println("*** Key doesn't fit value=" + bigInt +
                                " byte length=" + a.length);
                    return;
                }
                key.setData(a);
            }

            OperationStatus status = db.putNoOverwrite(null, key, data);
            if (status == OperationStatus.KEYEXIST && randomKeys) {
                i -= 1;
                out.println("Random key already exists -- retrying");
                continue;
            }
            if (status != OperationStatus.SUCCESS) {
                out.println("*** " + status);
                return;
            }

            if (i % 10000 == 0) {
                EnvironmentStats stats = env.getStats(null);
                if (stats.getNNodesScanned() > 0) {
                    out.println("*** Ran out of cache memory at record " + i +
                                " -- try increasing the Java heap size ***");
                    return;
                }
                out.print(".");
                out.flush();
            }
        }
    }

    private static void preloadRecords(final PrintStream out,
                                       final Database db)
        throws DatabaseException {

        Thread thread = new Thread() {
            public void run() {
                while (true) {
                    try {
                        out.print(".");
                        out.flush();
                        Thread.sleep(5 * 1000);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
        };
        thread.start();
        db.preload(0);
        thread.interrupt();
        try {
            thread.join();
        } catch (InterruptedException e) {
            e.printStackTrace(out);
        }
    }

    private static void printStats(PrintStream out,
                                   Environment env,
                                   String msg)
        throws DatabaseException {

        out.println();
        out.println(msg + ':');

        EnvironmentStats stats = env.getStats(null);

        out.println("CacheSize=" +
                    INT_FORMAT.format(stats.getCacheTotalBytes()) +
                    " BtreeSize=" +
                    INT_FORMAT.format(stats.getCacheDataBytes()));

        if (stats.getNNodesScanned() > 0) {
            out.println("*** All records did not fit in the cache ***");
        }
    }
}
