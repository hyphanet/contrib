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
public class NonblockingQueue implements Queue4 {

	private List4 _insertionPoint;
	private List4 _next;
	
    /* (non-Javadoc)
	 * @see com.db4o.foundation.Queue4#add(java.lang.Object)
	 */
    public final void add(Object obj) {
    	List4 newNode = new List4(null, obj);
    	if (_insertionPoint == null) {
    		_next = newNode;
    	} else {
    		_insertionPoint._next = newNode;
    	}
    	_insertionPoint = newNode;
    }
    
	/* (non-Javadoc)
	 * @see com.db4o.foundation.Queue4#next()
	 */
	public final Object next() {
		if(_next == null){
			return null;
		}
		Object ret = _next._element;
		removeNext();
		return ret;
	}

	private void removeNext() {
		_next = _next._next;
		if (_next == null) {
			_insertionPoint = null;
		}
	}
	
	public Object nextMatching(Predicate4 condition) {
		if (null == condition) {
			throw new ArgumentNullException();
		}
		
		List4 current = _next;
		List4 previous = null;
		while (null != current) {
			final Object element = current._element;
			if (condition.match(element)) {
				if (previous == null) {
					removeNext();
				} else {
					previous._next = current._next;
				}
				return element;
			}
			previous = current;
			current = current._next;
		}
		return null;
	}
    
    /* (non-Javadoc)
	 * @see com.db4o.foundation.Queue4#hasNext()
	 */
    public final boolean hasNext() {
        return _next != null;
    }

	/* (non-Javadoc)
	 * @see com.db4o.foundation.Queue4#iterator()
	 */
	public Iterator4 iterator() {
		final List4 origInsertionPoint = _insertionPoint;
		final List4 origNext = _next;
		return new Iterator4Impl(_next) {
			
			public boolean moveNext() {
				if (queueWasModified()) {
					throw new IllegalStateException();
				}
				return super.moveNext();
			}

			private boolean queueWasModified() {
				return origInsertionPoint != _insertionPoint
					|| origNext != _next;
			}
		};
	}
}
