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
package com.db4o.typehandlers;

import java.util.*;

import com.db4o.ext.*;
import com.db4o.foundation.*;
import com.db4o.internal.*;
import com.db4o.internal.activation.*;
import com.db4o.internal.delete.*;
import com.db4o.internal.handlers.*;
import com.db4o.internal.marshall.*;
import com.db4o.marshall.*;

/**
 * Typehandler for java.util.Hashtable
 * @sharpen.ignore
 */
public class HashtableTypeHandler implements TypeHandler4 , FirstClassHandler, CanHoldAnythingHandler, VariableLengthTypeHandler{
    
    public PreparedComparison prepareComparison(Context context, Object obj) {
        // TODO Auto-generated method stub
        return null;
    }

    public void write(WriteContext context, Object obj) {
        Hashtable hashtable = (Hashtable)obj;
        writeElementCount(context, hashtable);
        writeElements(context, hashtable);
    }
    
    public Object read(ReadContext context) {
    	Hashtable hashtable = (Hashtable)((UnmarshallingContext) context).persistentObject();
        int elementCount = context.readInt();
        TypeHandler4 elementHandler = elementTypeHandler(context, hashtable);
        for (int i = 0; i < elementCount; i++) {
            Object key = context.readObject(elementHandler);
            Object value = context.readObject(elementHandler);
            hashtable.put(key, value);
        }
        return hashtable;
    }
    
    private void writeElementCount(WriteContext context, Hashtable hashtable) {
        context.writeInt(hashtable.size());
    }

    private void writeElements(WriteContext context, Hashtable hashtable) {
        TypeHandler4 elementHandler = elementTypeHandler(context, hashtable);
        final Enumeration elements = hashtable.keys();
        while (elements.hasMoreElements()) {
            Object key = elements.nextElement();
            context.writeObject(elementHandler, key);
            context.writeObject(elementHandler, hashtable.get(key));
        }
    }

    private ObjectContainerBase container(Context context) {
        return ((InternalObjectContainer)context.objectContainer()).container();
    }
    
    private TypeHandler4 elementTypeHandler(Context context, Hashtable hashtable){
        
        // TODO: If all elements in the Hashtable are of one type,
        //       it is possible to use a more specific handler
        
        return container(context).handlers().untypedObjectHandler();
    }        

    public void delete(final DeleteContext context) throws Db4oIOException {
        if (! context.cascadeDelete()) {
            return;
        }
        TypeHandler4 handler = elementTypeHandler(context, null);
        int elementCount = context.readInt();
        for (int i = elementCount; i > 0; i--) {
            handler.delete(context);
            handler.delete(context);
        }
    }

    public void defragment(DefragmentContext context) {
        TypeHandler4 handler = elementTypeHandler(context, null);
        int elementCount = context.readInt(); 
        for (int i = elementCount; i > 0; i--) {
            context.defragment(handler);
            context.defragment(handler);
        }
    }
    
    public final void cascadeActivation(ActivationContext4 context) {
    	Hashtable hashtable = (Hashtable) context.targetObject();
        Enumeration keys = (hashtable).keys();
        while (keys.hasMoreElements()) {
            final Object key = keys.nextElement();
            context.cascadeActivationToChild(key);
            context.cascadeActivationToChild(hashtable.get(key));
        }
    }

    public TypeHandler4 readCandidateHandler(QueryingReadContext context) {
        return this;
    }
    
    public void collectIDs(final QueryingReadContext context) {
        int elementCount = context.readInt();
        TypeHandler4 elementHandler = context.container().handlers().untypedObjectHandler();
        for (int i = 0; i < elementCount; i++) {
            context.readId(elementHandler);
            context.skipId(elementHandler);
        }
    }

}
