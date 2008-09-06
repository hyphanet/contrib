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
package com.db4o.reflect.self;

public class FieldInfo {
	private String _name;
	private Class _clazz;
	private boolean _isPublic; 
	private boolean _isStatic; 
	private boolean _isTransient;

	
	
	public FieldInfo(String name, Class clazz, boolean isPublic, boolean isStatic, boolean isTransient) {
		_name = name;
		_clazz = clazz;
		_isPublic = isPublic;
		_isStatic = isStatic;
		_isTransient = isTransient;
	}

	public String name() {
		return _name;
	}
	
	public Class type() {
		return _clazz;
	}

	public boolean isPublic() {
		return _isPublic;
	}

	public boolean isStatic() {
		return _isStatic;
	}

	public boolean isTransient() {
		return _isTransient;
	}
}
