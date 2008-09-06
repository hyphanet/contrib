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

import com.db4o.ext.*;
import com.db4o.foundation.*;
import com.db4o.reflect.*;


/**
 * @exclude
 */
public class StoredFieldImpl implements StoredField {
    
    private final Transaction _transaction;
    
    private final FieldMetadata _fieldMetadata;

    public StoredFieldImpl(Transaction transaction, FieldMetadata fieldMetadata) {
        _transaction = transaction;
        _fieldMetadata = fieldMetadata;
    }

    public void createIndex() {
        _fieldMetadata.createIndex();
    }

    public FieldMetadata fieldMetadata(){
        return _fieldMetadata;
    }
    
    public Object get(Object onObject) {
        return _fieldMetadata.get(_transaction, onObject);
    }

    public String getName() {
        return _fieldMetadata.getName();
    }

    public ReflectClass getStoredType() {
        return _fieldMetadata.getStoredType();
    }

    public boolean hasIndex() {
        return _fieldMetadata.hasIndex();
    }

    public boolean isArray() {
        return _fieldMetadata.isArray();
    }

    public void rename(String name) {
        _fieldMetadata.rename(name);
    }

    public void traverseValues(Visitor4 visitor) {
        _fieldMetadata.traverseValues(_transaction, visitor);
    }
    public int hashCode() {
        return _fieldMetadata.hashCode();
    }

    public boolean equals(Object obj) {
        if (obj == null){
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        return _fieldMetadata.equals(((StoredFieldImpl) obj)._fieldMetadata);
    }
    
}
