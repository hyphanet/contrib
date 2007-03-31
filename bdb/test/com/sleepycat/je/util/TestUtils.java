/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: TestUtils.java,v 1.75.2.1 2007/02/01 14:50:23 cwl Exp $
 */

package com.sleepycat.je.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.NumberFormat;
import java.util.Iterator;
import java.util.Random;

import junit.framework.TestCase;

import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.DbTestProxy;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.StatsConfig;
import com.sleepycat.je.dbi.CursorImpl;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.dbi.INList;
import com.sleepycat.je.latch.LatchSupport;
import com.sleepycat.je.log.FileManager;
import com.sleepycat.je.tree.BIN;
import com.sleepycat.je.tree.ChildReference;
import com.sleepycat.je.tree.IN;
import com.sleepycat.je.tree.SearchResult;
import com.sleepycat.je.tree.Tree;
import com.sleepycat.je.tree.WithRootLatched;

public class TestUtils {
    public static String DEST_DIR = "testdestdir";
    public static String NO_SYNC = "txnnosync";
    public static String LONG_TEST = "longtest";

    public static final String LOG_FILE_NAME = "00000000.jdb";

    public static final StatsConfig FAST_STATS;

    static {
        FAST_STATS = new StatsConfig();
        FAST_STATS.setFast(true);
    }
    
    private static final boolean DEBUG = true;
    private static Random rnd = new Random();

    public void debugMsg(String message) {

        if (DEBUG) {
            System.out.println
		(Thread.currentThread().toString() + " " + message);
        }
    }

    static public void setRandomSeed(int seed) {

        rnd = new Random(seed);
    }

    static public void generateRandomAlphaBytes(byte[] bytes) {

        byte[] aAndZ = "AZ".getBytes();
        int range = aAndZ[1] - aAndZ[0] + 1;

        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (rnd.nextInt(range) + aAndZ[0]);
        }
    }

    static public void checkLatchCount() {
        TestCase.assertTrue(LatchSupport.countLatchesHeld() == 0);
    }

    static public void printLatchCount(String msg) {
        System.out.println(msg + " : " + LatchSupport.countLatchesHeld());
    }

    static public void printLatches(String msg) {
        System.out.println(msg + " : ");
        LatchSupport.dumpLatchesHeld();
    }

    /**
     * Generate a synthetic base 26 four byte alpha key from an int.
     * The bytes of the key are between 'A' and 'Z', inclusive.  0 maps
     * to 'AAAA', 1 to 'AAAB', etc.
     */
    static public int alphaKey(int i) {

        int ret = 0;
        for (int j = 0; j < 4; j++) {
            byte b = (byte) (i % 26);
            ret <<= 8;
            ret |= (b + 65);
            i /= 26;
        }

        return ret;
    }

    /**
     * Marshall an unsigned int (long) into a four byte buffer.
     */
    static public void putUnsignedInt(byte[] buf, long value) {

        int i = 0;
        buf[i++] = (byte) (value >>> 0);
        buf[i++] = (byte) (value >>> 8);
        buf[i++] = (byte) (value >>> 16);
        buf[i] =   (byte) (value >>> 24);
    }

    /**
     * All flavors of removeLogFiles should check if the remove has been
     * disabled. (Used for debugging, so that the tester can dump the
     * log file.
     */
    private static boolean removeDisabled() {

        String doRemove = System.getProperty("removeLogFiles");
        return ((doRemove != null) && doRemove.equalsIgnoreCase("false"));
    }

    /**
     * Remove je log files from the home directory. Will be disabled
     * if the unit test is run with -DremoveLogFiles=false
     * @param msg prefix to append to error messages
     * @param envFile environment directory
     */
    public static void removeLogFiles(String msg,
                                      File envFile,
                                      boolean checkRemove)
        throws IOException {

        removeFiles(msg, envFile, FileManager.JE_SUFFIX, checkRemove);
    }

    /**
     * Remove files with this suffix from the je home directory
     * @param msg prefix to append to error messages
     * @param envFile environment directory
     * @param suffix files with this suffix will be removed
     */
    public static void removeFiles(String msg,
				   File envFile,
				   String suffix)
        throws IOException {

        removeFiles(msg, envFile, suffix, false);
    }

    /**
     * Remove files with this suffix from the je home directory
     * @param msg prefix to append to error messages
     * @param envFile environment directory
     * @param suffix files with this suffix will be removed
     * @param checkRemove if true, check the -DremoveLogFiles system
     *  property before removing. 
     */
    public static void removeFiles(String msg,
                                   File envFile,
                                   String suffix,
                                   boolean checkRemove)
        throws IOException {

        if (checkRemove && removeDisabled()) {
            return;
        }

	String[] suffixes = new String[] { suffix };
        String[] names = FileManager.listFiles(envFile, suffixes);

        /* Clean up any target files in this directory. */
        for (int i = 0; i < names.length; i++) {
            File oldFile = new File(envFile, names[i]);
            boolean done = oldFile.delete();
            assert done :
                msg + " couldn't delete " + names[i] + " out of " +
                names[names.length - 1];
            oldFile = null;
        }
    }

    /**
     * Remove files with the pattern indicated by the filename filter from the
     * environment home directory.
     * Note that BadFileFilter looks for this pattern: NNNNNNNN.bad.#
     *           InfoFileFilter looks for this pattern: je.info.#
     * @param envFile environment directory
     */
    public static void removeFiles(File envFile, FilenameFilter filter)
        throws IOException {

        if (removeDisabled()) {
            return;
        }

        File[] targetFiles = envFile.listFiles(filter);

        // Clean up any target files in this directory
        for (int i = 0; i < targetFiles.length; i++) {
            boolean done = targetFiles[i].delete();
            if (!done) {
                System.out.println
		    ("Warning, couldn't delete "
		     + targetFiles[i]
		     + " out of "
		     + targetFiles[targetFiles.length - 1]);
            }
        }
    }

    /**
     * Copies all files in fromDir to toDir.  Does not copy subdirectories.
     */
    public static void copyFiles(File fromDir, File toDir)
        throws IOException {

        String[] names = fromDir.list();
        if (names != null) {
            for (int i = 0; i < names.length; i += 1) {
                File fromFile = new File(fromDir, names[i]);
                if (fromFile.isDirectory()) {
                    continue;
                }
                File toFile = new File(toDir, names[i]);
                int len = (int) fromFile.length();
                byte[] data = new byte[len];
                FileInputStream fis = null;
                FileOutputStream fos = null;
                try {
                    fis = new FileInputStream(fromFile);
                    fos = new FileOutputStream(toFile);
                    fis.read(data);
                    fos.write(data);
                } finally {
                    if (fis != null) {
                        fis.close();
                    }
                    if (fos != null) {
                        fos.close();
                    }
                }
            }
        }
    }

    /**
     * Useful utility for generating byte arrays with a known order.
     * Vary the length just to introduce more variability.
     * @return a byte array of length val % 100 with the value of "val"
     */
    public static byte[] getTestArray(int val) {

        int length = val % 10;
        length = length < 4 ? 4 : length;
        byte[] test = new byte[length];
        test[3] = (byte) ((val >>> 0) & 0xff);
        test[2] = (byte) ((val >>> 8) & 0xff);
        test[1] = (byte) ((val >>> 16) & 0xff);
        test[0] = (byte) ((val >>> 24) & 0xff);
        return test;
    }

    /**
     * Return the value of a test data array generated with getTestArray
     * as an int
     */
    public static int getTestVal(byte[] testArray) {

        int val = 0;
        val |= (testArray[3] & 0xff);
        val |= ((testArray[2] & 0xff) << 8);
        val |= ((testArray[1] & 0xff) << 16);
        val |= ((testArray[0] & 0xff) << 24);
        return val;
    }

    /**
     * @return length and data of a byte array, printed as decimal numbers
     */
    public static String dumpByteArray(byte[] b) {

        StringBuffer sb = new StringBuffer();
        sb.append("<byteArray len = ");
        sb.append(b.length);
        sb.append(" data = \"");
        for (int i = 0; i < b.length; i++) {
            sb.append(b[i]).append(",");
        }
        sb.append("\"/>");
        return sb.toString();
    }

    /**
     * @return a copy of the passed in byte array
     */
    public static byte[] byteArrayCopy(byte[] ba) {

        int len = ba.length;
        byte[] ret = new byte[len];
        System.arraycopy(ba, 0, ret, 0, len);
        return ret;
    }

    /*
     * Check that the stored memory count for all INs on the inlist 
     * matches their computed count. The environment mem usage check
     * may be run with assertions or not.
     *
     * In a multithreaded environment (or one with daemons running),
     * you can't be sure that the cached size will equal the calculated size.
     *
     * Nodes, txns, and locks are all counted within the memory budget.
     */
    public static long validateNodeMemUsage(EnvironmentImpl envImpl,
                                            boolean assertOnError)
        throws DatabaseException {

        long total = tallyNodeMemUsage(envImpl);
        long nodeCacheUsage = envImpl.getMemoryBudget().getTreeMemoryUsage();
        NumberFormat formatter = NumberFormat.getNumberInstance();
        if (assertOnError) {
            assert (total==nodeCacheUsage) : 
                  "calculatedTotal=" + formatter.format(total) +
                  " envCacheUsage=" + formatter.format(nodeCacheUsage);
        } else {
            if (DEBUG) {
                if (nodeCacheUsage != total) {
                    long diff = Math.abs(nodeCacheUsage - total);
                    if ((diff / nodeCacheUsage) > .05) {
                        System.out.println("calculatedTotal=" +
                                           formatter.format(total) +
                                           " envCacheUsage=" +
                                           formatter.format(nodeCacheUsage));
                    }
                }
            }
        }

        return nodeCacheUsage;
    }

    public static long tallyNodeMemUsage(EnvironmentImpl envImpl)
        throws DatabaseException {

        INList inList = envImpl.getInMemoryINs();
        inList.latchMajor();
        long total = 0;
        try {
            Iterator iter = inList.iterator();
            while (iter.hasNext()) {
                IN in = (IN) iter.next();
                in.latch();
                try {
                    assert in.verifyMemorySize():
                        "in nodeId=" + in.getNodeId() +
                        ' ' + in.getClass().getName();
                    total += in.getInMemorySize();
                } finally {
                    in.releaseLatch();
                }
            }
        } finally {
            inList.releaseMajorLatch();
        }
        return total;
    }

    /**
     * Called by each unit test to enforce isolation level settings specified
     * in the isolationLevel system property.  Other system properties or
     * default settings may be applied in the future.
     */
    public static EnvironmentConfig initEnvConfig() {

        EnvironmentConfig config = new EnvironmentConfig();
        String val = System.getProperty("isolationLevel");
        if (val != null && val.length() > 0) {
            if ("serializable".equals(val)) {
                config.setTxnSerializableIsolation(true);
            } else if ("readCommitted".equals(val)) {
                DbInternal.setTxnReadCommitted(config, true);
            } else {
                throw new IllegalArgumentException
                    ("Unknown isolationLevel system property value: " + val);
            }
        }
        return config;
    }
    
    /**
     * If a unit test needs to override the isolation level, it should call
     * this method after calling initEnvConfig.
     */
    public static void clearIsolationLevel(EnvironmentConfig config) {
        DbInternal.setTxnReadCommitted(config, false);
        config.setTxnSerializableIsolation(false);
    }

    /**
     * Loads the given resource relative to the given class, and copies it to
     * log file zero in the given directory.
     */
    public static void loadLog(Class cls, String resourceName, File envHome)
        throws IOException {

        File logFile = new File(envHome, LOG_FILE_NAME);
        InputStream is = cls.getResourceAsStream(resourceName);
        OutputStream os = new FileOutputStream(logFile);
        byte[] buf = new byte[is.available()];
        int len = is.read(buf);
        if (buf.length != len) {
            throw new IllegalStateException();
        }
        os.write(buf, 0, len);
        is.close();
        os.close();
    }

    /**
     * Logs the BIN at the cursor provisionally and the parent IN
     * non-provisionally.  Used to simulate a partial checkpoint or eviction.
     */
    public static void logBINAndIN(Environment env, Cursor cursor)
        throws DatabaseException {

        BIN bin = getBIN(cursor);
        Tree tree = bin.getDatabase().getTree();


        /* Log the BIN and update its parent entry. */
        bin.latch();
        SearchResult result = tree.getParentINForChildIN(bin, true, true);
        assert result.parent != null;
        assert result.exactParentFound;
        IN binParent = result.parent;
        long binLsn = logIN(env, bin, true, binParent);
        binParent.updateEntry(result.index, bin, binLsn);
        result.parent.releaseLatch();

        /* Log the BIN parent and update its parent entry. */
        binParent.latch();
        result = tree.getParentINForChildIN(binParent, true, true);
        IN inParent = null;
        if (result.parent != null) {
            result.parent.releaseLatch();
            assert result.exactParentFound;
            inParent = result.parent;
	    inParent.latch();
        }
        final long inLsn = logIN(env, binParent, false, null);
        if (inParent != null) {
            inParent.updateEntry(result.index, binParent, inLsn);
	    inParent.releaseLatch();
        } else {
            tree.withRootLatchedExclusive(new WithRootLatched() {
                public IN doWork(ChildReference root)
                    throws DatabaseException {
                    root.setLsn(inLsn);
                    return null;
                }
            });
        }
    }

    /**
     * Logs the given IN.
     */
    public static long logIN(Environment env,
                             IN in,
                             boolean provisional,
                             IN parent)
        throws DatabaseException {

        EnvironmentImpl envImpl = DbInternal.envGetEnvironmentImpl(env);
        in.latch();
        long lsn;
        if (provisional) {
            lsn = in.log(envImpl.getLogManager(), 
        	         false,  // allowDeltas
                         true,   // isProvisional
                         false,  // proactiveMigration
                         false,  // backgroundIO
        	         parent);// provisional parent
        } else {
            lsn = in.log(envImpl.getLogManager());
        }
        in.releaseLatch();
        return lsn;
    }

    /**
     * Returns the parent IN of the given BIN.
     */
    public static IN getIN(BIN bin)
        throws DatabaseException {

        Tree tree = bin.getDatabase().getTree();
        bin.latch();
        SearchResult result = tree.getParentINForChildIN(bin, true, true);
        assert result.parent != null;
        result.parent.releaseLatch();
        assert result.exactParentFound;
        return result.parent;
    }

    /**
     * Returns the target BIN for the given cursor.
     */
    public static BIN getBIN(Cursor cursor)
        throws DatabaseException {

        CursorImpl impl = DbTestProxy.dbcGetCursorImpl(cursor);
        BIN bin = impl.getDupBIN();
        if (bin == null) {
            bin = impl.getBIN();
            assert bin != null;
        }
        return bin;
    }

    /**
     * Assert if the tree is not this deep. Use to ensure that data setups
     * are as expected.
     */
    public static boolean checkTreeDepth(Database db, int desiredDepth)
        throws DatabaseException {

        Tree tree = DbInternal.dbGetDatabaseImpl(db).getTree();
        IN rootIN = tree.getRootIN(false /* update generation */);
        int level = 0;
        if (rootIN != null) {
            level = rootIN.getLevel() & IN.LEVEL_MASK;
            rootIN.releaseLatch();
        }

        return (desiredDepth == level);
    }

    /**
     * @return true if long running tests are enabled. 
     */
    static public boolean runLongTests() {
        String longTestProp =  System.getProperty(TestUtils.LONG_TEST);
        if ((longTestProp != null)  &&
            longTestProp.equalsIgnoreCase("true")) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Skip over the JE version number at the start of the exception
     * message for tests which are looking for a specific message.
     */
    public static String skipVersion(Exception e) {
        int versionHeaderLen = DatabaseException.getVersionHeader().length();
        return (e.getMessage().substring(versionHeaderLen));
    }
}
