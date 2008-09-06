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
package com.db4o.foundation;

/**
 * @exclude
 */
public class SortedCollection4 {
	
	private final Comparison4 _comparison;
	private Tree _tree;

	public SortedCollection4(Comparison4 comparison) {
		if (null == comparison) {
			throw new ArgumentNullException();
		}
		_comparison = comparison;
		_tree = null;
	}
	
	public Object singleElement() {
		if (1 != size()) {
			throw new IllegalStateException();
		}
		return _tree.key();
	}
	
	public void addAll(Iterator4 iterator) {		
		while (iterator.moveNext()) {
			add(iterator.current());
		}		
	}

	public void add(Object element) {
		_tree = Tree.add(_tree, new TreeObject(element, _comparison));
	}	

	public void remove(Object element) {
		_tree = Tree.removeLike(_tree, new TreeObject(element, _comparison));
	}

	public Object[] toArray(final Object[] array) {
		Tree.traverse(_tree, new Visitor4() {
			int i = 0;
			public void visit(Object obj) {
				array[i++] = ((TreeObject)obj).key();
			}
		});
		return array;
	}
	
	public int size() {
		return Tree.size(_tree);
	}
	
}
