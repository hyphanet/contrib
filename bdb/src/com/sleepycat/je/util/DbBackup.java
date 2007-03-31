/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002,2007 Oracle.  All rights reserved.
 *
 * $Id: DbBackup.java,v 1.10.2.1 2007/02/01 14:49:53 cwl Exp $
 */

package com.sleepycat.je.util;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.Environment;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.log.FileManager;
import com.sleepycat.je.utilint.DbLsn;


/**
 * DbBackup is a helper class for stopping and restarting JE background
 * activity in an open environment in order to simplify backup operations. It
 * also lets the application create a backup which can support restoring the
 * environment to a specific point in time.
 * <p>
 * <b>Backing up without DbBackup:</b>
 * Because JE has an append only log file architecture, it is always possible
 * to do a hot backup without the use of DbBackup by copying all log files
 * (.jdb files) to your archival location. As long as the log files are copied
 * in alphabetical order, (numerical in effect) <i>and</i> all log files are
 * copied, the environment can be successfully backed up without any need to
 * stop database operations or background activity. This means that your
 * backup operation must do a loop to check for the creation of new log files
 * before deciding that the backup is finished. For example:
 * <pre>
 * time    files in                    activity
 *         environment    
 *
 *  t0     000000001.jdb     Backup starts copying file 1
 *         000000003.jdb     
 *         000000004.jdb
 *
 *  t1     000000001.jdb     JE log cleaner migrates portion of file 3 to newly
 *         000000004.jdb     created file 5 and deletes file 3. Backup finishes
 *         000000005.jdb     file 1, starts copying file 4. Backup MUST include
 *                           file 5 for a consistent backup!
 *
 *  t2     000000001.jdb     Backup finishes copying file 4, starts and finishes
 *         000000004.jdb     file 5, has caught up. Backup ends.
 *         000000005.jdb                 
 *</pre>
 * <p>
 * In the example above, the backup operation must be sure to copy file 5,
 * which came into existence after the backup had started. If the backup 
 * stopped operations at file 4, the backup set would include only file 1 and
 * 4, omitting file 3, which would be an inconsistent set.
 * <p>
 * Also note that log file 5 may not have filled up before it was copied to
 * archival storage. On the next backup, there might be a newer, larger version
 * of file 5, and that newer version should replace the older file 5 in archive
 * storage.
 * <p>
 * <b>Backup up with DbBackup</b>
 * <p>
 * DbBackup helps simplify application backup by defining the set of files that
 * must be copied for each backup operation. If the environment directory has
 * read/write protection, the application must pass DbBackup an open, 
 * read/write environment handle.
 * <p>
 * When entering backup mode, JE
 * determines the set of log files needed for a consistent backup, and freezes
 * all changes to those files. The application can copy that defined set of
 * files and finish operation without checking for the ongoing creation of new
 * files. Also, there will be no need to check for a newer version of the last
 * file on the next backup. 
 * <p>
 * In the example above, if DbBackupHelper was used at t0, the application
 * would only have to copy files 1, 3 and 4 to back up. On a subsequent backup,
 * the application could start its copying at file 5. There would be no need
 * to check for a newer version of file 4.
 * <p>
 * An example usage:
 * <pre>
 *
 *    Environment env = new Environment(...);
 *    DbBackup backupHelper = new DbBackup(env);
 *
 *    // Find the file number of the last file in the previous backup
 *    // persistently, by either checking the backup archive, or saving
 *    // state in a persistent file.
 *    long lastFileCopiedInPrevBackup =  ...
 *
 *    // Start backup, find out what needs to be copied. 
 *    backupHelper.startBackup();
 *    try {
 *        String[] filesForBackup =
 *             backupHelper.getLogFilesInBackupSet(lastFileCopiedInPrevBackup);
 *
 *        // Copy the files to archival storage.
 *        myApplicationCopyMethod(filesForBackup) 

 *        // Update our knowlege of the last file saved in the backup set,
 *        // so we can copy less on the next backup
 *        lastFileCopiedInPrevBackup = backupHelper.getLastFileInBackupSet();
 *        myApplicationSaveLastFile(lastFileCopiedInBackupSet);
 *    } finally {
 *        // Remember to exit backup mode, or all log files won't be cleaned
 *        // and disk usage will bloat.
 *       backupHelper.endBackup();
 *   }
 */
public class DbBackup {

    private EnvironmentImpl envImpl;
    private boolean backupStarted;
    private long lastFileInBackupSet = -1;
    private boolean envIsReadOnly;

    /**
     * DbBackup must be created with an open, valid environment handle.
     * If the environment directory has read/write permissions, the environment
     * handle must be configured for read/write.
     */
    public DbBackup(Environment env) 
        throws DatabaseException {

        /* Check that the Environment is open. */
        env.checkHandleIsValid();
        envImpl = DbInternal.envGetEnvironmentImpl(env);
        FileManager fileManager = envImpl.getFileManager();

        /*
         * If the environment is writable, we need a r/w environment handle
         * in order to flip the file.
         */
        envIsReadOnly = fileManager.checkEnvHomePermissions(true);
        if ((!envIsReadOnly) && envImpl.isReadOnly()) {
            throw new DatabaseException(this.getClass().getName() +
                                " requires a read/write Environment handle");
        }
    }

    /**
     * Start backup mode in order to determine the definitive backup set needed
     * for this point in time. After calling this method, log cleaning will be
     * disabled until endBackup() is called. Be sure to call endBackup() to
     * re-enable log cleaning or disk space usage will bloat.
     */
    public synchronized void startBackup() 
        throws DatabaseException {
	
        if (backupStarted) {
            throw new DatabaseException(this.getClass().getName() +
                                         ".startBackup was already called");
        }

        backupStarted = true;

        try {
            /* Prevent any file deletions. */
            envImpl.getCleaner().setDeleteProhibited();

            FileManager fileManager = envImpl.getFileManager();
        
            /* 
             * Flip the log so that we can know that the list of files
             * corresponds to a given point.
             */
            if (envIsReadOnly) {
                lastFileInBackupSet = fileManager.getLastFileNum().longValue();
            } else {
                long newFileNum =  envImpl.forceLogFileFlip();
                lastFileInBackupSet = DbLsn.getFileNumber(newFileNum) - 1;
            }
        } catch (DatabaseException e) {
            backupStarted = false;
            throw e;
        }
    }

    /**
     * End backup mode, thereby re-enabling normal JE log cleaning.
     */
    public synchronized void endBackup() 
        throws DatabaseException {
	
        checkBackupStarted();
        
        try {
            envImpl.getCleaner().clearDeleteProhibited();
        } finally {
            backupStarted = false;
        }
    }

    /**
     * Can only be called in backup mode, after startBackup() has been called.
     *
     * @return the file number of the last file in the current backup set.
     * Save this value to reduce the number of files that must be copied at
     * the next backup session.
     */
    public synchronized long getLastFileInBackupSet() 
        throws DatabaseException {
	
        checkBackupStarted();
        return lastFileInBackupSet;
    }

    /**
     * Get the list of all files that are needed for the environment at the
     * point of time when backup mode started.  Can only be called in backup
     * mode, after startBackup() has been called.
     *
     * @return the names of all files in the backup set, sorted in alphabetical
     * order.
     */
    public synchronized String[] getLogFilesInBackupSet()
        throws DatabaseException {

        checkBackupStarted();
        return envImpl.getFileManager().listFiles(0, lastFileInBackupSet);
    }

    /**
     * Get the minimum list of files that must be copied for this backup. This
     * consists of the set of backup files that are greater than the last file
     * copied in the previous backup session.  Can only be called in backup
     * mode, after startBackup() has been called.
     *
     * @param lastFileCopiedInPrevBackup file number of last file copied in the 
     * last backup session, obtained from getLastFileInBackupSet(). 
     *
     * @return the names of all the files in the backup set that come after
     * lastFileCopiedInPrevBackup.
     */
    public synchronized
        String[] getLogFilesInBackupSet(long lastFileCopiedInPrevBackup)
        throws DatabaseException {
	
        checkBackupStarted();
        FileManager fileManager = envImpl.getFileManager();
        return fileManager.listFiles(lastFileCopiedInPrevBackup + 1,
                                      lastFileInBackupSet);
    }

    private void checkBackupStarted() 
        throws DatabaseException {

        if (!backupStarted) {
            throw new DatabaseException( this.getClass().getName() +
                                         ".startBackup was not called");
        }
    }
}
