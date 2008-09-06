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
class QPending extends Tree{
	
	final QConJoin			_join;
	QCon 					_constraint;
	
	int 					_result;

	// Constants, so QConJoin.evaluatePending is made easy:
	static final int FALSE = -4;
	static final int BOTH = 1;
	static final int TRUE = 2;
	
	QPending(QConJoin a_join, QCon a_constraint, boolean a_firstResult){
		_join = a_join;
		_constraint = a_constraint;
		
		_result = a_firstResult ? TRUE : FALSE;
	}
	
	public int compare(Tree a_to) {
		return _constraint.i_id - ((QPending)a_to)._constraint.i_id;
	}

	void changeConstraint(){
		_constraint = _join.getOtherConstraint(_constraint);
	}

	public Object shallowClone() {
		QPending pending = internalClonePayload();
		super.shallowCloneInternal(pending);
		return pending;
	}

	QPending internalClonePayload() {
		QPending pending = new QPending(_join, _constraint, false);
		pending._result=_result;
		return pending;
	}
	
    public Object key(){
    	throw new NotImplementedException();
    }

}

