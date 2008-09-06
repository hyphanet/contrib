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

import java.net.*;

import com.db4o.config.*;
import com.db4o.config.annotations.reflect.*;
import com.db4o.ext.*;
import com.db4o.reflect.*;
import com.db4o.reflect.jdk.*;

/**
 * @decaf.ignore
 */
class JDK_5 extends JDK_1_4 {

	private static final String ENUM_CLASSNAME = "java.lang.Enum";

	private static ReflectClass enumClass;

	public Config4Class extendConfiguration(ReflectClass clazz,
			Configuration config, Config4Class classConfig) {
		Class javaClazz = JdkReflector.toNative(clazz);
		if(javaClazz==null) {
			return classConfig;
		}
		try {
			ConfigurationIntrospector instrospetor = new ConfigurationIntrospector(javaClazz, config, classConfig);
			return instrospetor.apply();
		} catch (Exception exc) {
			throw new Db4oException(exc);
		}
	}
    
    public boolean isConnected(Socket socket){
        if(socket == null){
            return false;
        }
        if(! socket.isConnected() ){
            return false;
        }
        return ! socket.isClosed();
    }

	boolean isEnum(Reflector reflector, ReflectClass claxx) {

		if (claxx == null) {
			return false;
		}

		if (enumClass == null) {
			try {
				enumClass = reflector.forClass(Class.forName(ENUM_CLASSNAME));
			} catch (ClassNotFoundException e) {
				return false;
			}
		}

		return enumClass.isAssignableFrom(claxx);
	}
	
	public long nanoTime() {
		return System.nanoTime();
	}
	
	public boolean useNativeSerialization() {
		return false;
	}

	public int ver() {
	    return 5;
	}
	
}
