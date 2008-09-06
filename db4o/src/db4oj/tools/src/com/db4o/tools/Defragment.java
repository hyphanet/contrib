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
package com.db4o.tools;

import java.io.*;
import java.lang.reflect.*;

import com.db4o.*;
import com.db4o.ext.*;
import com.db4o.foundation.*;
import com.db4o.foundation.io.*;
import com.db4o.internal.*;
import com.db4o.types.*;

/**
 * old Deframent source code, now replaced by the built-in functionality in com.db4o.defragment.Defragment.
 * <br><br><b>This class is deprecated. Please use {@link com.db4o.defragment.Defragment}.</b><br>
 * This class is delivered as sourcecode in the
 * path ../com/db4o/tools/<br><br> <b>Prerequites:</b><br> - The database file may not be
 * in use.<br> - All stored classes need to be available.<br> - If you use yor own special
 * Db4o translators, they need to be installed before starting Defragment.<br><br>
 * <b>Performed tasks:</b><br> - Free filespace is removed.<br> - Deleted IDs are
 * removed.<br> - Unavailable classes are removed.<br> - Unavailable class members are
 * removed.<br> - Class indices are restored.<br> - Previous rename tasks are removed.<br>
 * <br>
 * <b>Backup:</b><br>
 * Defragment creates a backup file with the name [filename].bak. If
 * a file with this name is already present, Defragment will not run
 * for safety reasons.<br><br>
 * <b>Recommendations:</b><br>
 * - Keep the backup copy of your database file.<br>
 * - <b>Always</b> back up your class files with your database files also.<br>
 * You will need them to restore the full data of all objects from old database file versions.<br>
 * - Scan the output log for "Class not available" messages.<br><br>
 * You may also run this task programmatically on a scheduled basis.
 * In this case note that <code>Defragment</code> modifies db4o
 * configuration parameters. You may have to restore them for your
 * application. See the private methods Defragment#configureDb4o() and
 * Db4o#restoreConfiguration() in the sourcecode of
 * com.db4o.tools.Defragment.java for the exact changed parameters that
 * may need to be restored.
 * @deprecated Please use com.db4o.defragment.Defragment 
 */
public class Defragment {
    
    
    private static Hashtable4 _secondClassNames;
    
    
	/**
	 * the main method is the only entry point
	 */
	public Defragment() {
	}

	/**
	 * the main method that runs Defragment.
	 * @param args a String array of length 1, with the name of the database
	 * file as element 0.
	 */
	public static void main(String[] args) {
		Db4o.configure().messageLevel(-1);
		if (args != null && args.length > 0) {

			// This is a hidden feature: For fast debugging reasons, delete
			// can be forced by supplying an additional "!" parameter.
			boolean forceBackupDelete = (args.length > 1 && "!".equals(args[1]));
			
			new Defragment().run(args[0], forceBackupDelete);
			
		} else {
			System.out.println("Usage: java com.db4o.tools.Defragment <database filename>");
		}
	}
    
    /**
     * allows to specify a class to be treated as "second class".
     * Second class objects are not migrated to the new database on their own. A second
     * class objects is only migrated, if it is referenced by another object.
     * @param className the fully qualified classname, including the package name for Java, 
     * including the namespaces and assembly name for .NET. Format examples:<br>
     * Java: 'com.db4o.f1.Pilot'<br>
     * .NET: 'com.db4o.f1.Pilot, MyAssembly'   
     */
    public static void setSecondClass(String className){
        if(_secondClassNames == null){
            _secondClassNames = new Hashtable4();
        }
        _secondClassNames.put(className, className);
    }
	
	/**
	 * programmatic interface to run Defragment with a forced delete of a possible
	 * old Defragment backup.
	 * <br>This method is supplied for regression tests only. It is not recommended
	 * to be used by application programmers.
	 * @param filename the database file. 
	 * @param forceBackupDelete forces deleting an old backup. <b>Not recommended.</b>
	 */
	public void run(String filename, boolean forceBackupDelete) {
		File file = new File(filename);
		if (file.exists()) {
			boolean canRun = true;
			File backupTest = new File(file.getAbsolutePath() + ".bak");
			if (backupTest.exists()) {
				if (forceBackupDelete) {
					backupTest.delete();
				} else {
					canRun = false;
					System.out.println("A backup file with the name ");
					System.out.println("'" + backupTest.getAbsolutePath() + "'");
					System.out.println("already exists.");
					System.out.println("Remove this file before calling 'Defragment'.");
				}
			}
			if (canRun) {
				file.renameTo(backupTest);
				try {
					configureDb4o();
					ObjectContainer readFrom = Db4o.openFile(backupTest.getAbsolutePath());
					ObjectContainer writeTo = Db4o.openFile(file.getAbsolutePath());
					writeTo.ext().migrateFrom(readFrom);
					migrate(readFrom, writeTo);
					readFrom.close();
					writeTo.close();
					System.out.println("Defragment operation completed successfully.");
				} catch (Exception e) {
					System.out.println("Defragment operation failed.");
					e.printStackTrace();
					try {
						new File(filename).delete();
						File4.copy(backupTest.getAbsolutePath(), filename);
					} catch (Exception ex) {
						System.out.println("Restore failed.");
						System.out.println("Please use the backup file:");
						System.out.println("'" + backupTest.getAbsolutePath() + "'");
						return;
					}
					System.out.println("The original file was restored.");
					try {
						new File(backupTest.getAbsolutePath()).delete();
					} catch (Exception ex) {
					}
				} finally{
					restoreConfiguration();
				}
			}
		} else {
			System.out.println("File '" + file.getAbsolutePath() + "' does not exist.");
		}
	}

	private void configureDb4o() {
		Db4o.configure().activationDepth(0);
		Db4o.configure().callbacks(false);
		Db4o.configure().classActivationDepthConfigurable(false);
		Db4o.configure().weakReferences(false);
	}
	
	private void restoreConfiguration(){
		Db4o.configure().activationDepth(5);
		Db4o.configure().callbacks(true);
		Db4o.configure().classActivationDepthConfigurable(true);
		Db4o.configure().weakReferences(true);
	}

	private void migrate(ObjectContainer origin, ObjectContainer destination)
		throws ClassNotFoundException {

		// get all stored classes
		StoredClass[] classes = origin.ext().storedClasses();
		removeUnavailableSecondAndAbstractClasses(classes);
		removeSubclasses(classes);		
		migrateClasses(origin, destination, classes);
	}

	private void migrateClasses(ObjectContainer origin, ObjectContainer destination, StoredClass[] classes) {

        for (int i = 0; i < classes.length; i++) {
            if(migrateClass(origin, destination, classes[i], true)){
                classes[i] = null;
            }
		}
        if(_secondClassNames != null){
            for (int i = 0; i < classes.length; i++) {
                migrateClass(origin, destination, classes[i], false);
            }
        }
        
	}
    
    private boolean migrateClass(ObjectContainer origin, ObjectContainer destination, StoredClass clazz, boolean firstClassPass){
        if(clazz == null){
            return false;
        }
        if(firstClassPass){
            if(_secondClassNames != null){
                if(_secondClassNames.get(clazz.getName()) != null){
                    return false;
                }
            }
        }
            
        long[] ids = clazz.getIDs();
        origin.ext().purge();
        destination.commit();
        destination.ext().purge();
        for (int j = 0; j < ids.length; j++) {
            Object obj = origin.ext().getByID(ids[j]);
            
            if(firstClassPass || destination.ext().isStored(obj)){

                // prevent possible constructor side effects
                origin.activate(obj, 1);
                origin.deactivate(obj, 2);
    
                origin.activate(obj, 3);
                destination.store(obj);
    
                // Both Containers keep track of state individually,
                // so we need to make sure, both know, the object is deactivated
                origin.deactivate(obj, 1);
                destination.deactivate(obj, 1);
            }
        }
        return true;
    }

	private void removeSubclasses(StoredClass[] classes) throws ClassNotFoundException {
		// rule out inheritance dependancies
		for (int i = 0; i < classes.length; i++) {
			if (classes[i] != null) {
				Class javaClass = Class.forName(classes[i].getName());
				for (int j = 0; j < classes.length; j++) {
					if (classes[j] != null && classes[i] != classes[j]) {
						Class superClass = Class.forName(classes[j].getName());
						if (superClass.isAssignableFrom(javaClass)) {
							classes[i] = null;
							break;
						}
					}
				}
			}
		}
	}

	private void removeUnavailableSecondAndAbstractClasses(StoredClass[] classes) {
		// remove classes that are currently not available,
		// abstract classes and all second class objects
		for (int i = 0; i < classes.length; i++) {
			Class javaClass = ReflectPlatform.forName(classes[i].getName());
			if (javaClass == null
					|| SecondClass.class.isAssignableFrom(javaClass)
					|| Modifier.isAbstract(javaClass.getModifiers())) {
				classes[i] = null;
			}
		}
	}

}
