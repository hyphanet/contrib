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
 * Adapts Iterable4/Iterator4 iteration model (moveNext, current) to the old db4o
 * and jdk model (hasNext, next).
 * 
 * @exclude
 */
public class Iterable4Adaptor {
	
	private static final Object EOF_MARKER = new Object();
	private static final Object MOVE_NEXT_MARKER = new Object();
	
	private final Iterable4 _delegate;
    
    private Iterator4 _iterator; 
    
    private Object _current = MOVE_NEXT_MARKER;
    
    public Iterable4Adaptor(Iterable4 delegate_) {
    	_delegate = delegate_;
    }
    
    public boolean hasNext() {
    	if (_current == MOVE_NEXT_MARKER) {
    		return moveNext();
    	}
    	return _current != EOF_MARKER;
    }
    
    public Object next() {
    	if (!hasNext()) {
    		throw new IllegalStateException();
    	}
        Object returnValue = _current;
        _current = MOVE_NEXT_MARKER;
        return returnValue;
    }

    protected boolean moveNext() {
    	if (null == _iterator) {
    		_iterator = _delegate.iterator();
    	}
    	if (_iterator.moveNext()) {
    		_current = _iterator.current();
    		return true;
    	}
    	_current = EOF_MARKER;
    	return false;
	}

	public void reset() {
        _iterator = null;
        _current = MOVE_NEXT_MARKER;
    }
}
