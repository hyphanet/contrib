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
package com.db4o.internal.handlers.array;

import com.db4o.*;
import com.db4o.foundation.*;
import com.db4o.internal.*;
import com.db4o.marshall.*;
import com.db4o.reflect.*;


/**
 * @exclude
 */
public class ArrayVersionHelper {
    
    public int classIDFromInfo(ObjectContainerBase container, ArrayInfo info){
        ClassMetadata classMetadata = container.produceClassMetadata(info.reflectClass());
        if (classMetadata == null) {
        	return Const4.IGNORE_ID;
        }
        return classMetadata.getID();
        
    }
    
    public int classIdToMarshalledClassId(int classID, boolean primitive){
        if(primitive){
            classID -= Const4.PRIMITIVE;
        }
        return - classID;
    }
    
    public ReflectClass classReflector(Reflector reflector, ClassMetadata classMetadata, boolean isPrimitive){
        return isPrimitive ?   
            Handlers4.primitiveClassReflector(classMetadata, reflector) : 
            classMetadata.classReflector();
    }
    
    public boolean useJavaHandling() {
    	return ! Deploy.csharp;
    }
    
    public boolean hasNullBitmap(ArrayInfo info) {
    	return false;
    }
    
    public boolean isPreVersion0Format(int elementCount) {
        return false;
    }
    
    public boolean isPrimitive(Reflector reflector, ReflectClass claxx, ClassMetadata classMetadata) {
        if(Deploy.csharp){
            return false;
        }
        return claxx.isPrimitive();
    }
    
    public ReflectClass reflectClassFromElementsEntry(ObjectContainerBase container, ArrayInfo info, int classID) {
        if(classID == Const4.IGNORE_ID){
            // TODO: Here is a low-frequency mistake, extremely unlikely.
            // If classID == 99999 by accident then we will get ignore.
            
            return null;
        }
            
        info.primitive(false);
        
        if(useJavaHandling()){
            if(classID < Const4.PRIMITIVE){
                info.primitive(true);
                classID -= Const4.PRIMITIVE;
            }
        }
        classID = - classID;
        
        ClassMetadata classMetadata0 = container.classMetadataForId(classID);
        if (classMetadata0 != null) {
            return classReflector(container.reflector(), classMetadata0, info.primitive());
        }
            
        return null;
    }
    
    public void writeTypeInfo(WriteContext context, ArrayInfo info) {
    	
    }
    
    public void readTypeInfo(Transaction trans, ReadBuffer buffer, ArrayInfo info, int classID) {
    	
    }


}
