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
package com.db4o.internal.slots;

import com.db4o.foundation.*;
import com.db4o.internal.*;

/**
 * @exclude
 */
public class ReferencedSlot extends TreeInt {

	private Slot _slot;

	private int _references;

	public ReferencedSlot(int a_key) {
		super(a_key);
	}

	public Object shallowClone() {
		ReferencedSlot rs = new ReferencedSlot(_key);
		rs._slot = _slot;
		rs._references = _references;
		return super.shallowCloneInternal(rs);
	}

	public void pointTo(Slot slot) {
		_slot = slot;
	}

	public Tree free(LocalObjectContainer file, Tree treeRoot, Slot slot) {
		file.free(_slot.address(), _slot.length());
		if (removeReferenceIsLast()) {
			if(treeRoot != null){
				return treeRoot.removeNode(this);
			}
		}
		pointTo(slot);
		return treeRoot;
	}

	public boolean addReferenceIsFirst() {
		_references++;
		return (_references == 1);
	}

	public boolean removeReferenceIsLast() {
		_references--;
		return _references < 1;
	}

    public Slot slot() {
        return _slot;
    }

}
