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


/**
 * @exclude
 */
public class StoredClassImpl implements StoredClass {
    
    private final Transaction _transaction;
    
    private final ClassMetadata _classMetadata;
    
    public StoredClassImpl(Transaction transaction, ClassMetadata classMetadata){
        if(classMetadata == null){
            throw new IllegalArgumentException();
        }
        _transaction = transaction;
        _classMetadata = classMetadata;
    }

    public long[] getIDs() {
        return _classMetadata.getIDs(_transaction);
    }

    public String getName() {
        return _classMetadata.getName();
    }

    public StoredClass getParentStoredClass() {
        ClassMetadata parentClassMetadata = _classMetadata.getAncestor();
        if(parentClassMetadata == null){
            return null;
        }
        return new StoredClassImpl(_transaction, parentClassMetadata);
    }

    public StoredField[] getStoredFields() {
        StoredField[] fieldMetadata = _classMetadata.getStoredFields();
        StoredField[] storedFields = new StoredField[fieldMetadata.length];
        for (int i = 0; i < fieldMetadata.length; i++) {
            storedFields[i] = new StoredFieldImpl(_transaction, (FieldMetadata)fieldMetadata[i]);
        }
        return storedFields;
    }
    
    public boolean hasClassIndex() {
        return _classMetadata.hasClassIndex();
    }

    // TODO: Write test case.
    public void rename(String newName) {
        _classMetadata.rename(newName);
    }

    public StoredField storedField(String name, Object type) {
        FieldMetadata fieldMetadata = (FieldMetadata) _classMetadata.storedField(name, type);
        if(fieldMetadata == null){
            return null;
        }
        return new StoredFieldImpl(_transaction, fieldMetadata);
    }
    
    public int hashCode() {
        return _classMetadata.hashCode();
    }

    public boolean equals(Object obj) {
        if (obj == null){
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        return _classMetadata.equals(((StoredClassImpl) obj)._classMetadata);
    }

	public int instanceCount() {
		return _classMetadata.instanceCount(_transaction);
	}

}
