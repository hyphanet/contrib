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

import java.io.*;
import java.lang.reflect.*;
import java.util.*;

import com.db4o.*;
import com.db4o.ext.*;
import com.db4o.foundation.*;

/**
 * 
 * @decaf.ignore.jdk11
 */
class JDK_1_4 extends JDK_1_3 {
	
	private Hashtable fileLocks;
	
	private Object _reflectionFactory;
	private Constructor _objectConstructor;
	private Method _factoryMethod;
	
	synchronized void lockFile(String path,Object file) {
		// Conversion to canonical is already done by RandomAccessFileAdapter, but it's probably
		// not safe to rely on that for other file-based adapters.
	    
		String canonicalPath;
        try {
            canonicalPath = new File(path).getCanonicalPath();
        } catch (IOException e) {
            throw new Db4oIOException(e);
        }
        
		if(fileLocks == null){
			fileLocks = new Hashtable();
		}
		if(fileLocks.containsKey(canonicalPath)) {
			throw new DatabaseFileLockedException(canonicalPath);
		}
		
		Object lock = null;
		Object channel = Reflection4.invoke(file, "getChannel");
		lock = Reflection4.invoke(channel, "tryLock");
		if(lock == null){
			throw new DatabaseFileLockedException(canonicalPath);
		}
		fileLocks.put(canonicalPath, lock);
	}
	
	synchronized void unlockFile(String path, Object file) {
		if (fileLocks == null) {
			return;
		}
		Object fl = fileLocks.get(path);
		if (fl == null) {
			return;
		}
		Reflection4.invoke("java.nio.channels.FileLock", "release", null, null, fl);
		fileLocks.remove(path);
	}
	
	public Constructor serializableConstructor(Class clazz){
	    if(_reflectionFactory == null){
	        if(! initSerializableConstructor()){
	            Platform4.callConstructorCheck = TernaryBool.YES;
	            return null;
	        }
	    }
	    return (Constructor) Reflection4.invoke(new Object[]{clazz, _objectConstructor}, _reflectionFactory, _factoryMethod);
	}
	
	
	private boolean initSerializableConstructor(){
		try {
			_reflectionFactory = Reflection4.invoke(Platform4.REFLECTIONFACTORY,
					"getReflectionFactory", null, null, null);
			_factoryMethod = Reflection4.getMethod(Platform4.REFLECTIONFACTORY,
					"newConstructorForSerialization", new Class[] { Class.class,
							Constructor.class });
			if (_factoryMethod == null) {
				return false;
			}
		} catch (ReflectException e) {
			return false;
		}
		
		try {
			_objectConstructor = Object.class
					.getDeclaredConstructor((Class[]) null);
			return true;
		} catch (Exception e) {
			if (Debug.atHome) {
				e.printStackTrace();
			}
			return false;
		}
	}
	
	public int ver(){
	    return 4;
	}
		
}
