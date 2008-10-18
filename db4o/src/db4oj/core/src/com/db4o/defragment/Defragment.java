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

import java.io.*;

import com.db4o.*;
import com.db4o.config.*;
import com.db4o.ext.*;
import com.db4o.foundation.*;
import com.db4o.foundation.io.*;
import com.db4o.internal.*;
import com.db4o.internal.btree.*;
import com.db4o.internal.classindex.*;
import com.db4o.internal.mapping.*;

/**
 * defragments database files.
 * 
 * <br><br>db4o structures storage inside database files as free and occupied slots, very
 * much like a file system - and just like a file system it can be fragmented.<br><br>
 * 
 * The simplest way to defragment a database file:<br><br>
 * 
 * <code>Defragment.defrag("sample.yap");</code><br><br>
 * 
 * This will move the file to "sample.yap.backup", then create a defragmented
 * version of this file in the original position, using a temporary file
 * "sample.yap.mapping". If the backup file already exists, this will throw an
 * exception and no action will be taken.<br><br>
 * 
 * For more detailed configuration of the defragmentation process, provide a
 * DefragmentConfig instance:<br><br>
 * 
 * <code>DefragmentConfig config=new DefragmentConfig("sample.yap","sample.bap",new BTreeIDMapping("sample.map"));<br>
 *	config.forceBackupDelete(true);<br>
 *	config.storedClassFilter(new AvailableClassFilter());<br>
 * config.db4oConfig(db4oConfig);<br>
 * Defragment.defrag(config);</code><br><br>
 * 
 * This will move the file to "sample.bap", then create a defragmented version
 * of this file in the original position, using a temporary file "sample.map" for BTree mapping.
 * If the backup file already exists, it will be deleted. The defragmentation
 * process will skip all classes that have instances stored within the yap file,
 * but that are not available on the class path (through the current
 * classloader). Custom db4o configuration options are read from the
 * {@link com.db4o.config.Configuration Configuration} passed as db4oConfig.
 * 
 * <strong>Note:</strong> For some specific, non-default configuration settings like
 * UUID generation, etc., you <strong>must</strong> pass an appropriate db4o configuration,
 * just like you'd use it within your application for normal database operation.
 */
public class Defragment {

	/**
	 * Renames the file at the given original path to a backup file and then
	 * builds a defragmented version of the file in the original place.
	 * 
	 * @param origPath
	 *            The path to the file to be defragmented.
	 * @throws IOException
	 *             if the original file cannot be moved to the backup location
	 */
	public static void defrag(String origPath) throws IOException {
		defrag(new DefragmentConfig(origPath), new NullListener());
	}

	/**
	 * Renames the file at the given original path to the given backup file and
	 * then builds a defragmented version of the file in the original place.
	 * 
	 * @param origPath
	 *            The path to the file to be defragmented.
	 * @param backupPath
	 *            The path to the backup file to be created.
	 * @throws IOException
	 *             if the original file cannot be moved to the backup location
	 */
	public static void defrag(String origPath, String backupPath)
			throws IOException {
		defrag(new DefragmentConfig(origPath, backupPath), new NullListener());
	}

	/**
	 * Renames the file at the configured original path to the configured backup
	 * path and then builds a defragmented version of the file in the original
	 * place.
	 * 
	 * @param config
	 *            The configuration for this defragmentation run.
	 * @throws IOException
	 *             if the original file cannot be moved to the backup location
	 */
	public static void defrag(DefragmentConfig config) throws IOException {
		defrag(config, new NullListener());
	}

	/**
	 * Renames the file at the configured original path to the configured backup
	 * path and then builds a defragmented version of the file in the original
	 * place.
	 * 
	 * @param config
	 *            The configuration for this defragmentation run.
	 * @param listener
	 *            A listener for status notifications during the defragmentation
	 *            process.
	 * @throws IOException
	 *             if the original file cannot be moved to the backup location
	 */
	public static void defrag(DefragmentConfig config, DefragmentListener listener) throws IOException {
		ensureFileExists(config.origPath());
		File backupFile = new File(config.backupPath());
		if (backupFile.exists()) {
			if (!config.forceBackupDelete()) {
				throw new IOException("Could not use '" + config.backupPath()
						+ "' as backup path - file exists.");
			}
			backupFile.delete();
		}
		File4.rename(config.origPath(), config.backupPath());
		
		if(config.fileNeedsUpgrade()) {
			upgradeFile(config);
		}
		
		DefragmentServicesImpl context = new DefragmentServicesImpl(config, listener);
		int newClassCollectionID = 0;
		int targetIdentityID = 0;
		int targetUuidIndexID = 0;
		try {
			firstPass(context, config);
			secondPass(context, config);
			defragUnindexed(context);
			newClassCollectionID = context.mappedID(context
					.sourceClassCollectionID());
			context.targetClassCollectionID(newClassCollectionID);
			int sourceIdentityID = context
					.databaseIdentityID(DefragmentServicesImpl.SOURCEDB);
			targetIdentityID = context.mappedID(sourceIdentityID,0);
			targetUuidIndexID = context
					.mappedID(context.sourceUuidIndexID(), 0);
		} catch (CorruptionException exc) {
			exc.printStackTrace();
		} finally {
			context.close();
		}
		if(targetIdentityID>0) {
			setIdentity(config, targetIdentityID, targetUuidIndexID);
		}
		else {
			listener.notifyDefragmentInfo(new DefragmentInfo("No database identity found in original file."));
		}
	}

	private static void ensureFileExists(String origPath) throws IOException {
		File file = new File(origPath);
		if(!file.exists() || file.length() == 0) {
			throw new IOException("Source database file '" + origPath
					+ "' does not exist or is empty.");			
		}
	}

	private static void upgradeFile(DefragmentConfig config) throws IOException {
		File4.copy(config.backupPath(),config.tempPath());
		Configuration db4oConfig=(Configuration)((Config4Impl)config.db4oConfig()).deepClone(null);
		db4oConfig.allowVersionUpdates(true);
		ObjectContainer db=Db4o.openFile(db4oConfig,config.tempPath());
		db.close();
	}

	private static void defragUnindexed(DefragmentServicesImpl services){
		IdSource unindexedIDs = services.unindexedIDs();
		while (unindexedIDs.hasMoreIds()) {
			final int origID = unindexedIDs.nextId();
			DefragmentContextImpl.processCopy(services, origID, new SlotCopyHandler() {
				public void processCopy(DefragmentContextImpl context){
					ClassMetadata.defragObject(context);
				}
			}, true);
		}
	}

	private static void setIdentity(DefragmentConfig config, int targetIdentityID,
			int targetUuidIndexID) {
		LocalObjectContainer targetDB = (LocalObjectContainer) Db4o.openFile(config.clonedDb4oConfig(), config.origPath());
		try {
			Db4oDatabase identity = (Db4oDatabase) targetDB
					.getByID(targetIdentityID);
			targetDB.setIdentity(identity);
			targetDB.systemData().uuidIndexId(targetUuidIndexID);
		} finally {
			targetDB.close();
		}
	}

	private static void firstPass(DefragmentServicesImpl context,
			DefragmentConfig config) throws CorruptionException, IOException {
		// System.out.println("FIRST");
		pass(context, config, new FirstPassCommand());
	}

	private static void secondPass(final DefragmentServicesImpl context,
			DefragmentConfig config) throws CorruptionException, IOException {
		// System.out.println("SECOND");
		pass(context, config, new SecondPassCommand(config.objectCommitFrequency()));
	}

	private static void pass(DefragmentServicesImpl context,
			DefragmentConfig config, PassCommand command)
			throws CorruptionException, IOException {
		command.processClassCollection(context);
		StoredClass[] classes = context
				.storedClasses(DefragmentServicesImpl.SOURCEDB);
		for (int classIdx = 0; classIdx < classes.length; classIdx++) {
			ClassMetadata yapClass = (ClassMetadata) classes[classIdx];
			if (!config.storedClassFilter().accept(yapClass)) {
				continue;
			}
			processYapClass(context, yapClass, command);
			command.flush(context);
			if(config.objectCommitFrequency()>0) {
				context.targetCommit();
			}
		}
		BTree uuidIndex = context.sourceUuidIndex();
		if (uuidIndex != null) {
			command.processBTree(context, uuidIndex);
		}
		command.flush(context);
		context.targetCommit();
	}

	// TODO order of class index/object slot processing is crucial:
	// - object slots before field indices (object slots register addresses for
	// use by string indices)
	// - class index before object slots, otherwise phantom btree entries from
	// deletions appear in the source class index?!?
	// reproducable with SelectiveCascadingDeleteTestCase and ObjectSetTestCase
	// - investigate.
	private static void processYapClass(final DefragmentServicesImpl context,
			final ClassMetadata curClass, final PassCommand command)
			throws CorruptionException, IOException {
		processClassIndex(context, curClass, command);
		if (!parentHasIndex(curClass)) {
			processObjectsForYapClass(context, curClass, command);
		}
		processYapClassAndFieldIndices(context, curClass, command);
	}

	private static boolean parentHasIndex(ClassMetadata curClass) {
		ClassMetadata parentClass = curClass.getAncestor();
		while (parentClass != null) {
			if (parentClass.hasClassIndex()) {
				return true;
			}
			parentClass = parentClass.getAncestor();
		}
		return false;
	}

	private static void processObjectsForYapClass(
			final DefragmentServicesImpl context, final ClassMetadata curClass,
			final PassCommand command) {
		context.traverseAll(curClass, new Visitor4() {
			public void visit(Object obj) {
				int id = ((Integer) obj).intValue();
				try {
					// FIXME bubble up exceptions
					command.processObjectSlot(context, curClass, id);
				} catch (CorruptionException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		});
	}

	private static void processYapClassAndFieldIndices(
			final DefragmentServicesImpl context, final ClassMetadata curClass,
			final PassCommand command) throws CorruptionException, IOException {
		int sourceClassIndexID = 0;
		int targetClassIndexID = 0;
		if (curClass.hasClassIndex()) {
			sourceClassIndexID = curClass.index().id();
			targetClassIndexID = context.mappedID(sourceClassIndexID, -1);
		}
		command.processClass(context, curClass, curClass.getID(),
				targetClassIndexID);
	}

	private static void processClassIndex(final DefragmentServicesImpl context,
			final ClassMetadata curClass, final PassCommand command)
			throws CorruptionException, IOException {
		if (curClass.hasClassIndex()) {
			BTreeClassIndexStrategy indexStrategy = (BTreeClassIndexStrategy) curClass
					.index();
			final BTree btree = indexStrategy.btree();
			command.processBTree(context, btree);
		}
	}

	static class NullListener implements DefragmentListener {
		public void notifyDefragmentInfo(DefragmentInfo info) {
		}
	}
}
