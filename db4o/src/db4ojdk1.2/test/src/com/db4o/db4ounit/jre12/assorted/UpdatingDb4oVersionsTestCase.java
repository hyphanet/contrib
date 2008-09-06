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
package com.db4o.db4ounit.jre12.assorted;

import com.db4o.*;
import com.db4o.config.*;
import com.db4o.db4ounit.common.handlers.*;
import com.db4o.ext.*;
import com.db4o.internal.*;
import com.db4o.internal.btree.*;
import com.db4o.internal.classindex.*;
import com.db4o.query.*;
import com.db4o.reflect.*;
import com.db4o.test.*;

import db4ounit.*;


public class UpdatingDb4oVersionsTestCase extends FormatMigrationTestCaseBase {

	public static void main(String[] args) {
		new ConsoleTestRunner(UpdatingDb4oVersionsTestCase.class).run();
	}

    protected void configureForTest(Configuration config){
        config.objectClass(UpdatingDb4oVersions.class).objectField("name").indexed(true);
    }
    
    protected void configureForStore(Configuration config){
        config.objectClass(UpdatingDb4oVersions.class).objectField("name").indexed(true);
    }
    
    /**
     * @deprecated we are using deprecated apis
     */
    protected void store(ExtObjectContainer oc){
        UpdatingDb4oVersions udv = new UpdatingDb4oVersions();
        udv.name = "check";
        udv.list = oc.collections().newLinkedList();
        udv.map = oc.collections().newHashMap(1);
        storeObject(oc, udv);
        oc.store(udv);
        udv.list.add("check");
        udv.map.put("check","check");
    }
    
    protected void assertObjectsAreReadable(ExtObjectContainer objectContainer){
        checkStoredObjectsArePresent(objectContainer);
        checkBTreeSize(objectContainer);
    }

    private void checkBTreeSize(ExtObjectContainer objectContainer) {
        ObjectContainerBase container = (ObjectContainerBase)objectContainer;
        Reflector reflector = container.reflector();
        ReflectClass claxx = reflector.forClass(UpdatingDb4oVersions.class);
        ClassMetadata yc = container.classMetadataForReflectClass(claxx); 
        BTreeClassIndexStrategy btreeClassIndexStrategy = (BTreeClassIndexStrategy) yc.index();
        BTree btree = btreeClassIndexStrategy.btree();
        Assert.isNotNull(btree);
        int size = btree.size(container.transaction());
        Assert.areEqual(1, size);
    }

    private void checkStoredObjectsArePresent(ExtObjectContainer objectContainer) {
        Query q = objectContainer.query();
        q.constrain(UpdatingDb4oVersions.class);
        ObjectSet objectSet = q.execute();
        Assert.areEqual(1, objectSet.size());
        UpdatingDb4oVersions udv = (UpdatingDb4oVersions)objectSet.next();
        Assert.areEqual("check", udv.name);
        Assert.areEqual(1, udv.list.size());
        Assert.areEqual("check", udv.list.get(0));
        Assert.areEqual("check", udv.map.get("check"));
    }
    
    protected String[] versionNames() {
        return UpdatingDb4oVersions.VERSIONS;
    }

    protected String fileNamePrefix() {
        return "";
    }
    
}

