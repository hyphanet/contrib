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

import com.db4o.typehandlers.*;


/**
 * @exclude
 */
public abstract class TypeHandlerConfiguration {
    
    protected final Config4Impl _config;
    
    private TypeHandler4 _listTypeHandler;
    
    private TypeHandler4 _mapTypeHandler;
    
    public abstract void apply();
    
    public TypeHandlerConfiguration(Config4Impl config){
        _config = config;
    }
    
    protected void listTypeHandler(TypeHandler4 listTypeHandler){
    	_listTypeHandler = listTypeHandler;
    }
    
    protected void mapTypeHandler(TypeHandler4 mapTypehandler){
    	_mapTypeHandler = mapTypehandler;
    }
    
    /*
     * The plan is to switch live both changes at once.
     */
    public static boolean enabled(){
        return false;
    }
    
    protected void registerCollection(Class clazz){
        registerListTypeHandlerFor(clazz);    
    }
    
    protected void registerMap(Class clazz){
        registerMapTypeHandlerFor(clazz);    
    }

    
    protected void ignoreFieldsOn(Class clazz){
    	_config.registerTypeHandler(new SingleClassTypeHandlerPredicate(clazz), new IgnoreFieldsTypeHandler());
    }
    
    private void registerListTypeHandlerFor(Class clazz){
        registerTypeHandlerFor(_listTypeHandler, clazz);
    }
    
    private void registerMapTypeHandlerFor(Class clazz){
        registerTypeHandlerFor(_mapTypeHandler, clazz);
    }
    
    private void registerTypeHandlerFor(TypeHandler4 typeHandler, Class clazz){
        _config.registerTypeHandler(new SingleClassTypeHandlerPredicate(clazz), typeHandler);
    }

}
