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
package com.db4o.types;

/**
 * base interface for db4o collections
 */
public interface Db4oCollection extends Db4oType{
    
    /**
     * configures the activation depth for objects returned from this collection.
     * <br><br>Specify a value less than zero to use the default activation depth
     * configured for the ObjectContainer or for individual objects.
     * @param depth the desired depth
     */
    public void activationDepth(int depth);
    
    
    /**
     * configures objects are to be deleted from the database file if they are
     * removed from this collection.
     * <br><br>Default value: <code>false</code>
     * @param flag the desired setting
     */
    public void deleteRemoved(boolean flag);
    

}
