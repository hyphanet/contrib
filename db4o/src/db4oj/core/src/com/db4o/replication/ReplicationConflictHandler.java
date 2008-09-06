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
package com.db4o.replication;

import com.db4o.*;

/**
 * will be called by a {@link com.db4o.replication.ReplicationProcess}upon
 * replication conflicts. Conflicts occur whenever
 * {@link ReplicationProcess#replicate(Object)}is called with an object that
 * was modified in both ObjectContainers since the last replication run between
 * the two.
 * @deprecated Since db4o-5.2. Use db4o Replication System (dRS)
 * instead.<br><br>
 */
public interface ReplicationConflictHandler {

	/**
	 * the callback method to be implemented to resolve a conflict. <br>
	 * <br>
	 * 
	 * @param replicationProcess
	 *            the {@link ReplicationProcess}for which this
	 *            ReplicationConflictHandler is registered
	 * @param a
	 *            the object modified in the peerA ObjectContainer
	 * @param b
	 *            the object modified in the peerB ObjectContainer
	 * @return the object (a or b) that should prevail in the conflict or null,
	 *         if no action is to be taken. If this would violate the direction
	 *         set with
	 *         {@link ReplicationProcess#setDirection(ObjectContainer, ObjectContainer)}
	 *         no action will be taken.
	 * @see ReplicationProcess#peerA()
	 * @see ReplicationProcess#peerB()
	 */
	public Object resolveConflict(ReplicationProcess replicationProcess,
			Object a, Object b);

}
