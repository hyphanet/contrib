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
package com.db4o.constraints;

import com.db4o.config.*;
import com.db4o.events.*;
import com.db4o.ext.*;
import com.db4o.foundation.*;
import com.db4o.internal.*;
import com.db4o.internal.btree.*;
import com.db4o.reflect.*;
import com.db4o.reflect.core.*;

/**
 * configures a field of a class to allow unique values only.
 */
public class UniqueFieldValueConstraint implements ConfigurationItem {
	
	protected final Object _clazz;
	protected final String _fieldName;
	
	/**
	 * constructor to create a UniqueFieldValueConstraint. 
	 * @param clazz can be a class (Java) / Type (.NET) / instance of the class / fully qualified class name
	 * @param fieldName the name of the field that is to be unique. 
	 */
	public UniqueFieldValueConstraint(Object clazz, String fieldName) {
		_clazz = clazz;
		_fieldName = fieldName;
	}
	
	public void prepare(Configuration configuration) {
		// Nothing to do...
	}
	
	/**
	 * internal method, public for implementation reasons.
	 */
	public void apply(final InternalObjectContainer objectContainer) {
		
		EventRegistryFactory.forObjectContainer(objectContainer).committing().addListener(
				new EventListener4() {

			private FieldMetadata _fieldMetaData;
			
			private void ensureSingleOccurence(Transaction trans, ObjectInfoCollection col){
				Iterator4 i = col.iterator();
				while(i.moveNext()){
					ObjectInfo info = (ObjectInfo) i.current();
					int id = (int)info.getInternalID();
					
					// TODO: check if the object is of the appropriate
					// type before going further?
					
					HardObjectReference ref = HardObjectReference.peekPersisted(trans, id, 1);
					Object fieldValue = fieldMetadata().getOn(trans, ref._object);
					if(fieldValue == null){
						continue;
					}
					BTreeRange range = fieldMetadata().search(trans, fieldValue);
					if(range.size() > 1){
						throw new UniqueFieldValueConstraintViolationException(classMetadata().getName(), fieldMetadata().getName()); 
					}
				}
			}

			private boolean isClassMetadataAvailable() {
				return null != classMetadata();
			}
			
			private FieldMetadata fieldMetadata() {
				if(_fieldMetaData != null){
					return _fieldMetaData;
				}
				_fieldMetaData = classMetadata().fieldMetadataForName(_fieldName);
				return _fieldMetaData;
			}
			
			private ClassMetadata classMetadata() {
				return objectContainer.classMetadataForReflectClass(reflectClass()); 
			}

			private ReflectClass reflectClass() {
				return ReflectorUtils.reflectClassFor(objectContainer.reflector(), _clazz);
			}
	
			public void onEvent(Event4 e, EventArgs args) {
				if (!isClassMetadataAvailable()) {
					return;
				}
				CommitEventArgs commitEventArgs = (CommitEventArgs) args;
				Transaction trans = (Transaction) commitEventArgs.transaction();
				ensureSingleOccurence(trans, commitEventArgs.added());
				ensureSingleOccurence(trans, commitEventArgs.updated());
			}
		});
		
	}
}
