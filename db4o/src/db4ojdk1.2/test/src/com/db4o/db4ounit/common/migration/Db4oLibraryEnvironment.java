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
package com.db4o.db4ounit.common.migration;

import java.io.*;
import java.lang.reflect.*;
import java.net.*;

import com.db4o.db4ounit.util.*;

/**
 * @sharpen.ignore
 */
public class Db4oLibraryEnvironment {
	
	private final static String[] PREFIXES = { "com.db4o" };
	private final ClassLoader _loader;
	
	public Db4oLibraryEnvironment(File db4oLibrary, File additionalClassPath) throws IOException {
		_loader = new VersionClassLoader(urls(db4oLibrary, additionalClassPath), PREFIXES);
	}

	private URL[] urls(File db4oLibrary, File additionalClassPath)
			throws MalformedURLException {
		return new URL[] { toURL(db4oLibrary), toURL(additionalClassPath) };
	}

	/**
	 * @deprecated using deprecated api
	 */
	private URL toURL(File db4oLibrary) throws MalformedURLException {
		return db4oLibrary.toURL();
	}
	
	public String version() throws Exception {
		String version = (String)invokeStaticMethod("com.db4o.Db4o", "version");
		return version.substring(5);
	}	

	private Object invokeStaticMethod(String className, String methodName) throws Exception {
        Class clazz = _loader.loadClass(className);
        Method method = clazz.getMethod(methodName, new Class[] {});
        return method.invoke(null, new Object[] {});
	}
	
	public Object invokeInstanceMethod(Class klass, String methodName, Object[] args) throws Exception {
		Class clazz = _loader.loadClass(klass.getName());
        Method method = clazz.getMethod(methodName, classes(args));
        return method.invoke(clazz.newInstance(), args);
	}

	private Class[] classes(Object[] args) {
		Class[] classes = new Class[args.length];
		for (int i=0; i<args.length; ++i) {
			classes[i] = args[i].getClass();
		}
		return classes;
	}	
}
