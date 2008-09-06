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
package com.db4o.internal.handlers.net;

import java.math.*;

import com.db4o.reflect.*;

/**
 * @exclude
 * @sharpen.ignore
 * @decaf.ignore.jdk11
 */
public class NetULong extends NetSimpleTypeHandler{
    
    private static final BigInteger ZERO = new BigInteger("0", 16); //$NON-NLS-1$
    
	private final static BigInteger FACTOR=new BigInteger("100",16); //$NON-NLS-1$
	
	public NetULong(Reflector reflector) {
		super(reflector, 23, 8);
	}
	
	public String toString(byte[] bytes) {
		BigInteger val=ZERO;
		for (int i = 0; i < 8; i++){
			val=val.multiply(FACTOR);
			val=val.add(new BigInteger(String.valueOf(bytes[i] & 0xff),10));
		}
		return val.toString(10);
	}
}
