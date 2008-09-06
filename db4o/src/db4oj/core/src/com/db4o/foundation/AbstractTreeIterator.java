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
public abstract class AbstractTreeIterator implements Iterator4 {
	
	private final Tree	_tree;

	private Stack4 _stack;

	public AbstractTreeIterator(Tree tree) {
		_tree = tree;
	}

	public Object current() {
		if(_stack == null){
			throw new IllegalStateException();
		}
		Tree tree = peek();
		if(tree == null){
			return null;
		}
		return currentValue(tree);
	}
	
	private Tree peek(){
		return (Tree) _stack.peek();
	}

	public void reset() {
		_stack = null;
	}

	public boolean moveNext() {
		if(_stack == null){
			initStack();
			return _stack != null;
		}
		
		Tree current = peek();
		if(current == null){
			return false;
		}
		
		if(pushPreceding(current._subsequent)){
			return true;
		}
		
		while(true){
			_stack.pop();
			Tree parent = peek();
			if(parent == null){
				return false;
			}
			if(current == parent._preceding){
				return true;
			}
			current = parent;
		}
	}

	private void initStack() {
		if(_tree == null){
			return;
		}
		_stack = new Stack4();
		pushPreceding(_tree);
	}

	private boolean pushPreceding(Tree node) {
		if(node == null){
			return false;
		}
		while (node != null) {
			_stack.push(node);
			node = node._preceding;
		}
		return true;
	}

	protected abstract Object currentValue(Tree tree);
}
