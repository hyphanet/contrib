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
package com.db4o.io;

import java.io.*;

import com.db4o.*;
import com.db4o.ext.*;
import com.db4o.internal.*;

/**
 * IO adapter for random access files.
 */
public class RandomAccessFileAdapter extends IoAdapter {

	private String _path;

	private RandomAccessFile _delegate;

	public RandomAccessFileAdapter() {
	}

	protected RandomAccessFileAdapter(String path, boolean lockFile,
			long initialLength, boolean readOnly) throws Db4oIOException {
		boolean ok = false;
		try {
			_path = new File(path).getCanonicalPath();
			_delegate = new RandomAccessFile(_path, readOnly ? "r" : "rw");
			if (initialLength > 0) {
				_delegate.seek(initialLength - 1);
				_delegate.write(new byte[] { 0 });
			}
			if (lockFile) {
				Platform4.lockFile(_path, _delegate);
			} 
			ok = true;
		} catch (IOException e) {
			throw new Db4oIOException(e);
		} finally {
			if(!ok) {
				close();
			}
		}
	}

	public void close() throws Db4oIOException {
		
		// FIXME: This is a temporary quickfix for a bug in Android.
		//        Remove after Android has been fixed.
		try {
			if (_delegate != null) {
				_delegate.seek(0);
			}
		} catch (IOException e) {
			// ignore
		}
		
		Platform4.unlockFile(_path, _delegate);
		try {
			if (_delegate != null) {
				_delegate.close();
			}
		} catch (IOException e) {
			throw new Db4oIOException(e);
		}
	}

	public void delete(String path) {
		new File(path).delete();
	}

	public boolean exists(String path) {
		File existingFile = new File(path);
		return existingFile.exists() && existingFile.length() > 0;
	}

	public long getLength() throws Db4oIOException {
		try {
			return _delegate.length();
		} catch (IOException e) {
			throw new Db4oIOException(e);
		}
	}

	public IoAdapter open(String path, boolean lockFile, long initialLength, boolean readOnly)
			throws Db4oIOException {
		return new RandomAccessFileAdapter(path, lockFile, initialLength, readOnly);
	}

	public int read(byte[] bytes, int length) throws Db4oIOException {
		try {
			return _delegate.read(bytes, 0, length);
		} catch (IOException e) {
			throw new Db4oIOException(e);
		}
	}

	public void seek(long pos) throws Db4oIOException {

		if (DTrace.enabled) {
			DTrace.REGULAR_SEEK.log(pos);
		}
		try {
			_delegate.seek(pos);
		} catch (IOException e) {
			throw new Db4oIOException(e);
		}

	}

	public void sync() throws Db4oIOException {
		try {
			_delegate.getFD().sync();
		} catch (IOException e) {
			throw new Db4oIOException(e);
		}
	}

	public void write(byte[] buffer, int length) throws Db4oIOException {
		try {
			_delegate.write(buffer, 0, length);
		} catch (IOException e) {
			throw new Db4oIOException(e);
		}
	}
}
