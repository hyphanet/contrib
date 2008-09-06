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


/**
 * @exclude
 */
public class TreeIntObject extends TreeInt {

	public Object _object;

	public TreeIntObject(int a_key) {
		super(a_key);
	}

	public TreeIntObject(int a_key, Object a_object) {
		super(a_key);
		_object = a_object;
	}

	public Object shallowClone() {
		return shallowCloneInternal(new TreeIntObject(_key));
	}

	protected Tree shallowCloneInternal(Tree tree) {
		TreeIntObject tio = (TreeIntObject) super.shallowCloneInternal(tree);
		tio._object = _object;
		return tio;
	}
    
    public Object getObject() {
        return _object;
    }
    
    public void setObject(Object obj) {
        _object = obj;
    }

	public Object read(ByteArrayBuffer a_bytes) {
		int key = a_bytes.readInt();
		Object obj = null;
		if (_object instanceof TreeInt) {
			obj = new TreeReader(a_bytes, (Readable) _object).read();
		} else {
			obj = ((Readable) _object).read(a_bytes);
		}
		return new TreeIntObject(key, obj);
	}

	public void write(ByteArrayBuffer a_writer) {
		a_writer.writeInt(_key);
		if (_object == null) {
			a_writer.writeInt(0);
		} else {
			if (_object instanceof TreeInt) {
				TreeInt.write(a_writer, (TreeInt) _object);
			} else {
				((ReadWriteable) _object).write(a_writer);
			}
		}
	}

	public int ownLength() {
		if (_object == null) {
			return Const4.INT_LENGTH * 2;
		} 
		return Const4.INT_LENGTH + ((Readable) _object).marshalledLength();
	}

	boolean variableLength() {
		return true;
	}

}
