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

public class FlatteningIterator extends CompositeIterator4 {
	
	private static class IteratorStack {
		public final Iterator4 iterator;
		public final IteratorStack next;
		
		public IteratorStack(Iterator4 iterator_, IteratorStack next_) {
			iterator = iterator_;
			next = next_;
		}
	}
	
	private IteratorStack _stack;

	public FlatteningIterator(Iterator4 iterators) {
		super(iterators);
	}

	public boolean moveNext() {
		if (null == _currentIterator) {
			if (null == _stack) {
				_currentIterator = _iterators;
			} else {
				_currentIterator = pop();
			}
		}
		if (!_currentIterator.moveNext()) {
			if (_currentIterator == _iterators) {
				return false;
			}
			_currentIterator = null;
			return moveNext();
		}
		
		final Object current = _currentIterator.current();
		if (current instanceof Iterator4) {
			push(_currentIterator);
			_currentIterator = nextIterator(current);
			return moveNext();
		}
		return true;
	}

	private void push(Iterator4 currentIterator) {
		_stack = new IteratorStack(currentIterator, _stack);
	}

	private Iterator4 pop() {
		final Iterator4 iterator = _stack.iterator;
		_stack = _stack.next;
		return iterator;
	}

}
