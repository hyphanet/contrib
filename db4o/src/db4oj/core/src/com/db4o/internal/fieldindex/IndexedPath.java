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
package com.db4o.internal.fieldindex;

import com.db4o.foundation.*;
import com.db4o.internal.*;
import com.db4o.internal.query.processor.*;

public class IndexedPath extends IndexedNodeBase {
	
	public static IndexedNode newParentPath(IndexedNode next, QCon constraint) {
		if (!canFollowParent(constraint)) {
			return null;
		}
		return new IndexedPath((QConObject) constraint.parent(), next);
	}	
	
	private static boolean canFollowParent(QCon con) {
		final QCon parent = con.parent();
		final FieldMetadata parentField = getYapField(parent);
		if (null == parentField) return false;
		final FieldMetadata conField = getYapField(con);
		if (null == conField) return false;
		return parentField.hasIndex() &&
		    parentField.handlerClassMetadata(con.transaction().container()).isAssignableFrom(conField.containingClass());
	}
	
	private static FieldMetadata getYapField(QCon con) {
		QField field = con.getField();
		if (null == field) return null;
		return field.getYapField();
	}
	
	private IndexedNode _next;

	public IndexedPath(QConObject parent, IndexedNode next) {
		super(parent);
		_next = next;
	}
	
	public Iterator4 iterator() {		
		return new IndexedPathIterator(this, _next.iterator());
	}

	public int resultSize() {
		throw new NotSupportedException();
	}
}
