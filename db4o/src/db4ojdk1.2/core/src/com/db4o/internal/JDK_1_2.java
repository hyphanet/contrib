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

import java.lang.ref.*;
import java.util.*;

import com.db4o.*;
import com.db4o.config.*;
import com.db4o.foundation.*;
import com.db4o.internal.handlers.*;
import com.db4o.internal.handlers.net.*;
import com.db4o.reflect.*;
import com.db4o.reflect.generic.*;
import com.db4o.typehandlers.*;
import com.db4o.types.*;

/**
 * @decaf.ignore.jdk11
 */
class JDK_1_2 extends JDKReflect {
	
	JDK_1_2(){
	}
	
	public Class loadClass(String className, Object loader) throws ClassNotFoundException {
		if(loader == null) {
			loader = getClass().getClassLoader();
		}
		return Class.forName(className, false, (ClassLoader)loader);
	}

	public static void link(){
	    // link standard translators, so they won't get deleted
	    // by deployment
	    
	    new TCollection();
	    new TMap();
	    new TSerializable();
	    new TTreeMap();
	    new TTreeSet();
	}

	/**
	 * @deprecated uses deprecated api
	 */
    Db4oCollections collections(Transaction transaction){
        return new P2Collections(transaction);
    }

    Object createReferenceQueue() {
        return new ReferenceQueue4();
    }

    public Object createWeakReference(Object obj){
        return new WeakReference(obj);
    }
    
    Object createActivateObjectReference(Object a_queue, ObjectReference a_yapObject, Object a_object) {
        return new ActiveObjectReference(a_queue, a_yapObject, a_object);
    }
    
    public void extendConfiguration(Config4Impl config) {
        new TypeHandlerConfigurationJDK_1_2(config).apply();
    }

    void forEachCollectionElement(Object a_object, Visitor4 a_visitor) {
        java.util.Iterator i = null;
        if (a_object instanceof java.util.Collection) {
            i = ((java.util.Collection) a_object).iterator();
        } else if (a_object instanceof java.util.Map) {
            i = ((java.util.Map) a_object).keySet().iterator();
        }
        if (i != null) {
            while (i.hasNext()) {
                a_visitor.visit(i.next());
            }
        }
    }

    Object getContextClassLoader() {
        return Thread.currentThread().getContextClassLoader();
    }

    Object getYapRefObject(Object a_object) {
        if (a_object instanceof ActiveObjectReference) {
            return ((ActiveObjectReference) a_object).get();
        }
        return a_object;
    }
    
    boolean isCollectionTranslator(Config4Class config) {
        if (config != null) {
            ObjectTranslator ot = config.getTranslator();
            if (ot != null) {
                return ot instanceof TCollection || ot instanceof TMap || ot instanceof THashtable;
            }
        }
        return false;
    }
    
    public int ver(){
        return 2;
    }
    
	void killYapRef(Object obj){
		if(obj instanceof ActiveObjectReference){
			((ActiveObjectReference)obj)._referent = null;
		}
	}

    void pollReferenceQueue(ObjectContainerBase container, Object referenceQueue) {
        if (referenceQueue != null) {
            ReferenceQueue4 queue = (ReferenceQueue4) referenceQueue;
            ActiveObjectReference ref;
            synchronized(container.lock()){
	            while ((ref = queue.yapPoll()) != null) {
	                container.removeFromAllReferenceSystems(ref._referent);
	            }
            }
        }
    }
    
	public void registerCollections(GenericReflector reflector) {
		reflector.registerCollection(java.util.Collection.class);
		reflector.registerCollection(java.util.Map.class);
		reflector.registerCollectionUpdateDepth(java.util.Map.class, 3);
	}

    void setAccessible(Object a_accessible) {
		try {
			((java.lang.reflect.AccessibleObject) a_accessible)
					.setAccessible(true);
		} catch (SecurityException e) {

		}
	}
    
    public NetTypeHandler[] netTypes(Reflector reflector) {
        return new NetTypeHandler[] {
            new NetDateTime(reflector),
            new NetDecimal(reflector),
            new NetSByte(reflector),
            new NetUInt(reflector),
            new NetULong(reflector),
            new NetUShort(reflector)
          };
    }
    
    public Object weakReferenceTarget(Object weakRef){
        if(weakRef instanceof WeakReference){
            return ((WeakReference)weakRef).get();
        }
        return weakRef;
    }
    

}
