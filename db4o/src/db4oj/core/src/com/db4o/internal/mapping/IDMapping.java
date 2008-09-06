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
package com.db4o.internal.mapping;

/**
 * A mapping from yap file source IDs/addresses to target IDs/addresses, used for defragmenting.
 * 
 * @exclude
 */
public interface IDMapping {
	/**
	 * @return a mapping for the given id. if it does refer to a system handler or the empty reference (0), returns the given id.
	 * @throws MappingNotFoundException if the given id does not refer to a system handler or the empty reference (0) and if no mapping is found
	 */
	int mappedID(int oldID) throws MappingNotFoundException;
	void mapIDs(int oldID,int newID, boolean isClassID);
}
