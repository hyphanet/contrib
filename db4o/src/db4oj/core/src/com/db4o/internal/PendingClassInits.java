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


class PendingClassInits {
	
    private final Transaction _systemTransaction;
	
	private Collection4 _pending = new Collection4();

	private Queue4 _members = new NonblockingQueue();
	private Queue4 _statics = new NonblockingQueue();
    private Queue4 _writes = new NonblockingQueue();
    private Queue4 _inits = new NonblockingQueue();
	
	private boolean _running = false;
	
	PendingClassInits(Transaction systemTransaction){
        _systemTransaction = systemTransaction;
	}
	
	void process(ClassMetadata newYapClass) {
		
		if(_pending.contains(newYapClass)) {
			return;
		}
		
        ClassMetadata ancestor = newYapClass.getAncestor();
        if (ancestor != null) {
            process(ancestor);
        }
		
		_pending.add(newYapClass);
        
        _members.add(newYapClass);
        
		
		if(_running) {
			return;
		}
		
		_running = true;
		
		checkInits();
		
		_pending = new Collection4();
		
		_running = false;
	}

	
	private void checkMembers() {
		while(_members.hasNext()) {
			ClassMetadata classMetadata = (ClassMetadata)_members.next();
			classMetadata.addMembers(stream());
            _statics.add(classMetadata);
		}
	}

    private ObjectContainerBase stream() {
        return _systemTransaction.container();
    }
	
	private void checkStatics() {
		checkMembers();
		while(_statics.hasNext()) {
			ClassMetadata yc = (ClassMetadata)_statics.next();
			yc.storeStaticFieldValues(_systemTransaction, true);
			_writes.add(yc);
			checkMembers();
		}
	}
	
	private void checkWrites() {
		checkStatics();
		while(_writes.hasNext()) {
			ClassMetadata yc = (ClassMetadata)_writes.next();
	        yc.setStateDirty();
	        yc.write(_systemTransaction);
            _inits.add(yc);
			checkStatics();
		}
	}
    
    private void checkInits() {
        checkWrites();
        while(_inits.hasNext()) {
            ClassMetadata yc = (ClassMetadata)_inits.next();
            yc.initConfigOnUp(_systemTransaction);
            checkWrites();
        }
    }


}
