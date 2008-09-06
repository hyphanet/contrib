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
package com.db4o.foundation.io;

import java.io.*;



/**
 * IMPORTANT: Keep the interface of this class compatible with .NET System.IO.Path otherwise
 * bad things will happen to you.
 * 
 * @sharpen.ignore
 */
public class Path4 { 
	
	private static final java.util.Random _random = new java.util.Random();
	
	public static String getDirectoryName(String targetPath) {
		return new File(targetPath).getParent();
	}

	public static String combine(String parent, String child) {		
		return parent.endsWith(java.io.File.separator)
        ? parent + child
        : parent + java.io.File.separator + child;
	}
	
	public static String getTempPath() {
		String path = System.getProperty("java.io.tmpdir"); 
		if(path == null || path.length() <= 1){
		    path = "/temp"; 
		}
		File4.mkdirs(path);
		return path;
	}

	public static String getTempFileName() {
		String tempPath = getTempPath();
		while (true) {
			String fname = combine(tempPath, "db4o-test-" + nextRandom() + ".tmp");
			if (!File4.exists(fname)) {
				try {
					new FileWriter(fname).close();
				} catch (IOException e) {
					throw new RuntimeException(e.getMessage());
				}
				return fname;
			}
		}
	}

	private static String nextRandom() {
		return Integer.toHexString(_random.nextInt());
	}	
}
