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
 * interface to the internal reference that an ObjectContainer
 * holds for a stored object.
 */
public interface ObjectInfo {
    
	/**
	 * returns the internal db4o ID.
	 */
	public long getInternalID();

    /**
     * returns the object that is referenced.
     * <br><br>This method may return null, if the object has
     * been garbage collected.
     * @return the referenced object or null, if the object has
     * been garbage collected.
     */
    public Object getObject();
    
    /**
     * returns a UUID representation of the referenced object.
	 * UUID generation has to be turned on, in order to be able
	 * to use this feature:
	 * {@link com.db4o.config.Configuration#generateUUIDs(int)}
     * @return the UUID of the referenced object.
     */
    public Db4oUUID getUUID();
	
	/**
	 * returns the transaction serial number ("version") the 
	 * referenced object was stored with last.
	 * Version number generation has to be turned on, in order to
	 * be able to use this feature: 
	 * {@link com.db4o.config.Configuration#generateVersionNumbers(int)}
	 * @return the version number.
	 */
	public long getVersion();
	
}
