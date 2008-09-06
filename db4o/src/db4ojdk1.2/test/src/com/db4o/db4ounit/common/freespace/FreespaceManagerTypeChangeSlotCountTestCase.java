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
package com.db4o.db4ounit.common.freespace;

import java.util.*;

import com.db4o.*;
import com.db4o.config.*;
import com.db4o.foundation.*;
import com.db4o.foundation.io.*;
import com.db4o.internal.*;
import com.db4o.internal.freespace.*;
import com.db4o.internal.slots.*;

import db4ounit.*;

public class FreespaceManagerTypeChangeSlotCountTestCase implements TestCase {

    private static final int SIZE = 10000;
    private LocalObjectContainer _container;
    private Configuration _currentConfig;
    private String _fileName;
    
    public static void main(String[] args) {
        new ConsoleTestRunner(FreespaceManagerTypeChangeSlotCountTestCase.class).run();
    }
    
    public FreespaceManagerTypeChangeSlotCountTestCase() {
        _fileName = Path4.getTempFileName();
        File4.delete(_fileName);
    }
    

    public void testMigrateFromRamToBTree() throws Exception {
        createDatabaseUsingRamManager();
        migrateToBTree();
        reopen();
        createFreeSpace();
        List initialSlots = getSlots(_container.freespaceManager());
        reopen();
        List currentSlots = getSlots(_container.freespaceManager());
        Assert.areEqual(initialSlots, currentSlots);
        _container.close();
    }
    
    public void testMigrateFromBTreeToRam() throws Exception {
        createDatabaseUsingBTreeManager();
        migrateToRam();
        createFreeSpace();
        List initialSlots = getSlots(_container.freespaceManager());
        reopen();
        Assert.areEqual(initialSlots, getSlots(_container.freespaceManager()));
        _container.close();
        
    }
    
    
    private void reopen() {
        _container.close();
        open();
    }

    private void createDatabaseUsingRamManager() {
        configureRamManager();
        open();
    }
    
    private void createDatabaseUsingBTreeManager() {
        configureBTreeManager();
        open();
    }

    private void open() {
        _container = (LocalObjectContainer)Db4o.openFile(_currentConfig, _fileName);
    }

    private void createFreeSpace() {
        Slot slot = _container.getSlot(SIZE);
        _container.free(slot);
    }

    private void migrateToBTree() throws Exception {
        _container.close();
        configureBTreeManager();
        open();
    }


    private void configureBTreeManager() {
        _currentConfig = Db4o.newConfiguration();
        _currentConfig.freespace().useBTreeSystem();
    }
    
    private void migrateToRam() throws Exception {
        _container.close();
        configureRamManager();
        open();
    }


    private void configureRamManager() {
        _currentConfig = Db4o.newConfiguration();
        _currentConfig.freespace().useRamSystem();
    }
    
    private List getSlots(FreespaceManager freespaceManager) {
        final List retVal = new ArrayList();
        freespaceManager.traverse(new Visitor4(){
            public void visit(Object obj) {
                retVal.add(obj);
            }
        });
        return retVal;
    }

}
