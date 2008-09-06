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

public class ClassIndexException extends ChainedRuntimeException {

	private String _className;
	
	public ClassIndexException(String className) {
		this(null,null,className);
	}

	public ClassIndexException(String msg,String className) {
		this(msg,null,className);
	}

	public ClassIndexException(Throwable cause,String className) {
		this(null,cause,className);
	}

	public ClassIndexException(String msg, Throwable cause,String className) {
		super(enhancedMessage(msg,className), cause);
		_className=className;
	}

	public String className() {
		return _className;
	}
	
	private static String enhancedMessage(String msg,String className) {
		String enhancedMessage="Class index for "+className;
		if(msg!=null) {
			enhancedMessage+=": "+msg;
		}
		return enhancedMessage;
	}
}
