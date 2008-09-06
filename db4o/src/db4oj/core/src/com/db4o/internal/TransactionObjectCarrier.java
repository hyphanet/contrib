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

import com.db4o.internal.slots.*;


/**
 * TODO: Check if all time-consuming stuff is overridden! 
 */
class TransactionObjectCarrier extends LocalTransaction{
	
	TransactionObjectCarrier(ObjectContainerBase container, Transaction parentTransaction, TransactionalReferenceSystem referenceSystem) {
		super(container, parentTransaction, referenceSystem);
	}
	
	public void commit() {
		// do nothing
	}
	
    public void slotFreeOnCommit(int id, Slot slot) {
//      do nothing
    }
    
    public void slotFreeOnRollback(int id, Slot slot) {
//      do nothing
    }
    
    void produceUpdateSlotChange(int id, Slot slot) {
        setPointer(id, slot);
    }
    
    void slotFreeOnRollbackCommitSetPointer(int id, Slot slot, boolean forFreespace) {
        setPointer(id, slot);
    }
    
    void slotFreePointerOnCommit(int a_id, Slot slot) {
//      do nothing
    }
    
    public void slotFreePointerOnCommit(int a_id) {
    	// do nothing
    }
	
	public void setPointer(int a_id, Slot slot) {
		writePointer(a_id, slot);
	}
    
    boolean supportsVirtualFields(){
        return false;
    }
    
    
    

}
