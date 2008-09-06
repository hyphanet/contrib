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
package com.db4o;

import com.db4o.ext.*;
import com.db4o.internal.*;

/**
 * Old database boot record class. 
 * 
 * This class was responsible for storing the last timestamp id,
 * for holding a reference to the Db4oDatabase object of the 
 * ObjectContainer and for holding on to the UUID index.
 * 
 * This class is no longer needed with the change to the new
 * fileheader. It still has to stay here to be able to read
 * old databases.
 *
 * @exclude
 * @persistent
 */
public class PBootRecord extends P1Object implements Internal4{

    public Db4oDatabase       i_db;

    public long               i_versionGenerator;

    public MetaIndex          i_uuidMetaIndex;

    public MetaIndex getUUIDMetaIndex(){
        return i_uuidMetaIndex;
    }

    public void write(LocalObjectContainer file) {
        SystemData systemData = file.systemData();
        i_versionGenerator = systemData.lastTimeStampID();
        i_db = systemData.identity();
        file.showInternalClasses(true);
        try {
        	store(2);
        } finally {
        	file.showInternalClasses(false);
        }
    }

}