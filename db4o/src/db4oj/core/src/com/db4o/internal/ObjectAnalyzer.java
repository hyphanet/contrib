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

import com.db4o.internal.fieldhandlers.*;
import com.db4o.reflect.*;


/**
 * @exclude
 */
class ObjectAnalyzer {
    
    private final PartialObjectContainer _container;
    
    private final Object _obj;
    
    private ClassMetadata _classMetadata;
    
    private ObjectReference _ref;
    
    private boolean _notStorable;
    
    ObjectAnalyzer(PartialObjectContainer container, Object obj){
        _container = container;
        _obj = obj;
    }
    
    void analyze(Transaction trans){
        _ref = trans.referenceForObject(_obj);
        if (_ref == null) {
            ReflectClass claxx = _container.reflector().forObject(_obj);
            if(claxx == null){
                notStorable(_obj, claxx);
                return;
            }
            if(!detectClassMetadata(trans, claxx)){
                return;
            }
        } else {
            _classMetadata = _ref.classMetadata();
        }
        
        if (isPlainObjectOrPrimitive(_classMetadata) ) {
            notStorable(_obj, _classMetadata.classReflector());
        }
        
    }

    private boolean detectClassMetadata(Transaction trans, ReflectClass claxx) {
        _classMetadata = _container.getActiveClassMetadata(claxx);
        if (_classMetadata == null) {
            FieldHandler fieldHandler = _container.fieldHandlerForClass(claxx);
            if(fieldHandler instanceof SecondClassTypeHandler){
                notStorable(_obj, claxx);
            }
            _classMetadata = _container.produceClassMetadata(claxx);
            if ( _classMetadata == null){
                notStorable(_obj, claxx);
                return false;
            }
            
            // The following may return a reference if the object is held
            // in a static variable somewhere ( often: Enums) that gets
            // stored or associated on initialization of the ClassMetadata.
            
            _ref = trans.referenceForObject(_obj);
        }
        return true;
    }

    private void notStorable(Object obj, ReflectClass claxx) {
        _container.notStorable(claxx, obj);
        _notStorable = true;
    }
    
    boolean notStorable(){
        return _notStorable;
    }
    
    private final boolean isPlainObjectOrPrimitive(ClassMetadata classMetadata) {
        return classMetadata.getID() == Handlers4.UNTYPED_ID  || classMetadata.isPrimitive();
    }

    ObjectReference objectReference() {
        return _ref;
    }

    public ClassMetadata classMetadata() {
        return _classMetadata;
    }

}
