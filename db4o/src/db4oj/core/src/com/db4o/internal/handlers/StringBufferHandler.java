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
package com.db4o.internal.handlers;

import com.db4o.ext.*;
import com.db4o.foundation.*;
import com.db4o.internal.*;
import com.db4o.internal.delete.*;
import com.db4o.marshall.*;
import com.db4o.reflect.*;
import com.db4o.typehandlers.*;

public final class StringBufferHandler implements TypeHandler4, BuiltinTypeHandler,
        SecondClassTypeHandler, VariableLengthTypeHandler, EmbeddedTypeHandler {

    private ReflectClass _classReflector;
    
    public void defragment(DefragmentContext context) {
        stringHandler(context).defragment(context);
    }

    public void delete(DeleteContext context) throws Db4oIOException {
        stringHandler(context).delete(context);
    }

    public Object read(ReadContext context) {
        Object read = stringHandler(context).read(context);
        if (null == read) {
            return null;
        }
        return new StringBuffer((String) read);
    }

    public void write(WriteContext context, Object obj) {
        stringHandler(context).write(context, obj.toString());
    }

    private TypeHandler4 stringHandler(Context context) {
        return handlers(context)._stringHandler;
    }

    private HandlerRegistry handlers(Context context) {
        return ((InternalObjectContainer) context.objectContainer()).handlers();
    }

    public PreparedComparison prepareComparison(Context context, Object obj) {
        return stringHandler(context).prepareComparison(context, obj);
    }

    /*
     * @see com.db4o.internal.BuiltinTypeHandler#classReflector(com.db4o.reflect.Reflector)
     */
    public ReflectClass classReflector() {
        return _classReflector;
    }

	public void registerReflector(Reflector reflector) {
        _classReflector = reflector.forClass(StringBuffer.class);
	}
}