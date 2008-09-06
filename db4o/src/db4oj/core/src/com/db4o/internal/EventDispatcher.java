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

import com.db4o.reflect.*;


/** @exclude */
public final class EventDispatcher {
	private static final String[] events = {
		"objectCanDelete",
		"objectOnDelete", 
		"objectOnActivate", 
		"objectOnDeactivate",
		"objectOnNew",
		"objectOnUpdate",
		"objectCanActivate",
		"objectCanDeactivate",
		"objectCanNew",
		"objectCanUpdate"
	};
	
	static final int CAN_DELETE = 0;
	static final int DELETE = 1;
	static final int SERVER_COUNT = 2;
	static final int ACTIVATE = 2;
	static final int DEACTIVATE = 3;
	static final int NEW = 4;
	public static final int UPDATE = 5;
	static final int CAN_ACTIVATE = 6;
	static final int CAN_DEACTIVATE = 7;
	static final int CAN_NEW = 8;
	static final int CAN_UPDATE = 9;
	static final int COUNT = 10;
	
	private final ReflectMethod[] methods;
	
	private EventDispatcher(ReflectMethod[] methods_){
		methods = methods_;
	}
	
	boolean dispatch(Transaction trans, Object obj, int eventID) {
		if (methods[eventID] == null) {
			return true;
		}
		Object[] parameters = new Object[] { trans.objectContainer()};
		ObjectContainerBase container = trans.container();
		int stackDepth = container.stackDepth();
		int topLevelCallId = container.topLevelCallId();
		container.stackDepth(0);
		try {
			Object res = methods[eventID].invoke(obj, parameters);
			if (res instanceof Boolean) {
				return ((Boolean) res).booleanValue();
			}
		}  finally {
			container.stackDepth(stackDepth);
			container.topLevelCallId(topLevelCallId);
		}
		return true;
	}
	
	static EventDispatcher forClass(ObjectContainerBase a_stream, ReflectClass classReflector){
        
        if(a_stream == null || classReflector == null){
            return null;
        }
        
		EventDispatcher dispatcher = null;
	    int count = 0;
	    if(a_stream.configImpl().callbacks()){
	        count = COUNT;
	    }else if(a_stream.configImpl().isServer()){
	        count = SERVER_COUNT;
	    }
	    if(count > 0){
			ReflectClass[] parameterClasses = {a_stream._handlers.ICLASS_OBJECTCONTAINER};
			ReflectMethod[] methods = new ReflectMethod[COUNT];
			for (int i = COUNT - 1; i >= 0; i--) {
				ReflectMethod method = classReflector.getMethod(events[i],
						parameterClasses);
				if (null == method) {
					method = classReflector.getMethod(toPascalCase(events[i]),
							parameterClasses);
				}
				if (method != null) {
					methods[i] = method;
					if (dispatcher == null) {
						dispatcher = new EventDispatcher(methods);
					}
				}
			}
	    }
        
		return dispatcher;
	}

	private static String toPascalCase(String name) {
		return name.substring(0, 1).toUpperCase() + name.substring(1);
	}

    public boolean hasEventRegistered(int eventID){
        return methods[eventID] != null;
    }
}
