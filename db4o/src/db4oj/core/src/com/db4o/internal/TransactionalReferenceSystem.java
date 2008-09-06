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
package com.db4o.internal;

import com.db4o.foundation.*;


/**
 * @exclude
 */
public class TransactionalReferenceSystem implements ReferenceSystem{
	
	private final ReferenceSystem _committedReferences;
	
	private ReferenceSystem _newReferences;
	
	public TransactionalReferenceSystem() {
		createNewReferences();
		_committedReferences = newReferenceSystem();
	}
	
	private ReferenceSystem newReferenceSystem(){
	    return new HashcodeReferenceSystem();
	    
	    // An alternative reference system using a hashtable: 
	    // return new HashtableReferenceSystem();
	}

	public void addExistingReference(ObjectReference ref) {
		_committedReferences.addExistingReference(ref);
	}

	public void addNewReference(ObjectReference ref) {
		_newReferences.addNewReference(ref);
	}
	
	public void commit(){
		traveseNewReferences(new Visitor4() {
			public void visit(Object obj) {
				ObjectReference oref = (ObjectReference)obj;
				Object referent = oref.getObject();
				if(referent != null){
					_committedReferences.addExistingReference(oref);
				}
			}
		});
		createNewReferences();
	}

	public void traveseNewReferences(final Visitor4 visitor) {
		_newReferences.traverseReferences(visitor);
	}
	
	private void createNewReferences(){
		_newReferences = newReferenceSystem();
	}

	public ObjectReference referenceForId(int id) {
		ObjectReference ref = _newReferences.referenceForId(id);
		if(ref != null){
			return ref;
		}
		return _committedReferences.referenceForId(id);
	}

	public ObjectReference referenceForObject(Object obj) {
		ObjectReference ref = _newReferences.referenceForObject(obj);
		if(ref != null){
			return ref;
		}
		return _committedReferences.referenceForObject(obj);
	}

	public void removeReference(ObjectReference ref) {
		_newReferences.removeReference(ref);
		_committedReferences.removeReference(ref);
	}
	
	public void rollback(){
		createNewReferences();
	}
	
	public void traverseReferences(Visitor4 visitor) {
		traveseNewReferences(visitor);
		_committedReferences.traverseReferences(visitor);
	}

}
