/* Copyright (C) 2004 - 2008  db4objects Inc.  http://www.db4o.com

This file is part of the db4o open source object database.

db4o is free software; you can redistribute it and/or modify it under
the terms of version 2 of the GNU General Public License as published
by the Free Software Foundation and as clarified by db4objects' GPL 
interpretation policy, available at
http://www.db4o.com/about/company/legalpolicies/gplinterpretation/
Alternatively you can write to db4objects, Inc., 1900 S Norfolk Street,
Suite 350, San Mateo, CA 94403, USA.

db4o is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or
FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
for more details.

You should have received a copy of the GNU General Public License along
with this program; if not, write to the Free Software Foundation, Inc.,
59 Temple Place - Suite 330, Boston, MA  02111-1307, USA. */
package com.db4o.defragment;

import com.db4o.*;
import com.db4o.config.*;
import com.db4o.ext.*;
import com.db4o.internal.*;

/**
 * Configuration for a defragmentation run.
 * 
 * @see Defragment
 */
public class DefragmentConfig {
	
	public static final boolean DEBUG = false;
	
	public final static String BACKUP_SUFFIX="backup";
	
	private String _origPath;
	private String _backupPath;
	private String _tempPath;
	private ContextIDMapping _mapping;
	private Configuration _config;
	
	private StoredClassFilter _storedClassFilter=null;
	
	private boolean _forceBackupDelete=false;
	
	private boolean _readOnly = true;
	
	
	private int _objectCommitFrequency;

	/**
	 * Creates a configuration for a defragmentation run. The backup and mapping
	 * file paths are generated from the original path by appending the default
	 * suffixes. All properties other than the provided paths are set to FALSE
	 * by default.
	 * 
	 * @param origPath The path to the file to be defragmented. Must exist and must be
	 *         a valid yap file.
	 */
	public DefragmentConfig(String origPath) {
		this(origPath,origPath+"."+BACKUP_SUFFIX);
	}

	/**
	 * Creates a configuration for a defragmentation run with in-memory mapping.
	 * All properties other than the provided paths are set to FALSE by default.
	 * 
	 * @param origPath The path to the file to be defragmented. Must exist and must be
	 *         a valid yap file.
	 * @param backupPath The path to the backup of the original file. No file should
	 *         exist at this position, otherwise it will be OVERWRITTEN if forceBackupDelete()
	 *         is set to true!
	 */
	public DefragmentConfig(String origPath, String backupPath) {
		this(origPath,backupPath,new TreeIDMapping());
	}

	/**
	 * Creates a configuration for a defragmentation run. All properties other
	 * than the provided paths are set to FALSE by default.
	 * 
	 * @param origPath The path to the file to be defragmented. Must exist and must be
	 *         a valid yap file.
	 * @param backupPath The path to the backup of the original file. No file should
	 *         exist at this position, otherwise it will be OVERWRITTEN if forceBackupDelete()
	 *         is set to true!
	 * @param mapping The intermediate mapping used internally.
	 */
	public DefragmentConfig(String origPath, String backupPath,ContextIDMapping mapping) {
		_origPath = origPath;
		_backupPath = backupPath;
		_mapping = mapping;
	}

	/**
	 * @return The path to the file to be defragmented.
	 */
	public String origPath() {
		return _origPath;
	}

	/**
	 * @return The path to the backup of the original file.
	 */
	public String backupPath() {
		return _backupPath;
	}
	
	/**
	 * @return The intermediate mapping used internally. For internal use only.
	 */
	public ContextIDMapping mapping() {
		return _mapping;
	}
	
	/**
	 * @return The {@link StoredClassFilter} used to select stored class extents to
	 * be included into the defragmented file.
	 */
	public StoredClassFilter storedClassFilter() {
		return (_storedClassFilter==null ? NULLFILTER : _storedClassFilter);
	}
	
	/**
	 * @param storedClassFilter The {@link StoredClassFilter} used to select stored class extents to
	 * be included into the defragmented file.
	 */
	public void storedClassFilter(StoredClassFilter storedClassFilter) {
		_storedClassFilter=storedClassFilter;
	}

	/**
	 * @return true, if an existing backup file should be deleted, false otherwise.
	 */
	public boolean forceBackupDelete() {
		return _forceBackupDelete;
	}
	
	/**
	 * @param forceBackupDelete true, if an existing backup file should be deleted, false otherwise.
	 */
	public void forceBackupDelete(boolean forceBackupDelete) {
		_forceBackupDelete=forceBackupDelete;
	}
	
	/**
	 * allows turning on and off readonly mode.<br><br>
	 * When changed classes are likely to be detected defragment, it may be required 
	 * to open the original database in read/write mode. <br><br>
	 * Readonly mode is the default setting.
	 * @param flag false, to turn off readonly mode. 
	 */
	public void readOnly(boolean flag){
	    _readOnly = flag;
	}
	
	/**
	 * @return true, if the original database file is to be opened in readonly mode.
	 */
	public boolean readOnly(){
	    return _readOnly;
	}
	

	/**
	 * @return The db4o {@link com.db4o.config.Configuration Configuration} to be applied
	 * during the defragment process.
	 */
	public Configuration db4oConfig() {
		if(_config==null) {
			_config=vanillaDb4oConfig(1);
		}
		return _config;
	}
	
	/**
	 * @param config The db4o {@link com.db4o.config.Configuration Configuration} to be applied
	 * during the defragment process.
	 */
	public void db4oConfig(Configuration config) {
		_config=config;
	}
	
	public int objectCommitFrequency() {
		return _objectCommitFrequency;
	}

	/**
	 * @param objectCommitFrequency The number of processed object (slots) that should trigger an
	 * intermediate commit of the target file. Default: 0, meaning: never.
	 */
	public void objectCommitFrequency(int objectCommitFrequency) {
		_objectCommitFrequency=objectCommitFrequency;
	}

	/**
	 * Instruct the defragment process to upgrade the source file to the current db4o
	 * version prior to defragmenting it. Use this option if your source file has been created
	 * with an older db4o version than the one you are using.
	 * @param tempPath The location for an intermediate, upgraded version of the source file. 
	 */
	public void upgradeFile(String tempPath) {
		_tempPath=tempPath;
	}
	
	public boolean fileNeedsUpgrade() {
		return _tempPath!=null;
	}
	
	public String tempPath() {
		return (_tempPath!=null ? _tempPath : _backupPath);
	}
	
	public int blockSize() {
		return ((Config4Impl)db4oConfig()).blockSize();
	}
	
	protected static class NullFilter implements StoredClassFilter {
		public boolean accept(StoredClass storedClass) {
			return true;
		}
	}
	
	private final static StoredClassFilter NULLFILTER=new NullFilter();
	
	public static Configuration vanillaDb4oConfig(int blockSize){
		Configuration config = Db4o.newConfiguration();
		config.weakReferences(false);
		config.blockSize(blockSize);
		return config;
	}
	
	public Configuration clonedDb4oConfig() {
		return (Configuration) ((Config4Impl)db4oConfig()).deepClone(null);
	}
	
}
