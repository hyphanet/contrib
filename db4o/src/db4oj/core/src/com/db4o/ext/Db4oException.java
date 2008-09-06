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
package com.db4o.ext;

import com.db4o.foundation.*;
import com.db4o.internal.*;

/**
 * db4o exception wrapper: Exceptions occurring during internal processing
 * will be proliferated to the client calling code encapsulated in an exception
 * of this type. The original exception, if any, is available through
 * Db4oException#getCause().
 */
public class Db4oException extends ChainedRuntimeException {
	
	/**
	 * Simple constructor
	 */
	public Db4oException() {
		this(null, null);
	}
	
	/**
	 * Constructor with an exception message specified 
	 * @param msg exception message 
	 */
	public Db4oException(String msg) {
		this(msg, null);
	}

	/**
	 * Constructor with an exception cause specified
	 * @param cause exception cause
	 */
	public Db4oException(Throwable cause) {
		this(null, cause);
	}
	
	/**
	 * Constructor with an exception message selected
	 * from the internal message collection. 
	 * @param messageConstant internal db4o message number
	 */
	public Db4oException(int messageConstant){
		this(Messages.get(messageConstant));
	}
	
	/**
	 * Constructor with an exception message and cause specified
	 * @param msg exception message
	 * @param cause exception cause
	 */
	public Db4oException(String msg, Throwable cause) {
		super(msg, cause);
	}
}
