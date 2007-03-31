/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: FileManager.java,v 1.162.2.1 2007/02/01 14:49:47 cwl Exp $
 */

package com.sleepycat.je.log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.EnvironmentStats;
import com.sleepycat.je.RunRecoveryException;
import com.sleepycat.je.StatsConfig;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.DbConfigManager;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.latch.Latch;
import com.sleepycat.je.latch.LatchSupport;
import com.sleepycat.je.log.entry.LogEntry;
import com.sleepycat.je.log.entry.SingleItemEntry;
import com.sleepycat.je.utilint.DbLsn;
import com.sleepycat.je.utilint.HexFormatter;

/**
 * The FileManager presents the abstraction of one contiguous file.  It doles
 * out LSNs.
 */
public class FileManager {

    public static class FileMode {
        public static final FileMode READ_MODE = new FileMode("r");
        public static final FileMode READWRITE_MODE = new FileMode("rw");

        private String fileModeValue;

        private FileMode(String fileModeValue) {
            this.fileModeValue = fileModeValue;
        }

        public String getModeValue() {
            return fileModeValue;
        }
    }

    static boolean IO_EXCEPTION_TESTING = false;
    private static final String DEBUG_NAME = FileManager.class.getName();
    /* The number of writes that have been performed. */
    private static long writeCount = 0;
    private static long stopOnWriteCount = Long.MAX_VALUE;

    public static final String JE_SUFFIX = ".jdb";  // regular log files
    public static final String DEL_SUFFIX = ".del";  // cleaned files
    public static final String BAD_SUFFIX = ".bad";  // corrupt files
    private static final String LOCK_FILE = "je.lck";// lock file
    static final String[] DEL_SUFFIXES = { DEL_SUFFIX };
    static final String[] JE_SUFFIXES = { JE_SUFFIX };
    private static final String[] JE_AND_DEL_SUFFIXES =
    { JE_SUFFIX, DEL_SUFFIX };

    /* May be set to false to speed unit tests. */
    private boolean syncAtFileEnd = true;

    private EnvironmentImpl envImpl;
    private long maxFileSize;
    private File dbEnvHome;

    /* True if .del files should be included in the list of log files. */
    private boolean includeDeletedFiles = false;

    /* File cache */
    private FileCache fileCache;
    private Latch fileCacheLatch;

    /* The channel and lock for the je.lck file. */
    private RandomAccessFile lockFile;
    private FileChannel channel;
    private FileLock envLock;
    private FileLock exclLock;

    /* True if all files should be opened readonly. */
    private boolean readOnly;

    /* Handles onto log position */
    private long currentFileNum;     // number of the current file
    private long nextAvailableLsn;   // nextLSN is the next one available
    private long lastUsedLsn;        // last LSN used in the current log file
    private long prevOffset;         // Offset to use for the previous pointer
    private boolean forceNewFile;    // Force new file on next write

    /* 
     * Saved versions of above.  Save this in case a write causes an
     * IOException, we can back the log up to the last known good LSN.
     */
    private long savedCurrentFileNum;
    private long savedNextAvailableLsn; // nextLSN is the next one available
    private long savedLastUsedLsn;   // last LSN used in the current log file
    private long savedPrevOffset;    // Offset to use for the previous pointer
    private boolean savedForceNewFile;

    /* endOfLog is used for writes and fsyncs to the end of the log. */
    private LogEndFileDescriptor endOfLog;

    /* group commit sync */
    private FSyncManager syncManager;

    /*
     * When we bump the LSNs over to a new file, we must remember the last LSN
     * of the previous file so we can set the prevOffset field of the file
     * header appropriately. We have to save it in a map because there's a time
     * lag between when we know what the last LSN is and when we actually do
     * the file write, because LSN bumping is done before we get a write
     * buffer.  This map is keyed by file num->last LSN.
     */
    private Map perFileLastUsedLsn;

    /* Whether to use NIO for file I/O. */
    private boolean useNIO;
    
    /*
     * If non-0, do NIO in chunks of this size.
     */
    private long chunkedNIOSize = 0;

    /**
     * Set up the file cache and initialize the file manager to point to the
     * beginning of the log.
     * 
     * @param configManager
     * @param dbEnvHome environment home directory
     */
    public FileManager(EnvironmentImpl envImpl,
                       File dbEnvHome,
                       boolean readOnly)
        throws DatabaseException {

        this.envImpl = envImpl;
        this.dbEnvHome = dbEnvHome;
        this.readOnly = readOnly;

        /* Read configurations. */
        DbConfigManager configManager = envImpl.getConfigManager();
        maxFileSize = configManager.getLong(EnvironmentParams.LOG_FILE_MAX);

        useNIO =
            configManager.getBoolean(EnvironmentParams.LOG_USE_NIO);
        chunkedNIOSize =
            configManager.getLong(EnvironmentParams.LOG_CHUNKED_NIO);
        boolean directNIO =
            configManager.getBoolean(EnvironmentParams.LOG_DIRECT_NIO);

        if (!useNIO && (chunkedNIOSize > 0 || directNIO)) {
            throw new IllegalArgumentException
                (EnvironmentParams.LOG_USE_NIO.getName() +
                 " is false and therefore " +
                 EnvironmentParams.LOG_DIRECT_NIO.getName() +
                 " or " +
                 EnvironmentParams.LOG_CHUNKED_NIO.getName() +
                 " may not be used.");
        }

        if (!envImpl.isMemOnly()) {
            if (!dbEnvHome.exists()) {
                throw new LogException("Environment home " + dbEnvHome +
                                         " doesn't exist");
            }
            lockEnvironment(readOnly, false);
        }

        /* Cache of files. */
        fileCache = new FileCache(configManager);
        fileCacheLatch =
            LatchSupport.makeLatch(DEBUG_NAME + "_fileCache", envImpl);

        /* Start out as if no log existed. */
        currentFileNum = 0L;
        nextAvailableLsn = DbLsn.makeLsn(currentFileNum,
                                         firstLogEntryOffset());
        lastUsedLsn = DbLsn.NULL_LSN;
        perFileLastUsedLsn = new HashMap();
        prevOffset = 0L;
        endOfLog = new LogEndFileDescriptor();
        forceNewFile = false;
        saveLastPosition();

        String stopOnWriteProp = System.getProperty("je.debug.stopOnWrite");
        if (stopOnWriteProp != null) {
            stopOnWriteCount = Long.parseLong(stopOnWriteProp);
        }

        syncManager = new FSyncManager(envImpl);
    }

    /**
     * Set the file manager's "end of log".
     *
     * @param nextAvailableLsn LSN to be used for the next log entry
     * @param lastUsedLsn last LSN to have a valid entry, may be null
     * @param prevOffset value to use for the prevOffset of the next entry.
     *  If the beginning of the file, this is 0.
     */
    public void setLastPosition(long nextAvailableLsn,
                                long lastUsedLsn,
                                long prevOffset) {
        this.lastUsedLsn = lastUsedLsn;
        perFileLastUsedLsn.put(new Long(DbLsn.getFileNumber(lastUsedLsn)),
                               new Long(lastUsedLsn));
        this.nextAvailableLsn = nextAvailableLsn;
        currentFileNum = DbLsn.getFileNumber(this.nextAvailableLsn);
        this.prevOffset = prevOffset;
        saveLastPosition();
    }

    /*
     * Cause the current LSN state to be saved in case we fail after we have
     * bumped the lsn pointer but before we've successfully marshalled into the
     * log buffer.
     */
    void saveLastPosition() {
        savedNextAvailableLsn = nextAvailableLsn;
        savedLastUsedLsn = lastUsedLsn;
        savedPrevOffset = prevOffset;
        savedForceNewFile = forceNewFile;
        savedCurrentFileNum = currentFileNum;
    }

    void restoreLastPosition() {
        nextAvailableLsn = savedNextAvailableLsn;
        lastUsedLsn = savedLastUsedLsn;
        prevOffset = savedPrevOffset;
        forceNewFile = savedForceNewFile;
        currentFileNum = savedCurrentFileNum;
    }

    /**
     * May be used to disable sync at file end to speed unit tests.
     * Must only be used for unit testing, since log corruption may result.
     */
    public void setSyncAtFileEnd(boolean sync) {
        syncAtFileEnd = sync;
    }

    /*
     * File management
     */

    /**
     * public for cleaner.
     *
     * @return the number of the first file in this environment.
     */
    public Long getFirstFileNum() {
        return getFileNum(true);
    }

    public boolean getReadOnly() {
        return readOnly;
    }

    /**
     * @return the number of the last file in this environment.
     */
    public Long getLastFileNum() {
        return getFileNum(false);
    }

    /*
     * For unit tests.
     */
    public long getCurrentFileNum() {
        return currentFileNum;
    }

    public void setIncludeDeletedFiles(boolean includeDeletedFiles) {
        this.includeDeletedFiles = includeDeletedFiles;
    }

    /**
     * Get all JE file numbers.
     * @return an array of all JE file numbers.
     */
    public Long[] getAllFileNumbers() {
        /* Get all the names in sorted order. */
        String[] names = listFiles(JE_SUFFIXES);
        Long[] nums = new Long[names.length];
        for (int i = 0; i < nums.length; i += 1) {
            nums[i] = getNumFromName(names[i]);
        }
        return nums;
    }

    /**
     * Get the next file number before/after currentFileNum. 
     * @param currentFileNum the file we're at right now. Note that
     * it may not exist, if it's been cleaned and renamed.
     * @param forward if true, we want the next larger file, if false
     * we want the previous file
     * @return null if there is no following file, or if filenum doesn't exist
     */
    public Long getFollowingFileNum(long currentFileNum, boolean forward) {
        /* Get all the names in sorted order. */
        String[] names = listFiles(JE_SUFFIXES);

        /* Search for the current file. */
        String searchName = getFileName(currentFileNum, JE_SUFFIX);
        int foundIdx = Arrays.binarySearch(names, searchName);

        boolean foundTarget = false;
        if (foundIdx >= 0) {
            if (forward) {
                foundIdx++;
            } else {
                foundIdx --;
            }
        } else {

            /* 
             * currentFileNum not found (might have been cleaned). FoundIdx
             * will be (-insertionPoint - 1).
             */
            foundIdx = Math.abs(foundIdx + 1);
            if (!forward) {
                foundIdx--;
            }
        }

        /* The current fileNum is found, return the next or prev file. */
        if (forward && (foundIdx < names.length)) {
            foundTarget = true;
        } else if (!forward && (foundIdx > -1)) {
            foundTarget = true;
        }

        if (foundTarget) {
            return getNumFromName(names[foundIdx]);
        } else {
            return null;
        }
    }

    /**
     * @return true if there are any files at all.
     */
    public boolean filesExist() {
        String[] names = listFiles(JE_SUFFIXES);
        return (names.length != 0);
    }

    /**
     * Get the first or last file number in the set of je files.
     * 
     * @param first if true, get the first file, else get the last file
     * @return the file number or null if no files exist
     */
    private Long getFileNum(boolean first) {
        String[] names = listFiles(JE_SUFFIXES);
        if (names.length == 0) {
            return null;
        } else {
            int index = 0;
            if (!first) {
                index = names.length - 1;
            }
            return getNumFromName(names[index]);
        }
    }

    /**
     * Get the file number from a file name.
     * 
     * @param the file name
     * @return the file number
     */
    public Long getNumFromName(String fileName) {
        String fileNumber = fileName.substring(0, fileName.indexOf("."));
        return new Long(Long.parseLong(fileNumber, 16));
    }

    /**
     * Find je files. Return names sorted in ascending fashion.     
     * @param suffix which type of file we're looking for
     * @return array of file names
     */
    public String[] listFiles(String[] suffixes) {
        String[] fileNames = dbEnvHome.list(new JEFileFilter(suffixes));
        if (fileNames != null) {
            Arrays.sort(fileNames);
        } else {
            fileNames = new String[0];
        }
        return fileNames;
    }

    /**
     * Find .jdb files which are >= the minimimum file number and
     * <= the maximum file number.
     * Return names sorted in ascending fashion.
     * 
     * @return array of file names
     */
    public String[] listFiles(long minFileNumber, long maxFileNumber) {

        String[] fileNames = dbEnvHome.list(new JEFileFilter(JE_SUFFIXES,
                                                             minFileNumber,
                                                             maxFileNumber));
        Arrays.sort(fileNames);
        return fileNames;
    }

   /**
     * Find je files, flavor for unit test support.
     * 
     * @param suffix which type of file we're looking for
     * @return array of file names
     */
    public static String[] listFiles(File envDirFile, String[] suffixes) {
        String[] fileNames = envDirFile.list(new JEFileFilter(suffixes));
        if (fileNames != null) {
            Arrays.sort(fileNames);
        } else {
            fileNames = new String[0];
        }
        return fileNames;
    }

    /**
     * @return the full file name and path for the nth je file.
     */
    String[] getFullFileNames(long fileNum) {
        if (includeDeletedFiles) {
            int nSuffixes = JE_AND_DEL_SUFFIXES.length;
            String[] ret = new String[nSuffixes];
            for (int i = 0; i < nSuffixes; i++) {
                ret[i] = getFullFileName(getFileName(fileNum,
                                                     JE_AND_DEL_SUFFIXES[i]));
            }
            return ret;
        } else {
            return new String[]
                { getFullFileName(getFileName(fileNum, JE_SUFFIX)) };
        }
    }

    /**
     * @return the full file name and path for the given file number and
     * suffix.
     */
    public String getFullFileName(long fileNum, String suffix) {
        return getFullFileName(getFileName(fileNum, suffix));
    }

    /**
     * @return the full file name and path for this file name.
     */
    private String getFullFileName(String fileName) {
        return dbEnvHome + File.separator + fileName;
    }

    /**
     * @return the file name for the nth file.
     */
    public static String getFileName(long fileNum, String suffix) {

        /* 
         * HexFormatter generates a 0 padded string starting with 0x.  We want
         * the right most 8 digits, so start at 10.
         */
        return (HexFormatter.formatLong(fileNum).substring(10) + suffix);
    }

    /**
     * Rename this file to NNNNNNNN.suffix. If that file already exists, try
     * NNNNNNNN.suffix.1, etc. Used for deleting files or moving corrupt files
     * aside.
     * 
     * @param fileNum the file we want to move
     * @param newSuffix the new file suffix
     */
    public void renameFile(long fileNum, String newSuffix)
        throws DatabaseException, IOException {

        int repeatNum = 0;
        boolean renamed = false;
        while (!renamed) {
            String generation = "";
            if (repeatNum > 0) {
                generation = "." + repeatNum;
            }
            String newName =
                getFullFileName(getFileName(fileNum, newSuffix) + generation);
            File targetFile = new File(newName);
            if (targetFile.exists()) {
                repeatNum++;
            } else {
                String oldFileName = getFullFileNames(fileNum)[0];
                clearFileCache(fileNum);
                File oldFile = new File(oldFileName);
                if (oldFile.renameTo(targetFile)) {
                    renamed = true;
                } else {
                    throw new LogException("Couldn't rename " + oldFileName +
                                             " to " + newName);
                }
            }
        }
    }

    /**
     * Delete log file NNNNNNNN.
     * 
     * @param fileNum the file we want to move
     */
    public void deleteFile(long fileNum)
        throws DatabaseException, IOException {

        String fileName = getFullFileNames(fileNum)[0];
        clearFileCache(fileNum);
        File file = new File(fileName);
        boolean done = file.delete();
        if (!done) {
            throw new LogException
                ("Couldn't delete " + file);
        }
    }

    /**
     * Return a read only file handle that corresponds the this file number.
     * Retrieve it from the cache or open it anew and validate the file header.
     * This method takes a latch on this file, so that the file descriptor will
     * be held in the cache as long as it's in use.  When the user is done with
     * the file, the latch must be released.
     *
     * @param fileNum which file
     * @return the file handle for the existing or newly created file
     */
    FileHandle getFileHandle(long fileNum)
        throws LogException, DatabaseException  {

        /* Check the file cache for this file. */
        Long fileId = new Long(fileNum);
        FileHandle fileHandle = null;

        /**
         * Loop until we get an open FileHandle.
         */
        while (true) {

            /*
             * The file cache is intentionally not latched here so that it's
             * not a bottleneck in the fast path.  We check that the file
             * handle that we get back is really still open after we latch it
             * down below.
             */
            fileHandle = fileCache.get(fileId);

            /* The file wasn't in the cache. */
            if (fileHandle == null) {
                fileCacheLatch.acquire();
                try {
                    /* Check the file cache again under the latch. */
                    fileHandle = fileCache.get(fileId);
                    if (fileHandle == null) {

                        fileHandle =
                            makeFileHandle(fileNum, FileMode.READ_MODE);

                        /* Put it into the cache. */
                        fileCache.add(fileId, fileHandle);
                    }
                } finally {
                    fileCacheLatch.release();
                }
            }

            /* Get latch before returning */
            fileHandle.latch();

            /*
             * We may have obtained this file handle outside the file cache
             * latch, so we have to test that the handle is still valid.  If
             * it's not, then loop back and try again.
             */
            if (fileHandle.getFile() == null) {
                fileHandle.release();
            } else {
                break;
            }
        }

        return fileHandle;
    }

    private FileHandle makeFileHandle(long fileNum, FileMode mode)
        throws DatabaseException {

        String[] fileNames = getFullFileNames(fileNum);
        RandomAccessFile newFile = null;
        String fileName = null;
        try {

            /* 
             * Open the file. Note that we are going to try a few names to open
             * this file -- we'll try for N.jdb, and if that doesn't exist and
             * we're configured to look for all types, we'll look for N.del.
             */
            FileNotFoundException FNFE = null;
            for (int i = 0; i < fileNames.length; i++) {
                fileName = fileNames[i];
                try {
                    newFile =
                        new RandomAccessFile(fileName, mode.getModeValue());
                    break;
                } catch (FileNotFoundException e) {
                    /* Save the first exception thrown. */
                    if (FNFE == null) {
                        FNFE = e;
                    }
                }
            }

            /* 
             * If we didn't find the file or couldn't create it, rethrow the
             * exception.
             */
            if (newFile == null) {
                throw FNFE;
            }

            boolean oldHeaderVersion = false;

            if (newFile.length() == 0) {
                /* 
                 * If the file is empty, reinitialize it if we can. If
                 * not, send the file handle back up; the calling code will
                 * deal with the fact that there's nothing there.
                 */
                if (mode == FileMode.READWRITE_MODE) {
                    /* An empty file, write a header. */
                    long lastLsn = DbLsn.longToLsn((Long)
                                                   perFileLastUsedLsn.remove
                                                   (new Long(fileNum - 1)));
                    long headerPrevOffset = 0;
                    if (lastLsn != DbLsn.NULL_LSN) {
                        headerPrevOffset = DbLsn.getFileOffset(lastLsn);
                    }
                    FileHeader fileHeader =
                        new FileHeader(fileNum,
                                       headerPrevOffset);
                    writeFileHeader(newFile,
                                    fileName,
                                    fileHeader);
                }
            } else {
                /* A non-empty file, check the header */
                oldHeaderVersion =
                    readAndValidateFileHeader(newFile, fileName, fileNum);
            }
            return new FileHandle
                (newFile, fileName, envImpl, oldHeaderVersion);
        } catch (FileNotFoundException e) {
            throw new LogFileNotFoundException
                ("Couldn't open file " + fileName + ": " +
                 e.getMessage());
        } catch (DbChecksumException e) {

            /* 
             * Let this exception go as a checksum exception, so it sets the
             * run recovery state correctly.
             */
            closeFileInErrorCase(newFile);
            throw new DbChecksumException
                (envImpl, "Couldn't open file " + fileName, e);
        } catch (Throwable t) {

            /*
             * Catch Throwable here (rather than exception) because in unit
             * test mode, we run assertions and they throw errors. We want to
             * clean up the file object in all cases.
             */
            closeFileInErrorCase(newFile);
            throw new DatabaseException
                ("Couldn't open file " + fileName + ": " + t, t);
        }
    }

    /**
     * Close this file and eat any exceptions. Used in catch clauses.
     */
    private void closeFileInErrorCase(RandomAccessFile file) {
        try {
            if (file != null) {
                file.close();
            }
        } catch (IOException e) {

            /* 
             * Klockwork - ok
             * Couldn't close file, oh well.
             */
        }
    }

    /**
     * Read the given je log file and validate the header. 
     *
     * @throws DatabaseException if the file header isn't valid
     *
     * @return whether the file header has an old version number.
     */
    private boolean readAndValidateFileHeader(RandomAccessFile file,
                                              String fileName,
                                              long fileNum)
        throws DatabaseException, IOException {

        /* 
         * Read the file header from this file. It's always the first log
         * entry.
         */
        LogManager logManager = envImpl.getLogManager();
        LogEntry headerEntry = 
            logManager.getLogEntry(DbLsn.makeLsn(fileNum, 0), file);
        FileHeader header = (FileHeader) headerEntry.getMainItem();
        return header.validate(fileName, fileNum);
    }

    /**
     * Write a proper file header to the given file.
     */
    private void writeFileHeader(RandomAccessFile file,
                                 String fileName,
                                 FileHeader header)
        throws DatabaseException, IOException {

        /*
         * Fail loudly if the environment is invalid.  A RunRecoveryException
         * must have occurred.
         */
        envImpl.checkIfInvalid();

        /*
         * Fail silent if the environment is not open.
         */
        if (envImpl.mayNotWrite()) {
            return;
        }

        /* Write file header into this buffer in the usual log entry format. */
        LogEntry headerLogEntry =
            new SingleItemEntry(LogEntryType.LOG_FILE_HEADER, header);
        ByteBuffer headerBuf = envImpl.getLogManager().
            putIntoBuffer(headerLogEntry,
                          0); // prevLogEntryOffset
        
        if (++writeCount >= stopOnWriteCount) {
            Runtime.getRuntime().halt(0xff);
        }

        /* Write the buffer into the channel. */
        int bytesWritten;
        try {
            if (RUNRECOVERY_EXCEPTION_TESTING) {
                generateRunRecoveryException(file, headerBuf, 0);
            }
            bytesWritten = writeToFile(file, headerBuf, 0);
        } catch (ClosedChannelException e) {

            /* 
             * The channel should never be closed. It may be closed because
             * of an interrupt received by another thread. See SR [#10463]
             */
            throw new RunRecoveryException
                (envImpl, "Channel closed, may be due to thread interrupt", e);
        } catch (IOException e) {
            /* Possibly an out of disk exception. */
            throw new RunRecoveryException
                (envImpl, "IOException caught: " + e);
        }

        if (bytesWritten != headerLogEntry.getSize() +
            LogEntryHeader.MIN_HEADER_SIZE) {
            throw new LogException
                ("File " + fileName +
                 " was created with an incomplete header. Only " +
                 bytesWritten + " bytes were written.");
        }
    }

    /**
     * @return the prevOffset field stored in the file header.
     */
    long getFileHeaderPrevOffset(long fileNum) 
        throws IOException, DatabaseException {
        
        LogEntry headerEntry = 
            envImpl.getLogManager().getLogEntry(DbLsn.makeLsn(fileNum, 0));
        FileHeader header = (FileHeader) headerEntry.getMainItem();
        return header.getLastEntryInPrevFileOffset();
    }

    /*
     * Support for writing new log entries
     */

    /**
     * @return the file offset of the last LSN that was used. For constructing
     * the headers of log entries. If the last LSN that was used was in a
     * previous file, or this is the very first LSN of the whole system, return
     * 0.
     */
    long getPrevEntryOffset() {
        return prevOffset;
    }

    /**
     * Increase the current log position by "size" bytes. Move the prevOffset
     * pointer along.
     * 
     * @param size is an unsigned int
     * @return true if we flipped to the next log file.
     */
    boolean bumpLsn(long size) {

        /* Save copy of initial lsn state. */
        saveLastPosition();

        boolean flippedFiles = false;

        if (forceNewFile ||
            (DbLsn.getFileOffset(nextAvailableLsn) + size) > maxFileSize) {

            forceNewFile = false;

            /* Move to another file. */
            currentFileNum++;

            /* Remember the last used LSN of the previous file. */
            if (lastUsedLsn != DbLsn.NULL_LSN) {
                perFileLastUsedLsn.put
                    (new Long(DbLsn.getFileNumber(lastUsedLsn)),
                     new Long(lastUsedLsn));
            }
            prevOffset = 0;
            lastUsedLsn = DbLsn.makeLsn(currentFileNum, 
                                        firstLogEntryOffset());
            flippedFiles = true;
        } else {
            if (lastUsedLsn == DbLsn.NULL_LSN) {
                prevOffset = 0; 
            } else {
                prevOffset = DbLsn.getFileOffset(lastUsedLsn);
            }
            lastUsedLsn = nextAvailableLsn;
        }
        nextAvailableLsn =
            DbLsn.makeLsn(DbLsn.getFileNumber(lastUsedLsn),
                          (DbLsn.getFileOffset(lastUsedLsn) + size));

        return flippedFiles;
    }

    /**
     * Write out a log buffer to the file.
     * @param fullBuffer buffer to write
     */
    void writeLogBuffer(LogBuffer fullBuffer)
        throws DatabaseException {

        /*
         * Fail loudly if the environment is invalid.  A RunRecoveryException
         * must have occurred.
         */
        envImpl.checkIfInvalid();

        /*
         * Fail silent if the environment is not open.
         */
        if (envImpl.mayNotWrite()) {
            return;
        }

        /* Use the LSN to figure out what file to write this buffer to. */
        long firstLsn = fullBuffer.getFirstLsn();

        /*
         * Is there anything in this write buffer? We could have been called by
         * the environment shutdown, and nothing is actually in the buffer.
         */
        if (firstLsn != DbLsn.NULL_LSN) {
            
            RandomAccessFile file =
                endOfLog.getWritableFile(DbLsn.getFileNumber(firstLsn));
            ByteBuffer data = fullBuffer.getDataBuffer();
 
            if (++writeCount >= stopOnWriteCount) {
                Runtime.getRuntime().halt(0xff);
            }

            try {

                /*
                 * Check that we do not overwrite unless the file only contains
                 * a header [#11915] [#12616].
                 */
                assert fullBuffer.getRewriteAllowed() ||
                    (DbLsn.getFileOffset(firstLsn) >= file.length() ||
                     file.length() == firstLogEntryOffset()) :
                        "FileManager would overwrite non-empty file 0x" +
                        Long.toHexString(DbLsn.getFileNumber(firstLsn)) +
                        " lsnOffset=0x" +
                        Long.toHexString(DbLsn.getFileOffset(firstLsn)) +
                        " fileLength=0x" +
                        Long.toHexString(file.length());

                if (IO_EXCEPTION_TESTING) {
                    throw new IOException("generated for testing");
                }
                if (RUNRECOVERY_EXCEPTION_TESTING) {
                    generateRunRecoveryException
                        (file, data, DbLsn.getFileOffset(firstLsn));
                }
                writeToFile(file, data, DbLsn.getFileOffset(firstLsn));
            } catch (ClosedChannelException e) {

                /*
                 * The file should never be closed. It may be closed because
                 * of an interrupt received by another thread. See SR [#10463].
                 */
                throw new RunRecoveryException
                    (envImpl, "File closed, may be due to thread interrupt",
                     e);
            } catch (IOException IOE) {

                /* 
                 * Possibly an out of disk exception, but java.io will only
                 * tell us IOException with no indication of whether it's out
                 * of disk or something else.
                 *
                 * Since we can't tell what sectors were actually written to
                 * disk, we need to change any commit records that might have
                 * made it out to disk to abort records.  If they made it to
                 * disk on the write, then rewriting should allow them to be
                 * rewritten.  See [11271].
                 */
                abortCommittedTxns(data);
                try {
                    if (IO_EXCEPTION_TESTING) {
                        throw new IOException("generated for testing");
                    }
                    writeToFile(file, data, DbLsn.getFileOffset(firstLsn));
                } catch (IOException IOE2) {
                    fullBuffer.setRewriteAllowed();
                    throw new DatabaseException(IOE2);
                }
            }

            assert EnvironmentImpl.maybeForceYield();
        }
    }

    /**
     * Write a buffer to a file at a given offset, using NIO if so configured.
     */
    private int writeToFile(RandomAccessFile file,
                            ByteBuffer data,
                            long destOffset)
        throws IOException, DatabaseException {

        int totalBytesWritten = 0;
        if (useNIO) {
            FileChannel channel = file.getChannel();

            if (chunkedNIOSize > 0) {

                /*
                 * We can't change the limit without impacting readers that
                 * might find this buffer in the buffer pool.  Duplicate the
                 * buffer so we can set the limit independently.
                 */
                ByteBuffer useData = data.duplicate();

                /*
                 * Write small chunks of data by manipulating the position and
                 * limit properties of the buffer, and submitting it for
                 * writing repeatedly.
                 *
                 * For each chunk, the limit is set to the position +
                 * chunkedNIOSize, capped by the original limit of the buffer.
                 *
                 * Preconditions: data to be written is betweek data.position()
                 * and data.limit()

                 * Postconditions: data.limit() has not changed,
                 * data.position() == data.limit(), offset of the channel has
                 * not been modified.
                 */
                int originalLimit = useData.limit();
                useData.limit(useData.position());
                while (useData.limit() < originalLimit) {
                    useData.limit((int)
                                  (Math.min(useData.limit() + chunkedNIOSize,
                                            originalLimit)));
                    int bytesWritten = channel.write(useData, destOffset);
                    destOffset += bytesWritten;
                    totalBytesWritten += bytesWritten;
                }
            } else {

                /*
                 * Perform a single write using NIO.
                 */
                totalBytesWritten = channel.write(data, destOffset);
            }
        } else {

            /*
             * Perform a RandomAccessFile write and update the buffer position.
             * ByteBuffer.array() is safe to use since all non-direct
             * ByteBuffers have a backing array.  Synchronization on the file
             * object is needed because two threads may call seek() on the same
             * file object.
             */
            synchronized (file) {
                assert data.hasArray();
                assert data.arrayOffset() == 0;

                int pos = data.position();
                int size = data.limit() - pos;
                file.seek(destOffset);
                file.write(data.array(), pos, size);
                data.position(pos + size);
                totalBytesWritten = size;
            }
        }
        return totalBytesWritten;
    }
 
    /**
     * Read a buffer from a file at a given offset, using NIO if so configured.
     */
    void readFromFile(RandomAccessFile file,
                      ByteBuffer readBuffer,
                      long offset)
        throws IOException {
 
        if (useNIO) {
            FileChannel channel = file.getChannel();

            if (chunkedNIOSize > 0) {

                /*
                 * Read a chunk at a time to prevent large direct memory
                 * allocations by NIO.
                 */
                int readLength = readBuffer.limit();
                long currentPosition = offset;
                while (readBuffer.position() < readLength) {
                    readBuffer.limit((int)
                                     (Math.min(readBuffer.limit() +
                                               chunkedNIOSize,
                                               readLength)));
                    int bytesRead = channel.read(readBuffer, currentPosition);
      
                    if (bytesRead < 1)
                        break;
      
                    currentPosition += bytesRead;
                }
            } else {

                /*
                 * Perform a single read using NIO.
                 */
                channel.read(readBuffer, offset);
            }
        } else {

            /*
             * Perform a RandomAccessFile read and update the buffer position.
             * ByteBuffer.array() is safe to use since all non-direct
             * ByteBuffers have a backing array.  Synchronization on the file
             * object is needed because two threads may call seek() on the same
             * file object.
             */
            synchronized (file) {
                assert readBuffer.hasArray();
                assert readBuffer.arrayOffset() == 0;

                int pos = readBuffer.position();
                int size = readBuffer.limit() - pos;
                file.seek(offset);
                int bytesRead = file.read(readBuffer.array(), pos, size);
                if (bytesRead > 0) {
                    readBuffer.position(pos + bytesRead);
                }
            }
        }
    }

    /*
     * Iterate through a buffer looking for commit records.  Change all commit
     * records to abort records.
     */
    private void abortCommittedTxns(ByteBuffer data)
        throws DatabaseException {

        final byte commitType = LogEntryType.LOG_TXN_COMMIT.getTypeNum();
        final byte abortType = LogEntryType.LOG_TXN_ABORT.getTypeNum();
        data.position(0);

        while (data.remaining() > 0) {
            int recStartPos = data.position();
            LogEntryHeader header =
                new LogEntryHeader(envImpl,
                                   data,
                                   false); // anticipateChecksumErrors

            if (header.getType() == commitType) {
                /* Change the log entry type, and recalculate the checksum. */
                header.convertCommitToAbort(data);
            }

            data.position(recStartPos + header.getSize() +
                          header.getItemSize());
        }
        data.position(0);
    }

    /**
     * FSync the end of the log.
     */
    void syncLogEnd()
        throws DatabaseException {

        try {
            endOfLog.force();
        } catch (IOException e) {
            throw new DatabaseException(e);
        }
    }

    /**
     * Sync the end of the log, close off this log file. Should only be called
     * under the log write latch.
     */
    void syncLogEndAndFinishFile()
        throws DatabaseException, IOException {
        
        if (syncAtFileEnd) {
            syncLogEnd();
        }
        endOfLog.close();
    }

    /**
     * Flush a file using the group sync mechanism, trying to amortize off
     * other syncs.
     */
    void groupSync()
        throws DatabaseException {

        syncManager.fsync();
    }

    /**
     * Close all file handles and empty the cache.
     */
    public void clear()
        throws IOException, DatabaseException {

        fileCacheLatch.acquire();
        try {
            fileCache.clear();
        } finally {
            fileCacheLatch.release();
        }

        endOfLog.close();
    }

    /**
     * Clear the file lock.
     */
    public void close()
        throws IOException, DatabaseException {

        if (envLock != null) {
            envLock.release();
        }

        if (exclLock != null) {
            exclLock.release();
        }

        if (channel != null) {
            channel.close();
        }

        if (lockFile != null) {
            lockFile.close();
            lockFile = null;
        }
    }

    /**
     * Lock the environment.  Return true if the lock was acquired.  If
     * exclusive is false, then this implements a single writer, multiple
     * reader lock.  If exclusive is true, then implement an exclusive lock.
     *
     * There is a lock file and there are two regions of the lock file: byte 0,
     * and byte 1.  Byte 0 is the exclusive writer process area of the lock
     * file.  If an environment is opened for write, then it attempts to take
     * an exclusive write lock on byte 0.  Byte 1 is the shared reader process
     * area of the lock file.  If an environment is opened for read-only, then
     * it attempts to take a shared lock on byte 1.  This is how we implement
     * single writer, multi reader semantics.
     *
     * The cleaner, each time it is invoked, attempts to take an exclusive lock
     * on byte 1.  The owning process already either has an exclusive lock on
     * byte 0, or a shared lock on byte 1.  This will necessarily conflict with
     * any shared locks on byte 1, even if it's in the same process and there
     * are no other holders of that shared lock.  So if there is only one
     * read-only process, it will have byte 1 for shared access, and the
     * cleaner can not run in it because it will attempt to get an exclusive
     * lock on byte 1 (which is already locked for shared access by itself).
     * If a write process comes along and tries to run the cleaner, it will
     * attempt to get an exclusive lock on byte 1.  If there are no other
     * reader processes (with shared locks on byte 1), and no other writers
     * (which are running cleaners on with exclusive locks on byte 1), then the
     * cleaner will run.
     */
    public boolean lockEnvironment(boolean readOnly, boolean exclusive)
        throws DatabaseException {

        try {
            if (checkEnvHomePermissions(readOnly)) {
                return true;
            }

            if (lockFile == null) {
                lockFile =
                    new RandomAccessFile(new File(dbEnvHome, LOCK_FILE),
                                 FileMode.READWRITE_MODE.getModeValue()); 

            }
            channel = lockFile.getChannel();

            boolean throwIt = false;
            try {
                if (exclusive) {

                    /*
                     * To lock exclusive, must have exclusive on 
                     * shared reader area (byte 1).
                     */
                    exclLock = channel.tryLock(1, 1, false);
                    if (exclLock == null) {
                        return false;
                    }
                    return true;
                } else {
                    if (readOnly) {
                        envLock = channel.tryLock(1, 1, true);
                    } else {
                        envLock = channel.tryLock(0, 1, false);
                    }
                    if (envLock == null) {
                        throwIt = true;
                    }
                }
            } catch (OverlappingFileLockException e) {
                throwIt = true;
            }
            if (throwIt) {
                throw new LogException
                    ("A " + LOCK_FILE + " file exists in " +
                     dbEnvHome.getAbsolutePath() +
                     " The environment can not be locked for " +
                     (readOnly ? "shared" : "single writer") + " access.");
            }
        } catch (IOException IOE) {
            throw new LogException(IOE.toString());
        }
        return true;
    }

    public void releaseExclusiveLock()
        throws DatabaseException {

        try {
            if (exclLock != null) {
                exclLock.release();
            }
        } catch (IOException IOE) {
            throw new DatabaseException(IOE);
        }
    }

    /**
     * Ensure that if the environment home dir is on readonly media or in a
     * readonly directory that the environment has been opened for readonly
     * access.
     *
     * @return true if the environment home dir is readonly.
     */
    public boolean checkEnvHomePermissions(boolean readOnly)
        throws DatabaseException {

        boolean envDirIsReadOnly = !dbEnvHome.canWrite();
        if (envDirIsReadOnly && !readOnly) {

            /* 
             * Use the absolute path in the exception message, to
             * make a mis-specified relative path problem more obvious.
             */
            throw new DatabaseException
                ("The Environment directory " +
                 dbEnvHome.getAbsolutePath() +
                 " is not writable, but the " +
                 "Environment was opened for read-write access.");
        }

        return envDirIsReadOnly;
    }

    /**
     * Truncate a log at this position. Used by recovery to a timestamp
     * utilities and by recovery to set the end-of-log position.
     *
     * <p>This method forces a new log file to be written next, if the last
     * file (the file truncated to) has an old version in its header.  This
     * ensures that when the log is opened by an old version of JE, a version
     * incompatibility will be detected.  [#11243]</p>
     */
    public void truncateLog(long fileNum, long offset)
        throws IOException, DatabaseException  {

        FileHandle handle = makeFileHandle(fileNum, FileMode.READWRITE_MODE);
        RandomAccessFile file = handle.getFile();

        try {
            file.getChannel().truncate(offset);
        } finally {
            file.close();
        }

        if (handle.isOldHeaderVersion()) {
            forceNewFile = true;
        }
    }

    /**
     * Set the flag that causes a new file to be written before the next write.
     */
    void forceNewLogFile() {
        forceNewFile = true;
    }

    /**
     * Return the offset of the first log entry after the file header.
     */

    /**
     * @return the size in bytes of the file header log entry.
     */
    public static int firstLogEntryOffset() {
        return FileHeader.entrySize() + LogEntryHeader.MIN_HEADER_SIZE;
    } 

    /**
     * Return the next available LSN in the log. Note that this is
     * unsynchronized, so is only valid as an approximation of log size.
     */
    public long getNextLsn() {
        return nextAvailableLsn;
    }

    /**
     * Return the last allocated LSN in the log. Note that this is
     * unsynchronized, so if it is called outside the log write latch it is
     * only valid as an approximation of log size.
     */
    public long getLastUsedLsn() {
        return lastUsedLsn;
    }

    /*
     * fsync stats.
     */
    public long getNFSyncs() {
        return syncManager.getNFSyncs();
    }

    public long getNFSyncRequests() {
        return syncManager.getNFSyncRequests();
    }

    public long getNFSyncTimeouts() {
        return syncManager.getNTimeouts();
    }

    void loadStats(StatsConfig config, EnvironmentStats stats) 
        throws DatabaseException {

        syncManager.loadStats(config, stats);
    }

    /*
     * Unit test support
     */

    /*
     * @return ids of files in cache
     */
    Set getCacheKeys() {
        return fileCache.getCacheKeys();
    }

    /**
     * Clear a file out of the file cache regardless of mode type.
     */
    private void clearFileCache(long fileNum)
        throws IOException, DatabaseException {

        fileCacheLatch.acquire();
        try {
            fileCache.remove(fileNum);
        } finally {
            fileCacheLatch.release();
        }
    }

    /*
     * The file cache keeps N RandomAccessFile objects cached for file
     * access. The cache consists of two parts: a Hashtable that doesn't
     * require extra synchronization, for the most common access, and a linked
     * list of files to support cache administration. Looking up a file from
     * the hash table doesn't require extra latching, but adding or deleting a
     * file does.
     */ 
    private static class FileCache {
        private Map fileMap;            // Long->file
        private LinkedList fileList;    // list of file numbers
        private int fileCacheSize;

        FileCache(DbConfigManager configManager)
            throws DatabaseException {

            /* 
             * A fileMap maps the file number to FileHandles (RandomAccessFile,
             * latch). The fileList is a list of Longs to determine which files
             * to eject out of the file cache if it's too small.
             */
            fileMap = new Hashtable();
            fileList = new LinkedList();
            fileCacheSize =
                configManager.getInt(EnvironmentParams.LOG_FILE_CACHE_SIZE);
        }

        private FileHandle get(Long fileId) {
            return (FileHandle) fileMap.get(fileId);
        }

        private void add(Long fileId, FileHandle fileHandle)
            throws DatabaseException {

            /* 
             * Does the cache have any room or do we have to evict?  Hunt down
             * the file list for an unused file. Note that the file cache might
             * actually grow past the prescribed size if there is nothing
             * evictable. Should we try to shrink the file cache? Presently if
             * it grows, it doesn't shrink.
             */
            if (fileList.size() >= fileCacheSize) {
                Iterator iter = fileList.iterator();
                while (iter.hasNext()) {
                    Long evictId = (Long) iter.next();
                    FileHandle evictTarget = (FileHandle) fileMap.get(evictId);

                    /* 
                     * Try to latch. If latchNoWait returns false, then another
                     * thread owns this latch. Note that a thread that's trying
                     * to get a new file handle should never already own the
                     * latch on another file handle, because these latches are
                     * meant to be short lived and only held over the i/o out
                     * of the file.
                     */
                    if (evictTarget.latchNoWait()) {
                        try {
                            fileMap.remove(evictId);
                            iter.remove();
                            evictTarget.close();
                        } catch (IOException e) {
                            throw new DatabaseException (e);
                        } finally {
                            evictTarget.release();
                        }
                        break;
                    } 
                }
            }

            /* 
             * We've done our best to evict. Add the file the the cache now
             * whether or not we did evict.
             */
            fileList.add(fileId);
            fileMap.put(fileId, fileHandle);
        }

        /**
         * Take any file handles corresponding to this file name out of the
         * cache. A file handle could be there twice, in rd only and in r/w
         * mode.
         */
        private void remove(long fileNum) 
            throws IOException, DatabaseException {

            Iterator iter = fileList.iterator();
            while (iter.hasNext()) {
                Long evictId = (Long) iter.next();
                if (evictId.longValue() == fileNum) {
                    FileHandle evictTarget = (FileHandle) fileMap.get(evictId);
                    try {
                        evictTarget.latch();
                        fileMap.remove(evictId);
                        iter.remove();
                        evictTarget.close();
                    } finally {
                        evictTarget.release();
                    }
                }
            }
        }

        private void clear()
            throws IOException, DatabaseException {

            Iterator iter = fileMap.values().iterator();
            while (iter.hasNext()) {
                FileHandle fileHandle = (FileHandle) iter.next();
                try {
                    fileHandle.latch();
                    fileHandle.close();
                    iter.remove();
                } finally {
                    fileHandle.release();
                }
            }
            fileMap.clear();
            fileList.clear();
        }

        private Set getCacheKeys() {
            return fileMap.keySet();
        }
    }

    /**
     * The LogEndFileDescriptor is used to write and fsync the end of the log.
     * Because the JE log is append only, there is only one logical R/W file
     * descriptor for the whole environment. This class actually implements two
     * RandomAccessFile instances, one for writing and one for fsyncing, so the
     * two types of operations don't block each other.
     * 
     * The write file descriptor is considered the master.  Manipulation of
     * this class is done under the log write latch. Here's an explanation of
     * why the log write latch is sufficient to safeguard all operations.
     *
     * There are two types of callers who may use this file descriptor: the
     * thread that is currently writing to the end of the log and any threads
     * that are fsyncing on behalf of the FSyncManager.
     *
     * The writing thread appends data to the file and fsyncs the file when we
     * flip over to a new log file.  The file is only instantiated at the point
     * that it must do so -- which is either when the first fsync is required
     * by JE or when the log file is full and we flip files.  Therefore, the
     * writing thread has two actions that change this descriptor -- we
     * initialize the file descriptor for the given log file at the first write
     * to the file, and we close the file descriptor when the log file is full.
     * Therefore is a period when there is no log descriptor -- when we have
     * not yet written a log buffer into a given log file.
     *
     * The fsyncing threads ask for the log end file descriptor asynchronously,
     * but will never modify it.  These threads may arrive at the point when
     * the file descriptor is null, and therefore skip their fysnc, but that is
     * fine because it means a writing thread already flipped that target file
     * and has moved on to the next file.
     *
     * Time     Activity
     * 10       thread 1 writes log entry A into file 0x0, issues fsync 
     *          outside of log write latch, yields the processor
     * 20       thread 2 writes log entry B, piggybacks off thread 1
     * 30       thread 3 writes log entry C, but no room left in that file,
     *          so it flips the log, and fsyncs file 0x0, all under the log
     *          write latch. It nulls out endOfLogRWFile, moves onto file 
     *          0x1, but doesn't create the file yet.
     * 40       thread 1 finally comes along, but endOfLogRWFile is null--
     *          no need to fsync in that case, 0x0 got fsynced.
     */
    class LogEndFileDescriptor {
        private RandomAccessFile endOfLogRWFile = null; 
        private RandomAccessFile endOfLogSyncFile = null; 
        private Object fsyncFileSynchronizer = new Object();

        /** 
         * getWritableFile must be called under the log write latch. 
         */
        RandomAccessFile getWritableFile(long fileNumber) 
            throws RunRecoveryException {

            try {

                if (endOfLogRWFile == null) {

                    /* 
                     * We need to make a file descriptor for the end of the
                     * log.  This is guaranteed to be called under the log
                     * write latch.
                     */
                    endOfLogRWFile =
                        makeFileHandle(fileNumber,
                                       FileMode.READWRITE_MODE).getFile();
                    synchronized (fsyncFileSynchronizer) {
                        endOfLogSyncFile =
                            makeFileHandle(fileNumber,
                                           FileMode.READWRITE_MODE).getFile();
                    }
                }
            
                return endOfLogRWFile;
            } catch (Exception e) {

                /* 
                 * If we can't get a write channel, we need to go into
                 * RunRecovery state.
                 */
                throw new RunRecoveryException(envImpl, e);
            }
        }

        /**
         * FSync the log file that makes up the end of the log.
         */
        void force()
            throws DatabaseException, IOException {

            /* 
             * Get a local copy of the end of the log file descriptor, it could
             * change. No need to latch, no harm done if we get an old file
             * descriptor, because we forcibly fsync under the log write latch
             * when we switch files.
             * 
             * If there is no current end file descriptor, we know that the log
             * file has flipped to a new file since the fsync was issued.
             */
            synchronized (fsyncFileSynchronizer) {
                RandomAccessFile file = endOfLogSyncFile;
                if (file != null) {
            
                    FileChannel channel = file.getChannel();
                    try {
                        channel.force(false);
                    } catch (ClosedChannelException e) {

                        /* 
                         * The channel should never be closed. It may be closed
                         * because of an interrupt received by another
                         * thread. See SR [#10463]
                         */
                        throw new RunRecoveryException
                            (envImpl,
                             "Channel closed, may be due to thread interrupt",
                             e);
                    } 
            
                    assert EnvironmentImpl.maybeForceYield();
                }
            }
        }

        /**
         * Close the end of the log file descriptor. Use atomic assignment to
         * ensure that we won't force and close on the same descriptor.
         */
        void close()
            throws IOException {

            IOException firstException = null;
            if (endOfLogRWFile != null) {
                RandomAccessFile file = endOfLogRWFile;

                /* 
                 * Null out so that other threads know endOfLogRWFile is no
                 * longer available.
                 */
                endOfLogRWFile = null;
                try {
                    file.close();
                } catch (IOException e) {
                    /* Save this exception, so we can try the second close. */
                    firstException = e;
                }
            }
            synchronized (fsyncFileSynchronizer) {
                if (endOfLogSyncFile != null) {
                    RandomAccessFile file = endOfLogSyncFile;

                    /* 
                     * Null out so that other threads know endOfLogSyncFile is
                     * no longer available.
                     */
                    endOfLogSyncFile = null;
                    file.close();
                }

                if (firstException != null) {
                    throw firstException;
                }
            }
        }
    }

    /*
     * Generate IOExceptions for testing.
     */

    /* Testing switch. */
    static boolean RUNRECOVERY_EXCEPTION_TESTING = false;
    /* Max write counter value. */
    private static final int RUNRECOVERY_EXCEPTION_MAX = 100;
    /* Current write counter value. */
    private int runRecoveryExceptionCounter = 0;
    /* Whether an exception has been thrown. */
    private boolean runRecoveryExceptionThrown = false;
    /* Random number generator. */
    private Random runRecoveryExceptionRandom = null;

    private void generateRunRecoveryException(RandomAccessFile file,
                                              ByteBuffer data,
                                              long destOffset)
        throws DatabaseException, IOException {

        if (runRecoveryExceptionThrown) {
            try {
                throw new Exception("Write after RunRecoveryException");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        runRecoveryExceptionCounter += 1;
        if (runRecoveryExceptionCounter >= RUNRECOVERY_EXCEPTION_MAX) {
            runRecoveryExceptionCounter = 0;
        }
        if (runRecoveryExceptionRandom == null) {
            runRecoveryExceptionRandom = new Random(System.currentTimeMillis());
        }
        if (runRecoveryExceptionCounter ==
            runRecoveryExceptionRandom.nextInt(RUNRECOVERY_EXCEPTION_MAX)) {
            int len = runRecoveryExceptionRandom.nextInt(data.remaining());
            if (len > 0) {
                byte[] a = new byte[len];
                data.get(a, 0, len);
                ByteBuffer buf = ByteBuffer.wrap(a);
                writeToFile(file, buf, destOffset);
            }
            runRecoveryExceptionThrown = true;
                throw new RunRecoveryException
                    (envImpl, "Randomly generated for testing");
        }
    }
}
