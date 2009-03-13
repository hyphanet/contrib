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
package com.db4o.internal.reflect.generic;

import com.db4o.foundation.*;
import com.db4o.internal.*;
import com.db4o.reflect.*;
import com.db4o.reflect.generic.*;

public class KnownClassesCollector {

	private final ObjectContainerBase _container;
	private final KnownClassesRepository _repository;

	public KnownClassesCollector(ObjectContainerBase container, KnownClassesRepository repository) {
		_container = container;
		_repository = repository;
	}

	public ReflectClass[] collect() {
        Collection4 classes = new Collection4();
		collectKnownClasses(classes);
		
		return (ReflectClass[])classes.toArray(new ReflectClass[classes.size()]);
	}
	
	private void collectKnownClasses(final Collection4 classes) {
		final Listener collectingListener = newCollectingClassListener(classes);
		_repository.addListener(collectingListener);
		try { 
			collectKnownClasses(classes, Iterators.copy(_repository.classes()));
		} finally { 
			_repository.removeListener(collectingListener);
		}
	}

	private Listener newCollectingClassListener(final Collection4 classes) {
		return new Listener() {		
			public void onEvent(Object addedClass) {
				collectKnownClass(classes, (ReflectClass) addedClass);
			}
		};
	}

	private void collectKnownClasses(Collection4 collector, Iterator4 knownClasses) {
		while(knownClasses.moveNext()){
            ReflectClass clazz = (ReflectClass) knownClasses.current();
            collectKnownClass(collector, clazz);
		}
	}

	private void collectKnownClass(Collection4 classes, ReflectClass clazz) {
		if(isInternalClass(clazz))
			return;
		
		if(isSecondClass(clazz))
			return;
		
		if(clazz.isArray())
			return;
		
		classes.add(clazz);
	}

	private boolean isInternalClass(ReflectClass clazz) {
		return _container._handlers.ICLASS_INTERNAL.isAssignableFrom(clazz);
	}

	private boolean isSecondClass(ReflectClass clazz) {
		ClassMetadata clazzMeta = _container.classMetadataForReflectClass(clazz);
		return clazzMeta != null && clazzMeta.isSecondClass();
	}
}
