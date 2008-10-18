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
package com.db4o.ext;

/**
 * the internal representation of a stored class.
 */
public interface StoredClass {
    
	/**
	 * returns the name of this stored class.
	 */
	public String getName();
	
	
	/**
	 * returns an array of IDs of all stored object instances of this stored class.
	 */
	public long[] getIDs();
	
	/**
	 * returns the StoredClass for the parent of the class, this StoredClass represents.    
	 */
	public StoredClass getParentStoredClass();
	
	/**
	 * returns all stored fields of this stored class.
	 */
	public StoredField[] getStoredFields();
	
	/**
	 * returns true if this StoredClass has a class index.
	 */
	public boolean hasClassIndex();
	
	/**
	 * renames this stored class.
	 * <br><br>After renaming one or multiple classes the ObjectContainer has
	 * to be closed and reopened to allow internal caches to be refreshed.
	 * <br><br>.NET: As the name you should provide [Classname, Assemblyname]<br><br>
	 * @param name the new name
	 */
	public void rename(String name);
	
	// TODO: add field creation
	
	/**
	 * returns an existing stored field of this stored class. 
	 * @param name the name of the field
	 * @param type the type of the field. 
	 * There are four possibilities how to supply the type:<br>
     * - a Class object.  (.NET: a Type object)<br>
     * - a fully qualified classname.<br>
     * - any object to be used as a template.<br><br>
     * - null, if the first found field should be returned.
	 * @return the {@link StoredField}
	 */
	public StoredField storedField(String name, Object type);

	/**
	 * Returns the number of instances of this class that have been persisted to the
	 * database, as seen by the transaction (container) that produces this StoredClass
	 * instance.
	 * 
	 * @return The number of instances
	 */
	public int instanceCount();

}
