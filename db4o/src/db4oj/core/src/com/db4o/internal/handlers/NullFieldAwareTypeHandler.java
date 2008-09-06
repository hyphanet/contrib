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
package com.db4o.internal.handlers;

import com.db4o.ext.*;
import com.db4o.foundation.*;
import com.db4o.internal.*;
import com.db4o.internal.activation.*;
import com.db4o.internal.delete.*;
import com.db4o.internal.marshall.*;
import com.db4o.internal.slots.*;
import com.db4o.marshall.*;
import com.db4o.typehandlers.*;

/**
 * @exclude
 */
public class NullFieldAwareTypeHandler implements FieldAwareTypeHandler{

	public static final FieldAwareTypeHandler INSTANCE = new NullFieldAwareTypeHandler();

	public void addFieldIndices(ObjectIdContextImpl context, Slot oldSlot) {
	}

	public void classMetadata(ClassMetadata classMetadata) {
	}

	public void collectIDs(CollectIdContext context, String fieldName) {
	}

	public void deleteMembers(DeleteContextImpl deleteContext, boolean isUpdate) {
	}

	public void readVirtualAttributes(ObjectReferenceContext context) {
	}

	public boolean seekToField(ObjectHeaderContext context, FieldMetadata field) {
		return false;
	}

	public void defragment(DefragmentContext context) {
	}

	public void delete(DeleteContext context) throws Db4oIOException {
	}

	public Object read(ReadContext context) {
		return null;
	}

	public void write(WriteContext context, Object obj) {
	}

	public PreparedComparison prepareComparison(Context context, Object obj) {
		return null;
	}

	public TypeHandler4 unversionedTemplate() {
		return null;
	}

	public Object deepClone(Object context) {
		return null;
	}

	public void cascadeActivation(ActivationContext4 context) {
		
	}

	public void collectIDs(QueryingReadContext context) {
		
	}

	public TypeHandler4 readCandidateHandler(QueryingReadContext context) {
		return null;
	}

}
