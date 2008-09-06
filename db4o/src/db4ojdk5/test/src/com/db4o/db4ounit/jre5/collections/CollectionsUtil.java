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
package com.db4o.db4ounit.jre5.collections;

import com.db4o.collections.*;
import com.db4o.ext.*;
import com.db4o.reflect.*;

import db4ounit.*;
import db4ounit.extensions.*;

/**
 * @exclude 
 * @sharpen.ignore
 */
public class CollectionsUtil {
    @SuppressWarnings("unchecked")
    public static ArrayList4<Integer> retrieveAndAssertNullArrayList4(
            ExtObjectContainer oc, Reflector reflector) throws Exception {
        ArrayList4<Integer> list = (ArrayList4<Integer>) AbstractDb4oTestCase
                .retrieveOnlyInstance(oc, ArrayList4.class);
        Assert.isFalse(oc.isActive(list));
        return list;
    }

    private static Object getField(Reflector reflector, Object parent,
            String fieldName) {
        ReflectClass parentClazz = reflector.forObject(parent);
        ReflectField field = parentClazz.getDeclaredField(fieldName);
        return field.get(parent);
    }

    private static void assertRetrieveStatus(Reflector reflector,
            ArrayMap4<String, Integer> map) {
// TODO COR-1261 fails when run with callConstructors(true) / in environments without serializable constructor
//        Assert.isNull(getField(reflector, map, "_keys"));
//        Assert.isNull(getField(reflector, map, "_values"));
        Assert.areEqual(new Integer(0), getField(reflector, map, "_size"));
    }

    @SuppressWarnings("unchecked")
    public static ArrayMap4<String, Integer> retrieveMapFromDB(
            ExtObjectContainer oc, Reflector reflector) {
        ArrayMap4<String, Integer> map = (ArrayMap4<String, Integer>) AbstractDb4oTestCase
                .retrieveOnlyInstance(oc, ArrayMap4.class);
        assertRetrieveStatus(reflector, map);
        return map;
    }
}
