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
package com.db4o.query;

/**
 * for implementation of callback evaluations.
 * <br><br>
 * To constrain a {@link Query} node with your own callback
 * <code>Evaluation</code>, construct an object that implements the
 * <code>Evaluation</code> interface and register it by passing it
 * to {@link Query#constrain(Object)}.
 * <br><br>
 * Evaluations are called as the last step during query execution,
 * after all other constraints have been applied. Evaluations in higher
 * level {@link Query} nodes in the query graph are called first.
 * <br><br>Java client/server only:<br>
 * db4o first attempts to use Java Serialization to allow to pass final
 * variables to the server. Please make sure that all variables that are
 * used within the evaluate() method are Serializable. This may include
 * the class an anonymous Evaluation object is created in. If db4o is
 * not successful at using Serialization, the Evaluation is transported
 * to the server in a db4o MemoryFile. In this case final variables can
 * not be restored. 
 */
public interface Evaluation extends java.io.Serializable {
	
	/**
	 * callback method during {@link Query#execute() query execution}.
	 * @param candidate reference to the candidate persistent object.
	 */
	public void evaluate(Candidate candidate);
	
}
