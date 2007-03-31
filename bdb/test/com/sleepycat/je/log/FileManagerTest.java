/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: FileManagerTest.java,v 1.65.2.1 2007/02/01 14:50:14 cwl Exp $
 */

package com.sleepycat.je.log;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Set;

import junit.framework.TestCase;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.util.TestUtils;
import com.sleepycat.je.utilint.DbLsn;

/**
 * File Manager
 */
public class FileManagerTest extends TestCase {

    static private int FILE_SIZE = 120;

    private EnvironmentImpl envImpl;
    private FileManager fileManager;
    private File envHome;

    public FileManagerTest() {
        super();
        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    protected void setUp()
        throws DatabaseException, IOException {

        /* Remove files to start with a clean slate. */
        TestUtils.removeFiles("Setup", envHome, FileManager.JE_SUFFIX);

        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
	DbInternal.disableParameterValidation(envConfig);
        envConfig.setConfigParam(EnvironmentParams.LOG_FILE_MAX.getName(),
                                 new Integer(FILE_SIZE).toString());
        /* Yank the cache size way down. */
        envConfig.setConfigParam
	    (EnvironmentParams.LOG_FILE_CACHE_SIZE.getName(), "3");
        envConfig.setAllowCreate(true);
        envImpl = new EnvironmentImpl(envHome, envConfig);

        /* Make a standalone file manager for this test. */
        envImpl.close();
        envImpl.open(); /* Just sets state to OPEN. */
        fileManager = new FileManager(envImpl, envHome, false);

        /* 
         * Remove any files after the environment is created again!  We want to
         * remove the files made by recovery, so we can test the file manager
         * in controlled cases.
         */
        TestUtils.removeFiles("Setup", envHome, FileManager.JE_SUFFIX);
    }

    protected void tearDown()
        throws IOException, DatabaseException {

        if (fileManager != null) {
            fileManager.clear();
            fileManager.close();
        }
        TestUtils.removeFiles("TearDown", envHome, FileManager.JE_SUFFIX);
    }

    /**
     * Test LSN administration.
     */
    public void testLsnBumping()
        throws Exception {

        /* 
	   We are adding these entries:
	   +----+------+---------+--------+
	   file 0:  |hdr | 30   |   50    |empty   |
	   +----+------+---------+--------+
	   0    hdr  hdr+30   hdr+80     99

	   +----+--------+-------+-------+-----+-------+
	   file 1:  |hdr | 40     | 20    | 10    | 5   | empty |
	   +----+--------+-------+-------+-----+-------+
	   0    hdr   hdr+40  hdr+60  hdr+70  hdr+75 

	   +-----+-----+--------+
	   file 2:  | hdr | 75  |  empty |
	   +-----+-----+--------+
	   0    hdr   hdr+75 

	   +-----+-------------------------------+
	   file 3:  | hdr | 125                           | 
	   +-----+-------------------------------+
	   0    hdr  

	   +-----+-----+------+-----+--------------+
	   file 4:  | hdr | 10  | 20   | 30  | empty
	   +-----+-----+------+-----+--------------+
	   0    hdr hdr+10 hdr+30

        */

        try {
            /* Should start out at LSN 0. */
                          
            /* "add" some entries to the log. */
            long hdrSize = FileManager.firstLogEntryOffset();

            fileManager.bumpLsn(30L);
            /* Item placed here. */
            assertEquals(DbLsn.makeLsn(0, hdrSize),
                         fileManager.getLastUsedLsn());
            /* prev entry. */
            assertEquals(0, fileManager.getPrevEntryOffset());

            fileManager.bumpLsn(50L);
            /* Item placed here. */
            assertEquals(DbLsn.makeLsn(0, (hdrSize + 30)),
                         fileManager.getLastUsedLsn());
            assertEquals(hdrSize, fileManager.getPrevEntryOffset());

            /* bump over to a file 1. */
            fileManager.bumpLsn(40L);
            /* item placed here. */
            assertEquals(DbLsn.makeLsn(1, hdrSize),
                         fileManager.getLastUsedLsn());
            assertEquals(0, fileManager.getPrevEntryOffset());

            fileManager.bumpLsn(20L);
            /* Item placed here. */
            assertEquals(DbLsn.makeLsn(1,(hdrSize+40)),
                         fileManager.getLastUsedLsn());
            assertEquals(hdrSize, fileManager.getPrevEntryOffset());

            fileManager.bumpLsn(10L);
            /* Item placed here. */
            assertEquals(DbLsn.makeLsn(1,(hdrSize+60)),
                         fileManager.getLastUsedLsn());
            assertEquals(hdrSize+40, fileManager.getPrevEntryOffset());

            fileManager.bumpLsn(5L);
            /* item placed here. */
            assertEquals(DbLsn.makeLsn(1,(hdrSize+70)),
                         fileManager.getLastUsedLsn());
            assertEquals(hdrSize+60, fileManager.getPrevEntryOffset());

            /* bump over to file 2. */
            fileManager.bumpLsn(75L);
            /* Item placed here. */
            assertEquals(DbLsn.makeLsn(2, hdrSize),
                         fileManager.getLastUsedLsn());
            assertEquals(0, fileManager.getPrevEntryOffset());

            /* Ask for something bigger than a file: bump over to file 3. */
            fileManager.bumpLsn(125L);
            /* item placed here. */
            assertEquals(DbLsn.makeLsn(3, hdrSize),
                         fileManager.getLastUsedLsn());      
            assertEquals(0, fileManager.getPrevEntryOffset());

            /* bump over to file 4. */
            fileManager.bumpLsn(10L);
            /* Item placed here. */
            assertEquals(DbLsn.makeLsn(4, hdrSize),
			 fileManager.getLastUsedLsn());
            assertEquals(0, fileManager.getPrevEntryOffset());

            fileManager.bumpLsn(20L);
            /* Item placed here. */
            assertEquals(DbLsn.makeLsn(4, (hdrSize+10)),
			 fileManager.getLastUsedLsn());
            assertEquals(hdrSize, fileManager.getPrevEntryOffset());

            fileManager.bumpLsn(30L);
            /* Item placed here. */
            assertEquals(DbLsn.makeLsn(4, (hdrSize+30)),
			 fileManager.getLastUsedLsn());
            assertEquals((hdrSize+10), fileManager.getPrevEntryOffset());

        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * Test initializing the last position in the logs.
     */
    public void testSetLastPosition()
        throws DatabaseException {

        /*
         * Pretend that the last file is file 79.
         */
        fileManager.setLastPosition(// next available LSN
				    DbLsn.makeLsn(79L, 88L),
                                    DbLsn.makeLsn(79L, 77),
                                    66L);

        /* Put an entry down, should fit within file 79. */
        fileManager.bumpLsn(11L);
        assertEquals(DbLsn.makeLsn(79L, 88L), fileManager.getLastUsedLsn());
        assertEquals(77L, fileManager.getPrevEntryOffset());

        /* Put another entry in, should go to the next file. */
        fileManager.bumpLsn(22L);
        assertEquals(DbLsn.makeLsn(80L, FileManager.firstLogEntryOffset()),
                     fileManager.getLastUsedLsn());
        assertEquals(0, fileManager.getPrevEntryOffset());
    }

    /**
     * Test log file naming.
     */
    public void testFileNameFormat()
        throws DatabaseException {

        String filePrefix = envHome + File.separator;
        assertEquals(filePrefix + "00000001.jdb",
                     fileManager.getFullFileNames(1L)[0]);
        assertEquals(filePrefix + "0000007b.jdb",
                     fileManager.getFullFileNames(123L)[0]);
    }

    /**
     * Test log file creation.
     */
    public void testFileCreation()
        throws IOException, DatabaseException {

        FileManagerTestUtils.createLogFile(fileManager, envImpl, FILE_SIZE);
        FileManagerTestUtils.createLogFile(fileManager, envImpl, FILE_SIZE);

        String [] jeFiles = fileManager.listFiles(FileManager.JE_SUFFIXES);

        assertEquals("Should have two files", 2, jeFiles.length);

        /* Make a fake files with confusing names. */
        File fakeFile1 = new File(envHome, "00000abx.jdb");
        File fakeFile2 = new File(envHome, "10.10.jdb");
        fakeFile1.createNewFile();
        fakeFile2.createNewFile();

        jeFiles = fileManager.listFiles(FileManager.JE_SUFFIXES);
        assertEquals("Should have two files", 2, jeFiles.length);

        /* Open the two existing log files. */
        FileHandle file0Handle = fileManager.getFileHandle(0L);
        FileHandle file1Handle = fileManager.getFileHandle(1L);

        jeFiles = fileManager.listFiles(FileManager.JE_SUFFIXES);
        assertEquals("Should have two files", 2, jeFiles.length);
        file0Handle.release();
        file1Handle.release();

        /* Empty the cache and get them again. */
        fileManager.clear();
        file0Handle = fileManager.getFileHandle(0L);
        file1Handle = fileManager.getFileHandle(1L);

        jeFiles = fileManager.listFiles(FileManager.JE_SUFFIXES);
        assertEquals("Should have two files", 2, jeFiles.length);
        file0Handle.close();
        file1Handle.close();
        file0Handle.release();
        file1Handle.release();

        fakeFile1.delete();
        fakeFile2.delete();
    }

    /**
     * Make sure we can find the last file.
     */
    public void testLastFile()
        throws IOException, DatabaseException {

        /* There shouldn't be any files here anymore. */
        String [] jeFiles = fileManager.listFiles(FileManager.JE_SUFFIXES);
        assertTrue(jeFiles.length == 0);

        /* No files exist, should get null. */
        assertNull("No last file", fileManager.getLastFileNum());

        /* Create some files, ask for the largest. */
        File fakeFile1 = new File(envHome, "108.cif");
        fakeFile1.createNewFile();
        FileManagerTestUtils.createLogFile(fileManager, envImpl, FILE_SIZE);
        FileManagerTestUtils.createLogFile(fileManager, envImpl, FILE_SIZE);
        FileManagerTestUtils.createLogFile(fileManager, envImpl, FILE_SIZE);

        assertEquals("Should have 2 as last file", 2L, 
                     fileManager.getLastFileNum().longValue());
        fakeFile1.delete();
    }

    /**
     * Make sure we can find the next file in a set of files.
     */
    public void testFollowingFile()
        throws IOException, DatabaseException {

        /* There shouldn't be any files here anymore. */
        String [] jeFiles = fileManager.listFiles(FileManager.JE_SUFFIXES);
        assertTrue(jeFiles.length == 0);

        /* No files exist, should get null. */
        assertNull("No last file", fileManager.getFollowingFileNum(0, true));
        assertNull("No last file", fileManager.getFollowingFileNum(0, false));
        assertNull("No last file", fileManager.getFollowingFileNum(1, true));
        assertNull("No last file", fileManager.getFollowingFileNum(-1, false));

        /* Create some files. */
        File okFile1 = new File(envHome, "00000001.jdb");
        okFile1.createNewFile();

        File fakeFile3 = new File(envHome, "003.jdb");
        fakeFile3.createNewFile();

        File okFile6 = new File(envHome, "00000006.jdb");
        okFile6.createNewFile();

        File okFile9 = new File(envHome, "00000009.jdb");
        okFile9.createNewFile();


        /* Test forward */
        assertEquals("Should get 6 next", 6L,
                     fileManager.getFollowingFileNum(2, true).longValue());
        assertEquals("Should get 9 next, testing non-existent file", 9L,
                     fileManager.getFollowingFileNum(8, true).longValue());
        assertNull("Should get null next", 
		   fileManager.getFollowingFileNum(9, true));
        assertNull("Should get null next", 
		   fileManager.getFollowingFileNum(10, true));

        /* Test prev */
        assertEquals("Should get 6 next, testing non-existent file", 6L,
                     fileManager.getFollowingFileNum(8, false).longValue());
        assertEquals("Should get 6 next", 6L,
                     fileManager.getFollowingFileNum(9, false).longValue());
        assertNull("Should get null next", 
		   fileManager.getFollowingFileNum(1, false));
        assertNull("Should get null next", 
		   fileManager.getFollowingFileNum(0, false));

        okFile1.delete();
        fakeFile3.delete();
        okFile6.delete();
        okFile9.delete();
    }

    /**
     * See if we can catch a file with an invalid header.
     */
    public void testBadHeader()
        throws IOException, DatabaseException {

        /* First try a bad environment r/w. */
        try {
            FileManager test =
                new FileManager(envImpl, new File("xxyy"), true);
            fail("expect creation of " + test + "to fail.");
        } catch (LogException e) {
            /* should throw */
        }
        
        /* Next try a bad environment r/o. */
        try {
            FileManager test =
                new FileManager(envImpl, new File("xxyy"), false);
            fail("expect creation of " + test + "to fail.");
        } catch (DatabaseException e) {
            /* should throw */
        }
        
        /* Now create a file, but mess up the header. */
        FileManagerTestUtils.createLogFile(fileManager, envImpl, FILE_SIZE);

        byte [] badData = new byte[]{1,1};
        int headerSize =  FileManager.firstLogEntryOffset();
        RandomAccessFile file0 = 
            new RandomAccessFile
		(fileManager.getFullFileName(0, FileManager.JE_SUFFIX),
		 FileManager.FileMode.READWRITE_MODE.getModeValue());
        file0.write(badData);
        file0.close();
        fileManager.clear();

        try {
            FileHandle file0Handle = fileManager.getFileHandle(0L);
            fail("expect to catch a checksum error");
        } catch (DbChecksumException e) {
        }
    }

    public void testTruncatedHeader()
        throws IOException, DatabaseException {

        /* Create a log file */
        FileManagerTestUtils.createLogFile(fileManager, envImpl, FILE_SIZE);

        /* Truncate the header */
        RandomAccessFile file0 = 
            new RandomAccessFile
		(fileManager.getFullFileName(0, FileManager.JE_SUFFIX),
		 FileManager.FileMode.READWRITE_MODE.getModeValue());
        file0.getChannel().truncate(FileManager.firstLogEntryOffset()/2);
        file0.close();

        try {
            fileManager.getFileHandle(0);
            fail("Should see assertion");
        } catch (DatabaseException e) {
        }
    }

    /**
     * Test the file cache.
     */
    public void testCache()
        throws Throwable {

        try {

            /* 
             * Make five log files. The file descriptor cache should be empty.
             */
            FileManagerTestUtils.createLogFile
		(fileManager, envImpl, FILE_SIZE);
            FileManagerTestUtils.createLogFile
		(fileManager, envImpl, FILE_SIZE);
            FileManagerTestUtils.createLogFile
		(fileManager, envImpl, FILE_SIZE);
            FileManagerTestUtils.createLogFile
		(fileManager, envImpl, FILE_SIZE);
            FileManagerTestUtils.createLogFile
		(fileManager, envImpl, FILE_SIZE);
            
            Long f0 = new Long(0L);
            Long f1 = new Long(1L);
            Long f2 = new Long(2L);
            Long f3 = new Long(3L);
            Long f4 = new Long(4L);

            Set keySet = fileManager.getCacheKeys();
            assertEquals("should have 0 keys", 0, keySet.size());

            /* 
             * Get file descriptors for three files, expect 3 handles in the
             * cache.
             */
            FileHandle f0Handle = fileManager.getFileHandle(0);
            FileHandle f1Handle = fileManager.getFileHandle(1);
            FileHandle f2Handle = fileManager.getFileHandle(2);
            keySet = fileManager.getCacheKeys();
            assertEquals("should have 3 keys", 3, keySet.size());
            assertTrue(keySet.contains(f0));
            assertTrue(keySet.contains(f1));
            assertTrue(keySet.contains(f2));

            /* 
             * Ask for a fourth handle, the cache should grow even though it
             * was set to 3 as a starting size, because all handles are
             * locked. Do it within another thread, otherwise we'll get a
             * latch-already-held exception when we test the other handles in
             * the cache. The other thread will get the handle and then release
             * it.
             */
            CachingThread otherThread = new CachingThread(fileManager, 3);
            otherThread.start();
            otherThread.join();

            keySet = fileManager.getCacheKeys();
            assertEquals("should have 4 keys", 4, keySet.size());
            assertTrue(keySet.contains(f0));
            assertTrue(keySet.contains(f1));
            assertTrue(keySet.contains(f2));
            assertTrue(keySet.contains(f3));

            /* 
             * Now ask for another file. The cache should not grow, because no
             * handles are locked and there's room to evict one.
             */
            f0Handle.release();
            f1Handle.release();
            f2Handle.release();
            FileHandle f4Handle = fileManager.getFileHandle(4);
            keySet = fileManager.getCacheKeys();
            assertEquals("should have 4 keys", 4, keySet.size());
            assertTrue(keySet.contains(f4));

            f4Handle.release();

            /* Clearing should release all file descriptors. */
            fileManager.clear();
            assertEquals("should have 0 keys", 0,
                         fileManager.getCacheKeys().size());
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
    }

    public void testFlipFile()
	throws Throwable {

        /*
         * The setUp() method opens a standalone FileManager, but in this test
         * case we need a regular Environment.  On Windows, we can't lock the
         * file range twice in FileManager.lockEnvironment, so we must close
         * the standalone FileManager here before opening a regular
         * environment.
         */
        fileManager.clear();
        fileManager.close();
        fileManager = null;

        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setAllowCreate(true);
        envConfig.setTransactional(true);
        Environment env = new Environment(envHome, envConfig);
	EnvironmentImpl envImpl = DbInternal.envGetEnvironmentImpl(env);
	FileManager fileManager = envImpl.getFileManager();

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
        Database exampleDb =
	    env.openDatabase(null, "simpleDb", dbConfig);

        assertEquals("Should have 0 as current file", 0L,
                     fileManager.getCurrentFileNum());
	long flipLsn = envImpl.forceLogFileFlip();
	assertEquals("LSN should be 1 post-flip", 1L,
		     DbLsn.getFileNumber(flipLsn));
	DatabaseEntry key = new DatabaseEntry();
	DatabaseEntry data = new DatabaseEntry();
	key.setData("key".getBytes());
	data.setData("data".getBytes());
	exampleDb.put(null, key, data);
        assertEquals("Should have 1 as last file", 1L,
                     fileManager.getCurrentFileNum());
	exampleDb.close();
	env.close();
    }	

    class CachingThread extends Thread {
        private FileManager fileManager;
        private long fileNum;
 
        private FileHandle handle;

        CachingThread(FileManager fileManager, long fileNum) {
            this.fileManager = fileManager;
            this.fileNum = fileNum;
        }

        public void run() {
            try {
                handle = fileManager.getFileHandle(fileNum);
                handle.release();
            } catch (Exception e) {
                fail(e.getMessage());
            }
        }

        FileHandle getHandle() {
            return handle;
        }
    }
}
