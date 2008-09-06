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

public class StackBasedLimitedSpaceThrowCondition implements ThrowCondition {

	private boolean _enabled;
	private long _sizeLimit;
	private final StackBasedConfiguration _config;
	private int _hitCount;
	
	public StackBasedLimitedSpaceThrowCondition(StackBasedConfiguration config) {
		_config = config;
		_sizeLimit = -1;
		_hitCount = 0;
	}
	
	public void enabled(boolean enabled) {
		_enabled = enabled;
	}
	
	public boolean shallThrow(long pos, int numBytes) {
		if(!_enabled) {
			return false;
		}
		if(_sizeLimit < 0) {
			if(!matchesStackTrace()) {
				return false;
			}
			_hitCount++;
			if(_hitCount < _config._hitThreshold) {
				return false;
			}
			_sizeLimit = pos + numBytes + 1;
			return false;
		}
		boolean exceedsLimit = pos + numBytes >= _sizeLimit;
		return exceedsLimit;
	}

	private boolean matchesStackTrace() {
		StackTraceElement[] stackTrace = new Exception().getStackTrace();
		for (StackTraceElement stackFrame : stackTrace) {
			if(stackFrame.getClassName().equals(_config._className) && stackFrame.getMethodName().equals(_config._methodName)) {
				return true;
			}
		}
		return false;
	}

}
