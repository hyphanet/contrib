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

import java.net.*;
import java.util.*;


/**
 * @exclude
 * @sharpen.ignore
 */
public class SignatureGenerator {
	
	private static final Random _random = new Random();
	
	private static int _counter;
	
	public static String generateSignature() {
		StringBuffer sb = new StringBuffer();
		try {
			sb.append(java.net.InetAddress.getLocalHost().getHostName());
		} catch (UnknownHostException e) {
		}
		int hostAddress = 0;
		byte[] addressBytes;
		try {
			addressBytes = java.net.InetAddress.getLocalHost().getAddress();
			for (int i = 0; i < addressBytes.length; i++) {
				hostAddress <<= 4;
				hostAddress -= addressBytes[i];
			}
		} catch (UnknownHostException e) {
		}
		sb.append(Integer.toHexString(hostAddress));
		sb.append(Long.toHexString(System.currentTimeMillis()));
		sb.append(Integer.toHexString(randomInt()));
		sb.append(Integer.toHexString(_counter++));
		return sb.toString();
	}

	private static int randomInt() {
		return _random.nextInt();
	}

}
