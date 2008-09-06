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

import com.db4o.config.*;
import com.db4o.internal.activation.*;
import com.db4o.internal.marshall.*;


final class TranslatedAspect extends FieldMetadata
{
	private final ObjectTranslator _translator;

	TranslatedAspect(ClassMetadata containingClass, ObjectTranslator translator){
	    super(containingClass, translator);
		_translator = translator;
		ObjectContainerBase stream = containingClass.container();
		configure(stream.reflector().forClass(translatorStoredClass(translator)), false);
	}
    
    public boolean canUseNullBitmap(){
        return false;
    }

	public void deactivate(Transaction trans, Object onObject, ActivationDepth depth){
		if(depth.requiresActivation()){
			cascadeActivation(trans, onObject, depth);
		}
		setOn(trans, onObject, null);
	}

	public Object getOn(Transaction a_trans, Object a_OnObject) {
		try {
			return _translator.onStore(a_trans.objectContainer(), a_OnObject);
		} catch(ReflectException e) {
			throw e;
		} catch (RuntimeException e) {
			throw new ReflectException(e);
		}
	}
	
	public Object getOrCreate(Transaction a_trans, Object a_OnObject) {
		return getOn(a_trans, a_OnObject);
	}

	public void instantiate(UnmarshallingContext context) {
	    
        Object obj = read(context);

        // Activation of members is necessary on purpose here.
        // Classes like Hashtable need fully activated members
        // to be able to calculate hashCode()
        
        context.container().activate(context.transaction(), obj, context.activationDepth());

        setOn(context.transaction(), context.persistentObject(), obj);
	}
	
	void refresh() {
	    // do nothing
	}
	
	private void setOn(Transaction trans, Object a_onObject, Object toSet) {
		try {
			_translator.onActivate(trans.objectContainer(), a_onObject, toSet);
		} catch (RuntimeException e) {
			throw new ReflectException(e);
		}
	}
	
	protected Object indexEntryFor(Object indexEntry) {
		return indexEntry;
	}
	
	protected Indexable4 indexHandler(ObjectContainerBase stream) {
		return (Indexable4)_handler;
	}
	
	public boolean equals(Object obj) {
        if(obj == this){
            return true;
        }
        if(obj == null || obj.getClass() != getClass()){
            return false;
        }
        TranslatedAspect other = (TranslatedAspect) obj;
        return _translator.equals(other._translator);
	}
	
	public int hashCode() {
	    return _translator.hashCode();
	}
	
    public AspectType aspectType() {
        return AspectType.TRANSLATOR;
    }

}
