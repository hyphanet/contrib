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
package com.db4o.internal.query.processor;

import com.db4o.foundation.*;


/**
 * @exclude
 */
class QOrder extends Tree{
	
	static int equalityIDGenerator = 1;
	
	final QConObject _constraint;
	final QCandidate _candidate;
	
	private int _equalityID;
	
	QOrder(QConObject a_constraint, QCandidate a_candidate){
		_constraint = a_constraint;
		_candidate = a_candidate;
	}
	
	public boolean isEqual(QOrder other){
		if(other == null){
			return false;
		}
		return _equalityID != 0 && _equalityID == other._equalityID;
	}

	public int compare(Tree a_to) {
		int res = internalCompare();
		if(res != 0){
			return res;
		}
		QOrder other = (QOrder) a_to;
		int equalityID = _equalityID; 
		if(equalityID == 0){
			if(other._equalityID != 0){
				equalityID = other._equalityID; 
			}
		}
		if(equalityID == 0){
			equalityID = generateEqualityID();
		}
		_equalityID = equalityID;
		other._equalityID = equalityID;
		return res;
	}
	
	private int internalCompare(){
	    int comparisonResult = _constraint._preparedComparison.compareTo(_candidate.value());
	    if(comparisonResult > 0){
	        return - _constraint.ordering();
	    }
	    if(comparisonResult == 0){
	        return 0;
	    }
		return _constraint.ordering();	
	}

	public Object shallowClone() {
		QOrder order= new QOrder(_constraint,_candidate);
		super.shallowCloneInternal(order);
		return order;
	}
	
    public Object key(){
    	throw new NotImplementedException();
    }
    
    private static int generateEqualityID(){
    	equalityIDGenerator++;
    	if(equalityIDGenerator < 1){
    		equalityIDGenerator = 1;
    	}
    	return equalityIDGenerator;
    }

}

