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

/**
 * The ID mapping used internally during a defragmentation run.
 * 
 * @see Defragment
 */
public interface ContextIDMapping {

	/**
	 * Returns a previously registered mapping ID for the given ID if it exists.
	 * If lenient mode is set to true, will provide the mapping ID for the next
	 * smaller original ID a mapping exists for. Otherwise returns 0.
	 * 
	 * @param origID The original ID
	 * @param lenient If true, lenient mode will be used for lookup, strict mode otherwise.
	 * @return The mapping ID for the given original ID or 0, if none has been registered.
	 */
	int mappedID(int origID, boolean lenient);

	/**
	 * Registers a mapping for the given IDs.
	 * 
	 * @param origID The original ID
	 * @param mappedID The ID to be mapped to the original ID.
	 * @param isClassID true if the given original ID specifies a class slot, false otherwise.
	 */
	void mapIDs(int origID, int mappedID, boolean isClassID);

	/**
	 * Prepares the mapping for use.
	 */
	void open();	
	
	/**
	 * Shuts down the mapping after use.
	 */
	void close();
}