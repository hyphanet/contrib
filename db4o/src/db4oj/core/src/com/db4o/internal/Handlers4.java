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

import com.db4o.internal.handlers.*;
import com.db4o.internal.handlers.array.*;
import com.db4o.internal.marshall.*;
import com.db4o.reflect.*;
import com.db4o.typehandlers.*;


/**
 * @exclude
 */
public class Handlers4 {

    public static final int INT_ID = 1;
    
    public static final int LONG_ID = 2;
    
    public static final int FLOAT_ID = 3;
    
    public static final int BOOLEAN_ID = 4;
    
    public static final int DOUBLE_ID = 5;
    
    public static final int BYTE_ID = 6;
    
    public static final int CHAR_ID = 7;
    
    public static final int SHORT_ID = 8;
    
    public static final int STRING_ID = 9;
    
    public static final int DATE_ID = 10;
    
    public static final int UNTYPED_ID = 11;
    
    public static final int ANY_ARRAY_ID = 12;
    
    public static final int ANY_ARRAY_N_ID = 13;
    
    public static TypeHandler4 correctHandlerVersion(HandlerVersionContext context, TypeHandler4 handler){
        int version = context.handlerVersion();
        if(version >= HandlerRegistry.HANDLER_VERSION){
            return handler;
        }
        return context.transaction().container().handlers().correctHandlerVersion(handler, version);
    }
    
    public static boolean handlerCanHold(TypeHandler4 handler, Reflector reflector, ReflectClass claxx){
        TypeHandler4 baseTypeHandler = baseTypeHandler(handler);
        if(handlesSimple(baseTypeHandler)){
            return claxx.equals(((BuiltinTypeHandler)baseTypeHandler).classReflector());
        }
        
        if(baseTypeHandler instanceof UntypedFieldHandler){
            return true;
        }
        
        if(handler instanceof CanHoldAnythingHandler){
        	return true;
        }
        
        ClassMetadata classMetadata = (ClassMetadata) baseTypeHandler;
        ReflectClass classReflector = classMetadata.classReflector();
        if(classReflector.isCollection()){
            return true;
        }
        return classReflector.isAssignableFrom(claxx);
    }
    
    public static boolean handlesSimple(TypeHandler4 handler){
        TypeHandler4 baseTypeHandler = baseTypeHandler(handler); 
        return (baseTypeHandler instanceof PrimitiveHandler)
        	|| (baseTypeHandler instanceof StringHandler)
        	|| (baseTypeHandler instanceof SecondClassTypeHandler); 
    }
    
    public static boolean handlesClass(TypeHandler4 handler){
        return baseTypeHandler(handler) instanceof FirstClassHandler;
    }
    
    public static ReflectClass primitiveClassReflector(TypeHandler4 handler, Reflector reflector){
        TypeHandler4 baseTypeHandler = baseTypeHandler(handler);
        if(baseTypeHandler instanceof PrimitiveHandler){
            return ((PrimitiveHandler)baseTypeHandler).primitiveClassReflector();
        }
        return null;
    }
    
    public static TypeHandler4 baseTypeHandler(TypeHandler4 handler){
        if(handler instanceof ArrayHandler){
            return ((ArrayHandler)handler).delegateTypeHandler();
        }
        if(handler instanceof PrimitiveFieldHandler){
            return ((PrimitiveFieldHandler)handler).typeHandler();
        }
        return handler;
    }
    
    public static ReflectClass baseType(ReflectClass clazz){
        if(clazz == null){
            return null;
        }
        if(clazz.isArray()){
            return baseType(clazz.getComponentType());
        }
        return clazz;
    }
}
