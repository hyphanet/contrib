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
import java.text.*;
import java.util.*;

import com.db4o.*;
import com.db4o.reflect.*;
import com.db4o.reflect.generic.*;
import com.db4o.reflect.jdk.*;


/**
 * 
 * package and class name are hard-referenced in JavaOnly#jdk()
 * 
 * TODO: may need to use this on instead of JDK on .NET. Check!
 * @sharpen.ignore
 */
public class JDKReflect extends JDK {
    Class constructorClass(){
        return Constructor.class;
    }
    
    Object deserialize(byte[] bytes) {
        try {
            return new ObjectInputStream(new ByteArrayInputStream(bytes)).readObject();
        } catch (Exception e) {
        }
        return null;
    }
    
	String format(Date date, boolean showTime) {
        String fmt = "yyyy-MM-dd";
        if (showTime) {
            fmt += " HH:mm:ss";
        }
        return new SimpleDateFormat(fmt).format(date);
	}
	
	public Class loadClass(String className, Object loader) throws ClassNotFoundException {
        return (loader != null ? ((ClassLoader)loader).loadClass(className) : Class.forName(className));
    }

	
    /**
     * use for system classes only, since not ClassLoader
     * or Reflector-aware
     */
    final boolean methodIsAvailable(String className, String methodName,
			Class[] params) {
		return Reflection4.getMethod(className, methodName, params) != null;
	}
    
    public void registerCollections(GenericReflector reflector) {
        reflector.registerCollection(java.util.Vector.class);
        reflector.registerCollection(java.util.Hashtable.class);
        reflector.registerCollectionUpdateDepth(java.util.Hashtable.class, 3);
    }
    
    byte[] serialize(Object obj) throws Exception{
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        new ObjectOutputStream(byteStream).writeObject(obj);
        return byteStream.toByteArray();
    }

    public Reflector createReflector(Object classLoader) {
    	if(classLoader==null) {
            
            // FIXME: The new reflector does not like the ContextCloader at all.
            //        Resolve hierarchies.
            // classLoader=getContextClassLoader();
            
            // if (cl == null || classloaderName.indexOf("eclipse") >= 0) {
                classLoader= Db4o.class.getClassLoader();
            // 
    	}
    	return new JdkReflector((ClassLoader)classLoader);
    }
    
    public Reflector reflectorForType(Class clazz) {
    	return createReflector(clazz.getClassLoader());
    }
}
