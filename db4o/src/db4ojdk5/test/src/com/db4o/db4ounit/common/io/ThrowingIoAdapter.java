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
package com.db4o.db4ounit.common.io;

import com.db4o.ext.*;
import com.db4o.io.*;

public class ThrowingIoAdapter extends VanillaIoAdapter {

	private final ThrowCondition _condition;
	private long _pos;
	
	public ThrowingIoAdapter(IoAdapter delegateAdapter, ThrowCondition condition) {
		super(delegateAdapter);
		_condition = condition;
		_pos = 0;
	}

	public IoAdapter open(String path, boolean lockFile, long initialLength, boolean readOnly) throws Db4oIOException {
		return new ThrowingIoAdapter(_delegate.open(path, lockFile, initialLength, readOnly), _condition);
	}

	public void seek(long pos) throws Db4oIOException {
		super.seek(pos);
		_pos = pos;
	}
	
	public void write(byte[] buffer, int length) throws Db4oIOException {
		if(_condition.shallThrow(_pos, length)) {
			throw new Db4oIOException("FAIL");
		}
		_delegate.write(buffer, length);
	}

}
