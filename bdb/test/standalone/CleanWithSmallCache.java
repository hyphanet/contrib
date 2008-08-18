/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2005,2008 Oracle.  All rights reserved.
 *
 * $Id: CleanWithSmallCache.java,v 1.3 2008/01/28 21:17:42 linda Exp $
 */

import java.io.File;
import java.text.NumberFormat;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import com.sleepycat.bind.tuple.TupleBinding;
import com.sleepycat.bind.tuple.TupleOutput;
import com.sleepycat.je.CheckpointConfig;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.EnvironmentStats;
import com.sleepycat.je.StatsConfig;

/**
 * Used to test a small cache and log cleaning.  For example, to create a large
 * set of log files (over 10 GB) that are almost 100% obsolete:
 *
 * java -Xmx6m -cp .:before.jar CleanWithSmallCache \
 *  -records 40000 -key 48 -data 10 -h tmp -random -cache 250k \
 *  -seconds 2000 -write 10000
 *
 * And then to clean that set of logs:
 *
 * java -Xmx10m -cp .:before.jar CleanWithSmallCache \
 *  -records 40000 -key 48 -data 10 -h tmp -random -cache 250k \
 *  -seconds 22000 -read 10 -clean
 */
public class CleanWithSmallCache {
    
    private static final NumberFormat INT_FORMAT =
        NumberFormat.getIntegerInstance();
    private static final NumberFormat NUMBER_FORMAT =
        NumberFormat.getNumberInstance();

    private File envHome = null;
    private int cacheSize = 0;
    private int records = -1;
    private int keySize = -1;
    private int dataSize = -1;
    private int fanout = 128;
    private int readRate = 0;
    private int writeRate = 0;
    private int totalSeconds = 0;
    private long beginTime = 0;
    private long endTime = 0;
    private boolean randomKeys = false;
    private boolean doClean = false;
    private boolean fillCache = false;
    private Random random = new Random(123);
    private AtomicInteger nReads = new AtomicInteger(0);
    private AtomicInteger nWrites = new AtomicInteger(0);
    private boolean programDone = false;
    private Environment env = null;
    private Database db = null;

    public static void main(String[] args) {
        try {
            CleanWithSmallCache test = new CleanWithSmallCache(args);
            long start = System.currentTimeMillis();
            System.out.println("Opening environment");
            test.open();
            System.out.println("Starting test");
            test.execute();
            test.close();
            long end = System.currentTimeMillis();
            System.out.println("Time: " + ((end - start) / 1000) + " sec");
        } catch (Throwable e) {
            e.printStackTrace(System.out);
        }
    }

    private CleanWithSmallCache(String[] args) {

        for (int i = 0; i < args.length; i += 1) {
            String name = args[i];
            String val = null;
            if (i < args.length - 1 && !args[i + 1].startsWith("-")) {
                i += 1;
                val = args[i];
            }
            if (name.equals("-h")) {
                if (val == null) {
                    usage("No value after -h");
                }
                envHome = new File(val);
            } else if (name.equals("-cache")) {
                if (val == null) {
                    usage("No value after -cache");
                }
                boolean mb = false;
                boolean kb = false;
                if (val.endsWith("m")) {
                    mb = true;
                    val = val.substring(0, val.length() - 1);
                } else if (val.endsWith("k")) {
                    kb = true;
                    val = val.substring(0, val.length() - 1);
                }
                try {
                    cacheSize = Integer.parseInt(val);
                } catch (NumberFormatException e) {
                    usage(val + " is not a number");
                }
                if (cacheSize <= 0) {
                    usage(val + " is not a positive integer");
                }
                if (mb) {
                    cacheSize *= 1024 * 1024;
                } else if (kb) {
                    cacheSize *= 1024;
                }
            } else if (name.equals("-records")) {
                if (val == null) {
                    usage("No value after -records");
                }
                try {
                    records = Integer.parseInt(val);
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
                if (dataSize < 0) {
                    usage(val + " is not a non-negative integer");
                }
            } else if (name.equals("-read")) {
                if (val == null) {
                    usage("No value after -read");
                }
                try {
                    readRate = Integer.parseInt(val);
                } catch (NumberFormatException e) {
                    usage(val + " is not a number");
                }
                if (readRate < 0) {
                    usage(val + " is not a non-negative integer");
                }
            } else if (name.equals("-write")) {
                if (val == null) {
                    usage("No value after -write");
                }
                try {
                    writeRate = Integer.parseInt(val);
                } catch (NumberFormatException e) {
                    usage(val + " is not a number");
                }
                if (writeRate < 0) {
                    usage(val + " is not a non-negative integer");
                }
            } else if (name.equals("-seconds")) {
                if (val == null) {
                    usage("No value after -seconds");
                }
                try {
                    totalSeconds = Integer.parseInt(val);
                } catch (NumberFormatException e) {
                    usage(val + " is not a number");
                }
                if (totalSeconds < 0) {
                    usage(val + " is not a non-negative integer");
                }
            } else if (name.equals("-fanout")) {
                if (val == null) {
                    usage("No value after -fanout");
                }
                try {
                    fanout = Integer.parseInt(val);
                } catch (NumberFormatException e) {
                    usage(val + " is not a number");
                }
                if (fanout <= 0) {
                    usage(val + " is not a positive integer");
                }
            } else if (name.equals("-random")) {
                randomKeys = true;
            } else if (name.equals("-clean")) {
                doClean = true;
            } else if (name.equals("-fillcache")) {
                fillCache = true;
            } else {
                usage("Unknown arg: " + name);
            }
        }

        if (envHome == null) {
            usage("-h not specified");
        }

        if (cacheSize <= 0) {
            usage("-cache not specified");
        }

        if (records <= 0) {
            usage("-records not specified");
        }

        if (keySize <= 0) {
            usage("-key not specified");
        }

        if (dataSize <= 0) {
            usage("-data not specified");
        }

        int maxRecNum;
        switch (keySize) {
        case 1:
            maxRecNum = Byte.MAX_VALUE;
            break;
        case 2:
        case 3:
            maxRecNum = Short.MAX_VALUE;
            break;
        default:
            maxRecNum = Integer.MAX_VALUE;
        }
        if (records > maxRecNum) {
            usage("-key size too small for number of records");
        }
    }

    private void usage(String msg) {

        if (msg != null) {
            System.out.println(msg);
        }

        System.out.println
            ("usage:" +
             "\njava "  + CleanWithSmallCache.class.getName() +
             "\n   -h <envHome>" +
             "\n      # Environment home directory" +
             "\n   -records <count>" +
             "\n      # Total records (key/data pairs); required" +
             "\n   -key <bytes>" +
             "\n      # Key bytes per record; required" +
             "\n   -data <bytes>" +
             "\n      # Data bytes per record; required" +
             "\n  [-fanout <entries>]" +
             "\n      # Number of entries per Btree node; default: 128" +
             "\n  [-read <readsPerSecond>]" +
             "\n      # Number of read operations per second; default: 0" +
             "\n  [-write <writesPerSecond>]" +
             "\n      # Number of write operations per second; default: 0" +
             "\n  [-random]" +
             "\n      # Write randomly generated keys;" +
             "\n      # default: write sequential keys" +
             "\n  [-seconds <totalSeconds>]" +
             "\n      # Number of seconds to run; default: 0 or forever" +
             "\n  [-clean]" +
             "\n      # Perform log cleaning; default: false" +
             "\n  [-fillcache]" +
             "\n      # Artificially fill the cache; default: false");

        System.exit(2);
    }

    private void open()
        throws DatabaseException {

        EnvironmentConfig envConfig = new EnvironmentConfig();
        envConfig.setAllowCreate(true);
        envConfig.setConfigParam("je.env.runCleaner", "false");
        envConfig.setCacheSize(cacheSize);
        env = new Environment(envHome, envConfig);

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
        dbConfig.setNodeMaxEntries(fanout);
        db = env.openDatabase(null, "foo", dbConfig);

        if (fillCache) {
            DbInternal.envGetEnvironmentImpl(env).getMemoryBudget().
                updateAdminMemoryUsage(cacheSize * 2);
        }
    }

    private void close()
        throws DatabaseException {

        db.close();
        env.close();
    }

    private int makeKey(int recNum, DatabaseEntry entry) {
        if (randomKeys) {
            recNum = random.nextInt(records - 1) + 1;
        } else {
            recNum += 1;
            if (recNum > records) {
                recNum = 1;
            }
        }
        if (keySize == 1) {
            entry.setData(new byte[] { (byte) recNum });
        } else {
            TupleOutput out = new TupleOutput(new byte[keySize]);
            int written;
            if (keySize == 2 || keySize == 3) {
                out.writeUnsignedShort((short) recNum);
                written = 2;
            } else {
                out.writeUnsignedInt(recNum);
                written = 4;
            }
            while (written < keySize) {
                out.writeFast(0);
                written += 1;
            }
            TupleBinding.outputToEntry(out, entry);
        }
        return recNum;
    }

    private void execute()
        throws InterruptedException, DatabaseException {

        Thread monitor = new Monitor();
        Thread cleaner = null;
        if (doClean) {
            cleaner = new Cleaner();
        }
        Thread writer = null;
        if (writeRate > 0) {
            writer = new OperationRunner(writeRate, nWrites, new Operation() {
                public void doOperation(DatabaseEntry key, DatabaseEntry data)
                    throws DatabaseException {
                    db.put(null, key, data);
                }
            });
        }
        Thread reader = null;
        if (readRate > 0) {
            reader = new OperationRunner(writeRate, nReads, new Operation() {
                public void doOperation(DatabaseEntry key, DatabaseEntry data)
                    throws DatabaseException {
                    Cursor cursor = db.openCursor(null, null);
                    cursor.getSearchKeyRange(key, data, null);
                    cursor.close();
                }
            });
        }
        beginTime = System.currentTimeMillis();
        if (totalSeconds > 0) {
            endTime = beginTime + (totalSeconds * 1000);
        }
        monitor.start();
        if (cleaner != null) {
            cleaner.start();
        }
        if (writer != null) {
            writer.start();
        }
        if (reader != null) {
            reader.start();
        }
        monitor.join();
        if (cleaner != null) {
            cleaner.join();
        }
        if (writer != null) {
            writer.join();
        }
        if (reader != null) {
            reader.join();
        }
    }

    private class Monitor extends Thread {
        public void run() {
            try {
                long lastTime = System.currentTimeMillis();
                while (totalSeconds == 0 || lastTime < endTime) {
                    Thread.sleep(5000);
                    long time = System.currentTimeMillis();
                    printStats(time);
                    lastTime = time;
                }
                programDone = true;
            } catch (Throwable e) {
                e.printStackTrace();
                System.exit(1);
            }
        }
    }

    private class Cleaner extends Thread {
        public void run() {
            CheckpointConfig forceConfig = new CheckpointConfig();
            forceConfig.setForce(true);
            try {
                boolean cleanedSome;
                do {
                    cleanedSome = false;
                    while (true) {
                        int nFiles = env.cleanLog();
                        if (nFiles == 0) {
                            break;
                        }
                        cleanedSome = true;
                    }
                    env.checkpoint(forceConfig);
                } while (cleanedSome && !programDone);
                programDone = true;
            } catch (Throwable e) {
                e.printStackTrace();
                System.exit(1);
            }
        }
    }

    private interface Operation {
        void doOperation(DatabaseEntry key, DatabaseEntry data)
            throws DatabaseException;
    }

    private class OperationRunner extends Thread {

        private int rate;
        private Operation op;
        private AtomicInteger nOps;

        OperationRunner(int rate, AtomicInteger nOps, Operation op) {
            this.rate = rate;
            this.nOps = nOps;
            this.op = op;
        }

        public void run() {

            int recNum = 0;
            int ops = 0;
            long beforeTime = System.currentTimeMillis();
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry data = new DatabaseEntry(new byte[dataSize]);

            try {
                while (!programDone) {
                    recNum = makeKey(recNum, key);
                    op.doOperation(key, data);
                    ops += 1;
                    nOps.incrementAndGet();
                    if (ops >= rate) {
                        long afterTime = System.currentTimeMillis();
                        long interval = afterTime - beforeTime;
                        if (interval < 1000) {
                            Thread.sleep(1000 - interval);
                        }
                        ops = 0;
                        beforeTime = afterTime;
                    }
                }
            } catch (Throwable e) {
                e.printStackTrace();
                System.exit(1);
            }
        }
    }

    private void printStats(long currentTime)
        throws DatabaseException {

        StatsConfig statsConfig = new StatsConfig();
        statsConfig.setClear(true);
        EnvironmentStats stats = env.getStats(statsConfig);

        float secs = (currentTime - beginTime) / 1000.0f;
        float writesPerSec = nWrites.get() / secs;
        float readsPerSec = nReads.get() / secs;

        System.out.println("\nWrites/Sec=" +
                           NUMBER_FORMAT.format(writesPerSec) +
                           " Reads/Sec=" +
                           NUMBER_FORMAT.format(readsPerSec) +
                           " CacheSize=" +
                           INT_FORMAT.format(stats.getCacheTotalBytes()) +
                           " DataSize=" +
                           INT_FORMAT.format(stats.getDataBytes()) +
                           " AdminSize=" +
                           INT_FORMAT.format(stats.getAdminBytes()) +
                           " LockSize=" +
                           INT_FORMAT.format(stats.getLockBytes()) +
                           " NEvictPasses=" +
                           INT_FORMAT.format(stats.getNEvictPasses()) +
                           " NCacheMiss=" +
                           INT_FORMAT.format(stats.getNCacheMiss()) +
                           " TotalLogSize=" +
                           INT_FORMAT.format(stats.getTotalLogSize()));
    }
}
