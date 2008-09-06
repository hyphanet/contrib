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
package com.db4o.foundation.network;

import com.db4o.ext.*;

public interface Socket4 {

	public abstract void close() throws Db4oIOException;

	public abstract void flush() throws Db4oIOException;
    
    public abstract boolean isConnected();

	public abstract int read() throws Db4oIOException;

	public abstract int read(byte[] a_bytes, int a_offset, int a_length) throws Db4oIOException;

	public abstract void setSoTimeout(int timeout);

	public abstract void write(byte[] bytes) throws Db4oIOException;

	public abstract void write(byte[] bytes, int off, int len) throws Db4oIOException;

	public abstract void write(int i) throws Db4oIOException;

	public abstract Socket4 openParalellSocket() throws Db4oIOException;

}