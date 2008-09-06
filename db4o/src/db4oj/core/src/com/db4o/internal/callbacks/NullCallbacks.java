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
package com.db4o.internal.callbacks;

import com.db4o.*;
import com.db4o.internal.*;
import com.db4o.query.*;

public class NullCallbacks implements Callbacks {

	public void queryOnFinished(Transaction transaction, Query query) {
	}

	public void queryOnStarted(Transaction transaction, Query query) {
	}

	public boolean objectCanNew(Transaction transaction, Object obj) {
		return true;
	}

	public boolean objectCanActivate(Transaction transaction, Object obj) {
		return true;
	}
	
	public boolean objectCanUpdate(Transaction transaction, Object obj) {
		return true;
	}
	
	public boolean objectCanDelete(Transaction transaction, Object obj) {
		return true;
	}
	
	public boolean objectCanDeactivate(Transaction transaction, Object obj) {
		return true;
	}
	
	public void objectOnNew(Transaction transaction, Object obj) {
	}
	
	public void objectOnActivate(Transaction transaction, Object obj) {
	}

	public void objectOnUpdate(Transaction transaction, Object obj) {
	}

	public void objectOnDelete(Transaction transaction, Object obj) {
	}

	public void objectOnDeactivate(Transaction transaction, Object obj) {	
	}

	public void objectOnInstantiate(Transaction transaction, Object obj) {
	}

	public void commitOnStarted(Transaction transaction, CallbackObjectInfoCollections objectInfoCollections) {
	}
	
	public void commitOnCompleted(Transaction transaction, CallbackObjectInfoCollections objectInfoCollections) {
	}

	public boolean caresAboutCommitting() {
		return false;
	}

	public boolean caresAboutCommitted() {
		return false;
	}

	public void classOnRegistered(ClassMetadata clazz) {
	}

    public boolean caresAboutDeleting() {
        return false;
    }

    public boolean caresAboutDeleted() {
        return false;
    }

	public void closeOnStarted(ObjectContainer container) {
	}
}
