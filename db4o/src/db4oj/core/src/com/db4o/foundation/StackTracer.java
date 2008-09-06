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

import java.io.*;


/**
 * Don't use directly but use Platform4.stackTrace() for 
 * .NET compatibility.
 * @sharpen.ignore 
 * @exclude
 */
public class StackTracer {
	
	public static String stackTrace(){
		try {
			throw new Exception();
		}catch (Exception ex){
			TracingOutputStream tos = new TracingOutputStream();
			PrintWriter pw = new PrintWriter(tos, true);
			ex.printStackTrace(pw);
			return tos.stackTrace();
		}
	}
	
	static class TracingOutputStream extends OutputStream{
		
		private final StringBuffer _stringBuffer = new StringBuffer();
		
		private int _writeCalls;
		
		private static final int IGNORE_FIRST_WRITE_CALLS = 2;
		
		private static final int IGNORE_FIRST_BYTES = 3;
		
		public void write(byte[] b, int off, int len) throws IOException {
			if(_writeCalls++ < IGNORE_FIRST_WRITE_CALLS){
				return;
			}
			for (int i = off + IGNORE_FIRST_BYTES; i < off + len; i++) {
				_stringBuffer.append((char)b[i]);	
			}
		}

		public void write(int b) throws IOException {
			throw new IllegalStateException();
		}
		
		String stackTrace(){
			return _stringBuffer.toString();
		}
		
	}

}
