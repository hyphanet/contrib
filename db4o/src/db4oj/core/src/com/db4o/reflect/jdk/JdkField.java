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
package com.db4o.reflect.jdk;

import java.lang.reflect.*;

import com.db4o.internal.*;
import com.db4o.reflect.*;

/**
 * Reflection implementation for Field to map to JDK reflection.
 * 
 * @sharpen.ignore
 */
public class JdkField implements ReflectField {

    private final Reflector reflector;
	private final Field field;

    public JdkField(Reflector reflector_, Field field_) {
    	reflector = reflector_;
        field = field_;
        setAccessible();
    }

    public String getName() {
        return field.getName();
    }

    public ReflectClass getFieldType() {
        return reflector.forClass(field.getType());
    }

    public boolean isPublic() {
        return Modifier.isPublic(field.getModifiers());
    }

    public boolean isStatic() {
        return Modifier.isStatic(field.getModifiers());
    }

    public boolean isTransient() {
        return Modifier.isTransient(field.getModifiers());
    }

    public void setAccessible() {
        Platform4.setAccessible(field);
    }

    public Object get(Object onObject) {
        try {
            return field.get(onObject);
        } catch (Exception e) {
            return null;
        }
    }

    public void set(Object onObject, Object attribute) {
        try {
            field.set(onObject, attribute);
        } catch (Exception e) {
            // FIXME: This doesn't work when in its own package...
//            if(Debug.atHome){
//                e.printStackTrace();
//            }
        }
    }

	public Object indexEntry(Object orig) {
		return orig;
	}

	public ReflectClass indexType() {
		return getFieldType();
	}
	
	public String toString() {
	    return "JDKField " + getFieldType().getName() + ":" + getName();
	}
}
