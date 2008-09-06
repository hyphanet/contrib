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
package com.db4o.test.util;

import java.io.*;
import java.net.*;
import java.util.*;

import com.db4o.foundation.*;

public class ExcludingClassLoader extends URLClassLoader {
    
    private static final boolean VERBOSE = false;
    
    private final Map _cache = new HashMap();
    
    private Collection4 _excludedNames;

    public ExcludingClassLoader(ClassLoader parent,Class[] excludedClasses) {
        this(parent, collectNames(excludedClasses));
    }
    
    public ExcludingClassLoader(ClassLoader parent,Collection4 excludedNames) {
        super(new URL[]{},parent);
        this._excludedNames=excludedNames;
    }

    protected synchronized Class loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if(_excludedNames.contains(name)) {
            print("EXCLUDED: " + name);
            throw new ClassNotFoundException(name);
        }
        if(_cache.containsKey(name)) {
            print("CACHED: " + name);
            return (Class)_cache.get(name);
        }
        if(mustDelegate(name)) {
            print("NATIVE: " + name);
            return super.loadClass(name, resolve);
        }
        Class clazz = findRawClass(name);
        if(resolve) {
            resolveClass(clazz);
        }
        _cache.put(clazz.getName(), clazz);
        print("LOADED: " + name);
        return clazz;
    }
    
    private static Collection4 collectNames(Class[] classes) {
        Collection4 names = new Collection4();
        for (int classIdx = 0; classIdx < classes.length; classIdx++) {
            names.add(classes[classIdx].getName());
        }
        return names;
    }

    private boolean mustDelegate(String name) {
        return isPlatformClassName(name)
                || name.equals(ExcludingClassLoader.class.getName())
                || name.startsWith("db4ounit.")
                ||((name.startsWith("com.db4o.") && name.indexOf("test.")<0 && name.indexOf("com.db4o.db4ounit.")<0 && name.indexOf("samples.")<0));
    }

    private static boolean isPlatformClassName(String name) {
        return name.startsWith("java.") || name.startsWith("javax.")
                || name.startsWith("sun.");
    }

    private Class findRawClass(String className) throws ClassNotFoundException {
        try {
            String resourcePath = className.replace('.','/') + ".class";
            InputStream resourceStream = getResourceAsStream(resourcePath);
            ByteArrayOutputStream rawByteStream = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int bytesread = 0;
            while((bytesread = resourceStream.read(buf)) >= 0) {
                rawByteStream.write(buf, 0, bytesread);
            }
            resourceStream.close();
            byte[] rawBytes = rawByteStream.toByteArray();
            return super.defineClass(className, rawBytes, 0, rawBytes.length);
        } catch (Exception exc) {
            throw new ClassNotFoundException(className, exc);
        }   
    }

    public static void main(String[] args) throws Exception {
        ClassLoader parent=ExcludingClassLoader.class.getClassLoader();
        String excName=ExcludingClassLoader.class.getName();
        Collection4 excluded=new Collection4();
        ClassLoader incLoader=new ExcludingClassLoader(parent,excluded);
        System.out.println(incLoader.loadClass(excName));
        excluded.add(excName);
        try {
            System.out.println(incLoader.loadClass(excName));
        }
        catch(ClassNotFoundException exc) {
            System.out.println("Ok, not found.");
        }
    }
    
    private void print(String msg){
        if(! VERBOSE){
            return;
        }
        System.err.println(msg);
    }
    
    
}
